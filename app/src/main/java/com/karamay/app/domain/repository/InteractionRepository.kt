package com.karamay.app.domain.repository

import com.karamay.app.domain.model.interaction.InteractionEventType
import com.karamay.app.domain.model.interaction.InteractionSignal
import kotlinx.coroutines.flow.Flow

interface InteractionRepository {
    val isTracking: Boolean

    fun observeLiveSignal(): Flow<InteractionSignal>

    fun startTracking(): Boolean
    fun stopTracking()
    fun resetSession()

    // Called by the Receiver to log a raw system event
    fun logSystemEvent(eventType: InteractionEventType)
}