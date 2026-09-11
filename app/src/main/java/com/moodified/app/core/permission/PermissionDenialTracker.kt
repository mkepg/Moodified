package com.moodified.app.core.permission

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-scoped singleton that tracks how many times the user has denied each
 * runtime permission across any screen in the app.
 *
 * A count of [MAX_DENIALS] or above means Android will silently swallow any
 * further [ActivityResultLauncher.launch] calls, so callers should redirect to
 * the app's system-settings page instead of showing the dialog again.
 */
@Singleton
class PermissionDenialTracker
    @Inject
    constructor() {
        companion object {
            const val MAX_DENIALS = 2
        }

        // Each permission gets its own counter so the two can be handled independently.
        private val _activityRecognitionDenials = MutableStateFlow(0)
        val activityRecognitionDenials: StateFlow<Int> =
            _activityRecognitionDenials.asStateFlow()

        private val _postNotificationDenials = MutableStateFlow(0)
        val postNotificationDenials: StateFlow<Int> =
            _postNotificationDenials.asStateFlow()

        fun recordActivityRecognitionDenial() {
            _activityRecognitionDenials.value++
        }

        fun recordPostNotificationDenial() {
            _postNotificationDenials.value++
        }

        /** Call when the user has granted a permission (e.g. via system settings) so the
         *  counter resets and the normal in-app dialog path is available again. */
        fun resetActivityRecognition() {
            _activityRecognitionDenials.value = 0
        }

        fun resetPostNotification() {
            _postNotificationDenials.value = 0
        }

        /** True when either permission has been denied enough times that the system
         *  dialog will no longer appear. */
        val isPermanentlyDenied: Boolean
            get() =
                _activityRecognitionDenials.value >= MAX_DENIALS ||
                    _postNotificationDenials.value >= MAX_DENIALS
    }
