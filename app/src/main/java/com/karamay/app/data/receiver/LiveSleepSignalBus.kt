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

    private var prefs: SharedPreferences? = null

    @Volatile
    var lastAsleepTimestamp: LocalDateTime? = null
        private set

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences("sleep_bus_prefs", Context.MODE_PRIVATE)
            val savedTime = prefs?.getString("last_asleep_time", null)
            if (savedTime != null) {
                lastAsleepTimestamp = LocalDateTime.parse(savedTime)
            }
        }
    }

    fun emit(events: List<SleepClassifyEvent>) {
        if (events.isEmpty()) return
        val latest = events.maxByOrNull { it.timestampMillis } ?: return

        val eventTime = Instant.ofEpochMilli(latest.timestampMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        val newStatus = if (latest.confidence >= 90) SleepStatus.ASLEEP else SleepStatus.AWAKE

        if (newStatus == SleepStatus.ASLEEP && lastAsleepTimestamp == null) {
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

    fun resetSession() {
        lastAsleepTimestamp = null
        prefs?.edit()?.remove("last_asleep_time")?.apply()
        _signals.value = SleepSignal()
    }
}