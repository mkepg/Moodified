package com.karamay.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.karamay.app.core.service.TrackingService
import com.karamay.app.data.local.datasource.ActivityPreferencesDataSource
import com.karamay.app.data.local.datasource.InteractionPreferencesDataSource
import com.karamay.app.data.local.datasource.SleepPreferencesDataSource
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Restarts [TrackingService] after device reboot or app self-update.
 *
 * FIX P3: The receiver is now a Hilt entry point so it can inject the three
 * PreferencesDataSource singletons. This eliminates duplicated raw SharedPreferences
 * name/key strings — if a key ever changes in a DataSource, this class automatically
 * picks up the change via the companion constants instead of silently falling back to
 * `is_tracking = false` (the old bug).
 *
 * FIX P0 (complement): The intents sent here land in TrackingService.onStartCommand
 * which now calls repository.startTracking() for each START action, so the repository
 * poll loops actually begin after a reboot.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var activityPrefs:    ActivityPreferencesDataSource
    @Inject lateinit var sleepPrefs:       SleepPreferencesDataSource
    @Inject lateinit var interactionPrefs: InteractionPreferencesDataSource

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        Log.d(TAG, "Received $action — checking tracking state.")

        val wasActivityTracking    = activityPrefs.isTracking
        val wasSleepTracking       = sleepPrefs.isTracking
        val wasInteractionTracking = interactionPrefs.isTracking

        Log.d(TAG,
            "Restore: activity=$wasActivityTracking " +
            "sleep=$wasSleepTracking " +
            "interaction=$wasInteractionTracking"
        )

        if (wasActivityTracking) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java).apply {
                    this.action = TrackingService.ACTION_START_ACTIVITY
                }
            )
            Log.d(TAG, "Sent ACTION_START_ACTIVITY to TrackingService.")
        }

        if (wasSleepTracking) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java).apply {
                    this.action = TrackingService.ACTION_START_SLEEP
                }
            )
            Log.d(TAG, "Sent ACTION_START_SLEEP to TrackingService.")
        }

        if (wasInteractionTracking) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java).apply {
                    this.action = TrackingService.ACTION_START_INTERACTION
                }
            )
            Log.d(TAG, "Sent ACTION_START_INTERACTION to TrackingService.")
        }

        if (!wasActivityTracking && !wasSleepTracking && !wasInteractionTracking) {
            Log.d(TAG, "Nothing was tracking before reboot — no action taken.")
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
