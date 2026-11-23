package com.example.routekeeper.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.routekeeper.data.model.VideoExportSettings
import com.example.routekeeper.ui.theme.RouteTheme
import com.example.routekeeper.ui.viewmodel.RouteViewModel
import kotlin.math.roundToInt

@Composable
fun VideoExportDialog(
    viewModel: RouteViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.videoExportSettings.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val isExporting by viewModel.isExportingVideo.collectAsState()
    val exportProgress by viewModel.videoExportProgress.collectAsState()
    val exportStage by viewModel.videoExportStage.collectAsState()
    val exportedResult by viewModel.exportedVideoResult.collectAsState()
    val exportError by viewModel.videoExportError.collectAsState()
    val colors = RouteTheme.colors

    val sampleCount = viewModel.getTargetActualTrackSampleCount()
    val hasActualTrack = sampleCount >= 2
    var savedToGallerySuccess by remember(exportedResult) { mutableStateOf<Boolean?>(null) }

    Dialog(
        onDismissRequest = {
            if (!isExporting) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 560.dp)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(20.dp))
                .testTag("video_export_dialog"),
            color = colors.canvasBg,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.brandPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = "Export Route Video",
                                tint = colors.onBrandPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Export Route MP4 Video",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Telemetry Overlays & Incident Review",
                                fontSize = 12.sp,
                                color = colors.textMuted
                            )
                        }
                    }

                    if (!isExporting) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("close_video_export_button")
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = colors.textMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Export Progress Live Card
                    if (isExporting) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.heroCardBg)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "ENCODING MP4 ROUTE VIDEO",
                                            color = colors.accent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${(exportProgress * 100).toInt()}%",
                                            color = colors.onHeroPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        progress = { exportProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = colors.accent,
                                        trackColor = colors.heroCardContainer
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = exportStage,
                                        color = colors.onHeroMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Error card
                    if (exportError != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.dangerBg),
                                border = BorderStroke(1.dp, colors.dangerBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Export Error",
                                        tint = colors.dangerAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = exportError ?: "Export failed",
                                        color = colors.dangerText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Exported Result Card (Save to Gallery & Share)
                    exportedResult?.let { result ->
                        item {
                            val speedDisplay = if (preferences.useKmh) {
                                "${(result.maxSpeedMph * 1.60934f).roundToInt()} km/h"
                            } else {
                                "${result.maxSpeedMph.roundToInt()} mph"
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("exported_video_result_card"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.deliveredBg),
                                border = BorderStroke(1.5.dp, colors.deliveredAccent)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Export Ready",
                                            tint = colors.deliveredAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "MP4 Video Ready for Review",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = colors.textPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = result.filename,
                                        fontSize = 11.sp,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${result.durationSeconds}s · ${result.sampleCount} telemetry pts · Peak $speedDisplay · ${result.speedingCount} speeding alerts",
                                        fontSize = 11.sp,
                                        color = colors.textMuted
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                savedToGallerySuccess = viewModel.saveExportedVideoToGallery()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("save_video_gallery_button"),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = colors.deliveredAccent,
                                                contentColor = colors.onDelivered
                                            )
                                        ) {
                                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (savedToGallerySuccess == true) "Saved ✓" else "Save Gallery",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = { viewModel.shareExportedVideo(context) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("share_video_button"),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, colors.brandPrimary)
                                        ) {
                                            Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = colors.brandPrimary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Share MP4", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.brandPrimary)
                                        }
                                    }

                                    if (savedToGallerySuccess == true) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Saved to Movies/RouteKeeper in device gallery.",
                                            fontSize = 11.sp,
                                            color = colors.deliveredAccent,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else if (savedToGallerySuccess == false) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Could not save to gallery. Try Share MP4 instead.",
                                            fontSize = 11.sp,
                                            color = colors.dangerAccent
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Route Telemetry Source Selector
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                            border = BorderStroke(1.dp, colors.borderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("ROUTE DATA SOURCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textMuted)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isActualSelected = settings.videoSource == "actual" && hasActualTrack
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isActualSelected) colors.accentContainer
                                                else if (!hasActualTrack) colors.cardSurfaceVariant
                                                else colors.canvasBg
                                            )
                                            .border(
                                                width = if (isActualSelected) 2.dp else 1.dp,
                                                color = if (isActualSelected) colors.brandPrimary else colors.borderSubtle,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable(enabled = !isExporting && hasActualTrack) {
                                                viewModel.updateVideoExportSettings(settings.copy(videoSource = "actual"))
                                            }
                                            .padding(10.dp)
                                            .testTag("video_source_actual_option")
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.GpsFixed,
                                                    contentDescription = null,
                                                    tint = if (isActualSelected) colors.onAccentContainer else colors.textMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Actual Driven GPS",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = if (isActualSelected) colors.onAccentContainer else if (hasActualTrack) colors.textPrimary else colors.textMuted
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = if (hasActualTrack) "$sampleCount GPS + Speed pts" else "Needs 2+ GPS pts",
                                                fontSize = 10.sp,
                                                color = if (hasActualTrack) colors.brandPrimary else colors.warningAccent,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    val isPlannedSelected = settings.videoSource == "planned" || !hasActualTrack
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isPlannedSelected) colors.accentContainer else colors.canvasBg)
                                            .border(
                                                width = if (isPlannedSelected) 2.dp else 1.dp,
                                                color = if (isPlannedSelected) colors.brandPrimary else colors.borderSubtle,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable(enabled = !isExporting) {
                                                viewModel.updateVideoExportSettings(settings.copy(videoSource = "planned"))
                                            }
                                            .padding(10.dp)
                                            .testTag("video_source_planned_option")
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Route,
                                                    contentDescription = null,
                                                    tint = if (isPlannedSelected) colors.onAccentContainer else colors.textMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Planned Route",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = if (isPlannedSelected) colors.onAccentContainer else colors.textPrimary
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "Scheduled route tour",
                                                fontSize = 10.sp,
                                                color = if (isPlannedSelected) colors.onAccentContainer.copy(alpha = 0.8f) else colors.textMuted
                                            )
                                        }
                                    }
                                }

                                if (!hasActualTrack) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Tip: Tap Navigate on any stop to record live GPS, speed, and OpenStreetMap road limits for incident export.",
                                        fontSize = 11.sp,
                                        color = colors.textMuted,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    // Duration Length Section
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                            border = BorderStroke(1.dp, colors.borderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("VIDEO LENGTH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textMuted)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(colors.accentContainer)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("${settings.durationSeconds} Seconds", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onAccentContainer)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                val presets = listOf(10 to "10s Quick", 15 to "15s Standard", 25 to "25s Detailed", 40 to "40s Reel")
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(presets) { (sec, label) ->
                                        FilterChip(
                                            selected = settings.durationSeconds == sec,
                                            onClick = { viewModel.updateVideoExportSettings(settings.copy(durationSeconds = sec)) },
                                            label = { Text(label, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = colors.brandPrimary,
                                                selectedLabelColor = colors.onBrandPrimary
                                            ),
                                            enabled = !isExporting
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Slider(
                                    value = settings.durationSeconds.toFloat(),
                                    onValueChange = { viewModel.updateVideoExportSettings(settings.copy(durationSeconds = it.toInt())) },
                                    valueRange = 5f..60f,
                                    steps = 10,
                                    colors = SliderDefaults.colors(
                                        thumbColor = colors.brandPrimary,
                                        activeTrackColor = colors.brandPrimary,
                                        inactiveTrackColor = colors.borderSubtle
                                    ),
                                    enabled = !isExporting
                                )
                            }
                        }
                    }

                    // Aspect Ratio Selector
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                            border = BorderStroke(1.dp, colors.borderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("FRAME ASPECT RATIO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textMuted)
                                Spacer(modifier = Modifier.height(10.dp))

                                val ratios = listOf(
                                    Triple("9:16", "Vertical (9:16)", "Mobile"),
                                    Triple("16:9", "Landscape (16:9)", "Incident Review"),
                                    Triple("1:1", "Square (1:1)", "Fleet Feed")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ratios.forEach { (key, _, desc) ->
                                        val isSelected = settings.aspectRatio == key
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) colors.accentContainer else colors.canvasBg)
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) colors.brandPrimary else colors.borderSubtle,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .clickable(enabled = !isExporting) {
                                                    viewModel.updateVideoExportSettings(settings.copy(aspectRatio = key))
                                                }
                                                .padding(10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = when (key) {
                                                        "9:16" -> Icons.Default.PhoneAndroid
                                                        "16:9" -> Icons.Default.Tv
                                                        else -> Icons.Default.Square
                                                    },
                                                    contentDescription = null,
                                                    tint = if (isSelected) colors.onAccentContainer else colors.textMuted,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(key, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isSelected) colors.onAccentContainer else colors.textPrimary)
                                                Text(desc, fontSize = 9.sp, color = if (isSelected) colors.onAccentContainer.copy(alpha = 0.8f) else colors.textMuted, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Camera Mode Selector
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                            border = BorderStroke(1.dp, colors.borderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("CAMERA PERSPECTIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textMuted)
                                Spacer(modifier = Modifier.height(8.dp))

                                val modes = listOf(
                                    "dynamic-follow" to "Dynamic Follow (Cockpit)",
                                    "overview" to "Territory Overview (Full Route)"
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(modes) { (mode, title) ->
                                        FilterChip(
                                            selected = settings.cameraMode == mode,
                                            onClick = { viewModel.updateVideoExportSettings(settings.copy(cameraMode = mode)) },
                                            label = { Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = colors.brandPrimary,
                                                selectedLabelColor = colors.onBrandPrimary
                                            ),
                                            enabled = !isExporting
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Display Elements & Overlays Checklist
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                            border = BorderStroke(1.dp, colors.borderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("WHAT TO DISPLAY ON VIDEO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textMuted)
                                    IconButton(
                                        onClick = {
                                            viewModel.updateVideoExportSettings(
                                                VideoExportSettings(
                                                    videoSource = if (hasActualTrack) "actual" else "planned"
                                                )
                                            )
                                        },
                                        modifier = Modifier.size(48.dp),
                                        enabled = !isExporting
                                    ) {
                                        Icon(imageVector = Icons.Default.RestartAlt, contentDescription = "Reset Overlays", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                DisplayToggleRow(
                                    label = "Embed GPS Speed Graph & Speeding Alerts",
                                    checked = settings.showSpeedTelemetry,
                                    enabled = !isExporting,
                                    onCheckedChange = { viewModel.updateVideoExportSettings(settings.copy(showSpeedTelemetry = it)) }
                                )
                                DisplayToggleRow(
                                    label = "Driver Speedometer & Speed Limit HUD",
                                    checked = settings.showSpeedometer,
                                    enabled = !isExporting,
                                    onCheckedChange = { viewModel.updateVideoExportSettings(settings.copy(showSpeedometer = it)) }
                                )
                                DisplayToggleRow(
                                    label = "Speed-Coded Traveled GPS Trail",
                                    checked = settings.showTrail,
                                    enabled = !isExporting,
                                    onCheckedChange = { viewModel.updateVideoExportSettings(settings.copy(showTrail = it)) }
                                )
                                DisplayToggleRow(
                                    label = "Delivery Van & Headlights",
                                    checked = settings.showVan,
                                    enabled = !isExporting,
                                    onCheckedChange = { viewModel.updateVideoExportSettings(settings.copy(showVan = it)) }
                                )
                                DisplayToggleRow(
                                    label = "Stop Pins & Sequence Badges",
                                    checked = settings.showStopPins,
                                    enabled = !isExporting,
                                    onCheckedChange = { viewModel.updateVideoExportSettings(settings.copy(showStopPins = it)) }
                                )
                                DisplayToggleRow(
                                    label = "Customer Names & Addresses",
                                    checked = settings.showStopNames,
                                    enabled = !isExporting,
                                    onCheckedChange = { viewModel.updateVideoExportSettings(settings.copy(showStopNames = it)) }
                                )
                                DisplayToggleRow(
                                    label = "Customer Booked Time Slot Badges",
                                    checked = settings.showTimeWindows,
                                    enabled = !isExporting,
                                    onCheckedChange = { viewModel.updateVideoExportSettings(settings.copy(showTimeWindows = it)) }
                                )
                                DisplayToggleRow(
                                    label = "Route Cockpit Title Bar",
                                    checked = settings.showCockpitHud,
                                    enabled = !isExporting,
                                    onCheckedChange = { viewModel.updateVideoExportSettings(settings.copy(showCockpitHud = it)) }
                                )
                                DisplayToggleRow(
                                    label = "Timeline Scrub Bar",
                                    checked = settings.showScrubBar,
                                    enabled = !isExporting,
                                    onCheckedChange = { viewModel.updateVideoExportSettings(settings.copy(showScrubBar = it)) }
                                )
                                DisplayToggleRow(
                                    label = "RouteKeeper Watermark",
                                    checked = settings.showWatermark,
                                    enabled = !isExporting,
                                    onCheckedChange = { viewModel.updateVideoExportSettings(settings.copy(showWatermark = it)) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons Footer
                if (isExporting) {
                    Button(
                        onClick = { viewModel.closeVideoExportDialog() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("cancel_video_export_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.dangerAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Cancel Export", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("dismiss_video_export_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.borderSubtle)
                        ) {
                            Text("Close", fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        }

                        Button(
                            onClick = {
                                savedToGallerySuccess = null
                                viewModel.startVideoExport()
                            },
                            modifier = Modifier
                                .weight(2f)
                                .height(50.dp)
                                .testTag("start_video_export_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.brandPrimary,
                                contentColor = colors.onBrandPrimary
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (exportedResult != null) "Re-Export ${settings.aspectRatio} MP4" else "Export ${settings.aspectRatio} MP4",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DisplayToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = RouteTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = colors.textPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.brandPrimary,
                checkmarkColor = colors.onBrandPrimary,
                uncheckedColor = colors.textMuted
            )
        )
    }
}
