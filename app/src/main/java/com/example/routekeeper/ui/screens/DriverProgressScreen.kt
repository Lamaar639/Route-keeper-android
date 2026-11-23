package com.example.routekeeper.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.routekeeper.data.model.Coordinate
import com.example.routekeeper.data.service.RoutingService
import com.example.routekeeper.ui.theme.RouteTheme
import com.example.routekeeper.ui.viewmodel.RouteViewModel
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun DriverProgressScreen(
    viewModel: RouteViewModel,
    onNavigateBack: () -> Unit
) {
    val summary by viewModel.driverProgressSummary.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val gpsStatusMessage by viewModel.gpsStatusMessage.collectAsState()
    val colors = RouteTheme.colors

    var logFilter by remember { mutableStateOf("all") } // "all" or "speeding"

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.startRouteTracking()
        }
    }

    val useKmh = preferences.useKmh
    val speedUnit = if (useKmh) "km/h" else "mph"
    fun formatSpeed(mph: Float): String {
        val valDisplay = if (useKmh) (mph * 1.60934f) else mph
        return "${valDisplay.roundToInt()} $speedUnit"
    }

    val displayedBreadcrumbs = remember(summary.actualTrack, logFilter) {
        val list = if (logFilter == "speeding") {
            summary.actualTrack.filter { it.isSpeeding || it.speedMph > it.speedLimitMph }
        } else {
            summary.actualTrack
        }
        list.reversed()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("driver_progress_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 52.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header: Back + Title "Driver Progress" + Export Video Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.cardSurface)
                            .border(1.dp, colors.borderSubtle, CircleShape)
                            .testTag("driver_progress_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.brandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Driver Progress",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.brandPrimary
                        )
                        Text(
                            text = summary.routeName.ifBlank { "Route Telemetry" },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                }

                Button(
                    onClick = {
                        viewModel.openVideoExportDialog(
                            targetRouteId = if (summary.isActiveRoute) null else summary.routeId,
                            preferActualTrack = true
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brandPrimary,
                        contentColor = colors.onBrandPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("export_incident_video_button")
                ) {
                    Icon(imageVector = Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Video", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Live GPS Control Card (for active route)
        if (summary.isActiveRoute) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (summary.isRecordingGps) colors.heroCardBg else colors.cardSurface
                    ),
                    border = if (summary.isRecordingGps) null else androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FiberManualRecord,
                                    contentDescription = null,
                                    tint = when {
                                        summary.isRecordingGps -> colors.dangerAccent
                                        summary.isTrackCombined -> colors.deliveredAccent
                                        else -> colors.textMuted
                                    },
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        summary.isRecordingGps -> "BACKGROUND GPS RECORDING (${preferences.gpsIntervalSeconds}s)"
                                        summary.isTrackCombined -> "ALL SEGMENTS COMBINED"
                                        else -> "GPS PAUSED BETWEEN STOPS"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = when {
                                        summary.isRecordingGps -> colors.accent
                                        summary.isTrackCombined -> colors.deliveredAccent
                                        else -> colors.textMuted
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "${summary.actualTrack.size} GPS points · ${summary.segmentCount} segment(s) · $gpsStatusMessage",
                                fontSize = 11.sp,
                                color = if (summary.isRecordingGps) colors.onHeroMuted else colors.textMuted
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (summary.isRecordingGps) {
                            Button(
                                onClick = { viewModel.stopRouteTracking() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.dangerAccent,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("progress_pause_gps_button")
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pause", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (viewModel.hasLocationPermission()) {
                                        viewModel.startRouteTracking()
                                    } else {
                                        val permsToRequest = buildList {
                                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                                            add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                add(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        }.toTypedArray()
                                        locationPermissionLauncher.launch(permsToRequest)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.brandPrimary,
                                    contentColor = colors.onBrandPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("progress_resume_gps_button")
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resume GPS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Actual Route Taken vs Planned Route Map Plot
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("driver_progress_map_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.GpsFixed, contentDescription = null, tint = colors.brandPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ACTUAL ROUTE DRIVEN VS PLANNED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textMuted
                            )
                        }
                        Text(
                            text = "${summary.actualTrack.size} GPS pts",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.brandPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(270.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.canvasBg)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                    ) {
                        val allCoords = remember(summary.stops, summary.plannedPath, summary.actualTrack) {
                            buildList {
                                summary.stops.forEach { if (it.latitude != null && it.longitude != null) add(Coordinate(it.latitude, it.longitude)) }
                                addAll(summary.plannedPath)
                                summary.actualTrack.forEach { add(Coordinate(it.latitude, it.longitude)) }
                            }
                        }

                        if (allCoords.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No route coordinates available yet.", color = colors.textMuted, fontSize = 12.sp)
                            }
                        } else {
                            Canvas(modifier = Modifier.fillMaxSize().padding(22.dp)) {
                                val w = size.width
                                val h = size.height
                                val minLat = allCoords.minOf { it.latitude }
                                val maxLat = allCoords.maxOf { it.latitude }
                                val minLon = allCoords.minOf { it.longitude }
                                val maxLon = allCoords.maxOf { it.longitude }
                                val latSpan = max(maxLat - minLat, 0.004)
                                val lonSpan = max(maxLon - minLon, 0.004)

                                fun toPx(lat: Double, lon: Double): Offset {
                                    val x = ((lon - minLon) / lonSpan * w).toFloat().coerceIn(8f, w - 8f)
                                    val y = ((maxLat - lat) / latSpan * h).toFloat().coerceIn(8f, h - 8f)
                                    return Offset(x, y)
                                }

                                // Planned route (dashed subtle blue)
                                val refPts = if (summary.plannedPath.size >= 2) {
                                    summary.plannedPath.map { toPx(it.latitude, it.longitude) }
                                } else {
                                    summary.stops.mapNotNull {
                                        if (it.latitude != null && it.longitude != null) toPx(it.latitude, it.longitude) else null
                                    }
                                }
                                if (refPts.size >= 2) {
                                    val pPath = Path().apply {
                                        refPts.forEachIndexed { idx, pt -> if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
                                    }
                                    drawPath(
                                        path = pPath,
                                        color = colors.navAccent.copy(alpha = 0.55f),
                                        style = Stroke(
                                            width = 2.5.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                                        )
                                    )
                                }

                                // Stop pins
                                summary.stops.forEach { stop ->
                                    if (stop.latitude != null && stop.longitude != null) {
                                        val pt = toPx(stop.latitude, stop.longitude)
                                        val c = if (stop.delivered) colors.deliveredAccent else colors.brandPrimary
                                        drawCircle(color = c, radius = 8.dp.toPx(), center = pt)
                                        drawCircle(color = colors.cardSurface, radius = 4.dp.toPx(), center = pt)
                                    }
                                }

                                // Actual GPS Driven Path color-coded by speed
                                val actual = summary.actualTrack
                                if (actual.size >= 2) {
                                    for (i in 0 until actual.size - 1) {
                                        val a = actual[i]
                                        val b = actual[i + 1]
                                        val p1 = toPx(a.latitude, a.longitude)
                                        val p2 = toPx(b.latitude, b.longitude)
                                        val segSpeed = max(a.speedMph, b.speedMph)
                                        val segLimit = max(a.speedLimitMph, b.speedLimitMph)
                                        val segColor = when {
                                            a.isSpeeding || b.isSpeeding || segSpeed > segLimit -> colors.dangerAccent
                                            segSpeed >= segLimit * 0.85f -> colors.warningAccent
                                            else -> colors.deliveredAccent
                                        }
                                        drawLine(
                                            color = segColor,
                                            start = p1,
                                            end = p2,
                                            strokeWidth = 4.5.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                                if (actual.isNotEmpty()) {
                                    val startPt = toPx(actual.first().latitude, actual.first().longitude)
                                    val endPt = toPx(actual.last().latitude, actual.last().longitude)
                                    drawCircle(color = colors.brandPrimary, radius = 7.dp.toPx(), center = startPt)
                                    drawCircle(color = colors.navAccent, radius = 9.dp.toPx(), center = endPt)
                                    drawCircle(color = Color.White, radius = 4.dp.toPx(), center = endPt)
                                }
                            }
                        }

                        if (summary.actualTrack.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.cardSurface.copy(alpha = 0.94f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (summary.isActiveRoute) {
                                        "Tap Resume GPS or Navigate to plot actual route every ${preferences.gpsIntervalSeconds}s"
                                    } else {
                                        "No live GPS breadcrumbs recorded for this route"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Map Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendDot(color = colors.navAccent.copy(alpha = 0.7f), label = "Planned")
                        LegendDot(color = colors.deliveredAccent, label = "Within Limit")
                        LegendDot(color = colors.warningAccent, label = "Near Limit")
                        LegendDot(color = colors.dangerAccent, label = "Speeding")
                    }
                }
            }
        }

        // Telemetry Summary Metrics
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "ACTUAL DRIVEN",
                    value = if (summary.actualTrack.size >= 2) {
                        RoutingService.formatDistance(summary.actualDistanceMeters)
                    } else "0 m",
                    sub = "Planned: ${RoutingService.formatDistance(summary.plannedDistanceMeters)}",
                    isAlert = false
                )
                TelemetryMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "PEAK SPEED",
                    value = formatSpeed(summary.maxSpeedMph),
                    sub = "Avg: ${formatSpeed(summary.avgSpeedMph)}",
                    isAlert = summary.speedingEventsCount > 0
                )
                TelemetryMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "SPEEDING",
                    value = "${summary.speedingEventsCount}",
                    sub = if (summary.latestRoadName != null) "OSM: ${summary.latestRoadName}" else "OSM Road Limits",
                    isAlert = summary.speedingEventsCount > 0
                )
            }
        }

        // Speed Over Time Timeline Graph
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SPEED TIMELINE (${preferences.gpsIntervalSeconds}s · OSM LIMITS)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textMuted
                        )
                        if (summary.speedingEventsCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = colors.dangerAccent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${summary.speedingEventsCount} Over OSM Limit",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.dangerAccent
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.canvasBg)
                            .padding(12.dp)
                    ) {
                        if (summary.actualTrack.size < 2) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Speed timeline will appear once 2+ GPS samples are recorded",
                                    fontSize = 11.sp,
                                    color = colors.textMuted
                                )
                            }
                        } else {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val track = summary.actualTrack
                                val maxLimit = max(track.maxOf { it.speedLimitMph }, summary.speedLimitMph)
                                val maxY = max(maxLimit * 1.35f, track.maxOf { it.speedMph } + 8f)

                                // Stepped OpenStreetMap road speed limit dashed curve
                                val limitPath = Path()
                                track.forEachIndexed { idx, crumb ->
                                    val x = (idx.toFloat() / max(1, track.size - 1)) * size.width
                                    val y = size.height - (crumb.speedLimitMph / maxY) * size.height
                                    if (idx == 0) limitPath.moveTo(x, y) else limitPath.lineTo(x, y)
                                }
                                drawPath(
                                    path = limitPath,
                                    color = colors.dangerAccent.copy(alpha = 0.72f),
                                    style = Stroke(
                                        width = 2.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                                    )
                                )

                                // Speed curve
                                val path = Path()
                                track.forEachIndexed { idx, crumb ->
                                    val x = (idx.toFloat() / max(1, track.size - 1)) * size.width
                                    val y = size.height - (crumb.speedMph / maxY) * size.height
                                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(
                                    path = path,
                                    color = colors.brandPrimary,
                                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )

                                // Dots (highlight speeding in red)
                                track.forEachIndexed { idx, crumb ->
                                    val x = (idx.toFloat() / max(1, track.size - 1)) * size.width
                                    val y = size.height - (crumb.speedMph / maxY) * size.height
                                    if (crumb.isSpeeding || crumb.speedMph > crumb.speedLimitMph) {
                                        drawCircle(color = colors.dangerAccent, radius = 4.5.dp.toPx(), center = Offset(x, y))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Speed Log Filter Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GPS SPEED LOG (${displayedBreadcrumbs.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textMuted
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = logFilter == "all",
                        onClick = { logFilter = "all" },
                        label = { Text("All", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.brandPrimary,
                            selectedLabelColor = colors.onBrandPrimary
                        ),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                    FilterChip(
                        selected = logFilter == "speeding",
                        onClick = { logFilter = "speeding" },
                        label = { Text("Speeding (${summary.speedingEventsCount})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.dangerAccent,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                }
            }
        }

        if (displayedBreadcrumbs.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.cardSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle)
                ) {
                    Text(
                        text = if (logFilter == "speeding") "No speeding events recorded above detected OpenStreetMap road limits."
                        else "No GPS speed logs recorded yet.",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 12.sp,
                        color = colors.textMuted
                    )
                }
            }
        } else {
            items(displayedBreadcrumbs, key = { it.id }) { crumb ->
                val isOver = crumb.isSpeeding || crumb.speedMph > crumb.speedLimitMph
                val roadLabel = crumb.roadName?.let { "$it · ${crumb.speedLimitSource}" } ?: crumb.speedLimitSource
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isOver) colors.dangerBg else colors.cardSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isOver) colors.dangerBorder else colors.borderSubtle
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = crumb.timeLabel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = colors.textPrimary
                            )
                            Text(
                                text = roadLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.brandPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = "${"%.5f".format(crumb.latitude)}, ${"%.5f".format(crumb.longitude)}",
                                fontSize = 10.sp,
                                color = colors.textMuted
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            val primarySpeedText = if (useKmh) {
                                "${crumb.speedKmh.roundToInt()} km/h (${crumb.speedMph.roundToInt()} mph)"
                            } else {
                                "${crumb.speedMph.roundToInt()} mph (${crumb.speedKmh.roundToInt()} km/h)"
                            }
                            val displaySpeed = if (useKmh) crumb.speedKmh else crumb.speedMph
                            val displayLimit = if (useKmh) crumb.speedLimitMph * 1.60934f else crumb.speedLimitMph
                            val overDelta = (displaySpeed - displayLimit).roundToInt().coerceAtLeast(1)

                            Text(
                                text = primarySpeedText,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = if (isOver) colors.dangerText else colors.brandPrimary
                            )
                            Text(
                                text = if (isOver) {
                                    "⚠ +$overDelta $speedUnit OVER OSM ${formatSpeed(crumb.speedLimitMph)}"
                                } else {
                                    "✓ Within OSM ${formatSpeed(crumb.speedLimitMph)}"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOver) colors.dangerAccent else colors.deliveredAccent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    val colors = RouteTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 10.sp, color = colors.textMuted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TelemetryMetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    sub: String,
    isAlert: Boolean
) {
    val colors = RouteTheme.colors
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAlert) colors.dangerBg else colors.cardSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isAlert) colors.dangerBorder else colors.borderSubtle
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isAlert) colors.dangerText else colors.textMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = if (isAlert) colors.dangerAccent else colors.brandPrimary
            )
            Text(
                text = sub,
                fontSize = 10.sp,
                color = colors.textMuted,
                maxLines = 1
            )
        }
    }
}
