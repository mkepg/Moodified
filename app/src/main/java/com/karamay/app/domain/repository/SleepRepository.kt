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
    val isTracking: Boolean
    fun startTracking(): Boolean
    fun stopTracking()
    fun getWeeklySummaries(endDate: LocalDate): Flow<List<DailySleepSummary>>
    fun getTelemetryBetween(start: LocalDateTime, end: LocalDateTime): Flow<List<SleepTelemetry>>
    suspend fun purgeTelemetryOlderThan(cutoffMillis: Long)

    // Newly added operations for the Receiver to pass data
    suspend fun insertSegments(segments: List<SleepSegment>)
    suspend fun insertTelemetry(telemetry: List<SleepTelemetry>)
}