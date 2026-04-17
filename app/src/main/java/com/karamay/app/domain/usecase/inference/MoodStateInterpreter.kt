package com.karamay.app.domain.usecase.inference

import com.karamay.app.domain.model.mood.Arousal
import com.karamay.app.domain.model.mood.Valence
import javax.inject.Inject

class MoodStateInterpreter @Inject constructor() {
    fun interpret(valence: Valence, arousal: Arousal): String {
        return when (valence) {
            Valence.NEGATIVE -> when (arousal) {
                Arousal.LOW  -> "Running on empty?"
                Arousal.MID  -> "Feeling a bit heavy?"
                Arousal.HIGH -> "Carrying some tension?"
            }
            Valence.NEUTRAL -> when (arousal) {
                Arousal.LOW  -> "A bit foggy today?"
                Arousal.MID  -> "Taking it as it comes?"
                Arousal.HIGH -> "Feeling a bit restless?"
            }
            Valence.POSITIVE -> when (arousal) {
                Arousal.LOW  -> "Peaceful and grounded?"
                Arousal.MID  -> "Steady and content?"
                Arousal.HIGH -> "Bright and energized?"
            }
        }
    }
}