package com.moodified.app.domain.usecase.intervention

import com.moodified.app.data.local.entity.intervention.InterventionHistoryEntity
import com.moodified.app.domain.model.activity.ActivityDailySummary
import com.moodified.app.domain.model.activity.ActivitySignal
import com.moodified.app.domain.model.inference.DailyBehaviorSnapshot
import com.moodified.app.domain.model.inference.InferredMoodState
import com.moodified.app.domain.model.interaction.InteractionDailySummary
import com.moodified.app.domain.model.interaction.InteractionSignal
import com.moodified.app.domain.model.intervention.InterventionAction
import com.moodified.app.domain.model.intervention.RoutineType
import com.moodified.app.domain.model.intervention.TrendDirection
import com.moodified.app.domain.model.intervention.WellBeingDomain
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.model.mood.Valence
import com.moodified.app.domain.model.sleep.DailySleepSummary
import com.moodified.app.domain.repository.InterventionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CareEvaluationEngineTest {
    // --- Fakes ---

    private class FakeInterventionRepository(
        private val onCooldownIds: Set<String> = emptySet(),
    ) : InterventionRepository {
        val recorded = mutableListOf<String>()

        override suspend fun recordInterventionShown(id: String) {
            recorded += id
        }

        override suspend fun getLastShownTime(id: String): Long? = null

        override suspend fun isOnCooldown(
            id: String,
            cooldownMillis: Long,
        ): Boolean = id in onCooldownIds

        override suspend fun getInterventionHistory(id: String): InterventionHistoryEntity? = null

        override suspend fun recordFeedback(
            id: String,
            feedback: String,
            wasCompleted: Boolean,
        ) = Unit
    }

    // Deviation from brief: EvaluateBaseGuidanceUseCase.invoke is open suspend — matches brief correctly.
    private class FakeEvaluateBaseGuidance(
        private val result: List<InterventionAction>,
        repo: InterventionRepository,
    ) : EvaluateBaseGuidanceUseCase(repo) {
        override suspend fun invoke(
            moodState: InferredMoodState?,
            snapshot: DailyBehaviorSnapshot?,
            liveActivity: ActivitySignal,
            liveInteraction: InteractionSignal,
        ): List<InterventionAction> = result
    }

    // Deviation from brief: SelectGuidedRoutineUseCase.invoke takes 3 params (moodState, currentCooldownMs, now).
    // The fake overrides all 3 and ignores them, returning the fixed result.
    private class FakeSelectGuidedRoutine(
        private val result: InterventionAction.GuidedRoutine?,
        repo: InterventionRepository,
    ) : SelectGuidedRoutineUseCase(repo) {
        override suspend fun invoke(
            moodState: InferredMoodState,
            currentCooldownMs: Long,
            now: LocalDateTime,
        ): InterventionAction.GuidedRoutine? = result
    }

    // Deviation from brief: DetectNegativeTrendsUseCase.invoke is NOT suspend in production code.
    // The fake overrides the non-suspend operator fun accordingly.
    private class FakeDetectNegativeTrends(
        private val result: List<InterventionAction.TrendAlert>,
    ) : DetectNegativeTrendsUseCase() {
        override operator fun invoke(
            sleepSummaries: List<DailySleepSummary>,
            activitySummaries: List<ActivityDailySummary>,
            interactionSummaries: List<InteractionDailySummary>,
            moodEntries: List<MoodEntry>,
        ): List<InterventionAction.TrendAlert> = result
    }

    // --- Fixtures ---

    private val moodState =
        InferredMoodState(
            valence = Valence.NEUTRAL,
            arousal = Arousal.MID,
            interpretationLabel = "Steady",
            confidenceScore = 70,
            explainabilityString = "Fixture state",
        )

    private val snapshot =
        DailyBehaviorSnapshot(
            targetDate = LocalDate.of(2026, 1, 15),
            sleepSummary = null,
            activitySummary = null,
            interactionSummary = null,
            moodEntries = emptyList(),
            dataCompletenessScore = 80,
        )

    // ActivitySignal and InteractionSignal both have all-defaulted constructors — no-arg call works.
    private val liveActivity: ActivitySignal = ActivitySignal()
    private val liveInteraction: InteractionSignal = InteractionSignal()

    private fun buildEngine(
        baseActions: List<InterventionAction> = emptyList(),
        routine: InterventionAction.GuidedRoutine? = null,
        trends: List<InterventionAction.TrendAlert> = emptyList(),
        onCooldownIds: Set<String> = emptySet(),
    ): CareEvaluationEngine {
        val repo = FakeInterventionRepository(onCooldownIds)
        return CareEvaluationEngine(
            evaluateBaseGuidanceUseCase = FakeEvaluateBaseGuidance(baseActions, repo),
            detectNegativeTrendsUseCase = FakeDetectNegativeTrends(trends),
            selectGuidedRoutineUseCase = FakeSelectGuidedRoutine(routine, repo),
            interventionRepository = repo,
        )
    }

    // ---- Null-guard ----

    @Test
    fun returnsEmptyListWhenMoodStateIsNull() =
        runTest {
            val engine = buildEngine(baseActions = listOf(fakeGuidance("g1", 10)))
            val result =
                engine(
                    moodState = null,
                    snapshot = snapshot,
                    liveActivity = liveActivity,
                    liveInteraction = liveInteraction,
                    historicalSleep = emptyList(),
                    historicalActivity = emptyList(),
                    historicalInteraction = emptyList(),
                    historicalMoods = emptyList(),
                )
            assertTrue(result.isEmpty())
        }

    @Test
    fun returnsEmptyListWhenSnapshotIsNull() =
        runTest {
            val engine = buildEngine(baseActions = listOf(fakeGuidance("g1", 10)))
            val result =
                engine(
                    moodState = moodState,
                    snapshot = null,
                    liveActivity = liveActivity,
                    liveInteraction = liveInteraction,
                    historicalSleep = emptyList(),
                    historicalActivity = emptyList(),
                    historicalInteraction = emptyList(),
                    historicalMoods = emptyList(),
                )
            assertTrue(result.isEmpty())
        }

    // ---- Priority-descending sort across all sources ----

    @Test
    fun aggregatedActionsAreReturnedSortedByPriorityDescending() =
        runTest {
            val engine =
                buildEngine(
                    baseActions = listOf(fakeGuidance("g1", priority = 5), fakeGuidance("g2", priority = 20)),
                    routine = fakeRoutine("r1", priority = 15),
                    trends = listOf(fakeTrend("t1", priority = 30)),
                )
            val result =
                engine(
                    moodState = moodState,
                    snapshot = snapshot,
                    liveActivity = liveActivity,
                    liveInteraction = liveInteraction,
                    historicalSleep = emptyList(),
                    historicalActivity = emptyList(),
                    historicalInteraction = emptyList(),
                    historicalMoods = emptyList(),
                )
            assertEquals(listOf(30, 20, 15, 5), result.map { it.priority })
        }

    // ---- Trend-alert cooldown filter ----

    @Test
    fun trendAlertOnCooldownIsExcluded() =
        runTest {
            val engine =
                buildEngine(
                    trends = listOf(fakeTrend("t1", 30), fakeTrend("t2", 25)),
                    onCooldownIds = setOf("t1"),
                )
            val result =
                engine(
                    moodState = moodState,
                    snapshot = snapshot,
                    liveActivity = liveActivity,
                    liveInteraction = liveInteraction,
                    historicalSleep = emptyList(),
                    historicalActivity = emptyList(),
                    historicalInteraction = emptyList(),
                    historicalMoods = emptyList(),
                )
            assertEquals(listOf("t2"), result.map { it.id })
        }

    // ---- Helper builders ----

    private fun fakeGuidance(
        id: String,
        priority: Int,
    ) = InterventionAction.Guidance(id = id, priority = priority, title = "t", description = "d")

    private fun fakeRoutine(
        id: String,
        priority: Int,
    ) = InterventionAction.GuidedRoutine(
        id = id,
        priority = priority,
        phases = emptyList(),
        estimatedMinutes = 5,
        routineType = RoutineType.WIND_DOWN,
    )

    private fun fakeTrend(
        id: String,
        priority: Int,
    ) = InterventionAction.TrendAlert(
        id = id,
        priority = priority,
        domain = WellBeingDomain.SLEEP,
        trendDirection = TrendDirection.DECLINING,
        severityLevel = 1,
        supportingDataPoints = emptyList(),
    )
}
