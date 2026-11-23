package com.example.routekeeper.data.repository

import android.content.Context
import com.example.routekeeper.data.model.AlternativeRouteOption
import com.example.routekeeper.data.model.AppThemeId
import com.example.routekeeper.data.model.Coordinate
import com.example.routekeeper.data.model.DeliveryStop
import com.example.routekeeper.data.model.GeocodeStatus
import com.example.routekeeper.data.model.GpsBreadcrumb
import com.example.routekeeper.data.model.GpsTrackSegment
import com.example.routekeeper.data.model.MapPreferences
import com.example.routekeeper.data.model.RouteDraft
import com.example.routekeeper.data.model.RoutingMode
import com.example.routekeeper.data.model.SavedRoute
import com.example.routekeeper.data.service.DeliveryParser
import com.example.routekeeper.data.service.RoadNavigationResult
import com.example.routekeeper.data.service.RoutingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class StopCompletionUpdate(
    val stopNowDelivered: Boolean,
    val wasActiveNavigationStop: Boolean,
    val shouldStopRecording: Boolean,
    val allDeliveriesCompleted: Boolean,
    val combinedSegmentCount: Int,
    val combinedBreadcrumbCount: Int
)

class RouteRepository(context: Context? = null) {

    private val sharedPrefs = context?.applicationContext?.getSharedPreferences(
        "routekeeper_preferences",
        Context.MODE_PRIVATE
    )

    private val _activeDraft = MutableStateFlow<RouteDraft?>(null)
    val activeDraft: StateFlow<RouteDraft?> = _activeDraft.asStateFlow()

    private val _savedRoutes = MutableStateFlow<List<SavedRoute>>(emptyList())
    val savedRoutes: StateFlow<List<SavedRoute>> = _savedRoutes.asStateFlow()

    private val _preferences = MutableStateFlow(loadPersistedPreferences())
    val preferences: StateFlow<MapPreferences> = _preferences.asStateFlow()

    init {
        initSampleData()
    }

    private fun loadPersistedPreferences(): MapPreferences {
        val sp = sharedPrefs ?: return MapPreferences()
        val themeName = sp.getString("app_theme", AppThemeId.FOREST_PINE.name)
        val parsedTheme = AppThemeId.entries.firstOrNull { it.name == themeName } ?: AppThemeId.FOREST_PINE
        return MapPreferences(
            defaultNavApp = sp.getString("default_nav_app", "google") ?: "google",
            defaultDwellMinutes = sp.getFloat("default_dwell_minutes", 6.5f).toDouble(),
            defaultRestStopCount = sp.getInt("default_rest_stop_count", 2),
            audioAlertsEnabled = sp.getBoolean("audio_alerts_enabled", true),
            useKmh = sp.getBoolean("use_kmh", false),
            gpsIntervalSeconds = sp.getInt("gps_interval_seconds", 5).coerceIn(1, 5),
            appTheme = parsedTheme
        )
    }

