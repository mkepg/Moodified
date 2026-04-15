package com.karamay.app.core.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.repository.InteractionRepository
import com.karamay.app.domain.repository.SleepRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var activityRepository:    ActivityRepository
    @Inject lateinit var sleepRepository:       SleepRepository
    @Inject lateinit var interactionRepository: InteractionRepository

    companion object {
        private const val TAG = "TrackingService"

        const val ACTION_START_ACTIVITY    = "ACTION_START_ACTIVITY"
        const val ACTION_STOP_ACTIVITY     = "ACTION_STOP_ACTIVITY"
        const val ACTION_PAUSE_ACTIVITY    = "ACTION_PAUSE_ACTIVITY"

        const val ACTION_START_SLEEP       = "ACTION_START_SLEEP"
        const val ACTION_STOP_SLEEP        = "ACTION_STOP_SLEEP"
        const val ACTION_PAUSE_SLEEP       = "ACTION_PAUSE_SLEEP"

        const val ACTION_START_INTERACTION = "ACTION_START_INTERACTION"
        const val ACTION_STOP_INTERACTION  = "ACTION_STOP_INTERACTION"
        const val ACTION_PAUSE_INTERACTION = "ACTION_PAUSE_INTERACTION"

        private const val CHANNEL_ID      = "HealthTrackingChannel"
        private const val NOTIFICATION_ID = 404
    }

    private var isActivityTracking    = false
    private var isSleepTracking       = false
    private var isInteractionTracking = false

    private fun hasRequiredPermissions(): Boolean {
        // [FIX APPLIED]: Decoupled ACTIVITY_RECOGNITION. TrackingService only needs Notifications.
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return hasNotif
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "[TRACKING_FLOW] onStartCommand action=${intent?.action}")

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
                // Activity Actions
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

                // Sleep Actions
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

                // Interaction Actions
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

        startServiceForeground()

        if (!isActivityTracking && !isSleepTracking && !isInteractionTracking) {
            Log.d(TAG, "[TRACKING_FLOW] Nothing to track — stopping self.")
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
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

    private fun startServiceForeground() {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Tracking Active")
                .setContentText("Monitoring health and phone interaction signals in the background.")
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
            Log.e(TAG, "SecurityException starting foreground service. Stopping gracefully.", e)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service. Stopping.", e)
            stopSelf()
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