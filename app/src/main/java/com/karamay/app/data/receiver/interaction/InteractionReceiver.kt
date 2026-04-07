package com.karamay.app.data.receiver.interaction

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.karamay.app.domain.model.interaction.InteractionEventType
import com.karamay.app.domain.repository.InteractionRepository

class InteractionReceiver(
    private val repository: InteractionRepository
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // ACTION_USER_PRESENT (unlock) is no longer forwarded — unlock frequency is not tracked.
        // Only SCREEN_ON and SCREEN_OFF events drive the interaction pipeline.
        val eventType = when (intent.action) {
            Intent.ACTION_SCREEN_ON                           -> InteractionEventType.SCREEN_ON
            Intent.ACTION_SCREEN_OFF, Intent.ACTION_SHUTDOWN -> InteractionEventType.SCREEN_OFF
            else                                              -> return
        }
        Log.d(TAG, "System event captured: ${intent.action} → ${eventType.name}")
        repository.logSystemEvent(eventType)
    }

    companion object {
        private const val TAG = "InteractionReceiver"
    }
}
