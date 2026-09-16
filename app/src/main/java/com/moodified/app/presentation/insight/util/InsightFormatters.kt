package com.moodified.app.presentation.insight.util

fun formatSteps(steps: Int): String =
    when {
        steps >= 10_000 -> "${steps / 1000}k"
        steps >= 1_000 -> "${"%.1f".format(steps / 1000f)}k"
        else -> "$steps"
    }
