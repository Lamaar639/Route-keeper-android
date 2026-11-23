package com.example.routekeeper.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.routekeeper.MainActivity
import com.example.routekeeper.R
import com.example.routekeeper.data.model.GpsBreadcrumb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object GpsTrackingCoordinator {
    private val _breadcrumbs = MutableSharedFlow<GpsBreadcrumb>(extraBufferCapacity = 128)
    val breadcrumbs: SharedFlow<GpsBreadcrumb> = _breadcrumbs.asSharedFlow()

    private val _roadLimitUpdates = MutableSharedFlow<RoadLimitUpdate>(extraBufferCapacity = 128)
    val roadLimitUpdates: SharedFlow<RoadLimitUpdate> = _roadLimitUpdates.asSharedFlow()

    private val _statusMessage = MutableStateFlow("Tap Navigate on a stop to auto-record GPS & OSM road speed")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    @Volatile
    var currentSpeedLimitMph: Float = 30f

    @Volatile
    var currentRoadName: String? = null

    @Volatile
    var currentIntervalSeconds: Int = 5

    @Volatile
    var currentStopLabel: String = "Active Delivery"

    fun publishBreadcrumb(crumb: GpsBreadcrumb) {
        currentSpeedLimitMph = crumb.speedLimitMph
        if (!crumb.roadName.isNullOrBlank()) {
            currentRoadName = crumb.roadName
        }
        _breadcrumbs.tryEmit(crumb)
    }

    fun publishRoadLimitUpdate(update: RoadLimitUpdate) {
        currentSpeedLimitMph = update.speedLimitMph
        if (!update.roadName.isNullOrBlank()) {
            currentRoadName = update.roadName
        }
        _roadLimitUpdates.tryEmit(update)
    }

    fun updateStatus(message: String) {
        _statusMessage.value = message
    }

    fun updateTrackingState(running: Boolean) {
        _isTracking.value = running
    }
}

class GpsForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var gpsTracker: GpsTrackerService? = null

    override fun onCreate() {
        super.onCreate()
        gpsTracker = GpsTrackerService(applicationContext)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTrackingAndService()
                return START_NOT_STICKY
            }
            else -> {
                val stopName = intent?.getStringExtra(EXTRA_STOP_NAME) ?: GpsTrackingCoordinator.currentStopLabel
                val intervalSec = (intent?.getIntExtra(
                    EXTRA_INTERVAL_SECONDS,
                    GpsTrackingCoordinator.currentIntervalSeconds
                ) ?: GpsTrackingCoordinator.currentIntervalSeconds).coerceIn(1, 5)

                GpsTrackingCoordinator.currentStopLabel = stopName
                GpsTrackingCoordinator.currentIntervalSeconds = intervalSec

                val notification = buildNotification(stopName, intervalSec)
                val foregroundStarted = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                    true
                } catch (_: SecurityException) {
                    GpsTrackingCoordinator.updateStatus("Location permission revoked. Background GPS stopped.")
                    false
                }

                if (!foregroundStarted) {
                    stopTrackingAndService()
                    return START_NOT_STICKY
                }

                val started = gpsTracker?.startTracking(
                    scope = serviceScope,
                    intervalSeconds = intervalSec,
                    onBreadcrumb = { crumb ->
                        GpsTrackingCoordinator.publishBreadcrumb(crumb)
                    },
                    onRoadLimitResolved = { update ->
                        GpsTrackingCoordinator.publishRoadLimitUpdate(update)
                    },
                    onStatusUpdate = { status ->
                        GpsTrackingCoordinator.updateStatus(status)
                    }
                ) ?: false

                GpsTrackingCoordinator.updateTrackingState(started)
                if (started) {
                    GpsTrackingCoordinator.updateStatus(
                        "Background GPS active (${intervalSec}s) · OSM road speed · $stopName"
                    )
                } else {
                    stopTrackingAndService()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun stopTrackingAndService() {
        gpsTracker?.stopTracking()
        GpsTrackingCoordinator.updateTrackingState(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val existing = mgr.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Navigation GPS & Speed Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Records GPS route and OpenStreetMap road speed limits in the background."
                setShowBadge(false)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(stopName: String, intervalSec: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("RouteKeeper GPS Recording (${intervalSec}s)")
            .setContentText("Recording route & OSM speed limits to $stopName")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .build()
    }

    override fun onDestroy() {
        gpsTracker?.stopTracking()
        GpsTrackingCoordinator.updateTrackingState(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "routekeeper_gps_tracking"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_START = "com.example.routekeeper.action.START_GPS"
        private const val ACTION_STOP = "com.example.routekeeper.action.STOP_GPS"
        private const val EXTRA_STOP_NAME = "extra_stop_name"
        private const val EXTRA_INTERVAL_SECONDS = "extra_interval_seconds"

        fun start(context: Context, stopName: String, intervalSeconds: Int): Boolean {
            val appCtx = context.applicationContext
            val safeInterval = intervalSeconds.coerceIn(1, 5)
            GpsTrackingCoordinator.currentStopLabel = stopName
            GpsTrackingCoordinator.currentIntervalSeconds = safeInterval
            val intent = Intent(appCtx, GpsForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_STOP_NAME, stopName)
                putExtra(EXTRA_INTERVAL_SECONDS, safeInterval)
            }
            return runCatching {
                ContextCompat.startForegroundService(appCtx, intent)
                true
            }.getOrDefault(false)
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, GpsForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching {
                appCtx.startService(intent)
            }
        }
    }
}
