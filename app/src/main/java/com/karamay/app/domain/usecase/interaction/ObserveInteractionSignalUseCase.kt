// app/src/main/java/com/karamay/app/domain/usecase/interaction/ObserveInteractionSignalUseCase.kt
package com.karamay.app.domain.usecase.interaction

import com.karamay.app.domain.model.interaction.InteractionSignal
import com.karamay.app.domain.repository.InteractionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveInteractionSignalUseCase @Inject constructor(
    private val repository: InteractionRepository
) {
    operator fun invoke(): Flow<InteractionSignal> = repository.observeLiveSignal()
}