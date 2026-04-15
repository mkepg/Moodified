package com.karamay.app.domain.repository
import com.karamay.app.domain.model.sleep.DailySleepSummary
import com.karamay.app.domain.model.sleep.SleepSegment
import com.karamay.app.domain.model.sleep.SleepSignal
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface SleepRepository {
    fun observeLiveSignal(): Flow<SleepSignal>
    fun getSegmentsForDate(date: LocalDate): Flow<List<SleepSegment>>
    val isTracking: Boolean
    fun startTracking(): Boolean
    fun stopTracking()
    fun pauseTracking() // [ADDED]: Pause contract
    fun getWeeklySummaries(endDate: LocalDate): Flow<List<DailySleepSummary>>
    suspend fun persistSegments(segments: List<SleepSegment>)
    suspend fun flushSleepDataToDb()
    fun hasUsagePermission(): Boolean
}