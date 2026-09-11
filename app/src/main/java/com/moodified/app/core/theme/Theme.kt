package com.moodified.app.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MoodifiedColorScheme =
    lightColorScheme(
        primary = DeepSage,
        onPrimary = MilkWhite,
        primaryContainer = SageSurface,
        onPrimaryContainer = TextPrimary,
        secondary = SageLight,
        onSecondary = MilkWhite,
        secondaryContainer = MilkDeep,
        onSecondaryContainer = TextSecondary,
        tertiary = SageMuted,
        onTertiary = MilkWhite,
        tertiaryContainer = MilkDim,
        onTertiaryContainer = TextSecondary,
        background = MilkWhite,
        onBackground = TextPrimary,
        surface = MilkWhite,
        onSurface = TextPrimary,
        surfaceVariant = SageSurface,
        onSurfaceVariant = TextSecondary,
        surfaceTint = SageLight,
        inverseSurface = TextPrimary,
        inverseOnSurface = MilkWhite,
        outline = SageDim,
        outlineVariant = MilkDeep,
        error = ErrorRed,
        onError = Color.White,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        scrim = SurfaceOverlay,
    )

@Composable
fun MoodifiedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MoodifiedColorScheme,
        typography = MoodifiedTypography,
        content = content,
    )
}
