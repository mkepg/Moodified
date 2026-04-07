package com.karamay.app.domain.model.interaction

data class InteractionDailySummary(
    val date: String,
    val totalScreenTimeMinutes: Int,
    // unlocks removed
    val lateNightUsageMinutes: Int = 0,   // minutes of screen-on time between 00:00–05:00
    val isPartialDay: Boolean = false
)
