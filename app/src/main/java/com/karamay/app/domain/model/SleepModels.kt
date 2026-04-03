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
    val ambientLight: Float       = 0f,
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

/**
 * Daily summary of a user's dominant sleep session.
 *
 * [sleepOnsetMinutes] — minutes from midnight when the sleep session started.
 *   e.g. 23:00 → 1380, 01:30 → 90, 00:00 → 0.
 *   Null if the session start time could not be determined.
 *
 * [isEstimated] — true when no finalized Play Services segments exist yet (i.e. the
 *   user just woke up and the Broadcast Receiver hasn't fired). The summary was built
 *   from LiveSleepSignalBus and should be replaced once the real data arrives.
 */
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
    val ambientLight: Float,
    val deviceMotion: Int
)