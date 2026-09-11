package com.moodified.app.domain.repository
import com.moodified.app.domain.model.interaction.InteractionDailySummary
import com.moodified.app.domain.model.interaction.InteractionEventType
import com.moodified.app.domain.model.interaction.InteractionSession
import com.moodified.app.domain.model.interaction.InteractionSignal
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface InteractionRepository {
    val isTracking: Boolean

    fun hasUsagePermission(): Boolean

    fun observeLiveSignal(): Flow<InteractionSignal>

    fun startTracking(): Boolean

    fun stopTracking()

    fun pauseTracking() // [ADDED]: Pause contract

    fun logSystemEvent(eventType: InteractionEventType)

    fun getDailySummary(date: LocalDate): Flow<InteractionDailySummary?>

    fun getWeeklySummaries(endDate: LocalDate): Flow<List<InteractionDailySummary>>

    fun getSessionsForDate(date: LocalDate): Flow<List<InteractionSession>>

    suspend fun purgeInteractionDataOlderThan(cutoffMillis: Long)

    suspend fun flushInteractionDataToDb()
}
