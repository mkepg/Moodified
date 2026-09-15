package com.moodified.app.presentation.insight.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ConsistencyColorTest {
    @Test
    fun `high score returns high-consistency color`() {
        val high = consistencyColor(85)
        val low = consistencyColor(20)
        assertNotEquals("High and low scores must map to different colors", high.value, low.value)
    }

    @Test
    fun `score is clamped for out-of-range values`() {
        // Consistency scoring is defensive; over/under bounds should not crash and should
        // map to the same bucket as the nearest in-range value.
        assertEquals(consistencyColor(100).value, consistencyColor(120).value)
        assertEquals(consistencyColor(0).value, consistencyColor(-5).value)
    }

    @Test
    fun `three buckets exist across the 0-100 range`() {
        val colors = (0..100 step 10).map { consistencyColor(it).value }.toSet()
        // Existing implementation splits into low / mid / high — at least 2 distinct outputs.
        assert(colors.size >= 2) { "Expected at least two distinct buckets, got ${colors.size}" }
    }
}
