// app/src/main/java/com/moodified/app/domain/usecase/interaction/GetDailyInteractionSummaryUseCase.kt
package com.moodified.app.domain.usecase.interaction

import com.moodified.app.domain.model.interaction.InteractionDailySummary
import com.moodified.app.domain.repository.InteractionRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class GetDailyInteractionSummaryUseCase @Inject constructor(
    private val repository: InteractionRepository
) {
    operator fun invoke(date: LocalDate): Flow<InteractionDailySummary?> =
        repository.getDailySummary(date)
}