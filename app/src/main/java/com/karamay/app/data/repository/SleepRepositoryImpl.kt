package com.karamay.app.data.repository

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.SleepSegmentRequest
import com.karamay.app.core.service.TrackingService
import com.karamay.app.core.utils.SleepTimeUtils
import com.karamay.app.data.local.dao.SleepSegmentDao
import com.karamay.app.data.local.dao.SleepTelemetryDao
import com.karamay.app.data.receiver.sleep.SleepSignalBus
import com.karamay.app.data.receiver.sleep.SleepReceiver
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
    private val sleepTelemetryDao: SleepTelemetryDao,
    private val sleepSignalBus: SleepSignalBus,
) : SleepRepository {

    companion object {
        private const val SESSION_RESUME_HOURS = 3L
        private const val PREFS_NAME           = "sleep_tracker_prefs"
        private const val SESSION_GAP_HOURS    = 4L
    }

    private val prefs      = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _isTracking = AtomicBoolean(prefs.getBoolean("is_tracking", false))

    private val activityRecognitionClient = ActivityRecognition.getClient(context)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, SleepReceiver::class.java).apply {
            action = SleepReceiver.ACTION_SLEEP_DATA
        }
        PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    override val isTracking: Boolean get() = _isTracking.get()

    init {
        sleepSignalBus.init(context)
    }

    @SuppressLint("MissingPermission")
    override fun startTracking(): Boolean {
        if (_isTracking.getAndSet(true)) return true

        prefs.edit().putBoolean("is_tracking", true).apply()

        val lastAsleep = sleepSignalBus.lastAsleepTimestamp
        val isResumingSession = lastAsleep != null &&
                Duration.between(lastAsleep, LocalDateTime.now()).toHours() < SESSION_RESUME_HOURS

        if (!isResumingSession) sleepSignalBus.resetSession()
        sleepSignalBus.setTrackingState(true)

        val serviceIntent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START_SLEEP
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        activityRecognitionClient
            .requestSleepSegmentUpdates(pendingIntent, SleepSegmentRequest.getDefaultSleepSegmentRequest())
            .addOnFailureListener {
                // Fix E: when Play Services rejects the request, roll back both the
                // in-memory flag AND the persisted value so BootReceiver doesn't try to
                // restart a session that never actually started.
                _isTracking.set(false)
                prefs.edit().putBoolean("is_tracking", false).apply()
                sleepSignalBus.setTrackingState(false)
            }

        return true
    }

    @SuppressLint("MissingPermission")
    override fun stopTracking() {
        if (!_isTracking.getAndSet(false)) return

        prefs.edit().putBoolean("is_tracking", false).apply()
        sleepSignalBus.setTrackingState(false)

        // Fix D (sleep side): Use plain startService() for the stop action,
        // not startForegroundService() — same correction as ActivityRepositoryImpl.
        context.startService(
            Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_STOP_SLEEP
            }
        )

        activityRecognitionClient.removeSleepSegmentUpdates(pendingIntent)
    }

    override fun observeLiveSignal(): Flow<SleepSignal> = sleepSignalBus.signals

    override fun getSegmentsForDate(date: LocalDate): Flow<List<SleepSegment>> {
        val windowStart = date.minusDays(1).atTime(6, 0).toString()
        val windowEnd   = date.plusDays(1).atTime(6, 0).toString()
        return sleepSegmentDao.getSegmentsBetween(windowStart, windowEnd)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getWeeklySummaries(endDate: LocalDate): Flow<List<DailySleepSummary>> {
        val broadStart = endDate.minusDays(8).atTime(6, 0).toString()
        val broadEnd   = endDate.plusDays(1).atTime(6, 0).toString()
        return sleepSegmentDao.getSegmentsBetween(broadStart, broadEnd)
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
                        timestamp    = Instant.ofEpochMilli(it.timestampMillis).atZone(ZoneId.systemDefault()).toLocalDateTime(),
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

    private fun buildSummaryFromSegments(date: String, segments: List<SleepSegment>): DailySleepSummary {
        val sleepSegs = segments.filter { it.status == SleepStatus.ASLEEP }.sortedBy { it.startTime }
        if (sleepSegs.isEmpty()) return DailySleepSummary(date, 0, 0, 0, 0)

        val totalSleepMinutes = sleepSegs.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
        val sessionStart      = sleepSegs.first().startTime
        val sessionEnd        = sleepSegs.last().endTime
        val timeInBedMinutes  = Duration.between(sessionStart, sessionEnd).toMinutes().toInt()

        var sleepBlocks = 0
        var currentEnd: LocalDateTime? = null
        for (seg in sleepSegs) {
            if (currentEnd == null || Duration.between(currentEnd, seg.startTime).toMinutes() > 5) sleepBlocks++
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
