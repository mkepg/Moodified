package com.moodified.app.core.devtools

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.moodified.app.MainActivity
import com.moodified.app.R
import com.moodified.app.data.local.entity.notification.NotificationRecordType
import com.moodified.app.data.receiver.MicroPromptReceiver
import com.moodified.app.domain.model.mood.Valence
import com.moodified.app.domain.model.notification.NotificationRecord
import com.moodified.app.domain.repository.NotificationHistoryRepository
import com.moodified.app.domain.usecase.devtools.SeedMockActivityDataUseCase
import com.moodified.app.domain.usecase.devtools.SeedMockMoodDataUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugMockDataSeeder
    @Inject
    constructor(
        private val seedMoodData: SeedMockMoodDataUseCase,
        private val seedActivityData: SeedMockActivityDataUseCase,
        private val notificationHistoryRepository: NotificationHistoryRepository,
    ) : MockDataSeeder {
        override val isAvailable: Boolean = true

        override suspend fun seedMoodData() = seedMoodData.invoke()

        override suspend fun seedActivityData() = seedActivityData.invoke()

        override suspend fun fireTestMicroPrompt(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(
                        "MicroPromptChannel",
                        "Gentle Check-ins",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = "Context-aware prompts asking how you are feeling."
                        setShowBadge(true)
                    }
                nm.createNotificationChannel(channel)
            }

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            // Quick action buttons for the notification
            val actions =
                Valence.entries.mapIndexed { index, valence ->
                    val intent =
                        Intent(context, MicroPromptReceiver::class.java).apply {
                            action = MicroPromptReceiver.ACTION_SELECT_VALENCE
                            putExtra(MicroPromptReceiver.EXTRA_VALENCE, valence.name)
                        }
                    val pending = PendingIntent.getBroadcast(context, index, intent, flags)

                    NotificationCompat.Action.Builder(
                        valence.iconRes(),
                        valence.displayLabel(),
                        pending,
                    ).build()
                }

            // Tap Intent for deep linking to QuickLogSheet
            val tapIntent =
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("moodified://quicklog")
                }

            val pendingTapIntent =
                PendingIntent.getActivity(
                    context,
                    0,
                    tapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val title = "Moodified is with you"
            val body = "You've been resting for a bit. How are you feeling?"

            val notification =
                NotificationCompat.Builder(context, "MicroPromptChannel")
                    .setContentTitle(title)
                    .setContentText(body)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentIntent(pendingTapIntent)
                    .apply { actions.forEach { addAction(it) } }
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()

            notificationHistoryRepository.record(
                NotificationRecord(
                    type = NotificationRecordType.MICRO_PROMPT,
                    title = title,
                    body = body,
                    deepLink = "moodified://quicklog",
                    deliveredAt = System.currentTimeMillis(),
                ),
            )
            nm.notify(MicroPromptReceiver.PROMPT_NOTIFICATION_ID, notification)
        }
    }
