package com.karamay.app.domain.usecase.activity

import com.karamay.app.domain.model.activity.ActivityDailySummary
import com.karamay.app.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class GetWeeklyActivitySummariesUseCase @Inject constructor(
    private val repository: ActivityRepository
) {
    operator fun invoke(endDate: LocalDate): Flow<List<ActivityDailySummary>> =
        repository.getWeeklySummaries(endDate)
}
