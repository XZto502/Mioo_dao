package com.mioo.dao.ui.components

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderContrastTest {

    @Test
    fun luminanceValuesMatchWcgExtremesAndPerceptualWeights() {
        assertEquals(0f, argbRelativeLuminance(0xFF000000.toInt()), 0.0001f)
        assertEquals(1f, argbRelativeLuminance(0xFFFFFFFF.toInt()), 0.0001f)
        assertTrue(argbRelativeLuminance(0xFF00FF00.toInt()) > argbRelativeLuminance(0xFF0000FF.toInt()))
        assertTrue(useLightForegroundForLuminance(argbRelativeLuminance(0xFF202020.toInt())))
        assertFalse(useLightForegroundForLuminance(argbRelativeLuminance(0xFFEAEAEA.toInt())))
    }

    @Test
    fun middleLuminanceChoosesTheMoreLegibleForeground() {
        assertTrue(useLightForegroundForLuminance(0.15f))
        assertFalse(useLightForegroundForLuminance(0.31f))
    }

    @Test
    fun darkMajorityToolbarUsesSingleWhiteColorDespiteBrightHighlight() {
        // 48x16 header:
        // - Top strip (rows 0..4, status bar): bright white -> dark status bar icons
        // - Toolbar (rows 5..15): 65% dark gray + 35% pure white (mean lum > 0.35, but dark area > 50%)
        val w = 48
        val h = 16
        val pixels = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            when {
                y < 5 -> 0xFFF8F9FA.toInt()
                x < 31 -> 0xFF262626.toInt() // ~65% dark area on left/title/subtitle
                else -> 0xFFFFFFFF.toInt()   // ~35% pure white over trailing actions
            }
        }
        val map = HeaderLuminanceMap.fromPixels(pixels, w, h)
        val style = resolveHeaderBarStyle(
            enabled = true,
            luminanceMap = map,
            colorScheme = lightColorScheme(),
            containerWidthPx = 1080f,
            containerHeightPx = 360f,
            statusBarHeightPx = 112f
        )

        // Status bar remains independent: bright strip -> dark icons
        assertFalse(style.statusBarUseLight)
        assertEquals(DarkHeaderInk, style.statusBarContentColor)
        assertFalse(
            CustomHeaderData(luminanceMap = map).useLightStatusBarIcons(
                containerWidthPx = 1080f,
                containerHeightPx = 360f,
                statusBarHeightPx = 112f
            )
        )

        // All toolbar elements share unified white ink despite white right-side patch
        assertTrue(style.navigationUseLight)
        assertEquals(LightHeaderInk, style.navigationContentColor)
        assertTrue(style.titleUseLight)
        assertEquals(LightHeaderInk, style.titleContentColor)
        assertTrue(style.subtitleUseLight)
        assertEquals(LightHeaderInk, style.subtitleContentColor)
        assertTrue(style.trailingActionColors.useLightContent)
        assertEquals(LightHeaderInk, style.trailingActionColors.contentColor)
        assertEquals(LightHeaderInk, style.trailingActionColors.activeAccentColor)

        for (slot in 0..4) {
            val action = style.trailingActionAt(slot)
            assertTrue(action.useLightContent)
            assertEquals(LightHeaderInk, action.contentColor)
            assertEquals(LightHeaderInk, action.activeAccentColor)
        }
    }

    @Test
    fun lightMajorityToolbarUsesSingleBlackColorDespiteDarkPatch() {
        // 48x16 header:
        // - Top strip (rows 0..4, status bar): dark navy -> white status bar icons
        // - Toolbar (rows 5..15): 70% bright + 30% pure black
        val w = 48
        val h = 16
        val pixels = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            when {
                y < 5 -> 0xFF101828.toInt()
                x < 14 -> 0xFF000000.toInt() // ~29% black on left
                else -> 0xFFF4F6F8.toInt()   // ~71% bright on title & actions
            }
        }
        val map = HeaderLuminanceMap.fromPixels(pixels, w, h)
        val style = resolveHeaderBarStyle(
            enabled = true,
            luminanceMap = map,
            colorScheme = darkColorScheme(),
            containerWidthPx = 1080f,
            containerHeightPx = 360f,
            statusBarHeightPx = 112f
        )

        // Status bar remains independent: dark strip -> white icons
        assertTrue(style.statusBarUseLight)
        assertEquals(LightHeaderInk, style.statusBarContentColor)
        assertTrue(
            CustomHeaderData(luminanceMap = map).useLightStatusBarIcons(
                containerWidthPx = 1080f,
                containerHeightPx = 360f,
                statusBarHeightPx = 112f
            )
        )

        // All toolbar elements share unified dark ink
        assertFalse(style.navigationUseLight)
        assertEquals(DarkHeaderInk, style.navigationContentColor)
        assertFalse(style.titleUseLight)
        assertEquals(DarkHeaderInk, style.titleContentColor)
        assertFalse(style.subtitleUseLight)
        assertEquals(DarkHeaderInk, style.subtitleContentColor)
        assertFalse(style.trailingActionColors.useLightContent)
        assertEquals(DarkHeaderInk, style.trailingActionColors.contentColor)
        assertEquals(DarkHeaderInk, style.trailingActionColors.activeAccentColor)

        for (slot in 0..4) {
            val action = style.trailingActionAt(slot)
            assertFalse(action.useLightContent)
            assertEquals(DarkHeaderInk, action.contentColor)
            assertEquals(DarkHeaderInk, action.activeAccentColor)
        }
    }

    @Test
    fun exactAreaTieBreaksDeterministicallyByMeanLuminance() {
        val w = 48
        val h = 16
        // 50% dark (0xFF202020) + 50% pure white (0xFFFFFFFF) -> high mean lum -> dark ink
        val brightTiePixels = IntArray(w * h) { idx ->
            if ((idx % w) < w / 2) 0xFF202020.toInt() else 0xFFFFFFFF.toInt()
        }
        val brightTieStyle = resolveHeaderBarStyle(
            enabled = true,
            luminanceMap = HeaderLuminanceMap.fromPixels(brightTiePixels, w, h),
            colorScheme = lightColorScheme()
        )
        assertFalse(brightTieStyle.titleUseLight)
        assertEquals(DarkHeaderInk, brightTieStyle.titleContentColor)

        // 50% pure black (0xFF000000) + 50% mid-light gray (0xFF909090) -> low mean lum -> white ink
        val darkTiePixels = IntArray(w * h) { idx ->
            if ((idx % w) < w / 2) 0xFF000000.toInt() else 0xFF909090.toInt()
        }
        val darkTieStyle = resolveHeaderBarStyle(
            enabled = true,
            luminanceMap = HeaderLuminanceMap.fromPixels(darkTiePixels, w, h),
            colorScheme = lightColorScheme()
        )
        assertTrue(darkTieStyle.titleUseLight)
        assertEquals(LightHeaderInk, darkTieStyle.titleContentColor)
    }

    @Test
    fun centerCropVisibleRectExcludesClippedOuterMarginsFromAreaDecision() {
        val w = 48
        val h = 16
        // Outer 50% (left 25% + right 25%) is bright white; center 50% is dark.
        // Rendered in a 500x500 container for a 1000x500 source, only cols 12..35 are visible.
        val pixels = IntArray(w * h) { idx ->
            val x = idx % w
            if (x in 12..35) 0xFF181818.toInt() else 0xFFFFFFFF.toInt()
        }
        val map = HeaderLuminanceMap.fromPixels(pixels, w, h, sourceWidth = 1000, sourceHeight = 500)
        val style = resolveHeaderBarStyle(
            enabled = true,
            luminanceMap = map,
            colorScheme = lightColorScheme(),
            containerWidthPx = 500f,
            containerHeightPx = 500f,
            statusBarHeightPx = 150f
        )

        assertTrue(style.statusBarUseLight)
        assertTrue(style.titleUseLight)
        assertEquals(LightHeaderInk, style.titleContentColor)
        assertEquals(LightHeaderInk, style.trailingActionAt(0).contentColor)
    }

    @Test
    fun disabledHeaderPreservesThemeColorSchemeUntouched() {
        val lightScheme = lightColorScheme()
        val disabledLight = resolveHeaderBarStyle(
            enabled = false,
            luminanceMap = null,
            colorScheme = lightScheme
        )
        assertFalse(disabledLight.enabled)
        assertEquals(lightScheme.onSurface, disabledLight.titleContentColor)
        assertEquals(lightScheme.onSurfaceVariant, disabledLight.subtitleContentColor)
        assertEquals(lightScheme.onSurface, disabledLight.navigationContentColor)
        assertEquals(lightScheme.onSurface, disabledLight.trailingActionColors.contentColor)

        val darkScheme = darkColorScheme()
        val disabledDark = resolveHeaderBarStyle(
            enabled = false,
            luminanceMap = null,
            colorScheme = darkScheme
        )
        assertFalse(disabledDark.enabled)
        assertEquals(darkScheme.onSurface, disabledDark.titleContentColor)
    }

    @Test
    fun centerCropVisibleRectMapsWiderOrTallerContainersAccurately() {
        // 3.8:1 bitmap rendered inside a 3.8:1 container shows 100% of bitmap
        val exact = computeCenterCropVisibleRect(1440, 379, 1080f, 284.21f)
        assertEquals(0f, exact.left, 0.01f)
        assertEquals(1f, exact.right, 0.01f)
        assertEquals(0f, exact.top, 0.01f)
        assertEquals(1f, exact.bottom, 0.01f)

        // Taller container crops horizontally (left/right clipped equally)
        val taller = computeCenterCropVisibleRect(1000, 500, 500f, 500f)
        assertEquals(0.25f, taller.left, 0.01f)
        assertEquals(0.75f, taller.right, 0.01f)
        assertEquals(0f, taller.top, 0.01f)
        assertEquals(1f, taller.bottom, 0.01f)
    }

    @Test
    fun statusBarPolarityUsesStatusStripAndRespectsRouteDrawerAndOverlayScope() {
        // Light theme + dark status strip on Forum tab -> light (white) status icons
        assertFalse(
            resolveEffectiveLightStatusBars(
                darkTheme = false,
                isHomeRoute = true,
                isThreadRoute = false,
                currentTabIndex = 0,
                isDrawerClosed = true,
                isOverlayOpen = false,
                hasCustomHeaderImage = true,
                applyHeaderToThread = false,
                useLightStatusBarIcons = true
            )
        )
        // Dark theme + bright status strip on Forum tab -> dark status icons
        assertTrue(
            resolveEffectiveLightStatusBars(
                darkTheme = true,
                isHomeRoute = true,
                isThreadRoute = false,
                currentTabIndex = 0,
                isDrawerClosed = true,
                isOverlayOpen = false,
                hasCustomHeaderImage = true,
                applyHeaderToThread = false,
                useLightStatusBarIcons = false
            )
        )
        // Switching away from tab 0 falls back to theme polarity
        assertTrue(
            resolveEffectiveLightStatusBars(
                darkTheme = false,
                isHomeRoute = true,
                isThreadRoute = false,
                currentTabIndex = 1,
                isDrawerClosed = true,
                isOverlayOpen = false,
                hasCustomHeaderImage = true,
                applyHeaderToThread = false,
                useLightStatusBarIcons = true
            )
        )
        // Opening drawer falls back to theme polarity
        assertTrue(
            resolveEffectiveLightStatusBars(
                darkTheme = false,
                isHomeRoute = true,
                isThreadRoute = false,
                currentTabIndex = 0,
                isDrawerClosed = false,
                isOverlayOpen = false,
                hasCustomHeaderImage = true,
                applyHeaderToThread = false,
                useLightStatusBarIcons = true
            )
        )
        // Thread route applies header polarity only when applyHeaderToThread is enabled
        assertTrue(
            resolveEffectiveLightStatusBars(
                darkTheme = false,
                isHomeRoute = false,
                isThreadRoute = true,
                currentTabIndex = 0,
                isDrawerClosed = true,
                isOverlayOpen = false,
                hasCustomHeaderImage = true,
                applyHeaderToThread = false,
                useLightStatusBarIcons = true
            )
        )
        assertFalse(
            resolveEffectiveLightStatusBars(
                darkTheme = false,
                isHomeRoute = false,
                isThreadRoute = true,
                currentTabIndex = 0,
                isDrawerClosed = true,
                isOverlayOpen = false,
                hasCustomHeaderImage = true,
                applyHeaderToThread = true,
                useLightStatusBarIcons = true
            )
        )
    }
}
