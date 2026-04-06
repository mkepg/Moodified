package com.karamay.app.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.karamay.app.R
import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.repository.SleepRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var activityRepository: ActivityRepository
    @Inject lateinit var sleepRepository   : SleepRepository

    companion object {
        private const val TAG             = "TrackingService"
        const val ACTION_START_ACTIVITY   = "ACTION_START_ACTIVITY"
        const val ACTION_STOP_ACTIVITY    = "ACTION_STOP_ACTIVITY"
        const val ACTION_START_SLEEP      = "ACTION_START_SLEEP"
        const val ACTION_STOP_SLEEP       = "ACTION_STOP_SLEEP"
        private const val CHANNEL_ID      = "HealthTrackingChannel"
        private const val NOTIFICATION_ID = 404
    }

    private var isActivityTracking = false
    private var isSleepTracking    = false

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // OS restarted the service after process death (START_STICKY).
            Log.d(TAG, "Restarted by OS (START_STICKY) — restoring from prefs.")
            restoreStateAndResume()
        } else {
            Log.d(TAG, "Received action: ${intent.action}")
            when (intent.action) {
                ACTION_START_ACTIVITY -> {
                    isActivityTracking = true
                    activityRepository.startTracking()
                }
                ACTION_STOP_ACTIVITY  -> {
                    isActivityTracking = false
                    activityRepository.stopTracking()
                }
                ACTION_START_SLEEP    -> {
                    isSleepTracking = true
                    sleepRepository.startTracking()
                }
                ACTION_STOP_SLEEP     -> {
                    isSleepTracking = false
                    sleepRepository.stopTracking()
                }
                else -> Log.w(TAG, "Unknown action: ${intent.action}")
            }
        }

        // FIX BUG-03: The original code derived isActivityTracking/isSleepTracking from the
        // explicit action intents only. In the START_STICKY null-intent path,
        // restoreStateAndResume() called startTracking() on each repo but never set the local
        // flags, so the isActivityTracking || isSleepTracking check below was always false —
        // causing the service to immediately stop itself instead of going foreground.
        // The fix is to read the source-of-truth (repository.isTracking, backed by prefs)
        // rather than relying solely on local flags that are lost across process death.
        if (activityRepository.isTracking || sleepRepository.isTracking) {
            startServiceForeground()
        } else {
            Log.d(TAG, "Nothing to track — stopping service.")
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun restoreStateAndResume() {
        val wasActivityTracking = activityRepository.isTracking
        val wasSleepTracking    = sleepRepository.isTracking
        Log.d(TAG, "Restoring: activity=$wasActivityTracking sleep=$wasSleepTracking")

        // FIX BUG-03: Set local flags before the startServiceForeground() check in
        // onStartCommand(). Previously these flags were only set inside the explicit-intent
        // branch, so restoreStateAndResume() (the null-intent / OS-kill path) left them false
        // and the service stopped itself immediately.
        if (wasActivityTracking) {
            isActivityTracking = true
            activityRepository.startTracking()
        }
        if (wasSleepTracking) {
            isSleepTracking = true
            sleepRepository.startTracking()
        }

        if (!wasActivityTracking && !wasSleepTracking) {
            Log.d(TAG, "Nothing was running before kill — stopping service cleanly.")
        }
    }

    private fun startServiceForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracking Active")
            .setContentText("Monitoring health signals in the background.")
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
            "Health Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains background tracking for activity and sleep."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
