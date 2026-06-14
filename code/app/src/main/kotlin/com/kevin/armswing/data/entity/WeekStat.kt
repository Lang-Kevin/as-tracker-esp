package com.kevin.armswing.data.entity

data class WeekStat(
    val week: String,
    val sessionCount: Int,
    val totalMinutes: Long,
    val avgMps: Float
)
