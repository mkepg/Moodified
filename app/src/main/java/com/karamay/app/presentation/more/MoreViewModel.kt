package com.karamay.app.presentation.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.core.permission.PermissionDenialTracker
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
    val isInteractionTracking: Boolean = false,
    val activityDenials: Int = 0,
    val notificationDenials: Int = 0,
)

// [FIX APPLIED]: Decoupled permanent denial checks
val MoreUiState.isActivityPermanentlyDenied: Boolean
    get() = activityDenials >= PermissionDenialTracker.MAX_DENIALS

val MoreUiState.isNotifPermanentlyDenied: Boolean
    get() = notificationDenials >= PermissionDenialTracker.MAX_DENIALS

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val activityRepository:          ActivityRepository,
    private val sleepRepository:             SleepRepository,
    private val interactionRepository:       InteractionRepository,
    private val seedMockMoodDataUseCase:     SeedMockMoodDataUseCase,
    private val seedMockActivityDataUseCase: SeedMockActivityDataUseCase,
    private val permissionDenialTracker:     PermissionDenialTracker,
) : ViewModel() {

    val uiState: StateFlow<MoreUiState> = combine(
        activityRepository.observeSignal(),
        sleepRepository.observeLiveSignal(),
        interactionRepository.observeLiveSignal(),
        permissionDenialTracker.activityRecognitionDenials,
        permissionDenialTracker.postNotificationDenials,
    ) { activity, sleep, interaction, activityDenials, notifDenials ->
        MoreUiState(
            isActivityTracking    = activity.isTracking,
            isSleepTracking       = sleep.isTracking,
            isInteractionTracking = interaction.isTracking,
            activityDenials       = activityDenials,
            notificationDenials   = notifDenials,
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = MoreUiState(
            isActivityTracking    = activityRepository.isTracking,
            isSleepTracking       = sleepRepository.isTracking,
            isInteractionTracking = interactionRepository.isTracking,
            activityDenials       = permissionDenialTracker.activityRecognitionDenials.value,
            notificationDenials   = permissionDenialTracker.postNotificationDenials.value,
        )
    )

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

    fun stopAllTracking() {
        activityRepository.stopTracking()
        sleepRepository.stopTracking()
        interactionRepository.stopTracking()
    }

    fun recordActivityRecognitionDenial() =
        permissionDenialTracker.recordActivityRecognitionDenial()

    fun recordPostNotificationDenial() =
        permissionDenialTracker.recordPostNotificationDenial()

    fun resetActivityRecognitionDenial() =
        permissionDenialTracker.resetActivityRecognition()

    fun resetPostNotificationDenial() =
        permissionDenialTracker.resetPostNotification()

    fun injectMockMoodData() {
        viewModelScope.launch { seedMockMoodDataUseCase() }
    }

    fun injectMockActivityData() {
        viewModelScope.launch { seedMockActivityDataUseCase() }
    }
}