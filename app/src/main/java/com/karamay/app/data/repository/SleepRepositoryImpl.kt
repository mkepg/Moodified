package com.karamay.app.data.repository

import android.content.Context
import android.util.Log
import com.karamay.app.core.coordination.PollingJob
import com.karamay.app.core.coordination.TrackingCoordinator
import com.karamay.app.core.utils.SleepTimeUtils
import com.karamay.app.data.local.dao.sleep.SleepSegmentDao
import com.karamay.app.data.local.dao.sleep.SleepTelemetryDao
import com.karamay.app.data.local.datasource.DeviceSensorDataSource
import com.karamay.app.data.local.datasource.SleepPreferencesDataSource
import com.karamay.app.data.local.datasource.UsageStatsDataSource
import com.karamay.app.data.local.entity.sleep.SleepSegmentEntity
import com.karamay.app.data.local.entity.sleep.SleepTelemetryEntity
import com.karamay.app.domain.model.sleep.DailySleepSummary
import com.karamay.app.domain.model.sleep.SleepSegment
import com.karamay.app.domain.model.sleep.SleepSignal
import com.karamay.app.domain.model.sleep.SleepStatus
import com.karamay.app.domain.model.sleep.SleepTelemetry
import com.karamay.app.domain.repository.SleepRepository
import com.karamay.app.domain.usecase.sleep.CalculateSleepSegmentsUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sleepSegmentDao:           SleepSegmentDao,
    private val sleepTelemetryDao:         SleepTelemetryDao,
    private val preferencesDataSource:     SleepPreferencesDataSource,
    private val usageStatsDataSource:      UsageStatsDataSource,
    private val deviceSensorDataSource:    DeviceSensorDataSource,
    private val inferSleepSegmentsUseCase: CalculateSleepSegmentsUseCase,
    // Phase 2: coordinator owns service intent dispatch.
    private val coordinator:               TrackingCoordinator,
) : SleepRepository {

    companion object {
        private const val TAG                       = "SleepRepo"
        private const val INFERENCE_POLL_INTERVAL   = 5 * 60_000L
        private const val SESSION_GAP_HOURS         = 4L
        private const val MIN_PERSIST_MINUTES       = 60L

        /**
         * P2: only attempt inference during morning hours (before 2 PM).
         * Evening/night hours are skipped — nothing to infer yet.
         * This prevents the old bug of stamping a future date key prematurely.
         */
        private const val MORNING_CUTOFF_HOUR = 14
    }

    private val scope      = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()

    // Phase 2: single source of truth.
    @Volatile private var _trackingActive: Boolean = preferencesDataSource.isTracking
    override val isTracking: Boolean get() = _trackingActive

    private val _signals = MutableStateFlow(
        SleepSignal(
            isTracking       = preferencesDataSource.isTracking,
            hasActiveSession = preferencesDataSource.hasActiveSession
        )
    )
    override fun observeLiveSignal(): Flow<SleepSignal> = _signals.asStateFlow()

    // Phase 2: PollingJob replaces the manual pollJob / startPollLoop pattern.
    private val poller = PollingJob(
        scope      = scope,
        mutex      = stateMutex,
        intervalMs = INFERENCE_POLL_INTERVAL,
        tag        = "$TAG/inference",
        isActive   = { _trackingActive }
    ) { runInferenceAndPersist() }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun startTracking(): Boolean {
        if (_trackingActive) { Log.d(TAG, "startTracking: already running."); return true }
        if (!usageStatsDataSource.hasPermission()) {
            Log.w(TAG, "startTracking: UsageStats permission not granted.")
            return false
        }
        _trackingActive = true
        preferencesDataSource.isTracking      = true
        preferencesDataSource.hasActiveSession = true
        _signals.update { it.copy(isTracking = true, hasActiveSession = true) }
        Log.d(TAG, "startTracking: launching inference poll loop.")
        poller.start()
        return true
    }

    override fun stopTracking() {
        if (!_trackingActive) return
        _trackingActive = false
        preferencesDataSource.isTracking = false
        poller.stop()
        _signals.update { it.copy(isTracking = false) }
        Log.d(TAG, "stopTracking: poll loop cancelled.")
    }

    override suspend fun updateLiveSignal(
        status:     SleepStatus,
        confidence: Int,
        motion:     Int,
        time:       LocalDateTime
    ) {
        if (!_trackingActive) return
        if (status == SleepStatus.UNKNOWN || status == SleepStatus.ASLEEP) {
            preferencesDataSource.lastScreenOffMillis =
                time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else if (status == SleepStatus.AWAKE) {
            val offMs = preferencesDataSource.lastScreenOffMillis
            if (offMs > 0) {
                val durationMs = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - offMs
                if (durationMs > 0) {
                    val inferredConf = ((durationMs / (8 * 3_600_000f)) * 100f)
                        .toInt().coerceIn(0, 100)
                    sleepTelemetryDao.insertTelemetry(listOf(
                        SleepTelemetryEntity(
                            timestampMillis = offMs,
                            confidence      = inferredConf,
                            deviceMotion    = if (motion > 0) 1 else 0
                        )
                    ))
                }
            }
            preferencesDataSource.lastScreenOffMillis = -1L
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

    // ─── Inference ────────────────────────────────────────────────────────────

    private suspend fun runInferenceAndPersist() {
        val now = LocalDateTime.now()
        // P2: skip outside morning inference window.
        if (now.hour >= MORNING_CUTOFF_HOUR) {
            Log.v(TAG, "Outside morning window (hour=${now.hour}) — skipping.")
            return
        }
        if (!usageStatsDataSource.hasPermission()) return

        val targetDate = LocalDate.now()
        val dateKey    = targetDate.toString()
        if (preferencesDataSource.lastInferredDate == dateKey) {
            Log.v(TAG, "Inference already ran for $dateKey — skipping.")
            return
        }

        Log.d(TAG, "Running morning inference for $dateKey…")
        val zone        = ZoneId.systemDefault()
        val windowStart = targetDate.minusDays(1)
            .atTime(CalculateSleepSegmentsUseCase.SLEEP_EARLIEST_HOUR, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val windowEnd   = targetDate
            .atTime(CalculateSleepSegmentsUseCase.WAKE_LATEST_HOUR, 0)
            .atZone(zone).toInstant().toEpochMilli()

        val rawGaps      = usageStatsDataSource.queryScreenOffGaps(windowStart, windowEnd)
        val hasMotion    = deviceSensorDataSource.hasSignificantMotionSensor()
        val arConfidence = deviceSensorDataSource.queryLatestArStillConfidence()
        val segments     = inferSleepSegmentsUseCase(
            targetDate        = targetDate,
            rawGaps           = rawGaps,
            hasMotionSensor   = hasMotion,
            arStillConfidence = arConfidence
        )

        if (segments.isEmpty()) { Log.d(TAG, "No qualifying window for $dateKey."); return }
        val primary     = segments.first()
        val durationMin = Duration.between(primary.startTime, primary.endTime).toMinutes()
        if (durationMin < MIN_PERSIST_MINUTES) {
            Log.d(TAG, "Segment too short ($durationMin min) — skipping."); return
        }

        sleepSegmentDao.insertSegments(listOf(SleepSegmentEntity.fromDomain(primary)))
        preferencesDataSource.cacheInferenceResult(
            date         = targetDate,
            sleepStart   = primary.startTime,
            sleepEnd     = primary.endTime,
            sleepMinutes = durationMin.toInt(),
            confidence   = 75
        )
        _signals.update { sig ->
            sig.copy(
                status           = SleepStatus.ASLEEP,
                confidence       = 75,
                timestamp        = primary.endTime,
                hasActiveSession = true
            )
        }
        Log.d(TAG, "Persisted: ${primary.startTime} → ${primary.endTime} ($durationMin min)")
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    override fun getSegmentsForDate(date: LocalDate): Flow<List<SleepSegment>> {
        val zone          = ZoneId.systemDefault()
        val windowStartMs = date.minusDays(1).atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
        val windowEndMs   = date.plusDays(1).atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
        return sleepSegmentDao.getSegmentsBetween(windowStartMs, windowEndMs)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getWeeklySummaries(endDate: LocalDate): Flow<List<DailySleepSummary>> {
        val zone         = ZoneId.systemDefault()
        val broadStartMs = endDate.minusDays(8).atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
        val broadEndMs   = endDate.plusDays(1).atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
        return sleepSegmentDao.getSegmentsBetween(broadStartMs, broadEndMs)
            .map { entities ->
                val all      = entities.map { it.toDomain() }.sortedBy { it.startTime }
                val sessions = groupIntoSessions(all)
                (0L..6L).mapNotNull { daysBack ->
                    val d       = endDate.minusDays(daysBack)
                    val session = sessions.firstOrNull { s -> s.last().endTime.toLocalDate() == d }
                        ?: return@mapNotNull null
                    buildSummary(d.toString(), session)
                }
            }
    }

    override fun getTelemetryBetween(
        start: LocalDateTime, end: LocalDateTime
    ): Flow<List<SleepTelemetry>> {
        val startMs = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMs   = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return sleepTelemetryDao.getTelemetryBetween(startMs, endMs)
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

    override suspend fun persistTelemetry(telemetry: List<SleepTelemetry>) {
        sleepTelemetryDao.insertTelemetry(
            telemetry.map {
                SleepTelemetryEntity(
                    timestampMillis = it.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    confidence      = it.confidence,
                    deviceMotion    = it.deviceMotion
                )
            }
        )
    }

    override suspend fun persistSegments(segments: List<SleepSegment>) {
        sleepSegmentDao.insertSegments(segments.map { SleepSegmentEntity.fromDomain(it) })
    }

    override fun hasUsagePermission(): Boolean = usageStatsDataSource.hasPermission()

    override suspend fun purgeTelemetryOlderThan(cutoffMillis: Long) {
        sleepTelemetryDao.deleteOlderThan(cutoffMillis)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun groupIntoSessions(segments: List<SleepSegment>): List<List<SleepSegment>> {
        if (segments.isEmpty()) return emptyList()
        val sessions = mutableListOf<MutableList<SleepSegment>>()
        var current  = mutableListOf(segments.first())
        for (i in 1 until segments.size) {
            if (Duration.between(segments[i - 1].endTime, segments[i].startTime).toHours() >= SESSION_GAP_HOURS) {
                sessions.add(current); current = mutableListOf()
            }
            current.add(segments[i])
        }
        sessions.add(current)
        return sessions
    }

    private fun buildSummary(date: String, segments: List<SleepSegment>): DailySleepSummary? {
        val asleep = segments.filter { it.status == SleepStatus.ASLEEP }.sortedBy { it.startTime }
        if (asleep.isEmpty()) return null
        val totalSleepMin = asleep.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
        val sessionStart  = asleep.first().startTime
        val sessionEnd    = asleep.last().endTime
        return DailySleepSummary(
            date              = date,
            totalSleepMinutes = totalSleepMin,
            timeInBedMinutes  = Duration.between(sessionStart, sessionEnd).toMinutes().toInt(),
            awakenings        = (asleep.size - 1).coerceAtLeast(0),
            sleepOnsetMinutes = SleepTimeUtils.minutesSince6PM(sessionStart),
            isEstimated       = true
        )
    }
}
