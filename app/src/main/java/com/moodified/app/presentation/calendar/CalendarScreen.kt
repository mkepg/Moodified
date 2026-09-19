package com.moodified.app.presentation.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodified.app.core.theme.*
import com.moodified.app.presentation.checkin.MoodEntryCard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    onBack: () -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MilkWhite),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            CalendarTopBar(onBack = onBack)
        }

        item {
            Spacer(Modifier.height(8.dp))
            CalendarCard(
                displayedMonth = state.displayedMonth,
                selectedDate = state.selectedDate,
                dailyEntryCounts = state.dailyEntryCounts,
                onSelectDate = viewModel::selectDate,
                onPreviousMonth = viewModel::goToPreviousMonth,
                onNextMonth = viewModel::goToNextMonth,
            )
            Spacer(Modifier.height(24.dp))
        }

        item {
            SelectedDateHeader(
                selectedDate = state.selectedDate,
                entryCount = state.selectedDateEntries.size,
            )
            Spacer(Modifier.height(12.dp))
        }

        if (state.isLoadingEntries) {
            item {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = DeepSage,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        } else if (state.selectedDateEntries.isEmpty()) {
            item {
                EmptyDateCard()
            }
        } else {
            items(state.selectedDateEntries, key = { it.id }) { entry ->
                MoodEntryCard(entry = entry)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun CalendarTopBar(onBack: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = "Mood Calendar",
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontFamily = DmSerifDisplay,
                    fontSize = 22.sp,
                ),
            color = TextPrimary,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun CalendarCard(
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    dailyEntryCounts: Map<LocalDate, Int>,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    val canGoNext = !displayedMonth.plusMonths(1).isAfter(YearMonth.now())

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = MilkDeep,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onPreviousMonth,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = "Previous month",
                        tint = DeepSage,
                        modifier = Modifier.size(22.dp),
                    )
                }

                AnimatedContent(
                    targetState = displayedMonth,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (fadeIn(tween(200)) + slideInVertically { if (forward) -20 else 20 })
                            .togetherWith(fadeOut(tween(150)))
                    },
                    label = "monthLabel",
                ) { month ->
                    Text(
                        text = month.format(monthFormatter),
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontFamily = DmSerifDisplay,
                                fontSize = 17.sp,
                            ),
                        color = TextPrimary,
                    )
                }

                IconButton(
                    onClick = onNextMonth,
                    enabled = canGoNext,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Next month",
                        tint = if (canGoNext) DeepSage else SageDim,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                val daysOfWeek =
                    listOf(
                        DayOfWeek.SUNDAY,
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY,
                        DayOfWeek.SATURDAY,
                    )
                daysOfWeek.forEach { dow ->
                    Text(
                        text = dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = TextTertiary,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            val firstDayOfMonth = displayedMonth.atDay(1)
            val firstDowIndex = (firstDayOfMonth.dayOfWeek.value % 7)
            val daysInMonth = displayedMonth.lengthOfMonth()
            val totalCells = firstDowIndex + daysInMonth
            val rows = (totalCells + 6) / 7

            repeat(rows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    repeat(7) { col ->
                        val cellIndex = row * 7 + col
                        val dayNumber = cellIndex - firstDowIndex + 1

                        if (dayNumber < 1 || dayNumber > daysInMonth) {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date = displayedMonth.atDay(dayNumber)
                            val isSelected = date == selectedDate
                            val isToday = date == LocalDate.now()
                            val isFuture = date.isAfter(LocalDate.now())
                            val entryCount = dailyEntryCounts[date] ?: 0

                            CalendarDayCell(
                                modifier = Modifier.weight(1f),
                                dayNumber = dayNumber,
                                isSelected = isSelected,
                                isToday = isToday,
                                isFuture = isFuture,
                                entryCount = entryCount,
                                onClick = { if (!isFuture) onSelectDate(date) },
                            )
                        }
                    }
                }
                if (row < rows - 1) Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    modifier: Modifier,
    dayNumber: Int,
    isSelected: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    entryCount: Int,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "dayScale",
    )

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .padding(2.dp)
                .scale(scale)
                .clip(CircleShape)
                .then(
                    if (isSelected) {
                        Modifier.background(DeepSage)
                    } else if (isToday) {
                        Modifier.border(1.5.dp, DeepSage, CircleShape)
                    } else {
                        Modifier
                    },
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !isFuture,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = dayNumber.toString(),
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp,
                    ),
                color =
                    when {
                        isSelected -> MilkWhite
                        isFuture -> SageDim
                        isToday -> DeepSage
                        else -> TextPrimary
                    },
            )

            if (entryCount > 0 && !isSelected) {
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Show a maximum of 3 dots
                    repeat(entryCount.coerceAtMost(3)) {
                        Box(
                            modifier =
                                Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(DeepSage.copy(alpha = 0.5f)),
                        )
                    }
                    // Append a tiny '+' if there are more than 3 entries for the day
                    if (entryCount > 3) {
                        Text(
                            text = "+",
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = DeepSage.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedDateHeader(
    selectedDate: LocalDate,
    entryCount: Int,
) {
    val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
    val isToday = selectedDate == LocalDate.now()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column {
            Text(
                text = if (isToday) "Today" else selectedDate.format(formatter),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontFamily = DmSerifDisplay,
                        fontSize = 20.sp,
                    ),
                color = TextPrimary,
            )
            if (!isToday) {
                Text(
                    text = selectedDate.format(formatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }
        }
        if (entryCount > 0) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SageSurface,
            ) {
                Text(
                    text = "$entryCount ${if (entryCount == 1) "entry" else "entries"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DeepSage,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyDateCard() {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = SageSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "✦",
                fontSize = 24.sp,
                color = DeepSage.copy(alpha = 0.3f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "No entries for this day",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Select a highlighted date to see logs",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
        }
    }
}
