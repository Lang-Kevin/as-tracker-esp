package com.kevin.armswing.data.db

import androidx.room.*
import com.kevin.armswing.data.entity.VelocitySample
import kotlinx.coroutines.flow.Flow

@Dao
interface VelocitySampleDao {
    @Insert
    suspend fun insert(sample: VelocitySample): Long

    @Query("SELECT * FROM velocity_samples WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun getSamplesForSession(sessionId: Long): Flow<List<VelocitySample>>

    @Query("SELECT COUNT(*) FROM velocity_samples WHERE sessionId = :sessionId")
    fun getSampleCount(sessionId: Long): Flow<Int>

    @Query("SELECT * FROM velocity_samples WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getSamplesOnce(sessionId: Long): List<VelocitySample>
}
