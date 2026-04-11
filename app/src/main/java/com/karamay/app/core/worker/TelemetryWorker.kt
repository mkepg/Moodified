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
import com.karamay.app.core.coordination.TrackingCoordinator
import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.repository.InteractionRepository
import com.karamay.app.domain.repository.SleepRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class TelemetryWorker @AssistedInject constructor(
    @Assisted appContext:    Context,
    @Assisted workerParams:  WorkerParameters,
    private val activityRepository:    ActivityRepository,
    private val interactionRepository: InteractionRepository,
    private val sleepRepository:       SleepRepository,
    private val trackingCoordinator:   TrackingCoordinator
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG       = "TelemetryWorker"
        const val WORK_NAME         = "karamay_telemetry_flush"
        private const val INTERVAL_MINUTES = 15L

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
            var activeTrackers = false

            if (activityRepository.isTracking) {
                activityRepository.flushTelemetryToDb()
                trackingCoordinator.startActivity() // Resurrection
                activeTrackers = true
                Log.d(TAG, "Activity telemetry flushed and service verified.")
            }

            if (interactionRepository.isTracking) {
                interactionRepository.flushInteractionDataToDb()
                trackingCoordinator.startInteraction() // Resurrection
                activeTrackers = true
                Log.d(TAG, "Interaction telemetry flushed and service verified.")
            }

            if (sleepRepository.isTracking) {
                trackingCoordinator.startSleep() // Resurrection
                activeTrackers = true
                Log.d(TAG, "Sleep service verified.")
            }

            if (!activeTrackers) {
                Log.d(TAG, "Nothing tracking — flush skipped.")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Telemetry flush failed: ${e.message}", e)
            Result.retry()
        }
    }
}