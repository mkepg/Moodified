package com.moodified.app.domain.usecase.intervention

import com.moodified.app.domain.model.inference.InferredMoodState
import com.moodified.app.domain.model.intervention.InterventionAction
import com.moodified.app.domain.model.intervention.RoutinePhase
import com.moodified.app.domain.model.intervention.RoutineType
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.Valence
import com.moodified.app.domain.repository.InterventionRepository
import java.time.LocalDateTime
import javax.inject.Inject

open class SelectGuidedRoutineUseCase
    @Inject
    constructor(
        private val interventionRepository: InterventionRepository,
    ) {
        open suspend operator fun invoke(
            moodState: InferredMoodState,
            currentCooldownMs: Long,
            now: LocalDateTime = LocalDateTime.now(),
        ): InterventionAction.GuidedRoutine? {
            // 1. Welcome Onboarding check
            val hasSeenWelcome = interventionRepository.getLastShownTime("routine_welcome") != null
            if (!hasSeenWelcome) {
                return buildWelcomeRoutine()
            }

            val hour = now.hour

            // 2. Identify valid candidates (Circadian anchors take precedence over acute daytime states)
            val candidates =
                when {
                    // Night & Early Morning Anchors
                    hour >= 21 || hour < 5 ->
                        listOf(
                            buildWindDownRoutine(),
                            buildEveningReflectionRoutine(),
                        )
                    hour in 5..9 ->
                        listOf(
                            buildMorningAnchorRoutine(),
                            buildMorningActivationRoutine(),
                        )
                    // Acute Distress Interruption (Daytime: 10:00 - 20:59)
                    moodState.valence == Valence.NEGATIVE && moodState.arousal == Arousal.LOW ->
                        listOf(
                            buildRecoveryBreakRoutine(),
                            buildEmotionalGroundingRoutine(),
                        )
                    // Evening Transition / Workday Decompression (18:00 - 20:59)
                    hour in 18..20 ->
                        listOf(
                            buildEveningTransitionRoutine(),
                            // Reused here as a decompression tool
                            buildRecoveryBreakRoutine(),
                        )
                    // Daytime Sluggishness / Focus (10:00 - 17:59)
                    moodState.arousal == Arousal.LOW && hour in 10..17 ->
                        listOf(
                            buildFocusSessionRoutine(),
                            buildDeepWorkRoutine(),
                        )
                    // User is active/positive during the day; no forced routine needed.
                    else -> emptyList()
                }

            if (candidates.isEmpty()) return null

            // 3. Filter out candidates currently on cooldown to prevent masking
            val availableCandidates =
                candidates.filter {
                    !interventionRepository.isOnCooldown(it.id, currentCooldownMs)
                }

            if (availableCandidates.isEmpty()) return null

            // 4. Adaptive Rotation: Pick the available routine that was least recently shown
            var selectedRoutine: InterventionAction.GuidedRoutine? = null
            var oldestTime = Long.MAX_VALUE

            for (routine in availableCandidates) {
                val history = interventionRepository.getInterventionHistory(routine.id)

                // If it has never been shown, prioritize it immediately
                if (history == null) {
                    return routine
                }

                if (history.lastShownAtMillis < oldestTime) {
                    oldestTime = history.lastShownAtMillis
                    selectedRoutine = routine
                }
            }

            return selectedRoutine
        }

        private fun buildWelcomeRoutine() =
            InterventionAction.GuidedRoutine(
                id = "routine_welcome",
                priority = 100,
                phases =
                    listOf(
                        RoutinePhase("Welcome to Care", "Let's take a moment to explore how guided routines work.", 15),
                        RoutinePhase("Breathe", "Take a deep breath in, and let it out. We're here to support your rhythms.", 20),
                        RoutinePhase("Ready", "You're all set. Check back anytime you need a quick reset.", 10),
                    ),
                estimatedMinutes = 1,
                routineType = RoutineType.WELCOME,
            )

        private fun buildWindDownRoutine() =
            InterventionAction.GuidedRoutine(
                id = "routine_wind_down",
                priority = 90,
                phases =
                    listOf(
                        RoutinePhase("Disconnect", "Put your devices away and find a comfortable spot.", 30),
                        RoutinePhase("Deep Breathing", "Inhale deeply for 4s, hold for 4s, exhale for 6s.", 60),
                        RoutinePhase("Settle", "Allow your muscles to relax into the surface beneath you.", 30),
                    ),
                estimatedMinutes = 2,
                routineType = RoutineType.WIND_DOWN,
            )

        private fun buildEveningReflectionRoutine() =
            InterventionAction.GuidedRoutine(
                id = "routine_evening_reflection",
                priority = 90,
                phases =
                    listOf(
                        RoutinePhase("Brain Dump", "Jot down any lingering thoughts from today to get them out of your head.", 60),
                        RoutinePhase("Gratitude", "Think of one small, specific thing that went well today.", 30),
                        RoutinePhase("Rest", "Close your eyes and let your breathing return to normal.", 30),
                    ),
                estimatedMinutes = 2,
                routineType = RoutineType.WIND_DOWN,
            )

        private fun buildEveningTransitionRoutine() =
            InterventionAction.GuidedRoutine(
                id = "routine_evening_transition",
                priority = 85,
                phases =
                    listOf(
                        RoutinePhase("Close the Loop", "Take 2 minutes to write down any lingering tasks for tomorrow.", 120),
                        RoutinePhase("Physical Shift", "Change into comfortable clothes or stretch your back.", 60),
                        RoutinePhase("Mental Clear", "Take three deep breaths, signaling the end of your workday.", 30),
                    ),
                estimatedMinutes = 3,
                routineType = RoutineType.WIND_DOWN,
            )

        private fun buildMorningAnchorRoutine() =
            InterventionAction.GuidedRoutine(
                id = "routine_morning_anchor",
                priority = 85,
                phases =
                    listOf(
                        RoutinePhase("Hydrate", "Drink a full glass of water to wake up your body.", 30),
                        RoutinePhase("Set Intentions", "Think of one priority you have for today.", 60),
                        RoutinePhase("Light Stretch", "Reach up high, then touch your toes.", 120),
                    ),
                estimatedMinutes = 3,
                routineType = RoutineType.MORNING_ANCHOR,
            )

        private fun buildMorningActivationRoutine() =
            InterventionAction.GuidedRoutine(
                id = "routine_morning_activation",
                priority = 85,
                phases =
                    listOf(
                        RoutinePhase("Deep Breaths", "Take three deep, energizing breaths to oxygenate your body.", 30),
                        RoutinePhase("Sunlight", "Look out a window or step outside to get natural light in your eyes.", 60),
                        RoutinePhase("Quick Move", "Do 10 jumping jacks or jog in place to get the blood flowing.", 60),
                    ),
                estimatedMinutes = 3,
                routineType = RoutineType.MORNING_ANCHOR,
            )

        private fun buildRecoveryBreakRoutine() =
            InterventionAction.GuidedRoutine(
                id = "routine_recovery_break",
                priority = 95,
                phases =
                    listOf(
                        RoutinePhase("Acknowledge", "Notice the heavy feeling without judging it.", 60),
                        RoutinePhase("Box Breathing", "Breathe in 4s, hold 4s, out 4s, hold 4s.", 180),
                        RoutinePhase("Re-center", "Gently open your eyes and stretch your shoulders.", 60),
                    ),
                estimatedMinutes = 5,
                routineType = RoutineType.RECOVERY_BREAK,
            )

        private fun buildEmotionalGroundingRoutine() =
            InterventionAction.GuidedRoutine(
                id = "routine_emotional_grounding",
                priority = 95,
                phases =
                    listOf(
                        RoutinePhase("5-4-3-2-1", "Name 5 things you see, 4 you can touch, 3 you hear, 2 you smell, 1 you taste.", 120),
                        RoutinePhase("Physical Anchor", "Press your feet firmly into the ground or hold a cold object.", 60),
                        RoutinePhase("Self-Compassion", "Remind yourself that it is okay to feel this way right now.", 60),
                    ),
                estimatedMinutes = 4,
                routineType = RoutineType.RECOVERY_BREAK,
            )

        private fun buildFocusSessionRoutine() =
            InterventionAction.GuidedRoutine(
                id = "routine_focus_session",
                priority = 75,
                phases =
                    listOf(
                        RoutinePhase("Clear Distractions", "Close unnecessary tabs and mute notifications.", 60),
                        RoutinePhase("Pomodoro", "Focus deeply on a single task.", 1500),
                        RoutinePhase("Short Break", "Stand up, stretch, and look away from the screen.", 300),
                    ),
                estimatedMinutes = 30,
                routineType = RoutineType.FOCUS_SESSION,
            )

        private fun buildDeepWorkRoutine() =
            InterventionAction.GuidedRoutine(
                id = "routine_deep_work",
                priority = 75,
                phases =
                    listOf(
                        RoutinePhase("Set Goal", "Define exactly what you will accomplish in this session.", 60),
                        RoutinePhase("Immersion", "Work without breaking concentration.", 1800),
                        RoutinePhase("Step Away", "Physically leave your workspace to rest your mind.", 300),
                    ),
                estimatedMinutes = 35,
                routineType = RoutineType.FOCUS_SESSION,
            )
    }
