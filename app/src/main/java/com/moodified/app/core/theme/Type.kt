package com.moodified.app.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.moodified.app.R

// ── Font families ──────────────────────────────────────────────────────────────
// DM Serif Display — editorial, warm, characterful for headings
val DmSerifDisplay = FontFamily(
    Font(R.font.dm_serif_display_regular, FontWeight.Normal)
)

// DM Sans — geometric humanist, clean and friendly for body
val DmSans = FontFamily(
    Font(R.font.dm_sans_regular, FontWeight.Normal),
    Font(R.font.dm_sans_medium, FontWeight.Medium),
    Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
    Font(R.font.dm_sans_bold, FontWeight.Bold),
)

// ── Typography scale ───────────────────────────────────────────────────────────
val MoodifiedTypography = Typography(
    // Large display — check-in hero greeting
    displayLarge = TextStyle(
        fontFamily = DmSerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 52.sp,
        lineHeight = 60.sp,
        letterSpacing = (-0.5).sp,
        color = TextPrimary
    ),
    // Section hero titles
    displayMedium = TextStyle(
        fontFamily = DmSerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.25).sp,
        color = TextPrimary
    ),
    // Screen titles
    displaySmall = TextStyle(
        fontFamily = DmSerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
        color = TextPrimary
    ),
    // Card / modal heading
    headlineLarge = TextStyle(
        fontFamily = DmSerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = DmSerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
        color = TextPrimary
    ),
    headlineSmall = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        color = TextPrimary
    ),
    // Body large
    bodyLarge = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
        color = TextSecondary
    ),
    bodyMedium = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
        color = TextSecondary
    ),
    bodySmall = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        color = TextTertiary
    ),
    // Labels / buttons
    labelLarge = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = TextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = TextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = TextTertiary
    ),
    // Title styles
    titleLarge = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
        color = TextPrimary
    ),
    titleSmall = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = TextPrimary
    ),
)
