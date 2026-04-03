package com.karamay.app.data.repository

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.SleepSegmentRequest
import com.karamay.app.core.service.TrackingService
import com.karamay.app.data.local.dao.SleepSegmentDao
import com.karamay.app.data.local.dao.SleepTelemetryDao
import com.karamay.app.data.receiver.LiveSleepSignalBus
import com.karamay.app.data.receiver.SleepReceiver
import com.karamay.app.domain.model.DailySleepSummary
import com.karamay.app.domain.model.SleepSegment
import com.karamay.app.domain.model.SleepSignal
import com.karamay.app.domain.model.SleepStatus
import com.karamay.app.domain.model.SleepTelemetry
import com.karamay.app.domain.repository.SleepRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    private val sleepTelemetryDao: SleepTelemetryDao
) : SleepRepository {

    companion object {
        private const val MIN_ESTIMATE_MINUTES = 60
        // Maximum plausible single sleep session. Guards against a stale
        // lastAsleepTimestamp producing a runaway morning estimate.
        private const val MAX_ESTIMATE_MINUTES = 14 * 60
        // Sessions separated by >= 4 hours are considered distinct sleep periods.
        private const val SESSION_GAP_HOURS = 4L
    }

    init {
        LiveSleepSignalBus.init(context)
    }

    private val activityRecognitionClient = ActivityRecognition.getClient(context)
    private val _isTracking = AtomicBoolean(false)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, SleepReceiver::class.java).apply {
            action = SleepReceiver.ACTION_SLEEP_DATA
        }
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    override val isTracking: Boolean get() = _isTracking.get()

    @SuppressLint("MissingPermission")
    override fun startTracking(): Boolean {
        if (_isTracking.getAndSet(true)) return true

        // Fix S3: Only wipe session state if this is a genuinely new session.
        // If the process was killed mid-session (OOM, system pressure), the
        // persisted lastAsleepTimestamp is still valid. Resetting it would make
        // the morning estimate permanently unavailable after any background kill.
        // A session is considered ongoing if its last known onset was < 14 hours ago.
        val lastAsleep = LiveSleepSignalBus.lastAsleepTimestamp
        val isResumingSession = lastAsleep != null &&
                Duration.between(lastAsleep, LocalDateTime.now()).toHours() < MAX_ESTIMATE_MINUTES / 60

        if (!isResumingSession) {
            LiveSleepSignalBus.resetSession()
        }
        LiveSleepSignalBus.setTrackingState(true)

        val serviceIntent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START_SLEEP
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        activityRecognitionClient
            .requestSleepSegmentUpdates(pendingIntent, SleepSegmentRequest.getDefaultSleepSegmentRequest())
            .addOnFailureListener { _isTracking.set(false) }

        return true
    }

    @SuppressLint("MissingPermission")
    override fun stopTracking() {
        if (!_isTracking.getAndSet(false)) return
        LiveSleepSignalBus.setTrackingState(false)

        val serviceIntent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP_SLEEP
        }
        ContextCompat.startForegroundService(context, serviceIntent)
        activityRecognitionClient.removeSleepSegmentUpdates(pendingIntent)
    }

    override fun observeLiveSignal(): Flow<SleepSignal> = LiveSleepSignalBus.signals

    // Fix S5 + irregular schedule: Use a 48h window (6 AM previous day → 6 AM next day)
    // instead of noon-to-noon. Session attribution to the correct date is handled in
    // the use case layer by wake-time anchoring, not by the query boundary.
    // This correctly captures night-shift workers (sleep starts in the afternoon),
    // extreme owls (wake past noon), and rotating schedules.
    override fun getSegmentsForDate(date: LocalDate): Flow<List<SleepSegment>> {
        val windowStart = date.minusDays(1).atTime(6, 0).toString()
        val windowEnd   = date.plusDays(1).atTime(6, 0).toString()
        return sleepSegmentDao.getSegmentsBetween(windowStart, windowEnd)
            .map { entities -> entities.map { it.toDomain() } }
    }

    // Fix S5: getDailySummary() was still using noonWindow() while getSegmentsForDate()
    // had already been updated to 48h. This divergence meant callers of getDailySummary()
    // got the old broken window. Now both paths use identical 48h boundaries.
    override fun getDailySummary(date: LocalDate): Flow<DailySleepSummary?> {
        val windowStart = date.minusDays(1).atTime(6, 0).toString()
        val windowEnd   = date.plusDays(1).atTime(6, 0).toString()
        return sleepSegmentDao.getSegmentsBetween(windowStart, windowEnd)
            .map { entities ->
                val segments = entities.map { it.toDomain() }
                if (segments.any { it.status == SleepStatus.ASLEEP }) {
                    buildSummaryFromSegments(date.toString(), segments)
                } else {
                    buildMorningEstimate(date)
                }
            }
    }

    // Fix irregular schedule: Replaced noon-to-noon windowing and start-time filtering
    // with session-boundary detection (4h gap) + wake-time attribution.
    // A session is attributed to the date its wake time (endTime) falls on.
    override fun getWeeklySummaries(endDate: LocalDate): Flow<List<DailySleepSummary>> {
        // Fetch a broad window covering 8 days worth of 48h-per-day queries.
        val broadStart = endDate.minusDays(8).atTime(6, 0).toString()
        val broadEnd   = endDate.plusDays(1).atTime(6, 0).toString()

        return sleepSegmentDao.getSegmentsBetween(broadStart, broadEnd)
            .map { entities ->
                val allSegments = entities.map { it.toDomain() }.sortedBy { it.startTime }

                // Split all segments into discrete sleep sessions on 4h gaps.
                val sessions = mutableListOf<List<SleepSegment>>()
                var current  = mutableListOf<SleepSegment>()

                for (seg in allSegments) {
                    if (current.isNotEmpty()) {
                        val gapHours = Duration.between(
                            current.last().endTime, seg.startTime
                        ).toHours()
                        if (gapHours >= SESSION_GAP_HOURS) {
                            sessions.add(current.toList())
                            current = mutableListOf()
                        }
                    }
                    current.add(seg)
                }
                if (current.isNotEmpty()) sessions.add(current.toList())

                // Attribute each session to a date by its wake time (end of session),
                // then build a summary for each of the 7 target dates.
                (0L..6L).mapNotNull { daysBack ->
                    val date    = endDate.minusDays(daysBack)
                    val session = sessions.firstOrNull { s ->
                        s.last().endTime.toLocalDate() == date
                    } ?: return@mapNotNull null

                    if (session.any { it.status == SleepStatus.ASLEEP }) {
                        buildSummaryFromSegments(date.toString(), session)
                    } else null
                }
            }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun buildSummaryFromSegments(
        date: String,
        segments: List<SleepSegment>
    ): DailySleepSummary {
        val sleepSegs = segments
            .filter { it.status == SleepStatus.ASLEEP }
            .sortedBy { it.startTime }

        if (sleepSegs.isEmpty()) return DailySleepSummary(date, 0, 0, 0, 0)

        val totalSleepMinutes = sleepSegs
            .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
            .toInt()

        val sessionStart     = sleepSegs.first().startTime
        val sessionEnd       = sleepSegs.last().endTime
        val timeInBedMinutes = Duration.between(sessionStart, sessionEnd).toMinutes().toInt()

        var sleepBlocks = 0
        var currentEnd  : LocalDateTime? = null
        for (seg in sleepSegs) {
            if (currentEnd == null || Duration.between(currentEnd, seg.startTime).toMinutes() > 5) {
                sleepBlocks++
            }
            currentEnd = seg.endTime
        }
        val awakenings = (sleepBlocks - 1).coerceAtLeast(0)

        return DailySleepSummary(
            date              = date,
            totalSleepMinutes = totalSleepMinutes,
            timeInBedMinutes  = timeInBedMinutes,
            awakenings        = awakenings,
            sleepOnsetMinutes = minutesSince6PM(sessionStart),
            isEstimated       = false
        )
    }

    private fun buildMorningEstimate(date: LocalDate): DailySleepSummary? {
        if (date != LocalDate.now()) return null
        val lastAsleep = LiveSleepSignalBus.lastAsleepTimestamp ?: return null
        val signal     = LiveSleepSignalBus.signals.value

        // Fix S1: Use an effective wake time that accounts for Doze-delayed delivery.
        // signal.timestamp is when Google last polled the classifier — it can be
        // hours stale. If the signal is old AND the user is clearly awake (they're
        // using the app), treat now() as the wake proxy rather than the stale timestamp.
        val signalAgeMinutes = Duration.between(signal.timestamp, LocalDateTime.now()).toMinutes()
        val effectiveWakeTime = when {
            signal.status == SleepStatus.AWAKE -> signal.timestamp
            signalAgeMinutes > 30              -> LocalDateTime.now()
            else                               -> return null  // recent signal still says ASLEEP
        }

        val estimatedMinutes = Duration.between(lastAsleep, effectiveWakeTime).toMinutes().toInt()
        if (estimatedMinutes < MIN_ESTIMATE_MINUTES) return null

        // Fix S1: Sanity cap — no single sleep session should exceed 14 hours.
        // Prevents runaway estimates if lastAsleepTimestamp is stale from a previous session.
        if (estimatedMinutes > MAX_ESTIMATE_MINUTES) return null

        return DailySleepSummary(
            date              = date.toString(),
            totalSleepMinutes = estimatedMinutes,
            timeInBedMinutes  = estimatedMinutes,
            awakenings        = 0,
            sleepOnsetMinutes = minutesSince6PM(lastAsleep),
            isEstimated       = true
        )
    }

    // Fix A6: Renamed from minutesFromMidnight() and switched to a 6 PM anchor.
    // minutesSince6PM() is a more meaningful sleep onset metric across all schedules:
    //   - Normal sleeper at 11 PM  → 300  (5h after 6 PM)
    //   - Night owl at 2 AM        → 480  (8h after 6 PM)
    //   - Night-shift at 8 AM      → 840  (14h after previous 6 PM)
    // This keeps onset values on a single consistent scale regardless of schedule,
    // unlike minutesSinceMidnight() which produces negative-feeling large numbers
    // for daytime sleepers and requires UI special-casing.
    private fun minutesSince6PM(dateTime: LocalDateTime): Int {
        val anchor = if (dateTime.hour >= 18) {
            dateTime.toLocalDate().atTime(18, 0)
        } else {
            dateTime.toLocalDate().minusDays(1).atTime(18, 0)
        }
        return Duration.between(anchor, dateTime).toMinutes().toInt()
    }

    override fun getTelemetryBetween(
        start: LocalDateTime,
        end: LocalDateTime
    ): Flow<List<SleepTelemetry>> {
        val startMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis   = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return sleepTelemetryDao.getTelemetryBetween(startMillis, endMillis)
            .map { entities ->
                entities.map {
                    SleepTelemetry(
                        timestamp    = Instant.ofEpochMilli(it.timestampMillis)
                            .atZone(ZoneId.systemDefault()).toLocalDateTime(),
                        confidence   = it.confidence,
                        ambientLight = it.ambientLight,
                        deviceMotion = it.deviceMotion
                    )
                }
            }
    }

    override suspend fun purgeTelemetryOlderThan(cutoffMillis: Long) {
        sleepTelemetryDao.deleteOlderThan(cutoffMillis)
    }
}