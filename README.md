# Moodified — Application Overview

> A private, low-friction mood tracking and adaptive wellbeing companion that turns daily check-ins and quiet behavioral signals into meaningful self-understanding.

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Problem Statement & Purpose](#2-problem-statement--purpose)
3. [Target Users](#3-target-users)
4. [Feature Overview](#4-feature-overview)
5. [User Workflows](#5-user-workflows)
6. [System Overview](#6-system-overview)
7. [Platform & Technology](#7-platform--technology)
8. [Application Organization](#8-application-organization)
9. [Information We Store](#9-information-we-store)
10. [On-Device Intelligence](#10-on-device-intelligence)
11. [Notifications & Background Behavior](#11-notifications--background-behavior)
12. [Offline & Resilience](#12-offline--resilience)
13. [Account & Data Lifecycle](#13-account--data-lifecycle)
14. [Security & Privacy](#14-security--privacy)
15. [Device Permissions](#15-device-permissions)

---

## 1. Introduction

**Moodified** is a mobile application that helps people notice, understand, and respond to how they feel — in seconds, not sessions. It pairs an effortless two-tap mood log with quiet, on-device context (activity, sleep, screen use) and adaptive in-the-moment care suggestions, so that small daily check-ins build into meaningful long-term insight.

Unlike clinical mental health platforms or generic wellness journals, Moodified targets a specific, underserved use case: **frictionless, context-aware, privacy-respecting self-reflection** — without therapy-level commitment, without account creation, and without surrendering personal data to the cloud.

The application:

- Captures a mood entry in under five seconds via a two-tap modal sheet.
- Quietly enriches each entry with passive behavioral context the user already generates.
- Surfaces interpretive trends and tailored care suggestions when the user needs them most.
- Operates entirely on-device in its current release — no accounts, no servers, no PII.
- Cleanly separates how moods are logged, how context is inferred, and how care is delivered.

Moodified is currently available on **Android**.

---

## 2. Problem Statement & Purpose

### The gap Moodified addresses

People who want to understand their emotional patterns face a few unsatisfying options:

| Existing solution | Limitation |
| --- | --- |
| Paper / generic journaling apps | High effort; users abandon within weeks. |
| Clinical mental-health platforms | Heavyweight; assume therapy or diagnosis; not for daily check-ins. |
| Wearables and fitness trackers | Strong on body data, weak on subjective mood; rarely tie the two together. |
| Generic wellness apps | Same breathing exercise for everyone; no awareness of the user's actual state. |

### Moodified's approach

Moodified solves these problems by combining three deliberate design choices:

- **Two-tap logging.** A modal sheet captures mood valence and arousal in seconds — no forms, no slow flow, no friction.
- **Passive enrichment.** With the user's permission, the app correlates each entry with on-device signals: step counts, activity intensity, inferred sleep windows, and screen interaction.
- **Adaptive Care.** A transparent rule-based engine reads the user's current behavioral snapshot and chooses interventions matched to that state — calming routines for elevated arousal, motivational nudges for sustained low valence, recovery suggestions when sleep is below baseline.

### Concrete problems solved

- **Logging fatigue** that causes users to abandon mood journals.
- **Missing context** behind a mood rating ("why did I feel that way?").
- **Generic advice** that doesn't match how the user actually feels right now.
- **Privacy concerns** that come with handing personal mental-health data to a cloud provider.
- **Trend blindness** — understanding how sleep, activity, and digital habits actually shape mood over time.

---

## 3. Target Users

Moodified is designed as a **self-reflection and behavior-context tool**, not a clinical product. Its primary audiences are:

### Health-conscious individuals (25–45)
Already track fitness or sleep with another app and want mood added to the picture. Primary value: trend correlation between sleep, activity, and mood.

### Stress- and anxiety-aware users
Want to identify mood triggers and have a tool to reach for in difficult moments. Primary value: quick logging combined with timely, adaptive support.

### Digital-wellness advocates
Concerned about how screen time and late-night phone use affect mood and rest. Primary value: screen-use insights and late-night-usage flags.

### Therapy and coaching clients *(planned)*
Want shareable longitudinal data and exports for sessions. Primary value: future therapist-share links and PDF exports.

A single Moodified install supports one primary user. Future cloud releases will introduce optional accounts, cross-device sync, and shareable links for clinical contexts.

---

## 4. Feature Overview

### 4.1 Quick Mood Logging
- Two-step modal sheet: **valence** (positive / neutral / negative) followed by **arousal** (calm / balanced / elevated).
- Optional note attached to any entry.
- Polished success animation confirms each save.
- Deep-linkable via `moodified://quicklog`, so notifications or shortcuts open directly into the logging flow.

### 4.2 Daily Check-In Dashboard
- The current inferred mood state (when no manual entry exists yet).
- Today's manual mood entries.
- Permission prompts when tracking is incomplete.
- Quick entry-points to calendar history and the quick-log sheet.

### 4.3 Insights
Four dedicated insight surfaces:

| Surface | Shown content |
| --- | --- |
| **Mood Insight** (overview) | Weekly mood distribution, inferred trends, and data completeness summary. |
| **Activity Insight** | Step trends, intensity bands, active minutes, weekly graphs. |
| **Sleep Insight** | Sleep duration, efficiency, and consistency over 7+ days. |
| **Screen Use Insight** | Daily screen time, unlock counts, late-night-usage flags, session analysis. |

### 4.4 Calendar Browse
A month-grid calendar lets users tap any past date to review every mood entry logged that day, supporting longitudinal reflection.

### 4.5 Adaptive Care
A swipeable stack of intervention cards generated by the Care engine. Card categories:

- **Micro-confirmations** — gentle acknowledgements ("You seem a bit low — that's okay.").
- **Guided routines** — short, actionable exercises (e.g., five-minute breathing).
- **Trend alerts** — week-over-week shifts ("Your sleep is down this week.").
- **Motivation nudges** — positive reinforcement for consistent logging or behavioral wins.

User engagement (dismiss, engage, feedback) is persisted to refine future suggestions.

### 4.6 Micro-Prompt Notifications
When eligibility conditions are satisfied (no recent log, appropriate time of day, etc.), a lightweight notification invites the user to check in — and deep-links straight into the quick-log sheet on tap.

### 4.7 Settings & Permissions
A consolidated settings surface offers:

- Per-domain tracking toggles (Activity, Sleep, Interaction).
- Direct deep-links to the relevant system permission screens.
- Navigation entry points to detailed insight screens.
- Privacy policy and one-tap data deletion.

---

## 5. User Workflows

### 5.1 First launch
1. Install and open Moodified.
2. The app presents a brief splash and requests Activity Recognition and Notifications permissions.
3. The user lands on the **Check In** tab.
4. Permission cards prompt for any additional context the user wishes to enable (e.g., Usage Access for screen-use and sleep inference).

### 5.2 Daily mood logging
1. Tap the central **Quick Log** action.
2. Select valence, then arousal, then (optionally) add a note.
3. The entry is saved instantly; a success animation plays; the sheet auto-dismisses.
4. The new entry appears immediately on the Check In dashboard.

### 5.3 Passive enrichment (background)
1. A small monitoring service hosts three independent trackers (activity, sleep, interaction).
2. Each tracker streams its signals into the local database.
3. A nightly rollover task produces a daily summary just after midnight.
4. A short periodic task flushes in-flight data so nothing is lost.
5. A weekly maintenance task removes data older than the retention window.

### 5.4 Reviewing insights
1. Tap **Insight** to see weekly mood distribution and data completeness.
2. Optionally drill into Activity, Sleep, or Screen Use insight cards for detail.
3. Return to the dashboard with state preserved.

### 5.5 Receiving Care
1. On every visit to **Care**, the Care engine evaluates the user's current state.
2. It considers the latest inferred mood, recent trends, and the user's recent intervention history.
3. It emits a ranked list of intervention cards.
4. The user engages or dismisses cards; feedback is persisted to inform future suggestions.

### 5.6 Deep-link logging
1. A notification (or an OS shortcut) carries the `moodified://quicklog` link.
2. The app opens the quick-log sheet immediately, on top of whichever tab the user was viewing.
3. The user logs and is returned to where they were.

---

## 6. System Overview

Moodified is built around a clean three-layer model: the **device** captures signals and stores everything locally, an **on-device intelligence layer** interprets that data, and the **app experience** presents it to the user. In the current release, no cloud component is required for the product to function.

```
┌────────────────────────────────────────────────────────────────┐
│                    On-Device Signals & Storage                  │
│   • Mood entries     • Activity & step counts                   │
│   • Sleep windows    • Screen-use sessions                      │
│   • All data stored locally in a versioned database             │
└──────────────────────────────┬─────────────────────────────────┘
                               │ reactive data streams
                               ▼
┌────────────────────────────────────────────────────────────────┐
│                  On-Device Intelligence Layer                   │
│                                                                │
│  • Rule-based mood inference (transparent, explainable)        │
│  • Care evaluation (matches interventions to current state)    │
│  • Background workers (rollover, telemetry flush, retention)   │
└──────────────────────────────┬─────────────────────────────────┘
                               │ stateful flows
                               ▼
┌────────────────────────────────────────────────────────────────┐
│                       Moodified App (Android)                  │
│                                                                │
│  • Check In, Insight, Quick Log, Care, More                    │
│  • Deep-link entry points (quick log, notifications)           │
│  • Polished Material 3 experience, edge-to-edge                │
└────────────────────────────────────────────────────────────────┘
```

### Key design principles

- **On-device by default.** All personal data lives on the user's phone in the current release; nothing sensitive is transmitted off the device.
- **Transparent, not opaque.** No black-box machine learning. Every inferred mood comes with a plain-language explanation and a confidence score.
- **Manual input always outranks inference.** When the user logs a mood manually, the app uses that — inference only fills in the gaps.
- **Graceful degradation.** When a permission is denied, the corresponding tracker pauses and the app surfaces a clear, non-nagging re-prompt card; nothing breaks.

---

## 7. Platform & Technology

Moodified is built on production-grade, industry-standard Android technologies to give clients confidence in reliability, maintainability, and scalability.

| Area | Choice |
| --- | --- |
| Mobile platform | Native Android (8.0 and above — ~98% of active devices) |
| User interface | Jetpack Compose with Material 3 |
| Architecture | Clean Architecture + MVVM with dependency injection |
| Local storage | Versioned on-device database with exported, migration-safe schemas |
| Background work | Managed periodic workers plus a foreground "health" service for reliable tracking |
| Animation & polish | Lottie-powered micro-interactions, animated transitions, edge-to-edge rendering |
| Distribution | Android App Bundle with ABI, density, and language splits to minimize download size |
| Planned cloud layer | Industry-standard authentication, real-time database, and managed payments (Phase 1+) |

The app is built end-to-end in Kotlin against modern Android tooling, following the patterns recommended by Google's own architecture guidance.

---

## 8. Application Organization

The Moodified app is organized into focused experience areas, each with its own dedicated screens:

- **Check In** — the main home surface showing current mood, today's entries, and tracking status.
- **Insight** — overview plus three drill-down surfaces (Activity, Sleep, Screen Use).
- **Quick Log** — the two-tap mood-capture action, available from any tab.
- **Care** — adaptive intervention cards tailored to the user's current state.
- **More** — settings, permissions, privacy controls, and data management.

A shared bottom-navigation shell ties these areas together. Full-screen surfaces such as the calendar, individual insight detail views, and the privacy screen hide the bottom bar for an immersive read.

---

## 9. Information We Store

To provide the service, Moodified records the following categories of information **on the user's device**:

| Category | What it contains |
| --- | --- |
| **Mood entries** | Valence, arousal, timestamp, optional note, and whether the entry was logged manually or inferred. |
| **Activity samples** | Periodic step counts, intensity bands, and active-minute estimates from the device's standard motion sensors. |
| **Activity summaries** | Aggregated daily totals used to power trend views efficiently. |
| **Sleep windows** | Detected sleep periods with duration, efficiency, and awakening counts. |
| **Screen-use sessions** | Unlock counts and session durations used for digital-wellness insights. |
| **Screen-use summaries** | Daily screen-time totals, unlock counts, and late-night-usage flags. |
| **Intervention history** | The Care suggestions the user has seen, plus engagement and feedback signals used to refine future recommendations. |

In the current release, **no personal data leaves the device**. The app does not require account creation, does not collect PII, and stores nothing on a remote server.

---

## 10. On-Device Intelligence

Moodified's intelligence layer is deliberately transparent — it does the heavy interpretive lifting without resorting to a black-box ML model.

| Engine | What it does for the user |
| --- | --- |
| **Rule-based mood inference** | Reads the user's daily behavior snapshot — manual entries, sleep, activity, and screen-use signals — and produces an inferred mood state when no manual entry exists, with a confidence score and a human-readable explanation. |
| **Care evaluation** | Looks at the latest inferred state, recent trends, and the user's intervention history, then emits a ranked list of intervention cards matched to the current moment. |
| **Background workers** | A short-cadence telemetry flush keeps in-flight data safe, a midnight rollover finalizes daily summaries, and a weekly maintenance pass removes data older than the retention window. |
| **Foreground tracking service** | A small, low-priority service keeps the three trackers (activity, sleep, interaction) running reliably and resumes automatically after a device reboot. |

Because the engines are rule-based, every recommendation is auditable, every inference is explainable, and the product can be tuned without retraining a model.

---

## 11. Notifications & Background Behavior

### 11.1 Notification types

Moodified's notifications are calm by design — supportive, never demanding:

| Type | Used for | Behavior |
| --- | --- | --- |
| **Micro-prompt** | Inviting the user to check in when eligibility conditions are met. | Lightweight notification that deep-links straight into the quick-log sheet. |
| **Care reminder** | Surfacing a relevant intervention based on the user's current state. | Standard notification with respectful timing. |
| **Tracking status** | Indicating that passive trackers are running. | Persistent low-priority notification tied to the foreground service. |

### 11.2 Overnight and background reliability

Modern phones aggressively pause background apps to save battery. Moodified runs a small, ongoing foreground "health" service that keeps its trackers alive enough to record meaningful activity, sleep, and screen-use context throughout the day and night. Periodic workers flush data on a short cadence and finalize summaries at midnight, so a single rollover or reboot does not result in lost data.

---

## 12. Offline & Resilience

Moodified is designed to remain fully functional regardless of network state. Because the current release operates entirely on-device, the app has no dependency on connectivity:

- **Always available.** Every feature — logging, insights, calendar, care — works without a network connection.
- **Data durability.** Periodic flushes write in-flight data to local storage so reboots, force-closes, and battery events don't lose entries.
- **Boot recovery.** A boot receiver re-establishes tracking and rescheduled work the moment the device finishes restarting.
- **Migration safety.** Versioned database schemas are exported and tested, so updates ship without putting user data at risk.

When cloud sync arrives in a future release, it is planned as an **offline-first** layer: queued actions taken while offline will replay automatically once connectivity returns.

---

## 13. Account & Data Lifecycle

### Current release
- **No account required.** Moodified ships as a fully on-device product. Users install the app, grant the permissions they're comfortable with, and begin logging — no sign-up, no email verification, no password.
- **Permission-aware experience.** Each tracking domain (activity, sleep, screen use) has its own permission and its own toggle. The user is in control at every step.
- **One-tap deletion.** A privacy screen offers immediate, comprehensive on-device data deletion. There are no remote copies to chase down.

### Planned cloud release
- **Optional identity.** Email and federated sign-in via an industry-standard authentication service.
- **Cross-device sync.** Mood entries, summaries, and care history kept in sync across the user's own devices.
- **Account deletion.** Comprehensive removal of all associated data, with no orphaned records left behind.
- **Subscription tier.** A **Moodified Pro** tier (extended history, advanced Care library, PDF exports, custom check-in schedules) and themed one-off **Care Packs** for specific life moments.

---

## 14. Security & Privacy

Privacy and security are foundational to Moodified, not features added at the end:

- **On-device by default.** All mood, sleep, activity, and interaction data lives on the user's phone in the current release. No central database, no analytics pipeline collecting personal entries.
- **No PII collected.** The app does not require a name, an email, a phone number, or any other identifying information to function.
- **Manual input is sovereign.** Inferred mood states never overwrite or modify a user's logged entries — the user's own input always wins.
- **Explicit, scoped permissions.** Each permission is requested only when its corresponding feature is in use, with a clear explanation of why.
- **Graceful degradation.** Revoking a permission pauses the relevant tracker and reduces the inference confidence accordingly — it never silently keeps tracking.
- **Comprehensive deletion.** A one-tap action permanently removes all on-device data.
- **Roadmapped hardening.** Future releases add database-level encryption, explicit data-extraction rules, signed cloud transport, and crash-reporting strictly gated to release builds.

---

## 15. Device Permissions

To deliver its features reliably, Moodified requests a small set of standard Android permissions. Each is tied to a specific feature and the app degrades gracefully if any are denied.

| Permission | Why Moodified needs it |
| --- | --- |
| Activity Recognition | Step counting and activity classification for the Activity Insight surface and for enriching mood entries. |
| Notifications | Delivering micro-prompts and gentle care reminders. |
| Usage Access | Inferring sleep windows and screen-use patterns from the device's own usage data. |
| Foreground Health Service | Keeping the tracking service running reliably as a long-running, low-priority health-type service. |
| Battery Optimization Exemption | Optional: improves tracking reliability on devices with aggressive battery management. |
| Restart on Reboot | Resumes tracking and rescheduled work automatically after the phone restarts. |

Moodified does not request camera, microphone, contacts, or location access. No biometric or audio data is ever captured.

---

*Moodified — quiet, context-aware, privacy-respecting self-reflection, designed for the people who actually have to live with their phones.*
