package com.mioo.dao.ui.theme

import android.app.Activity
import androidx.compose.ui.graphics.Brush
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.mioo.dao.ui.components.LocalThreadCommentPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.mioo.dao.ui.theme.isActivitySystemBarsOverridden
import kotlinx.coroutines.delay


private object NoRippleTheme : RippleTheme {
    @Composable override fun defaultColor(): Color = Color.Transparent
    @Composable override fun rippleAlpha() = RippleAlpha(0f, 0f, 0f, 0f)
}

// Fixed palettes keep independent primary, secondary and tertiary tonal families.
private fun ColorScheme.withTonalSurfaces(dark: Boolean): ColorScheme = copy(
    onSecondary = if (dark) background else Color.White,
    onTertiary = if (dark) background else Color.White,
    onTertiaryContainer = if (dark) tertiary else Color(0xFF1C2024),
    onBackground = if (dark) Color(0xFFE5E8E6) else Color(0xFF1C2024),
    onSurface = if (dark) Color(0xFFE5E8E6) else Color(0xFF1C2024),
    onSurfaceVariant = if (dark) Color(0xFFC2C7C4) else Color(0xFF444A47),
    outline = if (dark) Color(0xFF8C928F) else Color(0xFF747A77),
    outlineVariant = if (dark) androidx.compose.ui.graphics.lerp(surfaceVariant, Color.White, 0.16f) else outlineVariant,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = if (dark) background else Color.White,
    surfaceContainerLow = if (dark) surface else Color(0xFFFCFCFD),
    surfaceContainer = androidx.compose.ui.graphics.lerp(surface, surfaceVariant, 0.4f),
    surfaceContainerHigh = androidx.compose.ui.graphics.lerp(surface, surfaceVariant, 0.7f),
    surfaceContainerHighest = surfaceVariant,
    surfaceBright = if (dark) androidx.compose.ui.graphics.lerp(surfaceVariant, Color.White, 0.05f) else Color.White,
    surfaceDim = if (dark) background else surfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFF625B71),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Color(0xFF7E5260),
    tertiaryContainer = Color(0xFFFFD9E3),
    background = Color(0xFFF8F7FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0EEF4),
    outlineVariant = Color(0xFFE2DFE8)
).withTonalSurfaces(false)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378A),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    tertiaryContainer = Color(0xFF633B48),
    background = Color(0xFF131217),
    surface = Color(0xFF1B1920),
    surfaceVariant = Color(0xFF26232D)
).withTonalSurfaces(true)

private val LightTealColorScheme = lightColorScheme(
    primary = Color(0xFF00677D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC5ECFA),
    onPrimaryContainer = Color(0xFF003543),
    secondary = Color(0xFF4B626B),
    secondaryContainer = Color(0xFFD6E5EC),
    onSecondaryContainer = Color(0xFF11262E),
    tertiary = Color(0xFF565D7E),
    tertiaryContainer = Color(0xFFDDE1FF),
    background = Color(0xFFF5F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFECF1F4),
    outlineVariant = Color(0xFFDCE3E7)
).withTonalSurfaces(false)

private val DarkTealColorScheme = darkColorScheme(
    primary = Color(0xFF7CD2EA),
    onPrimary = Color(0xFF003643),
    primaryContainer = Color(0xFF004E5F),
    onPrimaryContainer = Color(0xFFC2ECFA),
    secondary = Color(0xFFB3CAD4),
    secondaryContainer = Color(0xFF334952),
    onSecondaryContainer = Color(0xFFCFE6F0),
    tertiary = Color(0xFFBFC5EB),
    tertiaryContainer = Color(0xFF3E4565),
    background = Color(0xFF101416),
    surface = Color(0xFF171C1F),
    surfaceVariant = Color(0xFF22282C)
).withTonalSurfaces(true)

private val LightPinkColorScheme = lightColorScheme(
    primary = Color(0xFFA84467),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCE5),
    onPrimaryContainer = Color(0xFF481126),
    secondary = Color(0xFF72575E),
    secondaryContainer = Color(0xFFF1DFE4),
    onSecondaryContainer = Color(0xFF2A161C),
    tertiary = Color(0xFF7A5733),
    tertiaryContainer = Color(0xFFFFDCC0),
    background = Color(0xFFFAF6F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF3ECEE),
    outlineVariant = Color(0xFFE6DCE0)
).withTonalSurfaces(false)

private val DarkPinkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB1C7),
    onPrimary = Color(0xFF641132),
    primaryContainer = Color(0xFF7A2946),
    onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFFE2BDC6),
    secondaryContainer = Color(0xFF573E45),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFE8BE96),
    tertiaryContainer = Color(0xFF5E3F1E),
    background = Color(0xFF161214),
    surface = Color(0xFF1E191B),
    surfaceVariant = Color(0xFF2A2225)
).withTonalSurfaces(true)

