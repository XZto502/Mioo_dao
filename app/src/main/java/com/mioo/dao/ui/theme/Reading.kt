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
fun readingBackground(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    MaterialTheme.colorScheme.background else Color(0xFFF0F1F4)

@Composable
fun readingCardColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    DaoTheme.colors.replyCardBg.copy(alpha = 1f) else Color(0xFFFAFAFC)

@Composable
fun readingCardBorder(): BorderStroke? = if (MaterialTheme.colorScheme.background == Color.Black)
    BorderStroke(0.5.dp, Color.White.copy(alpha = 0.07f)) else null

@Composable
fun readingTopBarColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    DaoTheme.colors.glassTopBar else DaoTheme.colors.glassTopBar.copy(alpha = 0.96f)
