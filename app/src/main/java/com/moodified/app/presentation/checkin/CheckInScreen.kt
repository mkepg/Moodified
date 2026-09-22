package com.moodified.app.presentation.checkin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.compose.*
import com.moodified.app.R
import com.moodified.app.core.theme.*
import com.moodified.app.core.utils.BatteryUtils
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.Valence
import com.moodified.app.presentation.checkin.components.TodaysCareCard
import com.moodified.app.presentation.components.BatteryOptimizationCard
import com.moodified.app.presentation.components.PermissionsActionCard
import kotlin.math.absoluteValue

@Composable
fun CheckInScreen(
    onQuickLog: () -> Unit,
    onViewCalendar: () -> Unit,
    onOpenCareAll: () -> Unit,
    onEditEntry: (Long) -> Unit,
    viewModel: CheckInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val topCareIntervention by viewModel.topCareIntervention.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasActivityPermission by remember { mutableStateOf(true) }
    var hasNotificationPermission by remember { mutableStateOf(true) }
    var hasUsageAccess by remember { mutableStateOf(true) }

    val notifDeniedPermanently = state.isNotifPermanentlyDenied

    val standardPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            val activityResult = permissions[Manifest.permission.ACTIVITY_RECOGNITION]
            val notifResult =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions[Manifest.permission.POST_NOTIFICATIONS]
                } else {
                    true
                }

            if (activityResult != null) {
                hasActivityPermission = activityResult
                if (!activityResult) viewModel.recordActivityRecognitionDenial()
            }

            if (notifResult != null && notifResult is Boolean) {
                hasNotificationPermission = notifResult
                if (!notifResult) viewModel.recordPostNotificationDenial()
            }

            if (hasNotificationPermission) {
                if (hasUsageAccess) {
                    viewModel.startAllTracking()
                }
            } else {
                viewModel.stopAllTracking()
            }
        }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.updateBatteryOptimizationStatus(
                        BatteryUtils.isIgnoringBatteryOptimizations(context),
                    )

                    hasUsageAccess = viewModel.hasUsageAccess

                    hasActivityPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACTIVITY_RECOGNITION,
                    ) == PackageManager.PERMISSION_GRANTED

                    hasNotificationPermission =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED
                        } else {
                            true
                        }

                    if (hasActivityPermission) viewModel.resetActivityRecognitionDenial()
                    if (hasNotificationPermission) viewModel.resetPostNotificationDenial()

                    // [FIX APPLIED]: Safely sync state to disable toggles if permissions are revoked,
                    // without blindly starting trackers and overriding user intent.
                    viewModel.syncTrackingState(
                        hasActivity = hasActivityPermission,
                        hasNotif = hasNotificationPermission,
                        hasUsage = hasUsageAccess,
                    )
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MilkWhite),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            CheckInHeader(
                greeting = state.greeting,
                dateLabel = state.todayDate,
                hasLoggedToday = state.todayEntries.isNotEmpty(),
                recentDaySummaries = state.recentDaySummaries,
                onViewCalendar = onViewCalendar,
                onDateSelected = { selectedDate ->
                    viewModel.selectDateFromWidget(selectedDate)
                    onViewCalendar()
                },
            )
        }

        topCareIntervention?.let { action ->
            item(key = "todays_care") {
                Spacer(Modifier.height(12.dp))
                TodaysCareCard(action = action, onOpenCareAll = onOpenCareAll, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }

        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
            ) {
                // Breathing room from whatever renders above (CheckInHeader or TodaysCareCard).
                // TodaysCareCard was flush against the "Log your mood" button when all the
                // conditional permission/battery cards were hidden.
                Spacer(Modifier.height(16.dp))
                PermissionsActionCard(
                    isVisible = !hasUsageAccess,
                    title = "Usage Access Required",
                    description = "Needed to securely track screen time, late-night phone usage, and infer your sleep from screen inactivity.",
                    buttonLabel = "Open Settings",
                    onRequest = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                )
                if (!hasUsageAccess) Spacer(Modifier.height(12.dp))

                PermissionsActionCard(
                    isVisible = !hasNotificationPermission && hasUsageAccess,
                    title = "Background Sync",
                    description =
                        if (notifDeniedPermanently) {
                            "Notifications were denied. Please enable them in your device settings to keep the background service running."
                        } else {
                            "Notifications are required to keep the tracking service running reliably in the background."
                        },
                    buttonLabel = if (notifDeniedPermanently) "Open Settings" else "Grant Permission",
                    onRequest = {
                        if (notifDeniedPermanently) {
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            context.startActivity(intent)
                        } else {
                            val permsToRequest = mutableListOf<String>()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            if (!hasActivityPermission && !state.isActivityPermanentlyDenied) {
                                permsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
                            }
                            if (permsToRequest.isNotEmpty()) standardPermissionLauncher.launch(permsToRequest.toTypedArray())
                        }
                    },
                )
                if (!hasNotificationPermission && hasUsageAccess) Spacer(Modifier.height(12.dp))

                BatteryOptimizationCard(
                    isIgnoring = state.isIgnoringBattery,
                    onRequestIgnore = { BatteryUtils.requestIgnoreBatteryOptimizations(context) },
                )
                if (!state.isIgnoringBattery) Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onQuickLog,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = DeepSage,
                            contentColor = MilkWhite,
                        ),
                    elevation =
                        ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 2.dp,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.todayEntries.isNotEmpty()) "Add another entry" else "Log your mood",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                        color = MilkWhite,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (state.todayEntries.isNotEmpty()) {
            item {
                Text(
                    text = "Today's log",
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = TextTertiary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                )
            }
            items(state.todayEntries, key = { it.id }) { entry ->
                MoodEntryCard(entry = entry, onClick = { onEditEntry(entry.id) })
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

// ... [CheckInHeader, LottieHeroPage, MoodHistoryOverviewPage, DayMoodCell, MoodEntryCard, EmptyTodayCard stay identical] ...
@Composable
private fun CheckInHeader(
    greeting: String,
    dateLabel: String,
    hasLoggedToday: Boolean,
    recentDaySummaries: List<DayMoodSummary>,
    onViewCalendar: () -> Unit,
    onDateSelected: (java.time.LocalDate) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MilkWhite)
                .statusBarsPadding()
                .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(20.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SageSurface,
        ) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = DeepSage,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = greeting,
            style =
                MaterialTheme.typography.displayMedium.copy(
                    fontFamily = DmSerifDisplay,
                    fontSize = 30.sp,
                ),
            color = TextPrimary,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text =
                if (hasLoggedToday) {
                    "You've been tracking today ✨"
                } else {
                    "How are you feeling right now?"
                },
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )

        Spacer(Modifier.height(20.dp))

        HorizontalPager(
            state = pagerState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(240.dp),
        ) { page ->
            val pageOffset =
                (pagerState.currentPage - page + pagerState.currentPageOffsetFraction)
                    .absoluteValue

            val scale by animateFloatAsState(
                targetValue = 1f - (pageOffset * 0.02f).coerceIn(0f, 0.02f),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "heroPageScale",
            )

            when (page) {
                0 ->
                    LottieHeroPage(
                        modifier =
                            Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                    )
                1 ->
                    MoodHistoryOverviewPage(
                        modifier =
                            Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                        recentDaySummaries = recentDaySummaries,
                        onViewCalendar = onViewCalendar,
                        onDateSelected = onDateSelected,
                    )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(2) { index ->
                val isSelected = pagerState.currentPage == index
                val width by animateFloatAsState(
                    targetValue = if (isSelected) 20f else 8f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "dotWidth$index",
                )
                Box(
                    modifier =
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = width.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) DeepSage else SageDim),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun LottieHeroPage(modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.girl_exploring))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        speed = 0.8f,
    )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SageSurface)
                .height(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.size(210.dp),
        )
    }
}

