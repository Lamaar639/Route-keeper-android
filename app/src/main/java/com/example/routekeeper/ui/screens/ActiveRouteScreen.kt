package com.example.routekeeper.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.routekeeper.data.model.DeliveryStop
import com.example.routekeeper.data.service.RoutingService
import com.example.routekeeper.ui.components.InteractiveRouteMapCard
import com.example.routekeeper.ui.theme.RouteTheme
import com.example.routekeeper.ui.util.rememberRouteHapticController
import com.example.routekeeper.ui.viewmodel.RouteViewModel

@Composable
fun ActiveRouteScreen(
    viewModel: RouteViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToOptions: () -> Unit,
    onNavigateToDriverProgress: () -> Unit,
    onNavigateToStopDetail: (DeliveryStop) -> Unit,
    onSaveRoute: () -> Unit
) {
    val context = LocalContext.current
    val draft by viewModel.activeDraft.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val paceStats by viewModel.paceStats.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredStops by viewModel.filteredStops.collectAsState()
    val gpsStatusMessage by viewModel.gpsStatusMessage.collectAsState()
    val isOptimizing by viewModel.isOptimizing.collectAsState()
    val optimizingStep by viewModel.optimizingStep.collectAsState()
    val isFetchingRoadRoute by viewModel.isFetchingRoadRoute.collectAsState()
    val colors = RouteTheme.colors
    val haptics = rememberRouteHapticController()

    var viewMode by remember { mutableStateOf("list") } // "list" or "map"
    var pendingNavigationStop by remember { mutableStateOf<DeliveryStop?>(null) }
    var selectedMapStopId by remember { mutableStateOf<String?>(null) }
    var mapFocusToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(draft?.id) {
        selectedMapStopId = null
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val navStop = pendingNavigationStop
        pendingNavigationStop = null
        if (navStop != null) {
            if (granted) {
                viewModel.startNavigationTrackingForStop(navStop)
            }
            viewModel.launchExternalNavigation(context, navStop)
        } else if (granted) {
            viewModel.startRouteTracking()
        }
    }

    fun triggerNavigateWithAutoGps(stop: DeliveryStop) {
        if (viewModel.hasLocationPermission()) {
            viewModel.startNavigationTrackingForStop(stop)
            viewModel.launchExternalNavigation(context, stop)
        } else {
            pendingNavigationStop = stop
            val permsToRequest = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
            locationPermissionLauncher.launch(permsToRequest)
        }
    }

    if (draft == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No active route loaded. Please import a manifest.", color = colors.textMuted)
        }
        return
    }

    val activeDraft = draft!!
    val nextStopId = remember(activeDraft.stops) {
        activeDraft.stops.firstOrNull { !it.delivered }?.id
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("active_route_scroll"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header Bar: Clean Back to Home, Title, Video Export, Options & Bookmark
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
                            .testTag("route_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = colors.brandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = activeDraft.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "${activeDraft.stops.size} stops · Start ${activeDraft.departureStartTime} · ${RoutingService.formatDistance(activeDraft.distanceMeters)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Video Animation Export
                    IconButton(
                        onClick = { viewModel.openVideoExportDialog() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.cardSurface)
                            .border(1.dp, colors.borderSubtle, CircleShape)
                            .testTag("route_video_export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Export Animated Video",
                            tint = colors.brandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Route Options (Tune departure/dwell)
                    IconButton(
                        onClick = onNavigateToOptions,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.cardSurface)
                            .border(1.dp, colors.borderSubtle, CircleShape)
                            .testTag("route_options_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Route Options",
                            tint = colors.brandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Bookmark / Save Route
                    IconButton(
                        onClick = onSaveRoute,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.accentContainer)
                            .testTag("save_route_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Save Route",
                            tint = colors.onAccentContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Optimization / Geocoding progress banner while stops are being geocoded in background
        if (isOptimizing) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("route_optimizing_banner"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.accentContainer),
                    border = BorderStroke(1.dp, colors.brandPrimary.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = colors.brandPrimary,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = optimizingStep.ifBlank { "Optimizing route..." },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onAccentContainer
                        )
                    }
                }
            }
        }

        // Driver Pace & Background Segmented GPS HUD Strip
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("driver_pace_hud"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.heroCardBg),
                border = BorderStroke(1.dp, colors.borderSubtle.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "DRIVER PACE: ${paceStats.stopsPerHour} STOPS/HR",
                                color = colors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Text(
                            text = "Finish ~ ${paceStats.projectedFinishTime}",
                            color = colors.onHeroPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { paceStats.percentComplete },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = colors.accent,
                        trackColor = colors.heroCardContainer
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${paceStats.completedStops} / ${paceStats.totalStops} Delivered · ${activeDraft.trackSegments.size} GPS Legs",
                            color = colors.onHeroMuted,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "${(paceStats.percentComplete * 100).toInt()}% Done",
                            color = colors.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // GPS Recording Control + Driver Progress Page Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (activeDraft.isRecordingGps) {
                                    viewModel.stopRouteTracking()
                                } else if (viewModel.hasLocationPermission()) {
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
                            modifier = Modifier
                                .weight(1.1f)
                                .height(48.dp)
                                .testTag("start_route_gps_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeDraft.isRecordingGps) colors.dangerAccent else colors.accent,
                                contentColor = if (activeDraft.isRecordingGps) colors.onHeroPrimary else colors.onAccent
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = if (activeDraft.isRecordingGps) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when {
                                    activeDraft.isRecordingGps -> "Recording (${activeDraft.actualTrack.size})"
                                    activeDraft.isTrackCombined -> "Combined (${activeDraft.actualTrack.size})"
                                    activeDraft.actualTrack.isNotEmpty() -> "GPS Paused (${activeDraft.actualTrack.size})"
                                    else -> "Auto GPS Ready"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = onNavigateToDriverProgress,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("open_driver_progress_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.heroCardContainer,
                                contentColor = colors.onHeroPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Driver Progress",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    val lastCrumb = activeDraft.actualTrack.lastOrNull()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = when {
                                    activeDraft.isRecordingGps -> colors.dangerAccent
                                    activeDraft.isTrackCombined -> colors.deliveredAccent
                                    else -> colors.accent
                                },
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = gpsStatusMessage,
                                color = colors.onHeroMuted,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                        if (lastCrumb != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            val speedText = if (preferences.useKmh) {
                                "${lastCrumb.speedKmh.toInt()} / ${(lastCrumb.speedLimitMph * 1.60934f).toInt()} km/h"
                            } else {
                                "${lastCrumb.speedMph.toInt()} / ${lastCrumb.speedLimitMph.toInt()} mph"
                            }
                            Text(
                                text = speedText,
                                color = if (lastCrumb.isSpeeding) colors.dangerBorder else colors.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }

        // Quick-Finder Bar & View Mode Toggle
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_finder_search_input"),
                    placeholder = { Text("Quick-Find: customer, address, order #", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = colors.textMuted)
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                        SegmentedButton(
                            selected = viewMode == "list",
                            onClick = { viewMode = "list" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Icon(imageVector = Icons.Default.ViewList, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("List (${filteredStops.size})", fontSize = 12.sp)
                        }
                        SegmentedButton(
                            selected = viewMode == "map",
                            onClick = { viewMode = "map" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Icon(imageVector = Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Route Map", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    IconButton(
                        onClick = onSaveRoute,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.accentContainer)
                            .testTag("save_route_secondary_button")
                    ) {
                        Icon(imageVector = Icons.Default.Bookmark, contentDescription = "Save Route", tint = colors.onAccentContainer, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Alternative Routes Switcher (Fastest / Shortest / Reverse - preserves delivered stops)
        if (activeDraft.alternativeRoutes.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(activeDraft.alternativeRoutes, key = { it.id }) { opt ->
                            FilterChip(
                                selected = activeDraft.selectedAlternativeId == opt.id,
                                onClick = { viewModel.selectAlternative(opt.id) },
                                label = {
                                    Text(
                                        "${opt.badge} · ${RoutingService.formatDistance(opt.distanceMeters)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.brandPrimary,
                                    selectedLabelColor = colors.onBrandPrimary,
                                    labelColor = colors.textPrimary
                                ),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            )
                        }
                    }
                    Text(
                        text = "Completed deliveries remain marked when switching route mode",
                        fontSize = 10.sp,
                        color = colors.textMuted
                    )
                }
            }
        }

        // INTERACTIVE MAP VIEW (Road-following OSRM route + Google Maps traffic colors + street tiles)
        if (viewMode == "map") {
            item {
                InteractiveRouteMapCard(
                    routeId = activeDraft.id,
                    stops = activeDraft.stops,
                    plannedPath = activeDraft.path,
                    trafficSegments = activeDraft.trafficSegments,
                    routeSteps = activeDraft.routeSteps,
                    routingMode = activeDraft.routingMode,
                    trafficSummary = activeDraft.trafficSummary,
                    isFetchingRoadRoute = isFetchingRoadRoute,
                    actualTrack = activeDraft.actualTrack,
                    activeNavigatingStopId = activeDraft.activeNavigatingStopId,
                    selectedStopId = selectedMapStopId,
                    focusRequestToken = mapFocusToken,
                    onSelectStop = { stop -> selectedMapStopId = stop?.id },
                    onOpenStopDetail = { stop -> onNavigateToStopDetail(stop) },
                    onNavigateStop = { stop -> triggerNavigateWithAutoGps(stop) },
                    onToggleDelivered = { stop ->
                        haptics.onMarkDoneClick(stop.delivered)
                        viewModel.markStopDelivered(stop.id)
                    },
                    onRetryRoadRoute = { viewModel.refreshRoadNavigationForCurrentDraft() }
                )
            }

            item {
                Text(
                    text = "DELIVERIES ON ROUTE (${filteredStops.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.testTag("map_view_deliveries_header")
                )
            }
        }

        // DELIVERY STOP CARDS (Shown in List View AND directly underneath Map View)
        itemsIndexed(filteredStops, key = { _, stop -> stop.id }) { index, stop ->
            val isCurrentlyNavigating = activeDraft.isRecordingGps && activeDraft.activeNavigatingStopId == stop.id
            val isFocusedOnMap = viewMode == "map" && selectedMapStopId == stop.id
            val isNextStop = stop.id == nextStopId
            StopRowCard(
                stop = stop,
                index = index,
                isNextStop = isNextStop,
                isNavigatingNow = isCurrentlyNavigating,
                isMapFocused = isFocusedOnMap,
                showFocusOnMapButton = viewMode == "map" && stop.latitude != null && stop.longitude != null,
                onFocusOnMap = {
                    selectedMapStopId = stop.id
                    mapFocusToken++
                },
                onTapCard = { onNavigateToStopDetail(stop) },
                onToggleDelivered = {
                    haptics.onMarkDoneClick(stop.delivered)
                    viewModel.markStopDelivered(stop.id)
                },
                onCallCustomer = {
                    if (stop.phone.isNotBlank() && stop.phone != "N/A") {
                        runCatching {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${stop.phone}"))
                            context.startActivity(intent)
                        }
                    }
                },
                onNavigateToLocation = {
                    triggerNavigateWithAutoGps(stop)
                }
            )
        }
    }
}

@Composable
fun StopRowCard(
    stop: DeliveryStop,
    index: Int,
    isNextStop: Boolean = false,
    isNavigatingNow: Boolean = false,
    isMapFocused: Boolean = false,
    showFocusOnMapButton: Boolean = false,
    onFocusOnMap: () -> Unit = {},
    onTapCard: () -> Unit,
    onToggleDelivered: () -> Unit,
    onCallCustomer: () -> Unit,
    onNavigateToLocation: () -> Unit
) {
    val colors = RouteTheme.colors
    val cardContainer = when {
        stop.delivered -> colors.deliveredBg
        else -> colors.cardSurface
    }
    val cardBorderWidth = if (isNextStop || isNavigatingNow || isMapFocused) 2.dp else 1.dp
    val cardBorderColor = when {
        isMapFocused -> colors.warningAccent
        isNavigatingNow -> colors.navAccent
        isNextStop -> colors.brandPrimary
        stop.delivered -> colors.deliveredAccent
        else -> colors.borderSubtle
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTapCard() }
            .testTag("stop_item_card_${stop.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainer),
        border = BorderStroke(cardBorderWidth, cardBorderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (isNextStop && !stop.delivered) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.brandPrimary)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                            .testTag("next_stop_badge_${stop.id}")
                    ) {
                        Text(
                            text = "NEXT STOP",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.onBrandPrimary,
                            letterSpacing = 0.7.sp
                        )
                    }
                    if (isNavigatingNow) {
                        Text(
                            text = "GPS RECORDING ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.navAccent
                        )
                    }
                }
            }

            // Top Row: Drop Number, Name/Order, High-Contrast ETA Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (stop.delivered) colors.deliveredAccent else colors.brandPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    if (stop.delivered) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Delivered",
                            tint = colors.onDelivered,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = stop.dropNumber ?: "${index + 1}",
                            color = colors.onBrandPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stop.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = stop.orderId ?: "Delivery Stop #${stop.dropNumber ?: (index + 1)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // High-contrast ETA Pill (especially elevated on Next Stop)
                val etaText = stop.estimatedArrival ?: "09:00"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                stop.delivered -> colors.deliveredAccent.copy(alpha = 0.16f)
                                isNextStop -> colors.etaPillBg
                                else -> colors.cardSurfaceVariant
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("stop_eta_badge_${stop.id}")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (stop.delivered) Icons.Default.CheckCircle else Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = when {
                                stop.delivered -> colors.deliveredAccent
                                isNextStop -> colors.etaPillText
                                else -> colors.textPrimary
                            },
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (stop.delivered) "DONE" else "ETA $etaText",
                            fontSize = if (isNextStop && !stop.delivered) 13.sp else 12.sp,
                            fontWeight = FontWeight.Black,
                            color = when {
                                stop.delivered -> colors.deliveredAccent
                                isNextStop -> colors.etaPillText
                                else -> colors.textPrimary
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Address line
            Text(
                text = stop.address,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                fontSize = 13.sp,
                maxLines = 2
            )

            // Prominent Customer Requested Time Slot Card
            stop.timeWindow?.let { win ->
                val statusColor = when (stop.windowStatus) {
                    "late" -> colors.warningAccent
                    "on-time" -> colors.deliveredAccent
                    else -> colors.slotCardAccent
                }
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.slotCardBg,
                    border = BorderStroke(1.5.dp, colors.slotCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(colors.slotCardAccent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = colors.cardSurface,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "CUSTOMER BOOKED TIME SLOT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colors.slotCardAccent,
                                    letterSpacing = 0.6.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(statusColor.copy(alpha = 0.16f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (stop.windowStatus == "late") "⚠ LATE" else if (stop.windowStatus == "on-time") "✓ ON-TIME" else "FLEXIBLE",
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = win.label ?: "${win.startTime} – ${win.endTime}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                color = colors.slotCardText
                            )

                            Text(
                                text = "ETA ${stop.estimatedArrival ?: "09:00"}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = colors.slotCardText
                            )
                        }
                    }
                }
            }

            if (isNavigatingNow) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        tint = colors.dangerAccent,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Background GPS & speed recording active for this delivery",
                        color = colors.navAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (stop.delivered && !stop.deliveredAt.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "✓ Completed at ${stop.deliveredAt}",
                    color = colors.deliveredAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // One-Handed Thumb Action Row: Navigate + Prominent 'Mark Done' + Call (+ Map Focus)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onNavigateToLocation,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("navigate_stop_button_${stop.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNavigatingNow) colors.navAccent else colors.brandPrimary,
                        contentColor = colors.onBrandPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isNavigatingNow) "GPS On" else "Navigate",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // Prominent One-Handed Thumb 'Mark Done' Button (positioned naturally in thumb sweep)
                if (!stop.delivered) {
                    Button(
                        onClick = onToggleDelivered,
                        modifier = Modifier
                            .weight(1.15f)
                            .height(52.dp)
                            .testTag("mark_done_button_${stop.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.deliveredAccent,
                            contentColor = colors.onDelivered
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Mark Done",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Mark Done",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onToggleDelivered,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("mark_done_button_${stop.id}"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, colors.deliveredAccent),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.deliveredAccent),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Undo Completed",
                            tint = colors.deliveredAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Completed",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                val canCall = stop.phone.isNotBlank() && stop.phone != "N/A"
                IconButton(
                    onClick = onCallCustomer,
                    enabled = canCall,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.cardSurfaceVariant)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                        .testTag("call_stop_button_${stop.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Call Customer",
                        tint = if (canCall) colors.brandPrimary else colors.textMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (showFocusOnMapButton) {
                    IconButton(
                        onClick = onFocusOnMap,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.cardSurfaceVariant)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                            .testTag("focus_map_stop_${stop.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "View Drop on Map",
                            tint = colors.brandPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
