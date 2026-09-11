package com.moodified.app.domain.usecase.devtools

import com.moodified.app.core.debug.MockActivityDataGenerator
import com.moodified.app.domain.repository.ActivityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SeedMockActivityDataUseCase
    @Inject
    constructor(
        private val repository: ActivityRepository,
    ) {
        suspend operator fun invoke() =
            withContext(Dispatchers.IO) {
                val mockData = MockActivityDataGenerator.generate(daysBack = 14)
                mockData.forEach { summary ->
                    repository.insertMockSummary(summary)
                }
            }
    }
