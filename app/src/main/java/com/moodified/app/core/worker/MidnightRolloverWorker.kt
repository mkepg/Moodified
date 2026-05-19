package com.moodified.app.core.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moodified.app.domain.repository.InteractionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * MidnightRolloverWorker — Phase 6
 *
 * Addresses a specific gap in the midnight-rollover lifecycle that [TelemetryWorker]
 * alone cannot cover:
 *
 * [TelemetryWorker] runs every 15 minutes and calls [InteractionRepository.flushInteractionDataToDb]
 * whenever [InteractionRepository.isTracking] is true. This handles the rollover reliably
 * whenever the process is alive. However, if the device reboots overnight, WorkManager
 * reschedules [TelemetryWorker] with a fresh 15-minute interval — meaning there is a
 * window (potentially up to 15 minutes after boot) during which no flush has occurred
 * and the previous day's summary has not been committed to Room.
 *
 * This worker is scheduled to fire at 00:05 each night, well after midnight, giving the
 * OS time to settle after any Doze transitions. It calls [flushInteractionDataToDb] which
 * already contains the complete rollover logic and is fully idempotent: if the rollover was
 * already handled by [TelemetryWorker] or a system event, the stored day-key will already
 * equal today's date and the function returns immediately with no side-effects.
 *
 * Robustness guarantees:
 *  - [ExistingPeriodicWorkPolicy.UPDATE] preserves the existing schedule across app updates
 *    without resetting the countdown (unlike REPLACE) or silently ignoring spec changes (KEEP).
 *  - [BackoffPolicy.EXPONENTIAL] with a 10-minute initial delay retries on failure without
 *    thrashing the system, allowing recovery within the same scheduling window.
 *  - No network or charging constraints — must fire even when the device is offline and
 *    unplugged overnight.
 *  - If tracking is inactive, doWork() exits immediately with [Result.success] —
 *    idempotent and side-effect free.
 *
 * Scheduling: Call [schedule] once from [MoodifiedApplication.onCreate]. WorkManager
 * persists the schedule across process death, reboots, and app updates automatically.
 */
@HiltWorker
class MidnightRolloverWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val interactionRepository: InteractionRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG           = "MidnightRolloverWorker"
        const val WORK_NAME             = "moodified_midnight_rollover"

        // Target: 00:05 — five minutes after midnight gives the OS a grace window
        // to exit Doze and reach full CPU capacity.
        private const val TARGET_HOUR   = 0
        private const val TARGET_MINUTE = 5

        /**
         * Enqueues (or updates) the periodic midnight rollover worker.
         * Safe to call on every app launch — WorkManager deduplicates via [WORK_NAME].
         */
        fun schedule(context: Context) {
            val initialDelay = computeInitialDelayMs()
            Log.d(TAG, "Scheduling midnight rollover in ${initialDelay / 1000}s " +
                    "(next run at ${TARGET_HOUR.toString().padStart(2,'0')}:" +
                    "${TARGET_MINUTE.toString().padStart(2,'0')})")

            val request = PeriodicWorkRequestBuilder<MidnightRolloverWorker>(
                repeatInterval         = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                // UPDATE: preserves schedule timing while applying any spec changes from
                // an app update. Safer than REPLACE (resets timer) and more correct than
                // KEEP (ignores spec changes silently).
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /**
         * Returns the millisecond delay until the next 00:05 occurrence.
         *
         * If the current time is already past 00:05 today, targets tomorrow's 00:05.
         * Minimum clamped to 60 seconds to prevent an immediate fire on edge-case reschedules.
         */
        private fun computeInitialDelayMs(): Long {
            val now    = LocalDateTime.now()
            val target = LocalTime.of(TARGET_HOUR, TARGET_MINUTE)

            var nextRun = now.toLocalDate().atTime(target)
            if (!now.toLocalTime().isBefore(target)) {
                nextRun = nextRun.plusDays(1)
            }

            return Duration.between(now, nextRun).toMillis().coerceAtLeast(60_000L)
        }
    }

    // -------------------------------------------------------------------------
    // doWork
    // -------------------------------------------------------------------------

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Running at ${LocalDateTime.now()}")

            if (!interactionRepository.isTracking) {
                Log.d(TAG, "Interaction tracking is inactive — no flush needed.")
                return Result.success()
            }

            // flushInteractionDataToDb is idempotent: if checkAndRolloverDay finds that
            // storedKey == todayKey (because TelemetryWorker already ran), it returns
            // immediately with no side-effects.
            interactionRepository.flushInteractionDataToDb()

            Log.d(TAG, "Midnight rollover flush complete for ${LocalDate.now().minusDays(1)}.")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Midnight rollover failed — scheduling retry. Error: ${e.message}", e)
            Result.retry()
        }
    }
}
