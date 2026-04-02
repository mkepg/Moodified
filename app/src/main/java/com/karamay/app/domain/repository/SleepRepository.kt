package com.karamay.app.domain.repository

import com.karamay.app.domain.model.DailySleepSummary
import com.karamay.app.domain.model.SleepSegment
import com.karamay.app.domain.model.SleepSignal
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface SleepRepository {
    // Live Dev Monitor Flow
    fun observeLiveSignal(): Flow<SleepSignal>

    // Historical Data Flows
    fun getSegmentsForDate(date: LocalDate): Flow<List<SleepSegment>>
    fun getDailySummary(date: LocalDate): Flow<DailySleepSummary?>

    // Control
    val isTracking: Boolean
    fun startTracking(): Boolean
    fun stopTracking()

    fun getWeeklySummaries(endDate: LocalDate): Flow<List<DailySleepSummary>>
}