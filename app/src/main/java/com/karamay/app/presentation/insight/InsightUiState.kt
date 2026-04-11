package com.karamay.app.presentation.insight

import androidx.compose.ui.graphics.vector.ImageVector
import com.karamay.app.domain.model.activity.ActivityDailySummary
import com.karamay.app.domain.model.activity.ActivityTrends
import com.karamay.app.domain.model.inference.InferredMoodState
import com.karamay.app.domain.model.interaction.InteractionDailySummary
import com.karamay.app.domain.model.interaction.InteractionTrends
import com.karamay.app.domain.model.mood.MoodEntry
import com.karamay.app.domain.model.sleep.DailySleepSummary
import com.karamay.app.domain.model.sleep.SleepTrends
import java.time.LocalDate

data class DomainReadiness(
    val isReady: Boolean,
    val daysWithData: Int,
    val requiredDays: Int
) {
    val progressFraction: Float
        get() = if (requiredDays == 0) 1f
        else (daysWithData.toFloat() / requiredDays).coerceIn(0f, 1f)
}

data class InsightDomainReadiness(
    val sleep: DomainReadiness,
    val phone: DomainReadiness,
    val activity: DomainReadiness,
    val mood: DomainReadiness
) {
    val anyReady: Boolean
        get() = sleep.isReady || phone.isReady || activity.isReady || mood.isReady
}

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
    val icon: ImageVector
)

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

data class ActivityBarPoint(
    val date: LocalDate,
    val sedentaryMinutes: Int,
    val lightMinutes: Int,
    val moderateMinutes: Int,
    val vigorousMinutes: Int,
    val totalSteps: Int
) {
    val activeMinutes: Int get() = lightMinutes + moderateMinutes + vigorousMinutes
}

data class ScreenTimeBarPoint(
    val date: LocalDate,
    val totalScreenMinutes: Int,
    val lateNightMinutes: Int
)

data class InsightUiState(
    val isLoading: Boolean                          = true,
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
    val hasEnoughData: Boolean get() = domainReadiness.anyReady
}