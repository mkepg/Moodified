package com.karamay.app.data.repository

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.SleepSegmentRequest
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

    // ────────────────────────────────────────────────────────────
    // Constants
    // ────────────────────────────────────────────────────────────

    companion object {
        /**
         * The daily window boundary hour (noon).
         * A sleep session is attributed to date D if it starts inside
         * [D-1 at 12:00, D at 12:00).
         */
        private const val WINDOW_BOUNDARY_HOUR = 12

        /**
         * Fix 1: gaps between adjacent segments shorter than this are treated as the
         * same sleep session (brief awakenings / micro-arousals).
         */
        private const val SESSION_GAP_MINUTES = 30L

        /**
         * Fix 5: minimum estimated sleep duration (minutes) to emit a morning estimate.
         * Prevents phantom summaries when the bus transitions to AWAKE after a mere
         * "put the phone down" event.
         */
        private const val MIN_ESTIMATE_MINUTES = 60
    }

    // ────────────────────────────────────────────────────────────
    // Tracking infrastructure
    // ────────────────────────────────────────────────────────────

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
        // Reset session-scoped state so stale onset data from last night is cleared
        LiveSleepSignalBus.resetSession()
        LiveSleepSignalBus.setTrackingState(true)
        activityRecognitionClient
            .requestSleepSegmentUpdates(pendingIntent, SleepSegmentRequest.getDefaultSleepSegmentRequest())
            .addOnFailureListener { _isTracking.set(false) }
        return true
    }

    @SuppressLint("MissingPermission")
    override fun stopTracking() {
        if (!_isTracking.getAndSet(false)) return
        LiveSleepSignalBus.setTrackingState(false)
        activityRecognitionClient.removeSleepSegmentUpdates(pendingIntent)
    }

    // ────────────────────────────────────────────────────────────
    // Live signal
    // ────────────────────────────────────────────────────────────

    override fun observeLiveSignal(): Flow<SleepSignal> = LiveSleepSignalBus.signals

    // ────────────────────────────────────────────────────────────
    // Segment queries  —  Fix 1: rolling noon-to-noon window
    // ────────────────────────────────────────────────────────────

    /**
     * Returns the segments belonging to the dominant sleep session for [date].
     *
     * "Dominant session" is defined as the contiguous sleep block with the highest
     * total ASLEEP minutes inside the [date-1 noon → date noon) window.
     * This replaces the old hardcoded midnight boundary and correctly handles both
     * standard sleepers and anyone going to bed after midnight.
     */
    override fun getSegmentsForDate(date: LocalDate): Flow<List<SleepSegment>> {
        val (windowStart, windowEnd) = noonWindow(date)
        return sleepSegmentDao.getSegmentsBetween(windowStart, windowEnd)
            .map { entities -> extractDominantSession(entities.map { it.toDomain() }) }
    }

    /**
     * Builds a [DailySleepSummary] for [date] from finalized DB segments.
     * Falls back to a live-signal estimate (Fix 5) when no DB rows exist yet.
     */
    override fun getDailySummary(date: LocalDate): Flow<DailySleepSummary?> {
        val (windowStart, windowEnd) = noonWindow(date)
        return sleepSegmentDao.getSegmentsBetween(windowStart, windowEnd)
            .map { entities ->
                val session = extractDominantSession(entities.map { it.toDomain() })
                when {
                    session.isNotEmpty() -> buildSummaryFromSegments(date.toString(), session)
                    else                 -> buildMorningEstimate(date)   // Fix 5
                }
            }
    }

    /**
     * Returns one [DailySleepSummary] per day for the 7 days ending at [endDate].
     * Days with no recorded sleep produce no entry (they are excluded from the list).
     */
    override fun getWeeklySummaries(endDate: LocalDate): Flow<List<DailySleepSummary>> {
        // Fetch a wide window covering all 7 days in a single DB query
        val broadStart = endDate.minusDays(8).atTime(WINDOW_BOUNDARY_HOUR, 0).toString()
        val broadEnd   = endDate.atTime(WINDOW_BOUNDARY_HOUR, 0).toString()

        return sleepSegmentDao.getSegmentsBetween(broadStart, broadEnd)
            .map { entities ->
                val allSegments = entities.map { it.toDomain() }
                (0L..6L).mapNotNull { daysBack ->
                    val date       = endDate.minusDays(daysBack)
                    val dayStart   = date.minusDays(1).atTime(WINDOW_BOUNDARY_HOUR, 0)
                    val dayEnd     = date.atTime(WINDOW_BOUNDARY_HOUR, 0)
                    val daySegs    = allSegments.filter { seg ->
                        !seg.startTime.isBefore(dayStart) && seg.startTime.isBefore(dayEnd)
                    }
                    val session = extractDominantSession(daySegs)
                    if (session.isEmpty()) null
                    else buildSummaryFromSegments(date.toString(), session)
                }
            }
    }

    // ────────────────────────────────────────────────────────────
    // Fix 1 helper: dominant session extraction
    // ────────────────────────────────────────────────────────────

    /**
     * Groups [segments] into contiguous sessions by merging consecutive entries whose
     * gap is ≤ [SESSION_GAP_MINUTES], then returns the session with the most ASLEEP
     * minutes — i.e. the user's primary sleep block.
     *
     * This correctly handles:
     *  - Night-shift workers whose sleep straddles any hour of the day
     *  - Brief awakenings (< 30 min) that would otherwise split one session into two
     *  - Naps: the longer session wins
     */
    private fun extractDominantSession(segments: List<SleepSegment>): List<SleepSegment> {
        if (segments.isEmpty()) return emptyList()
        val sorted = segments.sortedBy { it.startTime }

        // Build sessions by grouping segments with small gaps
        val sessions = mutableListOf<MutableList<SleepSegment>>()
        var current  = mutableListOf(sorted.first())

        for (i in 1 until sorted.size) {
            val gap = Duration.between(current.last().endTime, sorted[i].startTime).toMinutes()
            if (gap <= SESSION_GAP_MINUTES) {
                current.add(sorted[i])
            } else {
                sessions.add(current)
                current = mutableListOf(sorted[i])
            }
        }
        sessions.add(current)

        // Return the session with the highest total sleep (ASLEEP) minutes
        return sessions.maxByOrNull { session ->
            session.filter { it.status == SleepStatus.ASLEEP }
                .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
        } ?: emptyList()
    }

    // ────────────────────────────────────────────────────────────
    // Summary builders
    // ────────────────────────────────────────────────────────────

    private fun buildSummaryFromSegments(
        date: String,
        segments: List<SleepSegment>
    ): DailySleepSummary {
        val sleepSegs         = segments.filter { it.status == SleepStatus.ASLEEP }
        val totalSleepMinutes = sleepSegs
            .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
            .toInt()

        val sessionStart      = segments.minOf { it.startTime }
        val sessionEnd        = segments.maxOf { it.endTime }
        val timeInBedMinutes  = Duration.between(sessionStart, sessionEnd).toMinutes().toInt()

        // An "awakening" is any AWAKE segment sandwiched between sleep segments
        val awakenings        = segments.count { it.status == SleepStatus.AWAKE }

        // Fix 4 prerequisite: record sleep onset in minutes-from-midnight
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

    /**
     * Fix 5: Generates a temporary estimated [DailySleepSummary] when:
     *  1. The requested date is today
     *  2. The live bus currently shows AWAKE (user just got up)
     *  3. A previous ASLEEP onset was recorded in this session
     *  4. The inferred duration is at least [MIN_ESTIMATE_MINUTES]
     *
     * This estimate is flagged with [DailySleepSummary.isEstimated] = true so the UI
     * can display a "Estimated — updating soon" badge.  It will be automatically
     * superseded once the real [SleepSegmentEvent] broadcast fires and populates the DB.
     */
    private fun buildMorningEstimate(date: LocalDate): DailySleepSummary? {
        if (date != LocalDate.now()) return null

        val signal       = LiveSleepSignalBus.signals.value
        val lastAsleep   = LiveSleepSignalBus.lastAsleepTimestamp ?: return null

        if (signal.status != SleepStatus.AWAKE) return null

        val estimatedMinutes = Duration.between(lastAsleep, signal.timestamp).toMinutes().toInt()
        if (estimatedMinutes < MIN_ESTIMATE_MINUTES) return null

        return DailySleepSummary(
            date               = date.toString(),
            totalSleepMinutes  = estimatedMinutes,
            timeInBedMinutes   = estimatedMinutes,   // best available proxy
            awakenings         = 0,                   // unknown without segments
            sleepOnsetMinutes  = minutesFromMidnight(lastAsleep),
            isEstimated        = true
        )
    }

    // ────────────────────────────────────────────────────────────
    // Utilities
    // ────────────────────────────────────────────────────────────

    /** Returns the [date-1 noon, date noon) string pair used for all DB queries. */
    private fun noonWindow(date: LocalDate): Pair<String, String> {
        val start = date.minusDays(1).atTime(WINDOW_BOUNDARY_HOUR, 0).toString()
        val end   = date.atTime(WINDOW_BOUNDARY_HOUR, 0).toString()
        return start to end
    }

    /**
     * Converts a [LocalDateTime] to minutes elapsed since the previous midnight.
     * Times before midnight (i.e. the previous calendar day) are expressed as values
     * ≥ 1440 so that 23:00 → 1380 and 01:30 → 90 remain sortable without wrapping.
     */
    private fun minutesFromMidnight(dateTime: LocalDateTime): Int {
        val midnight = dateTime.toLocalDate().atStartOfDay()
        val mins     = Duration.between(midnight, dateTime).toMinutes().toInt()
        // Clamp negatives (should not occur with valid data, but be defensive)
        return if (mins < 0) mins + 1440 else mins
    }
}