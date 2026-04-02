package com.karamay.app.domain.usecase.sleep

import com.karamay.app.domain.repository.SleepRepository
import javax.inject.Inject

class ControlSleepTrackingUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    val isTracking: Boolean get() = repository.isTracking

    fun start(): Boolean = repository.startTracking()
    fun stop() = repository.stopTracking()
}