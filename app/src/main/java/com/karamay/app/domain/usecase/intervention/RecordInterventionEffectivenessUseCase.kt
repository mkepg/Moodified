package com.karamay.app.domain.usecase.intervention

import com.karamay.app.domain.repository.InterventionRepository
import javax.inject.Inject

class RecordInterventionEffectivenessUseCase @Inject constructor(
    private val repository: InterventionRepository
) {
    suspend operator fun invoke(id: String, feedback: String, wasCompleted: Boolean = false) {
        repository.recordFeedback(id, feedback, wasCompleted)
    }
}