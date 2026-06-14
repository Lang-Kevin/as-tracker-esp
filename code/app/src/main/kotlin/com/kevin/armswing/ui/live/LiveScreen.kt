package com.kevin.armswing.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.armswing.ui.shared.OmegaSessionChart
import com.kevin.armswing.ui.shared.StatItem
import com.kevin.armswing.ui.theme.BackgroundDark
import com.kevin.armswing.ui.theme.OnPrimary
import com.kevin.armswing.ui.theme.PrimaryPurple
import com.kevin.armswing.ui.theme.TertiaryPink

@Composable
fun LiveScreen(
    onStopSession: () -> Unit,
    onAbortSession: () -> Unit = onStopSession,
    viewModel: LiveViewModel = hiltViewModel()
) {
    val currentVelocityMs by viewModel.currentVelocityMs.collectAsStateWithLifecycle()
    val currentVelocityKmh by viewModel.currentVelocityKmh.collectAsStateWithLifecycle()
    val velocityHistory by viewModel.velocityHistory.collectAsStateWithLifecycle()
    val sessionPeakMs by viewModel.sessionPeakMs.collectAsStateWithLifecycle()
    val avgVelocityMs by viewModel.avgVelocityMs.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val sampleCount by viewModel.sampleCount.collectAsStateWithLifecycle()
    val sessionLabel by viewModel.sessionLabel.collectAsStateWithLifecycle()
    val batteryLevel by viewModel.batteryLevel.collectAsStateWithLifecycle()

    var showAbortDialog by remember { mutableStateOf(false) }
    var showStopDialog by remember { mutableStateOf(false) }

    if (showAbortDialog) {
        ConfirmDialog(
            title = "Aufzeichnung abbrechen?",
            text = "Die aufgezeichneten Daten werden verworfen und nicht gespeichert.",
            confirmLabel = "Abbrechen",
            onConfirm = { showAbortDialog = false; onAbortSession() },
            onDismiss = { showAbortDialog = false }
        )
    }

    if (showStopDialog) {
        ConfirmDialog(
            title = "Aufzeichnung abschließen?",
            text = "Die Aufzeichnung wird beendet und die Daten werden gespeichert.",
            confirmLabel = "Speichern",
            onConfirm = { showStopDialog = false; onStopSession() },
            onDismiss = { showStopDialog = false }
        )
    }

    val mm = elapsed / 60
    val ss = elapsed % 60

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sessionLabel?.uppercase() ?: "LIVE-AUFZEICHNUNG",
                color = PrimaryPurple,
                style = MaterialTheme.typography.titleLarge
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                batteryLevel?.let { BatteryChip(it) }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(TertiaryPink, CircleShape)
                )
                Text(
                    text = "%02d:%02d".format(mm, ss),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        OmegaSessionChart(
            velocityHistory = velocityHistory,
            currentVelocityMs = currentVelocityMs,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(Modifier.height(12.dp))

        // Prominent km/h display
        currentVelocityKmh?.let { kmh ->
            Text(
                text = "%.1f km/h".format(kmh),
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(4.dp))
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(
                "AKTUELL",
                currentVelocityMs?.let { "%.2f m/s".format(it) } ?: "—",
                Modifier.weight(1f)
            )
            StatItem(
                "PEAK",
                sessionPeakMs?.let { "%.2f m/s".format(it) } ?: "—",
                Modifier.weight(1f),
                valueColor = PrimaryPurple
            )
            StatItem(
                "Ø",
                avgVelocityMs?.let { "%.2f m/s".format(it) } ?: "—",
                Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem("GESAMTZEIT", "%02d:%02d".format(mm, ss), Modifier.weight(1f))
            StatItem("SAMPLES", sampleCount.toString(), Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { showAbortDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.error
                )
            ) { Text("Abbrechen") }
            Button(
                onClick = { showStopDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryPurple,
                    contentColor = OnPrimary
                )
            ) { Text("Abschließen") }
        }
    }
}

@Composable
private fun BatteryChip(level: Int) {
    val tint = when {
        level >= 50 -> Color(0xFF4CAF50)
        level >= 20 -> Color(0xFFFF9800)
        else        -> Color(0xFFF44336)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.size(width = 18.dp, height = 10.dp)
        ) {
            val stroke = 1.2.dp.toPx()
            val bodyW = size.width * 0.87f
            val termH = size.height * 0.4f
            val termY = (size.height - termH) / 2f
            // Body outline
            drawRoundRect(
                color = tint,
                size = Size(bodyW, size.height),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(stroke)
            )
            // Terminal
            drawRect(
                color = tint,
                topLeft = Offset(bodyW, termY),
                size = Size(size.width - bodyW, termH)
            )
            // Fill bar
            val fillW = ((bodyW - stroke * 4) * level / 100f).coerceAtLeast(0f)
            if (fillW > 0f) {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(stroke * 2, stroke * 2),
                    size = Size(fillW, size.height - stroke * 4),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
            }
        }
        Text(
            text = "$level%",
            color = tint,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Weiter messen") }
        }
    )
}
