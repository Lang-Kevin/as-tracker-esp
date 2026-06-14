package com.kevin.armswing.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kevin.shared.domain.SoftDeletable

@Entity
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val peakMps: Float = 0f,
    val avgMps: Float = 0f,
    val sampleCount: Int = 0,
    val note: String? = null,
    override val deletedAt: Long? = null
) : SoftDeletable