    private fun initSampleData() {
        val parsed = DeliveryParser.parseDeliveryList(DeliveryParser.SAMPLE_DELIVERY_NOTE_TEXT)
        val extractedDeparture = DeliveryParser.extractDepartureTime(DeliveryParser.SAMPLE_DELIVERY_NOTE_TEXT)
        val startTime = extractedDeparture ?: "09:00"
        val defaultCoords = listOf(
            Coordinate(51.503364, -0.127625),
            Coordinate(51.523767, -0.158556),
            Coordinate(51.521740, -0.125740),
            Coordinate(51.511746, -0.123997),
            Coordinate(51.508039, -0.128069),
            Coordinate(51.505500, -0.090800)
        )

        val geocodedStops = parsed.mapIndexed { idx, st ->
            val c = defaultCoords.getOrElse(idx) { Coordinate(51.5074, -0.1278) }
            st.copy(
                latitude = c.latitude,
                longitude = c.longitude,
                geocodeStatus = GeocodeStatus.LOCATED
            )
        }

        val optimized = RoutingService.optimizeTour(geocodedStops, startTime, 6.5)
        val scheduledStops = RoutingService.calculateSchedule(optimized, startTime, 6.5)
        val path = scheduledStops.map { Coordinate(it.latitude!!, it.longitude!!) }
        val distance = RoutingService.calculatePathDistanceMeters(path)

        val shortestPath = RoutingService.optimizeShortestTour(scheduledStops)
            .mapNotNull { if (it.latitude != null && it.longitude != null) Coordinate(it.latitude, it.longitude) else null }
        val shortestDist = RoutingService.calculatePathDistanceMeters(shortestPath)
        val reverseDist = RoutingService.calculatePathDistanceMeters(path.reversed())

        val draft = RouteDraft(
            id = "draft-active-01",
            name = "North-West Express",
            sourceName = "Central Metro Dispatch",
            createdAt = "12 Oct 2026",
            stops = scheduledStops,
            baseFastestOrderIds = scheduledStops.map { it.id },
            path = path,
            routingMode = RoutingMode.ROAD_NETWORK,
            distanceMeters = distance,
            durationSeconds = (distance / 416.0 * 60.0),
            departureStartTime = startTime,
            wasDepartureAutoExtracted = extractedDeparture != null,
            defaultDwellMinutes = 6.5,
            restStopCount = 2,
            waitStops = RoutingService.generateQuietWaitStops(scheduledStops, 2),
            alternativeRoutes = listOf(
                AlternativeRouteOption("fastest", "Fastest Flow", "Fastest", "Optimized by travel time", distance, distance / 450.0 * 60.0),
                AlternativeRouteOption("shortest", "Shortest Miles", "Shortest", "Minimizes road odometer", shortestDist, shortestDist / 390.0 * 60.0),
                AlternativeRouteOption("reverse", "Reverse Order", "Reverse", "Runs planned stops in reverse sequence", reverseDist, reverseDist / 416.0 * 60.0)
            ),
            selectedAlternativeId = "fastest",
            speedLimitMph = 30f
        )
        _activeDraft.value = draft

        val pastStops = scheduledStops.take(4).map { it.copy(delivered = false, deliveredAt = null) }
        _savedRoutes.value = listOf(
            SavedRoute(
                id = "route-hist-01",
                name = "Southwark Courier Run",
                sourceName = "Morning Manifest B",
                createdAt = "11 Oct 2026",
                savedAt = "11 Oct 2026, 14:30",
                stops = pastStops,
                path = pastStops.map { Coordinate(it.latitude!!, it.longitude!!) },
                routingMode = RoutingMode.ROAD_NETWORK,
                distanceMeters = 8400.0,
                durationSeconds = 1920.0,
                departureStartTime = "08:30",
                speedLimitMph = 30f
            )
        )
    }

    fun setDraft(draft: RouteDraft?) {
        _activeDraft.value = draft
    }

    fun updateDraftDepartureTime(time: String) {
        _activeDraft.update { current ->
            if (current == null) return@update null
            val scheduled = if (current.stops.any { it.latitude != null }) {
                RoutingService.calculateSchedule(
                    stops = current.stops,
                    departureStartTime = time,
                    defaultDwellMinutes = current.defaultDwellMinutes
                )
            } else current.stops
            current.copy(
                departureStartTime = time,
                stops = scheduled
            )
        }
    }

