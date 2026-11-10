package com.mioo.dao.ui.navigation

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.pager.PagerState
import kotlin.math.roundToInt

// Build momentum earlier, then settle continuously without overshooting the page.
private val HomeTabEasing = CubicBezierEasing(0.28f, 0f, 0.28f, 1f)

/** Four home tabs are close enough to animate the whole distance without a pre-jump. */
@OptIn(ExperimentalFoundationApi::class)
internal suspend fun PagerState.animateHomeTabTo(page: Int) {
    if (pageCount == 0) return
    val target = page.coerceIn(0, pageCount - 1)
    scroll {
        updateTargetPage(target)
        // Calculate inside the scroll mutation: a rapid second tap cancels the old
        // animation and continues from its actual fractional position.
        val stride = layoutInfo.pageSize + layoutInfo.pageSpacing
        val displacement = (target - currentPage - currentPageOffsetFraction) * stride
        animateHomeTabDistance(displacement)
    }
    // The tween delivers the exact endpoint within the same scroll mutation.
    // A second scrollToPage here would trigger another layout/settle at the end.
}

internal suspend fun ScrollScope.animateHomeTabDistance(displacement: Float) {
    var previousPixel = 0
    // Pager places pages at integer pixels. Send each pixel once instead of
    // feeding fractional consumption back into the final animation frames.
    animate(0f, displacement, animationSpec = tween(320, easing = HomeTabEasing)) { position, _ ->
        val pixel = position.roundToInt()
        val delta = pixel - previousPixel
        if (delta != 0) scrollBy(delta.toFloat())
        previousPixel = pixel
    }
}
