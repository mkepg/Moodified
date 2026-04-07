package com.karamay.app.domain.usecase.mood

import com.karamay.app.domain.model.mood.MoodEntry
import com.karamay.app.domain.repository.MoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * Groups all mood entries by local date, sorted newest-first.
 * Returns a map of LocalDate → list-of-entries (oldest entry first within each day).
 *
 * Using getAllEntries() — which already exists on MoodRepository — means no new
 * data-layer contract is required; this use case owns the grouping logic exclusively.
 */
class GetMoodHistoryUseCase @Inject constructor(
    private val repository: MoodRepository
) {
    operator fun invoke(): Flow<Map<LocalDate, List<MoodEntry>>> =
        repository.getAllEntries().map { entries ->
            entries
                .groupBy { it.timestamp.toLocalDate() }
                .toSortedMap(compareByDescending { it }) // newest date first
        }
}
