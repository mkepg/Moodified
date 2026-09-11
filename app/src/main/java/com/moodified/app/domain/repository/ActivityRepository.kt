package com.moodified.app.domain.repository

import com.moodified.app.domain.model.activity.ActivityBlock
import com.moodified.app.domain.model.activity.ActivityDailySummary
import com.moodified.app.domain.model.activity.ActivityIntensity
import com.moodified.app.domain.model.activity.ActivitySignal
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ActivityRepository {
    fun observeSignal(): Flow<ActivitySignal>

    val isTracking: Boolean

    fun startTracking(): Boolean

    fun stopTracking()

    fun pauseTracking()

    suspend fun purgeActivityTelemetryOlderThan(cutoffMillis: Long)

    suspend fun updateActivityIntensity(
        intensity: ActivityIntensity,
        confidence: Int,
    )

    suspend fun flushTelemetryToDb()

    suspend fun insertMockSummary(summary: ActivityDailySummary)

    fun getDailySummary(date: LocalDate): Flow<ActivityDailySummary?>

    fun getWeeklySummaries(endDate: LocalDate): Flow<List<ActivityDailySummary>>

    fun getActivityBlocksForDate(date: LocalDate): Flow<List<ActivityBlock>>
}
