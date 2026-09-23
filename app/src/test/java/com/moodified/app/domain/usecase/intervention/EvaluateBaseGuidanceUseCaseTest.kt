package com.moodified.app.domain.usecase.intervention

import com.moodified.app.data.local.entity.intervention.InterventionHistoryEntity
import com.moodified.app.domain.model.activity.ActivityIntensity
import com.moodified.app.domain.model.activity.ActivitySignal
import com.moodified.app.domain.model.inference.DailyBehaviorSnapshot
import com.moodified.app.domain.model.inference.InferredMoodState
import com.moodified.app.domain.model.interaction.InteractionSignal
import com.moodified.app.domain.model.intervention.InterventionAction
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.Valence
import com.moodified.app.domain.model.sleep.DailySleepSummary
import com.moodified.app.domain.repository.InterventionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EvaluateBaseGuidanceUseCaseTest {
    private class FakeInterventionRepository(
        private val onCooldownIds: Set<String> = emptySet(),
    ) : InterventionRepository {
        override suspend fun recordInterventionShown(id: String) = Unit

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

    // Snapshot with no sleep — used for most tests where sleep data is irrelevant.
    private val snapshot =
        DailyBehaviorSnapshot(
            targetDate = LocalDate.of(2026, 1, 15),
            sleepSummary = null,
            activitySummary = null,
            interactionSummary = null,
            moodEntries = emptyList(),
            dataCompletenessScore = 80,
        )

    // Snapshot with good sleep (>= 420 min threshold) — used for the REST motivation test.
    private val snapshotWithGoodSleep =
        snapshot.copy(
            sleepSummary =
                DailySleepSummary(
                    date = "2026-01-15",
                    totalSleepMinutes = 480,
                    awakenings = 1,
                ),
        )

    private fun moodState(
        valence: Valence,
        arousal: Arousal,
        confidenceScore: Int = 70,
    ) = InferredMoodState(
        valence = valence,
        arousal = arousal,
        interpretationLabel = "test",
        confidenceScore = confidenceScore,
        explainabilityString = "test",
    )

    private fun activity(
        steps: Int = 0,
        intensity: ActivityIntensity = ActivityIntensity.SEDENTARY,
        sedentaryMinutes: Int = 0,
    ): ActivitySignal =
        ActivitySignal(
            steps = steps,
            intensity = intensity,
            sedentaryMinutes = sedentaryMinutes,
        )

    private fun interaction(
        totalScreenTimeTodayMs: Long = 0L,
        lateNightScreenTimeTodayMs: Long = 0L,
    ): InteractionSignal =
        InteractionSignal(
            totalScreenTimeTodayMs = totalScreenTimeTodayMs,
            lateNightScreenTimeTodayMs = lateNightScreenTimeTodayMs,
        )

    private fun useCase(onCooldownIds: Set<String> = emptySet()) = EvaluateBaseGuidanceUseCase(FakeInterventionRepository(onCooldownIds))

    // ---- Guidance: disconnect fires for NEGATIVE + high total screen time ----
    // Threshold: totalScreenTimeTodayMs > 240 min. Using 300 min (comfortably above).

    @Test
    fun negativeValenceWithHighScreenTimeEmitsDisconnectGuidance() =
        runTest {
            val result =
                useCase()(
                    moodState(Valence.NEGATIVE, Arousal.MID),
                    snapshot,
                    activity(),
                    interaction(totalScreenTimeTodayMs = 300 * 60_000L),
                )
            assertTrue(
                "Expected ID_GUIDANCE_DISCONNECT in result",
                result.any { it.id == EvaluateBaseGuidanceUseCase.ID_GUIDANCE_DISCONNECT },
            )
        }

    // ---- Guidance: move fires for NEGATIVE + LOW arousal + SEDENTARY + long sedentary minutes ----
    // Threshold: sedentaryMinutes > 120. Using 180 (comfortably above).

    @Test
    fun negativeLowArousalWhileSedentaryEmitsMoveGuidance() =
        runTest {
            val result =
                useCase()(
                    moodState(Valence.NEGATIVE, Arousal.LOW),
                    snapshot,
                    activity(
                        steps = 200,
                        intensity = ActivityIntensity.SEDENTARY,
                        sedentaryMinutes = 180,
                    ),
                    interaction(),
                )
            assertTrue(
                "Expected ID_GUIDANCE_MOVE in result",
                result.any { it.id == EvaluateBaseGuidanceUseCase.ID_GUIDANCE_MOVE },
            )
        }

    // ---- Motivation: momentum fires for POSITIVE + HIGH arousal + steps > 5000 ----
    // Threshold: steps > 5000. Using 8000 (comfortably above).

    @Test
    fun positiveHighArousalWithManyStepsEmitsMotivationMomentum() =
        runTest {
            val result =
                useCase()(
                    moodState(Valence.POSITIVE, Arousal.HIGH),
                    snapshot,
                    activity(steps = 8_000),
                    interaction(),
                )
            assertTrue(
                "Expected ID_MOTIVATION_MOMENTUM in result",
                result.any { it.id == EvaluateBaseGuidanceUseCase.ID_MOTIVATION_MOMENTUM },
            )
        }

    // ---- Motivation: rest fires for POSITIVE + LOW arousal + good sleep (>= 420 min) ----
    // Threshold: totalSleepMinutes >= 420. Using 480 (one hour above, covering typical 8 h nights).

    @Test
    fun positiveLowArousalWithGoodSleepEmitsMotivationRest() =
        runTest {
            val result =
                useCase()(
                    moodState(Valence.POSITIVE, Arousal.LOW),
                    snapshotWithGoodSleep,
                    activity(),
                    interaction(),
                )
            assertTrue(
                "Expected ID_MOTIVATION_REST in result",
                result.any { it.id == EvaluateBaseGuidanceUseCase.ID_MOTIVATION_REST },
            )
        }

    // ---- Micro-confirmation is suppressed when its ID is on cooldown ----

    @Test
    fun microConfirmationOnCooldownDoesNotFire() =
        runTest {
            val onCooldown = setOf(EvaluateBaseGuidanceUseCase.ID_MICRO_CONFIRM)
            val result =
                useCase(onCooldownIds = onCooldown)(
                    moodState(Valence.POSITIVE, Arousal.MID),
                    snapshot,
                    activity(),
                    interaction(),
                )
            assertTrue(
                "Micro-confirmation must not be emitted while on cooldown",
                result.none { it is InterventionAction.MicroConfirmation },
            )
        }

    // ---- Null-guard: null moodState → empty list ----

    @Test
    fun nullMoodStateReturnsEmpty() =
        runTest {
            val result = useCase()(null, snapshot, activity(), interaction())
            assertTrue("Expected empty list when moodState is null", result.isEmpty())
        }
}
