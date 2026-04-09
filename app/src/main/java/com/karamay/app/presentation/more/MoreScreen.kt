package com.karamay.app.presentation.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DataArray
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karamay.app.core.theme.*

@Composable
fun MoreScreen(
    onNavigateToActivityMonitor:    () -> Unit,
    onNavigateToSleepMonitor:       () -> Unit,
    onNavigateToInteractionMonitor: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    LazyColumn(
        modifier       = Modifier
            .fillMaxSize()
            .background(MilkWhite)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item { MoreHeader() }

        item {
            SectionHeader("Dev Tools")
            // Phase 3: DevRow and SettingsRow were near-duplicate composables
            // that differed only in the trailing element (chevron vs badge).
            // Both are now replaced by the single MenuRow composable which
            // accepts an optional trailingContent lambda.
            MenuRow(
                icon        = Icons.Outlined.DirectionsRun,
                iconBgColor = ValencePositive.copy(alpha = 0.12f),
                iconTint    = ValencePositive,
                title       = "Activity Monitor",
                description = "Step cadence · intensity classification",
                onClick     = onNavigateToActivityMonitor,
            )
            MenuRow(
                icon        = Icons.Rounded.Bedtime,
                iconBgColor = ValenceNeutral.copy(alpha = 0.12f),
                iconTint    = ValenceNeutral,
                title       = "Sleep Monitor",
                description = "UsageStats inference · screen-off gaps",
                onClick     = onNavigateToSleepMonitor,
            )
            MenuRow(
                icon        = Icons.Rounded.PhoneAndroid,
                iconBgColor = ValenceNegative.copy(alpha = 0.12f),
                iconTint    = ValenceNegative,
                title       = "Interaction Monitor",
                description = "Screen time · late-night usage",
                onClick     = onNavigateToInteractionMonitor,
            )
        }

        item {
            SectionHeader("Data")
            MenuRow(
                icon        = Icons.Rounded.DataArray,
                iconBgColor = ArousalLow.copy(alpha = 0.12f),
                iconTint    = ArousalLow,
                title       = "Seed Mock Mood Data",
                description = "Insert 14 days of synthetic mood entries",
                actionLabel = "INJECT",
                onClick     = viewModel::injectMockData,
            )
        }
    }
}

// ─── Private composables ──────────────────────────────────────────────────────

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
            text  = "Developer tools and data management.",
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

/**
 * Phase 3: unified replacement for the old [DevRow] and [SettingsRow]
 * composables, which were structurally identical and differed only in whether
 * they showed a chevron or an action badge as the trailing element.
 *
 * Rules:
 *  - If [actionLabel] is provided → show a filled badge (old SettingsRow style).
 *  - Otherwise → show a chevron arrow (old DevRow style).
 */
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