    fun switchRouteMode(modeId: String) {
        _activeDraft.update { current ->
            if (current == null) return@update null
            val reordered = RoutingService.reorderStopsForMode(
                stops = current.stops,
                baseFastestOrderIds = current.baseFastestOrderIds,
                modeId = modeId
            )
            val scheduled = RoutingService.calculateSchedule(
                stops = reordered,
                departureStartTime = current.departureStartTime,
                defaultDwellMinutes = current.defaultDwellMinutes
            )
            val newPath = scheduled.mapNotNull {
                if (it.latitude != null && it.longitude != null) Coordinate(it.latitude, it.longitude) else null
            }
            val newDist = RoutingService.calculatePathDistanceMeters(newPath)
            val matchedOpt = current.alternativeRoutes.find { it.id == modeId }

            current.copy(
                stops = scheduled,
                path = newPath,
                trafficSegments = emptyList(),
                routeSteps = emptyList(),
                trafficSummary = "Updating road navigation & traffic...",
                selectedAlternativeId = modeId,
                distanceMeters = matchedOpt?.distanceMeters ?: newDist,
                durationSeconds = matchedOpt?.durationSeconds ?: (newDist / 416.0 * 60.0)
            )
        }
    }

    fun applyRoadNavigationResult(
        draftId: String,
        expectedModeId: String,
        result: RoadNavigationResult
    ) {
        _activeDraft.update { current ->
            if (current == null || current.id != draftId || current.selectedAlternativeId != expectedModeId) {
                return@update current
            }
            val updatedAlternatives = current.alternativeRoutes.map { opt ->
                if (opt.id == expectedModeId && result.distanceMeters > 0.0) {
                    opt.copy(
                        distanceMeters = result.distanceMeters,
                        durationSeconds = result.durationSeconds
                    )
                } else opt
            }
            current.copy(
                path = result.path,
                routeSteps = result.steps,
                trafficSegments = result.trafficSegments,
                trafficSummary = result.trafficSummary,
                routingMode = result.routingMode,
                distanceMeters = if (result.distanceMeters > 0.0) result.distanceMeters else current.distanceMeters,
                durationSeconds = if (result.durationSeconds > 0.0) result.durationSeconds else current.durationSeconds,
                alternativeRoutes = updatedAlternatives
            )
        }
    }

    fun getAllHistoricalBreadcrumbs(): List<GpsBreadcrumb> {
        val fromSaved = _savedRoutes.value.flatMap { it.actualTrack }
        val fromActive = _activeDraft.value?.actualTrack ?: emptyList()
        return fromSaved + fromActive
    }

    fun updateDraftStops(stops: List<DeliveryStop>) {
        _activeDraft.update { current ->
            if (current == null) return@update null
            val path = stops.filter { it.latitude != null && it.longitude != null }
                .map { Coordinate(it.latitude!!, it.longitude!!) }
            val dist = RoutingService.calculatePathDistanceMeters(path)
            current.copy(
                stops = stops,
                path = path,
                distanceMeters = dist,
                durationSeconds = dist / 416.0 * 60.0
            )
        }
    }

    fun beginNavigationSegment(stopId: String, stopName: String) {
        val nowTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _activeDraft.update { current ->
            if (current == null) return@update null

            // Close any previously open segment for a different stop
            val closedPrevious = current.trackSegments.map { seg ->
                if (seg.endedAt == null && seg.stopId != stopId) {
                    seg.copy(endedAt = nowTime)
                } else seg
            }

            val existingOpen = closedPrevious.lastOrNull { it.stopId == stopId && it.endedAt == null }
            val updatedSegments = if (existingOpen != null) {
                closedPrevious
            } else {
                closedPrevious + GpsTrackSegment(
                    id = UUID.randomUUID().toString(),
                    stopId = stopId,
                    stopName = stopName,
                    segmentIndex = closedPrevious.size + 1,
                    startedAt = nowTime
                )
            }

            current.copy(
                trackSegments = updatedSegments,
                activeNavigatingStopId = stopId,
                isRouteStarted = true,
                isRecordingGps = true,
                isTrackCombined = false,
                routeStartedAt = current.routeStartedAt ?: nowTime
            )
        }
    }

    fun pauseNavigationSegment() {
        val nowTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _activeDraft.update { current ->
            if (current == null) return@update null
            val closed = current.trackSegments.map { seg ->
                if (seg.endedAt == null) seg.copy(endedAt = nowTime) else seg
            }
            current.copy(
                trackSegments = closed,
                activeNavigatingStopId = null,
                isRecordingGps = false
            )
        }
    }

