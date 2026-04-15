package com.karamay.app.data.local.datasource

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageStatsDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "UsageStatsDataSource"
        private val LATE_NIGHT_START: LocalTime = LocalTime.MIDNIGHT
        private val LATE_NIGHT_END: LocalTime   = LocalTime.of(5, 0)
        private const val MAX_SESSION_GAP_MS    = 12L * 60 * 60 * 1_000
    }

    fun hasPermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.w(TAG, "Permission check failed: ${e.message}")
            false
        }
    }

    fun queryDayStats(
        date:  LocalDate = LocalDate.now(),
        endMs: Long      = System.currentTimeMillis()
    ): DayStats? {
        val usm     = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val zone    = ZoneId.systemDefault()
        val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()

        return try {
            val events = usm.queryEvents(startMs, endMs) ?: return null
            parseEvents(events, startMs, endMs, zone)
        } catch (e: SecurityException) {
            Log.e(TAG, "UsageStats permission denied: ${e.message}"); null
        } catch (e: Exception) {
            Log.e(TAG, "queryDayStats failed: ${e.message}"); null
        }
    }

    private fun parseEvents(
        events:  UsageEvents,
        startMs: Long,
        endMs:   Long,
        zone:    ZoneId
    ): DayStats {
        var screenOnMs   = 0L
        var lateNightMs  = 0L
        var unlockCount  = 0
        var sessionStart = -1L

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    sessionStart = maxOf(event.timeStamp, startMs)
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (sessionStart > 0L) {
                        val sessionEnd = minOf(event.timeStamp, endMs)
                        if (sessionEnd > sessionStart) {
                            val duration = sessionEnd - sessionStart
                            if (duration <= MAX_SESSION_GAP_MS) {
                                screenOnMs  += duration
                                lateNightMs += lateNightOverlapMs(sessionStart, sessionEnd, zone)
                            }
                        }
                        sessionStart = -1L
                    }
                }
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlockCount++
            }
        }

        if (sessionStart > 0L && endMs > sessionStart) {
            val duration = endMs - sessionStart
            if (duration <= MAX_SESSION_GAP_MS) {
                screenOnMs  += duration
                lateNightMs += lateNightOverlapMs(sessionStart, endMs, zone)
            }
        }

        return DayStats(screenOnMs, lateNightMs, unlockCount)
    }

    private fun lateNightOverlapMs(startMs: Long, endMs: Long, zone: ZoneId): Long {
        if (endMs <= startMs) return 0L
        var total  = 0L
        var day    = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
        val endDay = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate()

        while (!day.isAfter(endDay)) {
            val windowStart  = day.atTime(LATE_NIGHT_START).atZone(zone).toInstant().toEpochMilli()
            val windowEnd    = day.atTime(LATE_NIGHT_END).atZone(zone).toInstant().toEpochMilli()

            val overlapStart = maxOf(startMs, windowStart)
            val overlapEnd   = minOf(endMs, windowEnd)

            if (overlapEnd > overlapStart) total += overlapEnd - overlapStart
            day = day.plusDays(1)
        }
        return total
    }

    fun queryScreenOffGaps(startMs: Long, endMs: Long): List<Pair<Long, Long>> {
        return try {
            val usm        = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val queryStart = startMs - 60 * 60_000L
            val events     = usm.queryEvents(queryStart, endMs) ?: return emptyList()

            val gaps       = mutableListOf<Pair<Long, Long>>()
            var screenOffAt = -1L
            val ev = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                when (ev.eventType) {
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        if (screenOffAt < 0) screenOffAt = ev.timeStamp
                    }
                    UsageEvents.Event.SCREEN_INTERACTIVE -> {
                        if (screenOffAt >= 0) {
                            val gapStart = maxOf(screenOffAt, startMs)
                            val gapEnd   = minOf(ev.timeStamp, endMs)
                            if (gapEnd > gapStart) gaps.add(gapStart to gapEnd)
                            screenOffAt = -1L
                        }
                    }
                }
            }

            // [FIX APPLIED]: Capping trailing gaps to current physical time to prevent future extrapolation
            val actualEndMs = minOf(endMs, System.currentTimeMillis())
            if (screenOffAt >= startMs && actualEndMs > screenOffAt) {
                gaps.add(screenOffAt to actualEndMs)
            }

            gaps
        } catch (e: Exception) {
            Log.e(TAG, "queryScreenOffGaps failed: ${e.message}")
            emptyList()
        }
    }

    fun hasEnoughSleepData(startMs: Long, endMs: Long): Boolean {
        return try {
            val usm    = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(startMs, endMs) ?: return false

            val ev     = UsageEvents.Event()
            var count  = 0
            while (events.hasNextEvent() && count < 5) {
                events.getNextEvent(ev)
                if (ev.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
                    ev.eventType == UsageEvents.Event.SCREEN_INTERACTIVE) count++
            }
            count >= 2
        } catch (e: Exception) {
            Log.w(TAG, "hasEnoughSleepData check failed: ${e.message}")
            false
        }
    }

    data class DayStats(val screenOnMs: Long, val lateNightMs: Long, val unlockCount: Int) {
        val screenOnMinutes:  Int get() = (screenOnMs  / 60_000L).toInt()
        val lateNightMinutes: Int get() = (lateNightMs / 60_000L).toInt()
    }
}