package com.karamay.app.presentation.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.core.utils.midnightTickerFlow
import com.karamay.app.data.local.datasource.CarePreferencesDataSource
import com.karamay.app.domain.model.activity.ActivityDailySummary
import com.karamay.app.domain.model.interaction.InteractionDailySummary
import com.karamay.app.domain.model.intervention.InterventionAction
import com.karamay.app.domain.model.intervention.WellBeingDomain
import com.karamay.app.domain.model.mood.MoodEntry
import com.karamay.app.domain.model.sleep.DailySleepSummary
import com.karamay.app.domain.usecase.activity.GetWeeklyActivitySummariesUseCase
import com.karamay.app.domain.usecase.activity.ObserveActivitySignalUseCase
import com.karamay.app.domain.usecase.inference.BuildDailyBehaviorSnapshotUseCase
import com.karamay.app.domain.usecase.inference.InferenceConstants
import com.karamay.app.domain.usecase.inference.RuleBasedMoodInferenceEngine
import com.karamay.app.domain.usecase.interaction.GetWeeklyInteractionSummariesUseCase
import com.karamay.app.domain.usecase.interaction.ObserveInteractionSignalUseCase
import com.karamay.app.domain.usecase.intervention.CareEvaluationEngine
import com.karamay.app.domain.usecase.intervention.RecordInterventionEffectivenessUseCase
import com.karamay.app.domain.usecase.mood.GetMoodHistoryUseCase
import com.karamay.app.domain.usecase.sleep.GetWeeklySleepSummariesUseCase
import com.karamay.app.presentation.insight.DomainReadiness
import com.karamay.app.presentation.insight.InsightDomainReadiness
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class CareViewModel @Inject constructor(
    private val buildDailyBehaviorSnapshot: BuildDailyBehaviorSnapshotUseCase,
    private val ruleBasedMoodInferenceEngine: RuleBasedMoodInferenceEngine,
    private val careEvaluationEngine: CareEvaluationEngine,
    private val recordInterventionEffectiveness: RecordInterventionEffectivenessUseCase,
    private val observeActivitySignal: ObserveActivitySignalUseCase,
    private val observeInteractionSignal: ObserveInteractionSignalUseCase,
    private val getWeeklySleepSummaries: GetWeeklySleepSummariesUseCase,
    private val getWeeklyActivitySummaries: GetWeeklyActivitySummariesUseCase,
    private val getWeeklyInteractionSummaries: GetWeeklyInteractionSummariesUseCase,
    private val getMoodHistory: GetMoodHistoryUseCase,
    private val carePreferences: CarePreferencesDataSource
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(System.currentTimeMillis())
    private val _activeDomain = MutableStateFlow(carePreferences.activeDomain)

    private data class HistoricalData(
        val sleep: List<DailySleepSummary>,
        val activity: List<ActivityDailySummary>,
        val interaction: List<InteractionDailySummary>,
        val moods: Map<LocalDate, List<MoodEntry>>
    )

    val uiState: StateFlow<CareUiState> = combine(
        midnightTickerFlow(),
        refreshTrigger
    ) { date, _ -> date }
        .flatMapLatest { today ->

            val snapshotFlow = buildDailyBehaviorSnapshot(today)

            val historicalDataFlow = combine(
                getWeeklySleepSummaries(today),
                getWeeklyActivitySummaries(today),
                getWeeklyInteractionSummaries(today),
                getMoodHistory()
            ) { sleep, activity, interaction, moods ->
                HistoricalData(sleep, activity, interaction, moods)
            }

            // Sync the two DB flows with a debounce to prevent UI tearing when
            // multiple Room tables invalidate simultaneously.
            val synchronizedDbFlow = combine(
                snapshotFlow,
                historicalDataFlow
            ) { snapshot, history ->
                Pair(snapshot, history)
            }.debounce(250)

            combine(
                synchronizedDbFlow,
                observeActivitySignal(),
                observeInteractionSignal(),
                _activeDomain
            ) { (snapshot, history), activity, interaction, domain ->

                val sleepDays = history.sleep.size
                val phoneDays = history.interaction.size
                val activityDays = history.activity.size
                val manualMoodDays = history.moods.values.flatten().count { it.isManual }

                val domainReadiness = InsightDomainReadiness(
                    sleep = DomainReadiness(isReady = sleepDays >= 1, daysWithData = sleepDays, requiredDays = 0),
                    phone = DomainReadiness(isReady = phoneDays >= 1, daysWithData = phoneDays, requiredDays = 0),
                    activity = DomainReadiness(isReady = activityDays >= 3, daysWithData = activityDays, requiredDays = 3),
                    mood = DomainReadiness(isReady = manualMoodDays >= 3, daysWithData = manualMoodDays, requiredDays = 3)
                )

                val moodState = ruleBasedMoodInferenceEngine(snapshot)

                val actions = careEvaluationEngine(
                    moodState = moodState,
                    snapshot = snapshot,
                    liveActivity = activity,
                    liveInteraction = interaction,
                    historicalSleep = history.sleep,
                    historicalActivity = history.activity,
                    historicalInteraction = history.interaction,
                    historicalMoods = history.moods.values.flatten()
                )

                val isIntradayComplete = snapshot.dataCompletenessScore >= InferenceConstants.MIN_COMPLETENESS_FOR_INFERENCE
                val activeGuidance = actions.filter { it is InterventionAction.Guidance || it is InterventionAction.Motivation }
                val suggestedRoutine = actions.filterIsInstance<InterventionAction.GuidedRoutine>().firstOrNull()

                CareUiState(
                    isLoading = false,
                    hasEnoughMultiDayData = domainReadiness.anyReady,
                    isIntradayComplete = isIntradayComplete,
                    domainReadiness = domainReadiness,
                    inferredMood = moodState,
                    activeGuidance = activeGuidance,
                    trendAlerts = actions.filterIsInstance<InterventionAction.TrendAlert>(),
                    suggestedRoutine = suggestedRoutine,
                    microInterventions = getMicroInterventionCatalog().filter { it.wellBeingDomain == domain },
                    motivationNudge = actions.filterIsInstance<InterventionAction.MotivationNudge>().firstOrNull(),
                    activeDomain = domain
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CareUiState(isLoading = true)
        )

    fun setWellBeingDomain(domain: WellBeingDomain) {
        carePreferences.activeDomain = domain
        _activeDomain.value = domain
    }

    fun recordFeedback(interventionId: String, feedback: String, wasCompleted: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            recordInterventionEffectiveness(interventionId, feedback, wasCompleted)
            refreshTrigger.value = System.currentTimeMillis()
        }
    }

    private fun getMicroInterventionCatalog(): List<InterventionAction.MicroIntervention> {
        return listOf(
            InterventionAction.MicroIntervention(
                id = "micro_breath_478",
                priority = 1,
                durationSeconds = 120,
                steps = listOf("Inhale quietly through your nose for 4 seconds.", "Hold your breath for 7 seconds.", "Exhale completely through your mouth for 8 seconds."),
                wellBeingDomain = WellBeingDomain.MENTAL
            ),
            InterventionAction.MicroIntervention(
                id = "micro_grounding_54321",
                priority = 2,
                durationSeconds = 180,
                steps = listOf("Acknowledge 5 things you see around you.", "Acknowledge 4 things you can touch.", "Acknowledge 3 things you hear.", "Acknowledge 2 things you can smell.", "Acknowledge 1 thing you can taste."),
                wellBeingDomain = WellBeingDomain.MENTAL
            ),
            InterventionAction.MicroIntervention(
                id = "micro_neck_stretch",
                priority = 3,
                durationSeconds = 60,
                steps = listOf("Drop your chin to your chest.", "Gently roll your head to the left.", "Roll your head to the right.", "Repeat 3 times."),
                wellBeingDomain = WellBeingDomain.PHYSICAL
            ),
            InterventionAction.MicroIntervention(
                id = "micro_rule_20_20_20",
                priority = 4,
                durationSeconds = 20,
                steps = listOf("Look away from your screen.", "Find an object about 20 feet away.", "Focus on it for 20 seconds to relax your eyes."),
                wellBeingDomain = WellBeingDomain.DIGITAL
            )
        )
    }
}