package com.karamay.app.presentation.devtools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.domain.model.ActivityIntensity
import com.karamay.app.domain.model.ActivitySignal
import com.karamay.app.domain.usecase.activity.ControlActivityTrackingUseCase
import com.karamay.app.domain.usecase.activity.ObserveActivitySignalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

// ── Permission state ──────────────────────────────────────────────────────────

sealed interface PermissionState {
    /** Permission has not been requested yet. */
    data object Idle : PermissionState

    /** Waiting for the system permission dialog result. */
    data object Requested : PermissionState

    /** User granted ACTIVITY_RECOGNITION. */
    data object Granted : PermissionState

    /**
     * User denied. [canAskAgain] = false means they selected "Don't ask again"
     * and we must direct them to Settings.
     */
    data class Denied(val canAskAgain: Boolean) : PermissionState
}

// ── UI state ──────────────────────────────────────────────────────────────────

data class ActivityMonitorUiState(
    val permission: PermissionState          = PermissionState.Idle,
    val isTracking: Boolean                  = false,
    val signal: ActivitySignal               = ActivitySignal(),
    /** Populated when [ControlActivityTrackingUseCase.start] returns false. */
    val hardwareError: String?               = null
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ActivityMonitorViewModel @Inject constructor(
    private val observeSignal: ObserveActivitySignalUseCase,
    private val controlTracking: ControlActivityTrackingUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityMonitorUiState())
    val state: StateFlow<ActivityMonitorUiState> = _state.asStateFlow()

    init {
        observeSignal()
            .onEach { signal ->
                _state.update { it.copy(signal = signal) }
            }
            .launchIn(viewModelScope)
    }

    // ── Permission callbacks (called from the screen's permission launcher) ───

    fun onPermissionGranted() {
        _state.update { it.copy(permission = PermissionState.Granted) }
    }

    fun onPermissionDenied(canAskAgain: Boolean) {
        _state.update { it.copy(permission = PermissionState.Denied(canAskAgain)) }
    }

    fun onPermissionRequested() {
        _state.update { it.copy(permission = PermissionState.Requested) }
    }

    // ── Tracking control ─────────────────────────────────────────────────────

    fun startTracking() {
        val started = controlTracking.start()
        if (started) {
            _state.update { it.copy(isTracking = true, hardwareError = null) }
        } else {
            _state.update {
                it.copy(
                    isTracking    = false,
                    hardwareError = "No compatible sensors found on this device."
                )
            }
        }
    }

    fun stopTracking() {
        controlTracking.stop()
        _state.update { it.copy(isTracking = false) }
    }

    fun toggleTracking() {
        if (_state.value.isTracking) stopTracking() else startTracking()
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        // Always release sensors when ViewModel is destroyed (e.g. back-stack pop).
        controlTracking.stop()
    }
}
