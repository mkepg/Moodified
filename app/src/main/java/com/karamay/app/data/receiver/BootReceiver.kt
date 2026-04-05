package com.karamay.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import com.karamay.app.core.service.TrackingService

/**
 * Fix #28: Restores foreground tracking after device reboot or app replacement.
 * Reads persisted tracker states from SharedPreferences and re-starts TrackingService
 * only for the trackers that were active before the kill.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val activityPrefs: SharedPreferences =
            context.getSharedPreferences("activity_monitor_prefs", Context.MODE_PRIVATE)
        val sleepPrefs: SharedPreferences =
            context.getSharedPreferences("sleep_tracker_prefs", Context.MODE_PRIVATE)

        val wasActivityTracking = activityPrefs.getBoolean("is_tracking", false)
        val wasSleepTracking    = sleepPrefs.getBoolean("is_tracking", false)

        if (wasActivityTracking) {
            val serviceIntent = Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START_ACTIVITY
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }

        if (wasSleepTracking) {
            val serviceIntent = Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START_SLEEP
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
