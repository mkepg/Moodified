package com.moodified.app.domain.usecase.devtools

import com.moodified.app.core.debug.MockMoodDataGenerator
import com.moodified.app.domain.repository.MoodRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SeedMockMoodDataUseCase @Inject constructor(
    private val repository: MoodRepository
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        val mockData = MockMoodDataGenerator.generate(daysBack = 14)
        mockData.forEach { entry ->
            repository.insertEntry(entry)
        }
    }
}