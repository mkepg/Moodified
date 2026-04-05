package com.karamay.app.presentation.devtools.activitymonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.domain.model.ActivitySignal
import com.karamay.app.domain.repository.ActivityRepository
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
    val permission:    PermissionState = PermissionState.Idle,
    val isTracking:    Boolean         = false,
    val signal:        ActivitySignal  = ActivitySignal(),
    val hardwareError: String?         = null,
)

@HiltViewModel
class ActivityMonitorViewModel @Inject constructor(
    // REPLACED: Injected the Repository directly instead of the deleted Use Cases
    private val repository: ActivityRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityMonitorUiState())
    val state: StateFlow<ActivityMonitorUiState> = _state.asStateFlow()

    init {
        // Seed isTracking from the repository on startup — same pattern as SleepMonitorViewModel.
        _state.update { it.copy(isTracking = repository.isTracking) }

        // Fix A: isTracking is now derived from signal.isTracking carried through
        // ActivitySignalBus, exactly mirroring how SleepMonitorViewModel derives
        // isTracking from signal.isTracking via SleepSignalBus. This eliminates
        // the optimistic setState(isTracking = true) that could diverge from reality
        // when the sensor registration fails asynchronously.
        repository.observeSignal()
            .onEach { signal ->
                _state.update {
                    it.copy(signal = signal, isTracking = signal.isTracking)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onPermissionGranted() {
        _state.update { it.copy(permission = PermissionState.Granted) }
    }

    // Fix C: parameter renamed from canAskAgain to canRequestAgain to match the
    // updated DevToolsState.PermissionState.Denied(canRequestAgain) field name.
    fun onPermissionDenied(canRequestAgain: Boolean) {
        _state.update { it.copy(permission = PermissionState.Denied(canRequestAgain)) }
    }

    fun onPermissionRequested() {
        _state.update { it.copy(permission = PermissionState.Requested) }
    }

    fun startTracking() {
        // Fix A: No longer sets isTracking = true here optimistically. The repository
        // calls ActivitySignalBus.setTrackingState(true) on confirmed start, which
        // flows back through observeSignal() and updates the UI reactively — confirmed,
        // not assumed. Hardware sensor absence is still surfaced via hardwareError below.
        val started = repository.startTracking()
        if (!started) {
            _state.update {
                it.copy(hardwareError = "No compatible sensors found on this device.")
            }
        } else {
            _state.update { it.copy(hardwareError = null) }
        }
    }

    fun stopTracking() {
        // Fix A: No longer sets isTracking = false here directly. ActivityRepositoryImpl
        // calls ActivitySignalBus.setTrackingState(false) synchronously in stopTracking(),
        // so the Flow emission arrives within the same turn and the UI updates reactively.
        repository.stopTracking()
    }
}