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
// Updated import: PurgeOldTelemetryUseCase moved from usecase/sleep → usecase/common
import com.karamay.app.domain.usecase.common.PurgeOldTelemetryUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var purgeOldTelemetry: PurgeOldTelemetryUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                ACTION_START_ACTIVITY -> isActivityTracking = true
                ACTION_STOP_ACTIVITY  -> isActivityTracking = false
                ACTION_START_SLEEP    -> { isSleepTracking = true; runHousekeeping() }
                ACTION_STOP_SLEEP     -> isSleepTracking = false
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
    }

    private fun runHousekeeping() {
        serviceScope.launch { runCatching { purgeOldTelemetry() } }
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

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
