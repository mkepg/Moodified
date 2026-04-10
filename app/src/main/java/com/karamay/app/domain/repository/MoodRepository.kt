package com.karamay.app.domain.repository

import com.karamay.app.domain.model.mood.MoodEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface MoodRepository {
    fun getAllEntries(): Flow<List<MoodEntry>>
    fun getTodayEntries(): Flow<List<MoodEntry>>
    fun getEntriesForDate(date: LocalDate): Flow<List<MoodEntry>>
    // Sprint 2: Range queries for inference window
    fun getEntriesInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<MoodEntry>>
    suspend fun insertEntry(entry: MoodEntry): Long
    suspend fun deleteEntry(id: Long)
}