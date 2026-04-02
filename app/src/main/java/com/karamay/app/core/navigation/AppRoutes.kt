package com.karamay.app.core.navigation

sealed class AppRoutes(val route: String) {
    data object CheckIn         : AppRoutes("checkin")
    data object Insight         : AppRoutes("insight")
    data object Intervention    : AppRoutes("intervention")
    data object More            : AppRoutes("more")
    data object QuickLog        : AppRoutes("quicklog")

    // Dev tools — prefixed so they never collide with production routes
    data object ActivityMonitor : AppRoutes("dev/activity_monitor")
}
