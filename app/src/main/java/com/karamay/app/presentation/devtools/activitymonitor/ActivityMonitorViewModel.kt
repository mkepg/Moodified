package com.karamay.app.presentation.devtools.activitymonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.domain.model.ActivitySignal
import com.karamay.app.domain.usecase.activity.ControlActivityTrackingUseCase
import com.karamay.app.domain.usecase.activity.ObserveActivitySignalUseCase
import com.karamay.app.presentation.devtools.PermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ActivityMonitorUiState(
    val permission: PermissionState = PermissionState.Idle,
    val isTracking: Boolean         = false,
    val signal: ActivitySignal      = ActivitySignal(),
    val hardwareError: String?      = null
)

@HiltViewModel
class ActivityMonitorViewModel @Inject constructor(
    private val observeSignal:    ObserveActivitySignalUseCase,
    private val controlTracking:  ControlActivityTrackingUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityMonitorUiState())
    val state: StateFlow<ActivityMonitorUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(isTracking = controlTracking.isTracking) }
        observeSignal()
            .onEach { signal -> _state.update { it.copy(signal = signal) } }
            .launchIn(viewModelScope)
    }

    fun onPermissionGranted() {
        _state.update { it.copy(permission = PermissionState.Granted) }
    }

    fun onPermissionDenied(canAskAgain: Boolean) {
        _state.update { it.copy(permission = PermissionState.Denied(canAskAgain)) }
    }

    fun onPermissionRequested() {
        _state.update { it.copy(permission = PermissionState.Requested) }
    }

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
}