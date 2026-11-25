package com.mioo.dao.ui.components

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import com.mioo.dao.data.local.HeaderImageSettings
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private const val SAMPLE_WIDTH = 48
private const val SAMPLE_HEIGHT = 16
private const val CROP_PREVIEW_SAMPLE_COLS = 32
private const val CROP_PREVIEW_SAMPLE_ROWS = 12
private const val DEFAULT_STATUS_SPLIT_FRACTION = 0.34f

internal val LightHeaderInk = Color.White
internal val DarkHeaderInk = Color(0xFF1A1C1E)
private val DarkHeaderInkLuminance = DarkHeaderInk.luminance()

private fun contrastRatio(first: Float, second: Float): Float =
    (max(first, second) + 0.05f) / (min(first, second) + 0.05f)

internal fun srgbChannelToLinear(channel: Float): Float {
    val c = channel.coerceIn(0f, 1f)
    return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
}

internal fun argbRelativeLuminance(argb: Int): Float {
    val r = srgbChannelToLinear(((argb ushr 16) and 0xFF) / 255f)
    val g = srgbChannelToLinear(((argb ushr 8) and 0xFF) / 255f)
    val b = srgbChannelToLinear((argb and 0xFF) / 255f)
    return (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)
}

/**
 * Normalized sub-rectangle of the source bitmap that is actually visible after center-cropping
 * into a container with dimensions [containerWidth] x [containerHeight].
 */
@Immutable
data class NormalizedRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

internal fun computeCenterCropVisibleRect(
    bitmapWidth: Int,
    bitmapHeight: Int,
    containerWidth: Float,
    containerHeight: Float
): NormalizedRect {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || containerWidth <= 0f || containerHeight <= 0f) {
        return NormalizedRect()
    }
    val iw = bitmapWidth.toFloat()
    val ih = bitmapHeight.toFloat()
    val scale = max(containerWidth / iw, containerHeight / ih)
    val visibleW = (containerWidth / scale / iw).coerceIn(0f, 1f)
    val visibleH = (containerHeight / scale / ih).coerceIn(0f, 1f)
    val left = ((1f - visibleW) * 0.5f).coerceIn(0f, 1f)
    val top = ((1f - visibleH) * 0.5f).coerceIn(0f, 1f)
    return NormalizedRect(
        left = left,
        top = top,
        right = (left + visibleW).coerceIn(left, 1f),
        bottom = (top + visibleH).coerceIn(top, 1f)
    )
}

internal data class RegionToneStats(
    val darkCount: Int,
    val totalCount: Int,
    val luminanceSum: Double
) {
    val lightCount: Int get() = (totalCount - darkCount).coerceAtLeast(0)
    val meanLuminance: Float
        get() = if (totalCount > 0) (luminanceSum / totalCount).toFloat().coerceIn(0f, 1f) else 0f

    fun useLightForeground(fallbackLightContent: Boolean = true): Boolean {
        if (totalCount <= 0) return fallbackLightContent
        return when {
            darkCount > lightCount -> true
            darkCount < lightCount -> false
            else -> useLightForegroundForLuminance(meanLuminance, fallbackLightContent)
        }
    }
}

/**
 * Compact per-cell WCAG relative luminance grid for a custom header image.
 * Enables O(1) area-based dark/light classification for the status bar and toolbar regions.
 */
