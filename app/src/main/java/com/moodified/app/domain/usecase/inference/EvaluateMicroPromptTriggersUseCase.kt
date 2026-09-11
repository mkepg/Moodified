package com.moodified.app.domain.usecase.inference

import com.moodified.app.data.local.datasource.PromptPreferencesDataSource
import com.moodified.app.domain.model.activity.ActivityIntensity
import com.moodified.app.domain.model.activity.ActivitySignal
import com.moodified.app.domain.model.interaction.InteractionSignal
import java.time.LocalDateTime
import javax.inject.Inject

data class PromptEvaluationResult(
    val shouldPrompt: Boolean,
    val promptMessage: String? = null,
)

class EvaluateMicroPromptTriggersUseCase
    @Inject
    constructor(
        private val promptPrefs: PromptPreferencesDataSource,
    ) {
        companion object {
            const val COOLDOWN_MS = 3 * 60 * 60 * 1000L // 3 hours minimum
            private const val MAX_PROMPTS_PER_DAY = 3
        }

        operator fun invoke(
            activity: ActivitySignal,
            interaction: InteractionSignal,
            now: LocalDateTime = LocalDateTime.now(),
        ): PromptEvaluationResult {
            // 1. Day Rollover Check
            val todayStr = now.toLocalDate().toString()
            if (promptPrefs.lastRolloverDate != todayStr) {
                promptPrefs.promptsTodayCount = 0
                promptPrefs.lastRolloverDate = todayStr
            }

            // 2. Hard Limits & System Cooldown
            if (promptPrefs.promptsTodayCount >= MAX_PROMPTS_PER_DAY) {
                return PromptEvaluationResult(false)
            }
            val nowMs = System.currentTimeMillis()
            if (nowMs - promptPrefs.lastPromptTimestampMs < COOLDOWN_MS) {
                return PromptEvaluationResult(false)
            }

            // 3. Time of Day Blocks (Protect sleep hours and transit)
            val hour = now.hour
            if (hour < 7 || hour > 22) {
                promptPrefs.isCoolingDown = false
                return PromptEvaluationResult(false)
            }
            if (activity.intensity == ActivityIntensity.IN_VEHICLE) {
                promptPrefs.isCoolingDown = false
                return PromptEvaluationResult(false)
            }

            // 4. Synchronous State Machine Updates (Executes instantly on every emission)
            if (promptPrefs.lastUnlockCount == -1 || interaction.unlockCount < promptPrefs.lastUnlockCount) {
                promptPrefs.lastUnlockCount = interaction.unlockCount
            }

            var shouldPrompt = false
            var activeTrigger: ContextTrigger? = null

            // Trigger A: Post-Activity Cool-down
            if (activity.intensity == ActivityIntensity.MODERATE || activity.intensity == ActivityIntensity.VIGOROUS) {
                promptPrefs.isCoolingDown = true
                promptPrefs.lastActiveIntensity = activity.intensity.name
            } else if (promptPrefs.isCoolingDown && activity.intensity == ActivityIntensity.SEDENTARY) {
                promptPrefs.isCoolingDown = false
                shouldPrompt = true
                activeTrigger =
                    if (promptPrefs.lastActiveIntensity == ActivityIntensity.VIGOROUS.name) {
                        ContextTrigger.POST_VIGOROUS
                    } else {
                        ContextTrigger.POST_MODERATE
                    }
            }

            // Trigger B: Screen Fatigue (Continuous session > 45 mins)
            if (!shouldPrompt && interaction.currentSessionDurationMs > 45 * 60_000L) {
                shouldPrompt = true
                activeTrigger = ContextTrigger.SCREEN_FATIGUE
            }

            // Trigger C: The Settle In / Late Night
            if (!shouldPrompt && interaction.unlockCount > promptPrefs.lastUnlockCount) {
                promptPrefs.lastUnlockCount = interaction.unlockCount

                if (activity.intensity == ActivityIntensity.SEDENTARY && activity.sedentaryMinutes > 15) {
                    shouldPrompt = true
                    activeTrigger =
                        if (hour >= 21) {
                            ContextTrigger.LATE_NIGHT_WIND_DOWN
                        } else {
                            ContextTrigger.PROLONGED_REST
                        }
                }
            }

            // 5. Fire Intent (Caller will perform final DB verification)
            if (shouldPrompt && activeTrigger != null) {
                return PromptEvaluationResult(true, getDynamicPrompt(activeTrigger, hour))
            }

            return PromptEvaluationResult(false)
        }

        private enum class ContextTrigger {
            POST_VIGOROUS,
            POST_MODERATE,
            PROLONGED_REST,
            SCREEN_FATIGUE,
            LATE_NIGHT_WIND_DOWN,
        }

        private fun getDynamicPrompt(
            trigger: ContextTrigger,
            hour: Int,
        ): String {
            return when (trigger) {
                ContextTrigger.POST_VIGOROUS ->
                    listOf(
                        "Great effort! How's your energy settling?",
                        "Heart rate coming down. How are you feeling?",
                        "That was intense! Take a breath and check in with your body.",
                    ).random()

                ContextTrigger.POST_MODERATE ->
                    listOf(
                        "Nice movement! How's your headspace?",
                        "A good brisk pace. How are you feeling now?",
                        "You've just finished moving. How is your energy?",
                    ).random()

                ContextTrigger.PROLONGED_REST ->
                    listOf(
                        "You've been resting for a bit. How are you feeling?",
                        "Taking it easy today? Let's do a quick check-in.",
                        "A quiet moment. How is your energy right now?",
                    ).random()

                ContextTrigger.SCREEN_FATIGUE ->
                    listOf(
                        "You've been on your device a while. How are your eyes and energy?",
                        "Time for a quick screen break? How are you feeling?",
                        "Deep in focus or just scrolling? Let's do a quick mental check-in.",
                    ).random()

                ContextTrigger.LATE_NIGHT_WIND_DOWN ->
                    listOf(
                        "It's getting late. How's your mind winding down?",
                        "Still up? Be gentle with yourself. How are you feeling?",
                        "Late night check-in. What's your energy like right now?",
                    ).random()
            }
        }
    }
