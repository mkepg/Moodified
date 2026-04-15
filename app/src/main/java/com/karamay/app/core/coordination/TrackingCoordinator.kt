package com.karamay.app.core.coordination

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.karamay.app.core.service.TrackingService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingCoordinator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "TrackingCoordinator"
    }

    private fun hasRequiredPermissions(): Boolean {
        // [FIX APPLIED]: Only validates POST_NOTIFICATIONS to allow Domain Isolation.
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return hasNotif
    }

    fun startActivity() = sendAction(TrackingService.ACTION_START_ACTIVITY)
    fun stopActivity()  = sendAction(TrackingService.ACTION_STOP_ACTIVITY)
    fun pauseActivity() = sendAction(TrackingService.ACTION_PAUSE_ACTIVITY) // NEW

    fun startSleep()    = sendAction(TrackingService.ACTION_START_SLEEP)
    fun stopSleep()     = sendAction(TrackingService.ACTION_STOP_SLEEP)
    fun pauseSleep()    = sendAction(TrackingService.ACTION_PAUSE_SLEEP)    // NEW

    fun startInteraction() = sendAction(TrackingService.ACTION_START_INTERACTION)
    fun stopInteraction()  = sendAction(TrackingService.ACTION_STOP_INTERACTION)
    fun pauseInteraction() = sendAction(TrackingService.ACTION_PAUSE_INTERACTION) // NEW



    private fun sendAction(action: String) {
        val isStartAction = action.startsWith("ACTION_START_")

        if (isStartAction && !hasRequiredPermissions()) {
            Log.w(TAG, "Missing core Notification permission. Cannot send $action to foreground service.")
            return
        }

        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java).apply {
                    this.action = action
                }
            )
            Log.d(TAG, "Sent $action to TrackingService.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send $action: ${e.message}", e)
        }
    }
}