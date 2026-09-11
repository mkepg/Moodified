package com.moodified.app.domain.usecase.privacy

import com.moodified.app.data.local.database.MoodifiedDatabase
import com.moodified.app.domain.repository.ActivityRepository
import com.moodified.app.domain.repository.InteractionRepository
import com.moodified.app.domain.repository.SleepRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Stops all background tracking and wipes every Room table so the user can
 * exercise their right-to-erasure. Datastore preferences are retained so the
 * app remains usable (the user can re-enable tracking and re-grant permissions).
 */
class DeleteAllUserDataUseCase
    @Inject
    constructor(
        private val database: MoodifiedDatabase,
        private val activityRepository: ActivityRepository,
        private val sleepRepository: SleepRepository,
        private val interactionRepository: InteractionRepository,
    ) {
        suspend operator fun invoke() =
            withContext(Dispatchers.IO) {
                activityRepository.stopTracking()
                sleepRepository.stopTracking()
                interactionRepository.stopTracking()
                database.clearAllTables()
            }
    }
