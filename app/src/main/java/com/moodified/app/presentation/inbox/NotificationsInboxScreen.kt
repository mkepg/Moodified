package com.moodified.app.presentation.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodified.app.core.theme.DeepSage
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.core.theme.SageSurface
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.TextTertiary
import com.moodified.app.domain.model.notification.NotificationRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsInboxScreen(
    onBack: () -> Unit,
    onDeepLink: (String) -> Unit,
    viewModel: NotificationsInboxViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MilkWhite)
                .statusBarsPadding(),
    ) {
        InboxTopBar(
            onBack = onBack,
            onClearAll = viewModel::dismissAll,
            clearAllEnabled = !state.isEmpty,
        )
        if (state.isEmpty) {
            InboxEmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp),
            ) {
                state.groups.forEach { group ->
                    item(key = "header_${group.date}") {
                        DayHeader(date = group.date)
                    }
                    items(count = group.records.size, key = { i -> "record_${group.records[i].id}" }) { i ->
                        val record = group.records[i]
                        DismissibleInboxRow(
                            record = record,
                            onTap = {
                                viewModel.markRead(record.id)
                                record.deepLink?.let(onDeepLink)
                            },
                            onDismissed = { viewModel.dismiss(record.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxTopBar(
    onBack: () -> Unit,
    onClearAll: () -> Unit,
    clearAllEnabled: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary,
            )
        }
        Text(
            text = "Notifications",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
        )
        if (clearAllEnabled) {
            TextButton(onClick = onClearAll) {
                Text(text = "Clear all", color = DeepSage, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun InboxEmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.NotificationsNone,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "No notifications yet",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "When Moodified sends you a check-in, it will show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    val label = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    Text(
        text = label,
        style =
            MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                fontSize = 10.sp,
            ),
        color = TextTertiary,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleInboxRow(
    record: NotificationRecord,
    onTap: () -> Unit,
    onDismissed: () -> Unit,
) {
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled) {
                    onDismissed()
                    true
                } else {
                    false
                }
            },
        )
    LaunchedEffect(record.id) {
        // Reset state on recomposition to a new record — SwipeToDismissBoxState is not keyed to record id.
        dismissState.reset()
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(SageSurface),
            )
        },
    ) {
        InboxRowContent(record = record, onTap = onTap)
    }
}

@Composable
private fun InboxRowContent(
    record: NotificationRecord,
    onTap: () -> Unit,
) {
    val isUnread = record.readAt == null
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MilkWhite)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onTap)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UnreadDot(visible = isUnread)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.title,
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                color = TextPrimary,
            )
            Text(text = record.body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun UnreadDot(visible: Boolean) {
    Box(
        modifier =
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (visible) DeepSage else androidx.compose.ui.graphics.Color.Transparent),
    )
}
