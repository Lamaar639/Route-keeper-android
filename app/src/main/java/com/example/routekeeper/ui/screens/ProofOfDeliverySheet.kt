package com.example.routekeeper.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.routekeeper.data.model.DeliveryStop
import com.example.routekeeper.data.model.ProofOfDelivery
import com.example.routekeeper.ui.theme.AmberAccess
import com.example.routekeeper.ui.theme.BorderClean
import com.example.routekeeper.ui.theme.CleanSurface
import com.example.routekeeper.ui.theme.EmeraldDelivered
import com.example.routekeeper.ui.theme.MintAccent
import com.example.routekeeper.ui.theme.MintAccentContainer
import com.example.routekeeper.ui.theme.PinePrimary
import com.example.routekeeper.ui.theme.PinePrimaryContainer
import com.example.routekeeper.ui.theme.TextMuted
import com.example.routekeeper.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProofOfDeliverySheet(
    stop: DeliveryStop,
    onDismiss: () -> Unit,
    onConfirmDelivery: (ProofOfDelivery) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedDisposition by remember { mutableStateOf("Handed to Recipient") }
    var notes by remember { mutableStateOf(stop.notes) }
    var photoCaptured by remember { mutableStateOf(false) }

    val signaturePaths = remember { mutableStateListOf<List<Offset>>() }
    var currentPath by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val dispositions = listOf(
        "Handed to Recipient",
        "Front Porch",
        "Safe Place / Shed",
        "Neighbor",
        "Gate Locked / Access Issue"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CleanSurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.testTag("proof_of_delivery_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header with Stop Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Proof of Delivery (POD)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PinePrimary
                    )
                    Text(
                        text = "Stop ${stop.dropNumber ?: stop.sourceSequence}: ${stop.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Disposition Selector Chips
            Text(
                text = "DELIVERY DISPOSITION",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                dispositions.chunked(2).forEach { rowChips ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowChips.forEach { disp ->
                            val isAccessIssue = disp.contains("Gate Locked", ignoreCase = true)
                            FilterChip(
                                selected = selectedDisposition == disp,
                                onClick = { selectedDisposition = disp },
                                label = { Text(disp, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                                leadingIcon = if (isAccessIssue) {
                                    { Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = AmberAccess, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (isAccessIssue) Color(0xFFFEF3C7) else PinePrimary,
                                    selectedLabelColor = if (isAccessIssue) AmberAccess else CleanSurface
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Photo POD button & indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { photoCaptured = !photoCaptured },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (photoCaptured) MintAccentContainer else CleanSurface
                    )
                ) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = PinePrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (photoCaptured) "Photo Attached ✓" else "Snap Doorstep Photo",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PinePrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Customer Touch Signature Canvas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Draw, contentDescription = null, tint = PinePrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CUSTOMER SIGNATURE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (signaturePaths.isNotEmpty() || currentPath.isNotEmpty()) {
                    Text(
                        text = "Clear Signature",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberAccess,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                signaturePaths.clear()
                                currentPath = emptyList()
                            }
                            .padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFAFAF7))
                    .border(1.dp, BorderClean, RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPath = listOf(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentPath = currentPath + change.position
                            },
                            onDragEnd = {
                                if (currentPath.isNotEmpty()) {
                                    signaturePaths.add(currentPath)
                                    currentPath = emptyList()
                                }
                            }
                        )
                    }
                    .testTag("signature_canvas")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw historical lines
                    signaturePaths.forEach { path ->
                        for (i in 0 until path.size - 1) {
                            drawLine(
                                color = PinePrimary,
                                start = path[i],
                                end = path[i + 1],
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    // Draw current dragging line
                    for (i in 0 until currentPath.size - 1) {
                        drawLine(
                            color = PinePrimary,
                            start = currentPath[i],
                            end = currentPath[i + 1],
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                if (signaturePaths.isEmpty() && currentPath.isEmpty()) {
                    Text(
                        text = "Sign here with finger / stylus",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Delivery Notes Text Field
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Driver notes (e.g. left behind blue planter, code 4821)", fontSize = 12.sp) },
                maxLines = 2,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Confirm Completion Button
            Button(
                onClick = {
                    val nowStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    val pod = ProofOfDelivery(
                        timestamp = nowStr,
                        disposition = selectedDisposition,
                        signatureCaptured = signaturePaths.isNotEmpty(),
                        photoCaptured = photoCaptured,
                        notes = notes
                    )
                    onConfirmDelivery(pod)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_delivery_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedDisposition.contains("Gate Locked")) AmberAccess else EmeraldDelivered,
                    contentColor = CleanSurface
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save Proof of Delivery & Complete",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
