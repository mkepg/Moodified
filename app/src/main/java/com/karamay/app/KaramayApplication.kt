package com.karamay.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.karamay.app.core.worker.MidnightRolloverWorker
import com.karamay.app.core.worker.PurgeWorker
import com.karamay.app.core.worker.TelemetryWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KaramayApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleWorkers()
    }

    private fun scheduleWorkers() {
        try {
            // 1. Flush in-flight telemetry to Room every 15 minutes
            TelemetryWorker.schedule(this)

            // 2. Roll over the interaction day-summary at 00:05 each night
            MidnightRolloverWorker.schedule(this)

            // 3. Purge old data to prevent database bloat
            PurgeWorker.schedule(this)

            Log.d(TAG, "All background workers scheduled successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule workers: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "KaramayApplication"
    }
}