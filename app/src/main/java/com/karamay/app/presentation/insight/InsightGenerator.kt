package com.karamay.app.presentation.insight

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import com.karamay.app.domain.model.activity.ActivityIntensity
import com.karamay.app.domain.model.mood.Arousal
import com.karamay.app.domain.model.mood.Valence
import com.karamay.app.domain.usecase.inference.InferenceConstants
import javax.inject.Inject

class InsightGenerator @Inject constructor() {
    companion object {
        private const val MIN_DAYS_FOR_CORRELATION = 3
        private const val GOOD_SLEEP_MINUTES        = 420
        private const val HIGH_SCREEN_MINUTES       = 240
    }

    fun generate(
        bundles:   List<DailyInsightBundle>,
        readiness: InsightDomainReadiness
    ): List<InsightCard> {
        return listOfNotNull(
            if (readiness.sleep.isReady)    sleepInsight(bundles)    else null,
            if (readiness.activity.isReady) activityInsight(bundles) else null,
            if (readiness.phone.isReady)    phoneInsight(bundles)    else null,
            if (readiness.mood.isReady)     moodInsight(bundles)     else null,
            if (readiness.sleep.isReady && readiness.mood.isReady)
                sleepMoodCorrelation(bundles) else null,
            if (readiness.activity.isReady && readiness.mood.isReady)
                activityMoodCorrelation(bundles) else null,
            if (readiness.phone.isReady && readiness.mood.isReady)
                phoneMoodCorrelation(bundles) else null
        )
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.priority.ordinal }, { it.category.ordinal }))
    }

    private fun sleepInsight(bundles: List<DailyInsightBundle>): InsightCard? {
        val sleepDays = bundles.mapNotNull { it.sleepSummary }
        if (sleepDays.isEmpty()) return null

        val avgMinutes    = sleepDays.sumOf { it.totalSleepMinutes } / sleepDays.size
        val debt          = sleepDays.sumOf { (GOOD_SLEEP_MINUTES - it.totalSleepMinutes).coerceAtLeast(0) }
        val avgAwakenings = sleepDays.sumOf { it.awakenings } / sleepDays.size

        return when {
            avgMinutes < 360 -> InsightCard(
                id       = "sleep_short",
                category = InsightCategory.SLEEP,
                priority = InsightPriority.HIGH,
                headline = "You're averaging under 6 hours",
                body     = "Your average over the past week is ${formatHours(avgMinutes)}. " +
                        "Consistent short sleep accumulates debt that affects mood and energy.",
                icon     = Icons.Rounded.Snooze
            )
            avgMinutes >= GOOD_SLEEP_MINUTES && avgAwakenings <= 2 -> InsightCard(
                id       = "sleep_great",
                category = InsightCategory.SLEEP,
                priority = InsightPriority.LOW,
                headline = "Solid sleep this week",
                body     = "You averaged ${formatHours(avgMinutes)} with few disruptions. " +
                        "Keep the consistent bedtime — it's working.",
                icon     = Icons.Rounded.AutoAwesome
            )
            debt > 120 -> InsightCard(
                id       = "sleep_debt",
                category = InsightCategory.SLEEP,
                priority = InsightPriority.MEDIUM,
                headline = "Sleep debt is building",
                body     = "You've accumulated roughly ${formatHours(debt)} of sleep debt this week. " +
                        "Extra rest on weekends only partially offsets chronic deficits.",
                icon     = Icons.Rounded.Bedtime
            )
            avgAwakenings > 3 -> InsightCard(
                id       = "sleep_fragmented",
                category = InsightCategory.SLEEP,
                priority = InsightPriority.MEDIUM,
                headline = "Your sleep is often fragmented",
                body     = "You averaged $avgAwakenings awakenings per night. Fragmented sleep " +
                        "can reduce deep-sleep quality even when total hours look adequate.",
                icon     = Icons.Rounded.Bedtime
            )
            else -> InsightCard(
                id       = "sleep_ok",
                category = InsightCategory.SLEEP,
                priority = InsightPriority.LOW,
                headline = "${formatHours(avgMinutes)} average sleep",
                body     = "You're near the recommended range. Aim for 7–9 hours consistently.",
                icon     = Icons.Rounded.Bedtime
            )
        }
    }

    private fun activityInsight(bundles: List<DailyInsightBundle>): InsightCard? {
        val actDays = bundles.mapNotNull { it.activitySummary }
        if (actDays.isEmpty()) return null

        val avgSteps      = actDays.sumOf { it.totalSteps } / actDays.size
        val avgActive     = actDays.sumOf { it.activeMinutes } / actDays.size
        val vigorousDays  = actDays.count {
            (it.minutesPerIntensityBand[ActivityIntensity.VIGOROUS] ?: 0) >=
                    InferenceConstants.VIGOROUS_MINUTES_THRESHOLD
        }
        val sedentaryDays = actDays.count {
            it.sedentaryMinutes > InferenceConstants.SEDENTARY_MINUTES_THRESHOLD
        }

        return when {
            avgSteps >= 10_000 -> InsightCard(
                id       = "activity_high_steps",
                category = InsightCategory.ACTIVITY,
                priority = InsightPriority.LOW,
                headline = "You hit 10 K steps on average",
                body     = "Averaging ${"%,d".format(avgSteps)} steps a day is excellent. " +
                        "Keep it up — it's strongly linked with positive mood.",
                icon     = Icons.Rounded.DirectionsRun
            )
            vigorousDays >= 3 -> InsightCard(
                id       = "activity_vigorous",
                category = InsightCategory.ACTIVITY,
                priority = InsightPriority.LOW,
                headline = "Regular vigorous exercise detected",
                body     = "$vigorousDays days this week had vigorous activity. This is one of " +
                        "the strongest natural mood boosters.",
                icon     = Icons.Rounded.FitnessCenter
            )
            sedentaryDays >= 4 -> InsightCard(
                id       = "activity_sedentary",
                category = InsightCategory.ACTIVITY,
                priority = InsightPriority.HIGH,
                headline = "Mostly sedentary this week",
                body     = "$sedentaryDays out of ${actDays.size} days were predominantly sedentary. " +
                        "Even a 20-minute walk can meaningfully improve mood.",
                icon     = Icons.Rounded.Chair
            )
            avgActive < 20 -> InsightCard(
                id       = "activity_low_active",
                category = InsightCategory.ACTIVITY,
                priority = InsightPriority.MEDIUM,
                headline = "Low active minutes this week",
                body     = "You averaged $avgActive active minutes per day. " +
                        "WHO guidelines suggest at least 30 minutes of moderate activity daily.",
                icon     = Icons.Rounded.DirectionsRun
            )
            else -> InsightCard(
                id       = "activity_moderate",
                category = InsightCategory.ACTIVITY,
                priority = InsightPriority.LOW,
                headline = "$avgActive min active / day on average",
                body     = "You're moderately active. Adding a couple of vigorous sessions " +
                        "per week tends to boost both energy and mood.",
                icon     = Icons.Rounded.DirectionsRun
            )
        }
    }

    private fun phoneInsight(bundles: List<DailyInsightBundle>): InsightCard? {
        val phoneDays = bundles.mapNotNull { it.interactionSummary }
        if (phoneDays.isEmpty()) return null

        val avgScreen      = phoneDays.sumOf { it.totalScreenTimeMinutes } / phoneDays.size
        val highScreenDays = phoneDays.count { it.totalScreenTimeMinutes > HIGH_SCREEN_MINUTES }
        val lateNightDays  = phoneDays.count {
            it.lateNightUsageMinutes > InferenceConstants.LATE_NIGHT_MINUTES_THRESHOLD
        }

        return when {
            lateNightDays >= 3 -> InsightCard(
                id       = "phone_late_night",
                category = InsightCategory.PHONE,
                priority = InsightPriority.HIGH,
                headline = "Late-night screen use is frequent",
                body     = "$lateNightDays nights had screen use after midnight. " +
                        "This can suppress melatonin and delay your sleeping time.",
                icon     = Icons.Rounded.Smartphone
            )
            avgScreen > HIGH_SCREEN_MINUTES -> InsightCard(
                id       = "phone_high_screen",
                category = InsightCategory.PHONE,
                priority = InsightPriority.MEDIUM,
                headline = "High daily screen time",
                body     = "You averaged ${formatHours(avgScreen)} of screen time per day. " +
                        "Heavy phone use is linked to lower mood.",
                icon     = Icons.Rounded.Smartphone
            )
            highScreenDays <= 1 -> InsightCard(
                id       = "phone_controlled",
                category = InsightCategory.PHONE,
                priority = InsightPriority.LOW,
                headline = "Screen time is well-managed",
                body     = "Most days stayed under ${formatHours(HIGH_SCREEN_MINUTES)} of screen time. " +
                        "That's a healthy baseline.",
                icon     = Icons.Rounded.Smartphone
            )
            else -> InsightCard(
                id       = "phone_moderate",
                category = InsightCategory.PHONE,
                priority = InsightPriority.LOW,
                headline = "${formatHours(avgScreen)} screen time on average",
                body     = "Phone use is moderate. Watch for late-night sessions — " +
                        "they tend to hurt sleep quality even when overall time looks fine.",
                icon     = Icons.Rounded.Smartphone
            )
        }
    }

    private fun moodInsight(bundles: List<DailyInsightBundle>): InsightCard? {
        val allManual = bundles.flatMap { b -> b.moodEntries.filter { it.isManual } }
        if (allManual.isEmpty()) return null

        val totalDays       = bundles.count { b -> b.moodEntries.any { it.isManual } }.coerceAtLeast(1)
        val positiveDays    = bundles.count { b -> b.moodEntries.any { it.isManual && it.valence == Valence.POSITIVE } }
        val negativeDays    = bundles.count { b -> b.moodEntries.any { it.isManual && it.valence == Valence.NEGATIVE } }
        val highArousalDays = bundles.count { b -> b.moodEntries.any { it.isManual && it.arousal == Arousal.HIGH } }

        return when {
            positiveDays.toFloat() / totalDays >= 0.6f -> InsightCard(
                id       = "mood_positive_streak",
                category = InsightCategory.MOOD,
                priority = InsightPriority.LOW,
                headline = "A mostly positive week",
                body     = "$positiveDays of your logged days this week were positive. " +
                        "Reflect on what made them feel that way.",
                icon     = Icons.Rounded.WbSunny
            )
            negativeDays.toFloat() / totalDays >= 0.5f -> InsightCard(
                id       = "mood_negative_pattern",
                category = InsightCategory.MOOD,
                priority = InsightPriority.HIGH,
                headline = "More low days than usual",
                body     = "$negativeDays days this week felt not-great. " +
                        "The patterns below may help explain why.",
                icon     = Icons.Rounded.Thunderstorm
            )
            highArousalDays >= 4 -> InsightCard(
                id       = "mood_high_arousal",
                category = InsightCategory.MOOD,
                priority = InsightPriority.MEDIUM,
                headline = "Energy has been elevated",
                body     = "$highArousalDays days had high arousal. High energy + positive mood = " +
                        "thriving. High energy + negative mood can signal stress.",
                icon     = Icons.Rounded.Bolt
            )
            else -> InsightCard(
                id       = "mood_mixed",
                category = InsightCategory.MOOD,
                priority = InsightPriority.LOW,
                headline = "Mixed mood this week",
                body     = "Your mood varied day-to-day. Keep logging — patterns become clearer after 2 weeks.",
                icon     = Icons.Rounded.CloudQueue
            )
        }
    }

    private fun sleepMoodCorrelation(bundles: List<DailyInsightBundle>): InsightCard? {
        val paired = bundles.filter { b ->
            b.sleepSummary != null && b.moodEntries.any { it.isManual }
        }
        if (paired.size < MIN_DAYS_FOR_CORRELATION) return null

        val goodSleepPositiveMood = paired.count { b ->
            b.sleepSummary!!.totalSleepMinutes >= GOOD_SLEEP_MINUTES &&
                    b.moodEntries.any { it.isManual && it.valence == Valence.POSITIVE }
        }
        val poorSleepNegativeMood = paired.count { b ->
            b.sleepSummary!!.totalSleepMinutes < 360 &&
                    b.moodEntries.any { it.isManual && it.valence == Valence.NEGATIVE }
        }

        return when {
            goodSleepPositiveMood.toFloat() / paired.size >= 0.5f -> InsightCard(
                id       = "corr_sleep_mood_positive",
                category = InsightCategory.CORRELATION,
                priority = InsightPriority.MEDIUM,
                headline = "Better sleep → better mood",
                body     = "On $goodSleepPositiveMood out of ${paired.size} tracked days, " +
                        "good sleep (7 h+) aligned with a positive mood.",
                icon     = Icons.Rounded.Lightbulb
            )
            poorSleepNegativeMood.toFloat() / paired.size >= 0.4f -> InsightCard(
                id       = "corr_sleep_mood_negative",
                category = InsightCategory.CORRELATION,
                priority = InsightPriority.HIGH,
                headline = "Poor sleep is hurting your mood",
                body     = "On $poorSleepNegativeMood days where sleep was under 6 hours, " +
                        "you also logged a low mood. Prioritising sleep may help.",
                icon     = Icons.Rounded.Lightbulb
            )
            else -> null
        }
    }

    private fun activityMoodCorrelation(bundles: List<DailyInsightBundle>): InsightCard? {
        val paired = bundles.filter { b ->
            b.activitySummary != null && b.moodEntries.any { it.isManual }
        }
        if (paired.size < MIN_DAYS_FOR_CORRELATION) return null

        val activePositive = paired.count { b ->
            b.activitySummary!!.activeMinutes > InferenceConstants.HIGH_ACTIVITY_MINUTES &&
                    b.moodEntries.any { it.isManual && it.valence == Valence.POSITIVE }
        }

        if (activePositive.toFloat() / paired.size < 0.45f) return null

        return InsightCard(
            id       = "corr_activity_mood",
            category = InsightCategory.CORRELATION,
            priority = InsightPriority.MEDIUM,
            headline = "Active days feel better",
            body     = "On $activePositive of ${paired.size} tracked days, higher activity " +
                    "aligned with a positive mood — exercise may be lifting your spirits.",
            icon     = Icons.Rounded.Lightbulb
        )
    }

    private fun phoneMoodCorrelation(bundles: List<DailyInsightBundle>): InsightCard? {
        val paired = bundles.filter { b ->
            b.interactionSummary != null && b.moodEntries.any { it.isManual }
        }
        if (paired.size < MIN_DAYS_FOR_CORRELATION) return null

        val highScreenNegative = paired.count { b ->
            b.interactionSummary!!.totalScreenTimeMinutes > HIGH_SCREEN_MINUTES &&
                    b.moodEntries.any { it.isManual && it.valence == Valence.NEGATIVE }
        }

        if (highScreenNegative.toFloat() / paired.size < 0.4f) return null

        return InsightCard(
            id       = "corr_phone_mood",
            category = InsightCategory.CORRELATION,
            priority = InsightPriority.MEDIUM,
            headline = "Heavy phone days feel worse",
            body     = "On $highScreenNegative of ${paired.size} tracked days, high screen time " +
                    "coincided with a lower mood. Reducing screen time might help.",
            icon     = Icons.Rounded.Lightbulb
        )
    }

    private fun formatHours(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}