// app/src/main/java/com/moodified/app/domain/usecase/interaction/ObserveInteractionSignalUseCase.kt
package com.moodified.app.domain.usecase.interaction

import com.moodified.app.domain.model.interaction.InteractionSignal
import com.moodified.app.domain.repository.InteractionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveInteractionSignalUseCase
    @Inject
    constructor(
        private val repository: InteractionRepository,
    ) {
        operator fun invoke(): Flow<InteractionSignal> = repository.observeLiveSignal()
    }
