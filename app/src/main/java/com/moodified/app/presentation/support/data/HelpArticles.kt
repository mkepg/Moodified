package com.moodified.app.presentation.support.data

data class HelpArticle(
    val id: String,
    val title: String,
    val body: String,
)

object HelpArticles {
    val all: List<HelpArticle> =
        listOf(
            HelpArticle(
                id = "what-is-moodified",
                title = "What is Moodified?",
                body =
                    "Moodified is a mood-tracking companion that combines your quick self-check-ins " +
                        "with quiet passive signals (activity, sleep, screen use) to help you notice patterns " +
                        "in how you feel over time. Everything runs on your device — nothing leaves your phone.",
            ),
            HelpArticle(
                id = "privacy",
                title = "Where does my data live?",
                body =
                    "All your mood entries, activity summaries, and inferred insights are stored locally in " +
                        "an encrypted Room database on your phone. Moodified has no server, no analytics, and no " +
                        "account system. You can export or delete everything at any time from Profile → " +
                        "Privacy & data control.",
            ),
            HelpArticle(
                id = "permissions",
                title = "Why do you ask for permissions?",
                body =
                    "Activity Recognition lets us count steps and detect movement intensity. Usage Access " +
                        "lets us learn your sleep and screen-use patterns from device idle time. Notifications " +
                        "let us send optional check-in prompts. All are optional — Moodified works fine without " +
                        "them, just with fewer passive signals to draw on.",
            ),
            HelpArticle(
                id = "inferred-mood",
                title = "What does the inferred mood mean?",
                body =
                    "When you haven't logged a mood recently, Moodified estimates one from your passive " +
                        "signals: how much you slept, how active you were, how your screen time and late-night " +
                        "use trended. The confidence score reflects how much data was available. Your own " +
                        "logged moods always take priority over inference.",
            ),
            HelpArticle(
                id = "notifications",
                title = "How do check-in notifications work?",
                body =
                    "If notifications are enabled, Moodified may send you a gentle micro-prompt at moments " +
                        "when a quick mood check-in would be most valuable. Tapping opens the two-tap Quick Log " +
                        "sheet directly. You can turn these off at any time in your system notification settings.",
            ),
            HelpArticle(
                id = "export",
                title = "Can I export my data?",
                body =
                    "Yes. Profile → Privacy & data control → Export. You'll get a JSON file with every mood " +
                        "entry, care event, and daily summary Moodified has saved. This file is yours — nothing " +
                        "is uploaded.",
            ),
            HelpArticle(
                id = "delete",
                title = "How do I delete my data?",
                body =
                    "Profile → Privacy & data control → Delete all data. This wipes the local database and " +
                        "resets Moodified to a fresh state. There's no way to recover deleted data — it never " +
                        "leaves your phone in the first place.",
            ),
            HelpArticle(
                id = "feedback",
                title = "How do I send feedback?",
                body =
                    "Profile → Send feedback opens your email app with a pre-filled draft. You can optionally " +
                        "include your device info (Android version, model, build type) to help us diagnose issues. " +
                        "No feedback data leaves your phone until you tap send in your email app.",
            ),
        )
}
