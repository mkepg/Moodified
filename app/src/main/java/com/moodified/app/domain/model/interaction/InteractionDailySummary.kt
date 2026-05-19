package com.moodified.app.domain.model.interaction

data class InteractionDailySummary(
    val date: String,
    val totalScreenTimeMinutes: Int,
    val lateNightUsageMinutes: Int = 0,
    val unlockCount: Int = 0,
    // Sprint 1 Additions: Session frequency metrics
    val sessionCount: Int = 0,
    val averageSessionDurationMinutes: Int = 0
)