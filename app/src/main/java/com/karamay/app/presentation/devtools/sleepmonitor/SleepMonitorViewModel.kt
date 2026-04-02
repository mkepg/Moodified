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
    val isTracking: Boolean = false,
    val liveSignal: SleepSignal = SleepSignal(),
    val latestSummary: DailySleepSummary? = null,
    val weeklyTrends: SleepTrends? = null,
    val hasPermission: Boolean = false
)

@HiltViewModel
class SleepMonitorViewModel @Inject constructor(
    private val observeLiveSignal: ObserveLiveSleepSignalUseCase,
    private val getDailySummary: GetDailySleepSummaryUseCase,
    private val getWeeklyTrends: GetWeeklySleepTrendsUseCase,
    private val controlTracking: ControlSleepTrackingUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SleepMonitorUiState())
    val state: StateFlow<SleepMonitorUiState> = _state.asStateFlow()

    init {
        // 1. Observe Live Telemetry
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

        // 2. Observe Last Night's Summary
        getDailySummary(LocalDate.now())
            .onEach { summary ->
                _state.update { it.copy(latestSummary = summary) }
            }
            .launchIn(viewModelScope)

        // 3. Observe 7-Day Trends (Consistency & Debt)
        getWeeklyTrends(LocalDate.now())
            .onEach { trends ->
                _state.update { it.copy(weeklyTrends = trends) }
            }
            .launchIn(viewModelScope)

        // 4. Initialize Tracking State
        _state.update { it.copy(isTracking = controlTracking.isTracking) }
    }

    fun onPermissionGranted() {
        _state.update { it.copy(hasPermission = true) }
        startTracking()
    }

    fun startTracking() {
        controlTracking.start()
    }

    fun stopTracking() {
        controlTracking.stop()
    }

    override fun onCleared() {
        super.onCleared()
        // We do NOT stop tracking on cleared because sleep tracking must run overnight
        // while the app is completely backgrounded or closed.
    }
}