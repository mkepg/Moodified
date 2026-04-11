package com.karamay.app.core.debug

import com.karamay.app.domain.model.activity.ActivityDailySummary
import com.karamay.app.domain.model.activity.ActivityIntensity
import java.time.LocalDate
import kotlin.random.Random

object MockActivityDataGenerator {

    fun generate(daysBack: Int = 14): List<ActivityDailySummary> {
        val summaries = mutableListOf<ActivityDailySummary>()
        val today = LocalDate.now()

        for (i in 0..daysBack) {
            val targetDate = today.minusDays(i.toLong())

            // Simulate a structured routine: Heavy training ~4 days a week
            val isTrainingDay = targetDate.dayOfWeek.value in listOf(1, 3, 5, 6)

            var totalSteps = 0
            var activeMins = 0
            var sedentaryMins = 0
            val distribution = mutableMapOf<ActivityIntensity, Int>()

            // Helper to chronologically build the day's timeline
            fun addBlock(intensity: ActivityIntensity, minutes: Int, steps: Int) {
                distribution[intensity] = (distribution[intensity] ?: 0) + minutes
                if (intensity == ActivityIntensity.SEDENTARY || intensity == ActivityIntensity.IN_VEHICLE) {
                    sedentaryMins += minutes
                } else {
                    activeMins += minutes
                }
                totalSteps += steps
            }

            // 00:00 - 07:30 : Sleep
            addBlock(ActivityIntensity.SEDENTARY, 450, Random.nextInt(20, 80))

            // 07:30 - 09:00 : Morning routine & commute
            addBlock(ActivityIntensity.LIGHT, 45, Random.nextInt(1200, 1800))
            addBlock(ActivityIntensity.IN_VEHICLE, 45, 0)

            // 09:00 - 17:00 : Long focus blocks (coding / compiling / lectures)
            // Predominantly sedentary with brief active breaks
            addBlock(ActivityIntensity.SEDENTARY, 390, Random.nextInt(300, 600))
            addBlock(ActivityIntensity.LIGHT, 90, Random.nextInt(1500, 2500))

            var peakIntensity = ActivityIntensity.LIGHT

            // 17:00 - 19:30 : Late afternoon split
            if (isTrainingDay) {
                // Structured training session
                addBlock(ActivityIntensity.VIGOROUS, 60, Random.nextInt(3000, 4500))
                addBlock(ActivityIntensity.MODERATE, 30, Random.nextInt(1000, 1500))
                addBlock(ActivityIntensity.SEDENTARY, 60, 0)
                peakIntensity = ActivityIntensity.VIGOROUS
            } else {
                // Rest day / continuous screen time / gaming
                addBlock(ActivityIntensity.SEDENTARY, 120, 0)
                addBlock(ActivityIntensity.LIGHT, 30, Random.nextInt(500, 1000))
                peakIntensity = ActivityIntensity.MODERATE
            }

            // 19:30 - 24:00 : Evening wind-down
            addBlock(ActivityIntensity.SEDENTARY, 240, Random.nextInt(200, 500))
            addBlock(ActivityIntensity.LIGHT, 30, Random.nextInt(500, 800))

            summaries.add(
                ActivityDailySummary(
                    date = targetDate.toString(),
                    totalSteps = totalSteps,
                    activeMinutes = activeMins,
                    sedentaryMinutes = sedentaryMins,
                    peakIntensity = peakIntensity,
                    isPartialDay = false,
                    minutesPerIntensityBand = distribution
                )
            )
        }
        return summaries.sortedBy { LocalDate.parse(it.date) }
    }
}