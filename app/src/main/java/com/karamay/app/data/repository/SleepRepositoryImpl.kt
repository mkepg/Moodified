package com.karamay.app.data.repository

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.SleepSegmentRequest
import com.karamay.app.core.service.TrackingService
import com.karamay.app.core.utils.BatteryUtils
import com.karamay.app.core.utils.SleepTimeUtils
import com.karamay.app.data.local.dao.SleepSegmentDao
import com.karamay.app.data.local.dao.SleepTelemetryDao
import com.karamay.app.data.local.datasource.SleepPreferencesDataSource
import com.karamay.app.data.local.entity.SleepSegmentEntity
import com.karamay.app.data.local.entity.SleepTelemetryEntity
import com.karamay.app.data.receiver.sleep.SleepReceiver
import com.karamay.app.domain.model.DailySleepSummary
import com.karamay.app.domain.model.SleepSegment
import com.karamay.app.domain.model.SleepSignal
import com.karamay.app.domain.model.SleepStatus
import com.karamay.app.domain.model.SleepTelemetry
import com.karamay.app.domain.repository.SleepRepository
import com.karamay.app.domain.usecase.sleep.GetDailySleepSummaryUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sleepSegmentDao: SleepSegmentDao,
    private val sleepTelemetryDao: SleepTelemetryDao,
    private val preferencesDataSource: SleepPreferencesDataSource
) : SleepRepository {

    companion object {
        private const val TAG = "SleepRepo"
        private val SESSION_GAP_HOURS = GetDailySleepSummaryUseCase.SESSION_GAP_HOURS
        private const val SESSION_RESUME_HOURS = 12L
    }

    private val _isTracking = AtomicBoolean(preferencesDataSource.isTracking)
    override val isTracking: Boolean get() = preferencesDataSource.isTracking

    private val _signals = MutableStateFlow(
        SleepSignal(
            isTracking       = preferencesDataSource.isTracking,
            hasActiveSession = preferencesDataSource.hasActiveSession
        )
    )
    override fun observeLiveSignal(): Flow<SleepSignal> = _signals.asStateFlow()

    private val activityRecognitionClient = ActivityRecognition.getClient(context)

    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, SleepReceiver::class.java).apply {
                action = SleepReceiver.ACTION_SLEEP_DATA
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    override fun startTracking(): Boolean {
        if (!BatteryUtils.isIgnoringBatteryOptimizations(context)) {
            Log.w(TAG, "Battery optimisation active — sleep events may be missed in Doze.")
        }

        // ✦ THE FIX: Atomic early exit guard.
        // If we are already tracking, return immediately to prevent intent flooding.
        if (_isTracking.getAndSet(true)) return true

        preferencesDataSource.isTracking       = true
        preferencesDataSource.hasActiveSession = true

        val lastAsleep = preferencesDataSource.lastAsleepTimestamp
        val isResumingSession = preferencesDataSource.hasActiveSession &&
                (lastAsleep == null ||
                        Duration.between(lastAsleep, LocalDateTime.now()).toHours() < SESSION_RESUME_HOURS)

        if (!isResumingSession) {
            preferencesDataSource.lastAsleepTimestamp = null
        }

        _signals.update { it.copy(isTracking = true, hasActiveSession = true) }

        ContextCompat.startForegroundService(
            context,
            Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START_SLEEP
            }
        )

        activityRecognitionClient
            .requestSleepSegmentUpdates(
                pendingIntent,
                SleepSegmentRequest.getDefaultSleepSegmentRequest()
            )
            .addOnSuccessListener {
                Log.d(TAG, "Sleep segment updates registered. resume=$isResumingSession")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Sleep segment registration failed: ${e.message}")
                _isTracking.set(false)
                preferencesDataSource.isTracking = false
                _signals.update { sig -> sig.copy(isTracking = false) }
            }

        return true
    }

    @SuppressLint("MissingPermission")
    override fun stopTracking() {
        // Write the authoritative stopped state to both the AtomicBoolean and
        // SharedPreferences *before* touching the signal flow. This ensures that
        // any in-flight SleepReceiver event that calls updateLiveSignal() after
        // this point will read _isTracking == false and will NOT flip isTracking
        // back to true in the emitted signal.
        if (!_isTracking.getAndSet(false)) return  // already stopped — nothing to do

        preferencesDataSource.isTracking = false
        _signals.update { it.copy(isTracking = false) }

        context.startService(
            Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_STOP_SLEEP
            }
        )
        activityRecognitionClient.removeSleepSegmentUpdates(pendingIntent)
            .addOnFailureListener { e -> Log.w(TAG, "removeSleepSegmentUpdates failed: ${e.message}") }
    }

    override fun resetSession() {
        if (_isTracking.get()) stopTracking()
        preferencesDataSource.resetSession()
        _signals.value = SleepSignal(
            isTracking       = false,
            hasActiveSession = false
        )
    }

    override suspend fun updateLiveSignal(
        status: SleepStatus,
        confidence: Int,
        motion: Int,
        time: LocalDateTime
    ) {
        // Guard: if tracking was stopped while this event was in flight, discard it.
        // This is the fix for the flicker bug — incoming SleepClassifyEvents were
        // previously overwriting isTracking = false with whatever was in the signal,
        // racing with stopTracking() and causing the UI to flip back to "tracking".
        if (!_isTracking.get()) {
            Log.d(TAG, "updateLiveSignal called while stopped — discarding event.")
            return
        }

        val previousStatus  = _signals.value.status
        val isEnteringSleep = (status == SleepStatus.ASLEEP || status == SleepStatus.UNKNOWN) &&
                previousStatus == SleepStatus.AWAKE
        if (isEnteringSleep) {
            preferencesDataSource.lastAsleepTimestamp = time
        }

        // isTracking is intentionally NOT copied from the incoming event — the
        // AtomicBoolean is the single source of truth for tracking state.
        _signals.update { current ->
            current.copy(
                status           = status,
                confidence       = confidence,
                deviceMotion     = motion,
                timestamp        = time,
                hasActiveSession = preferencesDataSource.hasActiveSession
                // isTracking is left as-is in the flow — only start/stopTracking mutate it
            )
        }
    }

    override suspend fun persistTelemetry(telemetry: List<SleepTelemetry>) {
        val entities = telemetry.map {
            SleepTelemetryEntity(
                timestampMillis = it.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                confidence      = it.confidence,
                deviceMotion    = it.deviceMotion
            )
        }
        sleepTelemetryDao.insertTelemetry(entities)
    }

    override suspend fun persistSegments(segments: List<SleepSegment>) {
        sleepSegmentDao.insertSegments(segments.map { SleepSegmentEntity.fromDomain(it) })
    }

    override fun getSegmentsForDate(date: LocalDate): Flow<List<SleepSegment>> {
        val windowStartMillis = date.minusDays(1).atTime(6, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val windowEndMillis   = date.plusDays(1).atTime(6, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return sleepSegmentDao.getSegmentsBetween(windowStartMillis, windowEndMillis)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getWeeklySummaries(endDate: LocalDate): Flow<List<DailySleepSummary>> {
        val broadStartMillis = endDate.minusDays(8).atTime(6, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val broadEndMillis   = endDate.plusDays(1).atTime(6, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return sleepSegmentDao.getSegmentsBetween(broadStartMillis, broadEndMillis)
            .map { entities ->
                val allSegments = entities.map { it.toDomain() }.sortedBy { it.startTime }
                val sessions    = mutableListOf<List<SleepSegment>>()
                var current     = mutableListOf<SleepSegment>()
                for (seg in allSegments) {
                    if (current.isNotEmpty()) {
                        val gapHours = Duration.between(current.last().endTime, seg.startTime).toHours()
                        if (gapHours >= SESSION_GAP_HOURS) {
                            sessions.add(current.toList())
                            current = mutableListOf()
                        }
                    }
                    current.add(seg)
                }
                if (current.isNotEmpty()) sessions.add(current.toList())
                (0L..6L).mapNotNull { daysBack ->
                    val date    = endDate.minusDays(daysBack)
                    val session = sessions.firstOrNull { s -> s.last().endTime.toLocalDate() == date }
                        ?: return@mapNotNull null
                    if (session.any { it.status == SleepStatus.ASLEEP })
                        buildSummaryFromSegments(date.toString(), session)
                    else null
                }
            }
    }

    override fun getTelemetryBetween(start: LocalDateTime, end: LocalDateTime): Flow<List<SleepTelemetry>> {
        val startMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis   = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return sleepTelemetryDao.getTelemetryBetween(startMillis, endMillis)
            .map { entities ->
                entities.map {
                    SleepTelemetry(
                        timestamp    = Instant.ofEpochMilli(it.timestampMillis)
                            .atZone(ZoneId.systemDefault()).toLocalDateTime(),
                        confidence   = it.confidence,
                        deviceMotion = it.deviceMotion
                    )
                }
            }
    }

    override suspend fun purgeTelemetryOlderThan(cutoffMillis: Long) {
        sleepTelemetryDao.deleteOlderThan(cutoffMillis)
    }

    private fun buildSummaryFromSegments(date: String, segments: List<SleepSegment>): DailySleepSummary {
        val sleepSegs = segments.filter { it.status == SleepStatus.ASLEEP }.sortedBy { it.startTime }
        if (sleepSegs.isEmpty()) return DailySleepSummary(date, 0, 0, 0, 0)
        val totalSleepMinutes = sleepSegs
            .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
        val sessionStart      = sleepSegs.first().startTime
        val sessionEnd        = sleepSegs.last().endTime
        val timeInBedMinutes  = Duration.between(sessionStart, sessionEnd).toMinutes().toInt()
        var sleepBlocks = 0
        var currentEnd: LocalDateTime? = null
        for (seg in sleepSegs) {
            if (currentEnd == null ||
                Duration.between(currentEnd, seg.startTime).toMinutes() > 5) sleepBlocks++
            currentEnd = seg.endTime
        }
        return DailySleepSummary(
            date              = date,
            totalSleepMinutes = totalSleepMinutes,
            timeInBedMinutes  = timeInBedMinutes,
            awakenings        = (sleepBlocks - 1).coerceAtLeast(0),
            sleepOnsetMinutes = SleepTimeUtils.minutesSince6PM(sessionStart),
            isEstimated       = false
        )
    }
}