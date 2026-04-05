package com.karamay.app.data.receiver.activity

import com.google.android.gms.location.DetectedActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge between [ActivityReceiver] and [ActivityRepositoryImpl].
 *
 * A manifest-declared BroadcastReceiver cannot inject a repository directly.
 * This @Singleton bus decouples the two without requiring a global object or static state.
 *
 * Responsibility split with [ActivitySignalBus]:
 *   - ActivityEventBus  → receives raw Play Services [DetectedActivity] events and
 *                         forwards them to [ActivityRepositoryImpl] via a callback listener.
 *   - ActivitySignalBus → owns the StateFlow, SharedPreferences persistence, and
 *                         exposes the observable signal to the rest of the app.
 *
 * Mirrors [com.karamay.app.data.receiver.sleep.SleepEventBus].
 */
@Singleton
class ActivityEventBus @Inject constructor() {

    @Volatile private var listener: ((DetectedActivity) -> Unit)? = null

    fun setListener(l: (DetectedActivity) -> Unit) {
        listener = l
    }

    /** Called from [ActivityRepositoryImpl.stopTracking] to prevent stale events being processed. */
    fun clearListener() {
        listener = null
    }

    fun emit(activity: DetectedActivity) {
        listener?.invoke(activity)
    }
}
