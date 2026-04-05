package com.karamay.app.data.receiver.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Manifest-declared BroadcastReceiver for Activity Recognition events from Play Services.
 *
 * Moved from [data.receiver] → [data.receiver.activity] to sit alongside its two
 * collaborators [ActivityEventBus] and [ActivitySignalBus], mirroring the sleep package:
 *
 *   data/receiver/activity/  →  ActivityReceiver, ActivityEventBus, ActivitySignalBus
 *   data/receiver/sleep/     →  SleepReceiver,    SleepEventBus,    SleepSignalBus
 *
 * Replaces the runtime-registered BroadcastReceiver from the original codebase.
 * Manifest-declared receivers survive process death; runtime registrations were lost
 * on OS kill, leaving the PendingIntent alive with no handler.
 */
@AndroidEntryPoint
class ActivityReceiver : BroadcastReceiver() {

    @Inject lateinit var eventBus: ActivityEventBus

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                val result   = ActivityRecognitionResult.extractResult(intent) ?: return@launch
                val activity = result.mostProbableActivity
                eventBus.emit(activity)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_PROCESS_ACTIVITY = "com.karamay.app.ACTION_PROCESS_ACTIVITY"
    }
}
