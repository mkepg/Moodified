package com.karamay.app.domain.model.inference

import com.karamay.app.domain.model.activity.ActivityDailySummary
import com.karamay.app.domain.model.activity.ActivityTrends
import com.karamay.app.domain.model.interaction.InteractionDailySummary
import com.karamay.app.domain.model.mood.MoodEntry
import com.karamay.app.domain.model.sleep.DailySleepSummary
import com.karamay.app.domain.model.sleep.SleepTrends
import java.time.LocalDate

data class DailyBehaviorSnapshot(
    val targetDate: LocalDate,
    val sleepSummary: DailySleepSummary?,
    val activitySummary: ActivityDailySummary?,
    val interactionSummary: InteractionDailySummary?,
    val moodEntries: List<MoodEntry>,
    val dataCompletenessScore: Int,
    val sleepTrends: SleepTrends? = null,
    val activityTrends: ActivityTrends? = null
)
