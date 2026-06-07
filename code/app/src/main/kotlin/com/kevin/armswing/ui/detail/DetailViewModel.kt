package com.kevin.armswing.ui.detail

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.armswing.data.db.ArmSwingDatabase
import com.kevin.armswing.data.entity.Session
import com.kevin.armswing.export.SessionExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val db: ArmSwingDatabase,
    private val exporter: SessionExporter
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle.get<Long>("sessionId"))

    val session: StateFlow<Session?> = db.sessionDao().getByIdFlow(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val omegaHistory: StateFlow<List<Float>> = db.omegaSampleDao()
        .getSamplesForSession(sessionId)
        .map { list -> list.map { it.omega } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    data class Stats(val avgOmega: Float, val maxOmega: Float, val sampleCount: Int)

    val stats: StateFlow<Stats?> = omegaHistory.map { list ->
        if (list.isEmpty()) null
        else Stats(
            avgOmega = list.average().toFloat(),
            maxOmega = list.max(),
            sampleCount = list.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateLabel(label: String) {
        viewModelScope.launch { db.sessionDao().updateLabel(sessionId, label) }
    }

    fun updateNote(note: String) {
        viewModelScope.launch { db.sessionDao().updateNote(sessionId, note) }
    }

    suspend fun export(context: Context): Intent? = exporter.buildShareIntent(context, sessionId)
}
