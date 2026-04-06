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
import javax.inject.Inject

/**
 * Receives activity-recognition events from Google Play Services and forwards
 * them to [ActivityRepository].
 *
 * ## Why GlobalScope was replaced
 *
 * The original code used `GlobalScope.launch` inside `goAsync()`.  GlobalScope
 * has no cancellation and no supervision, meaning:
 *   - If the coroutine crashes it silently swallows the exception.
 *   - There is no back-pressure — rapid events can queue unboundedly.
 *   - `pendingResult.finish()` was in a `finally` block, which is correct, but
 *     GlobalScope leaks if the process is killed mid-coroutine.
 *
 * **Fix:** We use a module-level [CoroutineScope] backed by [SupervisorJob] and
 * [Dispatchers.IO].  The scope lives as long as the application process (which
 * is appropriate for a `@Singleton`-injected BroadcastReceiver component), so
 * in-flight work is not abandoned between receiver invocations.  We still call
 * `goAsync()` + `pendingResult.finish()` to tell the OS we need extra time
 * beyond the default 10-second BroadcastReceiver window.
 *
 * Note: For Android 14+ (API 34) there is a hard 10-second limit on
 * `goAsync()` receivers even in the foreground.  Our work — a single
 * repository method call — is well within this budget.
 */
@AndroidEntryPoint
class ActivityReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: ActivityRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val result   = ActivityRecognitionResult.extractResult(intent) ?: return@launch
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
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ActivityReceiver"
        const val ACTION_PROCESS_ACTIVITY = "com.karamay.app.ACTION_PROCESS_ACTIVITY"

        /**
         * Stable scope tied to the application process.  SupervisorJob ensures
         * one failing coroutine does not cancel sibling coroutines.
         */
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}