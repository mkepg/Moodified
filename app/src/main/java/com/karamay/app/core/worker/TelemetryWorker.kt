// app/src/main/java/com/karamay/app/core/worker/TelemetryWorker.kt
package com.karamay.app.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.repository.InteractionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TelemetryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val activityRepository: ActivityRepository,
    private val interactionRepository: InteractionRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            if (activityRepository.isTracking) {
                activityRepository.flushTelemetryToDb()
            }

            // Phase 4 completeness: Periodic flushing for interaction data
            if (interactionRepository.isTracking) {
                interactionRepository.flushInteractionDataToDb()
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}