package com.karamay.app.domain.usecase.inference

import com.karamay.app.domain.model.activity.ActivityIntensity
import com.karamay.app.domain.model.inference.DailyBehaviorSnapshot
import com.karamay.app.domain.model.inference.InferredMoodState
import com.karamay.app.domain.model.inference.ScoringEvent
import com.karamay.app.domain.model.mood.Arousal
import com.karamay.app.domain.model.mood.Valence
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

class RuleBasedMoodInferenceEngine @Inject constructor(
    private val interpreter: MoodStateInterpreter,
    private val explainer:   MoodExplainabilityGenerator
) {
    operator fun invoke(snapshot: DailyBehaviorSnapshot): InferredMoodState {
        if (snapshot.moodEntries.isNotEmpty()) return deriveFromManualEntries(snapshot)
        if (snapshot.dataCompletenessScore < InferenceConstants.MIN_COMPLETENESS_FOR_INFERENCE) {
            return generateFallbackState(snapshot)
        }
        return runPassiveInference(snapshot)
    }

    private fun runPassiveInference(snapshot: DailyBehaviorSnapshot): InferredMoodState {
        var valenceScore = InferenceConstants.BASE_SCORE
        var arousalScore = InferenceConstants.BASE_SCORE
        val events       = mutableListOf<ScoringEvent>()

        // --- 1. Dynamic Baselines ---
        val sleepBaseline = snapshot.sleepTrends?.averageSleepMinutes?.takeIf { it > 0 }
            ?: InferenceConstants.GOOD_SLEEP_MINUTES_MIN

        val rawDynamicPoor = (sleepBaseline * InferenceConstants.DYNAMIC_POOR_SLEEP_MULTIPLIER).toInt()
        val dynamicPoorSleepThreshold = if (rawDynamicPoor > InferenceConstants.GOOD_SLEEP_MINUTES_MIN) {
            InferenceConstants.GOOD_SLEEP_MINUTES_MIN // Biological safety net
        } else {
            rawDynamicPoor.coerceAtLeast(180)
        }

        val dynamicGoodSleepMin = (sleepBaseline * InferenceConstants.DYNAMIC_GOOD_SLEEP_MIN_MULTIPLIER)
            .toInt().coerceAtLeast(240)

        val stepBaseline = snapshot.activityTrends?.averageSteps?.takeIf { it > 0 } ?: InferenceConstants.HIGH_STEPS_THRESHOLD
        val dynamicHighSteps = (stepBaseline * InferenceConstants.DYNAMIC_HIGH_STEPS_MULTIPLIER).toInt()

        val activeMinBaseline = snapshot.activityTrends?.averageActiveMinutes?.takeIf { it > 0 } ?: InferenceConstants.HIGH_ACTIVITY_MINUTES
        val dynamicHighActive = (activeMinBaseline * InferenceConstants.DYNAMIC_HIGH_ACTIVITY_MULTIPLIER).toInt()

        // --- 2. Boolean Matrices (Cross-Domain Context) ---
        val isDigitallyFatigued = snapshot.interactionSummary?.let {
            it.lateNightUsageMinutes > InferenceConstants.LATE_NIGHT_MINUTES_THRESHOLD ||
                    it.totalScreenTimeMinutes > InferenceConstants.HIGH_SCREEN_TIME_MINUTES ||
                    (it.sessionCount > InferenceConstants.HIGH_SESSION_COUNT && it.averageSessionDurationMinutes < InferenceConstants.SHORT_SESSION_DURATION_MINUTES)
        } ?: false

        val isSleepDeprived = (snapshot.sleepSummary?.totalSleepMinutes ?: 999) < dynamicPoorSleepThreshold

        // --- 3. Evaluate Sleep ---
        snapshot.sleepSummary?.let { sleep ->
            if (isSleepDeprived) {
                valenceScore -= 12
                arousalScore -= 8
                events += ScoringEvent("shorter sleep than your usual", -12, -8)
            } else if (sleep.totalSleepMinutes >= dynamicGoodSleepMin && sleep.sleepEfficiencyPercent >= InferenceConstants.GOOD_SLEEP_EFFICIENCY) {
                valenceScore += 15
                events += ScoringEvent("solid, restful sleep", 15, 0)
            }

            if (!isSleepDeprived && sleep.sleepEfficiencyPercent < InferenceConstants.POOR_SLEEP_EFFICIENCY) {
                valenceScore -= 8
                arousalScore -= 5
                events += ScoringEvent("poor sleep quality", -8, -5)
            }
        }

        // --- 4. Evaluate Activity (With Floor Decay & Matrices) ---
        snapshot.activitySummary?.let { activity ->
            val vigorousMins = activity.minutesPerIntensityBand[ActivityIntensity.VIGOROUS] ?: 0
            val commuteMins  = activity.minutesPerIntensityBand[ActivityIntensity.IN_VEHICLE] ?: 0

            if (activity.activeMinutes > dynamicHighActive) {
                if (isSleepDeprived) {
                    arousalScore -= 10
                    valenceScore -= 5
                    events += ScoringEvent("pushing hard on low sleep", -5, -10)
                } else {
                    arousalScore += 20
                    valenceScore += 15
                    events += ScoringEvent("higher physical activity than usual", 15, 20)
                }
            }

            if (activity.sedentaryMinutes > InferenceConstants.SEDENTARY_MINUTES_THRESHOLD) {
                if (isDigitallyFatigued) {
                    arousalScore -= 12
                    valenceScore -= 8
                    events += ScoringEvent("prolonged inactivity with digital fatigue", -8, -12)
                } else {
                    arousalScore -= 5
                    events += ScoringEvent("prolonged period of focus or rest", 0, -5)
                }
            }

            if (vigorousMins > InferenceConstants.VIGOROUS_MINUTES_THRESHOLD) {
                // Using 6 hours as a rough heuristic for elapsed time since exercise
                val estimatedHoursElapsed = 6f
                val decayedArousal = applyFloorDecay(15, estimatedHoursElapsed)
                arousalScore += decayedArousal
                events += ScoringEvent("vigorous exercise earlier today", 0, decayedArousal)
            }

            if (activity.totalSteps > dynamicHighSteps) {
                valenceScore += 5
                events += ScoringEvent("surpassed your usual step count", 5, 0)
            }

            val totalTrackedMinutes = activity.activeMinutes + activity.sedentaryMinutes
            val vehicleRatio = if (totalTrackedMinutes > 0) commuteMins.toFloat() / totalTrackedMinutes else 0f
            if (commuteMins > InferenceConstants.LONG_COMMUTE_MINUTES && vehicleRatio < 0.25f) {
                valenceScore -= 5
                events += ScoringEvent("long commute", -5, 0)
            }
        }

        // --- 5. Evaluate Interactions ---
        snapshot.interactionSummary?.let { interaction ->
            if (interaction.lateNightUsageMinutes > InferenceConstants.LATE_NIGHT_MINUTES_THRESHOLD) {
                valenceScore -= 10
                arousalScore += 5
                events += ScoringEvent("late-night screen usage", -10, 5)
            }
        }

        // --- 6. Logistic Penalty Curves for Chronic States ---
        snapshot.sleepTrends?.let { trends ->
            if (trends.totalSleepDebtMinutes > 0) {
                var logisticPenalty = applyLogisticCurve(
                    x = trends.totalSleepDebtMinutes.toFloat(),
                    maxPenalty = InferenceConstants.LOGISTIC_MAX_PENALTY,
                    steepness = InferenceConstants.LOGISTIC_STEEPNESS,
                    midpoint = InferenceConstants.LOGISTIC_MIDPOINT
                )

                // Dampen the chronic penalty if they slept well today
                val todaySleep = snapshot.sleepSummary?.totalSleepMinutes ?: 0
                if (todaySleep >= dynamicGoodSleepMin) {
                    logisticPenalty /= 3
                }

                if (logisticPenalty > 0) {
                    valenceScore -= logisticPenalty
                    events += ScoringEvent("lingering fatigue from accumulated sleep debt", -logisticPenalty, 0)
                }
            }
        }

        snapshot.activityTrends?.let { trends ->
            if (trends.consistencyScore < InferenceConstants.LOW_ACTIVITY_CONSISTENCY_THRESHOLD) {
                valenceScore -= 5
                events += ScoringEvent("low activity consistency this week", -5, 0)
            }
        }

        // --- 7. Final Mapping ---
        val finalValence = mapScoreToValence(valenceScore)
        val finalArousal = mapScoreToArousal(arousalScore)
        val sortedEvents = events.sortedByDescending { abs(it.valenceDelta) + abs(it.arousalDelta) }

        return InferredMoodState(
            valence              = finalValence,
            arousal              = finalArousal,
            interpretationLabel  = interpreter.interpret(finalValence, finalArousal),
            confidenceScore      = calculateConfidence(snapshot),
            explainabilityString = explainer.generateExplanation(sortedEvents),
            isFallback           = false
        )
    }

    private fun applyFloorDecay(
        initialImpact: Int,
        hoursElapsed: Float,
        decayConstant: Float = InferenceConstants.TRANSIENT_DECAY_RATE,
        floorRatio: Float = InferenceConstants.TRANSIENT_FLOOR_RATIO
    ): Int {
        if (hoursElapsed <= 0f) return initialImpact
        val absImpact = abs(initialImpact).toFloat()
        val floor = absImpact * floorRatio
        val exponent = (-decayConstant * hoursElapsed).coerceIn(-50f, 0f)
        val decayed = absImpact * exp(exponent)
        val finalAbs = max(floor, decayed).toInt()
        return if (initialImpact < 0) -finalAbs else finalAbs
    }

    private fun applyLogisticCurve(
        x: Float,
        maxPenalty: Float,
        steepness: Float,
        midpoint: Float
    ): Int {
        if (x <= 0f) return 0
        val exponent = (-steepness * (x - midpoint)).coerceIn(-50f, 50f)
        val penalty = maxPenalty / (1.0f + exp(exponent))
        return penalty.toInt().coerceIn(0, maxPenalty.toInt())
    }

    private fun deriveFromManualEntries(snapshot: DailyBehaviorSnapshot): InferredMoodState {
        val entries = snapshot.moodEntries

        // 'primary' is the latest entry by timestamp, acting as the definitive final state of the day.
        val primary = entries.maxByOrNull { it.timestamp } ?: entries.first()

        val finalValence = primary.valence
        val finalArousal = primary.arousal
        val explainabilityString: String

        if (entries.size == 1) {
            explainabilityString = "Based on the moment you took to reflect today."
        } else {
            val valenceCounts = entries.groupingBy { it.valence }.eachCount()
            val arousalCounts = entries.groupingBy { it.arousal }.eachCount()

            val firstEntry = entries.minByOrNull { it.timestamp } ?: primary
            val lastEntry = primary

            val majorityValenceCount = valenceCounts.maxByOrNull { it.value }?.value ?: 0
            val modalValence = valenceCounts.maxByOrNull { it.value }?.key ?: primary.valence

            /// CORRECTED logical flags for trajectory tracking
            val valenceImproved = lastEntry.valence > firstEntry.valence
            val valenceDeclined = lastEntry.valence < firstEntry.valence
            val energySpiked    = lastEntry.arousal > firstEntry.arousal
            val energyDropped   = lastEntry.arousal < firstEntry.arousal

            explainabilityString = when {
                // Case 1: 100% Identical
                valenceCounts.size == 1 && arousalCounts.size == 1 -> {
                    "You've been feeling exactly this way across all ${entries.size} check-ins today."
                }

                // Case 2: Strong Majority (e.g., 3 out of 4 check-ins were the same)
                majorityValenceCount >= (entries.size - 1) && entries.size >= 3 -> {
                    if (modalValence == lastEntry.valence) {
                        "Despite a slight shift earlier, you've mostly hovered around this feeling today."
                    } else {
                        "You mostly hovered around a different feeling today, but ultimately settled here."
                    }
                }

                // Case 3: Mood stayed steady, but energy dropped
                valenceCounts.size == 1 && energyDropped -> {
                    "Your mood stayed steady, but your energy levels have wound down since your first check-in."
                }

                // Case 4: Mood stayed steady, but energy spiked
                valenceCounts.size == 1 && energySpiked -> {
                    "Your mood stayed steady, and your energy levels have picked up since your first check-in."
                }

                // Case 5: Clear Upward Trajectory
                valenceImproved -> {
                    "Your mood has steadily lifted since your first check-in today."
                }

                // Case 6: Clear Downward Trajectory
                valenceDeclined -> {
                    "Your mood has dipped a bit since your earlier check-ins."
                }

                // Case 7: Rollercoaster / True Fluctuation (The Fallback)
                else -> {
                    "Your energy has fluctuated across your ${entries.size} check-ins, ultimately settling here."
                }
            }
        }

        // Confidence scales with the number of manual entries (Max 100%)
        val confidence = (40 + entries.size.coerceAtMost(3) * 15).coerceAtMost(100)

        return InferredMoodState(
            valence              = finalValence,
            arousal              = finalArousal,
            interpretationLabel  = interpreter.interpret(finalValence, finalArousal),
            confidenceScore      = confidence,
            explainabilityString = explainabilityString,
            isFallback           = false
        )
    }

    private fun generateFallbackState(snapshot: DailyBehaviorSnapshot) = InferredMoodState(
        valence              = Valence.NEUTRAL,
        arousal              = Arousal.MID,
        interpretationLabel  = "Neutral / Unknown",
        confidenceScore      = snapshot.dataCompletenessScore,
        explainabilityString = "Insufficient data to form a confident inference.",
        isFallback           = true
    )

    private fun calculateConfidence(snapshot: DailyBehaviorSnapshot): Int {
        var score = snapshot.dataCompletenessScore
        if (snapshot.sleepSummary?.isEstimated == true) score -= InferenceConstants.ESTIMATED_SLEEP_PENALTY
        if (snapshot.activitySummary?.isPartialDay == true) score -= InferenceConstants.PARTIAL_DAY_ACTIVITY_PENALTY
        val bonusEntries = snapshot.moodEntries.size.coerceAtMost(InferenceConstants.MANUAL_ENTRY_BONUS_MAX_ENTRIES)
        score += bonusEntries * InferenceConstants.MANUAL_ENTRY_BONUS_PER_ENTRY
        return score.coerceIn(0, 100)
    }

    private fun mapScoreToValence(score: Int): Valence = when {
        score < InferenceConstants.VALENCE_NEGATIVE_THRESHOLD -> Valence.NEGATIVE
        score > InferenceConstants.VALENCE_POSITIVE_THRESHOLD -> Valence.POSITIVE
        else -> Valence.NEUTRAL
    }

    private fun mapScoreToArousal(score: Int): Arousal = when {
        score < InferenceConstants.AROUSAL_LOW_THRESHOLD  -> Arousal.LOW
        score > InferenceConstants.AROUSAL_HIGH_THRESHOLD -> Arousal.HIGH
        else -> Arousal.MID
    }
}