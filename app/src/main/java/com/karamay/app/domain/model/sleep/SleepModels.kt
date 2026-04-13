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
    val timestamp: LocalDateTime  = LocalDateTime.now(),
    val isTracking: Boolean       = false,
    val hasActiveSession: Boolean = false
)

data class SleepSegment(
    val id: Long                  = 0,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val status: SleepStatus,
    val awakenings: Int           = 0,
    val timeInBedMinutes: Int     = 0,
    val totalSleepMinutes: Int    = 0,
    val confidence: Int           = 0
)

data class DailySleepSummary(
    val date: String,
    val totalSleepMinutes: Int,
    val timeInBedMinutes: Int,
    val awakenings: Int,
    val sleepOnsetMinutes: Int?   = null,
    val isEstimated: Boolean      = false
) {
    val sleepEfficiencyPercent: Int
        get() = if (timeInBedMinutes > 0) {
            ((totalSleepMinutes.toFloat() / timeInBedMinutes) * 100).toInt().coerceIn(0, 100)
        } else 0
}

data class SleepTrends(
    val daysAnalyzed: Int,
    val averageSleepMinutes: Int,
    val totalSleepDebtMinutes: Int,
    val consistencyScore: Int,
    val sleepGoalMinutes: Int     = 480,
)