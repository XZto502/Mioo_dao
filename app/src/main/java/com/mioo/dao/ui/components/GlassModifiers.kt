package com.mioo.dao.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Global glassmorphism (毛玻璃) utilities for the MiooDao app.
 *
 * Provides Modifier extensions and composable helpers to apply
 * a consistent frosted-glass aesthetic across all surfaces.
 */
object GlassStyle {
    // Semi-transparent fill colors (kept frosted, but less see-through)
    val lightGlass = Color(0xE6FFFFFF)        // White ~90% opaque
    val darkGlass = Color(0xD92B2930)         // Dark surface ~85% opaque
    val lightGlassCard = Color(0xF2FFFFFF)    // White ~95% opaque (cards need readability)
    val darkGlassCard = Color(0xEB282530)     // Dark ~92% opaque

    // Border / highlight tint
    val lightBorder = Color(0x33FFFFFF)        // Subtle white edge highlight
    val darkBorder = Color(0x28FFFFFF)         // Very subtle edge in dark

    // Gradient overlays for extra depth (softer so fill opacity dominates)
    val lightGradient = Brush.verticalGradient(
        listOf(
            Color(0x22FFFFFF),
            Color(0x08FFFFFF)
        )
    )
    val darkGradient = Brush.verticalGradient(
        listOf(
            Color(0x14FFFFFF),
            Color(0x05FFFFFF)
        )
    )
}

/**
 * Applies a glassmorphism background fill + subtle border to the [Modifier].
 *
 * When [enabled] is false, uses solid surface colors without the frosted look.
 *
 * @param isDark  Whether the current theme is dark mode.
 * @param shape   Shape to clip and border (default 16.dp rounded).
 * @param elevation Shadow elevation.
 * @param borderWidth Width of the highlight border.
 * @param type   The type of glass surface — [GlassType.SURFACE] for bars/navigation,
 *               [GlassType.CARD] for content cards with higher opacity.
 * @param enabled Whether glass styling is active (respects user setting).
 */
fun Modifier.glassSurface(
    isDark: Boolean,
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 4.dp,
    borderWidth: Dp = 0.5.dp,
    type: GlassType = GlassType.SURFACE,
    enabled: Boolean = true
): Modifier {
    if (!enabled) {
        val solid = if (isDark) Color(0xFF1D1B20) else Color(0xFFF4F5F7)
        return this
            .shadow(elevation, shape)
            .clip(shape)
            .background(solid)
    }

    val fill = when (type) {
        GlassType.SURFACE -> if (isDark) GlassStyle.darkGlass else GlassStyle.lightGlass
        GlassType.CARD -> if (isDark) GlassStyle.darkGlassCard else GlassStyle.lightGlassCard
    }
    val borderColor = if (isDark) GlassStyle.darkBorder else GlassStyle.lightBorder
    val gradient = if (isDark) GlassStyle.darkGradient else GlassStyle.lightGradient

    return this
        .shadow(elevation, shape)
        .clip(shape)
        .background(fill)
        .background(gradient)
        .border(borderWidth, borderColor, shape)
}

enum class GlassType {
    SURFACE,  // For bars, navigation, overlays
    CARD      // For content cards — higher opacity for readability
}

/**
 * A convenience composable that returns the appropriate glass fill color for the current theme.
 */
@Composable
fun glassCardColor(isDark: Boolean = isSystemInDarkTheme()): Color {
    return if (isDark) GlassStyle.darkGlassCard else GlassStyle.lightGlassCard
}

/**
 * A convenience composable that returns the appropriate glass surface color for the current theme.
 */
@Composable
fun glassSurfaceColor(isDark: Boolean = isSystemInDarkTheme()): Color {
    return if (isDark) GlassStyle.darkGlass else GlassStyle.lightGlass
}
