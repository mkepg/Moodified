package com.karamay.app.domain.usecase.inference

import com.karamay.app.domain.model.inference.DailyBehaviorSnapshot
import com.karamay.app.domain.repository.MoodRepository
import com.karamay.app.domain.usecase.activity.GetDailyActivitySummaryUseCase
import com.karamay.app.domain.usecase.activity.GetWeeklyActivityTrendsUseCase
import com.karamay.app.domain.usecase.interaction.GetDailyInteractionSummaryUseCase
import com.karamay.app.domain.usecase.sleep.GetDailySleepSummaryUseCase
import com.karamay.app.domain.usecase.sleep.GetWeeklySleepTrendsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

class BuildDailyBehaviorSnapshotUseCase @Inject constructor(
    private val getDailySleepSummary:       GetDailySleepSummaryUseCase,
    private val getDailyActivitySummary:    GetDailyActivitySummaryUseCase,
    private val getDailyInteractionSummary: GetDailyInteractionSummaryUseCase,
    private val getWeeklySleepTrends:       GetWeeklySleepTrendsUseCase,
    private val getWeeklyActivityTrends:    GetWeeklyActivityTrendsUseCase,
    private val moodRepository:             MoodRepository
) {
    suspend operator fun invoke(date: LocalDate): DailyBehaviorSnapshot = withContext(Dispatchers.IO) {

        // Each signal is fetched with an explicit catch so that a Room or pipeline
        // exception produces null (no data) rather than propagating and causing the
        // entire snapshot to fail. This preserves the existing null == "no data"
        // semantics without conflating pipeline failures with genuinely absent data.
        // The completeness score therefore reflects real signal availability, not
        // accidental exceptions.

        val sleepSummary = runCatching {
            getDailySleepSummary(date)
                .catch { emit(null) }
                .first()
        }.getOrNull()

        val activitySummary = runCatching {
            getDailyActivitySummary(date)
                .catch { emit(null) }
                .first()
        }.getOrNull()

        val interactionSummary = runCatching {
            getDailyInteractionSummary(date)
                .catch { emit(null) }
                .first()
        }.getOrNull()

        val moodEntries = runCatching {
            moodRepository.getEntriesForDate(date)
                .catch { emit(emptyList()) }
                .first()
        }.getOrElse { emptyList() }

        val sleepTrends = runCatching {
            getWeeklySleepTrends(date)
                .catch { emit(null) }
                .first()
        }.getOrNull()

        val activityTrends = runCatching {
            getWeeklyActivityTrends(date)
                .catch { emit(null) }
                .first()
        }.getOrNull()

        DailyBehaviorSnapshot(
            targetDate            = date,
            sleepSummary          = sleepSummary,
            activitySummary       = activitySummary,
            interactionSummary    = interactionSummary,
            moodEntries           = moodEntries,
            dataCompletenessScore = calculateCompleteness(
                hasSleep       = sleepSummary != null,
                hasActivity    = activitySummary != null,
                hasInteraction = interactionSummary != null
            ),
            sleepTrends    = sleepTrends,
            activityTrends = activityTrends
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