package com.mioo.dao.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

private fun TextUnit.scale(scale: Float): TextUnit {
    return if (this.isSpecified) this * scale else this
}

private fun TextStyle.scale(scale: Float): TextStyle {
    return this.copy(
        fontSize = this.fontSize.scale(scale),
        lineHeight = this.lineHeight.scale(scale)
    )
}

fun Typography.scale(scale: Float): Typography {
    if (scale == 1.0f) return this
    return Typography(
        displayLarge = displayLarge.scale(scale),
        displayMedium = displayMedium.scale(scale),
        displaySmall = displaySmall.scale(scale),
        headlineLarge = headlineLarge.scale(scale),
        headlineMedium = headlineMedium.scale(scale),
        headlineSmall = headlineSmall.scale(scale),
        titleLarge = titleLarge.scale(scale),
        titleMedium = titleMedium.scale(scale),
        titleSmall = titleSmall.scale(scale),
        bodyLarge = bodyLarge.scale(scale),
        bodyMedium = bodyMedium.scale(scale),
        bodySmall = bodySmall.scale(scale),
        labelLarge = labelLarge.scale(scale),
        labelMedium = labelMedium.scale(scale),
        labelSmall = labelSmall.scale(scale)
    )
}

private fun TextStyle.scaleLineHeight(scale: Float): TextStyle {
    return this.copy(
        lineHeight = this.lineHeight.scale(scale)
    )
}

fun Typography.scaleLineHeight(scale: Float): Typography {
    if (scale == 1.0f) return this
    return Typography(
        displayLarge = displayLarge.scaleLineHeight(scale),
        displayMedium = displayMedium.scaleLineHeight(scale),
        displaySmall = displaySmall.scaleLineHeight(scale),
        headlineLarge = headlineLarge.scaleLineHeight(scale),
        headlineMedium = headlineMedium.scaleLineHeight(scale),
        headlineSmall = headlineSmall.scaleLineHeight(scale),
        titleLarge = titleLarge.scaleLineHeight(scale),
        titleMedium = titleMedium.scaleLineHeight(scale),
        titleSmall = titleSmall.scaleLineHeight(scale),
        bodyLarge = bodyLarge.scaleLineHeight(scale),
        bodyMedium = bodyMedium.scaleLineHeight(scale),
        bodySmall = bodySmall.scaleLineHeight(scale),
        labelLarge = labelLarge.scaleLineHeight(scale),
        labelMedium = labelMedium.scaleLineHeight(scale),
        labelSmall = labelSmall.scaleLineHeight(scale)
    )
}
