package com.karamay.app.domain.usecase.intervention

import com.karamay.app.domain.model.activity.ActivityIntensity
import com.karamay.app.domain.model.activity.ActivitySignal
import com.karamay.app.domain.model.inference.DailyBehaviorSnapshot
import com.karamay.app.domain.model.inference.InferredMoodState
import com.karamay.app.domain.model.interaction.InteractionSignal
import com.karamay.app.domain.model.intervention.InterventionAction
import com.karamay.app.domain.model.mood.Arousal
import com.karamay.app.domain.model.mood.Valence
import com.karamay.app.domain.repository.InterventionRepository
import javax.inject.Inject

class EvaluateBaseGuidanceUseCase @Inject constructor(
    private val interventionRepository: InterventionRepository
) {
    companion object {
        private const val COOLDOWN_MICRO_CONFIRMATION = 12 * 60 * 60 * 1000L // 12 hours
        private const val COOLDOWN_GUIDANCE           = 4 * 60 * 60 * 1000L  // 4 hours
        private const val COOLDOWN_MOTIVATION         = 4 * 60 * 60 * 1000L  // 4 hours

        const val ID_MICRO_CONFIRM       = "micro_confirm_base"
        const val ID_GUIDANCE_DISCONNECT = "guidance_disconnect"
        const val ID_GUIDANCE_MOVE       = "guidance_move"
        const val ID_MOTIVATION_MOMENTUM = "motivation_momentum"
        const val ID_MOTIVATION_REST     = "motivation_rest"
    }

    suspend operator fun invoke(
        moodState: InferredMoodState?,
        snapshot: DailyBehaviorSnapshot?,
        liveActivity: ActivitySignal,
        liveInteraction: InteractionSignal
    ): List<InterventionAction> {
        val actions = mutableListOf<InterventionAction>()
        if (moodState == null || snapshot == null) return actions

        // 1. Evaluate Micro-Confirmation (Safe Window check)
        if (shouldTriggerMicroConfirmation(moodState, liveActivity, liveInteraction)) {
            if (!interventionRepository.isOnCooldown(ID_MICRO_CONFIRM, COOLDOWN_MICRO_CONFIRMATION)) {
                val prompt = when (moodState.valence) {
                    Valence.NEGATIVE -> "We noticed you might be feeling a bit heavy. Is this right?"
                    Valence.POSITIVE -> "Looks like you're having a good day! Spot on?"
                    Valence.NEUTRAL  -> "Seems like a steady, balanced moment. Is this accurate?"
                }
                actions.add(
                    InterventionAction.MicroConfirmation(
                        id              = ID_MICRO_CONFIRM,
                        prompt          = prompt,
                        inferredValence = moodState.valence,
                        inferredArousal = moodState.arousal
                    )
                )
            }
        }

        // 2. Evaluate Adaptive Guidance
        if (moodState.valence == Valence.NEGATIVE) {
            if (liveInteraction.lateNightScreenTimeTodayMs > 30 * 60_000L || liveInteraction.totalScreenTimeTodayMs > 240 * 60_000L) {
                if (!interventionRepository.isOnCooldown(ID_GUIDANCE_DISCONNECT, COOLDOWN_GUIDANCE)) {
                    actions.add(
                        InterventionAction.Guidance(
                            id          = ID_GUIDANCE_DISCONNECT,
                            priority    = 10,
                            title       = "Time to Disconnect?",
                            description = "Your screen time is adding up, which might be contributing to that heavy feeling. Try stepping away for 10 minutes."
                        )
                    )
                }
            }
            if (moodState.arousal == Arousal.LOW && liveActivity.intensity == ActivityIntensity.SEDENTARY && liveActivity.sedentaryMinutes > 120) {
                if (!interventionRepository.isOnCooldown(ID_GUIDANCE_MOVE, COOLDOWN_GUIDANCE)) {
                    actions.add(
                        InterventionAction.Guidance(
                            id          = ID_GUIDANCE_MOVE,
                            priority    = 9,
                            title       = "A Quick Reset",
                            description = "You've been still for a while. A quick stretch or short walk could help shake off the sluggishness."
                        )
                    )
                }
            }
        }

        // 3. Evaluate Motivational Support
        if (moodState.valence == Valence.POSITIVE) {
            if (moodState.arousal == Arousal.HIGH && liveActivity.steps > 5000) {
                if (!interventionRepository.isOnCooldown(ID_MOTIVATION_MOMENTUM, COOLDOWN_MOTIVATION)) {
                    actions.add(
                        InterventionAction.Motivation(
                            id          = ID_MOTIVATION_MOMENTUM,
                            priority    = 5,
                            title       = "Riding the Momentum",
                            description = "You've been moving fast and your energy is peaking. Keep riding that wave!"
                        )
                    )
                }
            }
            val goodSleep = (snapshot.sleepSummary?.totalSleepMinutes ?: 0) >= 420
            if (moodState.arousal == Arousal.LOW && goodSleep) {
                if (!interventionRepository.isOnCooldown(ID_MOTIVATION_REST, COOLDOWN_MOTIVATION)) {
                    actions.add(
                        InterventionAction.Motivation(
                            id          = ID_MOTIVATION_REST,
                            priority    = 4,
                            title       = "Peaceful Restoration",
                            description = "That solid rest is paying off. Enjoy this calm, grounded energy."
                        )
                    )
                }
            }
        }

        return actions.sortedByDescending { it.priority }
    }

    private fun shouldTriggerMicroConfirmation(
        moodState: InferredMoodState,
        liveActivity: ActivitySignal,
        liveInteraction: InteractionSignal
    ): Boolean {
        if (moodState.confidenceScore >= 75) return false
        if (liveActivity.intensity == ActivityIntensity.VIGOROUS || liveActivity.intensity == ActivityIntensity.IN_VEHICLE) return false
        if (liveInteraction.currentSessionDurationMs > 45 * 60_000L) return false
        return true
    }
}