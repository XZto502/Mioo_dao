package com.mioo.dao.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Ratios use the project's scaled typography, preserving custom fonts and both reading settings.
@Composable
fun readingTextStyle(size: Float, height: Float, weight: FontWeight = FontWeight.Normal): TextStyle {
    val base = MaterialTheme.typography.bodyMedium
    return base.copy(fontSize = (size * base.fontSize.value / 14f).sp,
        lineHeight = (height * base.lineHeight.value / 20f).sp, fontWeight = weight,
        letterSpacing = 0.sp)
}

@Composable
fun readingBodyStyle() = readingTextStyle(15f, 23.5f).copy(
    letterSpacing = 0.25.sp,
    color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
        Color(0xFFE3E6EA) else Color(0xFF242629))

@Composable
fun readingQuoteStyle() = readingTextStyle(13.5f, 19.5f).copy(
    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))

@Composable
fun readingMetaStyle() = readingTextStyle(11.5f, 16f).copy(fontFeatureSettings = "tnum")

@Composable
fun readingTitleStyle() = readingTextStyle(16.5f, 23f, FontWeight.SemiBold)

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
fun readingCardBorder(): BorderStroke? = if (MaterialTheme.colorScheme.background.luminance() >= 0.5f)
    BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)) else null

@Composable
fun readingTopBarColor(): Color = readingBackground()

/** Shared type scale for settings controls and cookie selection surfaces. */
@Composable
fun settingsItemStyle() = readingTextStyle(15f, 22f, FontWeight.Medium)

@Composable
fun settingsDescriptionStyle() = readingTextStyle(12f, 18f, FontWeight.Normal)
