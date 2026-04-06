package com.karamay.app.domain.repository

import com.karamay.app.domain.model.ActivityIntensity
import com.karamay.app.domain.model.ActivitySignal
import com.karamay.app.domain.model.DailyActivitySummary
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ActivityRepository {
    fun observeSignal(): Flow<ActivitySignal>
    val isTracking: Boolean
    fun startTracking(): Boolean
    fun stopTracking()
    fun resetSession()
    suspend fun purgeActivityTelemetryOlderThan(cutoffMillis: Long)
    suspend fun updateActivityIntensity(intensity: ActivityIntensity, confidence: Int)
    suspend fun flushTelemetryToDb()

    // ── Daily aggregation ────────────────────────────────────────────────────

    /** Emits the persisted summary for [date], or null when no data exists yet. */
    fun getDailySummary(date: LocalDate): Flow<DailyActivitySummary?>

    /**
     * Emits the seven most-recent daily summaries ending on [endDate], ordered
     * oldest-first. Days with no data are omitted rather than zero-filled so
     * the inference engine can distinguish "no data" from "genuinely inactive."
     */
    fun getWeeklySummaries(endDate: LocalDate): Flow<List<DailyActivitySummary>>
}
