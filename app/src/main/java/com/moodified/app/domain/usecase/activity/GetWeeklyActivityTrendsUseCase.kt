package com.moodified.app.domain.usecase.activity

import com.moodified.app.domain.model.activity.ActivityTrends
import com.moodified.app.domain.repository.ActivityRepository
import com.moodified.app.domain.usecase.inference.InferenceConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.sqrt

class GetWeeklyActivityTrendsUseCase
    @Inject
    constructor(
        private val repository: ActivityRepository,
    ) {
        companion object {
            private const val STEPS_NORMALIZER = 5000.0
        }

        operator fun invoke(endDate: LocalDate): Flow<ActivityTrends?> {
            return repository.getWeeklySummaries(endDate).map { summaries ->
                val todayStr = LocalDate.now().toString()

                val completedDays =
                    summaries
                        .filter { it.date != todayStr && !it.isPartialDay }
                        .sortedBy { it.date }

                if (completedDays.size < 2) return@map null

                val dailySteps = completedDays.map { it.totalSteps }
                val dailyActiveMins = completedDays.map { it.activeMinutes }

                val emaSteps = calculateAsymmetricEma(dailySteps, InferenceConstants.HIGH_STEPS_THRESHOLD)
                val emaActiveMins = calculateAsymmetricEma(dailyActiveMins, InferenceConstants.HIGH_ACTIVITY_MINUTES)

                val bestDay = completedDays.maxByOrNull { it.totalSteps }

                val variance =
                    completedDays.sumOf {
                        (it.totalSteps - emaSteps).toDouble().pow(2.0)
                    } / completedDays.size

                val stdDev = sqrt(variance)
                val consistencyScore =
                    (100.0 - (stdDev / STEPS_NORMALIZER * 100.0))
                        .coerceIn(0.0, 100.0)
                        .toInt()

                ActivityTrends(
                    daysAnalyzed = completedDays.size,
                    averageSteps = emaSteps,
                    averageActiveMinutes = emaActiveMins,
                    bestDayDate = bestDay?.date,
                    bestDaySteps = bestDay?.totalSteps ?: 0,
                    consistencyScore = consistencyScore,
                )
            }
        }

        private fun calculateAsymmetricEma(
            values: List<Int>,
            defaultFallback: Int,
        ): Int {
            if (values.isEmpty()) return defaultFallback

            val firstDay = values.first()
            var ema =
                if (kotlin.math.abs(firstDay - defaultFallback) > (defaultFallback * 0.5)) {
                    ((firstDay + defaultFallback) / 2.0)
                } else {
                    firstDay.toDouble()
                }

            for (i in 1 until values.size) {
                val current = values[i].toDouble()
                val clampedCurrent =
                    if (i >= 3) {
                        val floor = ema * (1.0 - InferenceConstants.EMA_CLAMP_RATIO)
                        val ceiling = ema * (1.0 + InferenceConstants.EMA_CLAMP_RATIO)
                        current.coerceIn(floor, ceiling)
                    } else {
                        current
                    }

                val alpha = if (clampedCurrent > ema) InferenceConstants.EMA_ALPHA_UP else InferenceConstants.EMA_ALPHA_DOWN
                ema = (clampedCurrent * alpha) + (ema * (1.0 - alpha))
            }
            return ema.toInt()
        }
    }
