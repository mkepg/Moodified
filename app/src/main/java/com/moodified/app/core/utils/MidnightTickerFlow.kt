package com.moodified.app.core.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Phase 2: moved from presentation/devtools/DevToolsState.kt to core/utils
 * so it can be imported by any layer without a presentation dependency.
 *
 * Emits [LocalDate.now()] immediately, then once per calendar day at midnight
 * (+ 60 s margin). The old implementation polled every 60 seconds and relied
 * on distinctUntilChanged() to suppress 1 439 redundant daily emissions, but
 * each tick still caused flatMapLatest to cancel and re-subscribe the inner
 * combine chain in all three monitor ViewModels — wasted work.
 */
fun midnightTickerFlow(): Flow<LocalDate> =
    flow {
        while (true) {
            emit(LocalDate.now())
            val now = LocalDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
            val delayMs = Duration.between(now, nextMidnight).toMillis() + 60_000L
            delay(delayMs.coerceAtLeast(60_000L))
        }
    }.distinctUntilChanged()
