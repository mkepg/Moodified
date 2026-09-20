package com.moodified.app.presentation.onboarding

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.moodified.app.core.theme.DeepSage
import com.moodified.app.core.theme.DmSerifDisplay
import com.moodified.app.core.theme.MilkDeep
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.core.theme.SageSurface
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.TextTertiary
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val slides = viewModel.slides
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MilkWhite)
                .statusBarsPadding(),
    ) {
        TopBar(
            onSkip = { viewModel.completeOnboarding(onFinished) },
            showSkip = pagerState.currentPage < slides.lastIndex,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            OnboardingPageContent(
                slide = slides[page],
                onActivityGranted = viewModel::onActivityRecognitionGranted,
                onUsageAccessGranted = viewModel::onUsageAccessGranted,
            )
        }

        PagerFooter(
            slideCount = slides.size,
            currentPage = pagerState.currentPage,
            onNext = {
                if (pagerState.currentPage < slides.lastIndex) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    viewModel.completeOnboarding(onFinished)
                }
            },
            isLastSlide = pagerState.currentPage == slides.lastIndex,
        )
    }
}

@Composable
private fun TopBar(
    onSkip: () -> Unit,
    showSkip: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f))
        if (showSkip) {
            TextButton(onClick = onSkip) {
                Text(text = "Skip", color = TextTertiary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    slide: OnboardingSlide,
    onActivityGranted: () -> Unit,
    onUsageAccessGranted: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = slide.title,
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = DmSerifDisplay),
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = slide.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        when (slide.kind) {
            OnboardingSlide.Kind.WELCOME,
            OnboardingSlide.Kind.QUICK_LOG,
            OnboardingSlide.Kind.READY,
            -> {
                // Illustration slot — text-only for now; a Lottie can drop in later.
            }
            OnboardingSlide.Kind.PERMISSIONS ->
                PermissionPrimingList(
                    onActivityGranted = onActivityGranted,
                    onUsageAccessGranted = onUsageAccessGranted,
                )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionPrimingList(
    onActivityGranted: () -> Unit,
    onUsageAccessGranted: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notifGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var activityGranted by remember { mutableStateOf(hasActivityRecognitionPermission(context)) }
    var usageGranted by remember { mutableStateOf(hasUsageAccessPermission(context)) }

    val notifLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> notifGranted = granted }
    val activityLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            activityGranted = granted
            if (granted) onActivityGranted()
        }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    notifGranted = hasNotificationPermission(context)
                    activityGranted = hasActivityRecognitionPermission(context)
                    val nowUsage = hasUsageAccessPermission(context)
                    if (nowUsage && !usageGranted) onUsageAccessGranted()
                    usageGranted = nowUsage
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PermissionCard(
            title = "Notifications",
            body = "Occasional gentle check-ins asking how you're feeling. Nothing else.",
            granted = notifGranted,
            onEnable = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
        PermissionCard(
            title = "Activity Recognition",
            body = "Sense whether you're moving or resting, so mood context can be smarter.",
            granted = activityGranted,
            onEnable = { activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
        )
        PermissionCard(
            title = "Usage Access",
            body = "See when your screen is on or off, without knowing which app.",
            granted = usageGranted,
            onEnable = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
        )
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun hasActivityRecognitionPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACTIVITY_RECOGNITION,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun hasUsageAccessPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
    return mode == AppOpsManager.MODE_ALLOWED
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    granted: Boolean,
    onEnable: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
        color = SageSurface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Row {
                if (granted) EnabledBadge() else EnableButton(onEnable)
                Spacer(Modifier.width(8.dp))
                // Skip is implicit — the slide has a global Skip in the top bar.
            }
        }
    }
}

@Composable
private fun EnabledBadge() {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)),
        color = MilkDeep,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = DeepSage,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Enabled",
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                    ),
                color = DeepSage,
            )
        }
    }
}

@Composable
private fun EnableButton(onEnable: () -> Unit) {
    Surface(
        modifier =
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onEnable),
        color = DeepSage,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = "Enable",
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                ),
            color = MilkWhite,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PagerFooter(
    slideCount: Int,
    currentPage: Int,
    onNext: () -> Unit,
    isLastSlide: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row {
            repeat(slideCount) { index ->
                val isActive = index == currentPage
                Box(
                    modifier =
                        Modifier
                            .size(if (isActive) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (isActive) DeepSage else MilkDeep),
                )
                if (index < slideCount - 1) Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onNext),
            color = DeepSage,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = if (isLastSlide) "Get started" else "Continue",
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                    ),
                color = MilkWhite,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}
