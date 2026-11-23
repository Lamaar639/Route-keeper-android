package com.example.routekeeper.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.routekeeper.data.service.RoutingService
import com.example.routekeeper.ui.theme.RouteTheme
import com.example.routekeeper.ui.util.rememberRouteHapticController
import com.example.routekeeper.ui.viewmodel.RouteViewModel

@Composable
fun HomeScreen(
    viewModel: RouteViewModel,
    onNavigateToRoute: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val activeDraft by viewModel.activeDraft.collectAsState()
    val savedRoutes by viewModel.savedRoutes.collectAsState()
    val paceStats by viewModel.paceStats.collectAsState()
    val colors = RouteTheme.colors
    val haptics = rememberRouteHapticController()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen_scroll"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Title & Welcome Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "RouteKeeper",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.brandPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Delivery Route Planner & Stop Tracker",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.heroCardBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "RouteKeeper",
                        tint = colors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Active Route Card (high-contrast Next Stop ETA + one-handed Mark Done thumb action)
        item {
            if (activeDraft != null) {
                val draft = activeDraft!!
                val nextStopIndex = draft.stops.indexOfFirst { !it.delivered }
                val nextStop = if (nextStopIndex >= 0) draft.stops[nextStopIndex] else null

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_route_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.heroCardBg),
                    border = BorderStroke(1.dp, colors.borderSubtle.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTIVE ROUTE",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accent,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.heroCardContainer)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${paceStats.stopsPerHour} stops/hr pace",
                                    color = colors.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = draft.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.onHeroPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${draft.stops.size} Stops · ${RoutingService.formatDistance(draft.distanceMeters)} · Finish ~${paceStats.projectedFinishTime}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onHeroMuted,
                            fontSize = 13.sp
                        )

                        // Next Stop & High-Contrast ETA Spotlight
                        if (nextStop != null) {
                            val nextDropNum = nextStop.dropNumber ?: "${nextStopIndex + 1}"
                            val nextEtaText = nextStop.estimatedArrival ?: draft.departureStartTime
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("active_route_next_stop_panel"),
                                shape = RoundedCornerShape(12.dp),
                                color = colors.heroCardContainer,
                                border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.45f))
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
                                            text = "NEXT STOP #$nextDropNum",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = colors.accent,
                                            letterSpacing = 0.8.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = nextStop.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.onHeroPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = nextStop.address,
                                            fontSize = 12.sp,
                                            color = colors.onHeroMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    // High-contrast Next Stop ETA badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colors.accent)
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                            .testTag("active_route_next_stop_eta"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.AccessTime,
                                                    contentDescription = null,
                                                    tint = colors.onAccent,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "ETA",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = colors.onAccent
                                                )
                                            }
                                            Text(
                                                text = nextEtaText,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Black,
                                                color = colors.onAccent
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { paceStats.percentComplete },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = colors.accent,
                            trackColor = colors.heroCardContainer
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${paceStats.completedStops} of ${paceStats.totalStops} completed",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onHeroMuted
                            )
                            Text(
                                text = "${(paceStats.percentComplete * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accent,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Thumb-Zone Action Row: Open Route + Prominent Mark Done for Next Stop
                        if (nextStop != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onNavigateToRoute,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("continue_route_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = colors.onAccent
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = "Open Route",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Button(
                                    onClick = {
                                        haptics.onMarkDoneClick(nextStop.delivered)
                                        viewModel.markStopDelivered(nextStop.id)
                                    },
                                    modifier = Modifier
                                        .weight(1.05f)
                                        .height(52.dp)
                                        .testTag("home_active_card_mark_done_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.deliveredAccent,
                                        contentColor = colors.onDelivered
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Mark Done",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = onNavigateToRoute,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("continue_route_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = colors.onAccent
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "All Stops Done · View Summary",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                    border = BorderStroke(1.dp, colors.borderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Active Route",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Import a delivery manifest or load sample deliveries to plan today's stops.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToImport,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.brandPrimary,
                                contentColor = colors.onBrandPrimary
                            )
                        ) {
                            Text("Import Delivery Note", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Quick Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToImport,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("action_import_manifest"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.borderSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.brandPrimary)
                ) {
                    Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Import Manifest", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onNavigateToHistory,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("action_route_history"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.borderSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.brandPrimary)
                ) {
                    Icon(imageVector = Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Past Routes", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        // Stats Dashboard Grid
        item {
            Text(
                text = "TODAY'S SHIFT SUMMARY",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                    border = BorderStroke(1.dp, colors.borderSubtle)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = colors.deliveredAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Completed", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${paceStats.completedStops} / ${paceStats.totalStops}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                    border = BorderStroke(1.dp, colors.borderSubtle)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = colors.brandPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Driver Pace", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${paceStats.stopsPerHour} / hr",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }

        // Recent Routes Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT DELIVERY RUNS",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onNavigateToHistory() }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "See All",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.brandPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        items(savedRoutes.take(3), key = { it.id }) { route ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.loadSavedRouteAsActive(route)
                        onNavigateToRoute()
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                border = BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = route.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "${route.stops.size} stops · ${route.savedAt}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.accentContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = RoutingService.formatDistance(route.distanceMeters),
                            color = colors.onAccentContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
