package com.karamay.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.ContextCompat
import com.karamay.app.core.service.TrackingService

/**
 * Restores tracking after device reboot or app self-update.
 *
 * ## Why this is the correct restoration entry point
 *
 * On a device reboot the entire process is killed.  SharedPreferences survive
 * because they are stored on disk, so [isTracking] flags still reflect what
 * the user had enabled before the reboot.  [BootReceiver] reads those flags
 * and starts [TrackingService] with the appropriate action, which in turn
 * calls into the repository layer to re-register sensors and Play Services
 * listeners.
 *
 * ## Ordering guarantee
 *
 * We start Activity tracking before Sleep tracking so the foreground service
 * is guaranteed to be promoted before the second `startForegroundService`
 * call.  Both actions are sent as separate intents so [TrackingService]
 * processes them in sequence via `onStartCommand`.
 *
 * ## App self-update (MY_PACKAGE_REPLACED)
 *
 * When the app is updated while tracking was active, the process is killed
 * and the receivers are re-registered.  MY_PACKAGE_REPLACED fires before
 * BOOT_COMPLETED would in a normal lifecycle, so we handle it here identically
 * to a reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        Log.d(TAG, "Received $action — checking tracking state.")

        val activityPrefs: SharedPreferences =
            context.getSharedPreferences(ACTIVITY_PREFS, Context.MODE_PRIVATE)
        val sleepPrefs: SharedPreferences =
            context.getSharedPreferences(SLEEP_PREFS, Context.MODE_PRIVATE)

        val wasActivityTracking = activityPrefs.getBoolean("is_tracking", false)
        val wasSleepTracking    = sleepPrefs.getBoolean("is_tracking", false)

        Log.d(TAG, "Restore: activity=$wasActivityTracking sleep=$wasSleepTracking")

        // Start activity tracking first so the foreground service is established
        // before the sleep tracking intent arrives.
        if (wasActivityTracking) {
            val serviceIntent = Intent(context, TrackingService::class.java).apply {
                this.action = TrackingService.ACTION_START_ACTIVITY
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.d(TAG, "Sent ACTION_START_ACTIVITY to TrackingService.")
        }

        if (wasSleepTracking) {
            val serviceIntent = Intent(context, TrackingService::class.java).apply {
                this.action = TrackingService.ACTION_START_SLEEP
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.d(TAG, "Sent ACTION_START_SLEEP to TrackingService.")
        }

        if (!wasActivityTracking && !wasSleepTracking) {
            Log.d(TAG, "Nothing was tracking before reboot — no action taken.")
        }
    }

    companion object {
        private const val TAG           = "BootReceiver"
        private const val ACTIVITY_PREFS = "activity_monitor_prefs"
        private const val SLEEP_PREFS    = "sleep_tracker_prefs"
    }
}