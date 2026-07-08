package com.mioo.dao.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onBackground = LightOnBackground,
    background = LightBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onBackground = DarkOnBackground,
    background = DarkBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline
)

// 1. Custom Teal Color Scheme (PO Teal accent)
private val LightTealColorScheme = lightColorScheme(
    primary = Color(0xFF00796B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201A),
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant
)

private val DarkTealColorScheme = darkColorScheme(
    primary = Color(0xFF80CBC4),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF004D40),
    onPrimaryContainer = Color(0xFFB2DFDB),
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant
)

// 2. Custom Pink Color Scheme (Sakura Pink accent)
private val LightPinkColorScheme = lightColorScheme(
    primary = Color(0xFFD81B60),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF8BBD0),
    onPrimaryContainer = Color(0xFF3B0017),
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant
)

private val DarkPinkColorScheme = darkColorScheme(
    primary = Color(0xFFF48FB1),
    onPrimary = Color(0xFF56002A),
    primaryContainer = Color(0xFF880E4F),
    onPrimaryContainer = Color(0xFFF8BBD0),
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant
)

// 3. Custom Green Color Scheme (Forest Green accent)
private val LightGreenColorScheme = lightColorScheme(
    primary = Color(0xFF388E3C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF002900),
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant
)

private val DarkGreenColorScheme = darkColorScheme(
    primary = Color(0xFFA5D6A7),
    onPrimary = Color(0xFF003300),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant
)

@Composable
fun MiooDaoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeColor: String = "dynamic",
    fontSizeScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeColor) {
        "dynamic" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        "classic" -> {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
        "teal" -> {
            if (darkTheme) DarkTealColorScheme else LightTealColorScheme
        }
        "pink" -> {
            if (darkTheme) DarkPinkColorScheme else LightPinkColorScheme
        }
        "green" -> {
            if (darkTheme) DarkGreenColorScheme else LightGreenColorScheme
        }
        else -> {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
    }

    val customColors = if (darkTheme) {
        DaoCustomColors(
            po = DarkPoColor,
            sage = DarkSageColor,
            admin = DarkAdminColor,
            quoteLink = DarkQuoteLinkColor,
            threadCardBg = DarkThreadCardBg,
            replyCardBg = DarkReplyCardBg,
            fabBg = DarkFabColor,
            fabContent = DarkOnFabColor,
            sageTag = DarkSageTagColor,
            glassTopBar = DarkGlassTopBar,
            glassNavBar = DarkGlassNavBar
        )
    } else {
        DaoCustomColors(
            po = LightPoColor,
            sage = LightSageColor,
            admin = LightAdminColor,
            quoteLink = LightQuoteLinkColor,
            threadCardBg = LightThreadCardBg,
            replyCardBg = LightReplyCardBg,
            fabBg = LightFabColor,
            fabContent = LightOnFabColor,
            sageTag = LightSageTagColor,
            glassTopBar = LightGlassTopBar,
            glassNavBar = LightGlassNavBar
        )
    }

    val scaledTypography = Typography.scale(fontSizeScale)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDaoCustomColors provides customColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography,
            shapes = Shapes
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background)
            ) {
                // Background blurred blobs (static for peak rendering performance)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(120.dp)
                ) {
                    // Blob 1: top-left primary glow
                    Box(
                        modifier = Modifier
                            .size(400.dp)
                            .align(Alignment.TopStart)
                            .offset(x = (-60).dp, y = (-30).dp)
                            .background(
                                color = colorScheme.primary.copy(alpha = 0.25f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    // Blob 2: bottom-right secondary glow
                    Box(
                        modifier = Modifier
                            .size(450.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 60.dp, y = 70.dp)
                            .background(
                                color = colorScheme.tertiary.copy(alpha = 0.18f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    // Blob 3: center-right accent
                    Box(
                        modifier = Modifier
                            .size(250.dp)
                            .align(Alignment.CenterEnd)
                            .offset(x = 30.dp, y = 10.dp)
                            .background(
                                color = colorScheme.secondary.copy(alpha = 0.15f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                }

                // Content layer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                ) {
                    content()
                }
            }
        }
    }
}

// Convenient object to access custom color palettes inside the application
object DaoTheme {
    val colors: DaoCustomColors
        @Composable
        get() = LocalDaoCustomColors.current
}
