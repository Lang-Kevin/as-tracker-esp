package com.kevin.armswing.data.db

import androidx.room.*
import com.kevin.armswing.data.entity.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: Session): Long

    @Query("UPDATE Session SET endedAt = :endedAt WHERE id = :id")
    suspend fun closeSession(id: Long, endedAt: Long)

    @Query("SELECT * FROM Session ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM Session WHERE id = :id")
    suspend fun getById(id: Long): Session?

    @Query("SELECT * FROM Session WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<Session?>

    // Closes sessions left open by a crash or app-kill (startedAt older than cutoff, endedAt null)
    @Query("UPDATE Session SET endedAt = :endedAt WHERE endedAt IS NULL AND startedAt < :cutoff")
    suspend fun closeOrphanedSessions(cutoff: Long, endedAt: Long)

    @Query("UPDATE Session SET label = :label WHERE id = :id")
    suspend fun updateLabel(id: Long, label: String)

    @Query("UPDATE Session SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String)

    @Query("DELETE FROM Session WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
