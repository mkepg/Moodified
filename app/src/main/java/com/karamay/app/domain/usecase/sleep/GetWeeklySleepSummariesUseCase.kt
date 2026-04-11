package com.karamay.app.domain.usecase.sleep

import com.karamay.app.domain.model.sleep.DailySleepSummary
import com.karamay.app.domain.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class GetWeeklySleepSummariesUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    operator fun invoke(endDate: LocalDate): Flow<List<DailySleepSummary>> =
        repository.getWeeklySummaries(endDate)
}
