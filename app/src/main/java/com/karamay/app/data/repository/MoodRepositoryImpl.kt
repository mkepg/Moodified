package com.karamay.app.data.repository

import com.karamay.app.data.local.dao.MoodEntryDao
import com.karamay.app.data.local.entity.MoodEntryEntity
import com.karamay.app.domain.model.MoodEntry
import com.karamay.app.domain.repository.MoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoodRepositoryImpl @Inject constructor(
    private val dao: MoodEntryDao
) : MoodRepository {

    // Fix #34: Returns a reactive Flow so CheckInViewModel always reflects the latest insert.
    override fun observeLatestEntry(): Flow<MoodEntry?> =
        dao.observeLatestEntry().map { it?.toDomain() }

    override fun getAllEntries(): Flow<List<MoodEntry>> =
        dao.getAllEntries().map { entities -> entities.map { it.toDomain() } }

    override fun getTodayEntries(): Flow<List<MoodEntry>> {
        val todayPrefix = LocalDate.now().toString()
        return dao.getEntriesByDate(todayPrefix)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun insertEntry(entry: MoodEntry): Long =
        dao.insert(MoodEntryEntity.fromDomain(entry))

    override suspend fun deleteEntry(id: Long) =
        dao.deleteById(id)
}
