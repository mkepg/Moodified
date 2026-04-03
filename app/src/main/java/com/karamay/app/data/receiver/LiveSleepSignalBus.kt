package com.karamay.app.data.receiver

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.location.SleepClassifyEvent
import com.karamay.app.domain.model.SleepSignal
import com.karamay.app.domain.model.SleepStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object LiveSleepSignalBus {

    private val _signals = MutableStateFlow(SleepSignal())
    val signals: StateFlow<SleepSignal> = _signals.asStateFlow()

    // Fix S2: @Volatile so the null-check in init() is visible across threads.
    // The full double-checked lock below ensures prefs is written exactly once.
    @Volatile private var prefs: SharedPreferences? = null

    @Volatile
    var lastAsleepTimestamp: LocalDateTime? = null
        private set

    fun init(context: Context) {
        // Fix S2: Double-checked locking — safe because prefs is @Volatile.
        // Prevents a race at cold-start where two injection paths could
        // both see prefs == null and double-initialise.
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.applicationContext
                .getSharedPreferences("sleep_bus_prefs", Context.MODE_PRIVATE)
            val savedTime = p.getString("last_asleep_time", null)
            if (savedTime != null) {
                lastAsleepTimestamp = LocalDateTime.parse(savedTime)
            }
            // Write last so other threads only see a non-null prefs
            // once the saved timestamp has already been restored.
            prefs = p
        }
    }

    fun emit(events: List<SleepClassifyEvent>) {
        if (events.isEmpty()) return
        val latest = events.maxByOrNull { it.timestampMillis } ?: return

        val eventTime = Instant.ofEpochMilli(latest.timestampMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        // Fix #8: Align threshold with GetDailySleepSummaryUseCase (CONFIDENCE_THRESHOLD = 75).
        // The original 90 caused the live signal to show AWAKE for confidence 75–89,
        // contradicting what the summary algorithm treats as asleep-quality data.
        val newStatus = if (latest.confidence >= 75) SleepStatus.ASLEEP else SleepStatus.AWAKE

        // Fix #9: Track the MOST RECENT sleep onset, not just the first.
        // Original code never updated lastAsleepTimestamp after the first onset,
        // so mid-night awakenings caused morning estimates to include the entire
        // awake period, massively inflating reported sleep duration.
        // We now update on every AWAKE → ASLEEP transition.
        val currentStatus = _signals.value.status
        if (newStatus == SleepStatus.ASLEEP && currentStatus != SleepStatus.ASLEEP) {
            lastAsleepTimestamp = eventTime
            prefs?.edit()?.putString("last_asleep_time", eventTime.toString())?.apply()
        }

        _signals.update { current ->
            current.copy(
                status       = newStatus,
                confidence   = latest.confidence,
                ambientLight = latest.light.toFloat(),
                deviceMotion = latest.motion,
                timestamp    = eventTime,
                isTracking   = current.isTracking
            )
        }
    }

    fun setTrackingState(isTracking: Boolean) {
        _signals.update { it.copy(isTracking = isTracking) }
    }

    // Fix S3: resetSession() is called by startTracking() only when it is
    // confirmed to be a genuinely new session (see SleepRepositoryImpl).
    // This function itself is unchanged — the resume-vs-reset decision
    // is made by the caller so this stays a clean, unconditional wipe.
    fun resetSession() {
        lastAsleepTimestamp = null
        prefs?.edit()?.remove("last_asleep_time")?.apply()
        _signals.value = SleepSignal()
    }
}