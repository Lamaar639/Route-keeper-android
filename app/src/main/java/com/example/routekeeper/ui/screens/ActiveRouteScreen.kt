package com.example.routekeeper.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.routekeeper.data.model.DeliveryStop
import com.example.routekeeper.data.service.RoutingService
import com.example.routekeeper.ui.theme.AmberAccess
import com.example.routekeeper.ui.theme.BlueNav
import com.example.routekeeper.ui.theme.BorderClean
import com.example.routekeeper.ui.theme.CleanSurface
import com.example.routekeeper.ui.theme.EmeraldDelivered
import com.example.routekeeper.ui.theme.MintAccent
import com.example.routekeeper.ui.theme.MintAccentContainer
import com.example.routekeeper.ui.theme.PinePrimary
import com.example.routekeeper.ui.theme.PinePrimaryContainer
import com.example.routekeeper.ui.theme.TextMuted
import com.example.routekeeper.ui.theme.TextPrimary
import com.example.routekeeper.ui.theme.WarmSandBg
import com.example.routekeeper.ui.viewmodel.RouteViewModel

@Composable
fun ActiveRouteScreen(
    viewModel: RouteViewModel,
    onNavigateToStopDetail: (DeliveryStop) -> Unit,
    onSaveRoute: () -> Unit
) {
    val context = LocalContext.current
    val draft by viewModel.activeDraft.collectAsState()
    val paceStats by viewModel.paceStats.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredStops by viewModel.filteredStops.collectAsState()
    val podStop by viewModel.podSheetStop.collectAsState()

    var viewMode by remember { mutableStateOf("list") } // "list" or "map"

    // Proof of delivery bottom sheet
    podStop?.let { stop ->
        ProofOfDeliverySheet(
            stop = stop,
            onDismiss = { viewModel.closePodSheet() },
            onConfirmDelivery = { pod ->
                viewModel.markStopDelivered(stop.id, pod)
                viewModel.closePodSheet()
            }
        )
    }

    if (draft == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No active route loaded. Please import a manifest.", color = TextMuted)
        }
        return
    }

    val activeDraft = draft!!

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("active_route_scroll"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Driver Pace & Shift HUD Strip
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("driver_pace_hud"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PinePrimary)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = MintAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "DRIVER PACE: ${paceStats.stopsPerHour} STOPS/HR",
                                color = MintAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Text(
                            text = "Finish ~ ${paceStats.projectedFinishTime}",
                            color = CleanSurface,
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
                        color = MintAccent,
                        trackColor = PinePrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${paceStats.completedStops} / ${paceStats.totalStops} Delivered · ${RoutingService.formatDistance(activeDraft.distanceMeters)}",
                            color = Color(0xFFC7D3CD),
                            fontSize = 11.sp
                        )
                        Text(
                            text = "${(paceStats.percentComplete * 100).toInt()}% Done",
                            color = MintAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
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
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = TextMuted)
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
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MintAccentContainer)
                            .testTag("save_route_button")
                    ) {
                        Icon(imageVector = Icons.Default.Bookmark, contentDescription = "Save Route", tint = PinePrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Alternative Routes Switcher
        if (activeDraft.alternativeRoutes.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeDraft.alternativeRoutes.forEach { opt ->
                        FilterChip(
                            selected = activeDraft.selectedAlternativeId == opt.id,
                            onClick = { viewModel.selectAlternative(opt.id) },
                            label = { Text("${opt.badge} · ${RoutingService.formatDistance(opt.distanceMeters)}", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PinePrimary,
                                selectedLabelColor = CleanSurface
                            )
                        )
                    }
                }
            }
        }

        // MAP VIEW (Canvas Interactive Drawing)
        if (viewMode == "map") {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .testTag("route_map_canvas_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = WarmSandBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderClean)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                            val w = size.width
                            val h = size.height

                            val lats = activeDraft.stops.mapNotNull { it.latitude }
                            val lons = activeDraft.stops.mapNotNull { it.longitude }

                            if (lats.isNotEmpty() && lons.isNotEmpty()) {
                                val minLat = lats.minOrNull() ?: 51.5
                                val maxLat = lats.maxOrNull() ?: 51.53
                                val minLon = lons.minOrNull() ?: -0.16
                                val maxLon = lons.maxOrNull() ?: -0.08

                                val latSpan = (maxLat - minLat).coerceAtLeast(0.005)
                                val lonSpan = (maxLon - minLon).coerceAtLeast(0.005)

                                fun toPixel(lat: Double, lon: Double): Offset {
                                    val x = ((lon - minLon) / lonSpan * w).toFloat().coerceIn(10f, w - 10f)
                                    val y = ((maxLat - lat) / latSpan * h).toFloat().coerceIn(10f, h - 10f)
                                    return Offset(x, y)
                                }

                                // Draw road path lines
                                val points = activeDraft.stops.mapNotNull {
                                    if (it.latitude != null && it.longitude != null) {
                                        toPixel(it.latitude, it.longitude)
                                    } else null
                                }

                                if (points.size >= 2) {
                                    for (i in 0 until points.size - 1) {
                                        drawLine(
                                            color = BlueNav,
                                            start = points[i],
                                            end = points[i + 1],
                                            strokeWidth = 4.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }

                                // Draw pins
                                points.forEachIndexed { i, pt ->
                                    val stop = activeDraft.stops[i]
                                    val pinColor = if (stop.delivered) EmeraldDelivered else PinePrimary
                                    drawCircle(
                                        color = pinColor,
                                        radius = 14.dp.toPx(),
                                        center = pt
                                    )
                                    drawCircle(
                                        color = CleanSurface,
                                        radius = 11.dp.toPx(),
                                        center = pt
                                    )
                                    drawCircle(
                                        color = pinColor,
                                        radius = 8.dp.toPx(),
                                        center = pt
                                    )
                                }
                            }
                        }

                        // Map Legend Overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CleanSurface.copy(alpha = 0.9f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "OpenStreetMap Network",
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // LIST VIEW (Stop Cards)
        if (viewMode == "list") {
            itemsIndexed(filteredStops) { index, stop ->
                StopRowCard(
                    stop = stop,
                    index = index,
                    onTapCard = { onNavigateToStopDetail(stop) },
                    onToggleDelivered = {
                        viewModel.openPodSheet(stop)
                    },
                    onCallCustomer = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${stop.phone}"))
                        context.startActivity(intent)
                    },
                    onNavigateToLocation = {
                        val uri = if (stop.latitude != null && stop.longitude != null) {
                            Uri.parse("geo:${stop.latitude},${stop.longitude}?q=${stop.latitude},${stop.longitude}(${Uri.encode(stop.name)})")
                        } else {
                            Uri.parse("geo:0,0?q=${Uri.encode(stop.address)}")
                        }
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun StopRowCard(
    stop: DeliveryStop,
    index: Int,
    onTapCard: () -> Unit,
    onToggleDelivered: () -> Unit,
    onCallCustomer: () -> Unit,
    onNavigateToLocation: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTapCard() }
            .testTag("stop_item_card_${stop.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (stop.delivered) Color(0xFFF0FDF4) else CleanSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (stop.delivered) EmeraldDelivered else BorderClean
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Drop Number, Name, Status Badge, Quick Checkmark
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drop # badge
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (stop.delivered) EmeraldDelivered else PinePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    if (stop.delivered) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = CleanSurface, modifier = Modifier.size(18.dp))
                    } else {
                        Text(
                            text = stop.dropNumber ?: "${index + 1}",
                            color = MintAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stop.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${stop.orderId ?: "Order"} · ETA ${stop.estimatedArrival ?: "09:00"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                // Time window indicator
                stop.timeWindow?.let { win ->
                    val statusColor = when (stop.windowStatus) {
                        "late" -> AmberAccess
                        "on-time" -> EmeraldDelivered
                        else -> PinePrimary
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = win.startTime,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Quick POD / Delivered Toggle Button
                IconButton(
                    onClick = onToggleDelivered,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("toggle_delivered_button_${stop.id}")
                ) {
                    Icon(
                        imageVector = if (stop.delivered) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Mark Delivered",
                        tint = if (stop.delivered) EmeraldDelivered else Color.Gray,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Address line
            Text(
                text = stop.address,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontSize = 13.sp,
                maxLines = 2
            )

            // Proof of Delivery info (if delivered)
            if (stop.delivered && stop.pod != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "POD: ${stop.pod.disposition} (${stop.pod.timestamp})",
                        color = EmeraldDelivered,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Call & Navigate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNavigateToLocation,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PinePrimaryContainer,
                        contentColor = CleanSurface
                    )
                ) {
                    Icon(imageVector = Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Navigate", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onCallCustomer,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = PinePrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call Recipient", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PinePrimary)
                }
            }
        }
    }
}
