package com.karamay.app.domain.model.activity

data class ActivityDailySummary(
    /** ISO date string, e.g. "2026-04-06" */
    val date: String,
    val totalSteps: Int,
    val activeMinutes: Int,
    val sedentaryMinutes: Int,
    val peakIntensity: ActivityIntensity,
    /**
     * True when tracking was stopped or the day rolled over before midnight,
     * meaning the record covers less than a full day. The inference engine
     * should weight partial-day records lower than complete ones.
     */
    val isPartialDay: Boolean = false,
)
