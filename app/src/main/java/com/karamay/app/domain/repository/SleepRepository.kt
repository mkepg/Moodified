package com.karamay.app.domain.repository

import com.karamay.app.domain.model.DailySleepSummary
import com.karamay.app.domain.model.SleepSegment
import com.karamay.app.domain.model.SleepSignal
import com.karamay.app.domain.model.SleepTelemetry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

interface SleepRepository {
    fun observeLiveSignal(): Flow<SleepSignal>
    fun getSegmentsForDate(date: LocalDate): Flow<List<SleepSegment>>

    // Fix #22: getDailySummary() removed from this interface.
    // It existed as a simpler, divergent summary builder inside SleepRepositoryImpl that
    // produced materially different results from GetDailySleepSummaryUseCase for the same input.
    // All callers now go through the use case, which has edge-trimming and gap-stitching.

    val isTracking: Boolean
    fun startTracking(): Boolean
    fun stopTracking()
    fun getWeeklySummaries(endDate: LocalDate): Flow<List<DailySleepSummary>>
    fun getTelemetryBetween(start: LocalDateTime, end: LocalDateTime): Flow<List<SleepTelemetry>>
    suspend fun purgeTelemetryOlderThan(cutoffMillis: Long)
}
