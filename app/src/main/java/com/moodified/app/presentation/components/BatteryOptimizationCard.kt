package com.moodified.app.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.ErrorRed
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary

/**
 * Phase 3: wrapped in [AnimatedVisibility] so the card smoothly expands into
 * view when battery optimisation is detected and collapses when the user
 * resolves the issue and returns to the screen — instead of abruptly
 * appearing/disappearing.
 */
@Composable
fun BatteryOptimizationCard(
    isIgnoring:      Boolean,
    onRequestIgnore: () -> Unit
) {
    AnimatedVisibility(
        visible = !isIgnoring,
        enter   = fadeIn() + expandVertically(),
        exit    = fadeOut() + shrinkVertically(),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ErrorRed.copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
            shape           = RoundedCornerShape(20.dp),
            color           = ErrorRed.copy(alpha = 0.05f),
            tonalElevation  = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.BatteryAlert,
                        contentDescription = "Battery Restriction",
                        tint               = ErrorRed,
                        modifier           = Modifier.padding(top = 2.dp).size(20.dp)
                    )
                    Column(
                        modifier            = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text  = "Background tracking restricted",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text  = "Allow unrestricted battery usage to ensure continuous data " +
                                    "collection so that we may infer your mood better.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick        = onRequestIgnore,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape          = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text  = "Resolve issue",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                            color = ErrorRed
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector        = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint               = ErrorRed,
                            modifier           = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