@Immutable
class HeaderLuminanceMap(
    val width: Int,
    val height: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    private val grid: FloatArray
) {
    internal fun analyzeRegionTone(
        leftFrac: Float,
        topFrac: Float,
        rightFrac: Float,
        bottomFrac: Float,
        visibleRect: NormalizedRect = NormalizedRect()
    ): RegionToneStats? {
        if (width <= 0 || height <= 0 || grid.isEmpty()) return null
        val x0 = (visibleRect.left + visibleRect.width * leftFrac.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        val x1 = (visibleRect.left + visibleRect.width * rightFrac.coerceIn(0f, 1f)).coerceIn(x0, 1f)
        val y0 = (visibleRect.top + visibleRect.height * topFrac.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        val y1 = (visibleRect.top + visibleRect.height * bottomFrac.coerceIn(0f, 1f)).coerceIn(y0, 1f)

        val colStart = floor(x0 * width).toInt().coerceIn(0, width - 1)
        val colEnd = (ceil(x1 * width).toInt() - 1).coerceIn(colStart, width - 1)
        val rowStart = floor(y0 * height).toInt().coerceIn(0, height - 1)
        val rowEnd = (ceil(y1 * height).toInt() - 1).coerceIn(rowStart, height - 1)

        var darkCount = 0
        var totalCount = 0
        var luminanceSum = 0.0
        for (row in rowStart..rowEnd) {
            val rowOffset = row * width
            for (col in colStart..colEnd) {
                val idx = rowOffset + col
                if (idx in grid.indices) {
                    val lum = grid[idx]
                    if (useLightForegroundForLuminance(lum)) {
                        darkCount++
                    }
                    luminanceSum += lum
                    totalCount++
                }
            }
        }
        return if (totalCount > 0) RegionToneStats(darkCount, totalCount, luminanceSum) else null
    }

    /**
     * Evaluates the status-bar strip by dark vs. light area, weighting the left clock and
     * right system status icons higher than the center cutout area.
     */
    fun statusBarUseLightForeground(
        statusSplitY: Float = DEFAULT_STATUS_SPLIT_FRACTION,
        visibleRect: NormalizedRect = NormalizedRect(),
        fallbackLightContent: Boolean = true
    ): Boolean {
        val split = statusSplitY.coerceIn(0.18f, 0.50f)
        val leftStats = analyzeRegionTone(0.02f, 0f, 0.28f, split, visibleRect)
        val centerStats = analyzeRegionTone(0.28f, 0f, 0.70f, split, visibleRect)
        val rightStats = analyzeRegionTone(0.70f, 0f, 0.98f, split, visibleRect)

        var weightedDark = 0.0
        var weightedLight = 0.0
        var weightedLum = 0.0
        var totalWeight = 0.0

        fun accumulate(stats: RegionToneStats?, weight: Double) {
            if (stats == null || stats.totalCount <= 0) return
            val darkFraction = stats.darkCount.toDouble() / stats.totalCount
            weightedDark += darkFraction * weight
            weightedLight += (1.0 - darkFraction) * weight
            weightedLum += stats.meanLuminance * weight
            totalWeight += weight
        }

        accumulate(leftStats, 1.5)
        accumulate(centerStats, 0.6)
        accumulate(rightStats, 1.5)

        if (totalWeight <= 0.0) return fallbackLightContent
        return when {
            weightedDark > weightedLight + 1e-6 -> true
            weightedLight > weightedDark + 1e-6 -> false
            else -> useLightForegroundForLuminance((weightedLum / totalWeight).toFloat(), fallbackLightContent)
        }
    }

    companion object {
        fun fromPixels(
            pixels: IntArray,
            width: Int,
            height: Int,
            sourceWidth: Int = width,
            sourceHeight: Int = height
        ): HeaderLuminanceMap {
            if (width <= 0 || height <= 0 || pixels.isEmpty()) {
                return HeaderLuminanceMap(0, 0, sourceWidth, sourceHeight, FloatArray(0))
            }
            val size = min(pixels.size, width * height)
            val grid = FloatArray(size) { i -> argbRelativeLuminance(pixels[i]) }
            return HeaderLuminanceMap(width, height, sourceWidth, sourceHeight, grid)
        }
    }
}

internal fun computeBitmapHeaderLuminanceMap(bitmap: Bitmap): HeaderLuminanceMap? {
    if (bitmap.width <= 0 || bitmap.height <= 0) return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
        return null
    }
    val sampleW = min(SAMPLE_WIDTH, bitmap.width).coerceAtLeast(1)
    val sampleH = min(SAMPLE_HEIGHT, bitmap.height).coerceAtLeast(1)
    val sampled = if (sampleW == bitmap.width && sampleH == bitmap.height) {
        bitmap
    } else {
        Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)
    }
    return try {
        val pixels = IntArray(sampleW * sampleH)
        sampled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        HeaderLuminanceMap.fromPixels(
            pixels = pixels,
            width = sampleW,
            height = sampleH,
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height
        )
    } finally {
        if (sampled !== bitmap) sampled.recycle()
    }
}

/**
 * Fast allocation-light crop preview luminance sampler (reads a 32x12 point grid directly).
 */
