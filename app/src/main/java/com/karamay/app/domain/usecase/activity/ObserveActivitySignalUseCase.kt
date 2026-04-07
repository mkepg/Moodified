package com.karamay.app.domain.usecase.activity

import com.karamay.app.domain.model.activity.ActivitySignal
import com.karamay.app.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveActivitySignalUseCase @Inject constructor(
    private val repository: ActivityRepository
) {
    operator fun invoke(): Flow<ActivitySignal> = repository.observeSignal()
}