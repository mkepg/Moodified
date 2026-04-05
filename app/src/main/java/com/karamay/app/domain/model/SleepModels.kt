package com.karamay.app.domain.model

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
    val isTracking: Boolean       = false
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
    val sleepOnsetMinutes: Int?   = null,
    val isEstimated: Boolean      = false
)

data class SleepTrends(
    val daysAnalyzed: Int,
    val averageSleepMinutes: Int,
    val totalSleepDebtMinutes: Int,
    val consistencyScore: Int
)

data class SleepTelemetry(
    val timestamp: LocalDateTime,
    val confidence: Int,
    val deviceMotion: Int
)