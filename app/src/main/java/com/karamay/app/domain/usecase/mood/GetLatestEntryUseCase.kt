package com.karamay.app.domain.usecase.mood

import com.karamay.app.domain.model.MoodEntry
import com.karamay.app.domain.repository.MoodRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// Fix #34: Returns Flow<MoodEntry?> instead of suspend MoodEntry?.
// CheckInViewModel now collects this continuously so latestEntry stays live
// when QuickLogSheet saves a new entry while the screen is visible.
class GetLatestEntryUseCase @Inject constructor(
    private val repository: MoodRepository
) {
    operator fun invoke(): Flow<MoodEntry?> = repository.observeLatestEntry()
}
