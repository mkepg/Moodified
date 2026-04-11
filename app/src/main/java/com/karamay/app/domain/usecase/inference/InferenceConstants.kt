package com.karamay.app.domain.usecase.inference

internal object InferenceConstants {
    const val BASE_SCORE = 50

    // Valence thresholds are now symmetric around BASE_SCORE (±12) so that a
    // day with no strong signals returns NEUTRAL rather than having a built-in
    // negative bias (previously +12 to go positive but only -10 to go negative).
    const val VALENCE_NEGATIVE_THRESHOLD = 38
    const val VALENCE_POSITIVE_THRESHOLD = 62

    const val AROUSAL_LOW_THRESHOLD  = 40
    const val AROUSAL_HIGH_THRESHOLD = 60

    // Sleep thresholds
    const val POOR_SLEEP_MINUTES      = 360
    const val GOOD_SLEEP_MINUTES_MIN  = 420
    const val GOOD_SLEEP_MINUTES_MAX  = 540
    const val GOOD_SLEEP_EFFICIENCY   = 85
    const val POOR_SLEEP_EFFICIENCY   = 75
    const val AWAKENING_THRESHOLD     = 3

    // Activity thresholds
    const val HIGH_ACTIVITY_MINUTES         = 45
    const val SEDENTARY_MINUTES_THRESHOLD   = 480
    const val VIGOROUS_MINUTES_THRESHOLD    = 15
    const val HIGH_STEPS_THRESHOLD          = 8_000
    const val LONG_COMMUTE_MINUTES          = 60

    // Interaction thresholds
    const val LATE_NIGHT_MINUTES_THRESHOLD  = 30
    // Lowered from 60 to 30 to catch moderately fragmented usage (one check every
    // ~30 min across a 16-hr day), not just compulsive >60-session days.
    const val HIGH_SESSION_COUNT            = 30
    const val SHORT_SESSION_DURATION_MINUTES = 3

    // Trend penalties
    const val SLEEP_DEBT_PENALTY_THRESHOLD       = 120
    const val LOW_ACTIVITY_CONSISTENCY_THRESHOLD = 40

    // Confidence adjustments
    const val ESTIMATED_SLEEP_PENALTY       = 10
    const val PARTIAL_DAY_ACTIVITY_PENALTY  = 8
    const val MANUAL_ENTRY_BONUS_PER_ENTRY  = 8
    const val MANUAL_ENTRY_BONUS_MAX_ENTRIES = 2

    // Minimum completeness score required to attempt a passive inference.
    // Kept at 30 so that sleep-only (40) or activity-only (40) days still produce
    // an inference, but interaction-only (20) days still fall back.
    const val MIN_COMPLETENESS_FOR_INFERENCE = 30
}