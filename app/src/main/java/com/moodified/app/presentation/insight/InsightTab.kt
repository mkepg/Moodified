package com.moodified.app.presentation.insight

enum class InsightTab {
    OVERVIEW,
    ACTIVITY,
    SLEEP,
    SCREEN_USE,
    ;

    companion object {
        fun fromQueryParam(value: String?): InsightTab =
            when (value) {
                "activity" -> ACTIVITY
                "sleep" -> SLEEP
                "screen_use" -> SCREEN_USE
                "overview" -> OVERVIEW
                else -> OVERVIEW
            }
    }
}
