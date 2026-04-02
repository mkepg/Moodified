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
import com.karamay.app.data.receiver.LiveSleepSignalBus
import com.karamay.app.data.receiver.SleepReceiver
import com.karamay.app.domain.model.DailySleepSummary
import com.karamay.app.domain.model.SleepSegment
import com.karamay.app.domain.model.SleepSignal
import com.karamay.app.domain.model.SleepStatus
import com.karamay.app.domain.repository.SleepRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sleepSegmentDao: SleepSegmentDao
) : SleepRepository {

    companion object {
        private const val WINDOW_BOUNDARY_HOUR = 12
        private const val MIN_ESTIMATE_MINUTES = 60
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
        LiveSleepSignalBus.resetSession()
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

    override fun getSegmentsForDate(date: LocalDate): Flow<List<SleepSegment>> {
        val (windowStart, windowEnd) = noonWindow(date)
        return sleepSegmentDao.getSegmentsBetween(windowStart, windowEnd)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getDailySummary(date: LocalDate): Flow<DailySleepSummary?> {
        val (windowStart, windowEnd) = noonWindow(date)
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

    override fun getWeeklySummaries(endDate: LocalDate): Flow<List<DailySleepSummary>> {
        val broadStart = endDate.minusDays(8).atTime(WINDOW_BOUNDARY_HOUR, 0).toString()
        val broadEnd   = endDate.atTime(WINDOW_BOUNDARY_HOUR, 0).toString()

        return sleepSegmentDao.getSegmentsBetween(broadStart, broadEnd)
            .map { entities ->
                val allSegments = entities.map { it.toDomain() }

                (0L..6L).mapNotNull { daysBack ->
                    val date       = endDate.minusDays(daysBack)
                    val dayStart   = date.minusDays(1).atTime(WINDOW_BOUNDARY_HOUR, 0)
                    val dayEnd     = date.atTime(WINDOW_BOUNDARY_HOUR, 0)

                    val daySegs = allSegments.filter { seg ->
                        !seg.startTime.isBefore(dayStart) && seg.startTime.isBefore(dayEnd)
                    }

                    if (daySegs.any { it.status == SleepStatus.ASLEEP }) {
                        buildSummaryFromSegments(date.toString(), daySegs)
                    } else {
                        null
                    }
                }
            }
    }

    private fun buildSummaryFromSegments(
        date: String,
        segments: List<SleepSegment>
    ): DailySleepSummary {
        val sleepSegs = segments.filter { it.status == SleepStatus.ASLEEP }.sortedBy { it.startTime }
        if (sleepSegs.isEmpty()) return DailySleepSummary(date, 0, 0, 0, 0)

        val totalSleepMinutes = sleepSegs
            .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
            .toInt()

        val sessionStart = sleepSegs.first().startTime
        val sessionEnd = sleepSegs.last().endTime
        val timeInBedMinutes = Duration.between(sessionStart, sessionEnd).toMinutes().toInt()

        // BUG FIX: Prevent fragmentation inflation caused by API broadcast glitches or service restarts
        // We calculate awakenings strictly by counting disjoint sleep blocks (> 5 min gap)
        var sleepBlocks = 0
        var currentEnd: LocalDateTime? = null

        for (seg in sleepSegs) {
            if (currentEnd == null || Duration.between(currentEnd, seg.startTime).toMinutes() > 5) {
                sleepBlocks++
            }
            currentEnd = seg.endTime
        }
        val awakenings = (sleepBlocks - 1).coerceAtLeast(0)

        val sleepOnsetMinutes = minutesFromMidnight(sessionStart)

        return DailySleepSummary(
            date               = date,
            totalSleepMinutes  = totalSleepMinutes,
            timeInBedMinutes   = timeInBedMinutes,
            awakenings         = awakenings,
            sleepOnsetMinutes  = sleepOnsetMinutes,
            isEstimated        = false
        )
    }

    private fun buildMorningEstimate(date: LocalDate): DailySleepSummary? {
        if (date != LocalDate.now()) return null

        val signal     = LiveSleepSignalBus.signals.value
        val lastAsleep = LiveSleepSignalBus.lastAsleepTimestamp ?: return null

        if (signal.status != SleepStatus.AWAKE) return null

        val estimatedMinutes = Duration.between(lastAsleep, signal.timestamp).toMinutes().toInt()
        if (estimatedMinutes < MIN_ESTIMATE_MINUTES) return null

        return DailySleepSummary(
            date               = date.toString(),
            totalSleepMinutes  = estimatedMinutes,
            timeInBedMinutes   = estimatedMinutes,
            awakenings         = 0,
            sleepOnsetMinutes  = minutesFromMidnight(lastAsleep),
            isEstimated        = true
        )
    }

    private fun noonWindow(date: LocalDate): Pair<String, String> {
        val start = date.minusDays(1).atTime(WINDOW_BOUNDARY_HOUR, 0).toString()
        val end   = date.atTime(WINDOW_BOUNDARY_HOUR, 0).toString()
        return start to end
    }

    private fun minutesFromMidnight(dateTime: LocalDateTime): Int {
        val midnight = dateTime.toLocalDate().atStartOfDay()
        val mins     = Duration.between(midnight, dateTime).toMinutes().toInt()
        return if (mins < 0) mins + 1440 else mins
    }
}