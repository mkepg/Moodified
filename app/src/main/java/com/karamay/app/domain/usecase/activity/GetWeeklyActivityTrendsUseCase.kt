package com.karamay.app.domain.usecase.activity

import com.karamay.app.domain.model.activity.ActivityTrends
import com.karamay.app.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.sqrt

class GetWeeklyActivityTrendsUseCase @Inject constructor(
    private val repository: ActivityRepository
) {
    companion object {
        // Normalizes step variance to calculate a 0-100 consistency score
        private const val STEPS_NORMALIZER = 5000.0
    }

    operator fun invoke(endDate: LocalDate): Flow<ActivityTrends?> {
        return repository.getWeeklySummaries(endDate).map { summaries ->
            val todayStr = LocalDate.now().toString()

            // Filter out partial days and today to ensure accurate historical averages
            val completedDays = summaries.filter { it.date != todayStr && !it.isPartialDay }

            // Guard: Cannot compute meaningful standard deviation/trends on less than 2 days
            if (completedDays.size < 2) return@map null

            val avgSteps = completedDays.sumOf { it.totalSteps } / completedDays.size
            val avgActive = completedDays.sumOf { it.activeMinutes } / completedDays.size
            val bestDay = completedDays.maxByOrNull { it.totalSteps }

            // Calculate consistency score based on standard deviation of daily steps
            val variance = completedDays.sumOf {
                (it.totalSteps - avgSteps).toDouble().pow(2.0)
            } / completedDays.size

            val stdDev = sqrt(variance)
            val consistencyScore = (100.0 - (stdDev / STEPS_NORMALIZER * 100.0))
                .coerceIn(0.0, 100.0)
                .toInt()

            ActivityTrends(
                daysAnalyzed = completedDays.size,
                averageSteps = avgSteps,
                averageActiveMinutes = avgActive,
                bestDayDate = bestDay?.date,
                bestDaySteps = bestDay?.totalSteps ?: 0,
                consistencyScore = consistencyScore
            )
        }
    }
}