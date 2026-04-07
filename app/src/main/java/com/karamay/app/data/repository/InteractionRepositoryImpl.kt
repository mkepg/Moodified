package com.karamay.app.data.repository

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.content.Intent
import com.karamay.app.core.service.TrackingService
import com.karamay.app.data.local.dao.interaction.InteractionDailySummaryDao
import com.karamay.app.data.local.dao.interaction.InteractionSessionDao
import com.karamay.app.data.local.datasource.InteractionPreferencesDataSource
import com.karamay.app.data.local.entity.interaction.InteractionDailySummaryEntity
import com.karamay.app.data.local.entity.interaction.InteractionSessionEntity
import com.karamay.app.domain.model.interaction.InteractionDailySummary
import com.karamay.app.domain.model.interaction.InteractionEventType
import com.karamay.app.domain.model.interaction.InteractionSession
import com.karamay.app.domain.model.interaction.InteractionSignal
import com.karamay.app.domain.repository.InteractionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InteractionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesDataSource: InteractionPreferencesDataSource,
    private val sessionDao: InteractionSessionDao,
    private val dailySummaryDao: InteractionDailySummaryDao
) : InteractionRepository {

    companion object {
        private const val TAG                    = "InteractionRepo"
        private const val TICKER_INTERVAL_MS     = 10_000L
        private const val MAX_SESSION_RESTORE_MS = 12 * 60 * 60 * 1000L
        private const val MIN_SESSION_DURATION_MS = 3_000L

        /** Late-night window: 00:00 (midnight) – 05:00 local time. */
        private val LATE_NIGHT_START: LocalTime = LocalTime.MIDNIGHT
        private val LATE_NIGHT_END:   LocalTime = LocalTime.of(5, 0)

        /**
         * Returns the number of milliseconds in the interval [startMs, endMs)
         * that fall inside the late-night window (00:00–05:00) on any day.
         *
         * Handles intervals that cross midnight or span multiple days.
         * In normal usage (intervals < MAX_SESSION_RESTORE_MS = 12 h) this
         * iterates at most over two calendar days.
         */
        private fun lateNightOverlapMs(startMs: Long, endMs: Long): Long {
            if (endMs <= startMs) return 0L
            val zoneId   = ZoneId.systemDefault()
            val startZdt = Instant.ofEpochMilli(startMs).atZone(zoneId)
            val endZdt   = Instant.ofEpochMilli(endMs).atZone(zoneId)

            var totalOverlap = 0L
            var day          = startZdt.toLocalDate()
            val endDate      = endZdt.toLocalDate()

            while (!day.isAfter(endDate)) {
                val windowStart = day.atTime(LATE_NIGHT_START).atZone(zoneId).toInstant().toEpochMilli()
                val windowEnd   = day.atTime(LATE_NIGHT_END).atZone(zoneId).toInstant().toEpochMilli()
                val overlapStart = maxOf(startMs, windowStart)
                val overlapEnd   = minOf(endMs, windowEnd)
                if (overlapEnd > overlapStart) totalOverlap += overlapEnd - overlapStart
                day = day.plusDays(1)
            }
            return totalOverlap
        }
    }

    private val scope      = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateMutex = Mutex()
    private var tickerJob: Job? = null
    // sessionUnlockCount removed — unlock frequency is no longer tracked

    private val _isTracking = AtomicBoolean(preferencesDataSource.isTracking)
    override val isTracking: Boolean get() = preferencesDataSource.isTracking

    private val _signal = MutableStateFlow(buildInitialSignal())
    override fun observeLiveSignal(): Flow<InteractionSignal> = _signal.asStateFlow()

    private fun buildInitialSignal(): InteractionSignal = InteractionSignal(
        isTracking                 = preferencesDataSource.isTracking,
        isScreenOn                 = preferencesDataSource.isScreenOn,
        totalScreenTimeTodayMs     = preferencesDataSource.totalScreenTimeTodayMs,
        lateNightScreenTimeTodayMs = preferencesDataSource.lateNightScreenTimeTodayMs
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun startTracking(): Boolean {
        if (_isTracking.getAndSet(true)) return true
        val displayManager      = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display             = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val isScreenOnInitially = display?.state == Display.STATE_ON

        preferencesDataSource.isTracking = true
        preferencesDataSource.isScreenOn = isScreenOnInitially

        scope.launch {
            stateMutex.withLock {
                val nowMs = System.currentTimeMillis()
                checkAndRolloverDay(nowMs)

                val savedAnchor = preferencesDataSource.sessionStartMillis
                if (isScreenOnInitially) {
                    if (savedAnchor != -1L) {
                        // Screen was on when we last stopped — restore the missed gap.
                        val gap = nowMs - preferencesDataSource.lastCalcMillis
                        if (preferencesDataSource.dayKey == LocalDate.now().toString() &&
                            gap in 1L..MAX_SESSION_RESTORE_MS
                        ) {
                            preferencesDataSource.totalScreenTimeTodayMs     += gap
                            preferencesDataSource.lateNightScreenTimeTodayMs +=
                                lateNightOverlapMs(preferencesDataSource.lastCalcMillis, nowMs)
                            Log.d(TAG, "Restored missing screen time gap of ${gap}ms")
                        }
                    } else {
                        preferencesDataSource.sessionStartMillis = nowMs
                    }
                    preferencesDataSource.lastCalcMillis = nowMs
                    startInteractionTicker()
                } else {
                    preferencesDataSource.sessionStartMillis = -1L
                    preferencesDataSource.lastCalcMillis     = -1L
                }
                publishSnapshot()
            }
        }
        return true
    }

    override fun stopTracking() {
        if (!_isTracking.getAndSet(false)) return
        preferencesDataSource.isTracking = false
        tickerJob?.cancel()
        context.startService(
            Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_STOP_INTERACTION
            }
        )
        scope.launch {
            stateMutex.withLock {
                val nowMs = System.currentTimeMillis()
                flushCurrentState(nowMs)
                if (preferencesDataSource.isScreenOn) {
                    finalizeSession(nowMs)
                }
                preferencesDataSource.sessionStartMillis = -1L
                preferencesDataSource.lastCalcMillis     = -1L
                val currentDayKey = preferencesDataSource.dayKey.ifEmpty { LocalDate.now().toString() }
                persistDailySummary(currentDayKey)
                publishSnapshot()
            }
        }
    }

    override fun resetSession() {
        val wasTracking = _isTracking.getAndSet(false)
        if (wasTracking) {
            preferencesDataSource.isTracking = false
            tickerJob?.cancel()
            context.startService(
                Intent(context, TrackingService::class.java).apply {
                    action = TrackingService.ACTION_STOP_INTERACTION
                }
            )
        }
        scope.launch {
            stateMutex.withLock {
                preferencesDataSource.resetSession()
                val currentDayKey = LocalDate.now().toString()
                persistDailySummary(currentDayKey)
                publishSnapshot()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event handling
    // ─────────────────────────────────────────────────────────────────────────

    override fun logSystemEvent(eventType: InteractionEventType) {
        if (!_isTracking.get()) return
        scope.launch {
            stateMutex.withLock {
                val nowMs = System.currentTimeMillis()
                checkAndRolloverDay(nowMs)

                when (eventType) {
                    InteractionEventType.SCREEN_ON -> {
                        if (!preferencesDataSource.isScreenOn) {
                            preferencesDataSource.isScreenOn         = true
                            preferencesDataSource.sessionStartMillis = nowMs
                            preferencesDataSource.lastCalcMillis     = nowMs
                            // sessionUnlockCount removed
                            startInteractionTicker()
                        }
                    }

                    InteractionEventType.UNLOCKED -> {
                        // Unlock frequency is no longer tracked as a metric.
                        // However, if the screen wasn't already marked on (e.g., the SCREEN_ON
                        // broadcast was missed), open a session so screen time is still captured.
                        if (!preferencesDataSource.isScreenOn) {
                            preferencesDataSource.isScreenOn         = true
                            preferencesDataSource.sessionStartMillis = nowMs
                            preferencesDataSource.lastCalcMillis     = nowMs
                            startInteractionTicker()
                        }
                    }

                    InteractionEventType.SCREEN_OFF -> {
                        if (preferencesDataSource.isScreenOn) {
                            flushCurrentState(nowMs)
                            finalizeSession(nowMs)
                            preferencesDataSource.isScreenOn         = false
                            preferencesDataSource.sessionStartMillis = -1L
                            preferencesDataSource.lastCalcMillis     = -1L
                            tickerJob?.cancel()
                        }
                    }
                }
                publishSnapshot(eventType)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun finalizeSession(nowMs: Long) {
        val startMs = preferencesDataSource.sessionStartMillis
        if (startMs <= 0L) return
        val sessionDuration = nowMs - startMs
        if (sessionDuration >= MIN_SESSION_DURATION_MS) {
            val session = InteractionSession(
                startTime       = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).toLocalDateTime(),
                endTime         = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDateTime(),
                durationMinutes = (sessionDuration / 60_000L).toInt().coerceAtLeast(1)
                // unlockCount removed
            )
            sessionDao.insertSession(InteractionSessionEntity.fromDomain(session))
            Log.d(TAG, "Session finalized: ${sessionDuration}ms saved to DB")
        } else {
            Log.d(TAG, "Session too short (${sessionDuration}ms) — discarded")
        }
    }

    private suspend fun persistDailySummary(dateKey: String) {
        val todayKey  = LocalDate.now().toString()
        val isPartial = dateKey == todayKey
        val summary = InteractionDailySummary(
            date                   = dateKey,
            totalScreenTimeMinutes = (preferencesDataSource.totalScreenTimeTodayMs / 60_000L).toInt(),
            lateNightUsageMinutes  = (preferencesDataSource.lateNightScreenTimeTodayMs / 60_000L).toInt(),
            isPartialDay           = isPartial
        )
        dailySummaryDao.upsert(InteractionDailySummaryEntity.fromDomain(summary))
        Log.d(TAG, "Daily summary saved for $dateKey (partial=$isPartial, lateNight=${summary.lateNightUsageMinutes}m)")
    }

    private fun startInteractionTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && preferencesDataSource.isScreenOn) {
                delay(TICKER_INTERVAL_MS)
                stateMutex.withLock {
                    val nowMs = System.currentTimeMillis()
                    checkAndRolloverDay(nowMs)
                    flushCurrentState(nowMs)
                    publishSnapshot()
                }
            }
        }
    }

    /**
     * Detects a calendar-day change and migrates accumulated state to the new day.
     * Handles the case where the screen was on across midnight by splitting the
     * elapsed time at the midnight boundary.
     */
    private suspend fun checkAndRolloverDay(nowMs: Long) {
        val storedKey = preferencesDataSource.dayKey
        val todayKey  = LocalDate.now().toString()
        if (storedKey == todayKey || storedKey.isEmpty()) {
            if (storedKey.isEmpty()) preferencesDataSource.dayKey = todayKey
            return
        }

        Log.d(TAG, "Day rollover: $storedKey → $todayKey")
        val calcAnchor = preferencesDataSource.lastCalcMillis
        val midnightMs = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (preferencesDataSource.isScreenOn && calcAnchor in 1 until midnightMs) {
            // Screen was on across midnight — split at midnight boundary.
            val beforeMidnight = (midnightMs - calcAnchor).coerceAtLeast(0L)
            val afterMidnight  = (nowMs - midnightMs).coerceAtLeast(0L)

            // Attribute time before midnight to the old day.
            preferencesDataSource.totalScreenTimeTodayMs     += beforeMidnight
            preferencesDataSource.lateNightScreenTimeTodayMs += lateNightOverlapMs(calcAnchor, midnightMs)

            persistDailySummary(storedKey)
            preferencesDataSource.rolloverToNewDay(todayKey)

            // Attribute time after midnight to the new day.
            preferencesDataSource.totalScreenTimeTodayMs     += afterMidnight
            preferencesDataSource.lateNightScreenTimeTodayMs += lateNightOverlapMs(midnightMs, nowMs)
            preferencesDataSource.lastCalcMillis              = nowMs
        } else {
            flushCurrentState(nowMs)
            persistDailySummary(storedKey)
            preferencesDataSource.rolloverToNewDay(todayKey)
            if (preferencesDataSource.isScreenOn) {
                preferencesDataSource.lastCalcMillis = nowMs
            }
        }
    }

    /**
     * Accumulates elapsed screen-on time (total + late-night) since the last calc anchor.
     * Must only be called while holding [stateMutex].
     */
    private fun flushCurrentState(nowMs: Long) {
        val calcAnchor = preferencesDataSource.lastCalcMillis
        if (calcAnchor <= 0L || !preferencesDataSource.isScreenOn) return
        val elapsed = (nowMs - calcAnchor).coerceAtLeast(0L)
        preferencesDataSource.totalScreenTimeTodayMs     += elapsed
        preferencesDataSource.lateNightScreenTimeTodayMs += lateNightOverlapMs(calcAnchor, nowMs)
        preferencesDataSource.lastCalcMillis              = nowMs
    }

    private fun publishSnapshot(lastEvent: InteractionEventType? = null) {
        val currentSessionDuration =
            if (preferencesDataSource.isScreenOn && preferencesDataSource.sessionStartMillis > 0L)
                (System.currentTimeMillis() - preferencesDataSource.sessionStartMillis).coerceAtLeast(0L)
            else 0L

        _signal.update {
            InteractionSignal(
                isTracking                 = _isTracking.get(),
                isScreenOn                 = preferencesDataSource.isScreenOn,
                currentSessionDurationMs   = currentSessionDuration,
                totalScreenTimeTodayMs     = preferencesDataSource.totalScreenTimeTodayMs,
                // unlocksToday removed
                lateNightScreenTimeTodayMs = preferencesDataSource.lateNightScreenTimeTodayMs,
                lastEventType              = lastEvent ?: it.lastEventType,
                timestamp                  = LocalDateTime.now()
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Repository reads
    // ─────────────────────────────────────────────────────────────────────────

    override fun getDailySummary(date: LocalDate): Flow<InteractionDailySummary?> =
        dailySummaryDao.getByDate(date.toString()).map { it?.toDomain() }

    override fun getWeeklySummaries(endDate: LocalDate): Flow<List<InteractionDailySummary>> {
        val startDate = endDate.minusDays(6).toString()
        val end       = endDate.toString()
        return dailySummaryDao.getBetweenDates(startDate, end)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getSessionsForDate(date: LocalDate): Flow<List<InteractionSession>> {
        val startMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis   = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return sessionDao.getSessionsBetween(startMillis, endMillis)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun purgeInteractionDataOlderThan(cutoffMillis: Long) {
        sessionDao.deleteOlderThan(cutoffMillis)
        val cutoffDate = Instant.ofEpochMilli(cutoffMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
        dailySummaryDao.deleteOlderThan(cutoffDate)
    }

    override suspend fun flushInteractionDataToDb() {
        stateMutex.withLock {
            val nowMs = System.currentTimeMillis()
            checkAndRolloverDay(nowMs)
            flushCurrentState(nowMs)
            val currentDayKey = preferencesDataSource.dayKey.ifEmpty { LocalDate.now().toString() }
            persistDailySummary(currentDayKey)
        }
    }
}
