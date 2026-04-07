package com.karamay.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.karamay.app.core.worker.MidnightRolloverWorker
import com.karamay.app.core.worker.PurgeWorker
import com.karamay.app.core.worker.TelemetryWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class KaramayApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()
        schedulePurgeWorker()
        scheduleTelemetryWorker()
        MidnightRolloverWorker.schedule(this)
    }

    // -------------------------------------------------------------------------
    // WorkManager scheduling
    // -------------------------------------------------------------------------

    /**
     * Purges telemetry older than 14 days once per day.
     *
     * Uses [ExistingPeriodicWorkPolicy.UPDATE] so that the worker spec is refreshed
     * on app update without silently ignoring changes (KEEP) or resetting the
     * countdown timer (REPLACE).
     */
    private fun schedulePurgeWorker() {
        val request = PeriodicWorkRequestBuilder<PurgeWorker>(24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "karamay_purge_worker",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Flushes in-memory activity and interaction telemetry to Room every 15 minutes.
     * Ensures no data is lost if the process is killed between natural flush points.
     *
     * Uses [ExistingPeriodicWorkPolicy.KEEP] intentionally: the 15-minute period is
     * not time-of-day sensitive, so preserving the existing schedule when the app is
     * relaunched avoids unnecessary extra flushes.
     */
    private fun scheduleTelemetryWorker() {
        val request = PeriodicWorkRequestBuilder<TelemetryWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "karamay_telemetry_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
