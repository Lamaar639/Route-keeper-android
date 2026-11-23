package com.example.routekeeper.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.routekeeper.ui.theme.RouteTheme
import com.example.routekeeper.ui.viewmodel.RouteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreRouteReviewScreen(
    viewModel: RouteViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToActiveRoute: () -> Unit
) {
    val draft by viewModel.activeDraft.collectAsState()
    val isOptimizing by viewModel.isOptimizing.collectAsState()
    val optimizingStep by viewModel.optimizingStep.collectAsState()
    val colors = RouteTheme.colors

    var departureTime by remember { mutableStateOf(draft?.departureStartTime ?: "09:00") }
    var dwellMinutes by remember { mutableDoubleStateOf(draft?.defaultDwellMinutes ?: 6.5) }
    var restStopCount by remember { mutableIntStateOf(draft?.restStopCount ?: 2) }
    var showExactTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(draft?.id, draft?.departureStartTime) {
        draft?.departureStartTime?.let { departureTime = it }
        draft?.defaultDwellMinutes?.let { dwellMinutes = it }
        draft?.restStopCount?.let { restStopCount = it }
    }

    val departureOptions = listOf("07:30", "08:00", "08:30", "09:00", "09:30", "10:00")
    val stops = draft?.stops ?: emptyList()
    val wasAutoExtracted = draft?.wasDepartureAutoExtracted == true

    if (showExactTimePicker) {
        val parts = departureTime.split(":")
        val initHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
        val initMin = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val timePickerState = rememberTimePickerState(
            initialHour = initHour,
            initialMinute = initMin,
            is24Hour = true
        )
        var useKeyboardInput by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showExactTimePicker = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Exact Start Time",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.brandPrimary
                    )
                    IconButton(onClick = { useKeyboardInput = !useKeyboardInput }) {
                        Icon(
                            imageVector = if (useKeyboardInput) Icons.Default.Schedule else Icons.Default.Keyboard,
                            contentDescription = "Toggle Clock or Keyboard Time Input",
                            tint = colors.brandPrimary
                        )
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (useKeyboardInput) {
                        TimeInput(state = timePickerState)
                    } else {
                        TimePicker(state = timePickerState)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val exact = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                        departureTime = exact
                        viewModel.updateDraftDepartureTime(exact)
                        showExactTimePicker = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brandPrimary,
                        contentColor = colors.onBrandPrimary
                    )
                ) {
                    Text("Set Time", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExactTimePicker = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pre_route_review_scroll"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                        .testTag("pre_route_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.brandPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Pre-Route Configuration",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.brandPrimary
                    )
                    Text(
                        text = draft?.sourceName?.let { "Source: $it · Tune departure & stops" }
                            ?: "Tune departure, dwell duration, and rest stops",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted
                    )
                }
            }
        }

        // Shift Start Departure Time (Auto-Extracted or Exact Time Selector)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.AccessTime, contentDescription = null, tint = colors.brandPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ROUTE DEPARTURE TIME",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (wasAutoExtracted) colors.deliveredBg else colors.warningBg
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (wasAutoExtracted) "AUTO-EXTRACTED" else "SELECT EXACT TIME",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (wasAutoExtracted) colors.deliveredAccent else colors.warningAccent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (wasAutoExtracted) colors.deliveredBg else colors.warningBg,
                        border = BorderStroke(
                            1.dp,
                            if (wasAutoExtracted) colors.deliveredAccent.copy(alpha = 0.45f) else colors.warningAccent.copy(alpha = 0.5f)
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (wasAutoExtracted) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = colors.deliveredAccent,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                    }
                                    Text(
                                        text = if (wasAutoExtracted) {
                                            "Detected from route manifest"
                                        } else {
                                            "No departure time found in route"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (wasAutoExtracted) colors.deliveredAccent else colors.warningText
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = departureTime,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = colors.brandPrimary,
                                    modifier = Modifier.testTag("selected_departure_time_text")
                                )
                            }

                            OutlinedButton(
                                onClick = { showExactTimePicker = true },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.brandPrimary),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("pick_exact_departure_time_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = colors.brandPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Exact Time",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.brandPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Quick presets:",
                        fontSize = 11.sp,
                        color = colors.textMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(departureOptions) { time ->
                            FilterChip(
                                selected = departureTime == time,
                                onClick = {
                                    departureTime = time
                                    viewModel.updateDraftDepartureTime(time)
                                },
                                label = { Text(time, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.brandPrimary,
                                    selectedLabelColor = colors.onBrandPrimary
                                ),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            )
                        }
                    }
                }
            }
        }

        // Service Dwell Time Slider (4 to 12 mins)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.HourglassBottom, contentDescription = null, tint = colors.brandPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AVERAGE STOP DWELL TIME",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.accentContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "%.1f min".format(dwellMinutes),
                                fontWeight = FontWeight.Bold,
                                color = colors.onAccentContainer,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Standard delivery stop duration (includes parking and handover).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = dwellMinutes.toFloat(),
                        onValueChange = { dwellMinutes = it.toDouble() },
                        valueRange = 4.0f..12.0f,
                        steps = 15,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.brandPrimary,
                            activeTrackColor = colors.brandPrimary,
                            inactiveTrackColor = colors.borderSubtle
                        )
                    )
                }
            }
        }

        // Rest Stops & Quiet Layby Slots
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                border = BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Coffee, contentDescription = null, tint = colors.brandPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "QUIET LAYBY / BUFFER REST STOPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(listOf(1, 2, 3, 4)) { count ->
                            FilterChip(
                                selected = restStopCount == count,
                                onClick = { restStopCount = count },
                                label = { Text("$count Rest Stop${if (count > 1) "s" else ""}", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.brandPrimary,
                                    selectedLabelColor = colors.onBrandPrimary
                                ),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            )
                        }
                    }
                }
            }
        }

        // Extracted Stops List Header
        item {
            Text(
                text = "STOPS TO OPTIMIZE (${stops.size})",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }

        itemsIndexed(stops, key = { _, stop -> stop.id }) { idx, stop ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                border = BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.brandPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${idx + 1}",
                                color = colors.onBrandPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stop.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = stop.address,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Prominent Customer Requested Time Slot
                    stop.timeWindow?.let { win ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = colors.slotCardBg,
                            border = BorderStroke(1.5.dp, colors.slotCardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = colors.slotCardAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "CUSTOMER BOOKED SLOT:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = colors.slotCardAccent,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Text(
                                    text = win.label ?: "${win.startTime} – ${win.endTime}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = colors.slotCardText
                                )
                            }
                        }
                    }
                }
            }
        }

        // Optimize Button
        item {
            if (isOptimizing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.heroCardBg)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = optimizingStep,
                            color = colors.onHeroPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        viewModel.optimizeRoute(departureTime, dwellMinutes, restStopCount)
                        onNavigateToActiveRoute()
                    },
                    enabled = stops.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("optimize_route_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brandPrimary,
                        contentColor = colors.onBrandPrimary
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Route, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Optimize & Plan Driving Route",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}
