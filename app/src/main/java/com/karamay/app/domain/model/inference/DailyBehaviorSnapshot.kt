package com.karamay.app.domain.model.inference

import com.karamay.app.domain.model.activity.ActivityDailySummary
import com.karamay.app.domain.model.interaction.InteractionDailySummary
import com.karamay.app.domain.model.mood.MoodEntry
import com.karamay.app.domain.model.sleep.DailySleepSummary
import java.time.LocalDate

/**
 * A perfectly temporally aligned snapshot of a single day's behavioral data.
 * This serves as the single source of truth for the Mood Inference Engine.
 */
data class DailyBehaviorSnapshot(
    val targetDate: LocalDate,
    val sleepSummary: DailySleepSummary?,
    val activitySummary: ActivityDailySummary?,
    val interactionSummary: InteractionDailySummary?,
    val moodEntries: List<MoodEntry>,
    val dataCompletenessScore: Int
)