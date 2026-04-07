package com.karamay.app.presentation.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karamay.app.R
import com.karamay.app.core.theme.*
import com.karamay.app.domain.model.mood.Arousal
import com.karamay.app.domain.model.mood.Valence

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

@Composable
fun MoodHistoryScreen(
    onBack:    () -> Unit,
    viewModel: MoodHistoryViewModel = hiltViewModel(),
) {
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDeleteId.collectAsStateWithLifecycle()

    // Delete confirmation dialog
    if (pendingDelete != null) {
        DeleteConfirmationDialog(
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MilkWhite),
    ) {
        HistoryTopBar(onBack = onBack)

        when (val state = uiState) {
            is MoodHistoryUiState.Loading -> HistoryLoadingState()
            is MoodHistoryUiState.Empty   -> HistoryEmptyState()
            is MoodHistoryUiState.Error   -> HistoryErrorState(message = state.message)
            is MoodHistoryUiState.Success -> HistoryTimeline(
                days          = state.days,
                onDeleteEntry = viewModel::requestDelete,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@Composable
private fun HistoryTopBar(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 12.dp),
    ) {
        IconButton(
            onClick  = onBack,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .offset(x = (-12).dp)
                .size(32.dp)
        ) {
            Icon(
                imageVector        = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "Back",
                tint               = TextSecondary,
                modifier           = Modifier.size(18.dp),
            )
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SageSurface,
        ) {
            Text(
                text     = "Mood Journal",
                style    = MaterialTheme.typography.labelMedium,
                color    = DeepSage,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text  = "Your history",
            style = MaterialTheme.typography.displaySmall.copy(
                fontFamily = DmSerifDisplay,
                fontSize   = 30.sp,
            ),
            color = TextPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text  = "All your past mood entries, grouped by day.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

// ---------------------------------------------------------------------------
// Timeline list
// ---------------------------------------------------------------------------

@Composable
private fun HistoryTimeline(
    days: List<MoodHistoryDayUiModel>,
    onDeleteEntry: (Long) -> Unit,
) {
    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        itemsIndexed(
            items = days,
            key   = { _, day -> day.date.toString() },
        ) { index, day ->
            AnimatedVisibility(
                visible = true,
                enter   = fadeIn(tween(200, delayMillis = index * 40)) +
                          slideInVertically(
                              tween(250, delayMillis = index * 40),
                              initialOffsetY = { it / 6 },
                          ),
            ) {
                DayGroup(
                    day           = day,
                    isFirst       = index == 0,
                    isLast        = index == days.lastIndex,
                    onDeleteEntry = onDeleteEntry,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Day group — collapsible section with timeline connector
// ---------------------------------------------------------------------------

@Composable
private fun DayGroup(
    day: MoodHistoryDayUiModel,
    isFirst: Boolean,
    isLast: Boolean,
    onDeleteEntry: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        // — Timeline rail —
        TimelineRail(
            isFirst   = isFirst,
            isLast    = isLast && !expanded,
            expanded  = expanded,
            entryCount = day.entryCount,
        )

        Spacer(Modifier.width(12.dp))

        // — Day content —
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 20.dp),
        ) {
            // Date header row (tappable to collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null,
                        onClick           = { expanded = !expanded },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text  = day.dateLabel,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = TextPrimary,
                    )
                    Text(
                        text  = if (day.entryCount == 1) "1 entry"
                                else "${day.entryCount} entries",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                }
                Icon(
                    imageVector        = if (expanded) Icons.Rounded.KeyboardArrowUp
                                         else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint               = TextTertiary,
                    modifier           = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Entry cards (animated collapse)
            AnimatedVisibility(
                visible = expanded,
                enter   = fadeIn(spring(stiffness = Spring.StiffnessMedium)),
                exit    = fadeOut(tween(150)),
            ) {
                Column(
                    modifier            = Modifier.animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    day.entries.forEach { entry ->
                        MoodHistoryEntryCard(
                            entry    = entry,
                            onDelete = { onDeleteEntry(entry.id) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Timeline rail (left vertical connector + dot)
// ---------------------------------------------------------------------------

@Composable
private fun TimelineRail(
    isFirst: Boolean,
    isLast: Boolean,
    expanded: Boolean,
    entryCount: Int,
) {
    // Rail width is 24.dp so the dot sits centred
    Column(
        modifier            = Modifier.width(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top connector (hidden for the very first group)
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(if (isFirst) 22.dp else 22.dp)
                .background(if (isFirst) MilkWhite else SageDim),
        )

        // Dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(DeepSage),
        )

        // Bottom connector (hidden if collapsed and last)
        val showBottom = expanded || !isLast
        Box(
            modifier = Modifier
                .width(2.dp)
                .weight(1f)
                .background(if (showBottom) SageDim else MilkWhite),
        )
    }
}

// ---------------------------------------------------------------------------
// Entry card
// ---------------------------------------------------------------------------

@Composable
private fun MoodHistoryEntryCard(
    entry: MoodHistoryEntryUiModel,
    onDelete: () -> Unit,
) {
    val valenceColor = entry.valence.accentColor()
    val valenceIcon  = entry.valence.iconRes()
    val arousalIcon  = entry.arousal.iconRes()

    Surface(
        modifier        = Modifier.fillMaxWidth(),
        shape           = RoundedCornerShape(18.dp),
        color           = MilkDeep,
        tonalElevation  = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: icon + labels
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.weight(1f),
            ) {
                // Valence icon in tinted circle
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(valenceColor.copy(alpha = 0.12f))
                        .border(1.5.dp, valenceColor.copy(alpha = 0.30f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter            = painterResource(id = valenceIcon),
                        contentDescription = entry.valence.displayLabel(),
                        modifier           = Modifier.size(26.dp),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text  = entry.valence.displayLabel(),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                    )
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Image(
                            painter            = painterResource(id = arousalIcon),
                            contentDescription = entry.arousal.displayLabel(),
                            modifier           = Modifier.size(14.dp),
                        )
                        Text(
                            text  = entry.arousal.displayLabel(),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    if (!entry.note.isNullOrBlank()) {
                        Text(
                            text  = entry.note,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Normal,
                            ),
                            color    = TextTertiary,
                            maxLines = 2,
                        )
                    }
                }
            }

            // Right: time + delete
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text  = entry.displayTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
                IconButton(
                    onClick  = onDelete,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete entry",
                        tint               = TextTertiary.copy(alpha = 0.6f),
                        modifier           = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Delete dialog
// ---------------------------------------------------------------------------

@Composable
private fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest   = onDismiss,
        containerColor     = MilkWhite,
        shape              = RoundedCornerShape(24.dp),
        title              = {
            Text(
                text  = "Remove entry?",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
        },
        text               = {
            Text(
                text  = "This entry will be permanently deleted and cannot be recovered.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        },
        confirmButton      = {
            TextButton(
                onClick = onConfirm,
                colors  = ButtonDefaults.textButtonColors(contentColor = ErrorRed),
            ) {
                Text(
                    text  = "Delete",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton      = {
            TextButton(
                onClick = onDismiss,
                colors  = ButtonDefaults.textButtonColors(contentColor = DeepSage),
            ) {
                Text(
                    text  = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Loading / empty / error states
// ---------------------------------------------------------------------------

@Composable
private fun HistoryLoadingState() {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color     = DeepSage,
            modifier  = Modifier.size(36.dp),
            strokeWidth = 2.5.dp,
        )
    }
}

@Composable
private fun HistoryEmptyState() {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text     = "✦",
                fontSize = 40.sp,
                color    = DeepSage.copy(alpha = 0.35f),
            )
            Text(
                text      = "No history yet",
                style     = MaterialTheme.typography.headlineSmall,
                color     = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text      = "Once you log a few moods, your entries will\nappear here grouped by day.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = TextTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HistoryErrorState(message: String) {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text     = "⚠",
                fontSize = 36.sp,
                color    = ErrorRed.copy(alpha = 0.6f),
            )
            Text(
                text      = "Couldn't load history",
                style     = MaterialTheme.typography.titleMedium,
                color     = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text      = message,
                style     = MaterialTheme.typography.bodySmall,
                color     = TextTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Domain model extension helpers (keep logic off the UI)
// ---------------------------------------------------------------------------

private fun Valence.accentColor() = when (this) {
    Valence.NEGATIVE -> ValenceNegative
    Valence.NEUTRAL  -> ValenceNeutral
    Valence.POSITIVE -> ValencePositive
}

private fun Valence.iconRes() = when (this) {
    Valence.NEGATIVE -> R.drawable.ic_sad
    Valence.NEUTRAL  -> R.drawable.ic_meh
    Valence.POSITIVE -> R.drawable.ic_happy
}

private fun Arousal.iconRes() = when (this) {
    Arousal.LOW  -> R.drawable.ic_no_energy
    Arousal.MID  -> R.drawable.ic_mid_energy
    Arousal.HIGH -> R.drawable.ic_high_energy
}
