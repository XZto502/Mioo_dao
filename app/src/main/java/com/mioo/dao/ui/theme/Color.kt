package com.mioo.dao.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Expressive M3 Light Palette
val LightPrimary = Color(0xFF6750A4)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFEADDFF)
val LightOnPrimaryContainer = Color(0xFF21005D)

val LightSecondary = Color(0xFF625B71)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE8DEF8)
val LightOnSecondaryContainer = Color(0xFF1D192B)

val LightTertiary = Color(0xFF7D5260)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFFD8E4)
val LightOnTertiaryContainer = Color(0xFF31111D)

val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFF9DEDC)
val LightOnErrorContainer = Color(0xFF410E0B)

val LightBackground = Color(0xFFF4F5F7)
val LightOnBackground = Color(0xFF1D1B20)
val LightSurface = Color(0xFFF4F5F7)
val LightOnSurface = Color(0xFF1D1B20)
val LightSurfaceVariant = Color(0xFFE7E0EC)
val LightOnSurfaceVariant = Color(0xFF49454F)
val LightOutline = Color(0xFF79747E)

// Expressive M3 Dark Palette
val DarkPrimary = Color(0xFFD0BCFF)
val DarkOnPrimary = Color(0xFF381E72)
val DarkPrimaryContainer = Color(0xFF4F378B)
val DarkOnPrimaryContainer = Color(0xFFEADDFF)

val DarkSecondary = Color(0xFFCCC2DC)
val DarkOnSecondary = Color(0xFF332D41)
val DarkSecondaryContainer = Color(0xFF4A4458)
val DarkOnSecondaryContainer = Color(0xFFE8DEF8)

val DarkTertiary = Color(0xFFEFB8C8)
val DarkOnTertiary = Color(0xFF492532)
val DarkTertiaryContainer = Color(0xFF633B48)
val DarkOnTertiaryContainer = Color(0xFFFFD8E4)

val DarkError = Color(0xFFF2B8B5)
val DarkOnError = Color(0xFF601410)
val DarkErrorContainer = Color(0xFF8C1D18)
val DarkOnErrorContainer = Color(0xFFF9DEDC)

val DarkBackground = Color(0xFF141218)
val DarkOnBackground = Color(0xFFE6E1E5)
val DarkSurface = Color(0xFF1D1B20)
val DarkOnSurface = Color(0xFFE6E1E5)
val DarkSurfaceVariant = Color(0xFF49454F)
val DarkOnSurfaceVariant = Color(0xFFCAC4D0)
val DarkOutline = Color(0xFF938F99)

// Dao Custom Colors (For PO, Sage, Admin, etc.)
val LightPoColor = Color(0xFF007A87)       // Teal Blue for PO in light theme
val DarkPoColor = Color(0xFF4DD0E1)        // Vibrant Teal for PO in dark theme

val LightSageColor = Color(0xFF5B7E5B)     // Muted Sage Green
val DarkSageColor = Color(0xFF8CAF8C)      // Pastel Sage Green

val LightAdminColor = Color(0xFFC62828)    // Crimson Red for Admin
val DarkAdminColor = Color(0xFFFF5252)     // Coral Red for Admin

val LightQuoteLinkColor = Color(0xFFD81B60) // Magenta/pink for >>No.xxxx
val DarkQuoteLinkColor = Color(0xFFFF4081)  // Neon Magenta/pink for >>No.xxxx

val LightThreadCardBg = Color(0xEDF4F5F7)   // ~93% matching background
val DarkThreadCardBg = Color(0xE61D1B20)    // ~90% matching dark surface

val LightReplyCardBg = Color(0xEAF4F5F7)   // ~92% matching background
val DarkReplyCardBg = Color(0xE31D1B20)     // ~89% matching dark surface

// FAB colors — mint/teal accent inspired by reference screenshot
val LightFabColor = Color(0xFFB2DFDB)      // Soft mint green for FAB background
val LightOnFabColor = Color(0xFF004D40)    // Dark teal for FAB text/icon
val DarkFabColor = Color(0xFF00897B)       // Teal 600 for dark FAB
val DarkOnFabColor = Color(0xFFE0F2F1)    // Light mint for dark FAB text/icon

// Glass surface colors for bars
val LightGlassTopBar = Color(0xDDF4F5F7)    // ~87% matching background
val DarkGlassTopBar = Color(0xDD1D1B20)     // ~87% matching dark surface
val LightGlassNavBar = Color(0xDDF4F5F7)    // ~87% matching background
val DarkGlassNavBar = Color(0xDD1D1B20)     // ~87% matching dark surface

// Sage tag badge colors
val LightSageTagColor = Color(0xFFD32F2F)  // Red for "已SAGE" badge
val DarkSageTagColor = Color(0xFFEF5350)   // Lighter red for dark mode

@Immutable
data class DaoCustomColors(
    val po: Color,
    val sage: Color,
    val admin: Color,
    val quoteLink: Color,
    val threadCardBg: Color,
    val replyCardBg: Color,
    val fabBg: Color,
    val fabContent: Color,
    val sageTag: Color,
    val glassTopBar: Color,
    val glassNavBar: Color
)

val LocalDaoCustomColors = staticCompositionLocalOf {
    DaoCustomColors(
        po = Color.Unspecified,
        sage = Color.Unspecified,
        admin = Color.Unspecified,
        quoteLink = Color.Unspecified,
        threadCardBg = Color.Unspecified,
        replyCardBg = Color.Unspecified,
        fabBg = Color.Unspecified,
        fabContent = Color.Unspecified,
        sageTag = Color.Unspecified,
        glassTopBar = Color.Unspecified,
        glassNavBar = Color.Unspecified
    )
}

/** Whether frosted-glass / ambient-glow styling is enabled. */
val LocalGlassEffectEnabled = staticCompositionLocalOf { true }
