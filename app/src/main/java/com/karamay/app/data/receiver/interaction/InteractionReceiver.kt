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
        val eventType = when (intent.action) {
            Intent.ACTION_SCREEN_ON -> InteractionEventType.SCREEN_ON
            Intent.ACTION_USER_PRESENT -> InteractionEventType.UNLOCKED
            Intent.ACTION_SCREEN_OFF,
            Intent.ACTION_SHUTDOWN -> InteractionEventType.SCREEN_OFF // Handle shutdown as screen off
            else -> return
        }

        Log.d(TAG, "System Event Captured: ${intent.action} mapped to ${eventType.name}")
        repository.logSystemEvent(eventType)
    }

    companion object {
        private const val TAG = "InteractionReceiver"
    }
}