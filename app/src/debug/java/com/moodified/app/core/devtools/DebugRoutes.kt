package com.moodified.app.core.devtools

/**
 * Debug-only route strings. These are duplicated here (not added to the shared
 * `AppRoutes`) so release builds don't even know they exist.
 */
object DebugRoutes {
    const val ACTIVITY_MONITOR    = "dev/activity_monitor"
    const val SLEEP_MONITOR       = "dev/sleep_monitor"
    const val INTERACTION_MONITOR = "dev/interaction_monitor"
}
