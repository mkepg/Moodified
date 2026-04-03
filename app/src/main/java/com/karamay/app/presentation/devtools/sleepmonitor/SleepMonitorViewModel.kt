package com.karamay.app.presentation.devtools.sleepmonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.domain.model.DailySleepSummary
import com.karamay.app.domain.model.SleepSignal
import com.karamay.app.domain.model.SleepTrends
import com.karamay.app.domain.usecase.sleep.ControlSleepTrackingUseCase
import com.karamay.app.domain.usecase.sleep.GetDailySleepSummaryUseCase
import com.karamay.app.domain.usecase.sleep.GetWeeklySleepTrendsUseCase
import com.karamay.app.domain.usecase.sleep.ObserveLiveSleepSignalUseCase
import com.karamay.app.presentation.devtools.PermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

data class SleepMonitorUiState(
    val permission:    PermissionState    = PermissionState.Idle,
    val isTracking:    Boolean            = false,
    val liveSignal:    SleepSignal        = SleepSignal(),
    val latestSummary: DailySleepSummary? = null,
    val weeklyTrends:  SleepTrends?       = null
)

@HiltViewModel
class SleepMonitorViewModel @Inject constructor(
    private val observeLiveSignal: ObserveLiveSleepSignalUseCase,
    private val getDailySummary:   GetDailySleepSummaryUseCase,
    private val getWeeklyTrends:   GetWeeklySleepTrendsUseCase,
    private val controlTracking:   ControlSleepTrackingUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SleepMonitorUiState())
    val state: StateFlow<SleepMonitorUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(isTracking = controlTracking.isTracking) }

        observeLiveSignal()
            .onEach { signal ->
                _state.update {
                    it.copy(
                        liveSignal = signal,
                        isTracking = signal.isTracking
                    )
                }
            }
            .launchIn(viewModelScope)

        getDailySummary(LocalDate.now())
            .onEach { summary -> _state.update { it.copy(latestSummary = summary) } }
            .launchIn(viewModelScope)

        getWeeklyTrends(LocalDate.now())
            .onEach { trends -> _state.update { it.copy(weeklyTrends = trends) } }
            .launchIn(viewModelScope)
    }

    /** Called by the screen after the system dialog returns granted. */
    fun onPermissionGranted() {
        _state.update { it.copy(permission = PermissionState.Granted) }
        // startTracking() is intentionally NOT called here — the screen calls it
        // immediately after onPermissionGranted(), keeping the same pattern as
        // ActivityMonitorViewModel so both screens are symmetric.
    }

    fun onPermissionDenied(canAskAgain: Boolean) {
        _state.update { it.copy(permission = PermissionState.Denied(canAskAgain)) }
    }

    fun onPermissionRequested() {
        _state.update { it.copy(permission = PermissionState.Requested) }
    }

    fun startTracking() {
        controlTracking.start()
        _state.update { it.copy(isTracking = true) }
    }

    fun stopTracking() {
        controlTracking.stop()
        _state.update { it.copy(isTracking = false) }
    }
}