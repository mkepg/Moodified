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

    // ── Raw data flows ────────────────────────────────────────────────────────

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

    // ── Exposed state ─────────────────────────────────────────────────────────

    val uiState: StateFlow<InsightUiState> = rawDataFlow
        .flatMapLatest { raw ->
            // Combine with trends; the second `trendsFlow` arg is the canonical
            // signature — only one emission per raw change matters here.
            trendsFlow.combine(trendsFlow) { trends, _ -> buildState(raw, trends) }
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = InsightUiState(isLoading = true)
        )

    // ── State builder ─────────────────────────────────────────────────────────

    private fun buildState(raw: RawWeeklyData, trends: WeeklyTrends): InsightUiState {

        // Build per-date lookup maps for the last 7 days.
        val last7Days = (0L until 7L).map { today.minusDays(it) }

        val sleepByDate:       Map<LocalDate, DailySleepSummary>       =
            raw.sleepList.associateBy { LocalDate.parse(it.date) }
        val activityByDate:    Map<LocalDate, ActivityDailySummary>    =
            raw.activityList.associateBy { LocalDate.parse(it.date) }
        val interactionByDate: Map<LocalDate, InteractionDailySummary> =
            raw.interactionList.associateBy { LocalDate.parse(it.date) }

        // ── Build bundles ─────────────────────────────────────────────────────

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

        // ── Per-domain readiness ───────────────────────────────────────────────
        //
        // Sleep / Phone: ready as soon as there is ≥ 1 day of data.
        //   Backfilled data is valid — no minimum day count required.
        //
        // Activity: requires ≥ 3 days with non-null ActivityDailySummary.
        //
        // Mood: requires ≥ 3 manual entries, OR ≥ 3 inferred moods that all
        //   clear the confidence threshold (≥ 50 %).

        val sleepDays       = bundles.count { it.sleepSummary != null }
        val phoneDays       = bundles.count { it.interactionSummary != null }
        val activityDays    = bundles.count { it.activitySummary != null }
        val manualMoodCount = bundles.sumOf { b -> b.moodEntries.count { it.isManual } }
        val confidentInferredMoods = bundles.count { b ->
            b.inferredMood != null && b.inferredMood.confidenceScore >= MOOD_CONFIDENCE_THRESHOLD
        }

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
                isReady = manualMoodCount >= MIN_MOOD_ENTRIES ||
                          confidentInferredMoods >= MIN_MOOD_ENTRIES,
                daysWithData = manualMoodCount
                    .coerceAtLeast(confidentInferredMoods),
                requiredDays = MIN_MOOD_ENTRIES
            )
        )

        // ── Chart points (oldest → newest for left-to-right display) ──────────

        val chartBundles = bundles.reversed()

        // Mood chart: only populated when mood domain is ready.
        val moodPoints: List<MoodChartPoint> = if (domainReadiness.mood.isReady) {
            val manual = chartBundles.flatMap { b ->
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
            // Fall back to high-confidence inferred moods when no manual entries.
            manual.ifEmpty {
                chartBundles.mapNotNull { b ->
                    val mood = b.inferredMood ?: return@mapNotNull null
                    if (mood.confidenceScore < MOOD_CONFIDENCE_THRESHOLD) return@mapNotNull null
                    MoodChartPoint(
                        date           = b.date,
                        valenceOrdinal = mood.valence.ordinal.toFloat(),
                        isManual       = false
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

        // Activity bar points include per-band breakdown for the stacked chart.
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

        // Broad day-count for the header subtitle.
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

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    // ── Private data holders ──────────────────────────────────────────────────

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
        /** Minimum days of activity data required before showing that section. */
        const val MIN_ACTIVITY_DAYS = 3

        /** Minimum manual mood entries (or high-confidence inferred days) for mood section. */
        const val MIN_MOOD_ENTRIES = 3

        /**
         * Minimum confidence score (0–100) for an inferred mood day to count
         * toward mood-section readiness.
         */
        const val MOOD_CONFIDENCE_THRESHOLD = 50
    }
}
