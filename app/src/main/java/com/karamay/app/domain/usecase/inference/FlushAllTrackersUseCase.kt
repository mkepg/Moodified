package com.karamay.app.domain.usecase.inference

import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.repository.InteractionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FlushAllTrackersUseCase @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val interactionRepository: InteractionRepository
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        val activityFlush = async {
            if (activityRepository.isTracking) {
                activityRepository.flushTelemetryToDb()
            }
        }

        val interactionFlush = async {
            if (interactionRepository.isTracking) {
                interactionRepository.flushInteractionDataToDb()
            }
        }

        activityFlush.await()
        interactionFlush.await()
    }
}