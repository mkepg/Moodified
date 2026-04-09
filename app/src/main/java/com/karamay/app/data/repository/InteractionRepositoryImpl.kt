package com.karamay.app.data.repository

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.karamay.app.core.service.TrackingService
import com.karamay.app.data.local.dao.interaction.InteractionDailySummaryDao
import com.karamay.app.data.local.dao.interaction.InteractionSessionDao
import com.karamay.app.data.local.datasource.InteractionPreferencesDataSource
import com.karamay.app.data.local.datasource.UsageStatsDataSource
import com.karamay.app.data.local.entity.interaction.InteractionDailySummaryEntity
import com.karamay.app.domain.model.interaction.InteractionDailySummary
import com.karamay.app.domain.model.interaction.InteractionEventType
import com.karamay.app.domain.model.interaction.InteractionSession
import com.karamay.app.domain.model.interaction.InteractionSignal
import com.karamay.app.domain.repository.InteractionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
    private val usageStatsDataSource:  UsageStatsDataSource,
    private val preferencesDataSource: InteractionPreferencesDataSource,
    private val sessionDao:            InteractionSessionDao,
    private val dailySummaryDao:       InteractionDailySummaryDao
) : InteractionRepository {

    companion object {
        private const val TAG              = "InteractionRepo"
        private const val POLL_INTERVAL_MS = 60_000L
        private const val BACKFILL_DAYS    = 7
    }

    private val scope      = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateMutex = Mutex()
    private var pollJob:    Job? = null

    private val _isTracking = AtomicBoolean(preferencesDataSource.isTracking)
    override val isTracking: Boolean get() = preferencesDataSource.isTracking

    private val _signal = MutableStateFlow(buildSignalFromPrefs())
    override fun observeLiveSignal(): Flow<InteractionSignal> = _signal.asStateFlow()

    override fun hasUsagePermission(): Boolean = usageStatsDataSource.hasPermission()

    override fun startTracking(): Boolean {
        Log.d(TAG, "[TRACKING_FLOW] Repo: startTracking() invoked.")

        if (!usageStatsDataSource.hasPermission()) {
            Log.w(TAG, "[TRACKING_FLOW] Repo: startTracking aborted -> No Usage permission.")
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "[TRACKING_FLOW] Repo: startTracking aborted -> Missing ACTIVITY_RECOGNITION.")
            return false
        }

        val wasTracking = _isTracking.getAndSet(true)
        if (wasTracking) {
            Log.d(TAG, "[TRACKING_FLOW] Repo: Already tracking. Circuit breaker triggered.")
            return true
        }

        preferencesDataSource.isTracking = true
        publishSnapshot("startTracking")

        Log.d(TAG, "[TRACKING_FLOW] Repo: Firing ACTION_START_INTERACTION intent to Service.")
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java).apply {
                    action = TrackingService.ACTION_START_INTERACTION
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "[TRACKING_FLOW] Repo: Failed to start service", e)
        }

        startPollLoop()
        scope.launch(Dispatchers.IO) { backfillHistory() }

        return true
    }

    override fun stopTracking() {
        Log.d(TAG, "[TRACKING_FLOW] Repo: stopTracking() invoked.")

        if (!_isTracking.getAndSet(false)) {
            Log.d(TAG, "[TRACKING_FLOW] Repo: Already stopped. Circuit breaker triggered.")
            return
        }

        preferencesDataSource.isTracking = false

        pollJob?.cancel()
        pollJob = null

        publishSnapshot("stopTracking")

        Log.d(TAG, "[TRACKING_FLOW] Repo: Firing ACTION_STOP_INTERACTION intent to Service.")
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java).apply {
                    action = TrackingService.ACTION_STOP_INTERACTION
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "[TRACKING_FLOW] Repo: Failed to send stop intent", e)
        }

        scope.launch {
            try {
                stateMutex.withLock { persistDailySummary(LocalDate.now()) }
            } catch (e: Exception) {
                Log.e(TAG, "[TRACKING_FLOW] Repo: Failed DB persist on stop", e)
            }
        }
    }

    private fun startPollLoop() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                stateMutex.withLock { refreshFromUsageStats() }
                delay(POLL_INTERVAL_MS)
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

        persistDailySummary(today)
        publishSnapshot("refreshFromUsageStats")
    }

    private suspend fun backfillHistory() {
        if (!usageStatsDataSource.hasPermission()) return

        val today = LocalDate.now()
        for (daysBack in 1..BACKFILL_DAYS) {
            val date = today.minusDays(daysBack.toLong())
            try {
                val zone   = ZoneId.systemDefault()
                val endMs  = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val stats  = usageStatsDataSource.queryDayStats(date = date, endMs = endMs)

                if (stats != null) {
                    val summary = InteractionDailySummaryEntity(
                        date                   = date.toString(),
                        totalScreenTimeMinutes = stats.screenOnMinutes,
                        lateNightUsageMinutes  = stats.lateNightMinutes,
                        unlockCount            = stats.unlockCount
                    )
                    dailySummaryDao.upsert(summary)
                }
            } catch (e: Exception) {
                Log.w(TAG, "backfillHistory: failed for $date: ${e.message}")
            }
        }
    }

    private suspend fun checkAndRolloverDay(today: LocalDate) {
        val storedKey = preferencesDataSource.dayKey
        val todayKey  = today.toString()

        if (storedKey == todayKey || storedKey.isEmpty()) {
            if (storedKey.isEmpty()) preferencesDataSource.dayKey = todayKey
            return
        }

        val yesterday      = runCatching { LocalDate.parse(storedKey) }.getOrNull() ?: today.minusDays(1)
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
        val summary = InteractionDailySummary(
            date                   = date.toString(),
            totalScreenTimeMinutes = (preferencesDataSource.totalScreenTimeTodayMs / 60_000L).toInt(),
            lateNightUsageMinutes  = (preferencesDataSource.lateNightScreenTimeTodayMs / 60_000L).toInt(),
            unlockCount            = preferencesDataSource.unlockCount
        )
        dailySummaryDao.upsert(InteractionDailySummaryEntity.fromDomain(summary))
    }

    private fun publishSnapshot(source: String) {
        val snapshot = InteractionSignal(
            isTracking                 = _isTracking.get(),
            totalScreenTimeTodayMs     = preferencesDataSource.totalScreenTimeTodayMs,
            lateNightScreenTimeTodayMs = preferencesDataSource.lateNightScreenTimeTodayMs,
            unlockCount                = preferencesDataSource.unlockCount,
            timestamp                  = LocalDateTime.now()
        )
        _signal.update { snapshot }
    }

    private fun buildSignalFromPrefs() = InteractionSignal(
        isTracking                 = preferencesDataSource.isTracking,
        totalScreenTimeTodayMs     = preferencesDataSource.totalScreenTimeTodayMs,
        lateNightScreenTimeTodayMs = preferencesDataSource.lateNightScreenTimeTodayMs,
        unlockCount                = preferencesDataSource.unlockCount
    )

    override fun logSystemEvent(eventType: InteractionEventType) = Unit
    override fun getDailySummary(date: LocalDate): Flow<InteractionDailySummary?> = dailySummaryDao.getByDate(date.toString()).map { it?.toDomain() }
    override fun getWeeklySummaries(endDate: LocalDate): Flow<List<InteractionDailySummary>> = dailySummaryDao.getBetweenDates(endDate.minusDays(6).toString(), endDate.toString()).map { it.map { e -> e.toDomain() } }
    override fun getSessionsForDate(date: LocalDate): Flow<List<InteractionSession>> {
        val zone = ZoneId.systemDefault()
        val s    = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val e    = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return sessionDao.getSessionsBetween(s, e).map { it.map { e2 -> e2.toDomain() } }
    }
    override suspend fun purgeInteractionDataOlderThan(cutoffMillis: Long) {
        sessionDao.deleteOlderThan(cutoffMillis)
        val cutoffDate = Instant.ofEpochMilli(cutoffMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
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
            persistDailySummary(today)
            publishSnapshot("flushInteractionDataToDb")
        }
    }
}