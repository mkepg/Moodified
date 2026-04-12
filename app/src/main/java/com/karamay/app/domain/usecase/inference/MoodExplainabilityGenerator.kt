package com.karamay.app.domain.usecase.inference

import com.karamay.app.domain.model.inference.ScoringEvent
import javax.inject.Inject

class MoodExplainabilityGenerator @Inject constructor() {

    fun generateExplanation(events: List<ScoringEvent>): String {
        if (events.isEmpty()) {
            return "Reflecting your general rhythm and habits today."
        }

        // Cap output to the top 3 most impactful events
        val topEvents = events.take(3)

        // Separate by Valence impact
        val positiveEvents = topEvents.filter { it.valenceDelta >= 0 }
        val negativeEvents = topEvents.filter { it.valenceDelta < 0 }

        if (topEvents.size == 1) {
            val primary = topEvents.first()
            return if (primary.valenceDelta >= 0) {
                "Lifted by ${primary.description}."
            } else {
                "Gently weighed down by ${primary.description}."
            }
        }

        // All positive
        if (negativeEvents.isEmpty()) {
            val formatted = formatList(positiveEvents.map { it.description })
            return "Boosted by $formatted."
        }

        // All negative
        if (positiveEvents.isEmpty()) {
            val formatted = formatList(negativeEvents.map { it.description })
            return "Weighed down by $formatted."
        }

        // Mixed (Contrastive Logic)
        val primaryEvent = topEvents.first()
        val isPrimaryPositive = primaryEvent.valenceDelta >= 0

        return if (isPrimaryPositive) {
            val posStrings = formatList(positiveEvents.map { it.description })
            val negStrings = formatList(negativeEvents.map { it.description })
            "Lifted by $posStrings, despite $negStrings."
        } else {
            val negStrings = formatList(negativeEvents.map { it.description })
            val posStrings = formatList(positiveEvents.map { it.description })
            "Weighed down by $negStrings, though softened by $posStrings."
        }
    }

    private fun formatList(items: List<String>): String {
        if (items.isEmpty()) return ""
        if (items.size == 1) return items.first()
        if (items.size == 2) return "${items[0]} and ${items[1]}"
        val allButLast = items.dropLast(1).joinToString(", ")
        return "$allButLast, and ${items.last()}"
    }
}