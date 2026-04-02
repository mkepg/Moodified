package com.karamay.app.domain.usecase.sleep

import com.karamay.app.domain.model.SleepSignal
import com.karamay.app.domain.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveLiveSleepSignalUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    operator fun invoke(): Flow<SleepSignal> = repository.observeLiveSignal()
}