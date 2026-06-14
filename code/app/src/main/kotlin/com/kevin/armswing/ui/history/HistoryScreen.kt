package com.kevin.armswing.ui.history

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
import com.kevin.shared.ui.session.SessionListItem
import com.kevin.shared.ui.session.SummaryCard
import com.kevin.shared.ui.session.durationString

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
            SummaryCard(listOf(
                "TRAININGS" to summary.sessionCount.toString(),
                "GESAMTDAUER" to durationString(summary.totalDurationS),
                "Ø m/s" to "%.2f".format(summary.avgMps),
                "LÄNGSTE" to durationString(summary.longestDurationS)
            ))
        }
        items(sessions, key = { it.id }) { session ->
            SessionListItem(
                label = session.label,
                startedAt = session.startedAt,
                endedAt = session.endedAt,
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

