package com.karamay.app.domain.model.interaction

import java.time.LocalDateTime

enum class InteractionEventType {
    SCREEN_ON,
    SCREEN_OFF,
    UNLOCKED;
    fun displayLabel(): String = when (this) {
        SCREEN_ON  -> "Screen On"
        SCREEN_OFF -> "Screen Off"
        UNLOCKED   -> "Device Unlocked"
    }
}

data class InteractionSession(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val durationMinutes: Int,
)

data class InteractionSignal(
    val isTracking: Boolean = false,
    val isScreenOn: Boolean = false,
    val currentSessionDurationMs: Long = 0L,
    val totalScreenTimeTodayMs: Long = 0L,
    val lateNightScreenTimeTodayMs: Long = 0L,
    val unlockCount: Int = 0,
    val lastEventType: InteractionEventType? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class InteractionTrends(
    val daysAnalyzed: Int,
    val averageScreenTimeMinutes: Int,
    val averageLateNightMinutes: Int,
    val consistencyScore: Int
)