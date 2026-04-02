package com.karamay.app.domain.usecase.mood

import com.karamay.app.domain.model.MoodEntry
import com.karamay.app.domain.repository.MoodRepository
import javax.inject.Inject

class GetLatestEntryUseCase @Inject constructor(
    private val repository: MoodRepository
) {
    suspend operator fun invoke(): MoodEntry? = repository.getLatestEntry()
}
