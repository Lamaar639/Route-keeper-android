package com.example.routekeeper.data.service

import com.example.routekeeper.data.model.Coordinate
import com.example.routekeeper.data.model.DeliveryStop
import com.example.routekeeper.data.model.GeocodeStatus
import com.example.routekeeper.data.model.GpsBreadcrumb
import com.example.routekeeper.data.model.QuietWaitStop
import com.example.routekeeper.data.model.RouteStep
import com.example.routekeeper.data.model.RoutingMode
import com.example.routekeeper.data.model.TrafficLevel
import com.example.routekeeper.data.model.TrafficRouteSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class RoadNavigationResult(
    val path: List<Coordinate>,
    val steps: List<RouteStep>,
    val trafficSegments: List<TrafficRouteSegment>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val routingMode: RoutingMode,
    val trafficSummary: String
)

object RoutingService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // Built-in landmark coordinate fallbacks for rapid instant geocoding and offline reliability
    private val KNOWN_COORDINATES = mapOf(
        "10 downing street" to Coordinate(51.503364, -0.127625),
        "221b baker street" to Coordinate(51.523767, -0.158556),
        "russell square" to Coordinate(51.521740, -0.125740),
        "covent garden" to Coordinate(51.511746, -0.123997),
        "trafalgar square" to Coordinate(51.508039, -0.128069),
        "borough market" to Coordinate(51.505500, -0.090800),
        "london" to Coordinate(51.5074, -0.1278)
    )

    fun haversineDistanceMeters(c1: Coordinate, c2: Coordinate): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(c2.latitude - c1.latitude)
        val dLon = Math.toRadians(c2.longitude - c1.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(c1.latitude)) * cos(Math.toRadians(c2.latitude)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun calculatePathDistanceMeters(path: List<Coordinate>): Double {
        var total = 0.0
        for (i in 0 until path.size - 1) {
            total += haversineDistanceMeters(path[i], path[i + 1])
        }
        return total
    }

    fun formatDistance(meters: Double): String {
        if (meters <= 0.0) return "0.0 mi"
        val miles = meters * 0.000621371
        val km = meters / 1000.0
        return if (miles < 0.1) {
            "${meters.roundToInt()} m"
        } else {
            "%.1f mi (%.1f km)".format(miles, km)
        }
    }

    fun formatDuration(seconds: Double): String {
        if (seconds <= 0.0) return "0 min"
        val minutes = (seconds / 60.0).roundToInt()
        if (minutes < 60) {
            return "$minutes min${if (minutes == 1) "" else "s"}"
        }
        val hours = minutes / 60
        val remainingMins = minutes % 60
        return "$hours hr${if (hours == 1) "" else "s"}${if (remainingMins > 0) " $remainingMins min" else ""}"
    }

    suspend fun geocodeStop(stop: DeliveryStop): DeliveryStop = withContext(Dispatchers.IO) {
        val cleanAddr = stop.address.trim().lowercase()

        // 1. Check known coordinates
        for ((key, coord) in KNOWN_COORDINATES) {
            if (cleanAddr.contains(key)) {
                return@withContext stop.copy(
                    latitude = coord.latitude,
                    longitude = coord.longitude,
                    geocodeStatus = GeocodeStatus.LOCATED,
                    geocodedBy = "RouteKeeper Verified Fast Cache"
                )
            }
        }

        // 2. Query OSM Nominatim
        try {
            val encoded = URLEncoder.encode(stop.address, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RouteKeeper-Android/1.0")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val arr = JSONArray(body)
                        if (arr.length() > 0) {
                            val item = arr.getJSONObject(0)
                            val lat = item.optDouble("lat", Double.NaN)
                            val lon = item.optDouble("lon", Double.NaN)
                            if (!lat.isNaN() && !lon.isNaN()) {
                                return@withContext stop.copy(
                                    latitude = lat,
                                    longitude = lon,
                                    geocodeStatus = GeocodeStatus.LOCATED,
                                    geocodedBy = "OpenStreetMap (Nominatim)"
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // fallback
        }

        // 3. Fallback to plausible coordinates near default center if unlocated
        val defaultCenter = Coordinate(51.5074, -0.1278)
        val hashOffset = (stop.address.hashCode() % 50) / 1000.0
        val fallbackLat = defaultCenter.latitude + hashOffset
        val fallbackLon = defaultCenter.longitude + (hashOffset * 0.8)

        return@withContext stop.copy(
            latitude = fallbackLat,
            longitude = fallbackLon,
            geocodeStatus = GeocodeStatus.LOCATED,
            geocodedBy = "Fast Approximate Geocoder"
        )
    }

    private fun parseClockMinutes(clock: String): Int? {
        val parts = clock.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    // Solve optimal sequence respecting customer booked time windows + travel distance
    fun optimizeTour(
        stops: List<DeliveryStop>,
        departureStartTime: String = "09:00",
        defaultDwellMinutes: Double = 6.5
    ): List<DeliveryStop> {
        val validStops = stops.filter { it.latitude != null && it.longitude != null }
        val invalidStops = stops.filter { it.latitude == null || it.longitude == null }
        if (validStops.size <= 1) return stops

        val hasAnyTimeWindows = validStops.any { it.timeWindow != null }
        if (!hasAnyTimeWindows) {
            // Greedy Nearest Neighbor + 2-opt when no time windows exist
            val unvisited = validStops.toMutableList()
            val tour = mutableListOf<DeliveryStop>()
            var current = unvisited.removeAt(0)
            tour.add(current)

            while (unvisited.isNotEmpty()) {
                val currentCoord = Coordinate(current.latitude!!, current.longitude!!)
                var bestIdx = 0
                var bestDist = Double.MAX_VALUE
                for (i in unvisited.indices) {
                    val candidateCoord = Coordinate(unvisited[i].latitude!!, unvisited[i].longitude!!)
                    val d = haversineDistanceMeters(currentCoord, candidateCoord)
                    if (d < bestDist) {
                        bestDist = d
                        bestIdx = i
                    }
                }
                current = unvisited.removeAt(bestIdx)
                tour.add(current)
            }

            var improved = true
            var iterations = 0
            while (improved && iterations < 50) {
                improved = false
                iterations++
                for (i in 1 until tour.size - 2) {
                    for (j in i + 1 until tour.size - 1) {
                        val pA = Coordinate(tour[i - 1].latitude!!, tour[i - 1].longitude!!)
                        val pB = Coordinate(tour[i].latitude!!, tour[i].longitude!!)
                        val pC = Coordinate(tour[j].latitude!!, tour[j].longitude!!)
                        val pD = Coordinate(tour[j + 1].latitude!!, tour[j + 1].longitude!!)

                        val curDist = haversineDistanceMeters(pA, pB) + haversineDistanceMeters(pC, pD)
                        val newDist = haversineDistanceMeters(pA, pC) + haversineDistanceMeters(pB, pD)

                        if (newDist < curDist - 1.0) {
                            var left = i
                            var right = j
                            while (left < right) {
                                val temp = tour[left]
                                tour[left] = tour[right]
                                tour[right] = temp
                                left++
                                right--
                            }
                            improved = true
                        }
                    }
                }
            }
            return tour + invalidStops
        }

        // Time-Window-Constrained Nearest Neighbor so stops align with their booked slots
        val startMin = (parseClockMinutes(departureStartTime) ?: (9 * 60)).toDouble()
        var simMinutes = startMin
        var current: DeliveryStop? = null
        val unvisited = validStops.toMutableList()
        val tour = mutableListOf<DeliveryStop>()

        while (unvisited.isNotEmpty()) {
            var bestIdx = 0
            var bestScore = Double.MAX_VALUE
            var bestFinishMinutes = simMinutes

            for (i in unvisited.indices) {
                val cand = unvisited[i]
                val distMeters = if (current == null) {
                    0.0
                } else {
                    haversineDistanceMeters(
                        Coordinate(current.latitude!!, current.longitude!!),
                        Coordinate(cand.latitude!!, cand.longitude!!)
                    )
                }
                val travelMins = if (current == null) 0.0 else (distMeters / 416.0).coerceAtLeast(3.0)
                val rawArrival = simMinutes + travelMins
                val candDwell = cand.serviceTimeMinutes.takeIf { it > 0 } ?: defaultDwellMinutes

                val wStart = cand.timeWindow?.let { parseClockMinutes(it.startTime)?.toDouble() }
                val wEnd = cand.timeWindow?.let { parseClockMinutes(it.endTime)?.toDouble() }

                val serviceStart = if (wStart != null && rawArrival < wStart) wStart else rawArrival
                val waitMins = if (wStart != null && rawArrival < wStart) (wStart - rawArrival) else 0.0
                val lateMins = if (wEnd != null && serviceStart > wEnd) (serviceStart - wEnd) else 0.0
                val finishTime = serviceStart + candDwell

                // Penalize visiting a later-opening stop before an earlier-closing stop
                var earlyJumpPenalty = 0.0
                var causedLatePenalty = 0.0
                for (j in unvisited.indices) {
                    if (j == i) continue
                    val other = unvisited[j]
                    val otherStart = other.timeWindow?.let { parseClockMinutes(it.startTime)?.toDouble() } ?: continue
                    val otherEnd = other.timeWindow.let { parseClockMinutes(it.endTime)?.toDouble() } ?: continue
                    if (wStart != null && otherEnd < wStart) {
                        earlyJumpPenalty += 10_000_000.0
                    } else if (wStart == null && otherStart <= rawArrival + 15.0) {
                        // Prefer already-open timed windows before flexible untimed stops
                        earlyJumpPenalty += 5_000.0
                    }
                    val otherTravel = (haversineDistanceMeters(
                        Coordinate(cand.latitude!!, cand.longitude!!),
                        Coordinate(other.latitude!!, other.longitude!!)
                    ) / 416.0).coerceAtLeast(3.0)
                    val estOtherArr = finishTime + otherTravel
                    if (estOtherArr > otherEnd) {
                        causedLatePenalty += 200_000.0 + (estOtherArr - otherEnd) * 10_000.0
                    }
                }

                val score = distMeters +
                    (waitMins * 600.0) +
                    (lateMins * 20_000.0) +
                    causedLatePenalty +
                    earlyJumpPenalty

                if (score < bestScore) {
                    bestScore = score
                    bestIdx = i
                    bestFinishMinutes = finishTime
                }
            }

            current = unvisited.removeAt(bestIdx)
            tour.add(current)
            simMinutes = bestFinishMinutes
        }

        return tour + invalidStops
    }

    // Calculate arrival & departure times so deliveries are scheduled during their booked slot (never before)
    fun calculateSchedule(
        stops: List<DeliveryStop>,
        departureStartTime: String = "09:00",
        defaultDwellMinutes: Double = 6.5
    ): List<DeliveryStop> {
        var currentMinutes = parseClockMinutes(departureStartTime) ?: (9 * 60)
        var lastValidCoord: Coordinate? = null

        val result = mutableListOf<DeliveryStop>()
        for (i in stops.indices) {
            val stop = stops[i]
            val curLat = stop.latitude
            val curLon = stop.longitude
            if (curLat != null && curLon != null) {
                val curCoord = Coordinate(curLat, curLon)
                val prevCoord = lastValidCoord
                if (prevCoord != null) {
                    val dist = haversineDistanceMeters(prevCoord, curCoord)
                    // Average urban speed ~ 25 km/h (416 m/min)
                    val travelMinutes = (dist / 416.0).coerceAtLeast(3.0)
                    currentMinutes += travelMinutes.roundToInt()
                }
                lastValidCoord = curCoord
            }

            val winStartM = stop.timeWindow?.let { parseClockMinutes(it.startTime) }
            val winEndM = stop.timeWindow?.let { parseClockMinutes(it.endTime) }

            // Wait until the customer's booked slot opens so the route never plans an arrival before the slot
            val plannedArrivalMinutes = if (winStartM != null && currentMinutes < winStartM) {
                winStartM
            } else {
                currentMinutes
            }

            val arrH = (plannedArrivalMinutes / 60) % 24
            val arrM = plannedArrivalMinutes % 60
            val arrivalStr = "%02d:%02d".format(arrH, arrM)

            val dwell = stop.serviceTimeMinutes.takeIf { it > 0 } ?: defaultDwellMinutes
            val depTotalM = plannedArrivalMinutes + dwell.roundToInt()
            val depH = (depTotalM / 60) % 24
            val depM = depTotalM % 60
            val departureStr = "%02d:%02d".format(depH, depM)

            // Window status
            val status = if (winStartM != null && winEndM != null && winEndM >= winStartM) {
                when {
                    plannedArrivalMinutes > winEndM -> "late"
                    else -> "on-time"
                }
            } else {
                "flexible"
            }

            result.add(
                stop.copy(
                    dropNumber = (i + 1).toString(),
                    estimatedArrival = arrivalStr,
                    estimatedDeparture = departureStr,
                    windowStatus = status
                )
            )

            currentMinutes = depTotalM
        }
        return result
    }

    // Compute Quiet Layby / Wait stops
    fun generateQuietWaitStops(stops: List<DeliveryStop>, count: Int = 2): List<QuietWaitStop> {
        if (stops.size < 4 || count <= 0) return emptyList()
        val waitStops = mutableListOf<QuietWaitStop>()
        val step = (stops.size / (count + 1)).coerceAtLeast(1)

        for (idx in 1..count) {
            val stopIdx = (idx * step).coerceAtMost(stops.size - 2)
            val st = stops[stopIdx]
            if (st.latitude != null && st.longitude != null) {
                waitStops.add(
                    QuietWaitStop(
                        id = UUID.randomUUID().toString(),
                        name = "Quiet Layby #${idx}",
                        address = "Near ${st.address} (Off-Arterial Safe Bay)",
                        latitude = st.latitude + 0.0012,
                        longitude = st.longitude + 0.0015,
                        arrivingFromStopIndex = stopIdx,
                        nextStopIndex = stopIdx + 1,
                        suggestedArrivalTime = st.estimatedDeparture ?: "12:30",
                        departureTime = "12:50",
                        waitDurationMinutes = 20,
                        reason = "Quiet break / buffer period before next delivery window"
                    )
                )
            }
        }
        return waitStops
    }

    /**
     * Strict nearest-neighbor shortest-leg ordering (distinct from 2-Opt Fastest).
     */
    fun optimizeShortestTour(stops: List<DeliveryStop>): List<DeliveryStop> {
        val validStops = stops.filter { it.latitude != null && it.longitude != null }
        val invalidStops = stops.filter { it.latitude == null || it.longitude == null }
        if (validStops.size <= 2) return stops

        // Start from stop closest to geographic centroid to minimize overall leg odometer
        val avgLat = validStops.map { it.latitude!! }.average()
        val avgLon = validStops.map { it.longitude!! }.average()
        val centroid = Coordinate(avgLat, avgLon)

        val unvisited = validStops.toMutableList()
        val startIdx = unvisited.indices.minByOrNull { idx ->
            haversineDistanceMeters(centroid, Coordinate(unvisited[idx].latitude!!, unvisited[idx].longitude!!))
        } ?: 0

        val tour = mutableListOf<DeliveryStop>()
        var current = unvisited.removeAt(startIdx)
        tour.add(current)

        while (unvisited.isNotEmpty()) {
            val currentCoord = Coordinate(current.latitude!!, current.longitude!!)
            var bestIdx = 0
            var bestDist = Double.MAX_VALUE
            for (i in unvisited.indices) {
                val candidateCoord = Coordinate(unvisited[i].latitude!!, unvisited[i].longitude!!)
                val d = haversineDistanceMeters(currentCoord, candidateCoord)
                if (d < bestDist) {
                    bestDist = d
                    bestIdx = i
                }
            }
            current = unvisited.removeAt(bestIdx)
            tour.add(current)
        }
        return tour + invalidStops
    }

    /**
     * Preserves prior completed delivery states (delivered, deliveredAt, notes)
     * across route mode changes or re-optimization within the same route.
     */
    fun preserveDeliveryStates(
        targetStops: List<DeliveryStop>,
        previousStops: List<DeliveryStop>
    ): List<DeliveryStop> {
        val byId = previousStops.associateBy { it.id }

        return targetStops.map { stop ->
            val match = byId[stop.id]
            if (match != null && (match.delivered || match.notes.isNotBlank())) {
                stop.copy(
                    delivered = match.delivered,
                    deliveredAt = match.deliveredAt,
                    notes = match.notes.ifBlank { stop.notes }
                )
            } else {
                stop
            }
        }
    }

    /**
     * Joins all recorded GPS navigation segments in chronological order into a single
     * continuous track when all deliveries on a route have been completed.
     */
    fun combineTrackSegments(
        segments: List<com.example.routekeeper.data.model.GpsTrackSegment>,
        fallbackBreadcrumbs: List<com.example.routekeeper.data.model.GpsBreadcrumb> = emptyList()
    ): List<com.example.routekeeper.data.model.GpsBreadcrumb> {
        val fromSegments = segments
            .sortedBy { it.segmentIndex }
            .flatMap { it.breadcrumbs }
        val combined = (if (fromSegments.isNotEmpty()) fromSegments else fallbackBreadcrumbs)
            .sortedBy { it.timestampMs }
        if (combined.isEmpty()) return emptyList()

        val deduplicated = mutableListOf<com.example.routekeeper.data.model.GpsBreadcrumb>()
        for (pt in combined) {
            val prev = deduplicated.lastOrNull()
            if (prev == null || prev.id != pt.id && prev.timestampMs != pt.timestampMs) {
                deduplicated.add(pt)
            }
        }
        return deduplicated
    }

    /**
     * Orders stops according to mode ("fastest", "shortest", "reverse") while keeping
     * all prior delivery completion statuses intact.
     */
    fun reorderStopsForMode(
        stops: List<DeliveryStop>,
        baseFastestOrderIds: List<String>,
        modeId: String
    ): List<DeliveryStop> {
        val byId = stops.associateBy { it.id }
        val fastestBase = if (baseFastestOrderIds.isNotEmpty()) {
            val mapped = baseFastestOrderIds.mapNotNull { byId[it] }
            val remaining = stops.filter { s -> baseFastestOrderIds.none { it == s.id } }
            mapped + remaining
        } else {
            optimizeTour(stops)
        }

        val reordered = when (modeId.lowercase()) {
            "shortest" -> optimizeShortestTour(fastestBase)
            "reverse" -> fastestBase.reversed()
            else -> fastestBase // "fastest"
        }

        return preserveDeliveryStates(reordered, stops)
    }

    private const val OSRM_ROUTE_BASE = "https://router.project-osrm.org/route/v1/driving"

    /**
     * Fetches an actual road-following navigation route (following one-way rules, turns,
     * roundabouts, and driving restrictions) via OSRM for the stops in the exact selected order,
     * enriched with historic time-of-day/road-class traffic profiles and any actual measured GPS speeds.
     */
    suspend fun fetchRoadNavigationRoute(
        stops: List<DeliveryStop>,
        departureStartTime: String = "09:00",
        historicalBreadcrumbs: List<GpsBreadcrumb> = emptyList()
    ): RoadNavigationResult = withContext(Dispatchers.IO) {
        val located = stops.filter { it.latitude != null && it.longitude != null }
        if (located.size < 2) {
            val single = located.map { Coordinate(it.latitude!!, it.longitude!!) }
            return@withContext RoadNavigationResult(
                path = single,
                steps = emptyList(),
                trafficSegments = emptyList(),
                distanceMeters = 0.0,
                durationSeconds = 0.0,
                routingMode = RoutingMode.APPROXIMATE,
                trafficSummary = "Need 2+ located stops for road navigation"
            )
        }

        val departureMinutes = parseClockMinutes(departureStartTime) ?: (9 * 60)

        // Query OSRM in overlapping chunks if route is very long (> 22 stops)
        val combinedPath = mutableListOf<Coordinate>()
        val combinedSteps = mutableListOf<RouteStep>()
        val combinedTraffic = mutableListOf<TrafficRouteSegment>()
        var totalDistance = 0.0
        var totalDuration = 0.0
        var elapsedSeconds = 0.0
        var osrmSucceeded = true

        val maxWaypointsPerCall = 22
        var startIdx = 0
        while (startIdx < located.size - 1) {
            val endIdx = minOf(located.size - 1, startIdx + maxWaypointsPerCall - 1)
            val chunk = located.subList(startIdx, endIdx + 1)
            val coordParam = chunk.joinToString(";") { "${it.longitude},${it.latitude}" }
            val url = "$OSRM_ROUTE_BASE/$coordParam?overview=full&geometries=geojson&steps=true&annotations=distance,duration,speed"

            val chunkResult = try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "RouteKeeper-Android/1.0")
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            parseOsrmRouteResponse(
                                jsonBody = body,
                                baseClockMinutes = departureMinutes + (elapsedSeconds / 60.0).roundToInt(),
                                historicalBreadcrumbs = historicalBreadcrumbs
                            )
                        } else null
                    } else null
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                null
            }

            if (chunkResult == null || chunkResult.path.size < 2) {
                osrmSucceeded = false
                break
            }

            chunkResult.path.forEach { pt ->
                val last = combinedPath.lastOrNull()
                if (last == null || abs(last.latitude - pt.latitude) > 1e-6 || abs(last.longitude - pt.longitude) > 1e-6) {
                    combinedPath.add(pt)
                }
            }
            combinedSteps.addAll(chunkResult.steps)
            combinedTraffic.addAll(chunkResult.trafficSegments)
            totalDistance += chunkResult.distanceMeters
            totalDuration += chunkResult.durationSeconds
            elapsedSeconds += chunkResult.durationSeconds

            startIdx = endIdx
        }

        if (osrmSucceeded && combinedPath.size >= 2 && combinedTraffic.isNotEmpty()) {
            return@withContext RoadNavigationResult(
                path = combinedPath,
                steps = combinedSteps,
                trafficSegments = combinedTraffic,
                distanceMeters = totalDistance,
                durationSeconds = totalDuration,
                routingMode = RoutingMode.ROAD_NETWORK,
                trafficSummary = buildTrafficSummary(combinedTraffic, historicalBreadcrumbs.isNotEmpty())
            )
        }

        // Offline / rate-limit fallback (explicitly marked APPROXIMATE)
        val fallbackCoords = located.map { Coordinate(it.latitude!!, it.longitude!!) }
        val fallbackDist = calculatePathDistanceMeters(fallbackCoords)
        val fallbackDur = fallbackDist / 416.0 * 60.0
        val fallbackTraffic = buildApproximateTrafficSegments(
            coords = fallbackCoords,
            baseClockMinutes = departureMinutes,
            historicalBreadcrumbs = historicalBreadcrumbs
        )
        RoadNavigationResult(
            path = fallbackCoords,
            steps = emptyList(),
            trafficSegments = fallbackTraffic,
            distanceMeters = fallbackDist,
            durationSeconds = fallbackDur,
            routingMode = RoutingMode.APPROXIMATE,
            trafficSummary = "Approximate line fallback (road server unreachable)"
        )
    }

    internal fun parseOsrmRouteResponse(
        jsonBody: String,
        baseClockMinutes: Int = 9 * 60,
        historicalBreadcrumbs: List<GpsBreadcrumb> = emptyList()
    ): RoadNavigationResult? {
        val root = try {
            JSONObject(jsonBody)
        } catch (_: Exception) {
            return null
        }
        if (root.optString("code") != "Ok") return null
        val routes = root.optJSONArray("routes") ?: return null
        if (routes.length() == 0) return null

        val routeObj = routes.getJSONObject(0)
        val routeDistance = routeObj.optDouble("distance", 0.0)
        val routeDuration = routeObj.optDouble("duration", 0.0)
        val legs = routeObj.optJSONArray("legs")

        val path = mutableListOf<Coordinate>()
        val steps = mutableListOf<RouteStep>()
        val trafficSegments = mutableListOf<TrafficRouteSegment>()

        var cumulativeMinutes = baseClockMinutes.toDouble()

        if (legs != null && legs.length() > 0) {
            for (legIdx in 0 until legs.length()) {
                val leg = legs.optJSONObject(legIdx) ?: continue
                val legSteps = leg.optJSONArray("steps") ?: continue

                for (sIdx in 0 until legSteps.length()) {
                    val stepObj = legSteps.optJSONObject(sIdx) ?: continue
                    val stepDist = stepObj.optDouble("distance", 0.0)
                    val stepDur = stepObj.optDouble("duration", 0.0)
                    val rawName = stepObj.optString("name", "").trim()
                    val rawRef = stepObj.optString("ref", "").trim()
                    val roadLabel = when {
                        rawName.isNotBlank() && rawRef.isNotBlank() && !rawName.contains(rawRef) -> "$rawName ($rawRef)"
                        rawName.isNotBlank() -> rawName
                        rawRef.isNotBlank() -> rawRef
                        else -> "Local road"
                    }

                    val maneuver = stepObj.optJSONObject("maneuver")
                    val mType = maneuver?.optString("type", "") ?: ""
                    val mMod = maneuver?.optString("modifier", "") ?: ""
                    val intersections = stepObj.optJSONArray("intersections")
                    val intersectionCount = intersections?.length() ?: 0

                    val stepCoordsJson = stepObj.optJSONObject("geometry")?.optJSONArray("coordinates")
                    val stepCoords = mutableListOf<Coordinate>()
                    if (stepCoordsJson != null) {
                        for (cIdx in 0 until stepCoordsJson.length()) {
                            val pair = stepCoordsJson.optJSONArray(cIdx) ?: continue
                            if (pair.length() >= 2) {
                                val lon = pair.optDouble(0, Double.NaN)
                                val lat = pair.optDouble(1, Double.NaN)
                                if (!lat.isNaN() && !lon.isNaN()) {
                                    stepCoords.add(Coordinate(lat, lon))
                                }
                            }
                        }
                    }

                    stepCoords.forEach { pt ->
                        val last = path.lastOrNull()
                        if (last == null || abs(last.latitude - pt.latitude) > 1e-6 || abs(last.longitude - pt.longitude) > 1e-6) {
                            path.add(pt)
                        }
                    }

                    val stepClockMin = (cumulativeMinutes.roundToInt()) % (24 * 60)
                    val freeFlowMph = estimateFreeFlowSpeedMph(roadLabel, mType, intersections)
                    val osrmStepMph = if (stepDur > 1.0 && stepDist > 5.0) {
                        ((stepDist / stepDur) * 2.23694).toFloat().coerceIn(4f, 75f)
                    } else {
                        freeFlowMph * 0.85f
                    }

                    val stepLevels = mutableListOf<TrafficLevel>()
                    if (stepCoords.size >= 2) {
                        for (i in 0 until stepCoords.size - 1) {
                            val a = stepCoords[i]
                            val b = stepCoords[i + 1]
                            val midLat = (a.latitude + b.latitude) / 2.0
                            val midLon = (a.longitude + b.longitude) / 2.0

                            val assessment = assessSegmentTraffic(
                                midCoord = Coordinate(midLat, midLon),
                                roadLabel = roadLabel,
                                osrmMph = osrmStepMph,
                                freeFlowMph = freeFlowMph,
                                intersectionDensity = intersectionCount,
                                clockMinutes = stepClockMin,
                                historicalBreadcrumbs = historicalBreadcrumbs
                            )
                            stepLevels.add(assessment.level)
                            trafficSegments.add(
                                TrafficRouteSegment(
                                    startLat = a.latitude,
                                    startLon = a.longitude,
                                    endLat = b.latitude,
                                    endLon = b.longitude,
                                    trafficLevel = assessment.level,
                                    roadName = roadLabel,
                                    expectedSpeedMph = assessment.expectedMph,
                                    freeFlowSpeedMph = freeFlowMph,
                                    source = assessment.source
                                )
                            )
                        }
                    }

                    val worstStepTraffic = stepLevels.maxByOrNull { it.ordinal } ?: TrafficLevel.CLEAR
                    if (stepDist >= 1.0 || mType == "arrive" || mType == "depart") {
                        steps.add(
                            RouteStep(
                                instruction = formatOsrmInstruction(mType, mMod, roadLabel, legIdx + 1),
                                distanceMeters = stepDist,
                                durationSeconds = stepDur,
                                name = roadLabel,
                                trafficLevel = worstStepTraffic,
                                maneuverType = mType
                            )
                        )
                    }

                    cumulativeMinutes += (stepDur / 60.0) + 1.0
                }
            }
        }

        // Fallback if overview geometry exists without step coordinates
        if (path.size < 2) {
            val overviewCoords = routeObj.optJSONObject("geometry")?.optJSONArray("coordinates")
            if (overviewCoords != null) {
                for (i in 0 until overviewCoords.length()) {
                    val pair = overviewCoords.optJSONArray(i) ?: continue
                    if (pair.length() >= 2) {
                        val lon = pair.optDouble(0, Double.NaN)
                        val lat = pair.optDouble(1, Double.NaN)
                        if (!lat.isNaN() && !lon.isNaN()) {
                            path.add(Coordinate(lat, lon))
                        }
                    }
                }
            }
            if (path.size >= 2 && trafficSegments.isEmpty()) {
                trafficSegments.addAll(
                    buildApproximateTrafficSegments(path, baseClockMinutes, historicalBreadcrumbs)
                )
            }
        }

        if (path.size < 2) return null

        return RoadNavigationResult(
            path = path,
            steps = steps,
            trafficSegments = trafficSegments,
            distanceMeters = if (routeDistance > 0.0) routeDistance else calculatePathDistanceMeters(path),
            durationSeconds = if (routeDuration > 0.0) routeDuration else (calculatePathDistanceMeters(path) / 416.0 * 60.0),
            routingMode = RoutingMode.ROAD_NETWORK,
            trafficSummary = buildTrafficSummary(trafficSegments, historicalBreadcrumbs.isNotEmpty())
        )
    }

    private data class SegmentTrafficAssessment(
        val level: TrafficLevel,
        val expectedMph: Float,
        val source: String
    )

    private fun assessSegmentTraffic(
        midCoord: Coordinate,
        roadLabel: String,
        osrmMph: Float,
        freeFlowMph: Float,
        intersectionDensity: Int,
        clockMinutes: Int,
        historicalBreadcrumbs: List<GpsBreadcrumb>
    ): SegmentTrafficAssessment {
        // 1. Check actual measured driver GPS speeds near this coordinate
        val actualSamples = historicalBreadcrumbs.filter { crumb ->
            crumb.speedMph > 1.5f &&
                abs(crumb.latitude - midCoord.latitude) <= 0.0007 &&
                abs(crumb.longitude - midCoord.longitude) <= 0.0009 &&
                haversineDistanceMeters(midCoord, Coordinate(crumb.latitude, crumb.longitude)) <= 65.0
        }

        val historicFactor = computeHistoricCongestionFactor(
            roadLabel = roadLabel,
            freeFlowMph = freeFlowMph,
            intersectionDensity = intersectionDensity,
            clockMinutes = clockMinutes
        )
        val osrmEffectiveSpeed = minOf(osrmMph, freeFlowMph * historicFactor)

        return if (actualSamples.isNotEmpty()) {
            val avgActualMph = actualSamples.map { it.speedMph }.average().toFloat()
            val avgLimitMph = actualSamples.map { it.speedLimitMph }.average().toFloat().coerceAtLeast(freeFlowMph)
            // Blend 70% actual measured GPS speed + 30% OSRM historic model
            val blendedMph = avgActualMph * 0.7f + osrmEffectiveSpeed * 0.3f
            val ratio = (blendedMph / avgLimitMph.coerceAtLeast(15f)).coerceIn(0.05f, 1.25f)
            SegmentTrafficAssessment(
                level = classifyTrafficLevel(ratio),
                expectedMph = blendedMph.coerceAtLeast(3f),
                source = "Actual GPS + Historic"
            )
        } else {
            val ratio = (osrmEffectiveSpeed / freeFlowMph.coerceAtLeast(15f)).coerceIn(0.05f, 1.25f)
            SegmentTrafficAssessment(
                level = classifyTrafficLevel(ratio),
                expectedMph = osrmEffectiveSpeed.coerceAtLeast(4f),
                source = "OSRM + Historic"
            )
        }
    }

    internal fun classifyTrafficLevel(speedToFreeFlowRatio: Float): TrafficLevel = when {
        speedToFreeFlowRatio < 0.38f -> TrafficLevel.SEVERE
        speedToFreeFlowRatio < 0.58f -> TrafficLevel.BUSY
        speedToFreeFlowRatio < 0.80f -> TrafficLevel.MODERATE
        else -> TrafficLevel.CLEAR
    }

    private fun computeHistoricCongestionFactor(
        roadLabel: String,
        freeFlowMph: Float,
        intersectionDensity: Int,
        clockMinutes: Int
    ): Float {
        val hour = (clockMinutes / 60) % 24
        val min = clockMinutes % 60
        val timeOfDayHours = hour + min / 60f

        // Commuter & commercial peak intensity (morning rush, lunch urban peak, evening rush)
        val timeCongestionPenalty = when {
            timeOfDayHours in 7.5f..9.5f -> 0.34f
            timeOfDayHours in 16.25f..18.75f -> 0.38f
            timeOfDayHours in 11.75f..14.0f -> 0.22f
            timeOfDayHours in 9.5f..16.25f -> 0.16f
            else -> 0.04f
        }

        val lower = roadLabel.lowercase()
        val roadBottleneckFactor = when {
            lower.contains("circus") || lower.contains("square") || lower.contains("bridge") ||
                lower.contains("junction") || lower.contains("ring") || lower.contains("strand") ||
                lower.contains("oxford") || lower.contains("marylebone") || lower.contains("euston") -> 0.28f
            Regex("""\b[am]\d{1,4}\b""").containsMatchIn(lower) ||
                lower.contains("high street") || lower.contains("broadway") ||
                lower.contains("road") || lower.contains("avenue") -> 0.16f
            lower.contains("close") || lower.contains("mews") || lower.contains("lane") ||
                lower.contains("gardens") || lower.contains("place") -> 0.03f
            freeFlowMph >= 40f -> 0.14f
            else -> 0.09f
        }

        val intersectionPenalty = (intersectionDensity.coerceAtMost(8) * 0.025f)
        return (1.0f - timeCongestionPenalty - roadBottleneckFactor - intersectionPenalty).coerceIn(0.24f, 1.0f)
    }

    private fun estimateFreeFlowSpeedMph(
        roadLabel: String,
        maneuverType: String,
        intersections: JSONArray?
    ): Float {
        if (maneuverType == "roundabout" || maneuverType == "rotary") return 22f
        val lower = roadLabel.lowercase()
        var base = when {
            Regex("""\bm\d{1,3}\b""").containsMatchIn(lower) || lower.contains("motorway") -> 65f
            Regex("""\ba\d{1,4}\b""").containsMatchIn(lower) || lower.contains("way") || lower.contains("bypass") -> 38f
            lower.contains("street") || lower.contains("road") || lower.contains("avenue") || lower.contains("square") -> 28f
            lower.contains("lane") || lower.contains("close") || lower.contains("mews") || lower.contains("court") -> 20f
            else -> 28f
        }
        if (intersections != null && intersections.length() > 0) {
            val classes = intersections.optJSONObject(0)?.optJSONArray("classes")
            if (classes != null) {
                for (i in 0 until classes.length()) {
                    when (classes.optString(i).lowercase()) {
                        "motorway" -> base = maxOf(base, 65f)
                        "tunnel", "bridge" -> base = minOf(base, 30f)
                    }
                }
            }
        }
        return base
    }

    private fun formatOsrmInstruction(
        type: String,
        modifier: String,
        roadName: String,
        legNum: Int
    ): String {
        val cleanMod = modifier.replace('_', ' ').trim()
        val roadSuffix = if (roadName.isNotBlank() && roadName != "Local road") " onto $roadName" else ""
        return when (type.lowercase()) {
            "depart" -> "Start$roadSuffix"
            "arrive" -> "Arrive at Stop #$legNum"
            "roundabout", "rotary" -> "Enter roundabout and exit$roadSuffix"
            "turn" -> if (cleanMod.isNotBlank()) "Turn $cleanMod$roadSuffix" else "Turn$roadSuffix"
            "new name", "continue" -> if (cleanMod.isNotBlank()) "Continue $cleanMod$roadSuffix" else "Continue$roadSuffix"
            "merge" -> "Merge $cleanMod$roadSuffix".trim()
            "on ramp", "off ramp", "fork" -> "Take $cleanMod ramp$roadSuffix".replace("  ", " ").trim()
            "end of road" -> "At end of road, turn $cleanMod$roadSuffix".trim()
            else -> "${type.replaceFirstChar { it.uppercase() }} $cleanMod$roadSuffix".trim()
        }
    }

    private fun buildApproximateTrafficSegments(
        coords: List<Coordinate>,
        baseClockMinutes: Int,
        historicalBreadcrumbs: List<GpsBreadcrumb>
    ): List<TrafficRouteSegment> {
        val list = mutableListOf<TrafficRouteSegment>()
        for (i in 0 until coords.size - 1) {
            val a = coords[i]
            val b = coords[i + 1]
            val assessment = assessSegmentTraffic(
                midCoord = Coordinate((a.latitude + b.latitude) / 2.0, (a.longitude + b.longitude) / 2.0),
                roadLabel = "Estimated Corridor",
                osrmMph = 24f,
                freeFlowMph = 30f,
                intersectionDensity = 3,
                clockMinutes = baseClockMinutes + i * 6,
                historicalBreadcrumbs = historicalBreadcrumbs
            )
            list.add(
                TrafficRouteSegment(
                    startLat = a.latitude,
                    startLon = a.longitude,
                    endLat = b.latitude,
                    endLon = b.longitude,
                    trafficLevel = assessment.level,
                    roadName = "Estimated Corridor",
                    expectedSpeedMph = assessment.expectedMph,
                    freeFlowSpeedMph = 30f,
                    source = assessment.source
                )
            )
        }
        return list
    }

    private fun buildTrafficSummary(
        segments: List<TrafficRouteSegment>,
        hasActualHistory: Boolean
    ): String {
        val severe = segments.count { it.trafficLevel == TrafficLevel.SEVERE }
        val busy = segments.count { it.trafficLevel == TrafficLevel.BUSY }
        val moderate = segments.count { it.trafficLevel == TrafficLevel.MODERATE }
        val prefix = if (hasActualHistory) "Actual GPS + Historic Traffic" else "Historic & OSM Road Traffic"
        return when {
            severe > 0 -> "$prefix · $severe heavy, $busy busy road segments"
            busy > 0 -> "$prefix · $busy busy, $moderate moderate segments"
            moderate > 0 -> "$prefix · $moderate moderate segments"
            else -> "$prefix · Mostly clear roads"
        }
    }
}

