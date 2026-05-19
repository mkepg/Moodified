package com.moodified.app.domain.usecase.activity

import com.moodified.app.domain.model.activity.ActivityDailySummary
import com.moodified.app.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class GetDailyActivitySummaryUseCase @Inject constructor(
    private val repository: ActivityRepository
) {
    operator fun invoke(date: LocalDate): Flow<ActivityDailySummary?> =
        repository.getDailySummary(date)
}
