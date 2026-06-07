package com.kevin.armswing.data.repository

import android.util.Log
import com.kevin.armswing.ble.BleManager
import com.kevin.armswing.data.db.ArmSwingDatabase
import com.kevin.armswing.data.entity.OmegaSample
import com.kevin.armswing.data.entity.Session
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val db: ArmSwingDatabase,
    private val bleManager: BleManager,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    val activeSession = activeSessionId.flatMapLatest { id ->
        if (id == null) flowOf(null) else db.sessionDao().getByIdFlow(id)
    }

    private var sampleJob: Job? = null
    @Volatile private var currentThreshold = 1.0f

    init {
        scope.launch { settingsRepository.omegaThreshold.collect { currentThreshold = it } }
        scope.launch {
            db.sessionDao().closeOrphanedSessions(
                cutoff = System.currentTimeMillis(),
                endedAt = System.currentTimeMillis()
            )
            Log.d("ArmSwing", "Orphaned sessions cleaned up")
        }
    }

    suspend fun startSession(label: String): Long {
        val id = db.sessionDao().insert(
            Session(label = label, startedAt = System.currentTimeMillis(), endedAt = null)
        )
        _activeSessionId.value = id
        sampleJob = scope.launch {
            bleManager.omegaReadings.collect { reading ->
                if (reading.omega >= currentThreshold) {
                    db.omegaSampleDao().insert(
                        OmegaSample(sessionId = id, timestampMs = reading.timestampMs, omega = reading.omega)
                    )
                    Log.d("ArmSwing", "DB: omega=${reading.omega} → session $id")
                }
            }
        }
        Log.d("ArmSwing", "Session $id started: $label")
        return id
    }

    suspend fun stopSession() {
        val id = _activeSessionId.value ?: return
        sampleJob?.cancel()
        sampleJob = null
        _activeSessionId.value = null
        db.sessionDao().closeSession(id, System.currentTimeMillis())
        Log.d("ArmSwing", "Session $id stopped")
    }

    fun getSessionsFlow() = db.sessionDao().getAllSessions()

    suspend fun deleteSessionsByIds(ids: List<Long>) = db.sessionDao().deleteByIds(ids)
}
