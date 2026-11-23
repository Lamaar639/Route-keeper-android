package com.example.routekeeper.data.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.routekeeper.data.model.Coordinate
import com.example.routekeeper.data.model.GpsBreadcrumb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class RoadLimitUpdate(
    val breadcrumbId: String,
    val speedLimitMph: Float,
    val roadName: String?,
    val source: String
)

class GpsTrackerService(private val context: Context) {

    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var activeListener: LocationListener? = null
    private var samplerJob: Job? = null
    private var latestLocation: Location? = null
    private var previousSampledLocation: Location? = null
    private var lastEmitTimeMs: Long = 0L

    @Volatile
    private var latestKnownOsmLimit: OsmRoadSpeedLimit = OsmRoadSpeedLimit(
        speedLimitMph = 30f,
        roadName = null,
        source = "OSM default"
    )

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun startTracking(
        scope: CoroutineScope,
        intervalSeconds: Int = 5,
        onBreadcrumb: (GpsBreadcrumb) -> Unit,
        onRoadLimitResolved: (RoadLimitUpdate) -> Unit = {},
        onStatusUpdate: (String) -> Unit
    ): Boolean {
        val safeIntervalSec = intervalSeconds.coerceIn(1, 5)
        val intervalMs = safeIntervalSec * 1000L
        val minEmitGapMs = (intervalMs - 200L).coerceAtLeast(750L)

        if (!hasLocationPermission()) {
            onStatusUpdate("Location permission required to record ${safeIntervalSec}s GPS & OSM speed telemetry.")
            return false
        }

        stopTracking()

        val manager = locationManager ?: run {
            onStatusUpdate("Location service unavailable on this device.")
            return false
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                latestLocation = location
                val now = System.currentTimeMillis()
                if (lastEmitTimeMs == 0L || (now - lastEmitTimeMs) >= minEmitGapMs) {
                    emitBreadcrumb(
                        scope = scope,
                        location = location,
                        nowMs = now,
                        isFreshFix = true,
                        onBreadcrumb = onBreadcrumb,
                        onRoadLimitResolved = onRoadLimitResolved
                    )
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        activeListener = listener

        var anyProviderEnabled = false
        try {
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    intervalMs,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
                anyProviderEnabled = true
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }

        try {
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    intervalMs,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
                anyProviderEnabled = true
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }

        // Seed from recent last known location (<= 2 min old) so initial point appears promptly
        try {
            val nowMs = System.currentTimeMillis()
            val lastGps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val seed = when {
                lastGps != null && lastNet != null -> if (lastGps.time >= lastNet.time) lastGps else lastNet
                lastGps != null -> lastGps
                else -> lastNet
            }
            if (seed != null && (nowMs - seed.time) <= 120_000L) {
                latestLocation = seed
                val seedIsFreshSpeed = (nowMs - seed.time) <= 10_000L
                emitBreadcrumb(
                    scope = scope,
                    location = seed,
                    nowMs = nowMs,
                    isFreshFix = seedIsFreshSpeed,
                    onBreadcrumb = onBreadcrumb,
                    onRoadLimitResolved = onRoadLimitResolved
                )
            }
        } catch (_: SecurityException) {
        }

        if (anyProviderEnabled) {
            onStatusUpdate("Recording live GPS & OSM speed limits every ${safeIntervalSec}s")
        } else {
            onStatusUpdate("Waiting for GPS provider signal (${safeIntervalSec}s)...")
        }

        // Periodic heartbeat matching the user-configured 1s..5s GPS logging interval
        samplerJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                val now = System.currentTimeMillis()
                val loc = latestLocation ?: try {
                    manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                } catch (_: SecurityException) {
                    null
                }

                if (loc != null && (now - lastEmitTimeMs) >= minEmitGapMs) {
                    val isNewLocObject = loc !== previousSampledLocation
                    val isRecent = (now - loc.time) <= (intervalMs * 2 + 2_000L)
                    emitBreadcrumb(
                        scope = scope,
                        location = loc,
                        nowMs = now,
                        isFreshFix = isNewLocObject && isRecent,
                        onBreadcrumb = onBreadcrumb,
                        onRoadLimitResolved = onRoadLimitResolved
                    )
                }
            }
        }

        return true
    }

    private fun emitBreadcrumb(
        scope: CoroutineScope,
        location: Location,
        nowMs: Long,
        isFreshFix: Boolean,
        onBreadcrumb: (GpsBreadcrumb) -> Unit,
        onRoadLimitResolved: (RoadLimitUpdate) -> Unit
    ) {
        val prev = previousSampledLocation
        val dtSeconds = if (lastEmitTimeMs > 0L) ((nowMs - lastEmitTimeMs) / 1000.0) else 0.0

        val computedMps: Float = when {
            !isFreshFix && location === prev -> 0f
            isFreshFix && location.hasSpeed() && location.speed >= 0f -> location.speed
            prev != null && dtSeconds >= 0.8 -> {
                val distM = RoutingService.haversineDistanceMeters(
                    Coordinate(prev.latitude, prev.longitude),
                    Coordinate(location.latitude, location.longitude)
                )
                (distM / dtSeconds).toFloat().coerceIn(0f, 65f)
            }
            else -> 0f
        }

        val speedMph = (computedMps * 2.23694f)
        val speedKmh = (computedMps * 3.6f)
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(nowMs))

        val cachedOsm = OsmSpeedLimitService.getCachedRoadLimit(location.latitude, location.longitude)
        val initialOsm = cachedOsm ?: latestKnownOsmLimit
        if (cachedOsm != null) {
            latestKnownOsmLimit = cachedOsm
        }

        val crumbId = UUID.randomUUID().toString()
        val crumb = GpsBreadcrumb(
            id = crumbId,
            timestampMs = nowMs,
            timeLabel = timeStr,
            latitude = location.latitude,
            longitude = location.longitude,
            speedMps = computedMps,
            speedMph = speedMph,
            speedKmh = speedKmh,
            speedLimitMph = initialOsm.speedLimitMph,
            isSpeeding = speedMph > initialOsm.speedLimitMph,
            roadName = initialOsm.roadName,
            speedLimitSource = initialOsm.source,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else 0f,
            bearing = if (location.hasBearing()) location.bearing else 0f
        )

        previousSampledLocation = location
        lastEmitTimeMs = nowMs
        onBreadcrumb(crumb)

        // If spatial cache missed/expired, asynchronously query OpenStreetMap Overpass for this road
        if (cachedOsm == null) {
            scope.launch(Dispatchers.IO) {
                val fetched = OsmSpeedLimitService.fetchRoadSpeedLimit(location.latitude, location.longitude)
                if (fetched != null) {
                    latestKnownOsmLimit = fetched
                    onRoadLimitResolved(
                        RoadLimitUpdate(
                            breadcrumbId = crumbId,
                            speedLimitMph = fetched.speedLimitMph,
                            roadName = fetched.roadName,
                            source = fetched.source
                        )
                    )
                }
            }
        }
    }

    fun stopTracking() {
        samplerJob?.cancel()
        samplerJob = null
        activeListener?.let { listener ->
            try {
                locationManager?.removeUpdates(listener)
            } catch (_: Exception) {
            }
        }
        activeListener = null
        lastEmitTimeMs = 0L
        previousSampledLocation = null
    }
}
