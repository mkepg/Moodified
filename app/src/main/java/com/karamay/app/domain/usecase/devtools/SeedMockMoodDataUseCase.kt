package com.karamay.app.domain.usecase.devtools

import com.karamay.app.core.debug.MockMoodDataGenerator
import com.karamay.app.domain.repository.MoodRepository
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