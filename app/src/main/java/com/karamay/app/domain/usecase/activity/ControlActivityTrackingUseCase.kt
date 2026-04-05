package com.karamay.app.domain.usecase.activity

import com.karamay.app.domain.repository.ActivityRepository
import javax.inject.Inject

class ControlActivityTrackingUseCase @Inject constructor(
    private val repository: ActivityRepository
) {
    val isTracking: Boolean get() = repository.isTracking

    fun start(): Boolean = repository.startTracking()

    fun stop() = repository.stopTracking()

    // Fix #11: resetSession() is now exposed through the use case so any future caller
    // going through the domain layer (e.g. a ViewModel or another use case) can trigger
    // a session reset without ever touching the concrete implementation.
    fun resetSession() = repository.resetSession()
}
