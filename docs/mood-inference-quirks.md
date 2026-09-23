# Mood inference engine — intentional quirks

These are behaviors in `RuleBasedMoodInferenceEngine` that look off at a glance but are intentional. Documented here so future readers don't "fix" them without understanding the design.

## S2. Late-night usage has a compound effect on the day

`InteractionDailySummary.lateNightUsageMinutes > LATE_NIGHT_MINUTES_THRESHOLD` (30 min) contributes to the day's score in two separate places:

1. It sets `isDigitallyFatigued = true`, which amplifies the sedentary penalty from `-5a` to `-8v/-12a` when the user also has `sedentaryMinutes > SEDENTARY_MINUTES_THRESHOLD`.
2. It applies its own direct penalty of `-10v/+5a` in the interaction rules block.

The result: a user with late-night screen usage AND a highly sedentary day gets both hits (total `-18v/-7a`). A user with late-night usage but active day only gets the direct penalty (`-10v/+5a`).

This is not double-counting a single signal — it's a compound effect. Late-night scrolling *plus* a sedentary day represents two different behavioral patterns that reinforce each other's impact on mood. Removing the compound would understate how much a "bad day" spirals.

## S4. Commute penalty applies only when in-vehicle time is a small fraction of the day

The commute rule:

```kotlin
val totalTrackedMinutes = activity.activeMinutes + activity.sedentaryMinutes
val vehicleRatio = if (totalTrackedMinutes > 0) commuteMins.toFloat() / totalTrackedMinutes else 0f
if (commuteMins > InferenceConstants.LONG_COMMUTE_MINUTES && vehicleRatio < 0.25f) {
    valenceScore -= 5
}
```

The `vehicleRatio < 0.25f` gate looks backwards — why penalize *only* when vehicle time is a small fraction? Because a commute is by definition not most of your day. If in-vehicle time exceeds 25% of the tracked day, the user is probably a professional driver (delivery, rideshare, trucking) for whom vehicle time isn't a negative-mood commute — it's their work.

The denominator is `active + sedentary` and deliberately excludes in-vehicle minutes so the ratio measures "vehicle time relative to actual on-foot/desk activity", not "vehicle time relative to the whole tracked day".

## S5. Vigorous exercise contributes to arousal only, not valence

The `vigorousMins > VIGOROUS_MINUTES_THRESHOLD` branch only bumps `arousalScore`, never `valenceScore`. Exercise typically improves mood, so this looks like a gap.

It isn't. The valence boost from exercise is already covered by the *high active minutes* branch that fires earlier:

```kotlin
if (activity.activeMinutes > dynamicHighActive) {
    // (non-sleep-deprived path)
    arousalScore += 20
    valenceScore += 15
    events += ScoringEvent("higher physical activity than usual", 15, 20)
}
```

The vigorous-specific branch is an additional arousal-only signal for exercise *intensity* — the residual "wired" feeling from a hard workout, decayed via `applyFloorDecay(15, 6h)` to roughly `+6a` by the end of the day.

The edge case this misses: someone with 20 min of vigorous exercise but low total active minutes (e.g., a HIIT session, mostly sedentary otherwise). They don't get a valence bump. This is deliberate — the engine emphasizes sustained activity over sporadic intensity for mood impact.
