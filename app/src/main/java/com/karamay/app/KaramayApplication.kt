package com.karamay.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.karamay.app.core.worker.PurgeWorker
import com.karamay.app.core.worker.TelemetryWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class KaramayApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        setupPeriodicWorkers()
    }

    private fun setupPeriodicWorkers() {
        // Run daily purge only when battery is not low
        val purgeConstraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val purgeRequest = PeriodicWorkRequestBuilder<PurgeWorker>(1, TimeUnit.DAYS)
            .setConstraints(purgeConstraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "purge_telemetry",
            ExistingPeriodicWorkPolicy.KEEP,
            purgeRequest
        )

        // Flush telemetry every 15 minutes reliably
        val telemetryRequest = PeriodicWorkRequestBuilder<TelemetryWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "flush_telemetry",
            ExistingPeriodicWorkPolicy.KEEP,
            telemetryRequest
        )
    }
}