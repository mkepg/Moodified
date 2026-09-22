package com.moodified.app.data.receiver.activity

import com.google.android.gms.location.DetectedActivity
import com.moodified.app.domain.model.activity.ActivityIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityReceiverTest {
    @Test
    fun `still maps to sedentary`() {
        val still = DetectedActivity(DetectedActivity.STILL, 90)
        val result = ActivityReceiver.classify(still, listOf(still))
        assertEquals(ActivityIntensity.SEDENTARY to 90, result)
    }

    @Test
    fun `walking maps to light`() {
        val walking = DetectedActivity(DetectedActivity.WALKING, 80)
        val result = ActivityReceiver.classify(walking, listOf(walking))
        assertEquals(ActivityIntensity.LIGHT to 80, result)
    }

    @Test
    fun `running maps to vigorous`() {
        val running = DetectedActivity(DetectedActivity.RUNNING, 70)
        val result = ActivityReceiver.classify(running, listOf(running))
        assertEquals(ActivityIntensity.VIGOROUS to 70, result)
    }

    // Real cyclists at high confidence should still promote to VIGOROUS.
    @Test
    fun `on bicycle at high confidence maps to vigorous`() {
        val cycling = DetectedActivity(DetectedActivity.ON_BICYCLE, 85)
        val result = ActivityReceiver.classify(cycling, listOf(cycling))
        assertEquals(ActivityIntensity.VIGOROUS to 85, result)
    }

    // Bumpy jeep / bus rides often show ON_BICYCLE at mid confidence — reject.
    @Test
    fun `on bicycle below confidence threshold is rejected`() {
        val cycling = DetectedActivity(DetectedActivity.ON_BICYCLE, 70)
        val result = ActivityReceiver.classify(cycling, listOf(cycling))
        assertNull(result)
    }

    // Ambiguous jeep signal: ON_BICYCLE tops the list but IN_VEHICLE is also present —
    // prefer IN_VEHICLE so the downstream stale-release safety net can protect us.
    @Test
    fun `in vehicle hint overrides top on bicycle pick`() {
        val cycling = DetectedActivity(DetectedActivity.ON_BICYCLE, 65)
        val vehicle = DetectedActivity(DetectedActivity.IN_VEHICLE, 45)
        val result = ActivityReceiver.classify(cycling, listOf(cycling, vehicle))
        assertEquals(ActivityIntensity.IN_VEHICLE to 45, result)
    }

    // In-vehicle hint at very low confidence should NOT override — noise floor.
    @Test
    fun `weak in vehicle hint does not override`() {
        val cycling = DetectedActivity(DetectedActivity.ON_BICYCLE, 85)
        val vehicle = DetectedActivity(DetectedActivity.IN_VEHICLE, 20)
        val result = ActivityReceiver.classify(cycling, listOf(cycling, vehicle))
        assertEquals(ActivityIntensity.VIGOROUS to 85, result)
    }

    @Test
    fun `unknown and tilting are ignored`() {
        val unknown = DetectedActivity(DetectedActivity.UNKNOWN, 90)
        val tilting = DetectedActivity(DetectedActivity.TILTING, 90)
        assertNull(ActivityReceiver.classify(unknown, listOf(unknown)))
        assertNull(ActivityReceiver.classify(tilting, listOf(tilting)))
    }
}
