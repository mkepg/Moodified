package com.karamay.app.domain.usecase.common

import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.repository.SleepRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Orchestrates telemetry housekeeping across all tracking domains.
 *
 * Moved from usecase/sleep → usecase/common because it touches both sleep and activity
 * data — it was architecturally wrong to live under the sleep package. Placing it in
 * common alongside PurgeActivityTelemetryUseCase makes the housekeeping layer
 * easy to find and extend when Phase 3 adds new data sources.
 *
 * Called by TrackingService.runHousekeeping() on each sleep session start.
 * Both purges share the same 14-day retention window.
 *
 * NOTE: If usecase/sleep still contains PurgeOldTelemetryUseCase.kt from a prior pass,
 * delete it — this file is the authoritative replacement.
 */
class PurgeOldTelemetryUseCase @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val activityRepository: ActivityRepository,
) {
    suspend operator fun invoke() {
        val cutoffMillis = Instant.now().minus(14, ChronoUnit.DAYS).toEpochMilli()
        sleepRepository.purgeTelemetryOlderThan(cutoffMillis)
        activityRepository.purgeActivityTelemetryOlderThan(cutoffMillis)
    }
}
