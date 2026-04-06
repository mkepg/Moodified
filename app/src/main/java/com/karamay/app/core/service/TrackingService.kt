package com.karamay.app.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
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
    @Inject lateinit var sleepRepository: SleepRepository

    companion object {
        const val ACTION_START_ACTIVITY = "ACTION_START_ACTIVITY"
        const val ACTION_STOP_ACTIVITY  = "ACTION_STOP_ACTIVITY"
        const val ACTION_START_SLEEP    = "ACTION_START_SLEEP"
        const val ACTION_STOP_SLEEP     = "ACTION_STOP_SLEEP"
        private const val CHANNEL_ID      = "HealthTrackingChannel"
        private const val NOTIFICATION_ID = 404
        private const val ACTIVITY_PREFS  = "activity_monitor_prefs"
        private const val SLEEP_PREFS     = "sleep_tracker_prefs"
    }

    private var isActivityTracking = false
    private var isSleepTracking    = false

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            restoreStateFromPrefs()
        } else {
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
            }
        }

        if (isActivityTracking || isSleepTracking) {
            startServiceForeground()
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    private fun restoreStateFromPrefs() {
        isActivityTracking = getSharedPreferences(ACTIVITY_PREFS, Context.MODE_PRIVATE).getBoolean("is_tracking", false)
        isSleepTracking    = getSharedPreferences(SLEEP_PREFS, Context.MODE_PRIVATE).getBoolean("is_tracking", false)

        if (isActivityTracking) activityRepository.startTracking()
        if (isSleepTracking) sleepRepository.startTracking()
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
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Health Tracking", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Maintains background tracking for activity and sleep."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}