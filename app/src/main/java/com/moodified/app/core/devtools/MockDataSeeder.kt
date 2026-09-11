package com.moodified.app.core.devtools

/**
 * Abstraction over debug-only mock data seeding so production code does not
 * link against the seed use cases. The real implementation lives in `src/debug/`;
 * `src/release/` provides a no-op that reports [isAvailable] = false.
 */
interface MockDataSeeder {
    val isAvailable: Boolean

    suspend fun seedMoodData()

    suspend fun seedActivityData()
}
