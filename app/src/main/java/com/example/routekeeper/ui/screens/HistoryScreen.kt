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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.routekeeper.data.model.SavedRoute
import com.example.routekeeper.data.service.RoutingService
import com.example.routekeeper.ui.theme.RouteTheme
import com.example.routekeeper.ui.viewmodel.RouteViewModel
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    viewModel: RouteViewModel,
    onSelectRoute: (SavedRoute) -> Unit,
    onNavigateToDriverProgress: (SavedRoute) -> Unit
) {
    val savedRoutes by viewModel.savedRoutes.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val colors = RouteTheme.colors
    var pendingDeleteRoute by remember { mutableStateOf<SavedRoute?>(null) }

    pendingDeleteRoute?.let { routeToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteRoute = null },
            title = {
                Text(
                    text = "Delete Saved Route?",
                    fontWeight = FontWeight.Bold,
                    color = colors.brandPrimary
                )
            },
            text = {
                Text(
                    text = "Remove \"${routeToDelete.name}\" and its recorded GPS telemetry logs from history?",
                    color = colors.textPrimary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSavedRoute(routeToDelete.id)
                        pendingDeleteRoute = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.dangerAccent,
                        contentColor = Color.White
                    )
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteRoute = null }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("history_screen_scroll"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Past Delivery Routes",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.brandPrimary
            )
            Text(
                text = "Completed manifests, GPS route telemetry, and driver progress logs.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
        }

        if (savedRoutes.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                    border = BorderStroke(1.dp, colors.borderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.History, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No saved routes yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "When you complete or save an active route, it will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted
                        )
                    }
                }
            }
        }

        items(savedRoutes, key = { it.id }) { route ->
            val deliveredCount = route.stops.count { it.delivered }
            val gpsCount = route.actualTrack.size
            val maxSpeedMph = if (route.actualTrack.isNotEmpty()) route.actualTrack.maxOf { it.speedMph } else 0f
            val peakLabel = if (preferences.useKmh) "${(maxSpeedMph * 1.60934f).roundToInt()} km/h" else "${maxSpeedMph.roundToInt()} mph"

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectRoute(route) }
                    .testTag("saved_route_card_${route.id}"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                border = BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${route.stops.size} Stops ($deliveredCount Delivered) · ${RoutingService.formatDistance(route.distanceMeters)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Saved on ${route.savedAt}" + if (gpsCount > 0) " · $gpsCount GPS pts (Peak $peakLabel)" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (gpsCount > 0) colors.deliveredAccent else colors.textMuted
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.accentContainer)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${route.stops.size} Stops",
                                    color = colors.onAccentContainer,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            IconButton(
                                onClick = { pendingDeleteRoute = route },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("delete_saved_route_${route.id}")
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onNavigateToDriverProgress(route) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("history_driver_progress_${route.id}"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.brandPrimary)
                        ) {
                            Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = colors.brandPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Driver Progress", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.brandPrimary)
                        }

                        Button(
                            onClick = { onSelectRoute(route) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("history_load_route_${route.id}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.brandPrimary,
                                contentColor = colors.onBrandPrimary
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Route, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Route", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
