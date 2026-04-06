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

/**
 * Long-lived foreground service that bridges OS lifecycle events to the
 * tracking repositories.
 *
 * ## Lifecycle guarantees
 *
 * | Event                   | Path                                        | Outcome                          |
 * |-------------------------|---------------------------------------------|----------------------------------|
 * | User starts tracking    | UI → ACTION_START_*                         | Foreground service + repos start |
 * | User stops tracking     | UI → ACTION_STOP_*                          | Repos stop, service self-stops   |
 * | OS kills process        | START_STICKY → intent = null                | restoreStateAndResume()          |
 * | Device reboot           | BootReceiver → ACTION_START_*               | Repos start fresh                |
 * | App update (MY_PACKAGE_REPLACED) | BootReceiver → ACTION_START_*      | Repos start fresh                |
 *
 * ## Why START_STICKY and not START_REDELIVER_INTENT
 *
 * START_REDELIVER_INTENT re-delivers the last intent after process death, which
 * would re-execute the most recent ACTION_START_* or ACTION_STOP_*.  If the last
 * action was STOP, re-delivering it after an unrelated process kill would
 * incorrectly stop tracking.  START_STICKY with `intent == null` handling
 * is the correct pattern: we read the persisted desired state from prefs and
 * restore only what was actually running.
 *
 * ## False-active-state prevention
 *
 * `restoreStateAndResume()` reads persisted prefs to determine which trackers
 * were active.  If *neither* was active it calls `stopSelf()` immediately,
 * ensuring the service never runs without a reason.
 */
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

    /** Tracks what this service instance believes is active, used to decide
     *  when to stop the foreground service. */
    private var isActivityTracking = false
    private var isSleepTracking    = false

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Process was killed by the OS and restarted by START_STICKY.
            // No intent means we must read persisted state to decide what to resume.
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

        if (isActivityTracking || isSleepTracking) {
            startServiceForeground()
        } else {
            Log.d(TAG, "Nothing to track — stopping service.")
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        // START_STICKY: OS will restart the service with intent = null after kills.
        // This is correct — we handle that case in restoreStateAndResume().
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Restore ───────────────────────────────────────────────────────────────

    /**
     * Called when the OS restarts us after a process kill (intent == null).
     *
     * We read the persisted [isTracking] flags from both repositories and
     * restart only the trackers that were genuinely active before the kill.
     * This ensures we never show a false-active state and sensors are properly
     * re-registered in the repository layer.
     */
    private fun restoreStateAndResume() {
        val wasActivityTracking = activityRepository.isTracking
        val wasSleepTracking    = sleepRepository.isTracking

        Log.d(TAG, "Restoring: activity=$wasActivityTracking sleep=$wasSleepTracking")

        if (wasActivityTracking) {
            isActivityTracking = true
            activityRepository.startTracking()
        }
        if (wasSleepTracking) {
            isSleepTracking = true
            sleepRepository.startTracking()
        }

        // If nothing was actually running, set prefs to false so the UI stays
        // consistent and stop ourselves.
        if (!isActivityTracking && !isSleepTracking) {
            Log.d(TAG, "Nothing was running before kill — stopping service cleanly.")
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

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