    fun markStopDelivered(stopId: String): StopCompletionUpdate {
        val nowShort = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val nowFull = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        var result = StopCompletionUpdate(
            stopNowDelivered = false,
            wasActiveNavigationStop = false,
            shouldStopRecording = false,
            allDeliveriesCompleted = false,
            combinedSegmentCount = 0,
            combinedBreadcrumbCount = 0
        )

        _activeDraft.update { current ->
            if (current == null) return@update null

            var toggledToDelivered = false
            val updatedStops = current.stops.map { st ->
                if (st.id == stopId) {
                    val isDelivered = !st.delivered
                    toggledToDelivered = isDelivered
                    st.copy(
                        delivered = isDelivered,
                        deliveredAt = if (isDelivered) nowShort else null
                    )
                } else st
            }

            val allCompleted = updatedStops.isNotEmpty() && updatedStops.all { it.delivered }
            val wasActiveStop = current.activeNavigatingStopId == stopId
            val shouldStop = toggledToDelivered && (wasActiveStop || allCompleted)

            val updatedSegments = if (shouldStop) {
                current.trackSegments.map { seg ->
                    if (seg.endedAt == null && (allCompleted || seg.stopId == stopId)) {
                        seg.copy(endedAt = nowFull)
                    } else seg
                }
            } else {
                current.trackSegments
            }

            val combinedTrack = if (allCompleted) {
                RoutingService.combineTrackSegments(updatedSegments, current.actualTrack)
            } else {
                current.actualTrack
            }

            result = StopCompletionUpdate(
                stopNowDelivered = toggledToDelivered,
                wasActiveNavigationStop = wasActiveStop,
                shouldStopRecording = shouldStop,
                allDeliveriesCompleted = allCompleted,
                combinedSegmentCount = updatedSegments.size,
                combinedBreadcrumbCount = combinedTrack.size
            )

            current.copy(
                stops = updatedStops,
                trackSegments = updatedSegments,
                activeNavigatingStopId = if (shouldStop) null else current.activeNavigatingStopId,
                isRecordingGps = if (shouldStop) false else current.isRecordingGps,
                isTrackCombined = allCompleted,
                actualTrack = combinedTrack
            )
        }

        return result
    }

    fun updateStopAddress(stopId: String, newAddress: String, coord: Coordinate?) {
        _activeDraft.update { current ->
            if (current == null) return@update null
            val updated = current.stops.map { st ->
                if (st.id == stopId) {
                    st.copy(
                        address = newAddress,
                        latitude = coord?.latitude ?: st.latitude,
                        longitude = coord?.longitude ?: st.longitude,
                        geocodeStatus = if (coord != null) GeocodeStatus.LOCATED else st.geocodeStatus
                    )
                } else st
            }
            current.copy(stops = updated)
        }
    }

    fun setRouteTrackingState(started: Boolean, recording: Boolean) {
        val nowTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _activeDraft.update { current ->
            if (current == null) return@update null
            val closedSegments = if (!recording) {
                current.trackSegments.map { seg ->
                    if (seg.endedAt == null) seg.copy(endedAt = nowTime) else seg
                }
            } else {
                current.trackSegments
            }
            current.copy(
                isRouteStarted = started,
                isRecordingGps = recording,
                trackSegments = closedSegments,
                activeNavigatingStopId = if (!recording) null else current.activeNavigatingStopId,
                routeStartedAt = current.routeStartedAt ?: if (started) nowTime else null
            )
        }
    }

