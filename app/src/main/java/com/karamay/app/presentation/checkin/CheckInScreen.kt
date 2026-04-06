package com.karamay.app.presentation.checkin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.compose.*
import com.karamay.app.R
import com.karamay.app.core.theme.*
import com.karamay.app.domain.model.mood.Arousal
import com.karamay.app.domain.model.mood.Valence
import com.karamay.app.presentation.components.BatteryOptimizationCard

@Composable
fun CheckInScreen(
    onQuickLog: () -> Unit,
    viewModel: CheckInViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MilkWhite),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            CheckInHero(
                greeting       = state.greeting,
                dateLabel      = state.todayDate,
                hasLoggedToday = state.todayEntries.isNotEmpty(),
                onQuickLog     = onQuickLog
            )
        }

        if (state.todayEntries.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text  = "Today's log",
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight    = FontWeight.SemiBold
                    ),
                    color    = TextTertiary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                )
            }

            items(state.todayEntries, key = { it.id }) { entry ->
                MoodEntryCard(entry = entry)
                Spacer(Modifier.height(10.dp))
            }
        }

        if (state.todayEntries.isEmpty() && !state.isLoading) {
            item {
                EmptyTodayCard(onQuickLog = onQuickLog)
            }
        }
    }
}

@Composable
private fun CheckInHero(
    greeting: String,
    dateLabel: String,
    hasLoggedToday: Boolean,
    onQuickLog: () -> Unit
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.girl_exploring)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations  = LottieConstants.IterateForever,
        speed       = 0.8f
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MilkWhite)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 28.dp)
        ) {
            Spacer(Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SageSurface
            ) {
                Text(
                    text     = dateLabel,
                    style    = MaterialTheme.typography.labelMedium,
                    color    = DeepSage,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text  = greeting,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = DmSerifDisplay,
                    fontSize   = 38.sp
                ),
                color = TextPrimary
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text  = if (hasLoggedToday) "You've been tracking today ✨"
                else "How are you feeling right now?",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(SageSurface)
                    .height(260.dp),
                contentAlignment = Alignment.Center
            ) {
                LottieAnimation(
                    composition = composition,
                    progress    = { progress },
                    modifier    = Modifier.size(230.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            BatteryOptimizationCard()
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onQuickLog,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape  = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepSage,
                    contentColor   = MilkWhite
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 2.dp
                )
            ) {
                Icon(
                    imageVector        = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier           = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = if (hasLoggedToday) "Add another entry" else "Log your mood",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                    color = MilkWhite
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun MoodEntryCard(entry: MoodEntryUiModel) {
    val valenceColor = when (entry.valence) {
        Valence.NEGATIVE -> ValenceNegative
        Valence.NEUTRAL  -> ValenceNeutral
        Valence.POSITIVE -> ValencePositive
    }

    val valenceIcon = when (entry.valence) {
        Valence.NEGATIVE -> R.drawable.ic_sad
        Valence.NEUTRAL  -> R.drawable.ic_meh
        Valence.POSITIVE -> R.drawable.ic_happy
    }

    val arousalIcon = when (entry.arousal) {
        Arousal.LOW  -> R.drawable.ic_no_energy
        Arousal.MID  -> R.drawable.ic_mid_energy
        Arousal.HIGH -> R.drawable.ic_high_energy
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape           = RoundedCornerShape(20.dp),
        color           = MilkDeep,
        tonalElevation  = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(valenceColor.copy(alpha = 0.12f))
                        .border(1.5.dp, valenceColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = valenceIcon),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column {
                    Text(
                        text  = entry.valence.displayLabel(),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Image(
                            painter = painterResource(id = arousalIcon),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text  = entry.arousal.displayLabel(),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            Text(
                text  = entry.displayTime, // Uses pre-formatted string from the ViewModel
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun EmptyTodayCard(onQuickLog: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape           = RoundedCornerShape(20.dp),
        color           = SageSurface,
        tonalElevation  = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text     = "✦",
                fontSize = 28.sp,
                color    = DeepSage.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text  = "No entries yet today",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "Tap the + below to start tracking",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
    }
}