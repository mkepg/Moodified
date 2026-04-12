package com.karamay.app.domain.usecase.inference

import com.karamay.app.domain.model.inference.ScoringEvent
import javax.inject.Inject

class MoodExplainabilityGenerator @Inject constructor() {
    fun generateExplanation(events: List<ScoringEvent>): String {
        if (events.isEmpty()) {
            return "Reflecting your general rhythm and habits today."
        }
        if (events.size == 1) {
            return "Gently shaped by ${events.first().description}."
        }
        val primary    = events.first()
        val secondaries = events.drop(1)
        return if (secondaries.size == 1) {
            "Mostly shaped by ${primary.description}, along with ${secondaries.first().description}."
        } else {
            val rest = secondaries.dropLast(1).joinToString(", ") { it.description }
            val last = secondaries.last().description
            "Mostly shaped by ${primary.description}, along with $rest and $last."
        }
    }
}