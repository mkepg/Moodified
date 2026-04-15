package com.karamay.app.data.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.karamay.app.core.service.TrackingService
import com.karamay.app.data.local.datasource.ActivityPreferencesDataSource
import com.karamay.app.data.local.datasource.InteractionPreferencesDataSource
import com.karamay.app.data.local.datasource.SleepPreferencesDataSource
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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

        val hasNotifPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasNotifPerm) {
            // [FIX APPLIED]: Prevent aggressive overwriting of user preferences on Boot.
            // If permissions are missing, the background service aborts safely, but intent is preserved.
            Log.w(TAG, "Notification permission missing on boot. Aborting tracking restore.")
            return
        }

        val wasActivityTracking    = activityPrefs.isTracking
        val wasSleepTracking       = sleepPrefs.isTracking
        val wasInteractionTracking = interactionPrefs.isTracking

        Log.d(TAG,
            "Restore Intents: activity=$wasActivityTracking " +
                    "sleep=$wasSleepTracking " +
                    "interaction=$wasInteractionTracking"
        )

        // TrackingService will naturally block domains missing data permissions internally.
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