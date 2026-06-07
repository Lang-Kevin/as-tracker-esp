package com.kevin.armswing.data.repository

import android.util.Log
import com.kevin.armswing.ble.BleManager
import com.kevin.armswing.data.db.ArmSwingDatabase
import com.kevin.armswing.data.entity.Session
import com.kevin.armswing.data.entity.VelocitySample
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class SessionRepository @Inject constructor(
    private val db: ArmSwingDatabase,
    private val bleManager: BleManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    val activeSession = activeSessionId.flatMapLatest { id ->
        if (id == null) flowOf(null) else db.sessionDao().getByIdFlow(id)
    }

    private var sampleJob: Job? = null
    private var peakMps = 0f
    private var runningSum = 0.0
    private var sampleCount = 0

    init {
        scope.launch {
            db.sessionDao().closeOrphanedSessions(
                cutoff = System.currentTimeMillis(),
                endedAt = System.currentTimeMillis()
            )
            Log.d("ArmSwing", "Orphaned sessions cleaned up")
        }
    }

    suspend fun startSession(label: String): Long {
        peakMps = 0f
        runningSum = 0.0
        sampleCount = 0
        val id = db.sessionDao().insert(
            Session(label = label, startedAt = System.currentTimeMillis())
        )
        _activeSessionId.value = id
        sampleJob = scope.launch {
            bleManager.velocityReadings.collect { reading ->
                val v = reading.velocityMps
                db.velocitySampleDao().insert(
                    VelocitySample(sessionId = id, timestampMs = reading.timestampMs, velocityMps = v)
                )
                peakMps = max(peakMps, v)
                runningSum += v
                sampleCount++
                Log.d("ArmSwing", "DB: velocity=$v m/s → session $id")
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
        if (sampleCount > 0) {
            db.sessionDao().updateStats(
                id = id,
                peakMps = peakMps,
                avgMps = (runningSum / sampleCount).toFloat(),
                sampleCount = sampleCount
            )
        }
        Log.d("ArmSwing", "Session $id stopped — peak=$peakMps avg=${runningSum/sampleCount.coerceAtLeast(1)} n=$sampleCount")
    }

    fun getSessionsFlow() = db.sessionDao().getAllSessions()

    suspend fun deleteSessionsByIds(ids: List<Long>) = db.sessionDao().deleteByIds(ids)
}
