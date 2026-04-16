package com.karamay.app.domain.model.intervention

enum class WellBeingDomain {
    MENTAL, PHYSICAL, SLEEP, DIGITAL, SOCIAL
}

enum class RoutineType {
    WIND_DOWN, FOCUS_SESSION, RECOVERY_BREAK, MORNING_ANCHOR, WELCOME
}

enum class TrendDirection {
    DECLINING, STAGNATING, IMPROVING
}

enum class NudgeTone {
    CELEBRATORY, ENCOURAGING, GENTLE
}

data class RoutinePhase(
    val title: String,
    val instruction: String,
    val durationSeconds: Int
)