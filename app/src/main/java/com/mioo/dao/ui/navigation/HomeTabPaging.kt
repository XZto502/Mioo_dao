package com.mioo.dao.ui.navigation

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.pager.PagerState

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
    var consumedDistance = 0f
    // A bounded, monotonic curve avoids the spring's tiny overshoot and long tail,
    // especially visible as an apparent pause at the first/last page boundary.
    animate(0f, displacement, animationSpec = tween(360, easing = FastOutSlowInEasing)) { position, _ ->
        consumedDistance += scrollBy(position - consumedDistance)
    }
}
