package com.moodified.app.presentation.support

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.BuildConfig
import com.moodified.app.core.theme.DeepSage
import com.moodified.app.core.theme.DmSerifDisplay
import com.moodified.app.core.theme.MilkDeep
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.core.theme.SageSurface
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.TextTertiary

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MilkWhite)
                .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item { AboutHeader(onBack = onBack) }
        item {
            Spacer(Modifier.height(8.dp))
            AboutMissionCard()
        }
        item {
            Spacer(Modifier.height(24.dp))
            AboutMadeWithSection()
        }
        item {
            Spacer(Modifier.height(24.dp))
            AboutLicensesRow(onClick = onNavigateToLicenses)
        }
        item {
            Spacer(Modifier.height(32.dp))
            AboutVersionLabel()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AboutHeader(onBack: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MilkWhite)
                .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = DeepSage.copy(alpha = 0.08f)) {
                Text(
                    text = "ABOUT",
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
                text = "Moodified",
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = DmSerifDisplay),
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "A quiet companion for your emotional patterns.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AboutMissionCard() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = SageSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text =
                "Moodified is a mood-tracking companion that combines your quick self-check-ins with " +
                    "quiet passive signals — activity, sleep, screen use — to help you notice patterns in " +
                    "how you feel. Everything runs on-device. Nothing leaves your phone unless you export it.",
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = TextSecondary,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutMadeWithSection() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(
            text = "MADE WITH",
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = 10.sp,
                ),
            color = TextTertiary,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Compose", "Material 3", "Room", "Hilt", "Lottie", "WorkManager").forEach { name ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MilkDeep,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutLicensesRow(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DeepSage.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                tint = DeepSage,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Open-source licenses", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(
                text = "The projects Moodified is built on",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AboutVersionLabel() {
    val buildLabel = if (BuildConfig.DEBUG) "debug" else "release"
    Text(
        text = "Version ${BuildConfig.VERSION_NAME} · $buildLabel",
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
        color = TextTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    )
}
