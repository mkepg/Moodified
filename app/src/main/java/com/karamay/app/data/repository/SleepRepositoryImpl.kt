package com.karamay.app.data.repository

import android.content.Context
import android.os.PowerManager
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
    private val coordinator:               TrackingCoordinator,
) : SleepRepository {

    companion object {
        private const val TAG                     = "SleepRepo"
        private const val INFERENCE_POLL_INTERVAL = 5 * 60_000L
        private const val SESSION_GAP_HOURS       = 4L
        private const val MIN_PERSIST_MINUTES     = 60L
        private const val MORNING_CUTOFF_HOUR     = 18
        // Minimum app age before inference is allowed to run. Prevents fabricated
        // sleep summaries sourced from pre-install UsageStats history on day one.
        private const val MIN_INSTALL_AGE_MS      = 24L * 60 * 60_000L
    }

    private val scope      = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()

    @Volatile private var _isProcessActive: Boolean = false

    override val isTracking: Boolean get() = preferencesDataSource.isTracking

    private val _signals = MutableStateFlow(buildInitialSignal(context, preferencesDataSource))
    override fun observeLiveSignal(): Flow<SleepSignal> = _signals.asStateFlow()

    private val poller = PollingJob(
        scope      = scope,
        mutex      = stateMutex,
        intervalMs = INFERENCE_POLL_INTERVAL,
        tag        = "$TAG/inference",
        isActive   = { _isProcessActive }
    ) { runInferenceAndPersist() }

    init {
        // Record the install timestamp exactly once. This is a no-op on every
        // subsequent launch after the first.
        preferencesDataSource.ensureInstallTimeRecorded()
    }

    override fun startTracking(): Boolean {
        if (_isProcessActive) { Log.d(TAG, "startTracking: already running."); return true }
        if (!usageStatsDataSource.hasPermission()) {
            Log.w(TAG, "startTracking: UsageStats permission not granted.")
            return false
        }
        _isProcessActive = true
        preferencesDataSource.isTracking     = true
        preferencesDataSource.hasActiveSession = true
        coordinator.startSleep()

        val powerManager  = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn    = powerManager.isInteractive
        val initialStatus = if (isScreenOn) SleepStatus.AWAKE else SleepStatus.UNKNOWN
        _signals.update {
            it.copy(
                isTracking       = true,
                hasActiveSession = true,
                status           = initialStatus,
                confidence       = if (isScreenOn) 100 else 0,
                deviceMotion     = if (isScreenOn) 1 else 0,
                timestamp        = LocalDateTime.now()
            )
        }
        Log.d(TAG, "startTracking: launching inference poll loop.")
        poller.start()
        return true
    }

    override fun stopTracking() {
        if (!_isProcessActive) return
        _isProcessActive                   = false
        preferencesDataSource.isTracking   = false
        coordinator.stopSleep()
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
        if (!_isProcessActive) return
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

    private suspend fun runInferenceAndPersist() {
        val now = LocalDateTime.now()
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

        // Guard against fabricated results on fresh install. UsageStatsManager
        // surfaces historical events from before the app was installed, so
        // querying it on day one produces enormous screen-off gaps that look like
        // legitimate sleep windows. We require the app to be at least 24 hours
        // old before attempting any inference.
        val appAgeMs = System.currentTimeMillis() - preferencesDataSource.installTimeMillis
        if (appAgeMs < MIN_INSTALL_AGE_MS) {
            Log.d(TAG, "Skipping inference: app installed ${appAgeMs / 3_600_000}h ago " +
                    "(need ≥${MIN_INSTALL_AGE_MS / 3_600_000}h). Will retry tomorrow.")
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

    private fun buildInitialSignal(context: Context, prefs: SleepPreferencesDataSource): SleepSignal {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn   = powerManager.isInteractive
        return SleepSignal(
            isTracking       = prefs.isTracking,
            hasActiveSession = prefs.hasActiveSession,
            status           = if (isScreenOn) SleepStatus.AWAKE else SleepStatus.UNKNOWN,
            confidence       = if (isScreenOn) 100 else 0,
            deviceMotion     = if (isScreenOn) 1 else 0,
            timestamp        = LocalDateTime.now()
        )
    }
}