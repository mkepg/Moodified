package com.karamay.app.domain.usecase.sleep

import com.karamay.app.domain.model.DailySleepSummary
import com.karamay.app.domain.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class GetDailySleepSummaryUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    operator fun invoke(date: LocalDate): Flow<DailySleepSummary?> {
        return repository.getDailySummary(date)
    }
}