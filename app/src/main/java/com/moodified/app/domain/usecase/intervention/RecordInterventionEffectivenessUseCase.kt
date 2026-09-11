package com.moodified.app.domain.usecase.intervention

import com.moodified.app.domain.repository.InterventionRepository
import javax.inject.Inject

class RecordInterventionEffectivenessUseCase
    @Inject
    constructor(
        private val repository: InterventionRepository,
    ) {
        suspend operator fun invoke(
            id: String,
            feedback: String,
            wasCompleted: Boolean = false,
        ) {
            repository.recordFeedback(id, feedback, wasCompleted)
            // FIX: Start the cooldown timer immediately so the engine knows to suppress it
            repository.recordInterventionShown(id)
        }
    }
