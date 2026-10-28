package com.example.routekeeper.data.repository

import com.example.routekeeper.data.model.AlternativeRouteOption
import com.example.routekeeper.data.model.Coordinate
import com.example.routekeeper.data.model.DeliveryStop
import com.example.routekeeper.data.model.MapPreferences
import com.example.routekeeper.data.model.ProofOfDelivery
import com.example.routekeeper.data.model.RouteDraft
import com.example.routekeeper.data.model.RoutingMode
import com.example.routekeeper.data.model.SavedRoute
import com.example.routekeeper.data.service.DeliveryParser
import com.example.routekeeper.data.service.RoutingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class RouteRepository {

    private val _activeDraft = MutableStateFlow<RouteDraft?>(null)
    val activeDraft: StateFlow<RouteDraft?> = _activeDraft.asStateFlow()

    private val _savedRoutes = MutableStateFlow<List<SavedRoute>>(emptyList())
    val savedRoutes: StateFlow<List<SavedRoute>> = _savedRoutes.asStateFlow()

    private val _preferences = MutableStateFlow(MapPreferences())
    val preferences: StateFlow<MapPreferences> = _preferences.asStateFlow()

    init {
        initSampleData()
    }

    private fun initSampleData() {
        val parsed = DeliveryParser.parseDeliveryList(DeliveryParser.SAMPLE_DELIVERY_NOTE_TEXT)
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
                geocodeStatus = com.example.routekeeper.data.model.GeocodeStatus.LOCATED
            )
        }

        val scheduledStops = RoutingService.calculateSchedule(geocodedStops, "09:00", 6.5)
        val path = scheduledStops.map { Coordinate(it.latitude!!, it.longitude!!) }
        val distance = RoutingService.calculatePathDistanceMeters(path)

        // Seed 1 active draft
        val draft = RouteDraft(
            id = "draft-active-01",
            name = "North-West Express",
            sourceName = "Central Metro Dispatch",
            createdAt = "12 Oct 2026",
            stops = scheduledStops,
            path = path,
            routingMode = RoutingMode.ROAD_NETWORK,
            distanceMeters = distance,
            durationSeconds = (distance / 416.0 * 60.0),
            departureStartTime = "09:00",
            defaultDwellMinutes = 6.5,
            restStopCount = 2,
            waitStops = RoutingService.generateQuietWaitStops(scheduledStops, 2),
            alternativeRoutes = listOf(
                AlternativeRouteOption("fastest", "Fastest Flow", "Fastest", "Optimized by travel time", distance, distance / 450.0 * 60.0),
                AlternativeRouteOption("shortest", "Shortest Miles", "Shortest", "Minimizes road odometer", distance * 0.94, distance * 0.94 / 380.0 * 60.0),
                AlternativeRouteOption("alternative", "Main Roads First", "Arterial", "Prioritizes high-capacity dual carriageways", distance * 1.08, distance * 1.08 / 480.0 * 60.0)
            )
        )
        _activeDraft.value = draft

        // Seed 1 past completed route in history
        val pastStops = scheduledStops.take(4).map { it.copy(delivered = true, deliveredAt = "09:42 AM") }
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
                durationSeconds = 1920.0
            )
        )
    }

    fun setDraft(draft: RouteDraft?) {
        _activeDraft.value = draft
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

    fun markStopDelivered(stopId: String, pod: ProofOfDelivery? = null) {
        val nowStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        _activeDraft.update { current ->
            if (current == null) return@update null
            val updated = current.stops.map { st ->
                if (st.id == stopId) {
                    val isDelivered = !st.delivered
                    st.copy(
                        delivered = isDelivered,
                        deliveredAt = if (isDelivered) nowStr else null,
                        pod = if (isDelivered) (pod ?: ProofOfDelivery(nowStr, "Handed to Recipient")) else null
                    )
                } else st
            }
            current.copy(stops = updated)
        }
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
                        geocodeStatus = if (coord != null) com.example.routekeeper.data.model.GeocodeStatus.LOCATED else st.geocodeStatus
                    )
                } else st
            }
            current.copy(stops = updated)
        }
    }

    fun saveActiveDraftToHistory() {
        val current = _activeDraft.value ?: return
        val dateStr = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        val saved = SavedRoute(
            id = UUID.randomUUID().toString(),
            name = current.name,
            sourceName = current.sourceName,
            createdAt = current.createdAt,
            savedAt = dateStr,
            stops = current.stops,
            path = current.path,
            routingMode = current.routingMode,
            distanceMeters = current.distanceMeters,
            durationSeconds = current.durationSeconds
        )
        _savedRoutes.update { listOf(saved) + it }
    }

    fun deleteSavedRoute(id: String) {
        _savedRoutes.update { it.filter { r -> r.id != id } }
    }

    fun updatePreferences(prefs: MapPreferences) {
        _preferences.value = prefs
    }
}
