package com.karamay.app.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.karamay.app.domain.repository.ActivityRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TelemetryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val activityRepository: ActivityRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            if (activityRepository.isTracking) {
                activityRepository.flushTelemetryToDb()
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}