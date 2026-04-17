package com.karamay.app.presentation.more

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
import com.karamay.app.MainActivity
import com.karamay.app.R
import com.karamay.app.core.permission.PermissionDenialTracker
import com.karamay.app.data.receiver.MicroPromptReceiver
import com.karamay.app.domain.model.mood.Valence
import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.repository.InteractionRepository
import com.karamay.app.domain.repository.SleepRepository
import com.karamay.app.domain.usecase.devtools.SeedMockActivityDataUseCase
import com.karamay.app.domain.usecase.devtools.SeedMockMoodDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

val MoreUiState.isActivityPermanentlyDenied: Boolean
    get() = activityDenials >= PermissionDenialTracker.MAX_DENIALS

val MoreUiState.isNotifPermanentlyDenied: Boolean
    get() = notificationDenials >= PermissionDenialTracker.MAX_DENIALS

@HiltViewModel
class MoreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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

    fun triggerTestMicroPrompt() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "MicroPromptChannel",
                "Gentle Check-ins",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Context-aware prompts asking how you are feeling."
                setShowBadge(true)
            }
            nm.createNotificationChannel(channel)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        // Quick action buttons for the notification
        val actions = Valence.entries.mapIndexed { index, valence ->
            val intent = Intent(context, MicroPromptReceiver::class.java).apply {
                action = MicroPromptReceiver.ACTION_SELECT_VALENCE
                putExtra(MicroPromptReceiver.EXTRA_VALENCE, valence.name)
            }
            val pending = PendingIntent.getBroadcast(context, index, intent, flags)

            NotificationCompat.Action.Builder(
                valence.iconRes(),
                valence.displayLabel(),
                pending
            ).build()
        }

        // Tap Intent for deep linking to QuickLogSheet
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("karamay://quicklog")
        }

        val pendingTapIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "MicroPromptChannel")
            .setContentTitle("Karamay is with you")
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