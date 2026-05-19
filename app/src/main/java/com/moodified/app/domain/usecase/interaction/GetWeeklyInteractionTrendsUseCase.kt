package com.moodified.app.domain.usecase.interaction

import com.moodified.app.domain.model.interaction.InteractionTrends
import com.moodified.app.domain.repository.InteractionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.sqrt

class GetWeeklyInteractionTrendsUseCase @Inject constructor(
    private val repository: InteractionRepository
) {
    companion object {
        private const val SCREEN_TIME_NORMALIZER_MINUTES = 240.0
    }

    operator fun invoke(endDate: LocalDate): Flow<InteractionTrends?> {
        return repository.getWeeklySummaries(endDate).map { summaries ->
            // Filter out today dynamically to prevent artificial drops in weekly averages
            val todayStr = LocalDate.now().toString()
            val completedDays = summaries.filter { it.date != todayStr }

            if (completedDays.isEmpty()) return@map null

            val avgScreenTime = completedDays.sumOf { it.totalScreenTimeMinutes } / completedDays.size
            val avgLateNight  = completedDays.sumOf { it.lateNightUsageMinutes } / completedDays.size

            val variance = completedDays.sumOf {
                (it.totalScreenTimeMinutes - avgScreenTime).toDouble().pow(2.0)
            } / completedDays.size

            val stdDev = sqrt(variance)

            val consistencyScore = (100.0 - (stdDev / SCREEN_TIME_NORMALIZER_MINUTES * 100.0))
                .coerceIn(0.0, 100.0)
                .toInt()

            InteractionTrends(
                daysAnalyzed             = completedDays.size,
                averageScreenTimeMinutes = avgScreenTime,
                averageLateNightMinutes  = avgLateNight,
                consistencyScore         = consistencyScore
            )
        }
    }
}