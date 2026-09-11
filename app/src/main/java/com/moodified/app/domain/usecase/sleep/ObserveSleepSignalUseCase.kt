package com.moodified.app.domain.usecase.sleep

import com.moodified.app.domain.model.sleep.SleepSignal
import com.moodified.app.domain.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSleepSignalUseCase
    @Inject
    constructor(
        private val repository: SleepRepository,
    ) {
        operator fun invoke(): Flow<SleepSignal> = repository.observeLiveSignal()
    }
