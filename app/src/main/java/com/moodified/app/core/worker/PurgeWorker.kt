package com.moodified.app.core.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moodified.app.domain.usecase.common.PurgeOldNotificationRecordsUseCase
import com.moodified.app.domain.usecase.common.PurgeOldTelemetryUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class PurgeWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val purgeOldTelemetryUseCase: PurgeOldTelemetryUseCase,
        private val purgeOldNotificationRecordsUseCase: PurgeOldNotificationRecordsUseCase,
    ) : CoroutineWorker(appContext, workerParams) {
        companion object {
            private const val TAG = "PurgeWorker"
            const val WORK_NAME = "moodified_purge_worker"

            fun schedule(context: Context) {
                Log.d(TAG, "Scheduling periodic database purge every 24 hours.")
                val request =
                    PeriodicWorkRequestBuilder<PurgeWorker>(24, TimeUnit.HOURS)
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
                purgeOldTelemetryUseCase()
                val deletedInboxCount = purgeOldNotificationRecordsUseCase()
                Log.d(TAG, "Purged old telemetry data + $deletedInboxCount inbox records older than 90 days.")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to purge data: ${e.message}", e)
                Result.retry()
            }
        }
    }
