package com.moodified.app.presentation.onboarding

data class OnboardingSlide(
    val key: String,
    val title: String,
    val subtitle: String,
    val kind: Kind,
) {
    enum class Kind {
        WELCOME,
        QUICK_LOG,
        PERMISSIONS,
        READY,
    }
}

internal val defaultOnboardingSlides: List<OnboardingSlide> =
    listOf(
        OnboardingSlide(
            key = "welcome",
            title = "Welcome to Moodified",
            subtitle = "A calmer way to notice how you feel and what shapes it.",
            kind = OnboardingSlide.Kind.WELCOME,
        ),
        OnboardingSlide(
            key = "quicklog",
            title = "Two taps to log a mood",
            subtitle = "Pick how you feel and how much energy you have. That's it.",
            kind = OnboardingSlide.Kind.QUICK_LOG,
        ),
        OnboardingSlide(
            key = "permissions",
            title = "Ambient context, only if you want",
            subtitle =
                "Moodified can pick up gentle signals — activity, sleep patterns, screen use — " +
                    "to surface trends you’d otherwise miss. Everything is optional and stays on this device.",
            kind = OnboardingSlide.Kind.PERMISSIONS,
        ),
        OnboardingSlide(
            key = "ready",
            title = "You're set",
            subtitle = "You can revisit any of this from Profile whenever you'd like.",
            kind = OnboardingSlide.Kind.READY,
        ),
    )
