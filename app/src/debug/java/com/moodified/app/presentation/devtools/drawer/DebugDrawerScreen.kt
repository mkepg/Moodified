package com.moodified.app.presentation.devtools.drawer

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodified.app.core.devtools.DebugRoutes
import com.moodified.app.core.devtools.DiagnosticsSnapshot
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.presentation.devtools.DebugRow
import com.moodified.app.presentation.devtools.IdleBanner
import com.moodified.app.presentation.devtools.MonitorCard
import com.moodified.app.presentation.devtools.MonitorHeader
import com.moodified.app.presentation.devtools.SectionLabel

@Composable
fun DebugDrawerScreen(
    onBack: () -> Unit,
    onNavigateToMonitor: (route: String) -> Unit,
    viewModel: DebugDrawerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MilkWhite),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            MonitorHeader(
                title = "Debug Drawer",
                subtitle = "Internal diagnostics",
                isTracking = false,
                onBack = onBack,
            )
        }

        item { SectionLabel("Monitors") }
        item { MonitorLinksCard(onNavigateToMonitor) }

        item { Spacer(Modifier.height(16.dp)) }
        item { SectionLabel("Debug Data") }
        item { DebugDataCard(viewModel, context) }

        val snap = state.snapshot
        if (snap == null) {
            item { Spacer(Modifier.height(16.dp)) }
            item { IdleBanner("Loading diagnostics...") }
        } else {
            item { Spacer(Modifier.height(16.dp)) }
            item { SectionLabel("DB Row Counts") }
            item { DbCountsCard(snap) }
            item { Spacer(Modifier.height(16.dp)) }
            item { SectionLabel("Workers") }
            item { WorkersCard(snap) }
            item { Spacer(Modifier.height(16.dp)) }
            item { SectionLabel("Permissions") }
            item { PermissionsCard(snap) }
            item { Spacer(Modifier.height(16.dp)) }
            item { SectionLabel("Trackers") }
            item { TrackersCard(snap) }
        }
    }
}

@Composable
private fun MonitorLinksCard(onNavigate: (String) -> Unit) {
    MonitorCard {
        OutlinedButton(
            onClick = { onNavigate(DebugRoutes.ACTIVITY_MONITOR) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Activity Monitor") }
        OutlinedButton(
            onClick = { onNavigate(DebugRoutes.SLEEP_MONITOR) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sleep Monitor") }
        OutlinedButton(
            onClick = { onNavigate(DebugRoutes.INTERACTION_MONITOR) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Interaction Monitor") }
    }
}

@Composable
private fun DebugDataCard(
    viewModel: DebugDrawerViewModel,
    context: Context,
) {
    MonitorCard {
        OutlinedButton(
            onClick = { viewModel.seedMoodData() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Seed Mock Mood Data") }
        OutlinedButton(
            onClick = { viewModel.seedActivityData() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Seed Mock Activity Data") }
        OutlinedButton(
            onClick = { viewModel.fireTestMicroPrompt(context) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Fire Test Micro-Prompt") }
    }
}

@Composable
private fun DbCountsCard(snap: DiagnosticsSnapshot) {
    MonitorCard {
        snap.dbRowCounts.entries.sortedBy { it.key }.forEach { (table, count) ->
            DebugRow(table, count.toString())
        }
    }
}

@Composable
private fun WorkersCard(snap: DiagnosticsSnapshot) {
    MonitorCard {
        snap.workerStatuses.forEach { w ->
            val value = if (w.lastRunMillis != null) "${w.state} @ ${w.lastRunMillis}" else w.state
            DebugRow(w.tag, value)
        }
    }
}

@Composable
private fun PermissionsCard(snap: DiagnosticsSnapshot) {
    MonitorCard {
        snap.permissionGrants.forEach { p ->
            DebugRow(p.permission.substringAfterLast('.'), if (p.granted) "granted" else "denied")
        }
    }
}

@Composable
private fun TrackersCard(snap: DiagnosticsSnapshot) {
    MonitorCard {
        snap.trackerStates.entries.sortedBy { it.key }.forEach { (name, enabled) ->
            DebugRow(name, if (enabled) "on" else "off")
        }
    }
}
