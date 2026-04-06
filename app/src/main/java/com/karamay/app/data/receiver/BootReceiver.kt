package com.karamay.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.ContextCompat
import com.karamay.app.core.service.TrackingService

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
        val interactionPrefs: SharedPreferences =
            context.getSharedPreferences(INTERACTION_PREFS, Context.MODE_PRIVATE)

        val wasActivityTracking    = activityPrefs.getBoolean("is_tracking", false)
        val wasSleepTracking       = sleepPrefs.getBoolean("is_tracking", false)
        val wasInteractionTracking = interactionPrefs.getBoolean("is_tracking", false)

        Log.d(TAG, "Restore: activity=$wasActivityTracking sleep=$wasSleepTracking interaction=$wasInteractionTracking")

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

        if (wasInteractionTracking) {
            val serviceIntent = Intent(context, TrackingService::class.java).apply {
                this.action = TrackingService.ACTION_START_INTERACTION
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.d(TAG, "Sent ACTION_START_INTERACTION to TrackingService.")
        }

        if (!wasActivityTracking && !wasSleepTracking && !wasInteractionTracking) {
            Log.d(TAG, "Nothing was tracking before reboot — no action taken.")
        }
    }

    companion object {
        private const val TAG               = "BootReceiver"
        private const val ACTIVITY_PREFS    = "activity_monitor_prefs"
        private const val SLEEP_PREFS       = "sleep_tracker_prefs"
        private const val INTERACTION_PREFS = "interaction_tracker_prefs"
    }
}