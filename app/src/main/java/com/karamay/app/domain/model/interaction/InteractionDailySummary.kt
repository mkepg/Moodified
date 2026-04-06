package com.karamay.app.domain.model

/**
 * Represents a finalized daily aggregation of phone interaction metrics.
 * This is the domain model used by the UI and ViewModels.
 */
data class InteractionDailySummary(
    val date: String,
    val totalScreenTimeMinutes: Int,
    val unlocks: Int
)