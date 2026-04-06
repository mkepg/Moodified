// app/src/main/java/com/karamay/app/domain/usecase/interaction/GetDailyInteractionSessionsUseCase.kt
package com.karamay.app.domain.usecase.interaction

import com.karamay.app.domain.model.interaction.InteractionSession
import com.karamay.app.domain.repository.InteractionRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class GetDailyInteractionSessionsUseCase @Inject constructor(
    private val repository: InteractionRepository
) {
    operator fun invoke(date: LocalDate): Flow<List<InteractionSession>> =
        repository.getSessionsForDate(date)
}