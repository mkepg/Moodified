package com.moodified.app.presentation.quicklog

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodified.app.R
import com.moodified.app.core.theme.*
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.Valence
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun QuickLogSheet(
    onDismiss: () -> Unit,
    editEntryId: Long? = null,
    viewModel: QuickLogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Initialize the VM once per opening. `editEntryId` acts as the key: opening in
    // add mode vs a specific entry id re-triggers the correct startAdd/startEdit call.
    LaunchedEffect(editEntryId) {
        if (editEntryId == null) viewModel.startAdd() else viewModel.startEdit(editEntryId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is QuickLogEvent.SaveError ->
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short,
                    )
                QuickLogEvent.EntryNotFound -> onDismiss()
                QuickLogEvent.Deleted -> onDismiss()
            }
        }
    }

    LaunchedEffect(state.step) {
        if (state.step == QuickLogStep.SUCCESS) {
            delay(1600)
            viewModel.reset()
            onDismiss()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (state.step != QuickLogStep.SUCCESS) onDismiss() },
                ),
    ) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 320.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = TextPrimary,
                contentColor = MilkWhite,
                shape = RoundedCornerShape(14.dp),
            )
        }

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MilkWhite,
            tonalElevation = 0.dp,
            shadowElevation = 24.dp,
        ) {
            // Crossfade only around the SUCCESS transition — the Valence↔Arousal swap stays
            // instant because composing both steps at once (Material3 TimePicker/DatePicker +
            // OutlinedTextField) produced visible jank on debug builds. Success is a small
            // composable, so overlapping it briefly with the outgoing form is cheap and hides
            // the abrupt cut between the form and the confirmation checkmark.
            Crossfade(
                targetState = state.step == QuickLogStep.SUCCESS,
                animationSpec = tween(220),
                label = "quickLogSuccessCrossfade",
            ) { isSuccess ->
                if (isSuccess) {
                    SuccessStep(isEditMode = state.isEditMode)
                } else {
                    when (state.step) {
                        QuickLogStep.VALENCE ->
                            ValenceStep(
                                isEditMode = state.isEditMode,
                                selectedValence = state.selectedValence,
                                onSelect = viewModel::selectValence,
                                onNext = viewModel::goToArousal,
                                onDismiss = onDismiss,
                            )
                        QuickLogStep.AROUSAL ->
                            ArousalStep(
                                isEditMode = state.isEditMode,
                                selectedArousal = state.selectedArousal,
                                note = state.note,
                                timestamp = state.timestamp,
                                isTimestampCustomized = state.isTimestampCustomized,
                                onSelect = viewModel::selectArousal,
                                onNoteChange = viewModel::updateNote,
                                onTimestampChange = viewModel::updateTimestamp,
                                onResetTimestamp = viewModel::resetTimestampToNow,
                                onSave = viewModel::save,
                                onDelete = viewModel::deleteCurrent,
                                onBack = viewModel::goBackToValence,
                                isSaving = state.isSaving,
                                isDeleting = state.isDeleting,
                            )
                        // Unreachable: when isSuccess is false, state.step is VALENCE or AROUSAL.
                        // Rendered as an empty box for the brief crossfade window after step flips.
                        QuickLogStep.SUCCESS -> Box(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

// ─── Steps ────────────────────────────────────────────────────────────────────

@Composable
private fun ValenceStep(
    isEditMode: Boolean,
    selectedValence: Valence?,
    onSelect: (Valence) -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetHandle()
        SheetHeader(
            title = if (isEditMode) "Edit entry" else "How are you feeling?",
            subtitle = if (isEditMode) "Adjust the mood you logged" else "Pick the one that resonates most",
            step = 1,
            showStepIndicator = !isEditMode,
            onDismiss = onDismiss,
            showBack = false,
            onBack = {},
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Valence.entries.forEach { valence ->
                MoodSelectionCard(
                    modifier = Modifier.weight(1f),
                    iconRes =
                        when (valence) {
                            Valence.NEGATIVE -> R.drawable.ic_sad
                            Valence.NEUTRAL -> R.drawable.ic_meh
                            Valence.POSITIVE -> R.drawable.ic_happy
                        },
                    label = valence.displayLabel(),
                    accentColor =
                        when (valence) {
                            Valence.NEGATIVE -> ValenceNegative
                            Valence.NEUTRAL -> ValenceNeutral
                            Valence.POSITIVE -> ValencePositive
                        },
                    isSelected = selectedValence == valence,
                    onClick = { onSelect(valence) },
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onNext,
            enabled = selectedValence != null,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = DeepSage,
                    contentColor = MilkWhite,
                    disabledContainerColor = SageDim,
                    disabledContentColor = MilkWhite.copy(alpha = 0.5f),
                ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            Text(
                text = "Next  →",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                color = MilkWhite,
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun ArousalStep(
    isEditMode: Boolean,
    selectedArousal: Arousal?,
    note: String,
    timestamp: LocalDateTime,
    isTimestampCustomized: Boolean,
    onSelect: (Arousal) -> Unit,
    onNoteChange: (String) -> Unit,
    onTimestampChange: (LocalDateTime) -> Unit,
    onResetTimestamp: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    isSaving: Boolean,
    isDeleting: Boolean,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isFutureTimestamp = timestamp.isAfter(LocalDateTime.now())

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetHandle()
        SheetHeader(
            title = if (isEditMode) "Edit entry" else "What's your energy like?",
            subtitle = if (isEditMode) "Adjust the details below" else "Be honest — there's no wrong answer",
            step = 2,
            showStepIndicator = !isEditMode,
            onDismiss = {},
            showBack = true,
            onBack = onBack,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Arousal.entries.forEach { arousal ->
                MoodSelectionCard(
                    modifier = Modifier.weight(1f),
                    iconRes =
                        when (arousal) {
                            Arousal.LOW -> R.drawable.ic_no_energy
                            Arousal.MID -> R.drawable.ic_mid_energy
                            Arousal.HIGH -> R.drawable.ic_high_energy
                        },
                    label = arousal.displayLabel(),
                    accentColor =
                        when (arousal) {
                            Arousal.LOW -> ArousalLow
                            Arousal.MID -> ArousalMid
                            Arousal.HIGH -> ArousalHigh
                        },
                    isSelected = selectedArousal == arousal,
                    onClick = { onSelect(arousal) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        NoteField(note = note, onNoteChange = onNoteChange)

        Spacer(Modifier.height(12.dp))
        WhenChip(
            timestamp = timestamp,
            isCustomized = isTimestampCustomized,
            onTimestampChange = onTimestampChange,
            onReset = onResetTimestamp,
        )

        if (isFutureTimestamp) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Can't log a mood in the future — pick an earlier time.",
                style = MaterialTheme.typography.labelSmall,
                color = ValenceNegative,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onSave,
            enabled = selectedArousal != null && !isSaving && !isFutureTimestamp,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = DeepSage,
                    contentColor = MilkWhite,
                    disabledContainerColor = SageDim,
                    disabledContentColor = MilkWhite.copy(alpha = 0.5f),
                ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            Text(
                text =
                    when {
                        isSaving -> "Saving…"
                        isEditMode -> "Save changes"
                        else -> "Save entry"
                    },
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                color = MilkWhite,
            )
        }

        if (isEditMode) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { showDeleteConfirm = true },
                enabled = !isDeleting,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = ValenceNegative,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isDeleting) "Deleting…" else "Delete entry",
                    color = ValenceNegative,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this entry?") },
            text = { Text("This mood entry will be removed from your history and insights.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
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

// Extracted so a note keystroke recomposes only this composable and its OutlinedTextField
// rather than re-running the enclosing ArousalStep (valence icons, arousal icons, When chip,
// Save/Delete buttons). Compose's restart-scope boundary here isolates the recomposition.
@Composable
private fun NoteField(
    note: String,
    onNoteChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = note,
        onValueChange = onNoteChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = "Add a note (optional)",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
            )
        },
        singleLine = false,
        minLines = 1,
        maxLines = 3,
        shape = RoundedCornerShape(14.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DeepSage,
                unfocusedBorderColor = SageDim,
                cursorColor = DeepSage,
            ),
    )
}

@Composable
private fun WhenChip(
    timestamp: LocalDateTime,
    isCustomized: Boolean,
    onTimestampChange: (LocalDateTime) -> Unit,
    onReset: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<LocalDate?>(null) }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { showDatePicker = true }
                .border(1.dp, SageDim, RoundedCornerShape(12.dp)),
        color = MilkDeep,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = DeepSage,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "When",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
                Text(
                    text = if (isCustomized) formatTimestamp(timestamp) else "Now · ${formatTimestamp(timestamp)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary,
                )
            }
            if (isCustomized) {
                TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text(
                        text = "Reset",
                        color = DeepSage,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    timestamp.toLocalDate()
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli(),
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                            val picked =
                                java.time.Instant.ofEpochMilli(utcTimeMillis)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                            return !picked.isAfter(LocalDate.now())
                        }
                    },
            )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        pendingDate =
                            java.time.Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val pending = pendingDate
    if (pending != null) {
        TimePickerBottomSheet(
            initialHour = timestamp.hour,
            initialMinute = timestamp.minute,
            onDismiss = { pendingDate = null },
            onConfirm = { hour, minute ->
                val combined = LocalDateTime.of(pending, java.time.LocalTime.of(hour, minute))
                pendingDate = null
                onTimestampChange(combined)
            },
        )
    }
}

@Composable
private fun TimePickerBottomSheet(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val timePickerState =
        rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = false,
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(timePickerState.hour, timePickerState.minute)
            }) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Pick a time") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(state = timePickerState)
            }
        },
    )
}

private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.getDefault())

private fun formatTimestamp(ts: LocalDateTime): String = ts.format(dateFormatter)

@Composable
private fun SuccessStep(isEditMode: Boolean) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✓", fontSize = 48.sp, color = DeepSage)
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isEditMode) "Entry updated" else "Mood logged",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isEditMode) "Your changes are saved." else "Keep it up — awareness is the first step.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

// ─── Sheet chrome ─────────────────────────────────────────────────────────────

@Composable
private fun SheetHandle() {
    Box(
        Modifier
            .size(width = 40.dp, height = 4.dp)
            .clip(CircleShape)
            .background(SageDim),
    )
    Spacer(Modifier.height(16.dp))
}

@Suppress("LongParameterList")
@Composable
private fun SheetHeader(
    title: String,
    subtitle: String,
    step: Int,
    showStepIndicator: Boolean,
    onDismiss: () -> Unit,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        if (showStepIndicator) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                repeat(2) { idx ->
                    Box(
                        Modifier
                            .size(width = if (idx + 1 == step) 20.dp else 8.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(if (idx + 1 == step) DeepSage else SageDim),
                    )
                }
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineLarge.copy(fontFamily = DmSerifDisplay, fontSize = 26.sp),
        color = TextPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = TextTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ─── Selection card ───────────────────────────────────────────────────────────

@Composable
private fun MoodSelectionCard(
    modifier: Modifier,
    iconRes: Int,
    label: String,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardScale",
    )
    Surface(
        modifier =
            modifier
                .scale(scale)
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) accentColor else SageDim,
                    shape = RoundedCornerShape(20.dp),
                ),
        color = if (isSelected) accentColor.copy(alpha = 0.08f) else MilkDeep,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = label,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 11.sp,
                    ),
                color = if (isSelected) accentColor else TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
