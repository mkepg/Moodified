package com.moodified.app.domain.model.activity

data class ActivityTrends(
    val daysAnalyzed: Int,
    val averageSteps: Int,
    val averageActiveMinutes: Int,
    val bestDayDate: String?,
    val bestDaySteps: Int,
    val consistencyScore: Int,
)
