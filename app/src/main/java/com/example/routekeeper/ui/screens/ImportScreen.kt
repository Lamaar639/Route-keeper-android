package com.example.routekeeper.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.routekeeper.data.service.DeliveryParser
import com.example.routekeeper.ui.theme.RouteTheme
import com.example.routekeeper.ui.viewmodel.RouteViewModel

@Composable
fun ImportScreen(
    viewModel: RouteViewModel,
    onNavigateToPreRouteReview: () -> Unit
) {
    val manifestText by viewModel.manifestInputText.collectAsState()
    val isImportingPdf by viewModel.isImportingPdf.collectAsState()
    val pdfImportMessage by viewModel.pdfImportMessage.collectAsState()
    val pdfImportError by viewModel.pdfImportError.collectAsState()
    val colors = RouteTheme.colors

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importSharedPdfUri(uri)
        }
    }

    val parsedStops = remember(manifestText) {
        if (manifestText.isNotBlank()) DeliveryParser.parseDeliveryList(manifestText) else emptyList()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("import_screen_scroll"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Import Delivery Manifest",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.brandPrimary
            )
            Text(
                text = "Share any PDF directly to RouteKeeper from another app, open a PDF, or paste delivery text below.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
        }

        // Direct PDF Share Tip Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.accentContainer),
                border = BorderStroke(1.dp, colors.brandPrimary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = colors.onAccentContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Direct PDF Share Ready: Viewing a delivery PDF in Gmail, WhatsApp, Files, or Drive? Tap Share → RouteKeeper to auto-import stops without downloading.",
                        fontSize = 12.sp,
                        color = colors.onAccentContainer,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // PDF Import Progress / Status / Error
        if (isImportingPdf) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.heroCardBg)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = pdfImportMessage ?: "Extracting stops from PDF...",
                            color = colors.onHeroPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else if (pdfImportError != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.dangerBg),
                    border = BorderStroke(1.dp, colors.dangerBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = "PDF Error", tint = colors.dangerAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = pdfImportError ?: "Failed to import PDF",
                            color = colors.dangerText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else if (pdfImportMessage != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.deliveredBg),
                    border = BorderStroke(1.dp, colors.deliveredAccent)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "PDF Imported", tint = colors.deliveredAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = pdfImportMessage ?: "",
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Quick action buttons: Pick PDF, Sample Manifest, Clear
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pick_pdf_manifest_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.brandPrimary,
                            contentColor = colors.onBrandPrimary
                        )
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Import PDF", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.clearPdfImportStatus()
                            viewModel.setManifestInputText(DeliveryParser.SAMPLE_DELIVERY_NOTE_TEXT)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("load_sample_manifest_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.brandPrimary)
                    ) {
                        Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = colors.brandPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Sample", fontSize = 13.sp, color = colors.brandPrimary, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.clearPdfImportStatus()
                            viewModel.setManifestInputText("")
                        },
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.borderSubtle)
                    ) {
                        Text(text = "Clear", fontSize = 13.sp, color = colors.textPrimary)
                    }
                }
            }
        }

        // Input text area
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                border = BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "MANIFEST TEXT / NOTES",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manifestText,
                        onValueChange = { viewModel.setManifestInputText(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .testTag("manifest_input_field"),
                        placeholder = {
                            Text(
                                "Share a PDF to RouteKeeper or paste order notes, addresses...",
                                color = colors.textMuted
                            )
                        },
                        maxLines = 15,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        // Live Parse Summary Badge
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
                        Text(
                            text = "EXTRACTED DELIVERY STOPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.accentContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${parsedStops.size} Stops Detected",
                                color = colors.onAccentContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    parsedStops.take(4).forEachIndexed { index, stop ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.brandPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
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
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = stop.address,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 12.sp,
                                    color = colors.textMuted,
                                    maxLines = 1
                                )
                            }
                            stop.timeWindow?.let { win ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.slotCardBg)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = win.startTime,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.slotCardAccent
                                    )
                                }
                            }
                        }
                    }

                    if (parsedStops.size > 4) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "+ ${parsedStops.size - 4} more stops",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Continue Button
        item {
            Button(
                onClick = {
                    if (parsedStops.isNotEmpty()) {
                        viewModel.importManifestText(manifestText)
                        onNavigateToPreRouteReview()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("continue_to_pre_route_button"),
                enabled = parsedStops.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.brandPrimary,
                    contentColor = colors.onBrandPrimary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Review & Schedule Route (${parsedStops.size} Stops)",
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
