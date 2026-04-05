package com.karamay.app.data.receiver.sleep

import com.karamay.app.domain.model.SleepSignal
import com.karamay.app.domain.model.SleepStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepSignalBus @Inject constructor() {

    private val _signals = MutableStateFlow(SleepSignal())
    val signals: StateFlow<SleepSignal> = _signals.asStateFlow()

    fun update(
        status: SleepStatus,
        confidence: Int,
        ambientLight: Float,
        deviceMotion: Int,
        timestamp: LocalDateTime,
    ) {
        _signals.update { current ->
            current.copy(
                status       = status,
                confidence   = confidence,
                ambientLight = ambientLight,
                deviceMotion = deviceMotion,
                timestamp    = timestamp,
                isTracking   = current.isTracking,
            )
        }
    }

    fun setTrackingState(isTracking: Boolean) {
        _signals.update { it.copy(isTracking = isTracking) }
    }

    fun resetSession() {
        _signals.value = SleepSignal()
    }
}