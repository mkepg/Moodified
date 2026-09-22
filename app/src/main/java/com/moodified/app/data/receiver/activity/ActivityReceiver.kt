package com.moodified.app.data.receiver.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.moodified.app.domain.model.activity.ActivityIntensity
import com.moodified.app.domain.repository.ActivityRepository
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

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!ActivityRecognitionResult.hasResult(intent)) return
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                withTimeout(8_000L) {
                    try {
                        val result = ActivityRecognitionResult.extractResult(intent) ?: return@withTimeout
                        val mostProbable = result.mostProbableActivity
                        Log.d(TAG, "Received Activity: ${mostProbable.type} (Confidence: ${mostProbable.confidence}%)")

                        val mapped = classify(mostProbable, result.probableActivities) ?: return@withTimeout
                        repository.updateActivityIntensity(mapped.first, mapped.second)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing activity recognition result", e)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ActivityReceiver"
        const val ACTION_PROCESS_ACTIVITY = "com.moodified.app.ACTION_PROCESS_ACTIVITY"
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Jeeps and buses produce high-frequency vibration that Google Play Services often
        // labels as ON_BICYCLE. Two guards mitigate this:
        //   1. If IN_VEHICLE also appears in the probable list at IN_VEHICLE_HINT_MIN_CONF or
        //      above, prefer IN_VEHICLE — it has a stale-release safety net downstream
        //      (IN_VEHICLE_STALE_RELEASE_MS), whereas VIGOROUS relies on cadence downgrade.
        //   2. Standalone ON_BICYCLE must clear a higher confidence bar than the repository's
        //      default upgrade gate (65) before it commits VIGOROUS.
        private const val IN_VEHICLE_HINT_MIN_CONF = 40
        private const val ON_BICYCLE_MIN_CONF = 75

        internal fun classify(
            mostProbable: DetectedActivity,
            probableActivities: List<DetectedActivity>,
        ): Pair<ActivityIntensity, Int>? {
            val vehicleHint = probableActivities.firstOrNull { it.type == DetectedActivity.IN_VEHICLE }
            val chosen: DetectedActivity =
                if (vehicleHint != null && vehicleHint.confidence >= IN_VEHICLE_HINT_MIN_CONF) {
                    vehicleHint
                } else {
                    mostProbable
                }

            val intensity: ActivityIntensity? =
                when (chosen.type) {
                    DetectedActivity.STILL -> ActivityIntensity.SEDENTARY
                    DetectedActivity.IN_VEHICLE -> ActivityIntensity.IN_VEHICLE
                    DetectedActivity.WALKING,
                    DetectedActivity.ON_FOOT,
                    -> ActivityIntensity.LIGHT
                    DetectedActivity.RUNNING -> ActivityIntensity.VIGOROUS
                    DetectedActivity.ON_BICYCLE ->
                        if (chosen.confidence >= ON_BICYCLE_MIN_CONF) ActivityIntensity.VIGOROUS else null
                    else -> null
                }

            return intensity?.let { it to chosen.confidence }
        }
    }
}
