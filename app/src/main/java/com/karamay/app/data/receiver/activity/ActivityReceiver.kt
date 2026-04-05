package com.karamay.app.data.receiver.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.karamay.app.domain.model.ActivityIntensity
import com.karamay.app.domain.repository.ActivityRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ActivityReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: ActivityRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result   = ActivityRecognitionResult.extractResult(intent) ?: return@launch
                val activity = result.mostProbableActivity

                // DATA LAYER mapping logic
                val mappedIntensity = when (activity.type) {
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
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_PROCESS_ACTIVITY = "com.karamay.app.ACTION_PROCESS_ACTIVITY"
    }
}