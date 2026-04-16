package com.karamay.app.core.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.karamay.app.R
import com.karamay.app.data.receiver.MicroPromptReceiver
import com.karamay.app.domain.model.activity.ActivityIntensity
import com.karamay.app.domain.model.mood.Valence
import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.repository.InteractionRepository
import com.karamay.app.domain.repository.MoodRepository
import com.karamay.app.domain.repository.SleepRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var activityRepository:    ActivityRepository
    @Inject lateinit var sleepRepository:       SleepRepository
    @Inject lateinit var interactionRepository: InteractionRepository
    @Inject lateinit var moodRepository:        MoodRepository

    companion object {
        private const val TAG = "TrackingService"

        // Original Tracking Actions
        const val ACTION_START_ACTIVITY    = "ACTION_START_ACTIVITY"
        const val ACTION_STOP_ACTIVITY     = "ACTION_STOP_ACTIVITY"
        const val ACTION_PAUSE_ACTIVITY    = "ACTION_PAUSE_ACTIVITY"
        const val ACTION_START_SLEEP       = "ACTION_START_SLEEP"
        const val ACTION_STOP_SLEEP        = "ACTION_STOP_SLEEP"
        const val ACTION_PAUSE_SLEEP       = "ACTION_PAUSE_SLEEP"
        const val ACTION_START_INTERACTION = "ACTION_START_INTERACTION"
        const val ACTION_STOP_INTERACTION  = "ACTION_STOP_INTERACTION"
        const val ACTION_PAUSE_INTERACTION = "ACTION_PAUSE_INTERACTION"

        // Notification Config
        private const val CHANNEL_ID             = "HealthTrackingChannel"
        private const val PROMPT_CHANNEL_ID      = "MicroPromptChannel"
        private const val NOTIFICATION_ID        = 404

        // Persistence Keys for Prompts
        private const val PREFS_PROMPT_NAME      = "micro_prompt_prefs"
        private const val KEY_LAST_PROMPT_DATE   = "last_prompt_date"
    }

    // Lifecycle Flags
    private var isActivityTracking    = false
    private var isSleepTracking       = false
    private var isInteractionTracking = false

    // State Machine for Context Awareness
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastUnlockCount = -1
    private var isCoolingDown = false
    private var localPromptDateCache = ""

    @Volatile private var isEvaluatingPrompt = false

    private fun hasRequiredPermissions(): Boolean {
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        return hasNotif
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannels()
        }
        // Launch the context-aware observation engine
        observeContextForPrompt()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "[TRACKING_FLOW] onStartCommand action=${intent?.action}")

        // Safety check for permissions
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "[TRACKING_FLOW] Missing notifications permission. Stopping service and trackers.")
            isActivityTracking = false
            isSleepTracking = false
            isInteractionTracking = false
            activityRepository.stopTracking()
            sleepRepository.stopTracking()
            interactionRepository.stopTracking()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent == null) {
            Log.d(TAG, "[TRACKING_FLOW] OS restart — restoring.")
            restoreStateAndResume()
        } else {
            when (intent.action) {
                ACTION_START_ACTIVITY -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
                        isActivityTracking = true
                        activityRepository.startTracking()
                        Log.d(TAG, "[TRACKING_FLOW] Activity tracking ACTIVE.")
                    } else {
                        Log.w(TAG, "[TRACKING_FLOW] Activity permission missing. Gracefully paused.")
                        isActivityTracking = false
                        activityRepository.pauseTracking()
                    }
                }
                ACTION_STOP_ACTIVITY -> {
                    isActivityTracking = false
                    activityRepository.stopTracking()
                    Log.d(TAG, "[TRACKING_FLOW] Activity tracking STOPPED.")
                }
                ACTION_PAUSE_ACTIVITY -> {
                    isActivityTracking = false
                    activityRepository.pauseTracking()
                    Log.d(TAG, "[TRACKING_FLOW] Activity tracking PAUSED (Intent preserved).")
                }
                ACTION_START_SLEEP -> {
                    if (sleepRepository.hasUsagePermission()) {
                        isSleepTracking = true
                        sleepRepository.startTracking()
                        Log.d(TAG, "[TRACKING_FLOW] Sleep tracking ACTIVE.")
                    } else {
                        Log.w(TAG, "[TRACKING_FLOW] Usage access missing. Sleep Tracker paused.")
                        isSleepTracking = false
                        sleepRepository.pauseTracking()
                    }
                }
                ACTION_STOP_SLEEP -> {
                    isSleepTracking = false
                    sleepRepository.stopTracking()
                    Log.d(TAG, "[TRACKING_FLOW] Sleep tracking STOPPED.")
                }
                ACTION_PAUSE_SLEEP -> {
                    isSleepTracking = false
                    sleepRepository.pauseTracking()
                    Log.d(TAG, "[TRACKING_FLOW] Sleep tracking PAUSED (Intent preserved).")
                }
                ACTION_START_INTERACTION -> {
                    if (interactionRepository.hasUsagePermission()) {
                        isInteractionTracking = true
                        interactionRepository.startTracking()
                        Log.d(TAG, "[TRACKING_FLOW] Interaction tracking ACTIVE.")
                    } else {
                        Log.w(TAG, "[TRACKING_FLOW] Usage access missing. Interaction Tracker paused.")
                        isInteractionTracking = false
                        interactionRepository.pauseTracking()
                    }
                }
                ACTION_STOP_INTERACTION -> {
                    isInteractionTracking = false
                    interactionRepository.stopTracking()
                    Log.d(TAG, "[TRACKING_FLOW] Interaction tracking STOPPED.")
                }
                ACTION_PAUSE_INTERACTION -> {
                    isInteractionTracking = false
                    interactionRepository.pauseTracking()
                    Log.d(TAG, "[TRACKING_FLOW] Interaction tracking PAUSED (Intent preserved).")
                }
            }
        }

        // Always ensure foreground status is updated
        startServiceForeground()

        // Shutdown if nothing is active
        if (!isActivityTracking && !isSleepTracking && !isInteractionTracking) {
            Log.d(TAG, "[TRACKING_FLOW] Nothing to track — stopping self.")
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "[TRACKING_FLOW] Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun restoreStateAndResume() {
        if (activityRepository.isTracking) {
            isActivityTracking = true
            activityRepository.startTracking()
        }
        if (sleepRepository.isTracking) {
            isSleepTracking = true
            sleepRepository.startTracking()
        }
        if (interactionRepository.isTracking) {
            isInteractionTracking = true
            interactionRepository.startTracking()
        }
    }

    /**
     * Contextual Prompt Engine:
     * Robust, non-blocking observation of live signal transitions.
     */
    private fun observeContextForPrompt() {
        serviceScope.launch {
            combine(
                activityRepository.observeSignal(),
                interactionRepository.observeLiveSignal()
            ) { activity, interaction ->
                activity to interaction
            }.collect { (activity, interaction) ->

                // Concurrency Guard
                if (isEvaluatingPrompt) return@collect

                val now = LocalDateTime.now()
                val todayStr = now.toLocalDate().toString()
                val hour = now.hour

                // Cache Initialization
                if (localPromptDateCache.isEmpty()) {
                    val prefs = getSharedPreferences(PREFS_PROMPT_NAME, Context.MODE_PRIVATE)
                    localPromptDateCache = prefs.getString(KEY_LAST_PROMPT_DATE, "") ?: ""
                }
                if (localPromptDateCache == todayStr) return@collect

                // Handle midnight reset for unlock counters
                if (lastUnlockCount == -1 || interaction.unlockCount < lastUnlockCount) {
                    lastUnlockCount = interaction.unlockCount
                }

                // Hard Blocks: Protect sleep hours and transit
                if (hour < 7 || hour > 22) {
                    isCoolingDown = false
                    return@collect
                }
                if (activity.intensity == ActivityIntensity.IN_VEHICLE) {
                    isCoolingDown = false
                    return@collect
                }

                var shouldPrompt = false
                var promptMessage = ""

                // Trigger 1: Post-Activity Cool-down
                if (activity.intensity == ActivityIntensity.MODERATE || activity.intensity == ActivityIntensity.VIGOROUS) {
                    isCoolingDown = true
                } else if (isCoolingDown && activity.intensity == ActivityIntensity.SEDENTARY) {
                    isCoolingDown = false
                    shouldPrompt = true
                    promptMessage = "You've just finished moving. How is your energy?"
                }

                // Trigger 2: The Settle In
                if (interaction.unlockCount > lastUnlockCount) {
                    lastUnlockCount = interaction.unlockCount
                    if (!shouldPrompt && activity.intensity == ActivityIntensity.SEDENTARY && activity.sedentaryMinutes > 15) {
                        shouldPrompt = true
                        promptMessage = "You've been resting for a bit. How are you feeling?"
                    }
                }

                // Fire Prompt: Decoupled DB Check to prevent stream lag
                if (shouldPrompt) {
                    isEvaluatingPrompt = true

                    serviceScope.launch {
                        try {
                            val entriesToday = moodRepository.getEntriesForDate(now.toLocalDate()).first()

                            // Prevent I/O leaks by caching the date regardless of outcome
                            localPromptDateCache = todayStr
                            val prefs = getSharedPreferences(PREFS_PROMPT_NAME, Context.MODE_PRIVATE)
                            prefs.edit().putString(KEY_LAST_PROMPT_DATE, todayStr).apply()

                            if (entriesToday.isEmpty()) {
                                triggerMicroPromptNotification(promptMessage)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed verification for micro-prompt", e)
                        } finally {
                            isEvaluatingPrompt = false
                        }
                    }
                }
            }
        }
    }

    private fun triggerMicroPromptNotification(contextMessage: String) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        // Stage 1 Actions: Valence Selection (Standard App Image Resources)
        val actions = Valence.entries.mapIndexed { index, valence ->
            val intent = Intent(this, MicroPromptReceiver::class.java).apply {
                action = MicroPromptReceiver.ACTION_SELECT_VALENCE
                putExtra(MicroPromptReceiver.EXTRA_VALENCE, valence.name)
            }
            val pending = PendingIntent.getBroadcast(this, index, intent, flags)
            NotificationCompat.Action.Builder(
                valence.iconRes(),
                valence.displayLabel(),
                pending
            ).build()
        }

        val notification = NotificationCompat.Builder(this, PROMPT_CHANNEL_ID)
            .setContentTitle("Karamay is with you")
            .setContentText(contextMessage)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .apply { actions.forEach { addAction(it) } }
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(MicroPromptReceiver.PROMPT_NOTIFICATION_ID, notification)
    }

    private fun startServiceForeground() {
        try {
            // Empathetic companion wording update
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Karamay is with you")
                .setContentText("Quietly learning your daily rhythms to support your well-being.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting foreground service.", e)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service.", e)
            stopSelf()
        }
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return

        // Tracking Channel
        val trackingChannel = NotificationChannel(
            CHANNEL_ID,
            "Health & Interaction Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains background tracking for activity, sleep, and phone interactions."
            setShowBadge(false)
        }

        // Micro-Prompt Channel
        val promptChannel = NotificationChannel(
            PROMPT_CHANNEL_ID,
            "Gentle Check-ins",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Context-aware prompts asking how you are feeling."
            setShowBadge(true)
        }

        nm.createNotificationChannel(trackingChannel)
        nm.createNotificationChannel(promptChannel)
    }
}