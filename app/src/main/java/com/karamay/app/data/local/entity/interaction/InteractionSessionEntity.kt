package com.karamay.app.data.local.entity.interaction

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.karamay.app.domain.model.interaction.InteractionSession
import java.time.Instant
import java.time.ZoneId

@Entity(tableName = "interaction_sessions")
data class InteractionSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMinutes: Int
    // unlockCount column removed — no longer tracked
) {
    fun toDomain(): InteractionSession = InteractionSession(
        startTime       = Instant.ofEpochMilli(startTimeMillis).atZone(ZoneId.systemDefault()).toLocalDateTime(),
        endTime         = Instant.ofEpochMilli(endTimeMillis).atZone(ZoneId.systemDefault()).toLocalDateTime(),
        durationMinutes = durationMinutes
    )

    companion object {
        fun fromDomain(session: InteractionSession): InteractionSessionEntity =
            InteractionSessionEntity(
                startTimeMillis = session.startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                endTimeMillis   = session.endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                durationMinutes = session.durationMinutes
            )
    }
}
