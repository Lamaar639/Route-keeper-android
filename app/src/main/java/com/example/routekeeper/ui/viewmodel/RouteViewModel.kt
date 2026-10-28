package com.example.routekeeper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.routekeeper.data.model.AlternativeRouteOption
import com.example.routekeeper.data.model.Coordinate
import com.example.routekeeper.data.model.DeliveryStop
import com.example.routekeeper.data.model.MapPreferences
import com.example.routekeeper.data.model.ProofOfDelivery
import com.example.routekeeper.data.model.RouteDraft
import com.example.routekeeper.data.model.RoutingMode
import com.example.routekeeper.data.model.SavedRoute
import com.example.routekeeper.data.repository.RouteRepository
import com.example.routekeeper.data.service.DeliveryParser
import com.example.routekeeper.data.service.RoutingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

class RouteViewModel(
    private val repository: RouteRepository = RouteRepository()
) : ViewModel() {

    val activeDraft: StateFlow<RouteDraft?> = repository.activeDraft
    val savedRoutes: StateFlow<List<SavedRoute>> = repository.savedRoutes
    val preferences: StateFlow<MapPreferences> = repository.preferences

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedStop = MutableStateFlow<DeliveryStop?>(null)
    val selectedStop: StateFlow<DeliveryStop?> = _selectedStop.asStateFlow()

    private val _podSheetStop = MutableStateFlow<DeliveryStop?>(null)
    val podSheetStop: StateFlow<DeliveryStop?> = _podSheetStop.asStateFlow()

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing: StateFlow<Boolean> = _isOptimizing.asStateFlow()

    private val _optimizingStep = MutableStateFlow("")
    val optimizingStep: StateFlow<String> = _optimizingStep.asStateFlow()

    // Filtered stops for instant search quick-finder
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

    // Driver Pace HUD calculations
    val paceStats: StateFlow<DriverPaceStats> = activeDraft.combine(_searchQuery) { draft, _ ->
        val stops = draft?.stops ?: return@combine DriverPaceStats()
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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectStop(stop: DeliveryStop?) {
        _selectedStop.value = stop
    }

    fun openPodSheet(stop: DeliveryStop) {
        _podSheetStop.value = stop
    }

    fun closePodSheet() {
        _podSheetStop.value = null
    }

    fun markStopDelivered(stopId: String, pod: ProofOfDelivery? = null) {
        repository.markStopDelivered(stopId, pod)
        // Also update selected stop if currently viewing
        _selectedStop.value?.let { current ->
            if (current.id == stopId) {
                _selectedStop.value = current.copy(
                    delivered = !current.delivered,
                    pod = pod
                )
            }
        }
    }

    fun importManifestText(text: String, sourceName: String = "Imported Delivery Note"): RouteDraft {
        val parsed = DeliveryParser.parseDeliveryList(text)
        val draft = RouteDraft(
            id = UUID.randomUUID().toString(),
            name = "Manifest (${parsed.size} Stops)",
            sourceName = sourceName,
            createdAt = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date()),
            stops = parsed,
            path = emptyList(),
            routingMode = RoutingMode.NOT_PLANNED
        )
        repository.setDraft(draft)
        return draft
    }

    fun optimizeRoute(departureTime: String = "09:00", dwellMinutes: Double = 6.5, restStops: Int = 2) {
        val currentDraft = activeDraft.value ?: return
        viewModelScope.launch {
            _isOptimizing.value = true
            _optimizingStep.value = "Geocoding delivery stops..."

            // Geocode all stops
            val geocoded = currentDraft.stops.map { st ->
                RoutingService.geocodeStop(st)
            }

            _optimizingStep.value = "Optimizing delivery sequence (2-Opt TSP)..."
            val optimizedTour = RoutingService.optimizeTour(geocoded)

            _optimizingStep.value = "Calculating arrival windows and laybys..."
            val scheduled = RoutingService.calculateSchedule(optimizedTour, departureTime, dwellMinutes)
            val path = scheduled.filter { it.latitude != null && it.longitude != null }
                .map { Coordinate(it.latitude!!, it.longitude!!) }
            val distance = RoutingService.calculatePathDistanceMeters(path)
            val duration = distance / 416.0 * 60.0
            val waitStops = RoutingService.generateQuietWaitStops(scheduled, restStops)

            val alternatives = listOf(
                AlternativeRouteOption("fastest", "Fastest Flow", "Fastest", "Optimized by travel time", distance, duration),
                AlternativeRouteOption("shortest", "Shortest Path", "Shortest", "Minimizes road odometer", distance * 0.94, duration * 1.05),
                AlternativeRouteOption("alternative", "Main Roads First", "Arterial", "Prioritizes wide dual carriageways", distance * 1.08, duration * 0.96)
            )

            val updatedDraft = currentDraft.copy(
                stops = scheduled,
                path = path,
                routingMode = RoutingMode.ROAD_NETWORK,
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
            _isOptimizing.value = false
        }
    }

    fun selectAlternative(id: String) {
        val draft = activeDraft.value ?: return
        val opt = draft.alternativeRoutes.find { it.id == id } ?: return
        repository.setDraft(
            draft.copy(
                selectedAlternativeId = id,
                distanceMeters = opt.distanceMeters,
                durationSeconds = opt.durationSeconds
            )
        )
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
        }
    }

    fun saveDraftToHistory() {
        repository.saveActiveDraftToHistory()
    }

    fun deleteSavedRoute(id: String) {
        repository.deleteSavedRoute(id)
    }

    fun updatePreferences(prefs: MapPreferences) {
        repository.updatePreferences(prefs)
    }
}