internal fun computeCropRegionLuminanceMap(
    bitmap: Bitmap,
    left: Int,
    top: Int,
    cropWidth: Int,
    cropHeight: Int
): HeaderLuminanceMap? {
    if (bitmap.width <= 0 || bitmap.height <= 0 || cropWidth <= 0 || cropHeight <= 0) return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
        return null
    }
    val cols = min(CROP_PREVIEW_SAMPLE_COLS, cropWidth).coerceAtLeast(1)
    val rows = min(CROP_PREVIEW_SAMPLE_ROWS, cropHeight).coerceAtLeast(1)
    val pixels = IntArray(cols * rows)
    val maxX = bitmap.width - 1
    val maxY = bitmap.height - 1
    for (row in 0 until rows) {
        val py = (top + ((row + 0.5f) / rows) * cropHeight).roundToInt().coerceIn(0, maxY)
        val rowOffset = row * cols
        for (col in 0 until cols) {
            val px = (left + ((col + 0.5f) / cols) * cropWidth).roundToInt().coerceIn(0, maxX)
            pixels[rowOffset + col] = bitmap.getPixel(px, py)
        }
    }
    return HeaderLuminanceMap.fromPixels(
        pixels = pixels,
        width = cols,
        height = rows,
        sourceWidth = cropWidth,
        sourceHeight = cropHeight
    )
}

internal fun useLightForegroundForLuminance(
    luminance: Float?,
    fallbackLightContent: Boolean = true
): Boolean = luminance?.let {
    contrastRatio(1f, it) >= contrastRatio(DarkHeaderInkLuminance, it)
} ?: fallbackLightContent

@Immutable
data class HeaderActionColors(
    val useLightContent: Boolean,
    val contentColor: Color,
    val secondaryContentColor: Color,
    val disabledContentColor: Color,
    val activeAccentColor: Color,
    val activeBadgeContainerColor: Color,
    val activeBadgeContentColor: Color
)

private fun resolveActionColorsForTone(
    enabled: Boolean,
    useLightContent: Boolean,
    colorScheme: ColorScheme
): HeaderActionColors {
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    if (!enabled) {
        return HeaderActionColors(
            useLightContent = isDarkTheme,
            contentColor = colorScheme.onSurface,
            secondaryContentColor = colorScheme.onSurfaceVariant,
            disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f),
            activeAccentColor = colorScheme.primary,
            activeBadgeContainerColor = colorScheme.primary,
            activeBadgeContentColor = colorScheme.onPrimary
        )
    }
    return if (useLightContent) {
        HeaderActionColors(
            useLightContent = true,
            contentColor = LightHeaderInk,
            secondaryContentColor = LightHeaderInk,
            disabledContentColor = LightHeaderInk.copy(alpha = 0.45f),
            activeAccentColor = LightHeaderInk,
            activeBadgeContainerColor = LightHeaderInk.copy(alpha = 0.94f),
            activeBadgeContentColor = DarkHeaderInk
        )
    } else {
        HeaderActionColors(
            useLightContent = false,
            contentColor = DarkHeaderInk,
            secondaryContentColor = DarkHeaderInk,
            disabledContentColor = DarkHeaderInk.copy(alpha = 0.40f),
            activeAccentColor = DarkHeaderInk,
            activeBadgeContainerColor = DarkHeaderInk.copy(alpha = 0.90f),
            activeBadgeContentColor = LightHeaderInk
        )
    }
}

@Immutable
data class HeaderBarStyle(
    val enabled: Boolean,
    val statusBarUseLight: Boolean,
    val statusBarContentColor: Color,
    val navigationUseLight: Boolean,
    val navigationContentColor: Color,
    val navigationDisabledColor: Color,
    val titleUseLight: Boolean,
    val titleContentColor: Color,
    val subtitleUseLight: Boolean,
    val subtitleContentColor: Color,
    val trailingActionColors: HeaderActionColors
) {
    /**
     * Returns the unified toolbar action colors for any trailing action slot.
     */
    fun trailingActionAt(@Suppress("UNUSED_PARAMETER") indexFromEnd: Int): HeaderActionColors =
        trailingActionColors
}

