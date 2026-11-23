package com.mioo.dao.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Precomputed immutable card typography and chrome shared across every visible list row.
 * Eliminates repeated per-row [TextStyle.copy], luminance checks, and [BorderStroke] allocations.
 */
@Immutable
data class ReadingCardStyles(
    val cardColor: Color,
    val cardBorder: BorderStroke?,
    val bodyMediumStyle: TextStyle,
    val metaStyle: TextStyle,
    val identityNormalStyle: TextStyle,
    val identityBoldStyle: TextStyle,
    val poBadgeStyle: TextStyle,
    val pillStyle: TextStyle,
    val replyBadgeStyle: TextStyle
)

val LocalReadingCardStyles = staticCompositionLocalOf<ReadingCardStyles?> { null }

internal fun buildReadingCardStyles(
    colorScheme: ColorScheme,
    typography: Typography,
    dark: Boolean
): ReadingCardStyles {
    val base = typography.bodyMedium
    val scaleSize = base.fontSize.value / 14f
    val scaleHeight = base.lineHeight.value / 20f
    val cardColor = when {
        colorScheme.background == Color.Black -> Color(0xFF0E0F12)
        dark -> colorScheme.surfaceContainerLow.copy(alpha = 1f)
        else -> Color(0xFFFCFCFD)
    }
    val cardBorder = if (dark) null else BorderStroke(0.5.dp, colorScheme.onSurface.copy(alpha = 0.06f))
    val bodyStyle = base.copy(
        fontSize = (15f * scaleSize).sp,
        lineHeight = (23.5f * scaleHeight).sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.25.sp,
        color = if (dark) Color(0xFFE3E6EA) else Color(0xFF242629)
    )
    val metaStyle = base.copy(
        fontSize = (11.5f * scaleSize).sp,
        lineHeight = (16f * scaleHeight).sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    )
    val identityNormalStyle = base.copy(
        fontSize = (12f * scaleSize).sp,
        lineHeight = (16f * scaleHeight).sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp
    )
    val identityBoldStyle = identityNormalStyle.copy(fontWeight = FontWeight.Bold)
    val poBadgeStyle = base.copy(
        fontSize = 11.sp,
        lineHeight = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
    val pillStyle = base.copy(
        fontSize = (10.5f * scaleSize).sp,
        lineHeight = (14f * scaleHeight).sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    )
    val replyBadgeStyle = typography.labelMedium.copy(
        fontSize = 11.5.sp,
        lineHeight = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
    return ReadingCardStyles(
        cardColor = cardColor,
        cardBorder = cardBorder,
        bodyMediumStyle = bodyStyle,
        metaStyle = metaStyle,
        identityNormalStyle = identityNormalStyle,
        identityBoldStyle = identityBoldStyle,
        poBadgeStyle = poBadgeStyle,
        pillStyle = pillStyle,
        replyBadgeStyle = replyBadgeStyle
    )
}

// Ratios use the project's scaled typography, preserving custom fonts and both reading settings.
@Composable
fun readingTextStyle(size: Float, height: Float, weight: FontWeight = FontWeight.Normal): TextStyle {
    val base = MaterialTheme.typography.bodyMedium
    return base.copy(fontSize = (size * base.fontSize.value / 14f).sp,
        lineHeight = (height * base.lineHeight.value / 20f).sp, fontWeight = weight,
        letterSpacing = 0.sp)
}

@Composable
fun readingBodyStyle(): TextStyle {
    val base = MaterialTheme.typography.bodyMedium
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(base, dark) {
        base.copy(
            fontSize = (15f * base.fontSize.value / 14f).sp,
            lineHeight = (23.5f * base.lineHeight.value / 20f).sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.25.sp,
            color = if (dark) Color(0xFFE3E6EA) else Color(0xFF242629)
        )
    }
}

@Composable
fun readingQuoteStyle(): TextStyle {
    val base = MaterialTheme.typography.bodyMedium
    val onSurface = MaterialTheme.colorScheme.onSurface
    return remember(base, onSurface) {
        base.copy(
            fontSize = (13.5f * base.fontSize.value / 14f).sp,
            lineHeight = (19.5f * base.lineHeight.value / 20f).sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            color = onSurface.copy(alpha = 0.75f)
        )
    }
}

@Composable
fun readingMetaStyle(): TextStyle {
    val base = MaterialTheme.typography.bodyMedium
    return remember(base) {
        base.copy(
            fontSize = (11.5f * base.fontSize.value / 14f).sp,
            lineHeight = (16f * base.lineHeight.value / 20f).sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            fontFeatureSettings = "tnum"
        )
    }
}

@Composable
fun readingTitleStyle(): TextStyle {
    val base = MaterialTheme.typography.bodyMedium
    return remember(base) {
        base.copy(
            fontSize = (16.5f * base.fontSize.value / 14f).sp,
            lineHeight = (23f * base.lineHeight.value / 20f).sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp
        )
    }
}

@Composable
fun readingBackground(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    MaterialTheme.colorScheme.background else Color(0xFFF5F6F8)

/** Opaque backing for the entire reply composer, including the navigation/IME slot. */
@Composable
fun readingComposerColor(): Color = readingBackground()

@Composable
fun readingCardColor(): Color = when {
    MaterialTheme.colorScheme.background == Color.Black -> Color(0xFF0E0F12)
    MaterialTheme.colorScheme.background.luminance() < 0.5f -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 1f)
    else -> Color(0xFFFCFCFD)
}

@Composable
fun readingCardBorder(): BorderStroke? {
    val light = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    if (!light) return null
    val onSurface = MaterialTheme.colorScheme.onSurface
    return remember(onSurface) { BorderStroke(0.5.dp, onSurface.copy(alpha = 0.06f)) }
}

@Composable
fun readingTopBarColor(): Color = readingBackground()

/** Shared type scale for settings controls and cookie selection surfaces. */
@Composable
fun settingsItemStyle() = readingTextStyle(15f, 22f, FontWeight.Medium)

@Composable
fun settingsDescriptionStyle() = readingTextStyle(12f, 18f, FontWeight.Normal)
