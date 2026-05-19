package com.moodified.app.domain.usecase.intervention

import com.moodified.app.domain.model.activity.ActivityDailySummary
import com.moodified.app.domain.model.interaction.InteractionDailySummary
import com.moodified.app.domain.model.intervention.InterventionAction
import com.moodified.app.domain.model.intervention.TrendDirection
import com.moodified.app.domain.model.intervention.WellBeingDomain
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.model.mood.Valence
import com.moodified.app.domain.model.sleep.DailySleepSummary
import javax.inject.Inject

class DetectNegativeTrendsUseCase @Inject constructor() {

    operator fun invoke(
        sleepSummaries: List<DailySleepSummary>,
        activitySummaries: List<ActivityDailySummary>,
        interactionSummaries: List<InteractionDailySummary>,
        moodEntries: List<MoodEntry>
    ): List<InterventionAction.TrendAlert> {
        val alerts = mutableListOf<InterventionAction.TrendAlert>()

        // 1. Sleep Decline (3+ consecutive days)
        if (sleepSummaries.size >= 3) {
            val recentSleep = sleepSummaries.sortedByDescending { it.date }.take(3)
            val isDeclining = recentSleep.zipWithNext().all { (newer, older) ->
                newer.totalSleepMinutes < older.totalSleepMinutes && newer.totalSleepMinutes < 360
            }
            if (isDeclining) {
                alerts.add(
                    InterventionAction.TrendAlert(
                        id = "trend_sleep_decline",
                        priority = 85,
                        domain = WellBeingDomain.SLEEP,
                        trendDirection = TrendDirection.DECLINING,
                        severityLevel = 2,
                        supportingDataPoints = listOf("Sleep duration has decreased for 3 consecutive nights.")
                    )
                )
            }
        }

        // 2. High Screen Time (5+ days)
        if (interactionSummaries.size >= 5) {
            val recentScreens = interactionSummaries.sortedByDescending { it.date }.take(5)
            val consistentlyHigh = recentScreens.all { it.totalScreenTimeMinutes > 240 }
            if (consistentlyHigh) {
                alerts.add(
                    InterventionAction.TrendAlert(
                        id = "trend_digital_fatigue",
                        priority = 80,
                        domain = WellBeingDomain.DIGITAL,
                        trendDirection = TrendDirection.DECLINING,
                        severityLevel = 2,
                        supportingDataPoints = listOf("Screen time has exceeded 4 hours for 5 days straight.")
                    )
                )
            }
        }

        // 3. Mood Valence Negative (4+ days)
        if (moodEntries.isNotEmpty()) {
            val dailyModalValence = moodEntries.groupBy { it.timestamp.toLocalDate() }
                .mapValues { (_, entries) ->
                    val counts = entries.groupingBy { it.valence }.eachCount()
                    counts.maxByOrNull { it.value }?.key ?: Valence.NEUTRAL
                }.entries.sortedByDescending { it.key }.take(4)

            if (dailyModalValence.size == 4 && dailyModalValence.all { it.value == Valence.NEGATIVE }) {
                alerts.add(
                    InterventionAction.TrendAlert(
                        id = "trend_mood_negative",
                        priority = 95,
                        domain = WellBeingDomain.MENTAL,
                        trendDirection = TrendDirection.DECLINING,
                        severityLevel = 3,
                        supportingDataPoints = listOf("Your mood has trended low for the past 4 days.")
                    )
                )
            }
        }

        return alerts
    }
}