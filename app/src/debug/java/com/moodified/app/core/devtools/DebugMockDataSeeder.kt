package com.moodified.app.core.devtools

import com.moodified.app.domain.usecase.devtools.SeedMockActivityDataUseCase
import com.moodified.app.domain.usecase.devtools.SeedMockMoodDataUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugMockDataSeeder @Inject constructor(
    private val seedMoodData: SeedMockMoodDataUseCase,
    private val seedActivityData: SeedMockActivityDataUseCase,
) : MockDataSeeder {
    override val isAvailable: Boolean = true
    override suspend fun seedMoodData() = seedMoodData.invoke()
    override suspend fun seedActivityData() = seedActivityData.invoke()
}
