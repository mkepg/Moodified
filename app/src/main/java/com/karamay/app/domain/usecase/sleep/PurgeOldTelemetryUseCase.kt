package com.karamay.app.domain.usecase.sleep

import com.karamay.app.domain.repository.SleepRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class PurgeOldTelemetryUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    suspend operator fun invoke() {
        // Keep exactly 14 days of raw high-density telemetry
        val cutoff = Instant.now().minus(14, ChronoUnit.DAYS).toEpochMilli()
        repository.purgeTelemetryOlderThan(cutoff)
    }
}