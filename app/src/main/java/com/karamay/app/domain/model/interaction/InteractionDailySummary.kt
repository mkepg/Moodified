package com.karamay.app.domain.model.interaction

data class InteractionDailySummary(
    val date: String,
    val totalScreenTimeMinutes: Int,
    val lateNightUsageMinutes: Int = 0,
    val unlockCount: Int = 0,
    val isPartialDay: Boolean = false
)