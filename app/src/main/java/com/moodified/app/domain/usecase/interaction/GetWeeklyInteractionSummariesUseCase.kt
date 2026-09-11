// app/src/main/java/com/moodified/app/domain/usecase/interaction/GetWeeklyInteractionSummariesUseCase.kt
package com.moodified.app.domain.usecase.interaction

import com.moodified.app.domain.model.interaction.InteractionDailySummary
import com.moodified.app.domain.repository.InteractionRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class GetWeeklyInteractionSummariesUseCase
    @Inject
    constructor(
        private val repository: InteractionRepository,
    ) {
        operator fun invoke(endDate: LocalDate): Flow<List<InteractionDailySummary>> = repository.getWeeklySummaries(endDate)
    }
