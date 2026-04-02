package com.karamay.app.core.navigation

sealed class AppRoutes(val route: String) {
    // Main scaffold tabs
    data object CheckIn      : AppRoutes("checkin")
    data object Insight      : AppRoutes("insight")
    data object Intervention : AppRoutes("intervention")
    data object More         : AppRoutes("more")

    // Full-screen sheets — not tab items
    data object QuickLog     : AppRoutes("quicklog")
}
