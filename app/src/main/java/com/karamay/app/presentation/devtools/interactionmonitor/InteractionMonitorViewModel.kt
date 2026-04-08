package com.karamay.app.presentation.devtools.interactionmonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.domain.model.interaction.InteractionDailySummary
import com.karamay.app.domain.model.interaction.InteractionSignal
import com.karamay.app.domain.model.interaction.InteractionTrends
import com.karamay.app.domain.repository.InteractionRepository
import com.karamay.app.domain.usecase.interaction.GetDailyInteractionSummaryUseCase
import com.karamay.app.domain.usecase.interaction.GetWeeklyInteractionSummariesUseCase
import com.karamay.app.domain.usecase.interaction.GetWeeklyInteractionTrendsUseCase
import com.karamay.app.domain.usecase.interaction.ObserveInteractionSignalUseCase
import com.karamay.app.presentation.devtools.MonitorError
import com.karamay.app.presentation.devtools.MonitorUiState
import com.karamay.app.presentation.devtools.PermissionState
import com.karamay.app.presentation.devtools.midnightTickerFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- Bridge Data Structures to Prevent UI Breakage ---
data class InteractionWeeklyData(
    val trends: InteractionTrends?,
    val summaries: List<InteractionDailySummary>
)

typealias InteractionMonitorUiState = MonitorUiState<InteractionSignal, InteractionDailySummary, InteractionWeeklyData>

val InteractionMonitorUiState.weeklyTrends: InteractionTrends? get() = weeklyData?.trends
val InteractionMonitorUiState.weeklySummaries: List<InteractionDailySummary> get() = weeklyData?.summaries ?: emptyList()
// ---------------------------------------------------

@HiltViewModel
class InteractionMonitorViewModel @Inject constructor(
    private val repository:         InteractionRepository,
    private val observeSignal:      ObserveInteractionSignalUseCase,
    private val getDailySummary:    GetDailyInteractionSummaryUseCase,
    private val getWeeklyTrends:    GetWeeklyInteractionTrendsUseCase,
    private val getWeeklySummaries: GetWeeklyInteractionSummariesUseCase,
) : ViewModel() {

    private val permissionState = MutableStateFlow<PermissionState>(PermissionState.Idle)
    private val errorState = MutableStateFlow<MonitorError?>(null)

    val state: StateFlow<InteractionMonitorUiState> = combine(
        midnightTickerFlow().flatMapLatest { date ->
            combine(
                getDailySummary(date),
                getWeeklyTrends(date),
                getWeeklySummaries(date)
            ) { daily, trends, summaries ->
                Triple(daily, trends, summaries)
            }
        },
        observeSignal(),
        permissionState,
        errorState
    ) { (daily, trends, summaries), signal, perm, err ->
        InteractionMonitorUiState(
            isLoading    = false,
            permission   = perm,
            isTracking   = signal.isTracking,
            liveSignal   = signal,
            todaySummary = daily,
            weeklyData   = InteractionWeeklyData(trends, summaries),
            error        = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InteractionMonitorUiState(
            isTracking = repository.isTracking,
            liveSignal = InteractionSignal(isTracking = repository.isTracking)
        )
    )

    init {
        updatePermissionState()
    }

    fun onResume() {
        viewModelScope.launch(Dispatchers.IO) { repository.flushInteractionDataToDb() }
        updatePermissionState()
    }

    private fun updatePermissionState() {
        if (!repository.hasUsagePermission()) {
            permissionState.value = PermissionState.RequiresSystemSettings
        } else if (permissionState.value is PermissionState.RequiresSystemSettings || permissionState.value is PermissionState.Idle) {
            permissionState.value = if (repository.isTracking) PermissionState.Granted else PermissionState.Idle
        }
    }

    fun onPermissionGranted() { permissionState.value = PermissionState.Granted }
    fun onPermissionDenied(canRequestAgain: Boolean) { permissionState.value = PermissionState.Denied(canRequestAgain) }
    fun onPermissionRequested() { permissionState.value = PermissionState.Requested }

    fun startTracking() {
        val started = repository.startTracking()
        if (!started) {
            if (!repository.hasUsagePermission()) {
                permissionState.value = PermissionState.RequiresSystemSettings
            } else {
                permissionState.value = PermissionState.Denied(true)
            }
        } else {
            errorState.value = null
            permissionState.value = PermissionState.Granted
        }
    }

    fun stopTracking() { repository.stopTracking() }
}