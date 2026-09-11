package com.moodified.app.domain.usecase.activity

import com.moodified.app.domain.model.activity.ActivitySignal
import com.moodified.app.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveActivitySignalUseCase
    @Inject
    constructor(
        private val repository: ActivityRepository,
    ) {
        operator fun invoke(): Flow<ActivitySignal> = repository.observeSignal()
    }
