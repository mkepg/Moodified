package com.moodified.app.domain.usecase.inference

import com.moodified.app.domain.model.inference.DailyBehaviorSnapshot
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.model.mood.Valence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RuleBasedMoodInferenceEngineTest {
    private val engine =
        RuleBasedMoodInferenceEngine(
            interpreter = MoodStateInterpreter(),
            explainer = MoodExplainabilityGenerator(),
        )

    private fun snapshot(
        moodEntries: List<MoodEntry> = emptyList(),
        completeness: Int = 100,
    ): DailyBehaviorSnapshot =
        DailyBehaviorSnapshot(
            targetDate = LocalDate.of(2026, 1, 15),
            sleepSummary = null,
            activitySummary = null,
            interactionSummary = null,
            moodEntries = moodEntries,
            dataCompletenessScore = completeness,
        )

    // ---- Rule: manual entries always outrank inference ----

    @Test
    fun manualEntryDrivesReturnedValenceAndArousal() {
        val manual = MoodEntry(valence = Valence.POSITIVE, arousal = Arousal.HIGH)
        val state = engine(snapshot(moodEntries = listOf(manual)))
        assertEquals(Valence.POSITIVE, state.valence)
        assertEquals(Arousal.HIGH, state.arousal)
        assertFalse("Manual-derived state must not be marked fallback", state.isFallback)
    }

    // ---- Rule: low completeness returns a fallback state ----

    @Test
    fun lowCompletenessReturnsFallbackState() {
        val belowThreshold = InferenceConstants.MIN_COMPLETENESS_FOR_INFERENCE - 1
        val state = engine(snapshot(completeness = belowThreshold))
        assertTrue("Expected isFallback when completeness < threshold", state.isFallback)
    }

    // ---- Rule: sufficient completeness with no manual entries runs passive inference (not fallback) ----

    @Test
    fun sufficientCompletenessRunsPassiveInference() {
        val state = engine(snapshot(completeness = 100))
        assertFalse("Should not fall back when completeness is 100", state.isFallback)
    }

    // ---- Rule: confidence stays in [0, 100] regardless of input shape ----

    @Test
    fun confidenceScoreStaysWithinZeroToOneHundred() {
        listOf(0, 25, 50, 75, 100).forEach { completeness ->
            val state = engine(snapshot(completeness = completeness))
            assertTrue(
                "confidenceScore out of range for completeness=$completeness: ${state.confidenceScore}",
                state.confidenceScore in 0..100,
            )
        }
    }

    // ---- Rule: explainability string is never blank ----

    @Test
    fun explainabilityStringIsNeverBlank() {
        val state = engine(snapshot())
        assertFalse(
            "Explainability must be non-empty",
            state.explainabilityString.isBlank(),
        )
    }

    // ---- Rule: interpretation label is never blank ----

    @Test
    fun interpretationLabelIsNeverBlank() {
        val state = engine(snapshot())
        assertFalse(state.interpretationLabel.isBlank())
    }
}
