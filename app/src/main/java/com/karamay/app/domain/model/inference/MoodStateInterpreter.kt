package com.karamay.app.domain.usecase.inference

import com.karamay.app.domain.model.mood.Arousal
import com.karamay.app.domain.model.mood.Valence
import javax.inject.Inject

/**
 * State Interpretation Layer: Converts valence and arousal into intuitive labels.
 */
class MoodStateInterpreter @Inject constructor() {

    fun interpret(valence: Valence, arousal: Arousal): String {
        return when (valence) {
            Valence.NEGATIVE -> when (arousal) {
                Arousal.LOW  -> "Tired / Depleted"
                Arousal.MID  -> "Down / Melancholy"
                Arousal.HIGH -> "Stressed / Anxious"
            }
            Valence.NEUTRAL -> when (arousal) {
                Arousal.LOW  -> "Fatigued / Foggy"
                Arousal.MID  -> "Neutral / Okay"
                Arousal.HIGH -> "Restless / Jittery"
            }
            Valence.POSITIVE -> when (arousal) {
                Arousal.LOW  -> "Calm / Relaxed"
                Arousal.MID  -> "Content / Balanced"
                Arousal.HIGH -> "Energized / Happy"
            }
        }
    }
}