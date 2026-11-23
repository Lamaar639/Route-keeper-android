package com.example.routekeeper.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.routekeeper.data.model.AlternativeRouteOption
import com.example.routekeeper.data.model.Coordinate
import com.example.routekeeper.data.model.DeliveryStop
import com.example.routekeeper.data.model.GpsBreadcrumb
import com.example.routekeeper.data.model.MapPreferences
import com.example.routekeeper.data.model.RouteDraft
import com.example.routekeeper.data.model.RoutingMode
import com.example.routekeeper.data.model.SavedRoute
import com.example.routekeeper.data.model.VideoExportSettings
import com.example.routekeeper.data.repository.RouteRepository
import com.example.routekeeper.data.service.DeliveryParser
import com.example.routekeeper.data.service.ExportedVideoResult
import com.example.routekeeper.data.service.GpsForegroundService
import com.example.routekeeper.data.service.GpsTrackerService
import com.example.routekeeper.data.service.GpsTrackingCoordinator
import com.example.routekeeper.data.service.PdfManifestExtractor
import com.example.routekeeper.data.service.RouteVideoExporter
import com.example.routekeeper.data.service.RoutingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class DriverPaceStats(
    val completedStops: Int = 0,
    val totalStops: Int = 0,
    val stopsPerHour: Int = 0,
    val projectedFinishTime: String = "--:--",
    val percentComplete: Float = 0f
)

data class DriverProgressSummary(
    val routeId: String = "",
    val routeName: String = "",
    val isActiveRoute: Boolean = false,
    val isRecordingGps: Boolean = false,
    val activeNavigatingStopId: String? = null,
    val segmentCount: Int = 0,
    val isTrackCombined: Boolean = false,
    val routeStartedAt: String? = null,
    val speedLimitMph: Float = 30f,
    val latestRoadName: String? = null,
    val stops: List<DeliveryStop> = emptyList(),
    val plannedPath: List<Coordinate> = emptyList(),
    val actualTrack: List<GpsBreadcrumb> = emptyList(),
    val plannedDistanceMeters: Double = 0.0,
    val actualDistanceMeters: Double = 0.0,
    val maxSpeedMph: Float = 0f,
    val avgSpeedMph: Float = 0f,
    val speedingEventsCount: Int = 0
)

class RouteViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = RouteRepository(application.applicationContext)
    private val fallbackGpsTracker = GpsTrackerService(application.applicationContext)

    val activeDraft: StateFlow<RouteDraft?> = repository.activeDraft
    val savedRoutes: StateFlow<List<SavedRoute>> = repository.savedRoutes
    val preferences: StateFlow<MapPreferences> = repository.preferences

    // GPS Telemetry Status
    private val _gpsStatusMessage = MutableStateFlow("Tap Navigate on any stop to auto-record GPS & speed")
    val gpsStatusMessage: StateFlow<String> = _gpsStatusMessage.asStateFlow()

    // Route whose Driver Progress page is currently open (null = activeDraft)
    private val _progressTargetRouteId = MutableStateFlow<String?>(null)
    val progressTargetRouteId: StateFlow<String?> = _progressTargetRouteId.asStateFlow()

    // Derived Driver Progress summary for active OR saved route
    val driverProgressSummary: StateFlow<DriverProgressSummary> = combine(
        activeDraft,
        savedRoutes,
        _progressTargetRouteId
    ) { draft, saved, targetId ->
        val matchedSaved = if (targetId != null) saved.find { it.id == targetId } else null
        if (matchedSaved != null) {
            buildProgressSummary(
                routeId = matchedSaved.id,
                routeName = matchedSaved.name,
                isActive = false,
                isRecording = false,
                activeNavigatingStopId = null,
                segmentCount = matchedSaved.trackSegments.size,
                isTrackCombined = matchedSaved.isTrackCombined,
                startedAt = matchedSaved.routeStartedAt,
                speedLimit = matchedSaved.speedLimitMph,
                stops = matchedSaved.stops,
                plannedPath = matchedSaved.path,
                actualTrack = matchedSaved.actualTrack,
                plannedDistance = matchedSaved.distanceMeters
            )
        } else if (draft != null) {
            buildProgressSummary(
                routeId = draft.id,
                routeName = draft.name,
                isActive = true,
                isRecording = draft.isRecordingGps,
                activeNavigatingStopId = draft.activeNavigatingStopId,
                segmentCount = draft.trackSegments.size,
                isTrackCombined = draft.isTrackCombined,
                startedAt = draft.routeStartedAt,
                speedLimit = draft.speedLimitMph,
                stops = draft.stops,
                plannedPath = draft.path,
                actualTrack = draft.actualTrack,
                plannedDistance = draft.distanceMeters
            )
        } else {
            DriverProgressSummary()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DriverProgressSummary())

    // Video Export Dialog & Live Process State
    private val _isVideoExportDialogOpen = MutableStateFlow(false)
    val isVideoExportDialogOpen: StateFlow<Boolean> = _isVideoExportDialogOpen.asStateFlow()

    private val _videoExportTargetRouteId = MutableStateFlow<String?>(null)

    private val _videoExportSettings = MutableStateFlow(VideoExportSettings())
    val videoExportSettings: StateFlow<VideoExportSettings> = _videoExportSettings.asStateFlow()

    private val _isExportingVideo = MutableStateFlow(false)
    val isExportingVideo: StateFlow<Boolean> = _isExportingVideo.asStateFlow()

    private val _videoExportProgress = MutableStateFlow(0f)
    val videoExportProgress: StateFlow<Float> = _videoExportProgress.asStateFlow()

    private val _videoExportStage = MutableStateFlow("")
    val videoExportStage: StateFlow<String> = _videoExportStage.asStateFlow()

    private val _exportedVideoResult = MutableStateFlow<ExportedVideoResult?>(null)
    val exportedVideoResult: StateFlow<ExportedVideoResult?> = _exportedVideoResult.asStateFlow()

    private val _videoExportError = MutableStateFlow<String?>(null)
    val videoExportError: StateFlow<String?> = _videoExportError.asStateFlow()

    private var videoExportJob: kotlinx.coroutines.Job? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedStop = MutableStateFlow<DeliveryStop?>(null)
    val selectedStop: StateFlow<DeliveryStop?> = _selectedStop.asStateFlow()

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing: StateFlow<Boolean> = _isOptimizing.asStateFlow()

    private val _optimizingStep = MutableStateFlow("")
    val optimizingStep: StateFlow<String> = _optimizingStep.asStateFlow()

    private val _isFetchingRoadRoute = MutableStateFlow(false)
    val isFetchingRoadRoute: StateFlow<Boolean> = _isFetchingRoadRoute.asStateFlow()

    private var optimizeJob: Job? = null
    private var roadRouteJob: Job? = null

    // Direct Shared PDF & Manifest Editor State
    private val _manifestInputText = MutableStateFlow(DeliveryParser.SAMPLE_DELIVERY_NOTE_TEXT)
    val manifestInputText: StateFlow<String> = _manifestInputText.asStateFlow()

    private val _isImportingPdf = MutableStateFlow(false)
    val isImportingPdf: StateFlow<Boolean> = _isImportingPdf.asStateFlow()

    private val _pdfImportMessage = MutableStateFlow<String?>(null)
    val pdfImportMessage: StateFlow<String?> = _pdfImportMessage.asStateFlow()

    private val _pdfImportError = MutableStateFlow<String?>(null)
    val pdfImportError: StateFlow<String?> = _pdfImportError.asStateFlow()

    private val _pendingPdfNavigation = MutableStateFlow<String?>(null)
    val pendingPdfNavigation: StateFlow<String?> = _pendingPdfNavigation.asStateFlow()

    init {
        viewModelScope.launch {
            GpsTrackingCoordinator.breadcrumbs.collect { crumb ->
                repository.appendGpsBreadcrumb(crumb)
            }
        }
        viewModelScope.launch {
            GpsTrackingCoordinator.roadLimitUpdates.collect { update ->
                repository.updateBreadcrumbRoadLimit(
                    breadcrumbId = update.breadcrumbId,
                    speedLimitMph = update.speedLimitMph,
                    roadName = update.roadName,
                    source = update.source
                )
            }
        }
        viewModelScope.launch {
            GpsTrackingCoordinator.statusMessage.collect { status ->
                if (activeDraft.value?.isRecordingGps == true) {
                    _gpsStatusMessage.value = status
                }
            }
        }
        // Upgrade default sample draft immediately from point-to-point to real road-following navigation & traffic
        refreshRoadNavigationForCurrentDraft()
    }

    val filteredStops: StateFlow<List<DeliveryStop>> = combine(activeDraft, searchQuery) { draft, query ->
        val stops = draft?.stops ?: emptyList()
        if (query.isBlank()) {
            stops
        } else {
            val q = query.trim().lowercase()
            stops.filter { stop ->
                stop.name.lowercase().contains(q) ||
                    stop.address.lowercase().contains(q) ||
                    (stop.orderId?.lowercase()?.contains(q) == true) ||
                    (stop.dropNumber?.lowercase()?.contains(q) == true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paceStats: StateFlow<DriverPaceStats> = activeDraft.map { draft ->
        val stops = draft?.stops ?: return@map DriverPaceStats()
        val total = stops.size
        val completed = stops.count { it.delivered }
        val percent = if (total > 0) completed.toFloat() / total else 0f

        val dwell = draft.defaultDwellMinutes
        val stopsPerHour = if (dwell > 0) (60.0 / (dwell + 3.0)).toInt().coerceIn(4, 25) else 15
        val remaining = (total - completed).coerceAtLeast(0)
        val remainingMins = (remaining * (dwell + 3.0)).toInt()

        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val estFinishMs = System.currentTimeMillis() + (remainingMins * 60 * 1000L)
        val estFinishStr = sdf.format(Date(estFinishMs))

        DriverPaceStats(
            completedStops = completed,
            totalStops = total,
            stopsPerHour = stopsPerHour,
            projectedFinishTime = estFinishStr,
            percentComplete = percent
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DriverPaceStats())

    private fun buildProgressSummary(
        routeId: String,
        routeName: String,
        isActive: Boolean,
        isRecording: Boolean,
        activeNavigatingStopId: String?,
        segmentCount: Int,
        isTrackCombined: Boolean,
        startedAt: String?,
        speedLimit: Float,
        stops: List<DeliveryStop>,
        plannedPath: List<Coordinate>,
        actualTrack: List<GpsBreadcrumb>,
        plannedDistance: Double
    ): DriverProgressSummary {
        val actualCoords = actualTrack.map { Coordinate(it.latitude, it.longitude) }
        val actualDist = RoutingService.calculatePathDistanceMeters(actualCoords)
        val maxSpeed = if (actualTrack.isNotEmpty()) actualTrack.maxOf { it.speedMph } else 0f
        val avgSpeed = if (actualTrack.isNotEmpty()) actualTrack.map { it.speedMph }.average().toFloat() else 0f
        val speedingEvents = actualTrack.count { it.isSpeeding || it.speedMph > it.speedLimitMph }
        val latestLimit = actualTrack.lastOrNull()?.speedLimitMph ?: speedLimit
        val latestRoad = actualTrack.lastOrNull { !it.roadName.isNullOrBlank() }?.roadName

        return DriverProgressSummary(
            routeId = routeId,
            routeName = routeName,
            isActiveRoute = isActive,
            isRecordingGps = isRecording,
            activeNavigatingStopId = activeNavigatingStopId,
            segmentCount = segmentCount,
            isTrackCombined = isTrackCombined,
            routeStartedAt = startedAt,
            speedLimitMph = latestLimit,
            latestRoadName = latestRoad,
            stops = stops,
            plannedPath = plannedPath,
            actualTrack = actualTrack,
            plannedDistanceMeters = plannedDistance,
            actualDistanceMeters = actualDist,
            maxSpeedMph = maxSpeed,
            avgSpeedMph = avgSpeed,
            speedingEventsCount = speedingEvents
        )
    }

    fun hasLocationPermission(): Boolean = fallbackGpsTracker.hasLocationPermission()

    /**
     * Auto-starts background GPS & speed recording for the given stop segment when clicking Navigate.
     */
    fun startNavigationTrackingForStop(stop: DeliveryStop): Boolean {
        val draft = activeDraft.value ?: return false
        if (!hasLocationPermission()) {
            _gpsStatusMessage.value = "Grant location permission to auto-record GPS while navigating"
            return false
        }

        val intervalSec = preferences.value.gpsIntervalSeconds.coerceIn(1, 5)
        repository.beginNavigationSegment(stop.id, stop.name)
        val startedForeground = GpsForegroundService.start(
            context = getApplication(),
            stopName = stop.name,
            intervalSeconds = intervalSec
        )
        if (!startedForeground) {
            fallbackGpsTracker.startTracking(
                scope = viewModelScope,
                intervalSeconds = intervalSec,
                onBreadcrumb = { crumb -> repository.appendGpsBreadcrumb(crumb) },
                onRoadLimitResolved = { update ->
                    repository.updateBreadcrumbRoadLimit(
                        breadcrumbId = update.breadcrumbId,
                        speedLimitMph = update.speedLimitMph,
                        roadName = update.roadName,
                        source = update.source
                    )
                },
                onStatusUpdate = { status -> _gpsStatusMessage.value = status }
            )
        }
        _gpsStatusMessage.value = "Background GPS (${intervalSec}s · OSM limits) · Navigating to ${stop.name}"
        return true
    }

    fun launchExternalNavigation(context: Context, stop: DeliveryStop) {
        val navApp = preferences.value.defaultNavApp
        val lat = stop.latitude
        val lon = stop.longitude
        val encodedAddr = Uri.encode(stop.address)

        val primaryUri = when (navApp) {
            "waze" -> if (lat != null && lon != null) {
                Uri.parse("https://waze.com/ul?ll=$lat,$lon&navigate=yes")
            } else {
                Uri.parse("https://waze.com/ul?q=$encodedAddr&navigate=yes")
            }
            "osm" -> if (lat != null && lon != null) {
                Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(stop.name)})")
            } else {
                Uri.parse("geo:0,0?q=$encodedAddr")
            }
            else -> if (lat != null && lon != null) {
                Uri.parse("google.navigation:q=$lat,$lon")
            } else {
                Uri.parse("google.navigation:q=$encodedAddr")
            }
        }

        val fallbackGeoUri = if (lat != null && lon != null) {
            Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(stop.name)})")
        } else {
            Uri.parse("geo:0,0?q=$encodedAddr")
        }

        val launchedPrimary = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, primaryUri))
        }.isSuccess

        if (!launchedPrimary) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, fallbackGeoUri))
            }
        }
    }

    fun startRouteTracking(): Boolean {
        val draft = activeDraft.value ?: return false
        val nextStop = draft.stops.firstOrNull { !it.delivered }
        return if (nextStop != null) {
            startNavigationTrackingForStop(nextStop)
        } else {
            val intervalSec = preferences.value.gpsIntervalSeconds.coerceIn(1, 5)
            if (!hasLocationPermission()) {
                _gpsStatusMessage.value = "Location permission required to record ${intervalSec}s GPS & OSM speed telemetry."
                return false
            }
            val startedForeground = GpsForegroundService.start(
                context = getApplication(),
                stopName = draft.name,
                intervalSeconds = intervalSec
            )
            if (!startedForeground) {
                fallbackGpsTracker.startTracking(
                    scope = viewModelScope,
                    intervalSeconds = intervalSec,
                    onBreadcrumb = { crumb -> repository.appendGpsBreadcrumb(crumb) },
                    onRoadLimitResolved = { update ->
                        repository.updateBreadcrumbRoadLimit(
                            breadcrumbId = update.breadcrumbId,
                            speedLimitMph = update.speedLimitMph,
                            roadName = update.roadName,
                            source = update.source
                        )
                    },
                    onStatusUpdate = { status -> _gpsStatusMessage.value = status }
                )
            }
            repository.setRouteTrackingState(started = true, recording = true)
            _gpsStatusMessage.value = "Background GPS (${intervalSec}s · OSM limits) active"
            true
        }
    }

    fun stopRouteTracking() {
        fallbackGpsTracker.stopTracking()
        GpsForegroundService.stop(getApplication())
        repository.pauseNavigationSegment()
        val draft = activeDraft.value
        _gpsStatusMessage.value = if (draft?.isTrackCombined == true) {
            "All stops done · ${draft.trackSegments.size} segments combined (${draft.actualTrack.size} pts)"
        } else {
            "GPS paused (${draft?.actualTrack?.size ?: 0} pts saved) · Tap Navigate on next stop to resume"
        }
    }

    fun setProgressTargetRoute(routeId: String?) {
        _progressTargetRouteId.value = routeId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectStop(stop: DeliveryStop?) {
        _selectedStop.value = stop
    }

    fun markStopDelivered(stopId: String) {
        val update = repository.markStopDelivered(stopId)
        _selectedStop.value?.let { current ->
            if (current.id == stopId) {
                val updated = activeDraft.value?.stops?.find { it.id == stopId }
                _selectedStop.value = updated ?: current.copy(delivered = !current.delivered)
            }
        }

        if (update.shouldStopRecording) {
            fallbackGpsTracker.stopTracking()
            GpsForegroundService.stop(getApplication())
        }

        if (update.allDeliveriesCompleted) {
            _gpsStatusMessage.value =
                "✓ All stops complete · ${update.combinedSegmentCount} GPS segment(s) joined (${update.combinedBreadcrumbCount} pts)"
        } else if (update.shouldStopRecording) {
            _gpsStatusMessage.value =
                "Delivery complete · GPS stopped. Tap Navigate on next stop to resume"
        }
    }

    fun setManifestInputText(text: String) {
        _manifestInputText.value = text
    }

    fun clearPdfImportStatus() {
        _pdfImportMessage.value = null
        _pdfImportError.value = null
    }

    fun consumePendingPdfNavigation() {
        _pendingPdfNavigation.value = null
    }

    fun importSharedPdfUri(uri: Uri) {
        if (_isImportingPdf.value) return
        viewModelScope.launch {
            _isImportingPdf.value = true
            _pdfImportError.value = null
            _pdfImportMessage.value = "Reading shared PDF manifest..."

            try {
                val extraction = PdfManifestExtractor.extractFromUri(getApplication(), uri)
                _manifestInputText.value = extraction.extractedText

                val parsedStops = DeliveryParser.parseDeliveryList(extraction.extractedText)
                val ocrNote = if (extraction.usedOcrFallback) " (scanned PDF OCR)" else ""

                if (parsedStops.isNotEmpty()) {
                    importManifestText(
                        text = extraction.extractedText,
                        sourceName = extraction.fileName,
                        customRouteName = "${extraction.fileName.substringBeforeLast('.').take(24)} (${parsedStops.size} Stops)"
                    )
                    _pdfImportMessage.value =
                        "Auto-imported ${parsedStops.size} stops from \"${extraction.fileName}\"$ocrNote"
                    _pendingPdfNavigation.value = "pre_route_review"
                } else {
                    _pdfImportMessage.value =
                        "Extracted ${extraction.pageCount} page(s) from \"${extraction.fileName}\"$ocrNote. Adjust text below if needed."
                    _pendingPdfNavigation.value = "import"
                }
            } catch (e: Exception) {
                _pdfImportError.value = e.message ?: "Unable to read shared PDF file."
                _pdfImportMessage.value = null
                _pendingPdfNavigation.value = "import"
            } finally {
                _isImportingPdf.value = false
            }
        }
    }

    fun importManifestText(
        text: String,
        sourceName: String = "Imported Delivery Note",
        customRouteName: String? = null
    ): RouteDraft {
        if (activeDraft.value?.isRecordingGps == true) {
            stopRouteTracking()
        }
        val parsed = DeliveryParser.parseDeliveryList(text)
            .map { it.copy(delivered = false, deliveredAt = null) }
        val extractedDeparture = DeliveryParser.extractDepartureTime(text)
        val draft = RouteDraft(
            id = UUID.randomUUID().toString(),
            name = customRouteName ?: "Manifest (${parsed.size} Stops)",
            sourceName = sourceName,
            createdAt = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date()),
            stops = parsed,
            path = emptyList(),
            routingMode = RoutingMode.NOT_PLANNED,
            departureStartTime = extractedDeparture ?: "09:00",
            wasDepartureAutoExtracted = extractedDeparture != null,
            defaultDwellMinutes = preferences.value.defaultDwellMinutes,
            restStopCount = preferences.value.defaultRestStopCount,
            speedLimitMph = 30f
        )
        repository.setDraft(draft)
        _gpsStatusMessage.value = "Tap Navigate on any stop to auto-record GPS & speed"
        return draft
    }

    fun updateDraftDepartureTime(time: String) {
        repository.updateDraftDepartureTime(time)
        val draft = activeDraft.value
        if (draft != null && draft.stops.count { it.latitude != null && it.longitude != null } >= 2) {
            refreshRoadNavigationForCurrentDraft()
        }
    }

    fun refreshRoadNavigationForCurrentDraft() {
        val currentDraft = activeDraft.value ?: return
        val locatedCount = currentDraft.stops.count { it.latitude != null && it.longitude != null }
        if (locatedCount < 2) return

        roadRouteJob?.cancel()
        val expectedDraftId = currentDraft.id
        val expectedModeId = currentDraft.selectedAlternativeId
        roadRouteJob = viewModelScope.launch {
            _isFetchingRoadRoute.value = true
            try {
                val roadNav = RoutingService.fetchRoadNavigationRoute(
                    stops = currentDraft.stops,
                    departureStartTime = currentDraft.departureStartTime,
                    historicalBreadcrumbs = repository.getAllHistoricalBreadcrumbs()
                )
                repository.applyRoadNavigationResult(
                    draftId = expectedDraftId,
                    expectedModeId = expectedModeId,
                    result = roadNav
                )
            } finally {
                _isFetchingRoadRoute.value = false
            }
        }
    }

    fun optimizeRoute(departureTime: String = "09:00", dwellMinutes: Double = 6.5, restStops: Int = 2) {
        val currentDraft = activeDraft.value ?: return
        optimizeJob?.cancel()
        roadRouteJob?.cancel()
        optimizeJob = viewModelScope.launch {
            _isOptimizing.value = true
            _isFetchingRoadRoute.value = true
            try {
                _optimizingStep.value = "Geocoding delivery stops..."

                val previousStops = currentDraft.stops
                val geocoded = currentDraft.stops.map { st ->
                    RoutingService.geocodeStop(st)
                }

                _optimizingStep.value = "Optimizing delivery sequence for booked slots..."
                val optimizedTour = RoutingService.preserveDeliveryStates(
                    targetStops = RoutingService.optimizeTour(geocoded, departureTime, dwellMinutes),
                    previousStops = previousStops
                )

                _optimizingStep.value = "Calculating arrival windows and laybys..."
                val scheduled = RoutingService.calculateSchedule(optimizedTour, departureTime, dwellMinutes)

                _optimizingStep.value = "Building road-following navigation route & traffic overlay..."
                val roadNav = RoutingService.fetchRoadNavigationRoute(
                    stops = scheduled,
                    departureStartTime = departureTime,
                    historicalBreadcrumbs = repository.getAllHistoricalBreadcrumbs()
                )
                val path = roadNav.path.ifEmpty {
                    scheduled.filter { it.latitude != null && it.longitude != null }
                        .map { Coordinate(it.latitude!!, it.longitude!!) }
                }
                val distance = if (roadNav.distanceMeters > 0.0) roadNav.distanceMeters else RoutingService.calculatePathDistanceMeters(path)
                val duration = if (roadNav.durationSeconds > 0.0) roadNav.durationSeconds else (distance / 416.0 * 60.0)
                val waitStops = RoutingService.generateQuietWaitStops(scheduled, restStops)

                val shortestCoords = RoutingService.optimizeShortestTour(scheduled)
                    .mapNotNull { if (it.latitude != null && it.longitude != null) Coordinate(it.latitude, it.longitude) else null }
                val shortestDist = RoutingService.calculatePathDistanceMeters(shortestCoords)
                val reverseDist = RoutingService.calculatePathDistanceMeters(
                    scheduled.mapNotNull { if (it.latitude != null && it.longitude != null) Coordinate(it.latitude, it.longitude) else null }.reversed()
                )

                val alternatives = listOf(
                    AlternativeRouteOption("fastest", "Fastest Flow", "Fastest", "Optimized by travel time", distance, duration),
                    AlternativeRouteOption("shortest", "Shortest Miles", "Shortest", "Minimizes road odometer", shortestDist, shortestDist / 390.0 * 60.0),
                    AlternativeRouteOption("reverse", "Reverse Order", "Reverse", "Runs planned stops in reverse order", reverseDist, reverseDist / 416.0 * 60.0)
                )

                val latestLiveDraft = activeDraft.value?.takeIf { it.id == currentDraft.id } ?: currentDraft
                val updatedDraft = latestLiveDraft.copy(
                    stops = RoutingService.preserveDeliveryStates(scheduled, latestLiveDraft.stops),
                    baseFastestOrderIds = scheduled.map { it.id },
                    path = path,
                    routeSteps = roadNav.steps,
                    trafficSegments = roadNav.trafficSegments,
                    trafficSummary = roadNav.trafficSummary,
                    routingMode = roadNav.routingMode,
                    distanceMeters = distance,
                    durationSeconds = duration,
                    departureStartTime = departureTime,
                    defaultDwellMinutes = dwellMinutes,
                    restStopCount = restStops,
                    waitStops = waitStops,
                    alternativeRoutes = alternatives,
                    selectedAlternativeId = "fastest"
                )

                repository.setDraft(updatedDraft)
            } finally {
                _isOptimizing.value = false
                _isFetchingRoadRoute.value = false
            }
        }
    }

    fun selectAlternative(id: String) {
        repository.switchRouteMode(id)
        refreshRoadNavigationForCurrentDraft()
    }

    fun updateStopAddress(stopId: String, newAddress: String) {
        viewModelScope.launch {
            val dummyStop = DeliveryStop(
                id = stopId,
                name = "",
                phone = "",
                address = newAddress
            )
            val geocoded = RoutingService.geocodeStop(dummyStop)
            val coord = if (geocoded.latitude != null && geocoded.longitude != null) {
                Coordinate(geocoded.latitude, geocoded.longitude)
            } else null
            repository.updateStopAddress(stopId, newAddress, coord)
            refreshRoadNavigationForCurrentDraft()
        }
    }

    fun saveDraftToHistory() {
        repository.saveActiveDraftToHistory()
    }

    fun loadSavedRouteAsActive(route: SavedRoute) {
        if (activeDraft.value?.isRecordingGps == true) {
            stopRouteTracking()
        }
        repository.loadSavedRouteIntoDraft(route)
        _gpsStatusMessage.value = when {
            route.isTrackCombined -> "All stops done · ${route.trackSegments.size} segments combined (${route.actualTrack.size} pts)"
            route.actualTrack.isNotEmpty() -> "${route.actualTrack.size} saved GPS pts · Tap Navigate to resume"
            else -> "Tap Navigate on any stop to auto-record GPS & speed"
        }
        if (route.trafficSegments.isEmpty() || route.path.size <= route.stops.size) {
            refreshRoadNavigationForCurrentDraft()
        }
    }

    fun deleteSavedRoute(id: String) {
        if (_progressTargetRouteId.value == id) {
            _progressTargetRouteId.value = null
        }
        repository.deleteSavedRoute(id)
    }

    fun updatePreferences(prefs: MapPreferences) {
        val oldInterval = preferences.value.gpsIntervalSeconds
        val newInterval = prefs.gpsIntervalSeconds.coerceIn(1, 5)
        repository.updatePreferences(prefs.copy(gpsIntervalSeconds = newInterval))

        val draft = activeDraft.value
        if (oldInterval != newInterval && draft?.isRecordingGps == true && hasLocationPermission()) {
            val activeStopName = draft.activeNavigatingStopId?.let { id ->
                draft.stops.find { it.id == id }?.name
            } ?: draft.name
            val startedForeground = GpsForegroundService.start(
                context = getApplication(),
                stopName = activeStopName,
                intervalSeconds = newInterval
            )
            if (!startedForeground) {
                fallbackGpsTracker.startTracking(
                    scope = viewModelScope,
                    intervalSeconds = newInterval,
                    onBreadcrumb = { crumb -> repository.appendGpsBreadcrumb(crumb) },
                    onRoadLimitResolved = { update ->
                        repository.updateBreadcrumbRoadLimit(
                            breadcrumbId = update.breadcrumbId,
                            speedLimitMph = update.speedLimitMph,
                            roadName = update.roadName,
                            source = update.source
                        )
                    },
                    onStatusUpdate = { status -> _gpsStatusMessage.value = status }
                )
            }
            _gpsStatusMessage.value = "Background GPS updated to ${newInterval}s interval · $activeStopName"
        }
    }

    // Video Animation Export Dialog
    fun openVideoExportDialog(targetRouteId: String? = null, preferActualTrack: Boolean = true) {
        _videoExportTargetRouteId.value = targetRouteId
        _exportedVideoResult.value = null
        _videoExportError.value = null
        val trackSize = getExportTargetTrack(targetRouteId).size
        val defaultSource = if (preferActualTrack && trackSize >= 2) "actual" else "planned"
        _videoExportSettings.value = _videoExportSettings.value.copy(videoSource = defaultSource)
        _isVideoExportDialogOpen.value = true
    }

    fun closeVideoExportDialog() {
        videoExportJob?.cancel()
        _isExportingVideo.value = false
        _isVideoExportDialogOpen.value = false
    }

    fun updateVideoExportSettings(settings: VideoExportSettings) {
        _videoExportSettings.value = settings
    }

    fun getTargetActualTrackSampleCount(): Int = getExportTargetTrack(_videoExportTargetRouteId.value).size

    private fun getExportTargetTrack(targetId: String?): List<GpsBreadcrumb> {
        val saved = if (targetId != null) savedRoutes.value.find { it.id == targetId } else null
        return saved?.actualTrack ?: activeDraft.value?.actualTrack ?: emptyList()
    }

    fun startVideoExport() {
        val targetId = _videoExportTargetRouteId.value
        val saved = if (targetId != null) savedRoutes.value.find { it.id == targetId } else null
        val draft = activeDraft.value

        val routeName = saved?.name ?: draft?.name ?: "Delivery Route"
        val stops = saved?.stops ?: draft?.stops ?: emptyList()
        val plannedPath = saved?.path ?: draft?.path ?: emptyList()
        val actualTrack = saved?.actualTrack ?: draft?.actualTrack ?: emptyList()
        val speedLimit = saved?.speedLimitMph ?: draft?.speedLimitMph ?: 30f

        _exportedVideoResult.value = null
        _videoExportError.value = null
        _isExportingVideo.value = true
        _videoExportProgress.value = 0.02f
        _videoExportStage.value = "Preparing route telemetry & video encoder..."

        videoExportJob = viewModelScope.launch {
            try {
                val result = RouteVideoExporter.exportRouteVideo(
                    context = getApplication(),
                    routeName = routeName,
                    stops = stops,
                    plannedPath = plannedPath,
                    actualTrack = actualTrack,
                    speedLimitMph = speedLimit,
                    useKmh = preferences.value.useKmh,
                    settings = _videoExportSettings.value,
                    onProgress = { pct, stage ->
                        _videoExportProgress.value = pct
                        _videoExportStage.value = stage
                    }
                )
                _exportedVideoResult.value = result
                _videoExportProgress.value = 1f
                _videoExportStage.value = "✓ Exported ${result.filename}"
                _isExportingVideo.value = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                _isExportingVideo.value = false
                _videoExportStage.value = "Video export cancelled."
            } catch (e: Exception) {
                _isExportingVideo.value = false
                _videoExportError.value = e.message ?: "Failed to encode MP4 video on this device"
                _videoExportStage.value = "Export failed"
            }
        }
    }

    fun saveExportedVideoToGallery(): Boolean {
        val file = _exportedVideoResult.value?.file ?: return false
        return RouteVideoExporter.saveVideoToDeviceGallery(getApplication(), file) != null
    }

    fun shareExportedVideo(context: Context) {
        val file = _exportedVideoResult.value?.file ?: return
        RouteVideoExporter.shareVideoFile(context, file)
    }

    override fun onCleared() {
        super.onCleared()
        fallbackGpsTracker.stopTracking()
    }
}
