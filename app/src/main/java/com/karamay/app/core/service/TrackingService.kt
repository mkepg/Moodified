package com.karamay.app.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.karamay.app.R
import com.karamay.app.data.receiver.interaction.InteractionReceiver
import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.repository.InteractionRepository
import com.karamay.app.domain.repository.SleepRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var activityRepository: ActivityRepository
    @Inject lateinit var sleepRepository: SleepRepository
    @Inject lateinit var interactionRepository: InteractionRepository

    companion object {
        private const val TAG = "TrackingService"

        // Activity Actions
        const val ACTION_START_ACTIVITY = "ACTION_START_ACTIVITY"
        const val ACTION_STOP_ACTIVITY  = "ACTION_STOP_ACTIVITY"

        // Sleep Actions
        const val ACTION_START_SLEEP = "ACTION_START_SLEEP"
        const val ACTION_STOP_SLEEP  = "ACTION_STOP_SLEEP"

        // Interaction Actions
        const val ACTION_START_INTERACTION = "ACTION_START_INTERACTION"
        const val ACTION_STOP_INTERACTION  = "ACTION_STOP_INTERACTION"

        private const val CHANNEL_ID = "HealthTrackingChannel"
        private const val NOTIFICATION_ID = 404
    }

    private var isActivityTracking = false
    private var isSleepTracking = false
    private var isInteractionTracking = false

    private var interactionReceiver: InteractionReceiver? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            Log.d(TAG, "Restarted by OS (START_STICKY) — restoring from prefs.")
            restoreStateAndResume()
        } else {
            Log.d(TAG, "Received action: ${intent.action}")
            when (intent.action) {
                ACTION_START_ACTIVITY -> {
                    isActivityTracking = true
                    activityRepository.startTracking()
                }
                ACTION_STOP_ACTIVITY -> {
                    isActivityTracking = false
                    activityRepository.stopTracking()
                }
                ACTION_START_SLEEP -> {
                    isSleepTracking = true
                    sleepRepository.startTracking()
                }
                ACTION_STOP_SLEEP -> {
                    isSleepTracking = false
                    sleepRepository.stopTracking()
                }
                ACTION_START_INTERACTION -> {
                    isInteractionTracking = true
                    interactionRepository.startTracking()
                    registerInteractionReceiver()
                }
                ACTION_STOP_INTERACTION -> {
                    isInteractionTracking = false
                    interactionRepository.stopTracking()
                    unregisterInteractionReceiver()
                }
                else -> Log.w(TAG, "Unknown action: ${intent.action}")
            }
        }

        // Keep foreground service alive if ANY tracker is active
        if (activityRepository.isTracking || sleepRepository.isTracking || interactionRepository.isTracking) {
            startServiceForeground()
        } else {
            Log.d(TAG, "Nothing to track — stopping service.")
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_STICKY
    }

    private fun registerInteractionReceiver() {
        if (interactionReceiver != null) return // Already registered

        Log.d(TAG, "Registering dynamic InteractionReceiver")
        interactionReceiver = InteractionReceiver(interactionRepository)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SHUTDOWN) // Handle device shutdown
        }

        // System broadcasts require RECEIVER_EXPORTED on modern Android versions
        ContextCompat.registerReceiver(
            this,
            interactionReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun unregisterInteractionReceiver() {
        interactionReceiver?.let {
            Log.d(TAG, "Unregistering dynamic InteractionReceiver")
            try {
                unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver was not registered: ${e.message}")
            }
            interactionReceiver = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterInteractionReceiver()
        Log.d(TAG, "Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun restoreStateAndResume() {
        val wasActivityTracking = activityRepository.isTracking
        val wasSleepTracking = sleepRepository.isTracking
        val wasInteractionTracking = interactionRepository.isTracking

        Log.d(TAG, "Restoring: activity=$wasActivityTracking sleep=$wasSleepTracking interaction=$wasInteractionTracking")

        if (wasActivityTracking) {
            isActivityTracking = true
            activityRepository.startTracking()
        }
        if (wasSleepTracking) {
            isSleepTracking = true
            sleepRepository.startTracking()
        }
        if (wasInteractionTracking) {
            isInteractionTracking = true
            interactionRepository.startTracking()
            registerInteractionReceiver()
        }

        if (!wasActivityTracking && !wasSleepTracking && !wasInteractionTracking) {
            Log.d(TAG, "Nothing was running before kill — stopping service cleanly.")
        }
    }

    private fun startServiceForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracking Active")
            .setContentText("Monitoring health and phone interaction signals in the background.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Health & Interaction Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains background tracking for activity, sleep, and phone interactions."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}