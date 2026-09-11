package com.moodified.app.presentation.quicklog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

@Composable
fun QuickLogSheet(
    onDismiss: () -> Unit,
    viewModel: QuickLogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Fix #36: Collect the one-shot UiEvent channel and surface errors as a Snackbar.
    // Using collectLatest means a newer error replaces a still-showing one immediately.
    // Previously, QuickLogUiState.error was populated on failure but QuickLogSheet never
    // read it — the user assumed their entry was saved when it silently wasn't.
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is QuickLogEvent.SaveError -> {
                    snackbarHostState.showSnackbar(
                        message = "Couldn't save your entry. Please try again.",
                        duration = SnackbarDuration.Short,
                    )
                }
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
        // Fix #36: SnackbarHost anchored above the bottom sheet so errors are visible
        // even when the sheet is fully expanded.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    // clears the sheet height
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
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInVertically { it / 8 })
                        .togetherWith(fadeOut(tween(200)))
                },
                label = "quickLogStep",
            ) { step ->
                when (step) {
                    QuickLogStep.VALENCE ->
                        ValenceStep(
                            selectedValence = state.selectedValence,
                            onSelect = viewModel::selectValence,
                            onNext = viewModel::goToArousal,
                            onDismiss = onDismiss,
                        )
                    QuickLogStep.AROUSAL ->
                        ArousalStep(
                            selectedArousal = state.selectedArousal,
                            onSelect = viewModel::selectArousal,
                            onSave = viewModel::save,
                            onBack = viewModel::goBackToValence,
                            isSaving = state.isSaving,
                        )
                    QuickLogStep.SUCCESS -> SuccessStep()
                }
            }
        }
    }
}

// ─── Steps ────────────────────────────────────────────────────────────────────

@Composable
private fun ValenceStep(
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
            title = "How are you feeling?",
            subtitle = "Pick the one that resonates most",
            step = 1,
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

@Composable
private fun ArousalStep(
    selectedArousal: Arousal?,
    onSelect: (Arousal) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    isSaving: Boolean,
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
            title = "What's your energy like?",
            subtitle = "Be honest — there's no wrong answer",
            step = 2,
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
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onSave,
            enabled = selectedArousal != null && !isSaving,
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
                text = if (isSaving) "Saving…" else "Save entry",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                color = MilkWhite,
            )
        }
    }
}

@Composable
private fun SuccessStep() {
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
            text = "Mood logged",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Keep it up — awareness is the first step.",
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

@Composable
private fun SheetHeader(
    title: String,
    subtitle: String,
    step: Int,
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
