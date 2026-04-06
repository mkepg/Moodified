package com.karamay.app.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.karamay.app.domain.usecase.common.PurgeOldTelemetryUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PurgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val purgeOldTelemetryUseCase: PurgeOldTelemetryUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            purgeOldTelemetryUseCase()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}