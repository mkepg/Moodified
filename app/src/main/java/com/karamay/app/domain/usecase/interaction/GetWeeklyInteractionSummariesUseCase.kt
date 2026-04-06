// app/src/main/java/com/karamay/app/domain/usecase/interaction/GetWeeklyInteractionSummariesUseCase.kt
package com.karamay.app.domain.usecase.interaction

import com.karamay.app.domain.model.interaction.InteractionDailySummary
import com.karamay.app.domain.repository.InteractionRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class GetWeeklyInteractionSummariesUseCase @Inject constructor(
    private val repository: InteractionRepository
) {
    operator fun invoke(endDate: LocalDate): Flow<List<InteractionDailySummary>> =
        repository.getWeeklySummaries(endDate)
}