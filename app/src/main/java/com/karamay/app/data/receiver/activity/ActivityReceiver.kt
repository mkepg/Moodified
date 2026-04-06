package com.karamay.app.data.receiver.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.karamay.app.domain.model.ActivityIntensity
import com.karamay.app.domain.repository.ActivityRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@AndroidEntryPoint
class ActivityReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: ActivityRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return
        val pendingResult = goAsync()
        receiverScope.launch {
            // FIX BUG-04: The original code had no timeout on the async work. Android gives
            // BroadcastReceivers roughly 10 seconds when using goAsync() before the system
            // considers the receiver timed out. Without a timeout, if the coroutine stalls
            // (e.g. database contention, slow sensor), pendingResult.finish() may never be
            // called, blocking subsequent broadcasts for this receiver. The 8-second budget
            // stays safely inside Android's limit while leaving headroom for cleanup.
            try {
                withTimeout(8_000L) {
                    try {
                        val result   = ActivityRecognitionResult.extractResult(intent) ?: return@withTimeout
                        val activity = result.mostProbableActivity
                        val mappedIntensity: ActivityIntensity? = when (activity.type) {
                            DetectedActivity.STILL      -> ActivityIntensity.SEDENTARY
                            DetectedActivity.IN_VEHICLE -> ActivityIntensity.IN_VEHICLE
                            DetectedActivity.WALKING,
                            DetectedActivity.ON_FOOT    -> ActivityIntensity.LIGHT
                            DetectedActivity.RUNNING    -> ActivityIntensity.VIGOROUS
                            else                        -> null
                        }
                        if (mappedIntensity != null) {
                            repository.updateActivityIntensity(mappedIntensity, activity.confidence)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing activity recognition result", e)
                    }
                }
            } finally {
                // Always finish, even if withTimeout cancels via TimeoutCancellationException.
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ActivityReceiver"
        const val ACTION_PROCESS_ACTIVITY = "com.karamay.app.ACTION_PROCESS_ACTIVITY"
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
