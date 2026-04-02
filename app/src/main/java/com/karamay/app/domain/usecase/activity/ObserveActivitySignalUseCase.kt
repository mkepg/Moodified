package com.karamay.app.domain.usecase.activity

import com.karamay.app.domain.model.ActivitySignal
import com.karamay.app.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Exposes the live [ActivitySignal] stream from the repository.
 * Follows the same operator invoke pattern as the mood use cases.
 */
class ObserveActivitySignalUseCase @Inject constructor(
    private val repository: ActivityRepository
) {
    operator fun invoke(): Flow<ActivitySignal> = repository.observeSignal()
}