private val LightGreenColorScheme = lightColorScheme(
    primary = Color(0xFF2B6A46),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4F0DC),
    onPrimaryContainer = Color(0xFF0D3B22),
    secondary = Color(0xFF4E6354),
    secondaryContainer = Color(0xFFDCE8DF),
    onSecondaryContainer = Color(0xFF192B1F),
    tertiary = Color(0xFF3B6470),
    tertiaryContainer = Color(0xFFC2EAF7),
    background = Color(0xFFF6F8F6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF2EE),
    outlineVariant = Color(0xFFDEE4DF)
).withTonalSurfaces(false)

private val DarkGreenColorScheme = darkColorScheme(
    primary = Color(0xFF8ED5A8),
    onPrimary = Color(0xFF00391D),
    primaryContainer = Color(0xFF184F30),
    onPrimaryContainer = Color(0xFFC5F2D3),
    secondary = Color(0xFFB6CCB9),
    secondaryContainer = Color(0xFF35493C),
    onSecondaryContainer = Color(0xFFD2E8D5),
    tertiary = Color(0xFFA3CDD9),
    tertiaryContainer = Color(0xFF214C58),
    background = Color(0xFF111411),
    surface = Color(0xFF181C18),
    surfaceVariant = Color(0xFF232923)
).withTonalSurfaces(true)

// AMOLED 纯黑：深色下背景/表面纯黑省电，其余颜色跟随动态取色；
// 浅色模式退回经典色板（选择入口也仅在深色模式下出现）


@Composable
fun MiooDaoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeColor: String = "dynamic",
    fontSizeScale: Float = 1.0f,
    lineHeightScale: Float = 1.0f,
    chineseFont: String = "",
    englishFont: String = "",
    dataSaverEnabled: Boolean = false,
    glassEffectEnabled: Boolean = true,
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
        "amoled" -> {
            if (darkTheme) {
                // 纯黑：动态取色为基底（低版本回退经典深色），仅把背景/表面压成纯黑
                val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dynamicDarkColorScheme(LocalContext.current)
                } else {
                    DarkColorScheme
                }
                base.copy(
                    background = Color(0xFF000000),
                    surface = Color(0xFF000000),
                    surfaceVariant = Color(0xFF1A1A1E)
                ).copy(surfaceTint = Color.Transparent)
            } else {
                LightColorScheme
            }
        }
        else -> {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
    }

    // Solid surface when glass is off so bars/cards fully cover the background.
    val solidSurface = if (darkTheme) colorScheme.background else Color(0xFFF5F6F8)
    val solidCard = colorScheme.surfaceContainerHigh
    // Modifier.blur cannot frost the list behind a bar, and it does nothing below API 31.
    // On Android 8–11, keep glass fills at least 95% opaque so text stays readable.
    val legacyNoBlur = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    fun glassFill(enabledColor: Color, solid: Color): Color {
        if (!glassEffectEnabled) return solid
        if (!legacyNoBlur) return enabledColor
        return enabledColor.copy(alpha = maxOf(enabledColor.alpha, 0.95f))
    }

    val amoledDark = darkTheme && themeColor == "amoled"
    val customColors = if (darkTheme) {
        // AMOLED 深色下卡片/玻璃用近黑底
        DaoCustomColors(
            sage = DarkSageColor,
            admin = DarkAdminColor,
            sageTag = DarkSageTagColor,
            glassTopBar = solidSurface,
            glassNavBar = solidSurface
        )
    } else {
        DaoCustomColors(
            sage = LightSageColor,
            admin = LightAdminColor,
            sageTag = LightSageTagColor,
            glassTopBar = solidSurface,
            glassNavBar = solidSurface
        )
    }

    val appContext = LocalContext.current
    val customTypeface = remember(chineseFont, englishFont) {
        com.mioo.dao.utils.FontStore.buildTypeface(appContext, chineseFont.ifBlank { englishFont }, "")
    }
    val customFontFamily = customTypeface?.let { FontFamily(typeface = it) }
    val scaledTypography = Typography.scale(fontSizeScale)
        .scaleLineHeight(lineHeightScale)
        .withFontFamily(customFontFamily)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            if (isActivitySystemBarsOverridden) return@SideEffect
            val window = (view.context as Activity).window
            val transparent = Color.Transparent.toArgb()
            window.statusBarColor = transparent
            window.navigationBarColor = transparent
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = transparent
            }
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = activeLightStatusBarsOverride ?: !darkTheme
                // Light icons on dark theme so the gesture pill stays visible on glow/content.
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val commentIconPainter = rememberVectorPainter(Icons.AutoMirrored.Outlined.Comment)
    CompositionLocalProvider(
        LocalDaoCustomColors provides customColors,
        LocalGlassEffectEnabled provides glassEffectEnabled,
        LocalDataSaverEnabled provides dataSaverEnabled,
        LocalThreadCommentPainter provides commentIconPainter
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography,
            shapes = Shapes
        ) {
            val rootModifier = Modifier.fillMaxSize().background(colorScheme.background)

            Box(modifier = rootModifier) {
                content()
            }
        }
    }
}

// Convenient object to access custom color palettes inside the application
object DaoTheme {
    val colors: DaoCustomColors
        @Composable
        get() = LocalDaoCustomColors.current

    val glassEffectEnabled: Boolean
        @Composable
        get() = LocalGlassEffectEnabled.current
}
