package com.moodified.app.presentation.insight

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import com.moodified.app.domain.model.activity.ActivityIntensity
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.Valence
import com.moodified.app.domain.usecase.inference.InferenceConstants
import javax.inject.Inject

class InsightGenerator
    @Inject
    constructor() {
        companion object {
            private const val MIN_DAYS_FOR_CORRELATION = 3
            private const val GOOD_SLEEP_MINUTES = 420
            private const val HIGH_SCREEN_MINUTES = 240
        }

        fun generate(
            bundles: List<DailyInsightBundle>,
            readiness: InsightDomainReadiness,
        ): List<InsightCard> {
            return listOfNotNull(
                if (readiness.sleep.isReady) sleepInsight(bundles) else null,
                if (readiness.activity.isReady) activityInsight(bundles) else null,
                if (readiness.phone.isReady) phoneInsight(bundles) else null,
                if (readiness.mood.isReady) moodInsight(bundles) else null,
                // [PHASE 3 IMPLEMENTATION]
                if (readiness.mood.isReady) moodStabilityInsight(bundles) else null,
                if (readiness.sleep.isReady && readiness.mood.isReady) {
                    sleepMoodCorrelation(bundles)
                } else {
                    null
                },
                if (readiness.activity.isReady && readiness.mood.isReady) {
                    activityMoodCorrelation(bundles)
                } else {
                    null
                },
                if (readiness.phone.isReady && readiness.mood.isReady) {
                    phoneMoodCorrelation(bundles)
                } else {
                    null
                },
            )
                .distinctBy { it.id }
                .sortedWith(compareBy({ it.priority.ordinal }, { it.category.ordinal }))
        }

        // [PHASE 3 IMPLEMENTATION]: Stability Rules
        private fun moodStabilityInsight(bundles: List<DailyInsightBundle>): InsightCard? {
            val manualEntries = bundles.flatMap { b -> b.moodEntries.filter { it.isManual } }
            if (manualEntries.size < 3) return null

            val mean = manualEntries.map { it.valence.ordinal.toFloat() }.average().toFloat()
            val variance = manualEntries.map { Math.pow((it.valence.ordinal.toFloat() - mean).toDouble(), 2.0) }.average().toFloat()

            return when {
                variance <= 0.25f ->
                    InsightCard(
                        id = "mood_highly_stable",
                        category = InsightCategory.MOOD,
                        priority = InsightPriority.LOW,
                        headline = "Smooth sailing",
                        body = "Your emotional rhythm has been remarkably steady lately. Consistent routines often help maintain this balance.",
                        icon = Icons.Rounded.Waves,
                    )
                variance >= 0.8f ->
                    InsightCard(
                        id = "mood_highly_volatile",
                        category = InsightCategory.MOOD,
                        priority = InsightPriority.MEDIUM,
                        headline = "Riding the rollercoaster",
                        body = "We've noticed some significant swings in your mood this week. Remember to be patient with yourself during these natural ups and downs.",
                        icon = Icons.Rounded.ShowChart,
                    )
                else -> null
            }
        }

        private fun sleepInsight(bundles: List<DailyInsightBundle>): InsightCard? {
            val sleepDays = bundles.mapNotNull { it.sleepSummary }
            if (sleepDays.isEmpty()) return null

            val avgMinutes = sleepDays.sumOf { it.totalSleepMinutes } / sleepDays.size
            val debt = sleepDays.sumOf { (GOOD_SLEEP_MINUTES - it.totalSleepMinutes).coerceAtLeast(0) }
            val avgAwakenings = sleepDays.sumOf { it.awakenings } / sleepDays.size

            return when {
                avgMinutes < 360 ->
                    InsightCard(
                        id = "sleep_short",
                        category = InsightCategory.SLEEP,
                        priority = InsightPriority.HIGH,
                        headline = "Running on less rest",
                        body = "You’ve been averaging under 6 hours lately. If you’re feeling a bit drained, this might be why. Try to give yourself some extra grace today.",
                        icon = Icons.Rounded.Snooze,
                    )
                avgMinutes >= GOOD_SLEEP_MINUTES && avgAwakenings <= 2 ->
                    InsightCard(
                        id = "sleep_great",
                        category = InsightCategory.SLEEP,
                        priority = InsightPriority.LOW,
                        headline = "Resting well",
                        body = "You averaged good sleep this week with few disruptions. Your body and mind will thank you for this steady rhythm.",
                        icon = Icons.Rounded.AutoAwesome,
                    )
                debt > 120 ->
                    InsightCard(
                        id = "sleep_debt",
                        category = InsightCategory.SLEEP,
                        priority = InsightPriority.MEDIUM,
                        headline = "Catching up on rest",
                        body = "You've missed out on a few hours of sleep this week. Tonight might be a good night to wind down a little earlier and reclaim that rest.",
                        icon = Icons.Rounded.Bedtime,
                    )
                avgAwakenings > 3 ->
                    InsightCard(
                        id = "sleep_fragmented",
                        category = InsightCategory.SLEEP,
                        priority = InsightPriority.MEDIUM,
                        headline = "Restless nights",
                        body = "You've been waking up a few times during the night. A calming wind-down routine might help you stay asleep more soundly.",
                        icon = Icons.Rounded.Bedtime,
                    )
                else ->
                    InsightCard(
                        id = "sleep_ok",
                        category = InsightCategory.SLEEP,
                        priority = InsightPriority.LOW,
                        headline = "Steady rest",
                        body = "You're getting a decent amount of sleep, averaging ${formatHours(
                            avgMinutes,
                        )}. Consistency is key, so keep up the good work.",
                        icon = Icons.Rounded.Bedtime,
                    )
            }
        }

        private fun activityInsight(bundles: List<DailyInsightBundle>): InsightCard? {
            val actDays = bundles.mapNotNull { it.activitySummary }
            if (actDays.isEmpty()) return null

            val avgSteps = actDays.sumOf { it.totalSteps } / actDays.size
            val avgActive = actDays.sumOf { it.activeMinutes } / actDays.size
            val vigorousDays =
                actDays.count {
                    (it.minutesPerIntensityBand[ActivityIntensity.VIGOROUS] ?: 0) >=
                        InferenceConstants.VIGOROUS_MINUTES_THRESHOLD
                }
            val sedentaryDays =
                actDays.count {
                    it.sedentaryMinutes > InferenceConstants.SEDENTARY_MINUTES_THRESHOLD
                }

            return when {
                avgSteps >= 10_000 ->
                    InsightCard(
                        id = "activity_high_steps",
                        category = InsightCategory.ACTIVITY,
                        priority = InsightPriority.LOW,
                        headline = "Moving with purpose",
                        body = "You're getting plenty of steps in! All that movement is a wonderful natural boost for your mood and energy.",
                        icon = Icons.Rounded.DirectionsRun,
                    )
                vigorousDays >= 3 ->
                    InsightCard(
                        id = "activity_vigorous",
                        category = InsightCategory.ACTIVITY,
                        priority = InsightPriority.LOW,
                        headline = "Getting your heart rate up",
                        body = "You've had some great active days this week. It's a fantastic way to clear your head and build resilience.",
                        icon = Icons.Rounded.FitnessCenter,
                    )
                sedentaryDays >= 4 ->
                    InsightCard(
                        id = "activity_sedentary",
                        category = InsightCategory.ACTIVITY,
                        priority = InsightPriority.HIGH,
                        headline = "A quieter week for movement",
                        body = "You've had a lot of still days this week. Whenever you're ready, even a short 10-minute walk can do wonders for your headspace.",
                        icon = Icons.Rounded.Chair,
                    )
                avgActive < 20 ->
                    InsightCard(
                        id = "activity_low_active",
                        category = InsightCategory.ACTIVITY,
                        priority = InsightPriority.MEDIUM,
                        headline = "Taking it easy",
                        body = "Your activity levels have been a bit lower lately. See if you can find small, enjoyable ways to move your body today.",
                        icon = Icons.Rounded.DirectionsRun,
                    )
                else ->
                    InsightCard(
                        id = "activity_moderate",
                        category = InsightCategory.ACTIVITY,
                        priority = InsightPriority.LOW,
                        headline = "Keeping a steady pace",
                        body = "You're maintaining a nice, moderate level of activity. Finding movement you enjoy makes all the difference.",
                        icon = Icons.Rounded.DirectionsRun,
                    )
            }
        }

        private fun phoneInsight(bundles: List<DailyInsightBundle>): InsightCard? {
            val phoneDays = bundles.mapNotNull { it.interactionSummary }
            if (phoneDays.isEmpty()) return null

            val avgScreen = phoneDays.sumOf { it.totalScreenTimeMinutes } / phoneDays.size
            val highScreenDays = phoneDays.count { it.totalScreenTimeMinutes > HIGH_SCREEN_MINUTES }
            val lateNightDays =
                phoneDays.count {
                    it.lateNightUsageMinutes > InferenceConstants.LATE_NIGHT_MINUTES_THRESHOLD
                }

            return when {
                lateNightDays >= 3 ->
                    InsightCard(
                        id = "phone_late_night",
                        category = InsightCategory.PHONE,
                        priority = InsightPriority.HIGH,
                        headline = "Late nights with your screen",
                        body = "You've been up late on your phone recently. Giving your eyes a break before bed can really help your mind wind down.",
                        icon = Icons.Rounded.Smartphone,
                    )
                avgScreen > HIGH_SCREEN_MINUTES ->
                    InsightCard(
                        id = "phone_high_screen",
                        category = InsightCategory.PHONE,
                        priority = InsightPriority.MEDIUM,
                        headline = "A lot of screen time",
                        body = "Your screen time has been quite high this week. It might feel good to schedule a small digital detox today.",
                        icon = Icons.Rounded.Smartphone,
                    )
                highScreenDays <= 1 ->
                    InsightCard(
                        id = "phone_controlled",
                        category = InsightCategory.PHONE,
                        priority = InsightPriority.LOW,
                        headline = "Balanced screen habits",
                        body = "You're doing a great job keeping your screen time in check. It leaves more room for being present in your day.",
                        icon = Icons.Rounded.Smartphone,
                    )
                else ->
                    InsightCard(
                        id = "phone_moderate",
                        category = InsightCategory.PHONE,
                        priority = InsightPriority.LOW,
                        headline = "Moderate screen use",
                        body = "Your screen time is at a steady level. Just keep an eye on those late-night scrolls to protect your rest.",
                        icon = Icons.Rounded.Smartphone,
                    )
            }
        }

        private fun moodInsight(bundles: List<DailyInsightBundle>): InsightCard? {
            val allManual = bundles.flatMap { b -> b.moodEntries.filter { it.isManual } }
            if (allManual.isEmpty()) return null

            val totalDays = bundles.count { b -> b.moodEntries.any { it.isManual } }.coerceAtLeast(1)
            val positiveDays = bundles.count { b -> b.moodEntries.any { it.isManual && it.valence == Valence.POSITIVE } }
            val negativeDays = bundles.count { b -> b.moodEntries.any { it.isManual && it.valence == Valence.NEGATIVE } }
            val highArousalDays = bundles.count { b -> b.moodEntries.any { it.isManual && it.arousal == Arousal.HIGH } }

            return when {
                positiveDays.toFloat() / totalDays >= 0.6f ->
                    InsightCard(
                        id = "mood_positive_streak",
                        category = InsightCategory.MOOD,
                        priority = InsightPriority.LOW,
                        headline = "A brighter week",
                        body = "You've logged a lot of positive days recently. Take a moment to savor whatever is bringing you this good energy.",
                        icon = Icons.Rounded.WbSunny,
                    )
                negativeDays.toFloat() / totalDays >= 0.5f ->
                    InsightCard(
                        id = "mood_negative_pattern",
                        category = InsightCategory.MOOD,
                        priority = InsightPriority.HIGH,
                        headline = "A heavy week",
                        body = "It looks like it’s been a tough week. Please remember to be gentle with yourself right now. The patterns below might help explain why.",
                        icon = Icons.Rounded.Thunderstorm,
                    )
                highArousalDays >= 4 ->
                    InsightCard(
                        id = "mood_high_arousal",
                        category = InsightCategory.MOOD,
                        priority = InsightPriority.MEDIUM,
                        headline = "High energy days",
                        body = "You've had a lot of high-energy days. If it feels like good energy, ride the wave! If it feels like stress, try to find a moment of calm.",
                        icon = Icons.Rounded.Bolt,
                    )
                else ->
                    InsightCard(
                        id = "mood_mixed",
                        category = InsightCategory.MOOD,
                        priority = InsightPriority.LOW,
                        headline = "Riding the waves",
                        body = "Your mood has ebbed and flowed this week, which is completely natural. We're here to help you spot the patterns.",
                        icon = Icons.Rounded.CloudQueue,
                    )
            }
        }

        private fun sleepMoodCorrelation(bundles: List<DailyInsightBundle>): InsightCard? {
            val paired =
                bundles.filter { b ->
                    b.sleepSummary != null && b.moodEntries.any { it.isManual }
                }
            if (paired.size < MIN_DAYS_FOR_CORRELATION) return null

            val goodSleepPositiveMood =
                paired.count { b ->
                    b.sleepSummary!!.totalSleepMinutes >= GOOD_SLEEP_MINUTES &&
                        b.moodEntries.any { it.isManual && it.valence == Valence.POSITIVE }
                }
            val poorSleepNegativeMood =
                paired.count { b ->
                    b.sleepSummary!!.totalSleepMinutes < 360 &&
                        b.moodEntries.any { it.isManual && it.valence == Valence.NEGATIVE }
                }

            return when {
                goodSleepPositiveMood.toFloat() / paired.size >= 0.5f ->
                    InsightCard(
                        id = "corr_sleep_mood_positive",
                        category = InsightCategory.CORRELATION,
                        priority = InsightPriority.MEDIUM,
                        headline = "Rest fuels your mood",
                        body = "We noticed that on days you sleep well, you tend to feel much brighter. Guard your rest—it's working for you.",
                        icon = Icons.Rounded.Lightbulb,
                    )
                poorSleepNegativeMood.toFloat() / paired.size >= 0.4f ->
                    InsightCard(
                        id = "corr_sleep_mood_negative",
                        category = InsightCategory.CORRELATION,
                        priority = InsightPriority.HIGH,
                        headline = "Tired days can be tough days",
                        body = "It seems like shorter nights are making your days feel a bit heavier. Prioritizing your sleep might really help lift your spirits.",
                        icon = Icons.Rounded.Lightbulb,
                    )
                else -> null
            }
        }

        private fun activityMoodCorrelation(bundles: List<DailyInsightBundle>): InsightCard? {
            val paired =
                bundles.filter { b ->
                    b.activitySummary != null && b.moodEntries.any { it.isManual }
                }
            if (paired.size < MIN_DAYS_FOR_CORRELATION) return null

            val activePositive =
                paired.count { b ->
                    b.activitySummary!!.activeMinutes > InferenceConstants.HIGH_ACTIVITY_MINUTES &&
                        b.moodEntries.any { it.isManual && it.valence == Valence.POSITIVE }
                }

            if (activePositive.toFloat() / paired.size < 0.45f) return null

            return InsightCard(
                id = "corr_activity_mood",
                category = InsightCategory.CORRELATION,
                priority = InsightPriority.MEDIUM,
                headline = "Movement brings you joy",
                body = "There's a clear link here: on days you move more, you feel better. Keep finding ways to stay active that feel good to you.",
                icon = Icons.Rounded.Lightbulb,
            )
        }

        private fun phoneMoodCorrelation(bundles: List<DailyInsightBundle>): InsightCard? {
            val paired =
                bundles.filter { b ->
                    b.interactionSummary != null && b.moodEntries.any { it.isManual }
                }
            if (paired.size < MIN_DAYS_FOR_CORRELATION) return null

            val highScreenNegative =
                paired.count { b ->
                    b.interactionSummary!!.totalScreenTimeMinutes > HIGH_SCREEN_MINUTES &&
                        b.moodEntries.any { it.isManual && it.valence == Valence.NEGATIVE }
                }

            if (highScreenNegative.toFloat() / paired.size < 0.4f) return null

            return InsightCard(
                id = "corr_phone_mood",
                category = InsightCategory.CORRELATION,
                priority = InsightPriority.MEDIUM,
                headline = "Screens and your mood",
                body = "We noticed that high screen time often matches up with lower moods for you. A little unplugged time might be refreshing.",
                icon = Icons.Rounded.Lightbulb,
            )
        }

        private fun formatHours(minutes: Int): String {
            val h = minutes / 60
            val m = minutes % 60
            return if (h > 0) "${h}h ${m}m" else "${m}m"
        }
    }
