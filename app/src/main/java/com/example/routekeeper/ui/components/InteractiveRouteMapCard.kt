package com.example.routekeeper.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.routekeeper.data.model.Coordinate
import com.example.routekeeper.data.model.DeliveryStop
import com.example.routekeeper.data.model.GpsBreadcrumb
import com.example.routekeeper.data.model.RouteStep
import com.example.routekeeper.data.model.RoutingMode
import com.example.routekeeper.data.model.TrafficLevel
import com.example.routekeeper.data.model.TrafficRouteSegment
import com.example.routekeeper.data.service.OsmTileLoader
import com.example.routekeeper.data.service.RoutingService
import com.example.routekeeper.ui.theme.RouteTheme
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// Google Maps standard road traffic palette
private val TrafficClearGreen = Color(0xFF16A34A)
private val TrafficModerateAmber = Color(0xFFF59E0B)
private val TrafficBusyLightRed = Color(0xFFEF4444)
private val TrafficSevereDarkRed = Color(0xFF8B0000)

private fun trafficLevelColor(level: TrafficLevel): Color = when (level) {
    TrafficLevel.CLEAR -> TrafficClearGreen
    TrafficLevel.MODERATE -> TrafficModerateAmber
    TrafficLevel.BUSY -> TrafficBusyLightRed
    TrafficLevel.SEVERE -> TrafficSevereDarkRed
}

private fun trafficLevelLabel(level: TrafficLevel): String = when (level) {
    TrafficLevel.CLEAR -> "Clear"
    TrafficLevel.MODERATE -> "Moderate"
    TrafficLevel.BUSY -> "Busy"
    TrafficLevel.SEVERE -> "Very Busy"
}

