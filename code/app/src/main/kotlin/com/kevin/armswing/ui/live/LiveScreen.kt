package com.kevin.armswing.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
