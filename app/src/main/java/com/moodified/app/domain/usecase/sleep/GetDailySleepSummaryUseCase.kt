package com.moodified.app.domain.usecase.sleep

import com.moodified.app.core.utils.SleepTimeUtils
import com.moodified.app.domain.model.sleep.DailySleepSummary
import com.moodified.app.domain.model.sleep.SleepSegment
import com.moodified.app.domain.model.sleep.SleepStatus
import com.moodified.app.domain.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

class GetDailySleepSummaryUseCase
    @Inject
    constructor(
        private val repository: SleepRepository,
    ) {
        companion object {
            const val SESSION_GAP_HOURS = 4L
        }

        operator fun invoke(date: LocalDate): Flow<DailySleepSummary?> {
            return repository.getSegmentsForDate(date).map { segments ->
                val asleepSegments =
                    segments
                        .filter { it.status == SleepStatus.ASLEEP }
                        .sortedBy { it.startTime }

                if (asleepSegments.isEmpty()) return@map null

                val targetSession = isolatePrimarySleepSession(asleepSegments, date)
                if (targetSession.isEmpty()) return@map null

                // The UseCase generates 1 primary segment per night, so we rely directly on its calculated properties
                val primary = targetSession.first()

                DailySleepSummary(
                    date = date.toString(),
                    totalSleepMinutes = primary.totalSleepMinutes,
                    awakenings = primary.awakenings,
                    sleepOnsetMinutes = SleepTimeUtils.minutesSince6PM(primary.startTime),
                    isEstimated = true,
                )
            }
        }

        private fun isolatePrimarySleepSession(
            segments: List<SleepSegment>,
            targetDate: LocalDate,
        ): List<SleepSegment> {
            if (segments.isEmpty()) return emptyList()

            val sessions = mutableListOf<MutableList<SleepSegment>>()
            var current = mutableListOf(segments.first())

            for (i in 1 until segments.size) {
                val gapHours =
                    Duration.between(
                        segments[i - 1].endTime,
                        segments[i].startTime,
                    ).toHours()

                if (gapHours >= SESSION_GAP_HOURS) {
                    sessions.add(current)
                    current = mutableListOf()
                }
                current.add(segments[i])
            }
            sessions.add(current)

            val byWakeTime =
                sessions.firstOrNull { s ->
                    s.last().endTime.toLocalDate() == targetDate
                }
            if (byWakeTime != null) return byWakeTime.toList()

            return sessions.firstOrNull { s ->
                val totalSeconds = Duration.between(s.first().startTime, s.last().endTime).seconds
                val mid = s.first().startTime.plusSeconds(totalSeconds / 2)
                mid.toLocalDate() == targetDate
            }?.toList() ?: emptyList()
        }
    }
