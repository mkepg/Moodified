package com.karamay.app.domain.usecase.activity

import com.karamay.app.domain.model.DailyActivitySummary
import com.karamay.app.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class GetDailyActivitySummaryUseCase @Inject constructor(
    private val repository: ActivityRepository
) {
    operator fun invoke(date: LocalDate): Flow<DailyActivitySummary?> =
        repository.getDailySummary(date)
}