@Composable
fun InteractiveRouteMapCard(
    routeId: String,
    stops: List<DeliveryStop>,
    plannedPath: List<Coordinate> = emptyList(),
    trafficSegments: List<TrafficRouteSegment> = emptyList(),
    routeSteps: List<RouteStep> = emptyList(),
    routingMode: RoutingMode = RoutingMode.NOT_PLANNED,
    trafficSummary: String? = null,
    isFetchingRoadRoute: Boolean = false,
    actualTrack: List<GpsBreadcrumb>,
    activeNavigatingStopId: String?,
    selectedStopId: String?,
    focusRequestToken: Int,
    onSelectStop: (DeliveryStop?) -> Unit,
    onOpenStopDetail: (DeliveryStop) -> Unit,
    onNavigateStop: (DeliveryStop) -> Unit,
    onToggleDelivered: (DeliveryStop) -> Unit,
    onRetryRoadRoute: () -> Unit = {}
) {
    var isFullscreen by remember { mutableStateOf(false) }
    var showTurnByTurnSteps by remember(routeId) { mutableStateOf(false) }
    var showAllSteps by remember(routeId) { mutableStateOf(false) }
    val colors = RouteTheme.colors

    val locatableStops = remember(stops) {
        stops.mapIndexedNotNull { index, stop ->
            if (stop.latitude != null && stop.longitude != null) index to stop else null
        }
    }

    val allCoords = remember(locatableStops, plannedPath, actualTrack) {
        buildList {
            locatableStops.forEach { (_, st) -> add(Coordinate(st.latitude!!, st.longitude!!)) }
            plannedPath.forEach { add(it) }
            actualTrack.forEach { add(Coordinate(it.latitude, it.longitude)) }
        }
    }

    var centerCoord by remember(routeId) {
        mutableStateOf(allCoords.firstOrNull() ?: Coordinate(51.5074, -0.1278))
    }
    var zoomLevel by remember(routeId) { mutableFloatStateOf(13.2f) }
    var hasUserMovedCamera by remember(routeId) { mutableStateOf(false) }
    var fittedCoordCount by remember(routeId) { mutableIntStateOf(0) }
    var viewportSize by remember { mutableStateOf(IntSize(800, 700)) }

    // Initial fit and automatic re-fit when additional coordinates or road geometry finish loading (until user pans)
    LaunchedEffect(routeId, allCoords.size, viewportSize, hasUserMovedCamera) {
        if (!hasUserMovedCamera && allCoords.isNotEmpty() && allCoords.size > fittedCoordCount &&
            viewportSize.width > 0 && viewportSize.height > 0
        ) {
            val (fitCenter, fitZoom) = OsmTileLoader.computeFitCamera(
                coords = allCoords,
                viewportWidthPx = viewportSize.width.toFloat(),
                viewportHeightPx = viewportSize.height.toFloat()
            )
            centerCoord = fitCenter
            zoomLevel = fitZoom
            fittedCoordCount = allCoords.size
        }
    }

    // Center smoothly on drop whenever user picks a drop via list or chip
    LaunchedEffect(focusRequestToken, selectedStopId) {
        if (focusRequestToken > 0 && selectedStopId != null) {
            val target = stops.find { it.id == selectedStopId }
            if (target?.latitude != null && target.longitude != null) {
                hasUserMovedCamera = true
                centerCoord = Coordinate(target.latitude, target.longitude)
                zoomLevel = zoomLevel.coerceAtLeast(16.0f)
            }
        }
    }

    val selectedStop = remember(stops, selectedStopId) {
        stops.find { it.id == selectedStopId }
    }
    val selectedDisplayIndex = remember(stops, selectedStopId) {
        stops.indexOfFirst { it.id == selectedStopId }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Horizontal Quick Drop Jumper Chips
        if (locatableStops.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(locatableStops, key = { _, (_, stop) -> stop.id }) { _, (listIdx, stop) ->
                    val isSelected = stop.id == selectedStopId
                    val dropLabel = stop.dropNumber ?: "${listIdx + 1}"
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            hasUserMovedCamera = true
                            onSelectStop(stop)
                            centerCoord = Coordinate(stop.latitude!!, stop.longitude!!)
                            zoomLevel = zoomLevel.coerceAtLeast(16.0f)
                        },
                        label = {
                            Text(
                                text = "#$dropLabel ${stop.name.take(14)}",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.brandPrimary,
                            selectedLabelColor = colors.onBrandPrimary
                        ),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                }
            }
        }

        // Embedded Interactive Map Viewport
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .testTag("route_map_canvas_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.canvasBg),
            border = BorderStroke(1.dp, colors.borderSubtle)
        ) {
            InteractiveMapViewport(
                stops = stops,
                plannedPath = plannedPath,
                trafficSegments = trafficSegments,
                routingMode = routingMode,
                isFetchingRoadRoute = isFetchingRoadRoute,
                actualTrack = actualTrack,
                activeNavigatingStopId = activeNavigatingStopId,
                selectedStopId = selectedStopId,
                centerCoord = centerCoord,
                zoomLevel = zoomLevel,
                isFullscreen = false,
                onUpdateCamera = { newCenter, newZoom ->
                    hasUserMovedCamera = true
                    centerCoord = newCenter
                    zoomLevel = newZoom
                },
                onFitAll = {
                    if (allCoords.isNotEmpty()) {
                        val (fitCenter, fitZoom) = OsmTileLoader.computeFitCamera(
                            coords = allCoords,
                            viewportWidthPx = viewportSize.width.toFloat(),
                            viewportHeightPx = viewportSize.height.toFloat()
                        )
                        hasUserMovedCamera = false
                        fittedCoordCount = allCoords.size
                        centerCoord = fitCenter
                        zoomLevel = fitZoom
                    }
                },
                onToggleFullscreen = { isFullscreen = true },
                onSelectStop = onSelectStop,
                onRetryRoadRoute = onRetryRoadRoute,
                onSizeChanged = { viewportSize = it }
            )
        }

        // Google Maps Traffic Color Scheme Legend & Road Navigation Summary Card
        TrafficLegendSummaryCard(
            routingMode = routingMode,
            trafficSummary = trafficSummary,
            isFetchingRoadRoute = isFetchingRoadRoute,
            trafficSegments = trafficSegments,
            onRetryRoadRoute = onRetryRoadRoute
        )

        // Expandable Estimated Turn-by-Turn Navigation & Segment Traffic Details
        if (routeSteps.isNotEmpty()) {
            TurnByTurnNavigationCard(
                routeSteps = routeSteps,
                expanded = showTurnByTurnSteps,
                showAllSteps = showAllSteps,
                onToggleExpand = { showTurnByTurnSteps = !showTurnByTurnSteps },
                onToggleShowAll = { showAllSteps = !showAllSteps }
            )
        }

        // Detailed Popover Card for the Selected Map Drop
        if (selectedStop != null) {
            SelectedMapDropDetailCard(
                stop = selectedStop,
                stopNumber = selectedStop.dropNumber ?: "${selectedDisplayIndex + 1}",
                isNavigating = activeNavigatingStopId == selectedStop.id,
                onDismiss = { onSelectStop(null) },
                onOpenDetail = { onOpenStopDetail(selectedStop) },
                onNavigate = { onNavigateStop(selectedStop) },
                onToggleDelivered = { onToggleDelivered(selectedStop) }
            )
        }
    }

    // Fullscreen Interactive Map Dialog
    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("fullscreen_interactive_map_dialog"),
                color = colors.canvasBg
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    var fullSize by remember { mutableStateOf(IntSize(1080, 1920)) }
                    InteractiveMapViewport(
                        stops = stops,
                        plannedPath = plannedPath,
                        trafficSegments = trafficSegments,
                        routingMode = routingMode,
                        isFetchingRoadRoute = isFetchingRoadRoute,
                        actualTrack = actualTrack,
                        activeNavigatingStopId = activeNavigatingStopId,
                        selectedStopId = selectedStopId,
                        centerCoord = centerCoord,
                        zoomLevel = zoomLevel,
                        isFullscreen = true,
                        onUpdateCamera = { newCenter, newZoom ->
                            hasUserMovedCamera = true
                            centerCoord = newCenter
                            zoomLevel = newZoom
                        },
                        onFitAll = {
                            if (allCoords.isNotEmpty()) {
                                val (fitCenter, fitZoom) = OsmTileLoader.computeFitCamera(
                                    coords = allCoords,
                                    viewportWidthPx = fullSize.width.toFloat(),
                                    viewportHeightPx = fullSize.height.toFloat()
                                )
                                hasUserMovedCamera = false
                                fittedCoordCount = allCoords.size
                                centerCoord = fitCenter
                                zoomLevel = fitZoom
                            }
                        },
                        onToggleFullscreen = { isFullscreen = false },
                        onSelectStop = onSelectStop,
                        onRetryRoadRoute = onRetryRoadRoute,
                        onSizeChanged = { fullSize = it }
                    )

                    if (selectedStop != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            SelectedMapDropDetailCard(
                                stop = selectedStop,
                                stopNumber = selectedStop.dropNumber ?: "${selectedDisplayIndex + 1}",
                                isNavigating = activeNavigatingStopId == selectedStop.id,
                                onDismiss = { onSelectStop(null) },
                                onOpenDetail = {
                                    isFullscreen = false
                                    onOpenStopDetail(selectedStop)
                                },
                                onNavigate = { onNavigateStop(selectedStop) },
                                onToggleDelivered = { onToggleDelivered(selectedStop) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrafficLegendSummaryCard(
    routingMode: RoutingMode,
    trafficSummary: String?,
    isFetchingRoadRoute: Boolean,
    trafficSegments: List<TrafficRouteSegment>,
    onRetryRoadRoute: () -> Unit
) {
    val colors = RouteTheme.colors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("traffic_legend_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
        border = BorderStroke(1.dp, colors.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Traffic,
                        contentDescription = null,
                        tint = colors.brandPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ROAD NAVIGATION & TRAFFIC",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.brandPrimary,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (routingMode) {
                                RoutingMode.ROAD_NETWORK -> colors.accentContainer
                                RoutingMode.APPROXIMATE -> colors.warningBg
                                RoutingMode.NOT_PLANNED -> colors.cardSurfaceVariant
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = when {
                            isFetchingRoadRoute -> "Routing..."
                            routingMode == RoutingMode.ROAD_NETWORK -> "Street Network"
                            routingMode == RoutingMode.APPROXIMATE -> "Approx. Fallback"
                            else -> "Pending"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (routingMode) {
                            RoutingMode.APPROXIMATE -> colors.warningText
                            RoutingMode.ROAD_NETWORK -> colors.onAccentContainer
                            RoutingMode.NOT_PLANNED -> colors.textMuted
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isFetchingRoadRoute -> "Calculating street navigation & traffic profile..."
                        !trafficSummary.isNullOrBlank() -> trafficSummary
                        trafficSegments.isNotEmpty() -> "Historic & OSM road traffic (${trafficSegments.size} segments)"
                        else -> "Waiting for geocoded drop locations"
                    },
                    fontSize = 11.sp,
                    color = colors.textMuted,
                    modifier = Modifier.weight(1f)
                )

                if (!isFetchingRoadRoute && routingMode == RoutingMode.APPROXIMATE) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onRetryRoadRoute,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("traffic_card_retry_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.brandPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry Road Route",
                            tint = colors.brandPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry", fontSize = 11.sp, color = colors.brandPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Google Maps Traffic Color Key
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrafficLegendChip(color = TrafficSevereDarkRed, label = "Very Busy")
                TrafficLegendChip(color = TrafficBusyLightRed, label = "Busy")
                TrafficLegendChip(color = TrafficModerateAmber, label = "Moderate")
                TrafficLegendChip(color = TrafficClearGreen, label = "Clear")
            }
        }
    }
}

@Composable
private fun TrafficLegendChip(color: Color, label: String) {
    val colors = RouteTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary
        )
    }
}

@Composable
private fun TurnByTurnNavigationCard(
    routeSteps: List<RouteStep>,
    expanded: Boolean,
    showAllSteps: Boolean,
    onToggleExpand: () -> Unit,
    onToggleShowAll: () -> Unit
) {
    val colors = RouteTheme.colors
    val busyCount = remember(routeSteps) {
        routeSteps.count { it.trafficLevel == TrafficLevel.BUSY || it.trafficLevel == TrafficLevel.SEVERE }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("turn_by_turn_navigation_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
        border = BorderStroke(1.dp, colors.borderSubtle)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("toggle_turn_by_turn_button"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Directions,
                        contentDescription = null,
                        tint = colors.brandPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Estimated Road Turns (${routeSteps.size} steps)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        if (busyCount > 0) {
                            Text(
                                text = "$busyCount busy or heavy traffic maneuvers ahead",
                                fontSize = 11.sp,
                                color = colors.dangerAccent,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse Directions" else "Expand Directions",
                    tint = colors.brandPrimary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = colors.borderSubtle)
                    val visibleSteps = if (showAllSteps) routeSteps else routeSteps.take(12)
                    visibleSteps.forEachIndexed { idx, step ->
                        val color = trafficLevelColor(step.trafficLevel)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = step.instruction,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "${RoutingService.formatDistance(step.distanceMeters)} · ${RoutingService.formatDuration(step.durationSeconds)}",
                                    fontSize = 10.sp,
                                    color = colors.textMuted
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(color.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = trafficLevelLabel(step.trafficLevel),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                            }
                        }
                        if (idx < visibleSteps.lastIndex) {
                            HorizontalDivider(
                                color = colors.borderSubtle.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }

                    if (routeSteps.size > 12) {
                        HorizontalDivider(color = colors.borderSubtle)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable { onToggleShowAll() }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (showAllSteps) "Show Fewer Steps" else "Show All ${routeSteps.size} Steps",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.brandPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveMapViewport(
    stops: List<DeliveryStop>,
    plannedPath: List<Coordinate>,
    trafficSegments: List<TrafficRouteSegment>,
    routingMode: RoutingMode,
    isFetchingRoadRoute: Boolean,
    actualTrack: List<GpsBreadcrumb>,
    activeNavigatingStopId: String?,
    selectedStopId: String?,
    centerCoord: Coordinate,
    zoomLevel: Float,
    isFullscreen: Boolean,
    onUpdateCamera: (Coordinate, Float) -> Unit,
    onFitAll: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSelectStop: (DeliveryStop?) -> Unit,
    onRetryRoadRoute: () -> Unit,
    onSizeChanged: (IntSize) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val colors = RouteTheme.colors

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val loadedTiles = remember { mutableStateMapOf<String, Bitmap>() }
    val inFlightTileKeys = remember { java.util.Collections.synchronizedSet(mutableSetOf<String>()) }
    val tileFetchScope = rememberCoroutineScope()
    var tileDrawRevision by remember { mutableIntStateOf(0) }

    val clampedZoom = zoomLevel.coerceIn(10f, 18f)
    val tileZoom = clampedZoom.roundToInt().coerceIn(10, 18)

    val latestCenter by rememberUpdatedState(centerCoord)
    val latestZoom by rememberUpdatedState(clampedZoom)
    val latestStops by rememberUpdatedState(stops)
    val latestOnUpdateCamera by rememberUpdatedState(onUpdateCamera)
    val latestOnSelectStop by rememberUpdatedState(onSelectStop)

    // Determine visible OpenStreetMap tile grid around centerCoord at clampedZoom
    val visibleTiles = remember(centerCoord, clampedZoom, boxSize) {
        if (boxSize.width <= 0 || boxSize.height <= 0) {
            emptyList()
        } else {
            val zDouble = clampedZoom.toDouble()
            val centerWX = OsmTileLoader.lonToWorldX(centerCoord.longitude, zDouble)
            val centerWY = OsmTileLoader.latToWorldY(centerCoord.latitude, zDouble)
            val leftWX = centerWX - boxSize.width / 2.0
            val rightWX = centerWX + boxSize.width / 2.0
            val topWY = centerWY - boxSize.height / 2.0
            val bottomWY = centerWY + boxSize.height / 2.0

            val tileScale = 256.0 * 2.0.pow((clampedZoom - tileZoom).toDouble())
            val minTx = floor(leftWX / tileScale).toInt()
            val maxTx = floor(rightWX / tileScale).toInt()
            val minTy = floor(topWY / tileScale).toInt()
            val maxTy = floor(bottomWY / tileScale).toInt()

            val maxIndex = (1 shl tileZoom) - 1
            buildList {
                for (ty in minTy.coerceAtLeast(0)..maxTy.coerceAtMost(maxIndex)) {
                    for (tx in minTx..maxTx) {
                        add(Triple(tileZoom, tx, ty))
                    }
                }
            }.take(36)
        }
    }

    // Fetch missing street tiles asynchronously without cancelling active downloads during pans
    LaunchedEffect(visibleTiles) {
        visibleTiles.forEach { (tz, tx, ty) ->
            val maxTile = 1 shl tz
            val wrappedX = ((tx % maxTile) + maxTile) % maxTile
            val key = "$tz/$wrappedX/$ty"
            if (!loadedTiles.containsKey(key) && inFlightTileKeys.add(key)) {
                val mem = OsmTileLoader.getMemoryTile(tz, wrappedX, ty)
                if (mem != null) {
                    loadedTiles[key] = mem
                    inFlightTileKeys.remove(key)
                    tileDrawRevision++
                } else {
                    tileFetchScope.launch {
                        try {
                            val fetched = OsmTileLoader.loadTile(context, tz, wrappedX, ty)
                            if (fetched != null) {
                                loadedTiles[key] = fetched
                                tileDrawRevision++
                            }
                        } finally {
                            inFlightTileKeys.remove(key)
                        }
                    }
                }
            }
        }
    }

    val hitRadiusPx = with(density) { 28.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged {
                boxSize = it
                onSizeChanged(it)
            }
            // Pan & Pinch-to-zoom handler that preserves gesture continuity across camera state updates
            .pointerInput(boxSize) {
                if (boxSize.width <= 0 || boxSize.height <= 0) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var curCenter = latestCenter
                    var curZoom = latestZoom
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            val nextZoom = (curZoom + kotlin.math.ln(zoomChange.toDouble()).toFloat() * 1.442695f)
                                .coerceIn(10f, 18f)

                            val centroid = event.calculateCentroid(useCurrent = false)
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()

                            // Keep zoom focal point anchored cleanly
                            val oldZ = curZoom.toDouble()
                            val newZ = nextZoom.toDouble()
                            val cWX = OsmTileLoader.lonToWorldX(curCenter.longitude, oldZ)
                            val cWY = OsmTileLoader.latToWorldY(curCenter.latitude, oldZ)

                            val focalOffsetX = if (centroid != Offset.Unspecified) centroid.x - w / 2f else 0f
                            val focalOffsetY = if (centroid != Offset.Unspecified) centroid.y - h / 2f else 0f

                            val worldAtCentroidLon = OsmTileLoader.worldXToLon(cWX + focalOffsetX - panChange.x, oldZ)
                            val worldAtCentroidLat = OsmTileLoader.worldYToLat(cWY + focalOffsetY - panChange.y, oldZ)

                            val newCentroidWX = OsmTileLoader.lonToWorldX(worldAtCentroidLon, newZ)
                            val newCentroidWY = OsmTileLoader.latToWorldY(worldAtCentroidLat, newZ)

                            val nextCenterWX = newCentroidWX - focalOffsetX
                            val nextCenterWY = newCentroidWY - focalOffsetY

                            curZoom = nextZoom
                            curCenter = Coordinate(
                                OsmTileLoader.worldYToLat(nextCenterWY, newZ),
                                OsmTileLoader.worldXToLon(nextCenterWX, newZ)
                            )
                            latestOnUpdateCamera(curCenter, curZoom)

                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            // Tap pin to inspect stop details, double-tap map to zoom in
            .pointerInput(boxSize) {
                if (boxSize.width <= 0 || boxSize.height <= 0) return@pointerInput
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        val activeCenter = latestCenter
                        val activeZoom = latestZoom
                        val nextZoom = (activeZoom + 1.4f).coerceIn(10f, 18f)
                        val oldZ = activeZoom.toDouble()
                        val newZ = nextZoom.toDouble()
                        val cWX = OsmTileLoader.lonToWorldX(activeCenter.longitude, oldZ)
                        val cWY = OsmTileLoader.latToWorldY(activeCenter.latitude, oldZ)
                        val offX = tapOffset.x - size.width / 2f
                        val offY = tapOffset.y - size.height / 2f
                        val tapLon = OsmTileLoader.worldXToLon(cWX + offX, oldZ)
                        val tapLat = OsmTileLoader.worldYToLat(cWY + offY, oldZ)
                        val newTapWX = OsmTileLoader.lonToWorldX(tapLon, newZ)
                        val newTapWY = OsmTileLoader.latToWorldY(tapLat, newZ)
                        latestOnUpdateCamera(
                            Coordinate(
                                OsmTileLoader.worldYToLat(newTapWY - offY * 0.35, newZ),
                                OsmTileLoader.worldXToLon(newTapWX - offX * 0.35, newZ)
                            ),
                            nextZoom
                        )
                    },
                    onTap = { tapOffset ->
                        val activeCenter = latestCenter
                        val zDouble = latestZoom.toDouble()
                        val cWX = OsmTileLoader.lonToWorldX(activeCenter.longitude, zDouble)
                        val cWY = OsmTileLoader.latToWorldY(activeCenter.latitude, zDouble)

                        var closestStop: DeliveryStop? = null
                        var bestDist = hitRadiusPx
                        latestStops.forEach { stop ->
                            if (stop.latitude != null && stop.longitude != null) {
                                val px = (OsmTileLoader.lonToWorldX(stop.longitude, zDouble) - cWX + size.width / 2f).toFloat()
                                val py = (OsmTileLoader.latToWorldY(stop.latitude, zDouble) - cWY + size.height / 2f).toFloat()
                                val d = hypot(tapOffset.x - px, tapOffset.y - py)
                                if (d <= bestDist) {
                                    bestDist = d
                                    closestStop = stop
                                }
                            }
                        }
                        latestOnSelectStop(closestStop)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Read tileDrawRevision so Canvas invalidates whenever an async OSM tile arrives
            @Suppress("UNUSED_VARIABLE")
            val rev = tileDrawRevision

            val w = size.width
            val h = size.height
            val zDouble = clampedZoom.toDouble()
            val centerWX = OsmTileLoader.lonToWorldX(centerCoord.longitude, zDouble)
            val centerWY = OsmTileLoader.latToWorldY(centerCoord.latitude, zDouble)

            fun toScreen(lat: Double, lon: Double): Offset {
                val wx = OsmTileLoader.lonToWorldX(lon, zDouble)
                val wy = OsmTileLoader.latToWorldY(lat, zDouble)
                return Offset(
                    x = (wx - centerWX + w / 2f).toFloat(),
                    y = (wy - centerWY + h / 2f).toFloat()
                )
            }

            // 1. Subtle coordinate grid fallback underneath tiles
            drawRect(color = colors.canvasBg)
            val gridStep = 56.dp.toPx()
            var gx = 0f
            while (gx < w) {
                drawLine(colors.borderSubtle.copy(alpha = 0.55f), Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
                gx += gridStep
            }
            var gy = 0f
            while (gy < h) {
                drawLine(colors.borderSubtle.copy(alpha = 0.55f), Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                gy += gridStep
            }

            // 2. Render Real OpenStreetMap Street Tiles
            val scaleFactor = 2.0.pow((clampedZoom - tileZoom).toDouble())
            val renderedTileSize = (256.0 * scaleFactor)
            val maxTile = 1 shl tileZoom

            visibleTiles.forEach { (tz, tx, ty) ->
                val wrappedX = ((tx % maxTile) + maxTile) % maxTile
                val bmp = loadedTiles["$tz/$wrappedX/$ty"] ?: return@forEach
                val tileLeft = ((tx * renderedTileSize) - centerWX + w / 2.0).roundToInt()
                val tileTop = ((ty * renderedTileSize) - centerWY + h / 2.0).roundToInt()
                val tileRight = (((tx + 1) * renderedTileSize) - centerWX + w / 2.0).roundToInt()
                val tileBottom = (((ty + 1) * renderedTileSize) - centerWY + h / 2.0).roundToInt()

                drawImage(
                    image = bmp.asImageBitmap(),
                    dstOffset = IntOffset(tileLeft, tileTop),
                    dstSize = IntSize(
                        width = (tileRight - tileLeft).coerceAtLeast(1),
                        height = (tileBottom - tileTop).coerceAtLeast(1)
                    )
                )
            }

            // 3. Draw Estimated Road-Following Navigation Route with Traffic Overlay
            val stopScreenPts = stops.mapIndexedNotNull { idx, st ->
                if (st.latitude != null && st.longitude != null) {
                    Triple(idx, st, toScreen(st.latitude, st.longitude))
                } else null
            }

            val casingWidth = 8.dp.toPx()
            val roadLineWidth = 5.dp.toPx()
            val arrowSpacingPx = 68.dp.toPx()
            val arrowSizePx = 5.dp.toPx()

            fun drawDirectionChevron(start: Offset, end: Offset) {
                val dx = end.x - start.x
                val dy = end.y - start.y
                val len = hypot(dx, dy)
                if (len < 12f) return
                val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
                if (mid.x < -20f || mid.x > w + 20f || mid.y < -20f || mid.y > h + 20f) return

                val angle = atan2(dy.toDouble(), dx.toDouble())
                val tip = Offset(
                    (mid.x + cos(angle) * arrowSizePx).toFloat(),
                    (mid.y + sin(angle) * arrowSizePx).toFloat()
                )
                val leftWing = Offset(
                    (mid.x + cos(angle + 2.45) * arrowSizePx).toFloat(),
                    (mid.y + sin(angle + 2.45) * arrowSizePx).toFloat()
                )
                val rightWing = Offset(
                    (mid.x + cos(angle - 2.45) * arrowSizePx).toFloat(),
                    (mid.y + sin(angle - 2.45) * arrowSizePx).toFloat()
                )
                val chevron = Path().apply {
                    moveTo(leftWing.x, leftWing.y)
                    lineTo(tip.x, tip.y)
                    lineTo(rightWing.x, rightWing.y)
                }
                drawPath(
                    path = chevron,
                    color = Color.White.copy(alpha = 0.95f),
                    style = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            if (trafficSegments.isNotEmpty()) {
                // First pass: crisp white road casing beneath all traffic segments
                val fullCasing = Path()
                trafficSegments.forEachIndexed { idx, seg ->
                    val s = toScreen(seg.startLat, seg.startLon)
                    val e = toScreen(seg.endLat, seg.endLon)
                    if (idx == 0) {
                        fullCasing.moveTo(s.x, s.y)
                        fullCasing.lineTo(e.x, e.y)
                    } else {
                        val prevEnd = trafficSegments[idx - 1]
                        if (kotlin.math.abs(prevEnd.endLat - seg.startLat) > 1e-6 ||
                            kotlin.math.abs(prevEnd.endLon - seg.startLon) > 1e-6
                        ) {
                            fullCasing.moveTo(s.x, s.y)
                        }
                        fullCasing.lineTo(e.x, e.y)
                    }
                }
                drawPath(
                    path = fullCasing,
                    color = Color.White.copy(alpha = 0.92f),
                    style = Stroke(width = casingWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Second pass: group contiguous same-traffic segments into smooth paths
                var activeLevel = trafficSegments.first().trafficLevel
                var activePath = Path()
                var distSinceArrow = 0f

                trafficSegments.forEachIndexed { idx, seg ->
                    val s = toScreen(seg.startLat, seg.startLon)
                    val e = toScreen(seg.endLat, seg.endLon)
                    val isContiguous = idx > 0 &&
                        kotlin.math.abs(trafficSegments[idx - 1].endLat - seg.startLat) <= 1e-6 &&
                        kotlin.math.abs(trafficSegments[idx - 1].endLon - seg.startLon) <= 1e-6

                    if (seg.trafficLevel != activeLevel || !isContiguous) {
                        if (idx > 0) {
                            drawPath(
                                path = activePath,
                                color = trafficLevelColor(activeLevel),
                                style = Stroke(width = roadLineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                        activeLevel = seg.trafficLevel
                        activePath = Path().apply {
                            moveTo(s.x, s.y)
                            lineTo(e.x, e.y)
                        }
                    } else {
                        if (idx == 0) activePath.moveTo(s.x, s.y)
                        activePath.lineTo(e.x, e.y)
                    }
                }
                drawPath(
                    path = activePath,
                    color = trafficLevelColor(activeLevel),
                    style = Stroke(width = roadLineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Third pass: navigation chevrons spaced evenly in screen coordinates
                trafficSegments.forEach { seg ->
                    val s = toScreen(seg.startLat, seg.startLon)
                    val e = toScreen(seg.endLat, seg.endLon)
                    distSinceArrow += hypot(e.x - s.x, e.y - s.y)
                    if (distSinceArrow >= arrowSpacingPx) {
                        drawDirectionChevron(s, e)
                        distSinceArrow = 0f
                    }
                }
            } else if (plannedPath.size >= 2) {
                val roadPts = plannedPath.map { toScreen(it.latitude, it.longitude) }
                val roadPath = Path().apply {
                    roadPts.forEachIndexed { i, pt ->
                        if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                    }
                }
                drawPath(
                    path = roadPath,
                    color = Color.White.copy(alpha = 0.92f),
                    style = Stroke(width = casingWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = roadPath,
                    color = if (routingMode == RoutingMode.ROAD_NETWORK) colors.navAccent else colors.navAccent.copy(alpha = 0.78f),
                    style = Stroke(
                        width = roadLineWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = if (routingMode == RoutingMode.ROAD_NETWORK) null else PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                    )
                )
                var distSinceArrow = 0f
                for (i in 0 until roadPts.size - 1) {
                    distSinceArrow += hypot(roadPts[i + 1].x - roadPts[i].x, roadPts[i + 1].y - roadPts[i].y)
                    if (distSinceArrow >= arrowSpacingPx) {
                        drawDirectionChevron(roadPts[i], roadPts[i + 1])
                        distSinceArrow = 0f
                    }
                }
            } else if (stopScreenPts.size >= 2) {
                val fallbackPath = Path().apply {
                    stopScreenPts.forEachIndexed { i, (_, _, pt) ->
                        if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                    }
                }
                drawPath(
                    path = fallbackPath,
                    color = Color.White.copy(alpha = 0.85f),
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = fallbackPath,
                    color = colors.navAccent.copy(alpha = 0.75f),
                    style = Stroke(
                        width = 3.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                    )
                )
            }

            // 4. Draw Actual Driven GPS Track
            if (actualTrack.size >= 2) {
                for (i in 0 until actualTrack.size - 1) {
                    val a = actualTrack[i]
                    val b = actualTrack[i + 1]
                    val p1 = toScreen(a.latitude, a.longitude)
                    val p2 = toScreen(b.latitude, b.longitude)
                    val segColor = if (a.isSpeeding || b.isSpeeding) TrafficBusyLightRed else colors.deliveredAccent
                    drawLine(
                        color = Color(0xFF0F172A).copy(alpha = 0.45f),
                        start = p1,
                        end = p2,
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = segColor,
                        start = p1,
                        end = p2,
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            if (actualTrack.isNotEmpty()) {
                val livePt = toScreen(actualTrack.last().latitude, actualTrack.last().longitude)
                drawCircle(color = colors.navAccent.copy(alpha = 0.25f), radius = 16.dp.toPx(), center = livePt)
                drawCircle(color = colors.navAccent, radius = 8.dp.toPx(), center = livePt)
                drawCircle(color = Color.White, radius = 3.5.dp.toPx(), center = livePt)
            }

            // 5. Draw Interactive Drop Pins with Numbers & Street Labels
            stopScreenPts.forEach { (origIdx, stop, pt) ->
                val isSelected = stop.id == selectedStopId
                val isNav = stop.id == activeNavigatingStopId
                val pinColor = when {
                    isSelected -> colors.warningAccent
                    isNav -> colors.navAccent
                    stop.delivered -> colors.deliveredAccent
                    else -> colors.brandPrimary
                }
                val numTextColor = when {
                    isSelected -> Color.White
                    isNav -> Color.White
                    stop.delivered -> colors.onDelivered
                    else -> colors.onBrandPrimary
                }
                val outerR = if (isSelected) 18.dp.toPx() else 14.dp.toPx()

                if (isSelected) {
                    drawCircle(
                        color = colors.warningAccent.copy(alpha = 0.28f),
                        radius = 25.dp.toPx(),
                        center = pt
                    )
                }
                drawCircle(color = pinColor, radius = outerR, center = pt)
                drawCircle(color = colors.cardSurface, radius = outerR - 3.dp.toPx(), center = pt)
                drawCircle(color = pinColor, radius = outerR - 5.dp.toPx(), center = pt)

                val numText = stop.dropNumber ?: "${origIdx + 1}"
                val numLayout = textMeasurer.measure(
                    text = numText,
                    style = TextStyle(
                        color = numTextColor,
                        fontSize = if (isSelected) 12.sp else 10.sp,
                        fontWeight = FontWeight.Black
                    )
                )
                drawText(
                    textLayoutResult = numLayout,
                    topLeft = Offset(
                        pt.x - numLayout.size.width / 2f,
                        pt.y - numLayout.size.height / 2f
                    )
                )

                // Show street callout pill when zoomed in closely or selected
                if (isSelected || clampedZoom >= 14.2f) {
                    val label = if (isSelected && stop.timeWindow != null) {
                        "${stop.name.take(15)} · ${stop.timeWindow.startTime}-${stop.timeWindow.endTime}"
                    } else {
                        stop.name.take(16)
                    }
                    val labelLayout = textMeasurer.measure(
                        text = label,
                        style = TextStyle(
                            color = if (isSelected) colors.onBrandPrimary else colors.textPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    val padH = 6.dp.toPx()
                    val padV = 3.dp.toPx()
                    val boxW = labelLayout.size.width + padH * 2f
                    val boxH = labelLayout.size.height + padV * 2f
                    val boxLeft = pt.x - boxW / 2f
                    val boxTop = pt.y - outerR - boxH - 4.dp.toPx()
                    drawRoundRect(
                        color = if (isSelected) colors.brandPrimary else colors.cardSurface.copy(alpha = 0.94f),
                        topLeft = Offset(boxLeft, boxTop),
                        size = androidx.compose.ui.geometry.Size(boxW, boxH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    drawText(
                        textLayoutResult = labelLayout,
                        topLeft = Offset(boxLeft + padH, boxTop + padV)
                    )
                }
            }
        }

        // Top-left Status Badge (Zoom + Interaction hint)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.cardSurface.copy(alpha = 0.92f))
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text(
                text = "Z${clampedZoom.roundToInt()} · Drag & Pinch · Tap Pin",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.brandPrimary
            )
        }

        // Bottom-left Road Routing / Traffic Status Pill
        if (isFetchingRoadRoute) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.cardSurface.copy(alpha = 0.94f))
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("map_routing_loading_pill"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = colors.brandPrimary,
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Routing streets & traffic...",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.brandPrimary
                )
            }
        } else if (routingMode == RoutingMode.APPROXIMATE) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.cardSurface.copy(alpha = 0.95f))
                    .border(1.dp, colors.warningAccent, RoundedCornerShape(10.dp))
                    .clickable { onRetryRoadRoute() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("map_retry_road_route_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry Road Route",
                    tint = colors.warningAccent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Approx. fallback · Tap to retry",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.warningText
                )
            }
        }

        // Top-right Fullscreen + Fit All Controls
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onFitAll,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.cardSurface.copy(alpha = 0.94f))
                    .border(1.dp, colors.borderSubtle, CircleShape)
                    .testTag("map_fit_all_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "Fit All Drops",
                    tint = colors.brandPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onToggleFullscreen,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.cardSurface.copy(alpha = 0.94f))
                    .border(1.dp, colors.borderSubtle, CircleShape)
                    .testTag("map_fullscreen_toggle_button")
            ) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullscreen) "Exit Fullscreen Map" else "Expand Map Fullscreen",
                    tint = colors.brandPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Right-side Zoom In / Zoom Out Controls
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    val nextZoom = (clampedZoom + 1f).coerceIn(10f, 18f)
                    onUpdateCamera(centerCoord, nextZoom)
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.cardSurface.copy(alpha = 0.95f))
                    .border(1.dp, colors.borderSubtle, CircleShape)
                    .testTag("map_zoom_in_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = colors.brandPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = {
                    val nextZoom = (clampedZoom - 1f).coerceIn(10f, 18f)
                    onUpdateCamera(centerCoord, nextZoom)
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.cardSurface.copy(alpha = 0.95f))
                    .border(1.dp, colors.borderSubtle, CircleShape)
                    .testTag("map_zoom_out_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = colors.brandPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Bottom-right OpenStreetMap attribution
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.cardSurface.copy(alpha = 0.90f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = "© OpenStreetMap · OSRM",
                fontSize = 10.sp,
                color = colors.textMuted,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SelectedMapDropDetailCard(
    stop: DeliveryStop,
    stopNumber: String,
    isNavigating: Boolean,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit,
    onNavigate: () -> Unit,
    onToggleDelivered: () -> Unit
) {
    val colors = RouteTheme.colors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("selected_map_drop_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
        border = BorderStroke(1.5.dp, colors.brandPrimary)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (stop.delivered) colors.deliveredAccent else colors.brandPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#$stopNumber",
                            color = if (stop.delivered) colors.onDelivered else colors.onBrandPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stop.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = stop.orderId ?: "Stop #$stopNumber",
                            fontSize = 11.sp,
                            color = colors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // High-contrast ETA pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (stop.delivered) colors.deliveredBg else colors.etaPillBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (stop.delivered) "DONE" else "ETA ${stop.estimatedArrival ?: "--:--"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = if (stop.delivered) colors.deliveredAccent else colors.etaPillText
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Selected Pin",
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = colors.brandPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = stop.address,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                    if (stop.latitude != null && stop.longitude != null) {
                        Text(
                            text = "${"%.5f".format(stop.latitude)}, ${"%.5f".format(stop.longitude)}",
                            fontSize = 10.sp,
                            color = colors.textMuted
                        )
                    }
                }
            }

            stop.timeWindow?.let { win ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.slotCardBg)
                        .border(1.dp, colors.slotCardBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = colors.slotCardAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Slot: ${win.label ?: "${win.startTime} – ${win.endTime}"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.slotCardText
                        )
                    }
                    Text(
                        text = if (stop.delivered) "DELIVERED" else "ETA ${stop.estimatedArrival ?: "--:--"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (stop.delivered) colors.deliveredAccent else colors.slotCardAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenDetail,
                    modifier = Modifier
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.borderSubtle),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(imageVector = Icons.Default.OpenInNew, contentDescription = "Details", tint = colors.brandPrimary, modifier = Modifier.size(16.dp))
                }

                Button(
                    onClick = onNavigate,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNavigating) colors.navAccent else colors.brandPrimary,
                        contentColor = if (isNavigating) Color.White else colors.onBrandPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Navigate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onToggleDelivered,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (stop.delivered) colors.deliveredBg else colors.deliveredAccent,
                        contentColor = if (stop.delivered) colors.deliveredAccent else colors.onDelivered
                    ),
                    border = if (stop.delivered) BorderStroke(1.dp, colors.deliveredAccent) else null,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (stop.delivered) "Done ✓" else "Mark Done",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
