// app/src/main/java/com/karamay/app/domain/usecase/common/PurgeOldTelemetryUseCase.kt
package com.karamay.app.domain.usecase.common

import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.repository.InteractionRepository
import com.karamay.app.domain.repository.SleepRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class PurgeOldTelemetryUseCase @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val activityRepository: ActivityRepository,
    private val interactionRepository: InteractionRepository
) {
    suspend operator fun invoke() {
        // Keeps data for the last 14 days, purging anything older.
        val cutoffMillis = Instant.now().minus(14, ChronoUnit.DAYS).toEpochMilli()

        sleepRepository.purgeTelemetryOlderThan(cutoffMillis)
        activityRepository.purgeActivityTelemetryOlderThan(cutoffMillis)
        interactionRepository.purgeInteractionDataOlderThan(cutoffMillis)
    }
}