package com.example.routekeeper.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Coordinate(
    val latitude: Double,
    val longitude: Double
)

enum class GeocodeStatus {
    PENDING,
    LOCATED,
    NOT_FOUND
}

enum class RoutingMode {
    NOT_PLANNED,
    ROAD_NETWORK,
    APPROXIMATE
}

@Serializable
data class RouteStep(
    val instruction: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val name: String? = null
)

@Serializable
data class DeliveryTimeWindow(
    val startTime: String, // e.g. "09:00"
    val endTime: String,   // e.g. "10:00" or "13:00"
    val durationHours: Int = 1,
    val label: String? = null
)

@Serializable
data class QuietWaitStop(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val trafficProfile: String = "quiet-side-street",
    val arrivingFromStopIndex: Int,
    val nextStopIndex: Int,
    val suggestedArrivalTime: String,
    val departureTime: String,
    val waitDurationMinutes: Int,
    val arterialAccessMinutes: Int = 2,
    val reason: String
)

@Serializable
data class ProofOfDelivery(
    val timestamp: String,
    val disposition: String, // e.g. "Handed to Recipient", "Front Porch", "Safe Place", "Neighbor", "Gate Locked"
    val signatureCaptured: Boolean = false,
    val photoCaptured: Boolean = false,
    val notes: String = ""
)

@Serializable
data class DeliveryStop(
    val id: String,
    val name: String,
    val phone: String,
    val address: String,
    val orderId: String? = null,
    val sourceSequence: Int = 0,
    val dropNumber: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geocodeStatus: GeocodeStatus = GeocodeStatus.PENDING,
    val geocodeError: String? = null,
    val geocodedBy: String? = null,
    val delivered: Boolean = false,
    val deliveredAt: String? = null,
    val timeWindow: DeliveryTimeWindow? = null,
    val serviceTimeMinutes: Double = 6.5,
    val estimatedArrival: String? = null,
    val estimatedDeparture: String? = null,
    val windowStatus: String? = null, // "on-time", "early", "late", "flexible"
    val pod: ProofOfDelivery? = null,
    val notes: String = ""
)

@Serializable
data class AlternativeRouteOption(
    val id: String, // "fastest", "shortest", "alternative"
    val title: String,
    val badge: String,
    val description: String,
    val distanceMeters: Double,
    val durationSeconds: Double
)

@Serializable
data class RouteDraft(
    val id: String,
    val name: String,
    val sourceName: String,
    val createdAt: String,
    val stops: List<DeliveryStop> = emptyList(),
    val path: List<Coordinate> = emptyList(),
    val routingMode: RoutingMode = RoutingMode.NOT_PLANNED,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val departureStartTime: String = "09:00",
    val defaultDwellMinutes: Double = 6.5,
    val restStopCount: Int = 2,
    val waitStops: List<QuietWaitStop> = emptyList(),
    val alternativeRoutes: List<AlternativeRouteOption> = emptyList(),
    val selectedAlternativeId: String = "fastest"
)

@Serializable
data class SavedRoute(
    val id: String,
    val name: String,
    val sourceName: String,
    val createdAt: String,
    val savedAt: String,
    val stops: List<DeliveryStop> = emptyList(),
    val path: List<Coordinate> = emptyList(),
    val routingMode: RoutingMode = RoutingMode.NOT_PLANNED,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Double = 0.0
)

@Serializable
data class MapPreferences(
    val markerColor: String = "#1E453B",
    val lineColor: String = "#2563EB",
    val defaultNavApp: String = "google", // "google", "waze", "osm"
    val defaultDwellMinutes: Double = 6.5,
    val defaultRestStopCount: Int = 2,
    val audioAlertsEnabled: Boolean = true
)
