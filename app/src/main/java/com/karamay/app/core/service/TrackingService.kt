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
 * Background foreground-service.
 *
 * Phase 2: the service still starts/stops repository tracking directly (the
 * repositories now own their own lifecycle guards). The [TrackingCoordinator]
 * is NOT injected here — the service *is* the component that responds to
 * coordinator intents, so no circular dependency is created. Repos call
 * coordinator; coordinator sends intents; service receives them and calls repos.
 */
@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var activityRepository:    ActivityRepository
    @Inject lateinit var sleepRepository:       SleepRepository
    @Inject lateinit var interactionRepository: InteractionRepository
    @Inject lateinit var sleepReceiver:         SleepReceiver

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
    private var sleepReceiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "[TRACKING_FLOW] onStartCommand action=${intent?.action}")

        if (intent == null) {
            // OS restarted via START_STICKY — restore all active repositories.
            Log.d(TAG, "[TRACKING_FLOW] OS restart — restoring.")
            restoreStateAndResume()
        } else {
            when (intent.action) {
                ACTION_START_ACTIVITY -> {
                    isActivityTracking = true
                    // P0: startTracking() registers sensors and starts the poll loop.
                    // The repo's circuit-breaker (_trackingActive) makes this idempotent.
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
                    registerSleepReceiver()
                    Log.d(TAG, "[TRACKING_FLOW] Sleep tracking ACTIVE.")
                }
                ACTION_STOP_SLEEP -> {
                    isSleepTracking = false
                    sleepRepository.stopTracking()
                    unregisterSleepReceiver()
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
        unregisterSleepReceiver()
        Log.d(TAG, "[TRACKING_FLOW] Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── Private ──────────────────────────────────────────────────────────────

    private fun restoreStateAndResume() {
        if (activityRepository.isTracking) {
            isActivityTracking = true
            activityRepository.startTracking()
            Log.d(TAG, "[TRACKING_FLOW] Restored activity tracking.")
        }
        if (sleepRepository.isTracking) {
            isSleepTracking = true
            sleepRepository.startTracking()
            registerSleepReceiver()
            Log.d(TAG, "[TRACKING_FLOW] Restored sleep tracking.")
        }
        if (interactionRepository.isTracking) {
            isInteractionTracking = true
            interactionRepository.startTracking()
            Log.d(TAG, "[TRACKING_FLOW] Restored interaction tracking.")
        }
    }

    private fun registerSleepReceiver() {
        if (sleepReceiverRegistered) return
        registerReceiver(
            sleepReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
        )
        sleepReceiverRegistered = true
        Log.d(TAG, "SleepReceiver registered.")
    }

    private fun unregisterSleepReceiver() {
        if (!sleepReceiverRegistered) return
        try { unregisterReceiver(sleepReceiver) }
        catch (e: IllegalArgumentException) { Log.w(TAG, "SleepReceiver not registered: ${e.message}") }
        sleepReceiverRegistered = false
        Log.d(TAG, "SleepReceiver unregistered.")
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
