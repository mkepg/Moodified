package com.karamay.app.domain.repository

import com.karamay.app.domain.model.sleep.DailySleepSummary
import com.karamay.app.domain.model.sleep.SleepSegment
import com.karamay.app.domain.model.sleep.SleepSignal
import com.karamay.app.domain.model.sleep.SleepStatus
import com.karamay.app.domain.model.sleep.SleepTelemetry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

interface SleepRepository {
    fun observeLiveSignal(): Flow<SleepSignal>
    fun getSegmentsForDate(date: LocalDate): Flow<List<SleepSegment>>
    val isTracking: Boolean
    fun startTracking(): Boolean
    fun stopTracking()
    fun resetSession()
    fun getWeeklySummaries(endDate: LocalDate): Flow<List<DailySleepSummary>>
    fun getTelemetryBetween(start: LocalDateTime, end: LocalDateTime): Flow<List<SleepTelemetry>>
    suspend fun purgeTelemetryOlderThan(cutoffMillis: Long)
    suspend fun updateLiveSignal(status: SleepStatus, confidence: Int, motion: Int, time: LocalDateTime)
    suspend fun persistTelemetry(telemetry: List<SleepTelemetry>)
    suspend fun persistSegments(segments: List<SleepSegment>)
}