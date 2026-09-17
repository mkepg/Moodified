package com.moodified.app.core.navigation

import com.moodified.app.presentation.insight.InsightTab

sealed class AppRoutes(val route: String) {
    data object CheckIn : AppRoutes("checkin")

    data object Insight : AppRoutes("insight") {
        const val ROUTE_WITH_ARGS = "insight?tab={tab}"

        const val TAB_ARG = "tab"

        fun withTab(tab: InsightTab): String =
            if (tab == InsightTab.OVERVIEW) {
                route
            } else {
                "insight?tab=${tab.queryValue()}"
            }
    }

    data object Care : AppRoutes("care")

    data object More : AppRoutes("more")

    data object QuickLog : AppRoutes("quicklog")

    data object Calendar : AppRoutes("calendar")

    // Privacy (Phase D)
    data object Privacy : AppRoutes("privacy")

    // Support surface (Phase 4)
    data object Support : AppRoutes("support") {
        const val HELP = "support/help"
        const val ABOUT = "support/about"
        const val LICENSES = "support/licenses"
    }
}

private fun InsightTab.queryValue(): String =
    when (this) {
        InsightTab.OVERVIEW -> "overview"
        InsightTab.ACTIVITY -> "activity"
        InsightTab.SLEEP -> "sleep"
        InsightTab.SCREEN_USE -> "screen_use"
    }
