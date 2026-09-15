package com.moodified.app.presentation.insight

import org.junit.Assert.assertEquals
import org.junit.Test

class InsightTabTest {
    @Test
    fun `known query values map to their tab`() {
        assertEquals(InsightTab.ACTIVITY, InsightTab.fromQueryParam("activity"))
        assertEquals(InsightTab.SLEEP, InsightTab.fromQueryParam("sleep"))
        assertEquals(InsightTab.SCREEN_USE, InsightTab.fromQueryParam("screen_use"))
        assertEquals(InsightTab.OVERVIEW, InsightTab.fromQueryParam("overview"))
    }

    @Test
    fun `null and unknown values fall back to OVERVIEW`() {
        assertEquals(InsightTab.OVERVIEW, InsightTab.fromQueryParam(null))
        assertEquals(InsightTab.OVERVIEW, InsightTab.fromQueryParam(""))
        assertEquals(InsightTab.OVERVIEW, InsightTab.fromQueryParam("garbage"))
        assertEquals(InsightTab.OVERVIEW, InsightTab.fromQueryParam("SLEEP")) // case-sensitive, per contract
    }
}