    fun appendGpsBreadcrumb(crumb: GpsBreadcrumb) {
        val nowTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _activeDraft.update { current ->
            if (current == null) return@update null

            val activeStopId = current.activeNavigatingStopId
            val openSegmentIndex = when {
                activeStopId != null -> current.trackSegments.indexOfLast { it.stopId == activeStopId && it.endedAt == null }
                else -> current.trackSegments.indexOfLast { it.endedAt == null }
            }

            val updatedSegments = if (openSegmentIndex >= 0) {
                current.trackSegments.mapIndexed { idx, seg ->
                    if (idx == openSegmentIndex) {
                        seg.copy(breadcrumbs = seg.breadcrumbs + crumb)
                    } else seg
                }
            } else {
                val label = activeStopId?.let { id ->
                    current.stops.find { it.id == id }?.name
                } ?: "Route Leg #${current.trackSegments.size + 1}"
                current.trackSegments + GpsTrackSegment(
                    id = UUID.randomUUID().toString(),
                    stopId = activeStopId ?: "manual",
                    stopName = label,
                    segmentIndex = current.trackSegments.size + 1,
                    startedAt = nowTime,
                    breadcrumbs = listOf(crumb)
                )
            }

            val liveCombinedTrack = RoutingService.combineTrackSegments(
                segments = updatedSegments,
                fallbackBreadcrumbs = current.actualTrack + crumb
            )

            current.copy(
                speedLimitMph = crumb.speedLimitMph,
                trackSegments = updatedSegments,
                actualTrack = liveCombinedTrack
            )
        }
    }

    fun updateBreadcrumbRoadLimit(
        breadcrumbId: String,
        speedLimitMph: Float,
        roadName: String?,
        source: String
    ) {
        _activeDraft.update { current ->
            if (current == null) return@update null
            val targetCrumb = current.actualTrack.find { it.id == breadcrumbId }
                ?: current.trackSegments.asSequence().flatMap { it.breadcrumbs.asSequence() }.find { it.id == breadcrumbId }
            val targetCoord = targetCrumb?.let { Coordinate(it.latitude, it.longitude) }

            fun shouldUpdate(b: GpsBreadcrumb): Boolean {
                if (b.id == breadcrumbId) return true
                if (b.speedLimitSource == "OSM default" && targetCoord != null) {
                    val dist = RoutingService.haversineDistanceMeters(
                        targetCoord,
                        Coordinate(b.latitude, b.longitude)
                    )
                    return dist <= 45.0
                }
                return false
            }

            val updatedSegments = current.trackSegments.map { seg ->
                seg.copy(
                    breadcrumbs = seg.breadcrumbs.map { b ->
                        if (shouldUpdate(b)) {
                            b.copy(
                                speedLimitMph = speedLimitMph,
                                isSpeeding = b.speedMph > speedLimitMph,
                                roadName = roadName ?: b.roadName,
                                speedLimitSource = source
                            )
                        } else b
                    }
                )
            }
            val updatedTrack = current.actualTrack.map { b ->
                if (shouldUpdate(b)) {
                    b.copy(
                        speedLimitMph = speedLimitMph,
                        isSpeeding = b.speedMph > speedLimitMph,
                        roadName = roadName ?: b.roadName,
                        speedLimitSource = source
                    )
                } else b
            }
            current.copy(
                speedLimitMph = speedLimitMph,
                trackSegments = updatedSegments,
                actualTrack = updatedTrack
            )
        }
    }

    fun clearActiveTrack() {
        _activeDraft.update { current ->
            current?.copy(
                trackSegments = emptyList(),
                actualTrack = emptyList(),
                activeNavigatingStopId = null,
                isTrackCombined = false
            )
        }
    }


