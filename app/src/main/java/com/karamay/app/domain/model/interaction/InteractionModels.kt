package com.karamay.app.domain.model.interaction

import java.time.LocalDateTime

enum class InteractionEventType {
    SCREEN_ON,
    SCREEN_OFF,
    UNLOCKED;

    fun displayLabel(): String = when (this) {
        SCREEN_ON -> "Screen On"
        SCREEN_OFF -> "Screen Off"
        UNLOCKED -> "Device Unlocked"
    }
}

// Represents a finalized period of phone usage (to be saved to DB in Phase 3)
data class InteractionSession(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val durationMinutes: Int,
    val unlockCount: Int
)

// The live, real-time state emitted to the ViewModels
data class InteractionSignal(
    val isTracking: Boolean = false,
    val isScreenOn: Boolean = false,
    val currentSessionDurationMs: Long = 0L,
    val totalScreenTimeTodayMs: Long = 0L,
    val unlocksToday: Int = 0,
    val lastEventType: InteractionEventType? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
)