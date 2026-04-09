package com.karamay.app.core.coordination

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Phase 2 — PollingJob
 *
 * Reusable, scope-bound poll-loop that was previously copy-pasted
 * verbatim across all three repository implementations.
 *
 * Usage:
 * ```
 * private val poller = PollingJob(scope, stateMutex, POLL_INTERVAL_MS) {
 *     doActualWork()
 * }
 * poller.start()
 * poller.stop()
 * ```
 *
 * The [mutex] parameter is optional. Pass the repository's existing
 * [Mutex] to preserve the serialised-access guarantee, or `null`
 * if the callback is already thread-safe.
 */
class PollingJob(
    private val scope:        CoroutineScope,
    private val mutex:        Mutex?,
    private val intervalMs:   Long,
    private val tag:          String = "PollingJob",
    private val isActive:     () -> Boolean = { true },
    private val block:        suspend () -> Unit
) {
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            while (isActive && this@PollingJob.isActive()) {
                try {
                    if (mutex != null) {
                        mutex.withLock { block() }
                    } else {
                        block()
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Poll error: ${e.message}", e)
                }
                delay(intervalMs)
            }
            Log.d(tag, "Poll loop exited.")
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
