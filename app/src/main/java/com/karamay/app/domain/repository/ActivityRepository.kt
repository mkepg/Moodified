package com.karamay.app.domain.repository

import com.karamay.app.domain.model.ActivitySignal
import kotlinx.coroutines.flow.Flow

interface ActivityRepository {
    // Fix A: observeSignal() now backed by ActivitySignalBus — the Flow emits a meaningful
    // restored snapshot immediately after a process kill, mirroring SleepRepository.observeLiveSignal().
    fun observeSignal(): Flow<ActivitySignal>

    val isTracking: Boolean
    fun startTracking(): Boolean
    fun stopTracking()

    // Fix #11 (prior pass): resetSession() on the interface avoids concrete-type casting.
    fun resetSession()

    // Fix #6 (prior pass): purge hook for PurgeActivityTelemetryUseCase.
    suspend fun purgeActivityTelemetryOlderThan(cutoffMillis: Long)
}
