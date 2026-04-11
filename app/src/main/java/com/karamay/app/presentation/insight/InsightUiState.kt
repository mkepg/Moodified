package com.karamay.app.presentation.insight

import com.karamay.app.domain.model.activity.ActivityDailySummary
import com.karamay.app.domain.model.activity.ActivityTrends
import com.karamay.app.domain.model.inference.InferredMoodState
import com.karamay.app.domain.model.interaction.InteractionDailySummary
import com.karamay.app.domain.model.interaction.InteractionTrends
import com.karamay.app.domain.model.mood.MoodEntry
import com.karamay.app.domain.model.sleep.DailySleepSummary
import com.karamay.app.domain.model.sleep.SleepTrends
import java.time.LocalDate

// ─────────────────────────────────────────────────────────────
// Per-domain data-readiness model
// ─────────────────────────────────────────────────────────────

/**
 * Readiness state for a single insight domain.
 *
 * [isReady]        – true when there is enough data to show the section.
 * [daysWithData]   – how many days in the window have data for this domain.
 * [requiredDays]   – the minimum needed before [isReady] flips to true.
 *                    0 = available immediately (e.g. sleep/phone use backfill).
 */
data class DomainReadiness(
    val isReady: Boolean,
    val daysWithData: Int,
    val requiredDays: Int
) {
    /** Progress fraction in [0, 1] — useful for placeholder progress indicators. */
    val progressFraction: Float
        get() = if (requiredDays == 0) 1f
                else (daysWithData.toFloat() / requiredDays).coerceIn(0f, 1f)
}

/**
 * Consolidated readiness snapshot across all four insight domains.
 *
 * Rules (per spec):
 *   • Sleep    – available immediately; backfilled data counts.
 *   • Phone    – available immediately; backfilled data counts.
 *   • Activity – requires ≥ 3 days of valid activity data.
 *   • Mood     – requires ≥ 3 manual entries OR inferred confidence ≥ threshold.
 *
 * The top-level Insight page is **always accessible** once at least one
 * domain is ready — there is no global day-count lock.
 */
data class InsightDomainReadiness(
    val sleep: DomainReadiness,
    val phone: DomainReadiness,
    val activity: DomainReadiness,
    val mood: DomainReadiness
) {
    /** True when at least one domain has enough data to show something. */
    val anyReady: Boolean
        get() = sleep.isReady || phone.isReady || activity.isReady || mood.isReady
}

// ─────────────────────────────────────────────────────────────
// Bundle / card models (unchanged shape, kept together)
// ─────────────────────────────────────────────────────────────

data class DailyInsightBundle(
    val date: LocalDate,
    val moodEntries: List<MoodEntry>             = emptyList(),
    val sleepSummary: DailySleepSummary?         = null,
    val activitySummary: ActivityDailySummary?   = null,
    val interactionSummary: InteractionDailySummary? = null,
    val inferredMood: InferredMoodState?         = null
)

enum class InsightPriority { HIGH, MEDIUM, LOW }
enum class InsightCategory { SLEEP, ACTIVITY, PHONE, MOOD, CORRELATION }

data class InsightCard(
    val id: String,
    val category: InsightCategory,
    val priority: InsightPriority,
    val headline: String,
    val body: String,
    val emoji: String
)

// ─────────────────────────────────────────────────────────────
// Chart point models
// ─────────────────────────────────────────────────────────────

data class MoodChartPoint(
    val date: LocalDate,
    val valenceOrdinal: Float,
    val isManual: Boolean
)

data class SleepBarPoint(
    val date: LocalDate,
    val totalSleepMinutes: Int,
    val isEstimated: Boolean
)

/**
 * Activity bar point extended with per-band minute breakdowns for the
 * redesigned stacked-bar visualisation.
 */
data class ActivityBarPoint(
    val date: LocalDate,
    val sedentaryMinutes: Int,
    val lightMinutes: Int,
    val moderateMinutes: Int,
    val vigorousMinutes: Int,
    val totalSteps: Int
) {
    /** Total tracked minutes across all non-sedentary bands. */
    val activeMinutes: Int get() = lightMinutes + moderateMinutes + vigorousMinutes
}

data class ScreenTimeBarPoint(
    val date: LocalDate,
    val totalScreenMinutes: Int,
    val lateNightMinutes: Int
)

// ─────────────────────────────────────────────────────────────
// Top-level UI state
// ─────────────────────────────────────────────────────────────

data class InsightUiState(
    val isLoading: Boolean                          = true,

    // Replaces the old single `hasEnoughData` boolean.
    // The screen uses this to decide which sections to show vs. placeholder.
    val domainReadiness: InsightDomainReadiness     = InsightDomainReadiness(
        sleep    = DomainReadiness(isReady = false, daysWithData = 0, requiredDays = 0),
        phone    = DomainReadiness(isReady = false, daysWithData = 0, requiredDays = 0),
        activity = DomainReadiness(isReady = false, daysWithData = 0, requiredDays = 3),
        mood     = DomainReadiness(isReady = false, daysWithData = 0, requiredDays = 3)
    ),

    val weeklyBundles: List<DailyInsightBundle>     = emptyList(),
    val todayInferredMood: InferredMoodState?       = null,
    val sleepTrends: SleepTrends?                   = null,
    val activityTrends: ActivityTrends?             = null,
    val interactionTrends: InteractionTrends?       = null,
    val moodChartPoints: List<MoodChartPoint>       = emptyList(),
    val sleepBarPoints: List<SleepBarPoint>         = emptyList(),
    val activityBarPoints: List<ActivityBarPoint>   = emptyList(),
    val screenTimePoints: List<ScreenTimeBarPoint>  = emptyList(),
    val insightCards: List<InsightCard>             = emptyList(),
    val daysWithData: Int                           = 0
) {
    /** Convenience: page is unlocked as soon as any one domain is ready. */
    val hasEnoughData: Boolean get() = domainReadiness.anyReady
}
