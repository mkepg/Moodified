package com.karamay.app.domain.usecase.inference

import com.karamay.app.domain.model.inference.ScoringEvent
import javax.inject.Inject

class MoodExplainabilityGenerator @Inject constructor() {

    /**
     * Generates a human-readable explanation from a list of [ScoringEvent]s.
     *
     * The caller is responsible for ordering events by significance (largest
     * absolute impact first). This generator surfaces the primary driver
     * explicitly and groups remaining factors so the user can immediately
     * understand what mattered most rather than reading a long flat list.
     */
    fun generateExplanation(events: List<ScoringEvent>): String {
        if (events.isEmpty()) {
            return "Based on your general behavioral baseline today."
        }

        if (events.size == 1) {
            return "Mainly influenced by ${events.first().description}."
        }

        val primary    = events.first()
        val secondaries = events.drop(1)

        return if (secondaries.size == 1) {
            "Mainly influenced by ${primary.description}, also ${secondaries.first().description}."
        } else {
            val rest = secondaries.dropLast(1).joinToString(", ") { it.description }
            val last = secondaries.last().description
            "Mainly influenced by ${primary.description}, also $rest and $last."
        }
    }
}