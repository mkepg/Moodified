package com.moodified.app.domain.usecase.activity

import com.moodified.app.domain.model.activity.ActivityIntensity
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculateActivityIntensityUseCaseTest {
    private val useCase = CalculateActivityIntensityUseCase()

    // Regression: an empty cadence window (device stationary, no step events) must resolve to
    // SEDENTARY regardless of prior state — otherwise a stale LIGHT/MODERATE/VIGOROUS lingers
    // and every idle minute accumulates as Active Time (e.g. a full night of sleep).
    @Test
    fun `empty window collapses non-sedentary state toward SEDENTARY`() {
        for (prior in ActivityIntensity.entries) {
            val result = useCase(emptyList(), prior)
            val expected =
                if (prior == ActivityIntensity.IN_VEHICLE) ActivityIntensity.IN_VEHICLE else ActivityIntensity.SEDENTARY
            assertEquals("prior=$prior", expected, result)
        }
    }

    @Test
    fun `single-sample window also collapses toward SEDENTARY`() {
        val singleSample = listOf(1_000L to 100)
        assertEquals(ActivityIntensity.SEDENTARY, useCase(singleSample, ActivityIntensity.LIGHT))
        assertEquals(ActivityIntensity.SEDENTARY, useCase(singleSample, ActivityIntensity.MODERATE))
        assertEquals(ActivityIntensity.SEDENTARY, useCase(singleSample, ActivityIntensity.VIGOROUS))
    }

    @Test
    fun `walking cadence upgrades SEDENTARY to LIGHT`() {
        // 25 spm over 60s: 25 steps between t=0 and t=60_000ms
        val window = listOf(0L to 0, 60_000L to 25)
        assertEquals(ActivityIntensity.LIGHT, useCase(window, ActivityIntensity.SEDENTARY))
    }

    @Test
    fun `running cadence upgrades SEDENTARY to VIGOROUS`() {
        val window = listOf(0L to 0, 60_000L to 120)
        assertEquals(ActivityIntensity.VIGOROUS, useCase(window, ActivityIntensity.SEDENTARY))
    }
}
