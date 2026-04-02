package com.karamay.app.domain.repository

import com.karamay.app.domain.model.MoodEntry
import kotlinx.coroutines.flow.Flow

interface MoodRepository {
    fun getAllEntries(): Flow<List<MoodEntry>>
    fun getTodayEntries(): Flow<List<MoodEntry>>
    suspend fun insertEntry(entry: MoodEntry): Long
    suspend fun deleteEntry(id: Long)
    suspend fun getLatestEntry(): MoodEntry?
}
