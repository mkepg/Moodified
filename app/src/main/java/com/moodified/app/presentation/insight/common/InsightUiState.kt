package com.moodified.app.presentation.insight.common

/**
 * Shared UI status across the user-facing Insight screens.
 * Maps repository state onto the four states a user-facing screen must handle.
 */
sealed interface InsightStatus {
    data object Loading : InsightStatus
    data object PermissionRequired : InsightStatus
    data object TrackingOff : InsightStatus
    data object Empty : InsightStatus
    data object Ready : InsightStatus
}

data class WeeklyBar(
    val label: String,
    val value: Int,
    val isToday: Boolean,
)
