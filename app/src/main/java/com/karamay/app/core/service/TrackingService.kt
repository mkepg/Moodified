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
import com.karamay.app.R
import com.karamay.app.data.receiver.sleep.SleepReceiver
import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.repository.InteractionRepository
import com.karamay.app.domain.repository.SleepRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Long-running foreground service coordinating all tracking subsystems.
 *
 * Sleep tracking change:
 *  - The previous version relied on GPS Sleep API (no registration here — it was in
 *    SleepRepositoryImpl via ActivityRecognition.getClient().requestSleepSegmentUpdates()).
 *  - The new approach registers [SleepReceiver] at runtime for SCREEN_OFF / SCREEN_ON
 *    broadcasts, which are protected broadcasts that can only be received via runtime
 *    registration (not manifest). This gives us an immediate, reliable screen-state signal.
 *  - [SleepReceiver] is unregistered when sleep tracking stops or the service is destroyed.
 */
@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var activityRepository:    ActivityRepository
    @Inject lateinit var sleepRepository:       SleepRepository
    @Inject lateinit var interactionRepository: InteractionRepository
    @Inject lateinit var sleepReceiver:         SleepReceiver   // Hilt-injected singleton

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

    /** Tracks whether [sleepReceiver] is currently registered to avoid double-registration. */
    private var sleepReceiverRegistered = false

    // -----------------------------------------------------------------------
    // Service lifecycle
    // -----------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "[TRACKING_FLOW] Service: onStartCommand action=${intent?.action}")
        if (intent == null) {
            Log.d(TAG, "[TRACKING_FLOW] Service: OS restart — restoring from repositories.")
            restoreStateAndResume()
        } else {
            when (intent.action) {
                ACTION_START_ACTIVITY -> isActivityTracking = true
                ACTION_STOP_ACTIVITY  -> isActivityTracking = false

                ACTION_START_SLEEP -> {
                    isSleepTracking = true
                    registerSleepReceiver()
                }
                ACTION_STOP_SLEEP -> {
                    isSleepTracking = false
                    unregisterSleepReceiver()
                }

                ACTION_START_INTERACTION -> {
                    Log.d(TAG, "[TRACKING_FLOW] Service: Interaction tracking ACTIVE.")
                    isInteractionTracking = true
                }
                ACTION_STOP_INTERACTION -> {
                    Log.d(TAG, "[TRACKING_FLOW] Service: Interaction tracking INACTIVE.")
                    isInteractionTracking = false
                }
            }
        }

        startServiceForeground()

        if (!isActivityTracking && !isSleepTracking && !isInteractionTracking) {
            Log.d(TAG, "[TRACKING_FLOW] Service: Nothing to track — stopping self.")
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterSleepReceiver()
        Log.d(TAG, "[TRACKING_FLOW] Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -----------------------------------------------------------------------
    // Sleep receiver registration
    // -----------------------------------------------------------------------

    /**
     * Registers [SleepReceiver] for SCREEN_OFF / SCREEN_ON protected broadcasts.
     * Must be runtime-registered — the OS does NOT deliver these to manifest receivers.
     */
    private fun registerSleepReceiver() {
        if (sleepReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(sleepReceiver, filter)
        sleepReceiverRegistered = true
        Log.d(TAG, "SleepReceiver registered for SCREEN_OFF/ON.")
    }

    private fun unregisterSleepReceiver() {
        if (!sleepReceiverRegistered) return
        try {
            unregisterReceiver(sleepReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "SleepReceiver was not registered: ${e.message}")
        }
        sleepReceiverRegistered = false
        Log.d(TAG, "SleepReceiver unregistered.")
    }

    // -----------------------------------------------------------------------
    // State restoration on OS restart
    // -----------------------------------------------------------------------

    private fun restoreStateAndResume() {
        if (activityRepository.isTracking) {
            isActivityTracking = true
            activityRepository.startTracking()
        }
        if (sleepRepository.isTracking) {
            isSleepTracking = true
            sleepRepository.startTracking()
            registerSleepReceiver()
        }
        if (interactionRepository.isTracking) {
            isInteractionTracking = true
            interactionRepository.startTracking()
        }
    }

    // -----------------------------------------------------------------------
    // Foreground notification
    // -----------------------------------------------------------------------

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
