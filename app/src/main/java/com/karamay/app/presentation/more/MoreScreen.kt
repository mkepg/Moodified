package com.karamay.app.presentation.more

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DataArray
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karamay.app.core.theme.*

@Composable
fun MoreScreen(
    onNavigateToActivityMonitor:    () -> Unit,
    onNavigateToSleepMonitor:       () -> Unit,
    onNavigateToInteractionMonitor: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var pendingTrackerAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingRequiresActivity by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val activityGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        if (permissions[Manifest.permission.ACTIVITY_RECOGNITION] == false) {
            viewModel.recordActivityRecognitionDenial()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            permissions[Manifest.permission.POST_NOTIFICATIONS] == false) {
            viewModel.recordPostNotificationDenial()
        }

        if (notifGranted && (!pendingRequiresActivity || activityGranted)) {
            pendingTrackerAction?.invoke()
        } else if (!notifGranted) {
            viewModel.stopAllTracking()
        }

        pendingTrackerAction = null
        pendingRequiresActivity = false
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasActivityPerm = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED

                val hasNotifPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else true

                val hasUsageAccess = viewModel.hasUsageAccess

                if (hasActivityPerm) viewModel.resetActivityRecognitionDenial()
                if (hasNotifPerm) viewModel.resetPostNotificationDenial()

                if (!hasNotifPerm) {
                    viewModel.stopAllTracking()
                } else {
                    if (!hasActivityPerm) {
                        viewModel.setActivityTracking(false)
                    }
                    if (!hasUsageAccess) {
                        viewModel.setSleepTracking(false)
                        viewModel.setInteractionTracking(false)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val executeToggle: (Boolean, () -> Unit) -> Unit = { requiresActivity, action ->
        val hasActivityPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        if (hasNotifPermission && (!requiresActivity || hasActivityPermission)) {
            action()
        } else {
            val needNotifPrompt = !hasNotifPermission && !state.isNotifPermanentlyDenied
            val needActPrompt = requiresActivity && !hasActivityPermission && !state.isActivityPermanentlyDenied

            if ((!hasNotifPermission && state.isNotifPermanentlyDenied) ||
                (requiresActivity && !hasActivityPermission && state.isActivityPermanentlyDenied)) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            } else {
                pendingTrackerAction = action
                pendingRequiresActivity = requiresActivity

                val permsToRequest = mutableListOf<String>()
                if (needNotifPrompt && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (needActPrompt) {
                    permsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
                }

                if (permsToRequest.isNotEmpty()) permissionLauncher.launch(permsToRequest.toTypedArray())
            }
        }
    }

    LazyColumn(
        modifier       = Modifier
            .fillMaxSize()
            .background(MilkWhite)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item { MoreHeader() }

        item {
            SectionHeader("Tracking Preferences")
            SwitchRow(
                title       = "Activity Tracking",
                description = "Notice your active patterns throughout the day",
                isChecked   = state.isActivityTracking,
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        executeToggle(true) { viewModel.setActivityTracking(true) }
                    } else {
                        viewModel.setActivityTracking(false)
                    }
                }
            )
            SwitchRow(
                title       = "Sleep Tracking",
                description = "Learn your sleep patterns from quiet moments",
                isChecked   = state.isSleepTracking,
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        if (!viewModel.hasUsageAccess) {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        } else {
                            executeToggle(false) { viewModel.setSleepTracking(true) }
                        }
                    } else {
                        viewModel.setSleepTracking(false)
                    }
                }
            )
            SwitchRow(
                title       = "Screen Time Tracking",
                description = "Understands your screen time and late-night use",
                isChecked   = state.isInteractionTracking,
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        if (!viewModel.hasUsageAccess) {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        } else {
                            executeToggle(false) { viewModel.setInteractionTracking(true) }
                        }
                    } else {
                        viewModel.setInteractionTracking(false)
                    }
                }
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            SectionHeader("Dev Tools")

            MenuRow(
                icon        = Icons.Outlined.DirectionsRun,
                iconBgColor = ValencePositive.copy(alpha = 0.12f),
                iconTint    = ValencePositive,
                title       = "Activity Monitor",
                description = "Step cadence and intensity data",
                onClick     = onNavigateToActivityMonitor,
            )
            MenuRow(
                icon        = Icons.Rounded.Bedtime,
                iconBgColor = ValenceNeutral.copy(alpha = 0.12f),
                iconTint    = ValenceNeutral,
                title       = "Sleep Monitor",
                description = "Inactivity and sleep inference data",
                onClick     = onNavigateToSleepMonitor,
            )
            MenuRow(
                icon        = Icons.Rounded.PhoneAndroid,
                iconBgColor = ValenceNegative.copy(alpha = 0.12f),
                iconTint    = ValenceNegative,
                title       = "Interaction Monitor",
                description = "Screen session and usage data",
                onClick     = onNavigateToInteractionMonitor,
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            SectionHeader("App Triggers")

            MenuRow(
                icon        = Icons.Rounded.NotificationsActive,
                iconBgColor = ArousalHigh.copy(alpha = 0.12f),
                iconTint    = ArousalHigh,
                title       = "Test Micro-Prompt",
                description = "Manually trigger a check-in notification",
                actionLabel = "TRIGGER",
                onClick     = viewModel::triggerTestMicroPrompt,
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            SectionHeader("Data")

            MenuRow(
                icon        = Icons.Rounded.DataArray,
                iconBgColor = ArousalLow.copy(alpha = 0.12f),
                iconTint    = ArousalLow,
                title       = "Seed Mock Mood Data",
                description = "Insert 14 days of synthetic mood entries",
                actionLabel = "INJECT",
                onClick     = viewModel::injectMockMoodData,
            )
            MenuRow(
                icon        = Icons.Rounded.DataArray,
                iconBgColor = ArousalLow.copy(alpha = 0.12f),
                iconTint    = ArousalLow,
                title       = "Seed Mock Activity Data",
                description = "Insert 14 days of synthetic activity summaries",
                actionLabel = "INJECT",
                onClick     = viewModel::injectMockActivityData,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = { onCheckedChange(!isChecked) }
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        SmoothAnimatedSwitch(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SmoothAnimatedSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val trackColor by animateColorAsState(
        targetValue   = if (checked) DeepSage else MilkDeep,
        animationSpec = tween(durationMillis = 250),
        label         = "trackColor"
    )
    val thumbColor by animateColorAsState(
        targetValue   = if (checked) MilkWhite else SageDim,
        animationSpec = tween(durationMillis = 250),
        label         = "thumbColor"
    )
    val thumbOffset by animateDpAsState(
        targetValue   = if (checked) 24.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness    = Spring.StiffnessMediumLow
        ),
        label         = "thumbOffset"
    )

    Box(
        modifier = Modifier
            .width(52.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

@Composable
private fun MoreHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MilkWhite)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Surface(shape = RoundedCornerShape(8.dp), color = DeepSage.copy(alpha = 0.08f)) {
            Text(
                text     = "MORE",
                style    = MaterialTheme.typography.labelSmall.copy(
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 1.8.sp,
                    fontSize      = 10.sp,
                ),
                color    = DeepSage,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text  = "More",
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = DmSerifDisplay),
            color = TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = "Settings, developer tools, and data management.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text     = text.uppercase(),
        style    = MaterialTheme.typography.labelSmall.copy(
            fontWeight    = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            fontSize      = 10.sp,
        ),
        color    = TextTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun MenuRow(
    icon:        ImageVector,
    iconBgColor: Color,
    iconTint:    Color,
    title:       String,
    description: String,
    actionLabel: String? = null,
    onClick:     () -> Unit,
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier         = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = iconTint,
                modifier           = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title,       style = MaterialTheme.typography.titleSmall,  color = TextPrimary)
            Text(text = description, style = MaterialTheme.typography.bodySmall,   color = TextSecondary)
        }
        if (actionLabel != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconBgColor,
            ) {
                Text(
                    text     = actionLabel.uppercase(),
                    style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color    = iconTint,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        } else {
            Icon(
                imageVector        = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint               = TextTertiary,
                modifier           = Modifier.size(18.dp)
            )
        }
    }
}