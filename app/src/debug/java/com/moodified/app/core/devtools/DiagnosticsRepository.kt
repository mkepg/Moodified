package com.moodified.app.core.devtools

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.work.WorkManager
import com.moodified.app.core.worker.MidnightRolloverWorker
import com.moodified.app.core.worker.PurgeWorker
import com.moodified.app.core.worker.TelemetryWorker
import com.moodified.app.data.local.database.MoodifiedDatabase
import com.moodified.app.domain.repository.ActivityRepository
import com.moodified.app.domain.repository.InteractionRepository
import com.moodified.app.domain.repository.SleepRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// ─── Data classes ─────────────────────────────────────────────────────────────

data class DiagnosticsSnapshot(
    val dbRowCounts: Map<String, Long>,
    val workerStatuses: List<WorkerStatus>,
    val permissionGrants: List<PermissionStatus>,
    val trackerStates: Map<String, Boolean>,
)

data class WorkerStatus(
    val tag: String,
    val state: String,
    val lastRunMillis: Long?,
)

data class PermissionStatus(
    val permission: String,
    val granted: Boolean,
)

// ─── Interface ────────────────────────────────────────────────────────────────

interface DiagnosticsRepository {
    fun snapshot(): Flow<DiagnosticsSnapshot>
}

// ─── Implementation ───────────────────────────────────────────────────────────

@Singleton
class DiagnosticsRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MoodifiedDatabase,
        private val workManager: WorkManager,
        private val activityRepository: ActivityRepository,
        private val interactionRepository: InteractionRepository,
        private val sleepRepository: SleepRepository,
    ) : DiagnosticsRepository {
        // ── DB row count flows ─────────────────────────────────────────────────

        private val dbCountsFlow: Flow<Map<String, Long>> =
            combine(
                database.moodEntryDao().observeCount(),
                database.sleepSegmentDao().observeCount(),
                database.activityTelemetryDao().observeCount(),
                database.activityDailySummaryDao().observeCount(),
                database.interactionSessionDao().observeCount(),
                database.interactionDailySummaryDao().observeCount(),
                database.interventionHistoryDao().observeCount(),
            ) { counts ->
                mapOf(
                    "mood_entries" to counts[0],
                    "sleep_segments" to counts[1],
                    "activity_telemetry" to counts[2],
                    "activity_daily_summaries" to counts[3],
                    "interaction_sessions" to counts[4],
                    "interaction_daily_summaries" to counts[5],
                    "intervention_history" to counts[6],
                )
            }

        // ── Worker status flows ────────────────────────────────────────────────

        private fun workerStatusFlow(workName: String): Flow<WorkerStatus> =
            workManager
                .getWorkInfosForUniqueWorkFlow(workName)
                .map { infos ->
                    val latest = infos.firstOrNull()
                    WorkerStatus(
                        tag = workName,
                        state = latest?.state?.name ?: "NEVER RUN",
                        lastRunMillis = null,
                    )
                }

        private val workerStatusesFlow: Flow<List<WorkerStatus>> =
            combine(
                workerStatusFlow(TelemetryWorker.WORK_NAME),
                workerStatusFlow(MidnightRolloverWorker.WORK_NAME),
                workerStatusFlow(PurgeWorker.WORK_NAME),
            ) { telemetry, midnight, purge ->
                listOf(telemetry, midnight, purge)
            }

        // ── Permission status (one-shot; permissions don't change while foreground) ──

        private fun checkPermission(permission: String): Boolean =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        private fun checkUsageStatsPermission(): Boolean {
            val appOps = context.getSystemService<AppOpsManager>() ?: return false
            val mode =
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            return mode == AppOpsManager.MODE_ALLOWED
        }

        private val permissionGrantsFlow: Flow<List<PermissionStatus>> =
            // Permissions only change if the user visits OS Settings; re-check on each
            // subscription restart via WhileSubscribed in the VM. A simple flow { emit }
            // is sufficient — no polling needed.
            activityRepository.observeSignal().map {
                listOf(
                    PermissionStatus(
                        permission = Manifest.permission.ACTIVITY_RECOGNITION,
                        granted = checkPermission(Manifest.permission.ACTIVITY_RECOGNITION),
                    ),
                    PermissionStatus(
                        permission = Manifest.permission.POST_NOTIFICATIONS,
                        granted = checkPermission(Manifest.permission.POST_NOTIFICATIONS),
                    ),
                    PermissionStatus(
                        permission = "android.permission.PACKAGE_USAGE_STATS",
                        granted = checkUsageStatsPermission(),
                    ),
                )
            }

        // ── Tracker state flows ────────────────────────────────────────────────

        private val trackerStatesFlow: Flow<Map<String, Boolean>> =
            combine(
                activityRepository.observeSignal(),
                interactionRepository.observeLiveSignal(),
                sleepRepository.observeLiveSignal(),
            ) { activity, interaction, sleep ->
                mapOf(
                    "activity" to activity.isTracking,
                    "interaction" to interaction.isTracking,
                    "sleep" to sleep.isTracking,
                )
            }

        // ── Public API ────────────────────────────────────────────────────────

        override fun snapshot(): Flow<DiagnosticsSnapshot> =
            combine(
                dbCountsFlow,
                workerStatusesFlow,
                permissionGrantsFlow,
                trackerStatesFlow,
            ) { dbCounts, workerStatuses, permissionGrants, trackerStates ->
                DiagnosticsSnapshot(
                    dbRowCounts = dbCounts,
                    workerStatuses = workerStatuses,
                    permissionGrants = permissionGrants,
                    trackerStates = trackerStates,
                )
            }
    }
