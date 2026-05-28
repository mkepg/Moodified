package com.moodified.app.core.navigation

sealed class AppRoutes(val route: String) {
    data object CheckIn          : AppRoutes("checkin")
    data object Insight          : AppRoutes("insight")
    data object Care             : AppRoutes("care")
    data object More             : AppRoutes("more")
    data object QuickLog         : AppRoutes("quicklog")
    data object Calendar         : AppRoutes("calendar")

    // User-facing insight screens (Phase C)
    data object ActivityInsight  : AppRoutes("insight/activity")
    data object SleepInsight     : AppRoutes("insight/sleep")
    data object ScreenUseInsight : AppRoutes("insight/screen_use")

    // Privacy (Phase D)
    data object Privacy          : AppRoutes("privacy")
}
