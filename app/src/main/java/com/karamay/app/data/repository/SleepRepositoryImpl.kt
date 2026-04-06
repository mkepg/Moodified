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

/**
 * Sleep tracking repository.
 *
 * ## Bug 10 fix — coordinated session gap constants
 *
 * Three separate constants previously governed session boundaries:
 *
 * | Constant              | Location                       | Value |
 * |-----------------------|--------------------------------|-------|
 * | SESSION_GAP_HOURS     | GetDailySleepSummaryUseCase    | 4     |
 * | SESSION_GAP_HOURS     | SleepRepositoryImpl.getWeeklySummaries | 4 |
 * | SESSION_RESUME_HOURS  | SleepRepositoryImpl.startTracking | 3   |
 *
 * With SESSION_RESUME_HOURS = 3 and SESSION_GAP_HOURS = 4: if a nap ended and the
 * user fell back asleep 3.5 hours later, startTracking() would treat it as a new
 * session (gap > SESSION_RESUME_HOURS), but isolatePrimarySleepSession() would keep
 * them in the same session (gap < SESSION_GAP_HOURS). This caused nap segments to
 * be attributed to the wrong date.
 *
 * **Fix:** All three constants now use the same value, sourced from
 * [GetDailySleepSummaryUseCase.SESSION_GAP_HOURS] as the single source of truth.
 *
 * ## Bug 6 fix — UNKNOWN status and lastAsleepTimestamp
 *
 * [updateLiveSignal] previously wrote [lastAsleepTimestamp] only for ASLEEP status.
 * With the three-state classification in [SleepReceiver], the status may be UNKNOWN
 * during light sleep / sleep onset. We now also write [lastAsleepTimestamp] when
 * transitioning into UNKNOWN from AWAKE, preserving the session-resume logic for
 * gradual sleep onset.
 */
@Singleton
class SleepRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sleepSegmentDao: SleepSegmentDao,
    private val sleepTelemetryDao: SleepTelemetryDao,
    private val preferencesDataSource: SleepPreferencesDataSource
) : SleepRepository {

    companion object {
        private const val TAG = "SleepRepo"

        /**
         * Single source of truth for the session gap threshold.
         * Sleep sessions separated by ≥ this many hours are treated as distinct.
         * Must equal [GetDailySleepSummaryUseCase.SESSION_GAP_HOURS].
         */
        private val SESSION_GAP_HOURS   = GetDailySleepSummaryUseCase.SESSION_GAP_HOURS

        /**
         * A paused session is resumed (rather than reset) if the user restarts
         * tracking within this window. Must equal [SESSION_GAP_HOURS] so that the
         * session attribution logic in the use case and the resume logic here are
         * consistent.
         */
        private val SESSION_RESUME_HOURS = SESSION_GAP_HOURS
    }

    private val _isTracking = AtomicBoolean(false)
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

    // ── Public API ────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    override fun startTracking(): Boolean {
        if (!BatteryUtils.isIgnoringBatteryOptimizations(context)) {
            Log.w(TAG, "Battery optimisation active — sleep events may be missed in Doze.")
        }
        if (_isTracking.getAndSet(true)) return true

        preferencesDataSource.isTracking       = true
        preferencesDataSource.hasActiveSession = true

        val lastAsleep        = preferencesDataSource.lastAsleepTimestamp
        val isResumingSession = lastAsleep != null &&
                Duration.between(lastAsleep, LocalDateTime.now()).toHours() < SESSION_RESUME_HOURS

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
                Log.d(TAG, "Sleep segment updates registered successfully.")
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
        preferencesDataSource.isTracking = false
        _signals.update { it.copy(isTracking = false) }
        if (!_isTracking.getAndSet(false)) return

        context.startService(
            Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_STOP_SLEEP
            }
        )
        activityRecognitionClient.removeSleepSegmentUpdates(pendingIntent)
            .addOnFailureListener { e -> Log.w(TAG, "removeSleepSegmentUpdates failed: ${e.message}") }
    }

    override fun resetSession() {
        preferencesDataSource.resetSession()
        val currentlyTracking = preferencesDataSource.isTracking
        _signals.value = SleepSignal(
            isTracking       = currentlyTracking,
            hasActiveSession = currentlyTracking
        )
    }

    /**
     * Updates the live sleep signal from a new [SleepClassifyEvent].
     *
     * ### Bug 6 fix — UNKNOWN status tracking
     *
     * [lastAsleepTimestamp] is now written when the status is either ASLEEP or UNKNOWN
     * (transitional / light sleep), as long as the *previous* status was AWAKE.
     * This ensures that gradual sleep onset — where confidence rises from 30 → 55 →
     * 70 → 85 over 20 minutes — records the correct onset time (when confidence
     * crossed 50, entering UNKNOWN) rather than the later time when it crossed 72
     * (ASLEEP), which could be 10–20 minutes into the actual sleep period.
     */
    override suspend fun updateLiveSignal(
        status: SleepStatus,
        confidence: Int,
        motion: Int,
        time: LocalDateTime
    ) {
        val previousStatus = _signals.value.status
        val isEnteringSleep = (status == SleepStatus.ASLEEP || status == SleepStatus.UNKNOWN) &&
                previousStatus == SleepStatus.AWAKE

        if (isEnteringSleep) {
            preferencesDataSource.lastAsleepTimestamp = time
        }

        _signals.update { current ->
            current.copy(
                status           = status,
                confidence       = confidence,
                deviceMotion     = motion,
                timestamp        = time,
                hasActiveSession = preferencesDataSource.hasActiveSession
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
        var currentEnd : LocalDateTime? = null
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