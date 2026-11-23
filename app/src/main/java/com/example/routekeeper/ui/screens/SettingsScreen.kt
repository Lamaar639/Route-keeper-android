package com.example.routekeeper.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.routekeeper.ui.theme.AppThemes
import com.example.routekeeper.ui.theme.RouteTheme
import com.example.routekeeper.ui.util.rememberRouteHapticController
import com.example.routekeeper.ui.viewmodel.RouteViewModel

@Composable
fun SettingsScreen(
    viewModel: RouteViewModel
) {
    val preferences by viewModel.preferences.collectAsState()
    val colors = RouteTheme.colors
    val haptics = rememberRouteHapticController()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen_scroll"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Driver Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.brandPrimary
            )
            Text(
                text = "Visual themes, navigation launcher preferences, speed telemetry, and service dwell.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
        }

        // App Appearance & Theme Switcher
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_theme_settings_card"),
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
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = colors.brandPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "APP THEME & VISUAL STYLE",
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
                                text = preferences.appTheme.displayName,
                                fontWeight = FontWeight.Bold,
                                color = colors.onAccentContainer,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Pick a visual palette suited to day or night driving. All routing, traffic, and GPS features remain identical.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(AppThemes.all, key = { it.id.name }) { spec ->
                            val isSelected = preferences.appTheme == spec.id
                            val p = spec.palette
                            Card(
                                modifier = Modifier
                                    .width(148.dp)
                                    .defaultMinSize(minHeight = 104.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.updatePreferences(preferences.copy(appTheme = spec.id))
                                    }
                                    .testTag("theme_option_${spec.id.name.lowercase()}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = p.canvasBg),
                                border = BorderStroke(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) colors.brandPrimary else colors.borderSubtle
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Mini UI mockup preview strip
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(p.cardSurface)
                                            .border(1.dp, p.borderSubtle, RoundedCornerShape(8.dp))
                                            .padding(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(p.brandPrimary)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .width(26.dp)
                                                        .height(10.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(p.heroCardBg)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clip(CircleShape)
                                                        .background(p.accent)
                                                )
                                            }
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(p.brandPrimary),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected Theme",
                                                        tint = p.onBrandPrimary,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = spec.id.displayName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = p.textPrimary,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "${if (spec.id.isDark) "Dark" else "Light"} · ${spec.id.subtitle}",
                                            fontSize = 10.sp,
                                            color = p.textMuted,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Navigation App Preference
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                border = BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Navigation, contentDescription = null, tint = colors.brandPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DEFAULT NAVIGATION APP",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    val navApps = listOf(
                        "google" to "Google Maps",
                        "waze" to "Waze",
                        "osm" to "OpenStreetMap"
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(navApps, key = { it.first }) { (id, label) ->
                            FilterChip(
                                selected = preferences.defaultNavApp == id,
                                onClick = {
                                    if (preferences.defaultNavApp != id) {
                                        haptics.onNavAppSwitched()
                                        viewModel.updatePreferences(preferences.copy(defaultNavApp = id))
                                    }
                                },
                                label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.brandPrimary,
                                    selectedLabelColor = colors.onBrandPrimary,
                                    labelColor = colors.textPrimary
                                ),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("nav_app_chip_$id")
                            )
                        }
                    }
                }
            }
        }

        // Default Dwell Time
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
                            Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = colors.brandPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DEFAULT STOP DWELL TIME",
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
                                text = "%.1f min".format(preferences.defaultDwellMinutes),
                                fontWeight = FontWeight.Bold,
                                color = colors.onAccentContainer,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = preferences.defaultDwellMinutes.toFloat(),
                        onValueChange = { viewModel.updatePreferences(preferences.copy(defaultDwellMinutes = it.toDouble())) },
                        valueRange = 4f..12f,
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

        // Audio Alert Chimes
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                border = BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Notifications, contentDescription = null, tint = colors.brandPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Audio Arrival Chimes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }
                        Text(
                            text = "Play a subtle chime and announce upcoming customer address when approaching next stop.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            fontSize = 12.sp
                        )
                    }

                    Switch(
                        checked = preferences.audioAlertsEnabled,
                        onCheckedChange = { viewModel.updatePreferences(preferences.copy(audioAlertsEnabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onBrandPrimary,
                            checkedTrackColor = colors.brandPrimary
                        )
                    )
                }
            }
        }

        // GPS Logging Frequency (1s to 5s)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gps_interval_settings_card"),
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
                            Icon(imageVector = Icons.Default.GpsFixed, contentDescription = null, tint = colors.brandPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GPS LOGGING INTERVAL",
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
                                text = "Every ${preferences.gpsIntervalSeconds}s",
                                fontWeight = FontWeight.Bold,
                                color = colors.onAccentContainer,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Choose how often GPS coordinates and vehicle speed are obtained and logged while navigating (1 to 5 seconds).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (1..5).forEach { seconds ->
                            val isSelected = preferences.gpsIntervalSeconds == seconds
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.updatePreferences(preferences.copy(gpsIntervalSeconds = seconds))
                                },
                                label = {
                                    Text(
                                        text = "${seconds}s",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.brandPrimary,
                                    selectedLabelColor = colors.onBrandPrimary,
                                    labelColor = colors.textPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("gps_interval_chip_$seconds")
                            )
                        }
                    }
                }
            }
        }

        // GPS Speed Limit & Unit Preferences
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("speed_telemetry_settings_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                border = BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = colors.brandPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DRIVER SPEED & TELEMETRY",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Speed Display Units",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !preferences.useKmh,
                            onClick = { viewModel.updatePreferences(preferences.copy(useKmh = false)) },
                            label = { Text("MPH (Miles/hr)", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.brandPrimary,
                                selectedLabelColor = colors.onBrandPrimary,
                                labelColor = colors.textPrimary
                            ),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("unit_mph_chip")
                        )
                        FilterChip(
                            selected = preferences.useKmh,
                            onClick = { viewModel.updatePreferences(preferences.copy(useKmh = true)) },
                            label = { Text("KM/H (Kilometers/hr)", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.brandPrimary,
                                selectedLabelColor = colors.onBrandPrimary,
                                labelColor = colors.textPrimary
                            ),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("unit_kmh_chip")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accentContainer.copy(alpha = 0.72f))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "Automatic OpenStreetMap Speed Limits",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onAccentContainer
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Speed thresholds are detected automatically per road from OpenStreetMap maxspeed & highway data at the driver's live GPS position.",
                                fontSize = 11.sp,
                                color = colors.textMuted,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // App Info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                border = BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = colors.brandPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ABOUT ROUTEKEEPER",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "RouteKeeper v1.0.0 (Build 2026)\nBuilt with Jetpack Compose & OpenStreetMap. Zero external cloud tracking subscriptions required.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
