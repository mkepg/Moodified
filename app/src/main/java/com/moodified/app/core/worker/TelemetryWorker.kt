package com.moodified.app.core.worker

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moodified.app.core.coordination.TrackingCoordinator
import com.moodified.app.domain.repository.ActivityRepository
import com.moodified.app.domain.repository.InteractionRepository
import com.moodified.app.domain.repository.SleepRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class TelemetryWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val activityRepository: ActivityRepository,
        private val interactionRepository: InteractionRepository,
        private val sleepRepository: SleepRepository,
        private val trackingCoordinator: TrackingCoordinator,
    ) : CoroutineWorker(appContext, workerParams) {
        companion object {
            private const val TAG = "TelemetryWorker"
            const val WORK_NAME = "moodified_telemetry_flush"
            private const val INTERVAL_MINUTES = 15L

            fun schedule(context: Context) {
                Log.d(TAG, "Scheduling periodic telemetry flush every ${INTERVAL_MINUTES}min.")
                val request =
                    PeriodicWorkRequestBuilder<TelemetryWorker>(
                        repeatInterval = INTERVAL_MINUTES,
                        repeatIntervalTimeUnit = TimeUnit.MINUTES,
                    )
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                                .build(),
                        )
                        .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            }
        }

        override suspend fun doWork(): Result {
            return try {
                var activeTrackers = false

                // Re-evaluating permissions dynamically.
                // Ensures auto-recovery if user regrants permissions inside OS Settings.
                val hasActivityPerm = ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasUsagePerm = interactionRepository.hasUsagePermission()

                if (activityRepository.isTracking) {
                    if (hasActivityPerm) {
                        activityRepository.startTracking() // Self-heals if previously paused
                        activityRepository.flushTelemetryToDb()
                        trackingCoordinator.startActivity()
                        activeTrackers = true
                        Log.d(TAG, "Activity telemetry flushed and service verified.")
                    } else {
                        Log.d(TAG, "Activity permission missing. Pausing gracefully.")
                        trackingCoordinator.pauseActivity() // [FIX APPLIED]: Uses pause instead of stop
                    }
                }

                if (interactionRepository.isTracking) {
                    if (hasUsagePerm) {
                        interactionRepository.startTracking() // Self-heals if previously paused
                        interactionRepository.flushInteractionDataToDb()
                        trackingCoordinator.startInteraction()
                        activeTrackers = true
                        Log.d(TAG, "Interaction telemetry flushed and service verified.")
                    } else {
                        Log.d(TAG, "Usage access missing. Pausing gracefully.")
                        trackingCoordinator.pauseInteraction() // [FIX APPLIED]: Uses pause instead of stop
                    }
                }

                if (sleepRepository.isTracking) {
                    if (hasUsagePerm) {
                        sleepRepository.startTracking() // Self-heals if previously paused
                        sleepRepository.flushSleepDataToDb()
                        trackingCoordinator.startSleep()
                        activeTrackers = true
                        Log.d(TAG, "Sleep data flushed and service verified.")
                    } else {
                        Log.d(TAG, "Usage access missing. Pausing gracefully.")
                        trackingCoordinator.pauseSleep() // [FIX APPLIED]: Uses pause instead of stop
                    }
                }

                if (!activeTrackers) {
                    Log.d(TAG, "No trackers active or permitted — flush skipped.")
                }

                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Telemetry flush failed: ${e.message}", e)
                Result.retry()
            }
        }
    }
