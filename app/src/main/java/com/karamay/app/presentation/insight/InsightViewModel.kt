package com.karamay.app.presentation.insight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.domain.model.activity.ActivityDailySummary
import com.karamay.app.domain.model.activity.ActivityIntensity
import com.karamay.app.domain.model.inference.DailyBehaviorSnapshot
import com.karamay.app.domain.model.interaction.InteractionDailySummary
import com.karamay.app.domain.model.sleep.DailySleepSummary
import com.karamay.app.domain.usecase.activity.GetWeeklyActivitySummariesUseCase
import com.karamay.app.domain.usecase.activity.GetWeeklyActivityTrendsUseCase
import com.karamay.app.domain.usecase.inference.RuleBasedMoodInferenceEngine
import com.karamay.app.domain.usecase.interaction.GetWeeklyInteractionSummariesUseCase
import com.karamay.app.domain.usecase.interaction.GetWeeklyInteractionTrendsUseCase
import com.karamay.app.domain.usecase.mood.GetMoodHistoryUseCase
import com.karamay.app.domain.usecase.sleep.GetWeeklySleepSummariesUseCase
import com.karamay.app.domain.usecase.sleep.GetWeeklySleepTrendsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class InsightViewModel @Inject constructor(
    getMoodHistory:                GetMoodHistoryUseCase,
    getWeeklySleepSummaries:       GetWeeklySleepSummariesUseCase,
    getWeeklyActivitySummaries:    GetWeeklyActivitySummariesUseCase,
    getWeeklyInteractionSummaries: GetWeeklyInteractionSummariesUseCase,
    getWeeklySleepTrends:          GetWeeklySleepTrendsUseCase,
    getWeeklyActivityTrends:       GetWeeklyActivityTrendsUseCase,
    getWeeklyInteractionTrends:    GetWeeklyInteractionTrendsUseCase,
    private val inferenceEngine:   RuleBasedMoodInferenceEngine,
    private val insightGenerator:  InsightGenerator
) : ViewModel() {

    private val today = LocalDate.now()

    private val rawDataFlow = combine(
        getMoodHistory(),
        getWeeklySleepSummaries(today),
        getWeeklyActivitySummaries(today),
        getWeeklyInteractionSummaries(today)
    ) { moodHistory, sleepList, activityList, interactionList ->
        RawWeeklyData(moodHistory, sleepList, activityList, interactionList)
    }

    private val trendsFlow = combine(
        getWeeklySleepTrends(today),
        getWeeklyActivityTrends(today),
        getWeeklyInteractionTrends(today)
    ) { sleepTrends, activityTrends, interactionTrends ->
        WeeklyTrends(sleepTrends, activityTrends, interactionTrends)
    }

    val uiState: StateFlow<InsightUiState> = rawDataFlow
        .flatMapLatest { raw ->
            trendsFlow.combine(trendsFlow) { trends, _ -> buildState(raw, trends) }
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = InsightUiState(isLoading = true)
        )

    private fun buildState(raw: RawWeeklyData, trends: WeeklyTrends): InsightUiState {
        val last7Days = (0L until 7L).map { today.minusDays(it) }

        val sleepByDate:       Map<LocalDate, DailySleepSummary>       =
            raw.sleepList.associateBy { LocalDate.parse(it.date) }
        val activityByDate:    Map<LocalDate, ActivityDailySummary>    =
            raw.activityList.associateBy { LocalDate.parse(it.date) }
        val interactionByDate: Map<LocalDate, InteractionDailySummary> =
            raw.interactionList.associateBy { LocalDate.parse(it.date) }

        val bundles: List<DailyInsightBundle> = last7Days.map { date ->
            val entries  = raw.moodHistory[date] ?: emptyList()
            val snapshot = DailyBehaviorSnapshot(
                targetDate            = date,
                sleepSummary          = sleepByDate[date],
                activitySummary       = activityByDate[date],
                interactionSummary    = interactionByDate[date],
                moodEntries           = entries,
                dataCompletenessScore = computeCompleteness(
                    sleepByDate[date],
                    activityByDate[date],
                    interactionByDate[date]
                )
            )

            DailyInsightBundle(
                date               = date,
                moodEntries        = entries,
                sleepSummary       = sleepByDate[date],
                activitySummary    = activityByDate[date],
                interactionSummary = interactionByDate[date],
                inferredMood       = runCatching { inferenceEngine(snapshot) }.getOrNull()
            )
        }

        val todayMood = bundles.firstOrNull()?.inferredMood

        val sleepDays       = bundles.count { it.sleepSummary != null }
        val phoneDays       = bundles.count { it.interactionSummary != null }
        val activityDays    = bundles.count { it.activitySummary != null }
        val manualMoodDays  = bundles.count { b -> b.moodEntries.any { it.isManual } }

        val domainReadiness = InsightDomainReadiness(
            sleep = DomainReadiness(
                isReady      = sleepDays >= 1,
                daysWithData = sleepDays,
                requiredDays = 0
            ),
            phone = DomainReadiness(
                isReady      = phoneDays >= 1,
                daysWithData = phoneDays,
                requiredDays = 0
            ),
            activity = DomainReadiness(
                isReady      = activityDays >= MIN_ACTIVITY_DAYS,
                daysWithData = activityDays,
                requiredDays = MIN_ACTIVITY_DAYS
            ),
            mood = DomainReadiness(
                isReady      = manualMoodDays >= MIN_MOOD_DAYS,
                daysWithData = manualMoodDays,
                requiredDays = MIN_MOOD_DAYS
            )
        )

        val chartBundles = bundles.reversed()

        val moodPoints: List<MoodChartPoint> = if (domainReadiness.mood.isReady) {
            chartBundles.flatMap { b ->
                b.moodEntries
                    .filter { it.isManual }
                    .map { e ->
                        MoodChartPoint(
                            date           = b.date,
                            valenceOrdinal = e.valence.ordinal.toFloat(),
                            isManual       = true
                        )
                    }
            }
        } else emptyList()

        val sleepPoints: List<SleepBarPoint> = chartBundles.mapNotNull { b ->
            b.sleepSummary?.let { s ->
                SleepBarPoint(
                    date              = b.date,
                    totalSleepMinutes = s.totalSleepMinutes,
                    isEstimated       = s.isEstimated
                )
            }
        }

        val activityPoints: List<ActivityBarPoint> = if (domainReadiness.activity.isReady) {
            chartBundles.mapNotNull { b ->
                b.activitySummary?.let { a ->
                    ActivityBarPoint(
                        date             = b.date,
                        sedentaryMinutes = a.minutesPerIntensityBand[ActivityIntensity.SEDENTARY] ?: a.sedentaryMinutes,
                        lightMinutes     = a.minutesPerIntensityBand[ActivityIntensity.LIGHT]     ?: 0,
                        moderateMinutes  = a.minutesPerIntensityBand[ActivityIntensity.MODERATE]  ?: 0,
                        vigorousMinutes  = a.minutesPerIntensityBand[ActivityIntensity.VIGOROUS]  ?: 0,
                        totalSteps       = a.totalSteps
                    )
                }
            }
        } else emptyList()

        val screenPoints: List<ScreenTimeBarPoint> = chartBundles.mapNotNull { b ->
            b.interactionSummary?.let { i ->
                ScreenTimeBarPoint(
                    date               = b.date,
                    totalScreenMinutes = i.totalScreenTimeMinutes,
                    lateNightMinutes   = i.lateNightUsageMinutes
                )
            }
        }

        val daysWithData = bundles.count {
            it.sleepSummary != null || it.activitySummary != null ||
                    it.interactionSummary != null || it.moodEntries.isNotEmpty()
        }

        return InsightUiState(
            isLoading         = false,
            domainReadiness   = domainReadiness,
            weeklyBundles     = bundles,
            todayInferredMood = todayMood,
            sleepTrends       = trends.sleep,
            activityTrends    = trends.activity,
            interactionTrends = trends.interaction,
            moodChartPoints   = moodPoints,
            sleepBarPoints    = sleepPoints,
            activityBarPoints = activityPoints,
            screenTimePoints  = screenPoints,
            insightCards      = insightGenerator.generate(bundles, domainReadiness),
            daysWithData      = daysWithData
        )
    }

    private fun computeCompleteness(
        sleep:       DailySleepSummary?,
        activity:    ActivityDailySummary?,
        interaction: InteractionDailySummary?
    ): Int {
        var score = 0
        if (sleep != null)       score += 40
        if (activity != null)    score += 40
        if (interaction != null) score += 20
        return score
    }

    private data class RawWeeklyData(
        val moodHistory:     Map<LocalDate, List<com.karamay.app.domain.model.mood.MoodEntry>>,
        val sleepList:       List<DailySleepSummary>,
        val activityList:    List<ActivityDailySummary>,
        val interactionList: List<InteractionDailySummary>
    )

    private data class WeeklyTrends(
        val sleep:       com.karamay.app.domain.model.sleep.SleepTrends?,
        val activity:    com.karamay.app.domain.model.activity.ActivityTrends?,
        val interaction: com.karamay.app.domain.model.interaction.InteractionTrends?
    )

    private companion object {
        const val MIN_ACTIVITY_DAYS = 3
        const val MIN_MOOD_DAYS = 3
        const val MOOD_CONFIDENCE_THRESHOLD = 50
    }
}