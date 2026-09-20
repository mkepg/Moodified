package com.moodified.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.core.coordination.TrackingCoordinator
import com.moodified.app.core.permission.PermissionDenialTracker
import com.moodified.app.domain.repository.ActivityRepository
import com.moodified.app.domain.repository.InteractionRepository
import com.moodified.app.domain.repository.NotificationHistoryRepository
import com.moodified.app.domain.repository.SleepRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ProfileUiState(
    val isActivityTracking: Boolean = false,
    val isSleepTracking: Boolean = false,
    val isInteractionTracking: Boolean = false,
    val activityDenials: Int = 0,
    val notificationDenials: Int = 0,
    val unreadNotificationCount: Int = 0,
)

val ProfileUiState.isActivityPermanentlyDenied: Boolean
    get() = activityDenials >= PermissionDenialTracker.MAX_DENIALS

val ProfileUiState.isNotifPermanentlyDenied: Boolean
    get() = notificationDenials >= PermissionDenialTracker.MAX_DENIALS

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        private val activityRepository: ActivityRepository,
        private val sleepRepository: SleepRepository,
        private val interactionRepository: InteractionRepository,
        private val permissionDenialTracker: PermissionDenialTracker,
        private val notificationHistoryRepository: NotificationHistoryRepository,
        private val trackingCoordinator: TrackingCoordinator,
    ) : ViewModel() {
        val uiState: StateFlow<ProfileUiState> =
            combine(
                activityRepository.observeSignal(),
                sleepRepository.observeLiveSignal(),
                interactionRepository.observeLiveSignal(),
                permissionDenialTracker.activityRecognitionDenials,
                permissionDenialTracker.postNotificationDenials,
                notificationHistoryRepository.observeUnreadCount(),
            ) { values ->
                ProfileUiState(
                    isActivityTracking = (values[0] as com.moodified.app.domain.model.activity.ActivitySignal).isTracking,
                    isSleepTracking = (values[1] as com.moodified.app.domain.model.sleep.SleepSignal).isTracking,
                    isInteractionTracking = (values[2] as com.moodified.app.domain.model.interaction.InteractionSignal).isTracking,
                    activityDenials = values[3] as Int,
                    notificationDenials = values[4] as Int,
                    unreadNotificationCount = values[5] as Int,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue =
                    ProfileUiState(
                        isActivityTracking = activityRepository.isTracking,
                        isSleepTracking = sleepRepository.isTracking,
                        isInteractionTracking = interactionRepository.isTracking,
                        activityDenials = permissionDenialTracker.activityRecognitionDenials.value,
                        notificationDenials = permissionDenialTracker.postNotificationDenials.value,
                        unreadNotificationCount = 0,
                    ),
            )

        val hasUsageAccess: Boolean
            get() = interactionRepository.hasUsagePermission()

        fun setActivityTracking(enabled: Boolean) {
            if (enabled) trackingCoordinator.startActivity() else trackingCoordinator.stopActivity()
        }

        fun setSleepTracking(enabled: Boolean) {
            if (enabled) trackingCoordinator.startSleep() else trackingCoordinator.stopSleep()
        }

        fun setInteractionTracking(enabled: Boolean) {
            if (enabled) trackingCoordinator.startInteraction() else trackingCoordinator.stopInteraction()
        }

        fun stopAllTracking() {
            trackingCoordinator.stopActivity()
            trackingCoordinator.stopSleep()
            trackingCoordinator.stopInteraction()
        }

        fun recordActivityRecognitionDenial() = permissionDenialTracker.recordActivityRecognitionDenial()

        fun recordPostNotificationDenial() = permissionDenialTracker.recordPostNotificationDenial()

        fun resetActivityRecognitionDenial() = permissionDenialTracker.resetActivityRecognition()

        fun resetPostNotificationDenial() = permissionDenialTracker.resetPostNotification()
    }
