package com.moodified.app.data.repository

import com.moodified.app.data.local.dao.mood.MoodEntryDao
import com.moodified.app.data.local.datasource.PromptPreferencesDataSource
import com.moodified.app.data.local.entity.mood.MoodEntryEntity
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.repository.MoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoodRepositoryImpl
    @Inject
    constructor(
        private val dao: MoodEntryDao,
        private val promptPrefs: PromptPreferencesDataSource,
    ) : MoodRepository {
        override fun getAllEntries(): Flow<List<MoodEntry>> = dao.getAllEntries().map { entities -> entities.map { it.toDomain() } }

        override fun getTodayEntries(): Flow<List<MoodEntry>> {
            val today = LocalDate.now()
            return getEntriesForDate(today)
        }

        override fun getEntriesForDate(date: LocalDate): Flow<List<MoodEntry>> {
            val startOfDayMillis = date.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDayMillis = date.plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            return dao.getEntriesBetween(startOfDayMillis, endOfDayMillis)
                .map { entities -> entities.map { it.toDomain() } }
        }

        override fun getEntriesInRange(
            startDate: LocalDate,
            endDate: LocalDate,
        ): Flow<List<MoodEntry>> {
            val startMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            return dao.getEntriesBetween(startMillis, endMillis)
                .map { entities -> entities.map { it.toDomain() } }
        }

        override suspend fun insertEntry(entry: MoodEntry): Long {
            val id = dao.insert(MoodEntryEntity.fromDomain(entry))
            recordUserMoodAction()
            return id
        }

        override suspend fun updateEntry(entry: MoodEntry) {
            dao.update(MoodEntryEntity.fromDomain(entry))
            recordUserMoodAction()
        }

        override suspend fun getEntryById(id: Long): MoodEntry? = dao.getById(id)?.toDomain()

        override suspend fun deleteEntry(id: Long) = dao.deleteById(id)

        // Track wall-clock time of the user's most recent mood-logging action so the
        // micro-prompt cooldown reflects "user just took action" even when the entry
        // itself was backdated to a past date (which would otherwise leave
        // getTodayEntries() unaware of the just-performed save).
        private fun recordUserMoodAction() {
            promptPrefs.lastMoodActionMs = System.currentTimeMillis()
        }
    }
