package com.moodified.app.data.repository

import android.content.Context
import android.util.Log
import com.moodified.app.core.coordination.PollingJob
import com.moodified.app.core.coordination.TrackingCoordinator
import com.moodified.app.data.local.dao.interaction.InteractionDailySummaryDao
import com.moodified.app.data.local.dao.interaction.InteractionSessionDao
import com.moodified.app.data.local.datasource.InteractionPreferencesDataSource
import com.moodified.app.data.local.datasource.UsageStatsDataSource
import com.moodified.app.data.local.entity.interaction.InteractionDailySummaryEntity
import com.moodified.app.data.local.entity.interaction.InteractionSessionEntity
import com.moodified.app.domain.model.interaction.InteractionDailySummary
import com.moodified.app.domain.model.interaction.InteractionEventType
import com.moodified.app.domain.model.interaction.InteractionSession
import com.moodified.app.domain.model.interaction.InteractionSignal
import com.moodified.app.domain.repository.InteractionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InteractionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usageStatsDataSource:  UsageStatsDataSource,
    private val preferencesDataSource: InteractionPreferencesDataSource,
    private val sessionDao:            InteractionSessionDao,
    private val dailySummaryDao:       InteractionDailySummaryDao,
    private val coordinator:           TrackingCoordinator,
) : InteractionRepository {

    companion object {
        private const val TAG           = "InteractionRepo"
        private const val POLL_INTERVAL = 60_000L
        private const val BACKFILL_DAYS = 7
    }

    private val scope      = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()

    @Volatile private var _isProcessActive: Boolean = false

    override val isTracking: Boolean get() = preferencesDataSource.isTracking

    private val _signal = MutableStateFlow(buildSignalFromPrefs())
    override fun observeLiveSignal(): Flow<InteractionSignal> = _signal.asStateFlow()

    private val poller = PollingJob(
        scope      = scope,
        mutex      = stateMutex,
        intervalMs = POLL_INTERVAL,
        tag        = "$TAG/poll",
        isActive   = { _isProcessActive }
    ) { refreshFromUsageStats() }

    override fun startTracking(): Boolean {
        Log.d(TAG, "[TRACKING_FLOW] startTracking()")
        if (!usageStatsDataSource.hasPermission()) {
            Log.w(TAG, "[TRACKING_FLOW] No Usage permission — aborted.")
            return false
        }

        preferencesDataSource.isTracking = true
        publishSnapshot("startTracking")

        if (_isProcessActive) {
            Log.d(TAG, "[TRACKING_FLOW] Already tracking — circuit breaker.")
            return true
        }
        _isProcessActive = true

        coordinator.startInteraction()
        poller.start()

        scope.launch(Dispatchers.IO) { backfillHistory() }
        return true
    }

    override fun stopTracking() {
        Log.d(TAG, "[TRACKING_FLOW] stopTracking()")
        preferencesDataSource.isTracking = false
        publishSnapshot("stopTracking_forced")

        if (!_isProcessActive) {
            Log.d(TAG, "[TRACKING_FLOW] Already stopped — circuit breaker bypassed for prefs.")
            return
        }

        _isProcessActive = false
        poller.stop()
        coordinator.stopInteraction()

        scope.launch {
            try {
                stateMutex.withLock { persistDailySummary(LocalDate.now()) }
            } catch (e: Exception) {
                Log.e(TAG, "[TRACKING_FLOW] DB persist on stop failed", e)
            }
        }
    }

    override fun pauseTracking() {
        if (!_isProcessActive) return
        Log.d(TAG, "[TRACKING_FLOW] pauseTracking() - suspending process. Intent preserved.")
        _isProcessActive = false
        poller.stop()
        coordinator.stopInteraction()

        scope.launch {
            try {
                stateMutex.withLock { persistDailySummary(LocalDate.now()) }
            } catch (e: Exception) {
                Log.e(TAG, "[TRACKING_FLOW] DB persist on pause failed", e)
            }
        }
    }

    private suspend fun refreshFromUsageStats() {
        val today = LocalDate.now()
        checkAndRolloverDay(today)
        val stats = usageStatsDataSource.queryDayStats(today) ?: return

        preferencesDataSource.totalScreenTimeTodayMs     = stats.screenOnMs
        preferencesDataSource.lateNightScreenTimeTodayMs = stats.lateNightMs
        preferencesDataSource.unlockCount                = stats.unlockCount

        // --- SYNCHRONIZE SESSIONS ---
        val zone = ZoneId.systemDefault()
        val startMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        sessionDao.deleteSessionsBetween(startMs, endMs)
        stats.sessions.forEach { session ->
            sessionDao.insertSession(InteractionSessionEntity.fromDomain(session))
        }
        // ----------------------------

        persistDailySummary(today)
        publishSnapshot("refreshFromUsageStats")
    }

    private suspend fun backfillHistory() {
        if (!usageStatsDataSource.hasPermission()) return
        val today = LocalDate.now()

        for (daysBack in 1..BACKFILL_DAYS) {
            val date = today.minusDays(daysBack.toLong())
            try {
                val zone  = ZoneId.systemDefault()
                val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val stats = usageStatsDataSource.queryDayStats(date = date, endMs = endMs)

                if (stats != null) {
                    // --- SYNCHRONIZE SESSIONS ---
                    sessionDao.deleteSessionsBetween(startMs, endMs)
                    stats.sessions.forEach { session ->
                        sessionDao.insertSession(InteractionSessionEntity.fromDomain(session))
                    }
                    // ----------------------------

                    dailySummaryDao.upsert(
                        InteractionDailySummaryEntity(
                            date                   = date.toString(),
                            totalScreenTimeMinutes = stats.screenOnMinutes,
                            lateNightUsageMinutes  = stats.lateNightMinutes,
                            unlockCount            = stats.unlockCount,
                            sessionCount           = stats.sessions.size,
                            averageSessionDurationMinutes = if (stats.sessions.isNotEmpty()) stats.sessions.sumOf { it.durationMinutes } / stats.sessions.size else 0
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "backfillHistory failed for $date: ${e.message}")
            }
        }
    }

    private suspend fun checkAndRolloverDay(today: LocalDate) {
        val storedKey = preferencesDataSource.dayKey
        val todayKey  = today.toString()

        if (today.year < 2024) {
            Log.w(TAG, "System clock indicates year ${today.year}. Awaiting NTP sync before rollover checks.")
            return
        }

        if (storedKey == todayKey || storedKey.isEmpty()) {
            if (storedKey.isEmpty()) preferencesDataSource.dayKey = todayKey
            return
        }

        val yesterday = runCatching { LocalDate.parse(storedKey) }.getOrNull()
            ?: today.minusDays(1)

        val yesterdayStats = usageStatsDataSource.queryDayStats(yesterday)
        if (yesterdayStats != null) {
            preferencesDataSource.totalScreenTimeTodayMs     = yesterdayStats.screenOnMs
            preferencesDataSource.lateNightScreenTimeTodayMs = yesterdayStats.lateNightMs
            preferencesDataSource.unlockCount                = yesterdayStats.unlockCount
        }

        persistDailySummary(yesterday)
        preferencesDataSource.rolloverToNewDay(todayKey)
    }

    private suspend fun persistDailySummary(date: LocalDate) {
        val zone = ZoneId.systemDefault()
        val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val sessions = sessionDao.getSessionsListBetween(startMs, endMs)
        val sessionCount = sessions.size
        val avgDuration = if (sessionCount > 0) sessions.sumOf { it.durationMinutes } / sessionCount else 0

        val summary = InteractionDailySummary(
            date                          = date.toString(),
            totalScreenTimeMinutes        = (preferencesDataSource.totalScreenTimeTodayMs / 60_000L).toInt(),
            lateNightUsageMinutes         = (preferencesDataSource.lateNightScreenTimeTodayMs / 60_000L).toInt(),
            unlockCount                   = preferencesDataSource.unlockCount,
            sessionCount                  = sessionCount,
            averageSessionDurationMinutes = avgDuration
        )

        dailySummaryDao.upsert(InteractionDailySummaryEntity.fromDomain(summary))
    }

    private fun publishSnapshot(source: String) {
        _signal.update {
            InteractionSignal(
                isTracking                 = preferencesDataSource.isTracking,
                totalScreenTimeTodayMs     = preferencesDataSource.totalScreenTimeTodayMs,
                lateNightScreenTimeTodayMs = preferencesDataSource.lateNightScreenTimeTodayMs,
                unlockCount                = preferencesDataSource.unlockCount,
                timestamp                  = LocalDateTime.now()
            )
        }
    }

    private fun buildSignalFromPrefs() = InteractionSignal(
        isTracking                 = preferencesDataSource.isTracking,
        totalScreenTimeTodayMs     = preferencesDataSource.totalScreenTimeTodayMs,
        lateNightScreenTimeTodayMs = preferencesDataSource.lateNightScreenTimeTodayMs,
        unlockCount                = preferencesDataSource.unlockCount
    )

    override fun logSystemEvent(eventType: InteractionEventType) = Unit

    override fun getDailySummary(date: LocalDate): Flow<InteractionDailySummary?> =
        dailySummaryDao.getByDate(date.toString()).map { it?.toDomain() }

    override fun getWeeklySummaries(endDate: LocalDate): Flow<List<InteractionDailySummary>> =
        dailySummaryDao
            .getBetweenDates(endDate.minusDays(6).toString(), endDate.toString())
            .map { it.map { e -> e.toDomain() } }

    override fun getSessionsForDate(date: LocalDate): Flow<List<InteractionSession>> {
        val zone = ZoneId.systemDefault()
        val s    = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val e    = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return sessionDao.getSessionsBetween(s, e).map { it.map { e2 -> e2.toDomain() } }
    }

    override suspend fun purgeInteractionDataOlderThan(cutoffMillis: Long) {
        sessionDao.deleteOlderThan(cutoffMillis)
        val cutoffDate = Instant.ofEpochMilli(cutoffMillis)
            .atZone(ZoneId.systemDefault()).toLocalDate().toString()
        dailySummaryDao.deleteOlderThan(cutoffDate)
    }

    override suspend fun flushInteractionDataToDb() {
        stateMutex.withLock {
            if (!usageStatsDataSource.hasPermission()) return
            val today = LocalDate.now()
            checkAndRolloverDay(today)
            val stats = usageStatsDataSource.queryDayStats(today) ?: return

            preferencesDataSource.totalScreenTimeTodayMs     = stats.screenOnMs
            preferencesDataSource.lateNightScreenTimeTodayMs = stats.lateNightMs
            preferencesDataSource.unlockCount                = stats.unlockCount

            // --- SYNCHRONIZE SESSIONS ---
            val zone = ZoneId.systemDefault()
            val startMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            sessionDao.deleteSessionsBetween(startMs, endMs)
            stats.sessions.forEach { session ->
                sessionDao.insertSession(InteractionSessionEntity.fromDomain(session))
            }
            // ----------------------------

            persistDailySummary(today)
            publishSnapshot("flushInteractionDataToDb")
        }
    }

    override fun hasUsagePermission(): Boolean = usageStatsDataSource.hasPermission()
}