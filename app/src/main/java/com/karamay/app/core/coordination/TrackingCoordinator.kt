package com.karamay.app.core.coordination

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.karamay.app.core.service.TrackingService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2 — TrackingCoordinator
 *
 * Single, application-scoped component responsible for all
 * [TrackingService] intent dispatch. Previously each repository
 * called [ContextCompat.startForegroundService] directly, meaning
 * three separate data-layer classes had Android service-lifecycle
 * knowledge — a Clean Architecture violation.
 *
 * Now:
 *  - Repositories call coordinator.startActivity() / stopActivity() etc.
 *  - The coordinator translates those calls into service intents.
 *  - Service-intent strings live only here and in [TrackingService].
 *
 * The coordinator is `@Singleton` so it shares the application
 * lifetime; it holds no mutable state of its own.
 */
@Singleton
class TrackingCoordinator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TrackingCoordinator"
    }

    fun startActivity() = sendAction(TrackingService.ACTION_START_ACTIVITY)
    fun stopActivity()  = sendAction(TrackingService.ACTION_STOP_ACTIVITY)

    fun startSleep()    = sendAction(TrackingService.ACTION_START_SLEEP)
    fun stopSleep()     = sendAction(TrackingService.ACTION_STOP_SLEEP)

    fun startInteraction() = sendAction(TrackingService.ACTION_START_INTERACTION)
    fun stopInteraction()  = sendAction(TrackingService.ACTION_STOP_INTERACTION)

    private fun sendAction(action: String) {
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
