package com.karamay.app.domain.repository

import com.karamay.app.domain.model.MoodEntry
import kotlinx.coroutines.flow.Flow

interface MoodRepository {
    // Fix #34: getLatestEntry() now returns Flow<MoodEntry?> instead of a one-shot suspend fun.
    // CheckInViewModel was fetching a single value in init{} and never updating it.
    // When QuickLogSheet saves a new entry while CheckInScreen is visible, the latest
    // entry card now updates reactively instead of showing stale data.
    fun observeLatestEntry(): Flow<MoodEntry?>

    fun getAllEntries(): Flow<List<MoodEntry>>   // Reserved for Phase 3 history export
    fun getTodayEntries(): Flow<List<MoodEntry>>
    suspend fun insertEntry(entry: MoodEntry): Long
    suspend fun deleteEntry(id: Long)
}
