// app/src/main/java/com/karamay/app/domain/usecase/interaction/GetDailyInteractionSummaryUseCase.kt
package com.karamay.app.domain.usecase.interaction

import com.karamay.app.domain.model.interaction.InteractionDailySummary
import com.karamay.app.domain.repository.InteractionRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class GetDailyInteractionSummaryUseCase @Inject constructor(
    private val repository: InteractionRepository
) {
    operator fun invoke(date: LocalDate): Flow<InteractionDailySummary?> =
        repository.getDailySummary(date)
}