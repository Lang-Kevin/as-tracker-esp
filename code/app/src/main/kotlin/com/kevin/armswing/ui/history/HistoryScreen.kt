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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.armswing.data.entity.Session
import com.kevin.armswing.data.entity.WeekStat
import com.kevin.armswing.ui.theme.PrimaryPurple
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
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val weeklyStats by viewModel.weeklyStats.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

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

    Column(modifier = Modifier.fillMaxSize()) {
        if (isSelectionMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            ) {
                TextButton(onClick = onBack) { Text("← Zurück") }
                Text("Verlauf", style = MaterialTheme.typography.headlineMedium)
            }
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Verlauf") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Statistik") }
                )
            }
        }

        when (selectedTab) {
            0 -> HistoryTab(
                sessions = sessions,
                selectedIds = selectedIds,
                isSelectionMode = isSelectionMode,
                summary = summary,
                onSessionClick = onSessionClick,
                onToggle = { viewModel.toggleSelection(it) },
                onLongClick = { viewModel.startSelection(it) }
            )
            1 -> StatsTab(weeklyStats = weeklyStats)
        }
    }
}

@Composable
private fun HistoryTab(
    sessions: List<Session>,
    selectedIds: Set<Long>,
    isSelectionMode: Boolean,
    summary: SummaryStats,
    onSessionClick: (Long) -> Unit,
    onToggle: (Long) -> Unit,
    onLongClick: (Long) -> Unit
) {
    if (sessions.isEmpty()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Noch keine Sessions aufgezeichnet.", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Starte eine Aufzeichnung, um deine Armschwung-Daten hier zu sehen.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            SummaryCard(summary)
        }
        items(sessions, key = { it.id }) { session ->
            SessionListItem(
                session = session,
                isSelected = session.id in selectedIds,
                isSelectionMode = isSelectionMode,
                onClick = {
                    if (isSelectionMode) onToggle(session.id)
                    else onSessionClick(session.id)
                },
                onLongClick = { onLongClick(session.id) }
            )
        }
    }
}

@Composable
private fun SummaryCard(summary: SummaryStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Übersicht",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem("TRAININGS", summary.sessionCount.toString(), Modifier.weight(1f))
                SummaryItem("GESAMTDAUER", durationString(summary.totalDurationS), Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem("Ø m/s", "%.2f".format(summary.avgMps), Modifier.weight(1f))
                SummaryItem("LÄNGSTE", durationString(summary.longestDurationS), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = PrimaryPurple)
    }
}

@Composable
private fun StatsTab(weeklyStats: List<WeekStat>) {
    if (weeklyStats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Noch keine Wochendaten vorhanden.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("WOCHE", Modifier.weight(2f), style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f))
                Text("EINH.", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f))
                Text("MIN", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f))
                Text("Ø m/s", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f))
            }
            HorizontalDivider()
        }
        items(weeklyStats) { stat ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stat.week, Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
                Text(stat.sessionCount.toString(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(stat.totalMinutes.toString(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text("%.2f".format(stat.avgMps), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
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
