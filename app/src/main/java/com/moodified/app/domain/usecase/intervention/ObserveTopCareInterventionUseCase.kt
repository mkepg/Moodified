package com.moodified.app.domain.usecase.intervention

import com.moodified.app.core.utils.midnightTickerFlow
import com.moodified.app.domain.model.activity.ActivityDailySummary
import com.moodified.app.domain.model.interaction.InteractionDailySummary
import com.moodified.app.domain.model.intervention.InterventionAction
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.model.sleep.DailySleepSummary
import com.moodified.app.domain.usecase.activity.GetWeeklyActivitySummariesUseCase
import com.moodified.app.domain.usecase.activity.ObserveActivitySignalUseCase
import com.moodified.app.domain.usecase.inference.BuildDailyBehaviorSnapshotUseCase
import com.moodified.app.domain.usecase.inference.RuleBasedMoodInferenceEngine
import com.moodified.app.domain.usecase.interaction.GetWeeklyInteractionSummariesUseCase
import com.moodified.app.domain.usecase.interaction.ObserveInteractionSignalUseCase
import com.moodified.app.domain.usecase.mood.GetMoodHistoryUseCase
import com.moodified.app.domain.usecase.sleep.GetWeeklySleepSummariesUseCase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import javax.inject.Inject

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ObserveTopCareInterventionUseCase
    @Inject
    constructor(
        private val buildDailyBehaviorSnapshot: BuildDailyBehaviorSnapshotUseCase,
        private val ruleBasedMoodInferenceEngine: RuleBasedMoodInferenceEngine,
        private val careEvaluationEngine: CareEvaluationEngine,
        private val observeActivitySignal: ObserveActivitySignalUseCase,
        private val observeInteractionSignal: ObserveInteractionSignalUseCase,
        private val getWeeklySleepSummaries: GetWeeklySleepSummariesUseCase,
        private val getWeeklyActivitySummaries: GetWeeklyActivitySummariesUseCase,
        private val getWeeklyInteractionSummaries: GetWeeklyInteractionSummariesUseCase,
        private val getMoodHistory: GetMoodHistoryUseCase,
    ) {
        private data class HistoricalData(
            val sleep: List<DailySleepSummary>,
            val activity: List<ActivityDailySummary>,
            val interaction: List<InteractionDailySummary>,
            val moods: Map<LocalDate, List<MoodEntry>>,
        )

        operator fun invoke(): Flow<InterventionAction?> =
            midnightTickerFlow()
                .flatMapLatest { today ->
                    val snapshotFlow = buildDailyBehaviorSnapshot(today)

                    val historicalDataFlow =
                        combine(
                            getWeeklySleepSummaries(today),
                            getWeeklyActivitySummaries(today),
                            getWeeklyInteractionSummaries(today),
                            getMoodHistory(),
                        ) { sleep, activity, interaction, moods ->
                            HistoricalData(sleep, activity, interaction, moods)
                        }

                    val synchronizedDbFlow =
                        combine(snapshotFlow, historicalDataFlow) { snapshot, history ->
                            Pair(snapshot, history)
                        }.debounce(250)

                    combine(
                        synchronizedDbFlow,
                        observeActivitySignal(),
                        observeInteractionSignal(),
                    ) { (snapshot, history), activity, interaction ->
                        val moodState = ruleBasedMoodInferenceEngine(snapshot)
                        val actions =
                            careEvaluationEngine(
                                moodState = moodState,
                                snapshot = snapshot,
                                liveActivity = activity,
                                liveInteraction = interaction,
                                historicalSleep = history.sleep,
                                historicalActivity = history.activity,
                                historicalInteraction = history.interaction,
                                historicalMoods = history.moods.values.flatten(),
                            )
                        actions.firstOrNull()
                    }
                }
    }
