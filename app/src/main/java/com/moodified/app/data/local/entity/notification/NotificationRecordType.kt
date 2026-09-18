package com.moodified.app.data.local.entity.notification

/**
 * Category of a notification stored in the inbox. Persisted as the enum name (String).
 */
enum class NotificationRecordType {
    MICRO_PROMPT,
    CARE_REMINDER,
    TREND_ALERT,
    TRACKING_STATUS,
}
