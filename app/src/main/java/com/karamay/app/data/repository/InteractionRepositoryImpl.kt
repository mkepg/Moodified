// app/src/main/java/com/karamay/app/data/repository/InteractionRepositoryImpl.kt
package com.karamay.app.data.repository

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
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
        private const val TAG = "InteractionRepo"
        private const val TICKER_INTERVAL_MS = 10_000L
        private const val MAX_SESSION_RESTORE_MS = 12 * 60 * 60 * 1000L
        private const val MIN_SESSION_DURATION_MS = 3_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateMutex = Mutex()
    private var tickerJob: Job? = null
    private var sessionUnlockCount = 0

    private val _isTracking = AtomicBoolean(preferencesDataSource.isTracking)
    override val isTracking: Boolean get() = preferencesDataSource.isTracking

    private val _signal = MutableStateFlow(buildInitialSignal())
    override fun observeLiveSignal(): Flow<InteractionSignal> = _signal.asStateFlow()

    private fun buildInitialSignal(): InteractionSignal {
        return InteractionSignal(
            isTracking = preferencesDataSource.isTracking,
            isScreenOn = preferencesDataSource.isScreenOn,
            totalScreenTimeTodayMs = preferencesDataSource.totalScreenTimeTodayMs,
            unlocksToday = preferencesDataSource.unlocksToday
        )
    }

    override fun startTracking(): Boolean {
        if (_isTracking.getAndSet(true)) return true

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
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
                        val gap = nowMs - preferencesDataSource.lastCalcMillis
                        if (preferencesDataSource.dayKey == LocalDate.now().toString() && gap in 1L..MAX_SESSION_RESTORE_MS) {
                            preferencesDataSource.totalScreenTimeTodayMs += gap
                            Log.d(TAG, "Restored missing screen time gap of ${gap}ms")
                        }
                    } else {
                        preferencesDataSource.sessionStartMillis = nowMs
                    }
                    preferencesDataSource.lastCalcMillis = nowMs
                    sessionUnlockCount = 0
                    startInteractionTicker()
                } else {
                    preferencesDataSource.sessionStartMillis = -1L
                    preferencesDataSource.lastCalcMillis = -1L
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

        scope.launch {
            stateMutex.withLock {
                flushCurrentState(System.currentTimeMillis())
                if (preferencesDataSource.isScreenOn) {
                    finalizeSession(System.currentTimeMillis())
                }
                preferencesDataSource.sessionStartMillis = -1L
                preferencesDataSource.lastCalcMillis = -1L

                val currentDayKey = preferencesDataSource.dayKey.ifEmpty { LocalDate.now().toString() }
                persistDailySummary(currentDayKey)

                publishSnapshot()
            }
        }
    }

    override fun resetSession() {
        if (_isTracking.get()) stopTracking()
        preferencesDataSource.resetSession()
        _signal.value = buildInitialSignal()
    }

    override fun logSystemEvent(eventType: InteractionEventType) {
        if (!_isTracking.get()) return

        scope.launch {
            stateMutex.withLock {
                val nowMs = System.currentTimeMillis()
                checkAndRolloverDay(nowMs)

                when (eventType) {
                    InteractionEventType.SCREEN_ON -> {
                        if (!preferencesDataSource.isScreenOn) {
                            preferencesDataSource.isScreenOn = true
                            preferencesDataSource.sessionStartMillis = nowMs
                            preferencesDataSource.lastCalcMillis = nowMs
                            sessionUnlockCount = 0
                            startInteractionTicker()
                        }
                    }
                    InteractionEventType.UNLOCKED -> {
                        preferencesDataSource.unlocksToday += 1
                        sessionUnlockCount += 1

                        if (!preferencesDataSource.isScreenOn) {
                            preferencesDataSource.isScreenOn = true
                            preferencesDataSource.sessionStartMillis = nowMs
                            preferencesDataSource.lastCalcMillis = nowMs
                            startInteractionTicker()
                        }
                    }
                    InteractionEventType.SCREEN_OFF -> {
                        if (preferencesDataSource.isScreenOn) {
                            flushCurrentState(nowMs)
                            finalizeSession(nowMs)
                            preferencesDataSource.isScreenOn = false
                            preferencesDataSource.sessionStartMillis = -1L
                            preferencesDataSource.lastCalcMillis = -1L
                            tickerJob?.cancel()
                        }
                    }
                }
                publishSnapshot(eventType)
            }
        }
    }

    private suspend fun finalizeSession(nowMs: Long) {
        val startMs = preferencesDataSource.sessionStartMillis
        if (startMs <= 0L) return

        val sessionDuration = nowMs - startMs
        if (sessionDuration >= MIN_SESSION_DURATION_MS) {
            val session = InteractionSession(
                startTime = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).toLocalDateTime(),
                endTime = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDateTime(),
                durationMinutes = (sessionDuration / 60_000L).toInt().coerceAtLeast(1),
                unlockCount = sessionUnlockCount
            )
            sessionDao.insertSession(InteractionSessionEntity.fromDomain(session))
            Log.d(TAG, "Session finalized and saved to DB: ${sessionDuration}ms")
        } else {
            Log.d(TAG, "Session too short (${sessionDuration}ms), discarded from DB.")
        }
    }

    private suspend fun persistDailySummary(dateKey: String) {
        val todayKey = LocalDate.now().toString()
        val isPartial = dateKey == todayKey

        val summary = InteractionDailySummary(
            date = dateKey,
            totalScreenTimeMinutes = (preferencesDataSource.totalScreenTimeTodayMs / 60_000L).toInt(),
            unlocks = preferencesDataSource.unlocksToday,
            isPartialDay = isPartial
        )
        dailySummaryDao.upsert(InteractionDailySummaryEntity.fromDomain(summary))
        Log.d(TAG, "Daily Summary saved to DB for $dateKey (isPartial=$isPartial)")
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

    private suspend fun checkAndRolloverDay(nowMs: Long) {
        val storedKey = preferencesDataSource.dayKey
        val todayKey = LocalDate.now().toString()

        if (storedKey == todayKey || storedKey.isEmpty()) {
            if (storedKey.isEmpty()) preferencesDataSource.dayKey = todayKey
            return
        }

        Log.d(TAG, "Day rollover detected: $storedKey -> $todayKey")

        val calcAnchor = preferencesDataSource.lastCalcMillis
        val midnightMs = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (preferencesDataSource.isScreenOn && calcAnchor in 1 until midnightMs) {
            val timeBeforeMidnight = (midnightMs - calcAnchor).coerceAtLeast(0L)
            val timeAfterMidnight = (nowMs - midnightMs).coerceAtLeast(0L)

            preferencesDataSource.totalScreenTimeTodayMs += timeBeforeMidnight
            persistDailySummary(storedKey)

            preferencesDataSource.rolloverToNewDay(todayKey)
            preferencesDataSource.totalScreenTimeTodayMs += timeAfterMidnight
            preferencesDataSource.lastCalcMillis = nowMs
        } else {
            flushCurrentState(nowMs)
            persistDailySummary(storedKey)
            preferencesDataSource.rolloverToNewDay(todayKey)

            if (preferencesDataSource.isScreenOn) {
                preferencesDataSource.lastCalcMillis = nowMs
            }
        }
    }

    private fun flushCurrentState(nowMs: Long) {
        val calcAnchor = preferencesDataSource.lastCalcMillis
        if (calcAnchor <= 0L || !preferencesDataSource.isScreenOn) return

        val elapsed = (nowMs - calcAnchor).coerceAtLeast(0L)
        preferencesDataSource.totalScreenTimeTodayMs += elapsed
        // Move the anchor forward, preserving the original sessionStartMillis
        preferencesDataSource.lastCalcMillis = nowMs
    }

    private fun publishSnapshot(lastEvent: InteractionEventType? = null) {
        val currentSessionDuration = if (preferencesDataSource.isScreenOn && preferencesDataSource.sessionStartMillis > 0L) {
            (System.currentTimeMillis() - preferencesDataSource.sessionStartMillis).coerceAtLeast(0L)
        } else 0L

        _signal.update {
            InteractionSignal(
                isTracking = _isTracking.get(),
                isScreenOn = preferencesDataSource.isScreenOn,
                currentSessionDurationMs = currentSessionDuration,
                totalScreenTimeTodayMs = preferencesDataSource.totalScreenTimeTodayMs,
                unlocksToday = preferencesDataSource.unlocksToday,
                lastEventType = lastEvent ?: it.lastEventType,
                timestamp = LocalDateTime.now()
            )
        }
    }

    // --- Phase 4: Historical Queries & Data Purging ---

    override fun getDailySummary(date: LocalDate): Flow<InteractionDailySummary?> {
        return dailySummaryDao.getByDate(date.toString())
            .map { entity -> entity?.toDomain() }
    }

    override fun getWeeklySummaries(endDate: LocalDate): Flow<List<InteractionDailySummary>> {
        val startDate = endDate.minusDays(6).toString()
        val end = endDate.toString()
        return dailySummaryDao.getBetweenDates(startDate, end)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getSessionsForDate(date: LocalDate): Flow<List<InteractionSession>> {
        val startMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

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