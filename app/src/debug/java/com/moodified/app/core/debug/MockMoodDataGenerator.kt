package com.moodified.app.core.debug

import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.model.mood.Valence
import java.time.LocalDateTime
import kotlin.random.Random

object MockMoodDataGenerator {
    fun generate(daysBack: Int = 14): List<MoodEntry> {
        val entries = mutableListOf<MoodEntry>()
        val now = LocalDateTime.now()
        val valences = Valence.entries.toTypedArray()
        val arousals = Arousal.entries.toTypedArray()

        for (i in 0..daysBack) {
            // Simulate 1 to 4 check-ins per day
            val entriesPerDay = Random.Default.nextInt(1, 5)
            val targetDate = now.minusDays(i.toLong())

            for (j in 0 until entriesPerDay) {
                // Spread times throughout the day (8 AM to 10 PM)
                val randomHour = Random.Default.nextInt(8, 23)
                val randomMinute = Random.Default.nextInt(0, 60)
                val timestamp = targetDate.withHour(randomHour).withMinute(randomMinute)

                entries.add(
                    MoodEntry(
                        valence = valences.random(),
                        arousal = arousals.random(),
                        timestamp = timestamp,
                        isManual = true,
                    ),
                )
            }
        }

        // Sort chronologically so it mimics sequential insertion
        return entries.sortedBy { it.timestamp }
    }
}
