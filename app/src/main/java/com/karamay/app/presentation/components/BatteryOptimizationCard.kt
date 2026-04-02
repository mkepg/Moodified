package com.karamay.app.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.karamay.app.core.theme.ErrorRed
import com.karamay.app.core.theme.TextPrimary
import com.karamay.app.core.theme.TextSecondary
import com.karamay.app.core.utils.BatteryUtils

@Composable
fun BatteryOptimizationCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isIgnoring by remember {
        mutableStateOf(BatteryUtils.isIgnoringBatteryOptimizations(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoring = BatteryUtils.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!isIgnoring) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ErrorRed.copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = ErrorRed.copy(alpha = 0.05f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 12.dp)
            ) {
                // Header and Body
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BatteryAlert,
                        contentDescription = "Battery Restriction",
                        tint = ErrorRed,
                        modifier = Modifier.padding(top = 2.dp).size(20.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Background tracking restricted",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Allow unrestricted battery usage to ensure continuous data collection so that we may infer your mood better.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Elegant, right-aligned action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { BatteryUtils.requestIgnoreBatteryOptimizations(context) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Resolve issue",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                            color = ErrorRed
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}