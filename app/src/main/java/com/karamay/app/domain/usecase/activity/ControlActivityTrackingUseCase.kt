package com.karamay.app.domain.usecase.activity

import com.karamay.app.domain.repository.ActivityRepository
import javax.inject.Inject

/**
 * Encapsulates start/stop lifecycle of the activity sensor session.
 * Returns false from [start] if the device lacks the required hardware.
 */
class ControlActivityTrackingUseCase @Inject constructor(
    private val repository: ActivityRepository
) {
    val isTracking: Boolean get() = repository.isTracking

    /** @return true if tracking started successfully, false if hardware unavailable. */
    fun start(): Boolean = repository.startTracking()

    fun stop() = repository.stopTracking()
}
