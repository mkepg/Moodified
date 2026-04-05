package com.karamay.app.data.repository

import com.karamay.app.data.local.dao.MoodEntryDao
import com.karamay.app.data.local.entity.MoodEntryEntity
import com.karamay.app.domain.model.MoodEntry
import com.karamay.app.domain.repository.MoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoodRepositoryImpl @Inject constructor(
    private val dao: MoodEntryDao
) : MoodRepository {

    override fun observeLatestEntry(): Flow<MoodEntry?> =
        dao.observeLatestEntry().map { it?.toDomain() }

    override fun getAllEntries(): Flow<List<MoodEntry>> =
        dao.getAllEntries().map { entities -> entities.map { it.toDomain() } }

    override fun getTodayEntries(): Flow<List<MoodEntry>> {
        val today = LocalDate.now()
        val startOfDayMillis = today.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDayMillis   = today.plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        return dao.getEntriesBetween(startOfDayMillis, endOfDayMillis)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun insertEntry(entry: MoodEntry): Long =
        dao.insert(MoodEntryEntity.fromDomain(entry))

    override suspend fun deleteEntry(id: Long) =
        dao.deleteById(id)
}