// app/src/main/java/com/karamay/app/domain/model/interaction/InteractionDailySummary.kt
package com.karamay.app.domain.model.interaction

data class InteractionDailySummary(
    val date: String,
    val totalScreenTimeMinutes: Int,
    val unlocks: Int,
    val isPartialDay: Boolean = false
)