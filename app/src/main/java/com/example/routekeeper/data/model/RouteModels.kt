package com.example.routekeeper.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Coordinate(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class GpsBreadcrumb(
    val id: String,
    val timestampMs: Long,
    val timeLabel: String, // e.g. "09:14:25"
    val latitude: Double,
    val longitude: Double,
    val speedMps: Float,
    val speedMph: Float,
    val speedKmh: Float,
    val speedLimitMph: Float = 30f,
    val isSpeeding: Boolean = false,
    val roadName: String? = null,
    val speedLimitSource: String = "OSM",
    val accuracyMeters: Float = 0f,
    val bearing: Float = 0f
)

@Serializable
data class GpsTrackSegment(
    val id: String,
    val stopId: String,
    val stopName: String,
    val segmentIndex: Int,
    val startedAt: String,
    val endedAt: String? = null,
    val breadcrumbs: List<GpsBreadcrumb> = emptyList()
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

enum class TrafficLevel {
    CLEAR,      // Green (#16A34A) - Free-flowing traffic
    MODERATE,   // Amber/Orange (#F59E0B) - Moderate / slowing traffic
    BUSY,       // Light Red (#EF4444) - Busy / congested traffic
    SEVERE      // Dark Red (#8B0000) - Extremely busy / standstill traffic
}

@Serializable
data class RouteStep(
    val instruction: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val name: String? = null,
    val trafficLevel: TrafficLevel = TrafficLevel.CLEAR,
    val maneuverType: String? = null
)

@Serializable
data class TrafficRouteSegment(
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val trafficLevel: TrafficLevel = TrafficLevel.CLEAR,
    val roadName: String? = null,
    val expectedSpeedMph: Float = 25f,
    val freeFlowSpeedMph: Float = 30f,
    val source: String = "OSRM + Historic Traffic"
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
    val notes: String = ""
)

@Serializable
data class AlternativeRouteOption(
    val id: String, // "fastest", "shortest", "reverse"
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
    val baseFastestOrderIds: List<String> = emptyList(),
    val path: List<Coordinate> = emptyList(),
    val routeSteps: List<RouteStep> = emptyList(),
    val trafficSegments: List<TrafficRouteSegment> = emptyList(),
    val trafficSummary: String? = null,
    val routingMode: RoutingMode = RoutingMode.NOT_PLANNED,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val departureStartTime: String = "09:00",
    val wasDepartureAutoExtracted: Boolean = false,
    val defaultDwellMinutes: Double = 6.5,
    val restStopCount: Int = 2,
    val waitStops: List<QuietWaitStop> = emptyList(),
    val alternativeRoutes: List<AlternativeRouteOption> = emptyList(),
    val selectedAlternativeId: String = "fastest",
    val trackSegments: List<GpsTrackSegment> = emptyList(),
    val activeNavigatingStopId: String? = null,
    val isTrackCombined: Boolean = false,
    val actualTrack: List<GpsBreadcrumb> = emptyList(),
    val isRouteStarted: Boolean = false,
    val isRecordingGps: Boolean = false,
    val routeStartedAt: String? = null,
    val speedLimitMph: Float = 30f
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
    val routeSteps: List<RouteStep> = emptyList(),
    val trafficSegments: List<TrafficRouteSegment> = emptyList(),
    val trafficSummary: String? = null,
    val routingMode: RoutingMode = RoutingMode.NOT_PLANNED,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val departureStartTime: String = "09:00",
    val trackSegments: List<GpsTrackSegment> = emptyList(),
    val isTrackCombined: Boolean = false,
    val actualTrack: List<GpsBreadcrumb> = emptyList(),
    val routeStartedAt: String? = null,
    val speedLimitMph: Float = 30f
)

enum class AppThemeId(
    val displayName: String,
    val subtitle: String,
    val isDark: Boolean
) {
    FOREST_PINE("Forest Pine", "Warm sage & deep pine", isDark = false),
    MIDNIGHT_DARK("Midnight Dark", "Low-glare slate & teal", isDark = true),
    OCEAN_COBALT("Cobalt Express", "Crisp ice & royal blue", isDark = false),
    SUNSET_TERRACOTTA("Warm Copper", "Cream linen & terracotta", isDark = false),
    HIGH_CONTRAST_CAB("Carbon Gold", "High-contrast cab dark", isDark = true)
}

@Serializable
data class MapPreferences(
    val markerColor: String = "#1E453B",
    val lineColor: String = "#2563EB",
    val defaultNavApp: String = "google", // "google", "waze", "osm"
    val defaultDwellMinutes: Double = 6.5,
    val defaultRestStopCount: Int = 2,
    val audioAlertsEnabled: Boolean = true,
    val gpsIntervalSeconds: Int = 5, // 1..5 seconds
    val useKmh: Boolean = false,
    val appTheme: AppThemeId = AppThemeId.FOREST_PINE
)

@Serializable
data class VideoExportSettings(
    val durationSeconds: Int = 15,
    val aspectRatio: String = "9:16", // "9:16", "16:9", "1:1"
    val cameraMode: String = "dynamic-follow", // "dynamic-follow", "overview"
    val videoSource: String = "actual", // "actual" = Actual Driven GPS + Speed, "planned" = Planned Route
    val showSpeedTelemetry: Boolean = true,
    val showVan: Boolean = true,
    val showTrail: Boolean = true,
    val showStopPins: Boolean = true,
    val showStopNames: Boolean = true,
    val showTimeWindows: Boolean = true,
    val showSpeedometer: Boolean = true,
    val showCockpitHud: Boolean = true,
    val showScrubBar: Boolean = true,
    val showWatermark: Boolean = true
)
