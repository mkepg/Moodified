package com.moodified.app.domain.usecase.common

import com.moodified.app.domain.repository.ActivityRepository
import com.moodified.app.domain.repository.InteractionRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class PurgeOldTelemetryUseCase @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val interactionRepository: InteractionRepository
) {
    suspend operator fun invoke() {
        val cutoffMillis = Instant.now().minus(14, ChronoUnit.DAYS).toEpochMilli()

        activityRepository.purgeActivityTelemetryOlderThan(cutoffMillis)
        interactionRepository.purgeInteractionDataOlderThan(cutoffMillis)
    }
}