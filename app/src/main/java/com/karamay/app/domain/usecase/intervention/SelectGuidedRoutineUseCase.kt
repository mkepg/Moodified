package com.karamay.app.domain.usecase.intervention

import com.karamay.app.domain.model.inference.InferredMoodState
import com.karamay.app.domain.model.intervention.InterventionAction
import com.karamay.app.domain.model.intervention.RoutinePhase
import com.karamay.app.domain.model.intervention.RoutineType
import com.karamay.app.domain.model.mood.Arousal
import com.karamay.app.domain.model.mood.Valence
import com.karamay.app.domain.repository.InterventionRepository
import java.time.LocalDateTime
import javax.inject.Inject

class SelectGuidedRoutineUseCase @Inject constructor(
    private val interventionRepository: InterventionRepository
) {
    suspend operator fun invoke(moodState: InferredMoodState, now: LocalDateTime = LocalDateTime.now()): InterventionAction.GuidedRoutine? {

        // 1. Check if the user has completed the welcome routine
        val hasSeenWelcome = interventionRepository.getLastShownTime("routine_welcome") != null
        if (!hasSeenWelcome) {
            return InterventionAction.GuidedRoutine(
                id = "routine_welcome",
                priority = 100, // Highest priority to ensure it surfaces first
                phases = listOf(
                    RoutinePhase("Welcome to Care", "Let's take a moment to explore how guided routines work.", 15),
                    RoutinePhase("Breathe", "Take a deep breath in, and let it out. We're here to support your rhythms.", 20),
                    RoutinePhase("Ready", "You're all set. Check back anytime you need a quick reset.", 10)
                ),
                estimatedMinutes = 1,
                routineType = RoutineType.WELCOME
            )
        }

        val hour = now.hour

        return when {
            // Late night -> Wind Down (Shortened to 2 minutes)
            hour >= 21 || hour < 4 -> InterventionAction.GuidedRoutine(
                id = "routine_wind_down",
                priority = 90,
                phases = listOf(
                    RoutinePhase("Disconnect", "Put your devices away and find a comfortable spot.", 30),
                    RoutinePhase("Deep Breathing", "Inhale deeply for 4s, hold for 4s, exhale for 6s.", 60),
                    RoutinePhase("Settle", "Allow your muscles to relax into the surface beneath you.", 30)
                ),
                estimatedMinutes = 2,
                routineType = RoutineType.WIND_DOWN
            )

            // Early morning -> Morning Anchor
            hour in 5..9 -> InterventionAction.GuidedRoutine(
                id = "routine_morning_anchor",
                priority = 85,
                phases = listOf(
                    RoutinePhase("Hydrate", "Drink a full glass of water to wake up your body.", 30),
                    RoutinePhase("Set Intentions", "Think of one priority you have for today.", 60),
                    RoutinePhase("Light Stretch", "Reach up high, then touch your toes.", 120)
                ),
                estimatedMinutes = 3,
                routineType = RoutineType.MORNING_ANCHOR
            )

            // Low arousal + Negative mood -> Recovery Break
            moodState.valence == Valence.NEGATIVE && moodState.arousal == Arousal.LOW -> InterventionAction.GuidedRoutine(
                id = "routine_recovery_break",
                priority = 95,
                phases = listOf(
                    RoutinePhase("Acknowledge", "Notice the heavy feeling without judging it.", 60),
                    RoutinePhase("Box Breathing", "Breathe in 4s, hold 4s, out 4s, hold 4s.", 180),
                    RoutinePhase("Re-center", "Gently open your eyes and stretch your shoulders.", 60)
                ),
                estimatedMinutes = 5,
                routineType = RoutineType.RECOVERY_BREAK
            )

            // Low arousal + Sedentary -> Focus Session
            moodState.arousal == Arousal.LOW && hour in 10..17 -> InterventionAction.GuidedRoutine(
                id = "routine_focus_session",
                priority = 75,
                phases = listOf(
                    RoutinePhase("Clear Distractions", "Close unnecessary tabs and mute notifications.", 60),
                    RoutinePhase("Pomodoro", "Focus deeply on a single task.", 1500),
                    RoutinePhase("Short Break", "Stand up, stretch, and look away from the screen.", 300)
                ),
                estimatedMinutes = 30,
                routineType = RoutineType.FOCUS_SESSION
            )

            else -> null
        }
    }
}