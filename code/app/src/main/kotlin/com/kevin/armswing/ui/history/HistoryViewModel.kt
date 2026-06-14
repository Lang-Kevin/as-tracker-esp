package com.kevin.armswing.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.armswing.data.db.ArmSwingDatabase
import com.kevin.armswing.data.entity.Session
import com.kevin.armswing.data.entity.WeekStat
import com.kevin.armswing.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SummaryStats(
    val sessionCount: Int = 0,
    val totalDurationS: Long = 0L,
    val avgMps: Float = 0f,
    val longestDurationS: Long = 0L
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val db: ArmSwingDatabase
) : ViewModel() {

    val sessions: StateFlow<List<Session>> = sessionRepository.getSessionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val summary: StateFlow<SummaryStats> = combine(
        db.sessionDao().getCompletedSessionCount(),
        db.sessionDao().getTotalDurationS(),
        db.sessionDao().getGlobalAvgMps(),
        db.sessionDao().getLongestDurationS()
    ) { count, total, avg, longest ->
        SummaryStats(count, total, avg, longest)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryStats())

    private val sixWeeksAgo = System.currentTimeMillis() - 6L * 7 * 24 * 60 * 60 * 1000L

    val weeklyStats: StateFlow<List<WeekStat>> = db.sessionDao()
        .getWeeklyStats(sixWeeksAgo)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trash: StateFlow<List<com.kevin.armswing.data.entity.Session>> = sessionRepository.getTrashFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(id: Long) {
        viewModelScope.launch { sessionRepository.restoreSession(id) }
    }

    fun softDelete(id: Long) {
        viewModelScope.launch { sessionRepository.deleteSessionsByIds(listOf(id)) }
    }

    fun startSelection(id: Long) {
        _selectedIds.value = setOf(id)
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (id in current) current - id else current + id
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        _selectedIds.value = emptySet()
        viewModelScope.launch { sessionRepository.deleteSessionsByIds(ids) }
    }
}
