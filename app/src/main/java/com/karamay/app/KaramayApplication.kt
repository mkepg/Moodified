package com.karamay.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * ## HiltWorkerFactory wiring
 *
 * [PurgeWorker] and [TelemetryWorker] are both annotated with `@HiltWorker`,
 * which means WorkManager must use [HiltWorkerFactory] to instantiate them
 * rather than the default [WorkerFactory].  Without this wiring, WorkManager
 * will crash at runtime with:
 *
 *   `java.lang.RuntimeException: Cannot create an instance of class PurgeWorker`
 *
 * We implement [Configuration.Provider] and supply [HiltWorkerFactory] so that
 * `WorkManager.getInstance(context)` always uses the Hilt-aware factory.
 * Note: when implementing [Configuration.Provider] you must NOT call
 * `WorkManager.initialize()` manually — WorkManager auto-initialises itself
 * lazily using `getWorkManagerConfiguration()`.
 */
@HiltAndroidApp
class KaramayApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}