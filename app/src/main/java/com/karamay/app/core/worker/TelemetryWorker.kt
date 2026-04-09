package com.karamay.app.core.worker

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
import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.repository.InteractionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * FIX P1: Periodically flushes in-flight tracking data to Room so that the
 * reactive DAO flows (observed by all three monitor ViewModels) receive new
 * emissions *during* an active tracking session — not only on Stop.
 *
 * Previously this Worker existed but had no schedule() call anywhere, so it
 * was never enqueued and the Room DB was only ever written on stopTracking().
 * That was the root cause of "data appears only when paused."
 *
 * Call [schedule] from KaramayApplication.onCreate() once WorkManager is ready.
 */
@HiltWorker
class TelemetryWorker @AssistedInject constructor(
    @Assisted appContext:    Context,
    @Assisted workerParams:  WorkerParameters,
    private val activityRepository:    ActivityRepository,
    private val interactionRepository: InteractionRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG       = "TelemetryWorker"
        const val WORK_NAME         = "karamay_telemetry_flush"
        private const val INTERVAL_MINUTES = 15L

        /**
         * Enqueues a periodic 15-minute telemetry flush.
         * Uses [ExistingPeriodicWorkPolicy.UPDATE] so re-scheduling on every
         * app launch is safe — it will replace the existing request without
         * resetting the interval timer if the constraints are identical.
         */
        fun schedule(context: Context) {
            Log.d(TAG, "Scheduling periodic telemetry flush every ${INTERVAL_MINUTES}min.")
            val request = PeriodicWorkRequestBuilder<TelemetryWorker>(
                repeatInterval         = INTERVAL_MINUTES,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            var flushed = false
            if (activityRepository.isTracking) {
                activityRepository.flushTelemetryToDb()
                flushed = true
                Log.d(TAG, "Activity telemetry flushed.")
            }
            if (interactionRepository.isTracking) {
                interactionRepository.flushInteractionDataToDb()
                flushed = true
                Log.d(TAG, "Interaction telemetry flushed.")
            }
            if (!flushed) Log.d(TAG, "Nothing tracking — flush skipped.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Telemetry flush failed: ${e.message}", e)
            Result.retry()
        }
    }
}
