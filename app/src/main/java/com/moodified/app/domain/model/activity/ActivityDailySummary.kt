package com.moodified.app.domain.model.activity

data class ActivityDailySummary(
    val date: String,
    val totalSteps: Int,
    val activeMinutes: Int,
    val sedentaryMinutes: Int,
    val peakIntensity: ActivityIntensity,
    val isPartialDay: Boolean = false,
    // Sprint 1 Addition: Intensity distribution
    val minutesPerIntensityBand: Map<ActivityIntensity, Int> = emptyMap()
)