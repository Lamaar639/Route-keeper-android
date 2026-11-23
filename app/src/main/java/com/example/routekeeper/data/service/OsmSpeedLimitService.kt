package com.example.routekeeper.data.service

import com.example.routekeeper.data.model.Coordinate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class OsmRoadSpeedLimit(
    val speedLimitMph: Float,
    val roadName: String?,
    val source: String
)

object OsmSpeedLimitService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val OVERPASS_ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )

    private data class CachedLimitEntry(
        val latitude: Double,
        val longitude: Double,
        val result: OsmRoadSpeedLimit,
        val fetchedAtMs: Long
    )

    private data class InFlightLookup(
        val latitude: Double,
        val longitude: Double,
        val deferred: CompletableDeferred<OsmRoadSpeedLimit?>
    )

    private val cacheLock = Any()
    private var lastCache: CachedLimitEntry? = null
    private var lastNetworkAttemptMs: Long = 0L
    private var activeLookup: InFlightLookup? = null

    private const val CACHE_RADIUS_METERS = 45.0
    private const val CACHE_TTL_MS = 20_000L
    private const val MIN_NETWORK_QUERY_GAP_MS = 3_500L

    /**
     * Synchronous fast cache lookup so 1s-5s GPS emissions are never delayed.
     */
    fun getCachedRoadLimit(latitude: Double, longitude: Double): OsmRoadSpeedLimit? {
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            val entry = lastCache ?: return null
            if ((now - entry.fetchedAtMs) > CACHE_TTL_MS) return null
            val dist = RoutingService.haversineDistanceMeters(
                Coordinate(latitude, longitude),
                Coordinate(entry.latitude, entry.longitude)
            )
            return if (dist <= CACHE_RADIUS_METERS) entry.result else null
        }
    }

    /**
     * Fetches the OpenStreetMap road speed limit (`maxspeed` tag or road class fallback)
     * around the given GPS coordinate via Overpass API. Coalesces concurrent 1s-2s lookups
     * so rapid GPS ticks still resolve once the in-flight query finishes.
     */
    suspend fun fetchRoadSpeedLimit(latitude: Double, longitude: Double): OsmRoadSpeedLimit? =
        withContext(Dispatchers.IO) {
            getCachedRoadLimit(latitude, longitude)?.let { return@withContext it }

            var joinDeferred: CompletableDeferred<OsmRoadSpeedLimit?>? = null
            var ownDeferred: CompletableDeferred<OsmRoadSpeedLimit?>? = null
            var fallbackCached: OsmRoadSpeedLimit? = null

            synchronized(cacheLock) {
                val running = activeLookup
                if (running != null) {
                    joinDeferred = running.deferred
                } else {
                    val now = System.currentTimeMillis()
                    if ((now - lastNetworkAttemptMs) < MIN_NETWORK_QUERY_GAP_MS) {
                        fallbackCached = lastCache?.result
                    } else {
                        lastNetworkAttemptMs = now
                        val created = CompletableDeferred<OsmRoadSpeedLimit?>()
                        activeLookup = InFlightLookup(latitude, longitude, created)
                        ownDeferred = created
                    }
                }
            }

            joinDeferred?.let { return@withContext it.await() }
            if (ownDeferred == null) {
                return@withContext fallbackCached
            }

            val deferred = ownDeferred!!
            var resolved: OsmRoadSpeedLimit? = null
            try {
                val overpassQuery =
                    "[out:json][timeout:4];way(around:35,$latitude,$longitude)[\"highway\"];out tags;"
                val formBody = "data=${URLEncoder.encode(overpassQuery, "UTF-8")}"
                    .toRequestBody("application/x-www-form-urlencoded".toMediaType())

                for (endpoint in OVERPASS_ENDPOINTS) {
                    try {
                        val request = Request.Builder()
                            .url(endpoint)
                            .post(formBody)
                            .header("User-Agent", "RouteKeeper-Android/1.0")
                            .header("Accept", "application/json")
                            .build()

                        val endpointParsed = httpClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string()
                                if (!body.isNullOrBlank()) parseOverpassResponse(body) else null
                            } else {
                                null
                            }
                        }
                        if (endpointParsed != null) {
                            synchronized(cacheLock) {
                                lastCache = CachedLimitEntry(
                                    latitude = latitude,
                                    longitude = longitude,
                                    result = endpointParsed,
                                    fetchedAtMs = System.currentTimeMillis()
                                )
                            }
                            resolved = endpointParsed
                            break
                        }
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (_: Exception) {
                    }
                }

                if (resolved == null) {
                    resolved = synchronized(cacheLock) { lastCache?.result }
                }
                deferred.complete(resolved)
                resolved
            } catch (t: Throwable) {
                deferred.complete(null)
                null
            } finally {
                synchronized(cacheLock) {
                    if (activeLookup?.deferred === deferred) {
                        activeLookup = null
                    }
                }
            }
        }

    internal fun parseOverpassResponse(jsonBody: String): OsmRoadSpeedLimit? {
        val root = try {
            JSONObject(jsonBody)
        } catch (_: Exception) {
            return null
        }
        val elements = root.optJSONArray("elements") ?: return null
        if (elements.length() == 0) return null

        var bestCandidate: Pair<Int, OsmRoadSpeedLimit>? = null

        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            val tags = el.optJSONObject("tags") ?: continue
            val highway = tags.optString("highway", "").lowercase().trim()
            if (highway.isEmpty() || highway in NON_VEHICLE_HIGHWAYS) continue

            val rawMaxSpeed = tags.optString("maxspeed", "")
                .ifBlank { tags.optString("maxspeed:forward", "") }
                .ifBlank { tags.optString("maxspeed:backward", "") }
                .trim()

            val roadName = tags.optString("name", "")
                .ifBlank { tags.optString("ref", "") }
                .takeIf { it.isNotBlank() }

            val explicitLimit = parseMaxSpeedMph(rawMaxSpeed)
            val limitMph = explicitLimit ?: inferSpeedLimitFromHighwayClass(highway)
            val source = if (explicitLimit != null) {
                "OSM maxspeed"
            } else {
                "OSM ${highway.replace('_', ' ')}"
            }

            val priority = highwayPriority(highway) + (if (explicitLimit != null) 100 else 0) +
                (if (roadName != null) 10 else 0)

            val candidate = OsmRoadSpeedLimit(
                speedLimitMph = limitMph,
                roadName = roadName,
                source = source
            )
            if (bestCandidate == null || priority > bestCandidate.first) {
                bestCandidate = priority to candidate
            }
        }

        return bestCandidate?.second
    }

    internal fun parseMaxSpeedMph(raw: String): Float? {
        if (raw.isBlank()) return null
        val normalized = raw.trim().lowercase()

        when {
            normalized.contains("motorway") -> return 70f
            normalized.contains("nsl_dual") -> return 70f
            normalized.contains("nsl_single") -> return 60f
            normalized.contains("urban") -> return 30f
            normalized.contains("rural") -> return 60f
            normalized == "walk" -> return 10f
            normalized == "none" -> return 70f
        }

        val numMatch = Regex("(\\d+(?:\\.\\d+)?)").find(normalized) ?: return null
        val value = numMatch.groupValues[1].toFloatOrNull() ?: return null
        if (value <= 0f) return null

        return if (normalized.contains("mph")) {
            value
        } else {
            // Standard OSM maxspeed without unit is in km/h
            (value * 0.621371f).roundToInt().toFloat().coerceAtLeast(5f)
        }
    }

    private fun inferSpeedLimitFromHighwayClass(highway: String): Float = when (highway) {
        "motorway", "motorway_link" -> 70f
        "trunk", "trunk_link" -> 50f
        "primary", "primary_link" -> 40f
        "secondary", "secondary_link", "tertiary", "tertiary_link", "unclassified" -> 30f
        "residential", "living_street", "service" -> 20f
        else -> 30f
    }

    private fun highwayPriority(highway: String): Int = when (highway) {
        "motorway" -> 90
        "trunk" -> 85
        "primary" -> 80
        "secondary" -> 75
        "tertiary" -> 70
        "unclassified" -> 60
        "residential" -> 55
        "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link" -> 50
        "living_street" -> 45
        "service" -> 25
        else -> 10
    }

    private val NON_VEHICLE_HIGHWAYS = setOf(
        "footway",
        "cycleway",
        "path",
        "pedestrian",
        "steps",
        "bridleway",
        "corridor",
        "track"
    )
}
