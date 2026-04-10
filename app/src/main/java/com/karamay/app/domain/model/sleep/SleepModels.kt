package com.karamay.app.domain.model.sleep

import java.time.LocalDateTime

enum class SleepStatus {
    AWAKE, ASLEEP, UNKNOWN;

    fun displayLabel(): String = when (this) {
        AWAKE   -> "Awake"
        ASLEEP  -> "Asleep"
        UNKNOWN -> "Unknown"
    }
}

data class SleepSignal(
    val status: SleepStatus       = SleepStatus.UNKNOWN,
    val confidence: Int           = 0,
    val deviceMotion: Int         = 0,
    val timestamp: LocalDateTime  = LocalDateTime.now(),
    val isTracking: Boolean       = false,
    val hasActiveSession: Boolean = false
)

data class SleepSegment(
    val id: Long                  = 0,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val status: SleepStatus
)

data class DailySleepSummary(
    val date: String,
    val totalSleepMinutes: Int,
    val timeInBedMinutes: Int,
    val awakenings: Int,
    /**
     * Minutes elapsed since 6 PM on the sleep-onset evening.
     * Use [com.karamay.app.core.utils.DateTimeUtils.offsetMinutesToClockTime] to convert
     * this to a human-readable clock time (e.g. "~11:45 PM").
     */
    val sleepOnsetMinutes: Int?   = null,
    val isEstimated: Boolean      = false
)

data class SleepTrends(
    val daysAnalyzed: Int,
    val averageSleepMinutes: Int,
    val totalSleepDebtMinutes: Int,
    val consistencyScore: Int,
    /**
     * The nightly sleep target used to compute [totalSleepDebtMinutes].
     * Surfaced here so the UI can display "vs Xh goal" without hard-coding
     * the baseline in the presentation layer.
     */
    val sleepGoalMinutes: Int     = 480,
)

data class SleepTelemetry(
    val timestamp: LocalDateTime,
    val confidence: Int,
    /**
     * Binary screen-state proxy: 0 = screen off (presumed still), 1 = screen on (user active).
     * Not a continuous motion sensor reading — stored at screen-on/off events only.
     */
    val deviceMotion: Int
)
