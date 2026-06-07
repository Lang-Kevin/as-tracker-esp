package com.kevin.armswing.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.armswing.data.entity.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onSessionClick: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Aufträge löschen?") },
            text = { Text("${selectedIds.size} Eintrag(Einträge) werden unwiderruflich gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteSelected()
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (isSelectionMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { viewModel.clearSelection() }) {
                    Icon(Icons.Default.Close, contentDescription = "Auswahl abbrechen")
                }
                Text(
                    "${selectedIds.size} ausgewählt",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showDeleteDialog = true },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Löschen")
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Zurück") }
                Text("Verlauf", style = MaterialTheme.typography.headlineMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (sessions.isEmpty()) {
            Text("Noch keine Sessions aufgezeichnet.", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Starte eine Aufzeichnung, um deine Armschwung-Daten hier zu sehen.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(sessions, key = { it.id }) { session ->
                SessionListItem(
                    session = session,
                    isSelected = session.id in selectedIds,
                    isSelectionMode = isSelectionMode,
                    onClick = {
                        if (isSelectionMode) viewModel.toggleSelection(session.id)
                        else onSessionClick(session.id)
                    },
                    onLongClick = { viewModel.startSelection(session.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionListItem(
    session: Session,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(session.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    session.startedAt.toDateString(),
                    style = MaterialTheme.typography.bodySmall
                )
                session.endedAt?.let { end ->
                    Text(
                        "Dauer: ${durationString((end - session.startedAt) / 1000)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                } ?: Text("läuft noch…", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun Long.toDateString(): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(this))

internal fun durationString(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}