@Composable
private fun MoodHistoryOverviewPage(
    modifier: Modifier = Modifier,
    recentDaySummaries: List<DayMoodSummary>,
    onViewCalendar: () -> Unit,
    onDateSelected: (java.time.LocalDate) -> Unit,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = SageSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onViewCalendar)
                        .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Last 7 days",
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontFamily = DmSerifDisplay,
                                fontSize = 18.sp,
                            ),
                        color = TextPrimary,
                    )
                    Text(
                        text = "Tap to open full calendar",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(DeepSage.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = "Open calendar",
                        tint = DeepSage,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                recentDaySummaries.forEach { summary ->
                    DayMoodCell(
                        summary = summary,
                        onClick = { onDateSelected(summary.date) },
                    )
                }
            }

            Column {
                HorizontalDivider(color = SageDim.copy(alpha = 0.5f), thickness = 1.dp)
                Spacer(Modifier.height(12.dp))

                val loggedDays = recentDaySummaries.count { it.totalEntries > 0 }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        recentDaySummaries
                            .mapNotNull { it.representativeEntry?.valence }
                            .groupBy { it }
                            .entries
                            .sortedByDescending { it.value.size }
                            .take(3)
                            .forEach { (valence, _) ->
                                val color =
                                    when (valence) {
                                        Valence.NEGATIVE -> ValenceNegative
                                        Valence.NEUTRAL -> ValenceNeutral
                                        Valence.POSITIVE -> ValencePositive
                                    }
                                Box(
                                    modifier =
                                        Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(color),
                                )
                            }
                    }
                    Text(
                        text = "$loggedDays / 7 days logged",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayMoodCell(
    summary: DayMoodSummary,
    onClick: () -> Unit,
) {
    val isToday = summary.date == java.time.LocalDate.now()
    val valenceColor =
        when (summary.representativeEntry?.valence) {
            Valence.NEGATIVE -> ValenceNegative
            Valence.NEUTRAL -> ValenceNeutral
            Valence.POSITIVE -> ValencePositive
            null -> null
        }

    Column(
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = summary.dayLabel.take(1),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (isToday) DeepSage else TextTertiary,
            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
        )

        if (summary.representativeEntry != null && valenceColor != null) {
            val valenceIcon =
                when (summary.representativeEntry.valence) {
                    Valence.NEGATIVE -> R.drawable.ic_sad
                    Valence.NEUTRAL -> R.drawable.ic_meh
                    Valence.POSITIVE -> R.drawable.ic_happy
                }
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(valenceColor.copy(alpha = 0.15f))
                        .border(
                            width = if (isToday) 2.dp else 1.dp,
                            color = if (isToday) DeepSage else valenceColor.copy(alpha = 0.4f),
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = valenceIcon),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MilkDeep)
                        .border(
                            width = if (isToday) 2.dp else 1.dp,
                            color = if (isToday) DeepSage else SageDim,
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = summary.dateNumber,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = if (isToday) DeepSage else TextTertiary,
                )
            }
        }

        if (summary.totalEntries > 0) {
            Text(
                text = "${summary.totalEntries}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = TextTertiary,
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun MoodEntryCard(
    entry: MoodEntryUiModel,
    onClick: () -> Unit = {},
) {
    val valenceColor =
        when (entry.valence) {
            Valence.NEGATIVE -> ValenceNegative
            Valence.NEUTRAL -> ValenceNeutral
            Valence.POSITIVE -> ValencePositive
        }

    val valenceIcon =
        when (entry.valence) {
            Valence.NEGATIVE -> R.drawable.ic_sad
            Valence.NEUTRAL -> R.drawable.ic_meh
            Valence.POSITIVE -> R.drawable.ic_happy
        }

    val arousalColor =
        when (entry.arousal) {
            Arousal.LOW -> ArousalLow
            Arousal.MID -> ArousalMid
            Arousal.HIGH -> ArousalHigh
        }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onClick),
        // Matched to Insight cards
        shape = RoundedCornerShape(24.dp),
        // Soft valence wash
        color = valenceColor.copy(alpha = 0.12f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Crisp white inner circle protects the full-color emoji from clashing
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MilkWhite)
                            .border(1.5.dp, valenceColor.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = valenceIcon),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column {
                    // Arousal styled as a bold, tracked-out kicker
                    Text(
                        text = entry.arousal.displayLabel().uppercase(),
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = 7.sp,
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        color = arousalColor,
                    )
                    Spacer(Modifier.height(2.dp))

                    // Valence taking center stage with the serif font
                    Text(
                        text = entry.valence.displayLabel(),
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.sp,
                            ),
                        color = TextPrimary,
                    )
                }
            }

            // Timestamp styled as an elegant pill matching the Insight page
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MilkWhite.copy(alpha = 0.6f),
            ) {
                Text(
                    text = entry.displayTime,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyTodayCard(onQuickLog: () -> Unit) {
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
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "✦",
                fontSize = 28.sp,
                color = DeepSage.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "No entries yet today",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tap the + below to start tracking",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
        }
    }
}
