package com.kevin.armswing.data.db

import androidx.room.*
import com.kevin.armswing.data.entity.OmegaSample
import kotlinx.coroutines.flow.Flow

@Dao
interface OmegaSampleDao {
    @Insert
    suspend fun insert(sample: OmegaSample): Long

    @Query("SELECT * FROM OmegaSample WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun getSamplesForSession(sessionId: Long): Flow<List<OmegaSample>>

    @Query("SELECT COUNT(*) FROM OmegaSample WHERE sessionId = :sessionId")
    fun getSampleCount(sessionId: Long): Flow<Int>

    @Query("SELECT * FROM OmegaSample WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getSamplesOnce(sessionId: Long): List<OmegaSample>
}
