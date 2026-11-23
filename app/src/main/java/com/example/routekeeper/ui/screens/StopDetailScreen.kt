package com.example.routekeeper.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.routekeeper.data.model.DeliveryStop
import com.example.routekeeper.ui.theme.RouteTheme
import com.example.routekeeper.ui.util.rememberRouteHapticController
import com.example.routekeeper.ui.viewmodel.RouteViewModel

@Composable
fun StopDetailScreen(
    stop: DeliveryStop,
    viewModel: RouteViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val draft by viewModel.activeDraft.collectAsState()
    val liveStop = draft?.stops?.find { it.id == stop.id } ?: stop
    val colors = RouteTheme.colors
    val haptics = rememberRouteHapticController()

    var isEditingAddress by remember { mutableStateOf(false) }
    var editedAddressText by remember(liveStop.address) { mutableStateOf(liveStop.address) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.startNavigationTrackingForStop(liveStop)
        }
        viewModel.launchExternalNavigation(context, liveStop)
    }

    fun navigateWithAutoGps() {
        if (viewModel.hasLocationPermission()) {
            viewModel.startNavigationTrackingForStop(liveStop)
            viewModel.launchExternalNavigation(context, liveStop)
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
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("stop_detail_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Back Button & Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.cardSurface)
                        .border(1.dp, colors.borderSubtle, CircleShape)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.brandPrimary)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Stop ${liveStop.dropNumber ?: liveStop.sourceSequence}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.brandPrimary
                    )
                    Text(
                        text = liveStop.orderId ?: "Delivery Package",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted
                    )
                }
            }
        }

        // Customer & Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = liveStop.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (liveStop.delivered) colors.deliveredBg else colors.accentContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (liveStop.delivered) "DELIVERED" else "PENDING",
                                color = if (liveStop.delivered) colors.deliveredAccent else colors.onAccentContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = colors.brandPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = liveStop.phone, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val hasCallablePhone = liveStop.phone.isNotBlank() && liveStop.phone != "N/A"
                        OutlinedButton(
                            onClick = {
                                if (hasCallablePhone) {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${liveStop.phone}"))
                                        context.startActivity(intent)
                                    }
                                }
                            },
                            enabled = hasCallablePhone,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.brandPrimary)
                        ) {
                            Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = colors.brandPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Call Customer", color = colors.brandPrimary, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { navigateWithAutoGps() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("stop_detail_navigate_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.brandPrimary,
                                contentColor = colors.onBrandPrimary
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Navigate", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Address & Geocoding Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = colors.brandPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "DELIVERY ADDRESS",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(onClick = { isEditingAddress = !isEditingAddress }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Address", tint = colors.brandPrimary, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (isEditingAddress) {
                        OutlinedTextField(
                            value = editedAddressText,
                            onValueChange = { editedAddressText = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.updateStopAddress(liveStop.id, editedAddressText)
                                    isEditingAddress = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.brandPrimary,
                                    contentColor = colors.onBrandPrimary
                                )
                            ) {
                                Text("Save & Re-Geocode")
                            }
                            OutlinedButton(
                                onClick = { isEditingAddress = false },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Cancel", color = colors.textPrimary)
                            }
                        }
                    } else {
                        Text(
                            text = liveStop.address,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    }

                    if (liveStop.latitude != null && liveStop.longitude != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Coordinates: ${"%.5f".format(liveStop.latitude)}, ${"%.5f".format(liveStop.longitude)} · ${liveStop.geocodedBy ?: "OpenStreetMap"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted
                        )
                    }
                }
            }
        }

        // Schedule & Delivery Window Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = colors.brandPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SCHEDULE & TIME WINDOW",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    liveStop.timeWindow?.let { win ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = colors.slotCardBg,
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, colors.slotCardBorder)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = colors.slotCardAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "CUSTOMER BOOKED WINDOW",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = colors.slotCardAccent,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                when (liveStop.windowStatus) {
                                                    "late" -> colors.warningBg
                                                    "on-time" -> colors.deliveredBg
                                                    else -> colors.brandPrimary.copy(alpha = 0.14f)
                                                }
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = if (liveStop.windowStatus == "late") "⚠ LATE" else if (liveStop.windowStatus == "on-time") "✓ ON-TIME" else "FLEXIBLE",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (liveStop.windowStatus) {
                                                "late" -> colors.warningText
                                                "on-time" -> colors.deliveredAccent
                                                else -> colors.brandPrimary
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = win.label ?: "${win.startTime} – ${win.endTime}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = colors.slotCardText
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Service Dwell Time", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                            Text(
                                text = "%.1f min".format(liveStop.serviceTimeMinutes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.etaPillBg)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "PLANNED ETA",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colors.etaPillText.copy(alpha = 0.85f)
                                )
                                Text(
                                    text = liveStop.estimatedArrival ?: "09:00",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Black,
                                    color = colors.etaPillText
                                )
                            }
                        }
                    }
                }
            }
        }

        // Direct Delivery Completion Card (No POD Section)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DELIVERY COMPLETION",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (liveStop.delivered) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = colors.deliveredAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Completed${liveStop.deliveredAt?.let { " at $it" } ?: ""}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.deliveredAccent
                            )
                        }
                    } else {
                        Text(
                            text = "Marking this delivery complete stops GPS recording until you click Navigate on the next stop.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            haptics.onMarkDoneClick(liveStop.delivered)
                            viewModel.markStopDelivered(liveStop.id)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("stop_detail_complete_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (liveStop.delivered) colors.deliveredBg else colors.deliveredAccent,
                            contentColor = if (liveStop.delivered) colors.deliveredAccent else colors.onDelivered
                        ),
                        border = if (liveStop.delivered) androidx.compose.foundation.BorderStroke(1.dp, colors.deliveredAccent) else null
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (liveStop.delivered) "Done ✓ (Tap to Reopen)" else "Mark Done",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}
