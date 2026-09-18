package com.moodified.app.data.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.moodified.app.R
import com.moodified.app.data.local.entity.notification.NotificationRecordType
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.model.mood.Valence
import com.moodified.app.domain.model.notification.NotificationRecord
import com.moodified.app.domain.repository.MoodRepository
import com.moodified.app.domain.repository.NotificationHistoryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MicroPromptReceiver : BroadcastReceiver() {
    @Inject lateinit var moodRepository: MoodRepository

    @Inject lateinit var notificationHistoryRepository: NotificationHistoryRepository

    companion object {
        private const val TAG = "MicroPromptReceiver"
        const val ACTION_SELECT_VALENCE = "com.moodified.app.ACTION_SELECT_VALENCE"
        const val ACTION_LOG_FINAL = "com.moodified.app.ACTION_LOG_FINAL"
        const val EXTRA_VALENCE = "EXTRA_VALENCE"
        const val EXTRA_AROUSAL = "EXTRA_AROUSAL"
        const val PROMPT_NOTIFICATION_ID = 405
        private const val PROMPT_CHANNEL_ID = "MicroPromptChannel"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            ACTION_SELECT_VALENCE -> handleValenceSelection(context, intent)
            ACTION_LOG_FINAL -> handleFinalLogging(context, intent)
        }
    }

    private fun handleValenceSelection(
        context: Context,
        intent: Intent,
    ) {
        val valenceStr = intent.getStringExtra(EXTRA_VALENCE) ?: return
        val nm = context.getSystemService(NotificationManager::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        // Build Arousal actions using standard app icons
        val actions =
            Arousal.entries.mapIndexed { index, arousal ->
                val arousalIntent =
                    Intent(context, MicroPromptReceiver::class.java).apply {
                        action = ACTION_LOG_FINAL
                        putExtra(EXTRA_VALENCE, valenceStr)
                        putExtra(EXTRA_AROUSAL, arousal.name)
                    }
                val pending = PendingIntent.getBroadcast(context, 10 + index, arousalIntent, flags)
                NotificationCompat.Action.Builder(
                    // Using drawable resource
                    arousal.iconRes(),
                    arousal.displayLabel(),
                    pending,
                ).build()
            }

        val secondPrompt =
            NotificationCompat.Builder(context, PROMPT_CHANNEL_ID)
                .setContentTitle("And your energy?")
                .setContentText("How does your body feel right now?")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .apply { actions.forEach { addAction(it) } }
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

        runBlocking(Dispatchers.IO) {
            notificationHistoryRepository.record(
                NotificationRecord(
                    type = NotificationRecordType.MICRO_PROMPT,
                    title = "And your energy?",
                    body = "How does your body feel right now?",
                    deepLink = null,
                    deliveredAt = System.currentTimeMillis(),
                ),
            )
        }
        nm?.notify(PROMPT_NOTIFICATION_ID, secondPrompt)
    }

    private fun handleFinalLogging(
        context: Context,
        intent: Intent,
    ) {
        val valenceStr = intent.getStringExtra(EXTRA_VALENCE) ?: return
        val arousalStr = intent.getStringExtra(EXTRA_AROUSAL) ?: return
        val pendingResult = goAsync()

        scope.launch {
            try {
                val valence = Valence.valueOf(valenceStr)
                val arousal = Arousal.valueOf(arousalStr)

                moodRepository.insertEntry(
                    MoodEntry(
                        valence = valence,
                        arousal = arousal,
                        isManual = true,
                        note = "Logged via sequential micro-prompt",
                    ),
                )

                context.getSystemService(NotificationManager::class.java)?.cancel(PROMPT_NOTIFICATION_ID)
                Log.d(TAG, "Sequential mood log complete: $valence/$arousal")
            } catch (e: Exception) {
                Log.e(TAG, "Failed final logging", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
