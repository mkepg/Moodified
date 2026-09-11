package com.moodified.app.presentation.privacy

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodified.app.core.theme.*

@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MilkWhite)
                .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            PrivacyHeader(onBack = onBack)
        }

        item {
            SectionLabel("What we collect")
            Spacer(Modifier.height(4.dp))
        }

        item {
            DataCategoryRow(
                icon = Icons.Rounded.Mood,
                title = "Mood entries",
                description = "Your check-ins and quick-log selections. Stored on this device only.",
                accent = DeepSage,
            )
            DataCategoryRow(
                icon = Icons.Rounded.DirectionsRun,
                title = "Activity",
                description = "Step counts and movement intensity. Sensor data never leaves your device.",
                accent = ValencePositive,
            )
            DataCategoryRow(
                icon = Icons.Rounded.Bedtime,
                title = "Sleep",
                description = "Estimated sleep windows derived from quiet device periods. We never read app content.",
                accent = ValenceNeutral,
            )
            DataCategoryRow(
                icon = Icons.Rounded.PhoneAndroid,
                title = "Screen use",
                description = "Aggregate screen-on time and unlock counts. We never see which apps you open.",
                accent = ValenceNegative,
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            SectionLabel("Your controls")
            Spacer(Modifier.height(4.dp))
        }

        item {
            ActionRow(
                icon = Icons.Rounded.Download,
                title = "Export your data",
                description = "Save a JSON copy to your Downloads folder",
                inProgress = state.exportStatus is PrivacyOpStatus.InProgress,
                onClick = viewModel::exportData,
            )
            ActionRow(
                icon = Icons.Rounded.Delete,
                title = "Delete all your data",
                description = "Permanently remove every entry stored on this device",
                inProgress = state.deleteStatus is PrivacyOpStatus.InProgress,
                destructive = true,
                onClick = { showDeleteConfirm = true },
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            SectionLabel("Documents")
            Spacer(Modifier.height(4.dp))
        }

        item {
            ActionRow(
                icon = Icons.Rounded.OpenInNew,
                title = "Privacy policy",
                description = state.privacyPolicyUrl,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.privacyPolicyUrl))
                    runCatching { context.startActivity(intent) }
                },
            )
        }
    }

    when (val s = state.exportStatus) {
        is PrivacyOpStatus.Success ->
            ResultDialog(
                title = "Export complete",
                body = s.message,
                onDismiss = viewModel::dismissExportStatus,
            )
        is PrivacyOpStatus.Error ->
            ResultDialog(
                title = "Export failed",
                body = s.message,
                onDismiss = viewModel::dismissExportStatus,
            )
        else -> Unit
    }

    when (val s = state.deleteStatus) {
        is PrivacyOpStatus.Success ->
            ResultDialog(
                title = "Data deleted",
                body = s.message,
                onDismiss = viewModel::dismissDeleteStatus,
            )
        is PrivacyOpStatus.Error ->
            ResultDialog(
                title = "Delete failed",
                body = s.message,
                onDismiss = viewModel::dismissDeleteStatus,
            )
        else -> Unit
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete all your data?") },
            text = {
                Text(
                    "This permanently removes every mood entry, sleep estimate, " +
                        "and activity record stored on this device. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteAllData()
                }) {
                    Text("Delete", color = ValenceNegative)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PrivacyHeader(onBack: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MilkWhite)
                .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Surface(shape = RoundedCornerShape(8.dp), color = DeepSage.copy(alpha = 0.08f)) {
            Text(
                text = "PRIVACY",
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.8.sp,
                        fontSize = 10.sp,
                    ),
                color = DeepSage,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Privacy & data",
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = DmSerifDisplay),
            color = TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Everything stays on your device. You can export or delete it any time.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style =
            MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
            ),
        color = TextTertiary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun DataCategoryRow(
    icon: ImageVector,
    title: String,
    description: String,
    accent: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    inProgress: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = if (destructive) ValenceNegative else DeepSage
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(enabled = !inProgress, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (inProgress) {
                CircularProgressIndicator(
                    color = accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun ResultDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
