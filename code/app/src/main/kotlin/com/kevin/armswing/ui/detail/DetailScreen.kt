package com.kevin.armswing.ui.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.kevin.armswing.ui.shared.OmegaLineChart
import com.kevin.armswing.ui.shared.StatItem
import com.kevin.armswing.ui.theme.BackgroundDark
import com.kevin.armswing.ui.theme.OnPrimary
import com.kevin.armswing.ui.theme.PrimaryPurple
import com.kevin.armswing.ui.history.durationString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TRAINING_TYPES = listOf(
    "Training",
    "Volleyball",
    "Tennis",
    "Baseball",
    "Handball",
    "Test"
)

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val omegaHistory by viewModel.omegaHistory.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        EditTrainingTypeDialog(
            current = session?.label ?: TRAINING_TYPES[0],
            onSave = { label ->
                viewModel.updateLabel(label)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }

    if (showNoteDialog) {
        EditNoteDialog(
            current = session?.note ?: "",
            onSave = { note ->
                viewModel.updateNote(note)
                showNoteDialog = false
            },
            onDismiss = { showNoteDialog = false }
        )
    }

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
            Box(modifier = Modifier.clickable { showEditDialog = true }) {
                Text(
                    text = session?.label?.uppercase() ?: "AUFZEICHNUNG",
                    color = PrimaryPurple,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            session?.let { s ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(s.startedAt)),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    s.endedAt?.let { end ->
                        Text(
                            durationString((end - s.startedAt) / 1000),
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OmegaLineChart(
            omegaHistory = omegaHistory,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(
                "MAX rad/s",
                stats?.maxOmega?.let { "%.2f".format(it) } ?: "—",
                Modifier.weight(1f),
                valueColor = PrimaryPurple
            )
            StatItem(
                "DAUER",
                session?.endedAt?.let { end ->
                    durationString((end - (session?.startedAt ?: end)) / 1000)
                } ?: "—",
                Modifier.weight(1f)
            )
            StatItem(
                "Ø rad/s",
                stats?.avgOmega?.let { "%.2f".format(it) } ?: "—",
                Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem("SAMPLES", stats?.sampleCount?.toString() ?: "—", Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        val noteText = session?.note
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showNoteDialog = true }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Notiz",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Text(
                    text = if (noteText.isNullOrBlank()) "Notiz hinzufügen…" else noteText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (noteText.isNullOrBlank()) Color.White.copy(alpha = 0.35f) else Color.White
                )
            }
            Text(
                "✎",
                style = MaterialTheme.typography.bodyMedium,
                color = PrimaryPurple
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) { Text("← Zurück") }
            Button(
                onClick = {
                    scope.launch {
                        val intent = viewModel.export(context) ?: return@launch
                        context.startActivity(Intent.createChooser(intent, "Session exportieren"))
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryPurple,
                    contentColor = OnPrimary
                )
            ) { Text("Exportieren") }
        }
    }
}

@Composable
private fun EditNoteDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notiz") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("z.B. Beine sehr müde, neues PB…") },
                maxLines = 4
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun EditTrainingTypeDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trainingstyp ändern") },
        text = {
            Column {
                TRAINING_TYPES.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = type }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == type,
                            onClick = { selected = type }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(type, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
