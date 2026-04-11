// app/src/main/java/com/karamay/app/presentation/more/MoreViewModel.kt
package com.karamay.app.presentation.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.repository.InteractionRepository
import com.karamay.app.domain.repository.SleepRepository
import com.karamay.app.domain.usecase.devtools.SeedMockActivityDataUseCase
import com.karamay.app.domain.usecase.devtools.SeedMockMoodDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MoreUiState(
    val isActivityTracking: Boolean = false,
    val isSleepTracking: Boolean = false,
    val isInteractionTracking: Boolean = false
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val sleepRepository: SleepRepository,
    private val interactionRepository: InteractionRepository,
    private val seedMockMoodDataUseCase: SeedMockMoodDataUseCase,
    private val seedMockActivityDataUseCase: SeedMockActivityDataUseCase
) : ViewModel() {

    val uiState: StateFlow<MoreUiState> = combine(
        activityRepository.observeSignal(),
        sleepRepository.observeLiveSignal(),
        interactionRepository.observeLiveSignal()
    ) { activity, sleep, interaction ->
        MoreUiState(
            isActivityTracking    = activity.isTracking,
            isSleepTracking       = sleep.isTracking,
            isInteractionTracking = interaction.isTracking
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = MoreUiState(
            isActivityTracking    = activityRepository.isTracking,
            isSleepTracking       = sleepRepository.isTracking,
            isInteractionTracking = interactionRepository.isTracking
        )
    )

    // Expose the Usage Stats check for the Sleep and Interaction toggles
    val hasUsageAccess: Boolean
        get() = interactionRepository.hasUsagePermission()

    fun setActivityTracking(enabled: Boolean) {
        if (enabled) activityRepository.startTracking() else activityRepository.stopTracking()
    }

    fun setSleepTracking(enabled: Boolean) {
        if (enabled) sleepRepository.startTracking() else sleepRepository.stopTracking()
    }

    fun setInteractionTracking(enabled: Boolean) {
        if (enabled) interactionRepository.startTracking() else interactionRepository.stopTracking()
    }

    fun injectMockMoodData() {
        viewModelScope.launch { seedMockMoodDataUseCase() }
    }

    fun injectMockActivityData() {
        viewModelScope.launch { seedMockActivityDataUseCase() }
    }
}