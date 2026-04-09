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

data class InteractionWeeklyData(
    val trends: InteractionTrends?,
    val summaries: List<InteractionDailySummary>
)

typealias InteractionMonitorUiState = MonitorUiState<InteractionSignal, InteractionDailySummary, InteractionWeeklyData>

val InteractionMonitorUiState.weeklyTrends: InteractionTrends? get() = weeklyData?.trends
val InteractionMonitorUiState.weeklySummaries: List<InteractionDailySummary> get() = weeklyData?.summaries ?: emptyList()

@HiltViewModel
class InteractionMonitorViewModel @Inject constructor(
    private val repository:         InteractionRepository,
    private val observeSignal:      ObserveInteractionSignalUseCase,
    private val getDailySummary:    GetDailyInteractionSummaryUseCase,
    private val getWeeklyTrends:    GetWeeklyInteractionTrendsUseCase,
    private val getWeeklySummaries: GetWeeklyInteractionSummariesUseCase,
) : ViewModel() {

    private val permissionState = MutableStateFlow<PermissionState>(PermissionState.Idle)
    private val errorState      = MutableStateFlow<MonitorError?>(null)

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
            // FIX #1 (UI Flickering): isLoading is omitted here, so it uses
            // the new default of `false` (see DevToolsState.kt). The
            // initialValue below already carries real data from the repository,
            // so there is never a genuine loading phase that should blank the UI.
            permission   = perm,
            isTracking   = signal.isTracking,
            liveSignal   = signal,
            todaySummary = daily,
            weeklyData   = InteractionWeeklyData(trends, summaries),
            error        = err
        )
    }.stateIn(
        scope        = viewModelScope,
        // FIX #1 (UI Flickering): WhileSubscribed(5_000) means the upstream
        // is kept alive for 5 s after the last subscriber leaves (e.g. during
        // the brief navigation transition between monitors). The StateFlow
        // replays its last value immediately to the new subscriber, so no
        // reset-to-zero flicker occurs on re-entry.
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = InteractionMonitorUiState(
            // Seed the initial value from persisted repository state so the
            // very first frame shows real data, not zeroes.
            isTracking = repository.isTracking,
            liveSignal = InteractionSignal(isTracking = repository.isTracking)
        )
    )

    init {
        // FIX #2 (Incorrect Tracking Lifecycle): updatePermissionState() is
        // called in init only to synchronise the permission UI badge (e.g. show
        // the "Usage Access Required" card if the OS permission is absent).
        // It no longer triggers tracking automatically — tracking is strictly
        // started by an explicit user tap via startTracking().
        updatePermissionState()
    }

    /**
     * Called on every ON_RESUME from the screen (returning from the OS
     * permission settings page, switching back from another app, etc.).
     *
     * FIX #2 (Incorrect Tracking Lifecycle): Previously this called
     * flushInteractionDataToDb() unconditionally, which caused UsageStats to
     * be polled even when tracking was off. Now the flush is only performed
     * when tracking is already active, keeping data collection fully under
     * user control.
     */
    fun onResume() {
        updatePermissionState()
        if (repository.isTracking) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.flushInteractionDataToDb()
            }
        }
    }

    /**
     * FIX #2 (Incorrect Tracking Lifecycle): Permission state is updated
     * independently of tracking state.
     *
     * Old logic set permissionState = Granted when the OS permission was
     * present AND tracking happened to be on — effectively making
     * "permission granted" a proxy for "start tracking". That coupling caused
     * the repository's poll loop to be started as a side-effect of the
     * permission check.
     *
     * New logic: permission state only reflects the OS-level permission
     * situation. Tracking state is managed exclusively by startTracking() /
     * stopTracking().
     */
    private fun updatePermissionState() {
        if (!repository.hasUsagePermission()) {
            permissionState.value = PermissionState.RequiresSystemSettings
        } else if (permissionState.value is PermissionState.RequiresSystemSettings
            || permissionState.value is PermissionState.Idle
        ) {
            // Permission is available but we do NOT start tracking here.
            // Keep the permission state as Idle so the UI shows the
            // "Start Tracking" button rather than auto-launching the poll loop.
            permissionState.value = PermissionState.Idle
        }
        // If the state is already Granted/Denied/Requested, leave it unchanged
        // to avoid resetting mid-flow.
    }

    // FIX #2 (Incorrect Tracking Lifecycle): onPermissionGranted() no longer
    // calls startTracking(). The screen's permissionLauncher callback was
    // previously doing:
    //   viewModel.onPermissionGranted()
    //   viewModel.startTracking()          ← auto-start on grant
    // The screen has been updated to only call onPermissionGranted() and let
    // the user press "Start Tracking" explicitly. This function now purely
    // reflects the OS grant in the UI state.
    fun onPermissionGranted() {
        permissionState.value = PermissionState.Granted
    }

    fun onPermissionDenied(canRequestAgain: Boolean) {
        permissionState.value = PermissionState.Denied(canRequestAgain)
    }

    fun onPermissionRequested() {
        permissionState.value = PermissionState.Requested
    }

    fun startTracking() {
        val started = repository.startTracking()
        if (!started) {
            permissionState.value = if (!repository.hasUsagePermission())
                PermissionState.RequiresSystemSettings
            else
                PermissionState.Denied(true)
        } else {
            errorState.value      = null
            permissionState.value = PermissionState.Granted
        }
    }

    fun stopTracking() {
        repository.stopTracking()
        // FIX #2: After an explicit stop, revert the permission state back to
        // Idle (permission still present, but tracking is not active). This
        // ensures the UI correctly shows "Start Tracking" again without needing
        // to re-navigate.
        if (repository.hasUsagePermission()) {
            permissionState.value = PermissionState.Idle
        }
    }
}