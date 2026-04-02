package com.karamay.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.SleepClassifyEvent
import com.google.android.gms.location.SleepSegmentEvent
import com.karamay.app.data.local.dao.SleepSegmentDao
import com.karamay.app.data.local.entity.SleepSegmentEntity
import com.karamay.app.domain.model.SleepStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SleepReceiver : BroadcastReceiver() {

    @Inject
    lateinit var sleepSegmentDao: SleepSegmentDao
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync() // Tells OS to keep process alive

        if (SleepClassifyEvent.hasEvents(intent)) {
            val events = SleepClassifyEvent.extractEvents(intent)
            LiveSleepSignalBus.emit(events)
        }

        if (SleepSegmentEvent.hasEvents(intent)) {
            val events = SleepSegmentEvent.extractEvents(intent)
            scope.launch {
                try {
                    val entities = events.map { event ->
                        SleepSegmentEntity(
                            startTime = java.time.Instant.ofEpochMilli(event.startTimeMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString(),
                            endTime = java.time.Instant.ofEpochMilli(event.endTimeMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString(),
                            status = if (event.status == 0) SleepStatus.ASLEEP.name else SleepStatus.AWAKE.name
                        )
                    }
                    sleepSegmentDao.insertSegments(entities)
                } finally {
                    pendingResult.finish() // Safely release process back to OS
                }
            }
        } else {
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_SLEEP_DATA = "com.karamay.app.ACTION_SLEEP_DATA"
    }
}