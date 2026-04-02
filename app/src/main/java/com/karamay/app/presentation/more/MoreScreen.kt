package com.karamay.app.presentation.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsRun
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
import com.karamay.app.core.theme.*

@Composable
fun MoreScreen(
    onNavigateToActivityMonitor: () -> Unit
) {
    LazyColumn(
        modifier       = Modifier
            .fillMaxSize()
            .background(MilkWhite),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {

        // ── Page header ──────────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text  = "More",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = DmSerifDisplay
                    ),
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "Settings and more coming soon.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(28.dp))
            }
        }

        // ── Developer section ────────────────────────────────────────────────
        item {
            DevSectionHeader()
            Spacer(Modifier.height(8.dp))
        }

        item {
            Surface(
                modifier        = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape           = RoundedCornerShape(20.dp),
                color           = MilkDeep,
                tonalElevation  = 0.dp,
                shadowElevation = 0.dp
            ) {
                DevRow(
                    icon        = Icons.Rounded.DirectionsRun,
                    iconBgColor = ArousalHigh.copy(alpha = 0.12f),
                    iconTint    = DeepSage,
                    title       = "Activity Monitor",
                    description = "Live step counter & accelerometer sensor data",
                    onClick     = onNavigateToActivityMonitor
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Dev section header ────────────────────────────────────────────────────────

@Composable
private fun DevSectionHeader() {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector        = Icons.Outlined.BugReport,
            contentDescription = null,
            tint               = TextTertiary,
            modifier           = Modifier.size(14.dp)
        )
        Text(
            text  = "DEVELOPER",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight    = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                fontSize      = 10.sp
            ),
            color = TextTertiary
        )
    }
}

// ── Dev row item ──────────────────────────────────────────────────────────────

@Composable
private fun DevRow(
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
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
            Text(
                text  = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text(
                text  = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Icon(
            imageVector        = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint               = TextTertiary,
            modifier           = Modifier.size(18.dp)
        )
    }
}
