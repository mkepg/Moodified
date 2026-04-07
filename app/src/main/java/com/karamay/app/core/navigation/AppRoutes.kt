package com.karamay.app.core.navigation

sealed class AppRoutes(val route: String) {
    data object CheckIn             : AppRoutes("checkin")
    data object Insight             : AppRoutes("insight")
    data object Intervention        : AppRoutes("intervention")
    data object More                : AppRoutes("more")
    data object QuickLog            : AppRoutes("quicklog")

    // ── New ──────────────────────────────────────────────────────────────
    data object MoodHistory         : AppRoutes("mood_history")

    // Dev tools (unchanged)
    data object ActivityMonitor     : AppRoutes("dev/activity_monitor")
    data object SleepMonitor        : AppRoutes("dev/sleep_monitor")
    data object InteractionMonitor  : AppRoutes("dev/interaction_monitor")
}
