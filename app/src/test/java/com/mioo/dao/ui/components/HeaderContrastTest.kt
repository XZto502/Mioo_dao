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
    fun perRegionSamplingAdaptsStatusBarNavigationTitleSubtitleAndActionsIndependently() {
        // Construct a 48x16 synthetic header:
        // - Top strip (rows 0..4, status bar): bright white -> dark status bar icons
        // - Left nav (cols 0..7, rows 5..15): dark navy -> white menu/back icon
        // - Title band (cols 8..31, rows 5..10): bright sky -> dark title text
        // - Subtitle band (cols 8..31, rows 11..15): deep forest -> white subtitle text
        // - Inner-right action (cols 32..39, rows 5..15): bright white -> dark icon
        // - Far-right action (cols 40..47, rows 5..15): black -> white icon
        val w = 48
        val h = 16
        val pixels = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            when {
                y < 5 -> 0xFFF8F9FA.toInt() // bright status bar
                x < 8 -> 0xFF101828.toInt() // dark nav
                x < 32 && y <= 10 -> 0xFFEDF2F7.toInt() // bright title
                x < 32 -> 0xFF14281D.toInt() // dark subtitle
                x < 40 -> 0xFFF4F6F8.toInt() // bright mid-right action
                else -> 0xFF0B0D10.toInt() // dark rightmost action
            }
        }
        val map = HeaderLuminanceMap.fromPixels(pixels, w, h)
        val style = resolveHeaderBarStyle(
            enabled = true,
            luminanceMap = map,
            colorScheme = lightColorScheme(),
            containerWidthPx = 1080f,
            containerHeightPx = 360f,
            statusBarHeightPx = 112f,
            barWidthDp = 411f
        )

        // Top strip is bright -> dark status bar icons
        assertFalse(style.statusBarUseLight)
        assertEquals(DarkHeaderInk, style.statusBarContentColor)

        // Left nav is dark -> white nav icon
        assertTrue(style.navigationUseLight)
        assertEquals(LightHeaderInk, style.navigationContentColor)

        // Title upper band is bright -> dark title ink
        assertFalse(style.titleUseLight)
        assertEquals(DarkHeaderInk, style.titleContentColor)

        // Subtitle lower band is dark -> white subtitle ink
        assertTrue(style.subtitleUseLight)
        assertEquals(LightHeaderInk, style.subtitleContentColor)

        // Rightmost icon slot (slot 0) is dark -> white ink
        val rightmost = style.trailingActionAt(0)
        assertTrue(rightmost.useLightContent)
        assertEquals(LightHeaderInk, rightmost.contentColor)

        // Second-from-right icon slot (slot 2, ~cols 32..37) is bright -> dark ink
        val innerRight = style.trailingActionAt(2)
        assertFalse(innerRight.useLightContent)
        assertEquals(DarkHeaderInk, innerRight.contentColor)
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
