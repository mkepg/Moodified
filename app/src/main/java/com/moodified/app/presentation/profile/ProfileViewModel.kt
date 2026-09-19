package com.moodified.app.presentation.profile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.MainActivity
import com.moodified.app.R
import com.moodified.app.core.devtools.MockDataSeeder
import com.moodified.app.core.permission.PermissionDenialTracker
import com.moodified.app.data.receiver.MicroPromptReceiver
import com.moodified.app.domain.model.mood.Valence
import com.moodified.app.domain.repository.ActivityRepository
import com.moodified.app.domain.repository.InteractionRepository
import com.moodified.app.domain.repository.NotificationHistoryRepository
import com.moodified.app.domain.repository.SleepRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
        @ApplicationContext private val context: Context,
        private val activityRepository: ActivityRepository,
        private val sleepRepository: SleepRepository,
        private val interactionRepository: InteractionRepository,
        private val mockDataSeeder: MockDataSeeder,
        private val permissionDenialTracker: PermissionDenialTracker,
        private val notificationHistoryRepository: NotificationHistoryRepository,
    ) : ViewModel() {
        val isMockDataAvailable: Boolean get() = mockDataSeeder.isAvailable

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

        fun recordActivityRecognitionDenial() = permissionDenialTracker.recordActivityRecognitionDenial()

        fun recordPostNotificationDenial() = permissionDenialTracker.recordPostNotificationDenial()

        fun resetActivityRecognitionDenial() = permissionDenialTracker.resetActivityRecognition()

        fun resetPostNotificationDenial() = permissionDenialTracker.resetPostNotification()

        fun injectMockMoodData() {
            viewModelScope.launch { mockDataSeeder.seedMoodData() }
        }

        fun injectMockActivityData() {
            viewModelScope.launch { mockDataSeeder.seedActivityData() }
        }

        fun triggerTestMicroPrompt() {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(
                        "MicroPromptChannel",
                        "Gentle Check-ins",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = "Context-aware prompts asking how you are feeling."
                        setShowBadge(true)
                    }
                nm.createNotificationChannel(channel)
            }

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            // Quick action buttons for the notification
            val actions =
                Valence.entries.mapIndexed { index, valence ->
                    val intent =
                        Intent(context, MicroPromptReceiver::class.java).apply {
                            action = MicroPromptReceiver.ACTION_SELECT_VALENCE
                            putExtra(MicroPromptReceiver.EXTRA_VALENCE, valence.name)
                        }
                    val pending = PendingIntent.getBroadcast(context, index, intent, flags)

                    NotificationCompat.Action.Builder(
                        valence.iconRes(),
                        valence.displayLabel(),
                        pending,
                    ).build()
                }

            // Tap Intent for deep linking to QuickLogSheet
            val tapIntent =
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("moodified://quicklog")
                }

            val pendingTapIntent =
                PendingIntent.getActivity(
                    context,
                    0,
                    tapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val notification =
                NotificationCompat.Builder(context, "MicroPromptChannel")
                    .setContentTitle("Moodified is with you")
                    .setContentText("You've been resting for a bit. How are you feeling?")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentIntent(pendingTapIntent) // Added Deep Link here
                    .apply { actions.forEach { addAction(it) } }
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()

            nm.notify(MicroPromptReceiver.PROMPT_NOTIFICATION_ID, notification)
        }
    }