    fun saveActiveDraftToHistory() {
        val current = _activeDraft.value ?: return
        val dateStr = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        val finalTrack = if (current.trackSegments.isNotEmpty()) {
            RoutingService.combineTrackSegments(current.trackSegments, current.actualTrack)
        } else current.actualTrack

        val saved = SavedRoute(
            id = UUID.randomUUID().toString(),
            name = current.name,
            sourceName = current.sourceName,
            createdAt = current.createdAt,
            savedAt = dateStr,
            stops = current.stops,
            path = current.path,
            routeSteps = current.routeSteps,
            trafficSegments = current.trafficSegments,
            trafficSummary = current.trafficSummary,
            routingMode = current.routingMode,
            distanceMeters = current.distanceMeters,
            durationSeconds = current.durationSeconds,
            departureStartTime = current.departureStartTime,
            trackSegments = current.trackSegments,
            isTrackCombined = current.isTrackCombined || (current.stops.isNotEmpty() && current.stops.all { it.delivered }),
            actualTrack = finalTrack,
            routeStartedAt = current.routeStartedAt,
            speedLimitMph = current.speedLimitMph
        )
        _savedRoutes.update { listOf(saved) + it }
    }

    fun loadSavedRouteIntoDraft(saved: SavedRoute) {
        val fastestIds = saved.stops.map { it.id }
        val path = saved.path.ifEmpty {
            saved.stops.mapNotNull {
                if (it.latitude != null && it.longitude != null) Coordinate(it.latitude, it.longitude) else null
            }
        }
        val shortestDist = RoutingService.calculatePathDistanceMeters(
            RoutingService.optimizeShortestTour(saved.stops).mapNotNull {
                if (it.latitude != null && it.longitude != null) Coordinate(it.latitude, it.longitude) else null
            }
        )
        val reverseDist = RoutingService.calculatePathDistanceMeters(path.reversed())

        _activeDraft.value = RouteDraft(
            id = saved.id,
            name = saved.name,
            sourceName = saved.sourceName,
            createdAt = saved.createdAt,
            stops = saved.stops,
            baseFastestOrderIds = fastestIds,
            path = path,
            routeSteps = saved.routeSteps,
            trafficSegments = saved.trafficSegments,
            trafficSummary = saved.trafficSummary,
            routingMode = saved.routingMode,
            distanceMeters = saved.distanceMeters,
            durationSeconds = saved.durationSeconds,
            departureStartTime = saved.departureStartTime,
            wasDepartureAutoExtracted = true,
            alternativeRoutes = listOf(
                AlternativeRouteOption("fastest", "Fastest Flow", "Fastest", "Optimized by travel time", saved.distanceMeters, saved.durationSeconds),
                AlternativeRouteOption("shortest", "Shortest Miles", "Shortest", "Minimizes road odometer", shortestDist, shortestDist / 390.0 * 60.0),
                AlternativeRouteOption("reverse", "Reverse Order", "Reverse", "Runs planned stops in reverse sequence", reverseDist, reverseDist / 416.0 * 60.0)
            ),
            selectedAlternativeId = "fastest",
            trackSegments = saved.trackSegments,
            isTrackCombined = saved.isTrackCombined,
            actualTrack = saved.actualTrack,
            routeStartedAt = saved.routeStartedAt,
            speedLimitMph = saved.speedLimitMph
        )
    }

    fun deleteSavedRoute(id: String) {
        _savedRoutes.update { it.filter { r -> r.id != id } }
    }

    fun updatePreferences(prefs: MapPreferences) {
        val sanitized = prefs.copy(gpsIntervalSeconds = prefs.gpsIntervalSeconds.coerceIn(1, 5))
        _preferences.value = sanitized
        sharedPrefs?.edit()
            ?.putString("default_nav_app", sanitized.defaultNavApp)
            ?.putFloat("default_dwell_minutes", sanitized.defaultDwellMinutes.toFloat())
            ?.putInt("default_rest_stop_count", sanitized.defaultRestStopCount)
            ?.putBoolean("audio_alerts_enabled", sanitized.audioAlertsEnabled)
            ?.putBoolean("use_kmh", sanitized.useKmh)
            ?.putInt("gps_interval_seconds", sanitized.gpsIntervalSeconds)
            ?.putString("app_theme", sanitized.appTheme.name)
            ?.apply()
    }
}
