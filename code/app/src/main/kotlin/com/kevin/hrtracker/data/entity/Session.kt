package com.kevin.armswing.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val startedAt: Long,
    val endedAt: Long?,
    val note: String? = null
)
