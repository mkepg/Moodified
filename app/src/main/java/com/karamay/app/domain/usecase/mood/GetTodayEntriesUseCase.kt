package com.karamay.app.domain.usecase.mood

import com.karamay.app.domain.model.MoodEntry
import com.karamay.app.domain.repository.MoodRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTodayEntriesUseCase @Inject constructor(
    private val repository: MoodRepository
) {
    operator fun invoke(): Flow<List<MoodEntry>> = repository.getTodayEntries()
}
