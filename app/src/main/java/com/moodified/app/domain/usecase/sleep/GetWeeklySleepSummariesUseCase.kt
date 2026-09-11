package com.moodified.app.domain.usecase.sleep

import com.moodified.app.domain.model.sleep.DailySleepSummary
import com.moodified.app.domain.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class GetWeeklySleepSummariesUseCase
    @Inject
    constructor(
        private val repository: SleepRepository,
    ) {
        operator fun invoke(endDate: LocalDate): Flow<List<DailySleepSummary>> = repository.getWeeklySummaries(endDate)
    }
