package com.karamay.app.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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
        const val ACTION_START_SLEEP       = "ACTION_START_SLEEP"
        const val ACTION_STOP_SLEEP        = "ACTION_STOP_SLEEP"
        const val ACTION_START_INTERACTION = "ACTION_START_INTERACTION"
        const val ACTION_STOP_INTERACTION  = "ACTION_STOP_INTERACTION"

        private const val CHANNEL_ID      = "HealthTrackingChannel"
        private const val NOTIFICATION_ID = 404
    }

    private var isActivityTracking    = false
    private var isSleepTracking       = false
    private var isInteractionTracking = false

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "[TRACKING_FLOW] onStartCommand action=${intent?.action}")

        if (intent == null) {
            Log.d(TAG, "[TRACKING_FLOW] OS restart — restoring.")
            restoreStateAndResume()
        } else {
            when (intent.action) {
                ACTION_START_ACTIVITY -> {
                    isActivityTracking = true
                    activityRepository.startTracking()
                    Log.d(TAG, "[TRACKING_FLOW] Activity tracking ACTIVE.")
                }
                ACTION_STOP_ACTIVITY -> {
                    isActivityTracking = false
                    activityRepository.stopTracking()
                    Log.d(TAG, "[TRACKING_FLOW] Activity tracking STOPPED.")
                }
                ACTION_START_SLEEP -> {
                    isSleepTracking = true
                    sleepRepository.startTracking()
                    Log.d(TAG, "[TRACKING_FLOW] Sleep tracking ACTIVE.")
                }
                ACTION_STOP_SLEEP -> {
                    isSleepTracking = false
                    sleepRepository.stopTracking()
                    Log.d(TAG, "[TRACKING_FLOW] Sleep tracking STOPPED.")
                }
                ACTION_START_INTERACTION -> {
                    isInteractionTracking = true
                    interactionRepository.startTracking()
                    Log.d(TAG, "[TRACKING_FLOW] Interaction tracking ACTIVE.")
                }
                ACTION_STOP_INTERACTION -> {
                    isInteractionTracking = false
                    interactionRepository.stopTracking()
                    Log.d(TAG, "[TRACKING_FLOW] Interaction tracking STOPPED.")
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