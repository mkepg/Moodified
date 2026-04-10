package com.karamay.app.domain.usecase.inference

import com.karamay.app.domain.model.inference.DailyBehaviorSnapshot
import com.karamay.app.domain.repository.MoodRepository
import com.karamay.app.domain.usecase.activity.GetDailyActivitySummaryUseCase
import com.karamay.app.domain.usecase.interaction.GetDailyInteractionSummaryUseCase
import com.karamay.app.domain.usecase.sleep.GetDailySleepSummaryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

class BuildDailyBehaviorSnapshotUseCase @Inject constructor(
    private val getDailySleepSummary: GetDailySleepSummaryUseCase,
    private val getDailyActivitySummary: GetDailyActivitySummaryUseCase,
    private val getDailyInteractionSummary: GetDailyInteractionSummaryUseCase,
    private val moodRepository: MoodRepository
) {
    suspend operator fun invoke(date: LocalDate): DailyBehaviorSnapshot = withContext(Dispatchers.IO) {
        val sleepSummary       = getDailySleepSummary(date).first()
        val activitySummary    = getDailyActivitySummary(date).first()
        val interactionSummary = getDailyInteractionSummary(date).first()
        val moodEntries        = moodRepository.getEntriesForDate(date).first()

        val completenessScore = calculateCompleteness(
            hasSleep       = sleepSummary != null,
            hasActivity    = activitySummary != null,
            hasInteraction = interactionSummary != null
        )

        DailyBehaviorSnapshot(
            targetDate            = date,
            sleepSummary          = sleepSummary,
            activitySummary       = activitySummary,
            interactionSummary    = interactionSummary,
            moodEntries           = moodEntries,
            dataCompletenessScore = completenessScore
        )
    }

    private fun calculateCompleteness(
        hasSleep: Boolean,
        hasActivity: Boolean,
        hasInteraction: Boolean
    ): Int {
        var score = 0
        if (hasSleep)       score += 40
        if (hasActivity)    score += 40
        if (hasInteraction) score += 20
        return score
    }
}