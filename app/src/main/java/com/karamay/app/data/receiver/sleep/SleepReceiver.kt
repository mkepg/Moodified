package com.karamay.app.data.receiver.sleep

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.karamay.app.domain.model.sleep.SleepStatus
import com.karamay.app.domain.repository.SleepRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Receives SCREEN_OFF and SCREEN_ON system broadcasts to maintain a real-time
 * "currently asleep?" signal.
 *
 * Unlike the previous GPS Sleep API approach this receiver:
 *  - Requires **no** ACTIVITY_RECOGNITION permission for its core function.
 *  - Does **not** use PendingIntent registration — it is registered at runtime
 *    inside [TrackingService] when sleep tracking is active, exactly like how
 *    screen-off events are used for interaction tracking.
 *  - Calls [SleepRepository.updateLiveSignal] so the UI flow updates instantly.
 *
 * Registration in TrackingService:
 *   val filter = IntentFilter().apply {
 *       addAction(Intent.ACTION_SCREEN_OFF)
 *       addAction(Intent.ACTION_SCREEN_ON)
 *   }
 *   registerReceiver(sleepReceiver, filter)  // no flag needed — protected broadcast
 *
 * The manifest declaration for this receiver is removed. See AndroidManifest changes.
 */
@AndroidEntryPoint
class SleepReceiver @Inject constructor() : BroadcastReceiver() {

    @Inject lateinit var sleepRepository: SleepRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                withTimeout(8_000L) {
                    val now = LocalDateTime.now()
                    when (intent.action) {
                        Intent.ACTION_SCREEN_OFF -> {
                            Log.d(TAG, "Screen OFF at $now")
                            sleepRepository.updateLiveSignal(
                                status     = SleepStatus.UNKNOWN,  // may be asleep — we don't know yet
                                confidence = 0,
                                motion     = 0,
                                time       = now
                            )
                        }
                        Intent.ACTION_SCREEN_ON -> {
                            Log.d(TAG, "Screen ON at $now")
                            sleepRepository.updateLiveSignal(
                                status     = SleepStatus.AWAKE,
                                confidence = 100,
                                motion     = 1,
                                time       = now
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SleepReceiver error: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SleepReceiver"
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
