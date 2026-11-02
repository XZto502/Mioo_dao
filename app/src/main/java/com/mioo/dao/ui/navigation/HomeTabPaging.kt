package com.mioo.dao.ui.navigation

import androidx.compose.animation.core.animate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.pager.PagerState
import com.mioo.dao.ui.theme.MiooMotion

/** Four home tabs are close enough to animate the whole distance without a pre-jump. */
@OptIn(ExperimentalFoundationApi::class)
internal suspend fun PagerState.animateHomeTabTo(page: Int) {
    if (pageCount == 0) return
    scroll {
        val target = page.coerceIn(0, pageCount - 1)
        updateTargetPage(target)
        // Calculate inside the scroll mutation: a rapid second tap cancels the old
        // animation and continues from its actual fractional position.
        val stride = layoutInfo.pageSize + layoutInfo.pageSpacing
        val displacement = (target - currentPage - currentPageOffsetFraction) * stride
        animateHomeTabDistance(displacement)
    }
}

internal suspend fun ScrollScope.animateHomeTabDistance(displacement: Float) {
    var consumedDistance = 0f
    animate(0f, displacement, animationSpec = MiooMotion.tabPagerSpring()) { position, _ ->
        consumedDistance += scrollBy(position - consumedDistance)
    }
}
