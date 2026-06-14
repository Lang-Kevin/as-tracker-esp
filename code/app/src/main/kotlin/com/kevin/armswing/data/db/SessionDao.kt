package com.kevin.armswing.data.db

import androidx.room.*
import com.kevin.armswing.data.entity.Session
import com.kevin.armswing.data.entity.WeekStat
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: Session): Long

    @Query("UPDATE Session SET endedAt = :endedAt WHERE id = :id")
    suspend fun closeSession(id: Long, endedAt: Long)

    @Query("UPDATE Session SET peakMps = :peakMps, avgMps = :avgMps, sampleCount = :sampleCount WHERE id = :id")
    suspend fun updateStats(id: Long, peakMps: Float, avgMps: Float, sampleCount: Int)

    @Query("SELECT * FROM Session WHERE deletedAt IS NULL ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM Session WHERE id = :id")
    suspend fun getById(id: Long): Session?

    @Query("SELECT * FROM Session WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<Session?>

    @Query("UPDATE Session SET endedAt = :endedAt WHERE endedAt IS NULL AND deletedAt IS NULL AND startedAt < :cutoff")
    suspend fun closeOrphanedSessions(cutoff: Long, endedAt: Long)

    @Query("UPDATE Session SET label = :label WHERE id = :id")
    suspend fun updateLabel(id: Long, label: String)

    @Query("UPDATE Session SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String)

    @Query("DELETE FROM Session WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE Session SET deletedAt = :timestamp WHERE id IN (:ids)")
    suspend fun softDeleteByIds(ids: List<Long>, timestamp: Long)

    @Query("UPDATE Session SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreSession(id: Long)

    @Query("SELECT * FROM Session WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrashFlow(): Flow<List<Session>>

    @Query("DELETE FROM Session WHERE deletedAt IS NOT NULL AND deletedAt < :cutoffMs")
    suspend fun purgeTrashBefore(cutoffMs: Long)

    @Query("SELECT COUNT(*) FROM Session WHERE endedAt IS NOT NULL")
    fun getCompletedSessionCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM((endedAt - startedAt) / 1000), 0) FROM Session WHERE endedAt IS NOT NULL")
    fun getTotalDurationS(): Flow<Long>

    @Query("SELECT COALESCE(AVG(avgMps), 0.0) FROM Session WHERE endedAt IS NOT NULL AND sampleCount > 0")
    fun getGlobalAvgMps(): Flow<Float>

    @Query("SELECT COALESCE(MAX((endedAt - startedAt) / 1000), 0) FROM Session WHERE endedAt IS NOT NULL")
    fun getLongestDurationS(): Flow<Long>

    @Query("""
        SELECT strftime('%Y-%W', startedAt / 1000, 'unixepoch') AS week,
               COUNT(*) AS sessionCount,
               SUM((endedAt - startedAt) / 60000) AS totalMinutes,
               AVG(avgMps) AS avgMps
        FROM Session
        WHERE endedAt IS NOT NULL AND startedAt >= :cutoffMs
        GROUP BY strftime('%Y-%W', startedAt / 1000, 'unixepoch')
        ORDER BY week DESC
    """)
    fun getWeeklyStats(cutoffMs: Long): Flow<List<WeekStat>>
}