internal fun resolveHeaderBarStyle(
    enabled: Boolean,
    luminanceMap: HeaderLuminanceMap?,
    colorScheme: ColorScheme,
    containerWidthPx: Float = 0f,
    containerHeightPx: Float = 0f,
    statusBarHeightPx: Float = 0f
): HeaderBarStyle {
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    if (!enabled) {
        val defaultActions = resolveActionColorsForTone(
            enabled = false,
            useLightContent = isDarkTheme,
            colorScheme = colorScheme
        )
        return HeaderBarStyle(
            enabled = false,
            statusBarUseLight = isDarkTheme,
            statusBarContentColor = colorScheme.onSurface,
            navigationUseLight = isDarkTheme,
            navigationContentColor = colorScheme.onSurface,
            navigationDisabledColor = colorScheme.onSurface.copy(alpha = 0.38f),
            titleUseLight = isDarkTheme,
            titleContentColor = colorScheme.onSurface,
            subtitleUseLight = isDarkTheme,
            subtitleContentColor = colorScheme.onSurfaceVariant,
            trailingActionColors = defaultActions
        )
    }

    val visibleRect = if (luminanceMap != null && containerWidthPx > 0f && containerHeightPx > 0f) {
        computeCenterCropVisibleRect(
            bitmapWidth = luminanceMap.sourceWidth,
            bitmapHeight = luminanceMap.sourceHeight,
            containerWidth = containerWidthPx,
            containerHeight = containerHeightPx
        )
    } else {
        NormalizedRect()
    }
    val statusSplitY = if (containerHeightPx > 0f && statusBarHeightPx > 0f) {
        (statusBarHeightPx / containerHeightPx).coerceIn(0.20f, 0.50f)
    } else {
        DEFAULT_STATUS_SPLIT_FRACTION
    }

    val statusUseLight = luminanceMap?.statusBarUseLightForeground(statusSplitY, visibleRect) ?: true
    val toolbarUseLight = luminanceMap
        ?.analyzeRegionTone(0f, statusSplitY, 1f, 1f, visibleRect)
        ?.useLightForeground()
        ?: true

    val toolbarColors = resolveActionColorsForTone(
        enabled = true,
        useLightContent = toolbarUseLight,
        colorScheme = colorScheme
    )

    return HeaderBarStyle(
        enabled = true,
        statusBarUseLight = statusUseLight,
        statusBarContentColor = if (statusUseLight) LightHeaderInk else DarkHeaderInk,
        navigationUseLight = toolbarUseLight,
        navigationContentColor = toolbarColors.contentColor,
        navigationDisabledColor = toolbarColors.disabledContentColor,
        titleUseLight = toolbarUseLight,
        titleContentColor = toolbarColors.contentColor,
        subtitleUseLight = toolbarUseLight,
        subtitleContentColor = toolbarColors.secondaryContentColor,
        trailingActionColors = toolbarColors
    )
}

@Immutable
data class CustomHeaderData(
    val bitmap: ImageBitmap? = null,
    val settings: HeaderImageSettings = HeaderImageSettings(),
    val luminanceMap: HeaderLuminanceMap? = null
) {
    val enabled: Boolean get() = bitmap != null

    /**
     * Whether system status-bar icons over the top strip of the custom header should be light (white),
     * optionally aligned to the measured top-bar container and status-bar inset.
     */
    fun useLightStatusBarIcons(
        containerWidthPx: Float = 0f,
        containerHeightPx: Float = 0f,
        statusBarHeightPx: Float = 0f
    ): Boolean {
        val map = luminanceMap ?: return true
        val visibleRect = if (containerWidthPx > 0f && containerHeightPx > 0f) {
            computeCenterCropVisibleRect(
                bitmapWidth = map.sourceWidth,
                bitmapHeight = map.sourceHeight,
                containerWidth = containerWidthPx,
                containerHeight = containerHeightPx
            )
        } else {
            NormalizedRect()
        }
        val statusSplitY = if (containerHeightPx > 0f && statusBarHeightPx > 0f) {
            (statusBarHeightPx / containerHeightPx).coerceIn(0.20f, 0.50f)
        } else {
            DEFAULT_STATUS_SPLIT_FRACTION
        }
        return map.statusBarUseLightForeground(statusSplitY, visibleRect)
    }
}

/**
 * Pure decision function for Activity status-bar icon polarity.
 * Returns `true` for dark status-bar icons (light bar background) and `false` for light icons.
 */
internal fun resolveEffectiveLightStatusBars(
    darkTheme: Boolean,
    isHomeRoute: Boolean,
    isThreadRoute: Boolean,
    currentTabIndex: Int,
    isDrawerClosed: Boolean,
    isOverlayOpen: Boolean,
    hasCustomHeaderImage: Boolean,
    applyHeaderToThread: Boolean,
    useLightStatusBarIcons: Boolean
): Boolean {
    val customHeaderVisible = hasCustomHeaderImage && !isOverlayOpen && when {
        isHomeRoute -> currentTabIndex == 0 && isDrawerClosed
        isThreadRoute -> applyHeaderToThread
        else -> false
    }
    return if (customHeaderVisible) !useLightStatusBarIcons else !darkTheme
}
