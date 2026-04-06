// app/src/main/java/com/karamay/app/domain/usecase/interaction/GetWeeklyInteractionTrendsUseCase.kt
package com.karamay.app.domain.usecase.interaction

import com.karamay.app.domain.model.interaction.InteractionDailySummary
import com.karamay.app.domain.model.interaction.InteractionTrends
import com.karamay.app.domain.repository.InteractionRepository
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
            if (summaries.isEmpty()) return@map null

            val avgScreenTime = summaries.sumOf { it.totalScreenTimeMinutes } / summaries.size
            val avgUnlocks = summaries.sumOf { it.unlocks } / summaries.size

            val variance = summaries.sumOf {
                (it.totalScreenTimeMinutes - avgScreenTime).toDouble().pow(2.0)
            } / summaries.size

            val stdDev = sqrt(variance)

            val consistencyScore = (100.0 - (stdDev / SCREEN_TIME_NORMALIZER_MINUTES * 100.0))
                .coerceIn(0.0, 100.0)
                .toInt()

            InteractionTrends(
                daysAnalyzed = summaries.size,
                averageScreenTimeMinutes = avgScreenTime,
                averageUnlocks = avgUnlocks,
                consistencyScore = consistencyScore
            )
        }
    }
}