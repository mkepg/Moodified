package com.karamay.app.domain.model.intervention

import com.karamay.app.domain.model.mood.Arousal
import com.karamay.app.domain.model.mood.Valence

sealed interface InterventionAction {
    val id: String
    val priority: Int

    data class Guidance(
        override val id: String,
        override val priority: Int,
        val title: String,
        val description: String
    ) : InterventionAction

    data class Motivation(
        override val id: String,
        override val priority: Int,
        val title: String,
        val description: String
    ) : InterventionAction

    data class MicroConfirmation(
        override val id: String,
        override val priority: Int = 100, // Highest priority to intercept user
        val prompt: String,
        val inferredValence: Valence,
        val inferredArousal: Arousal
    ) : InterventionAction
}