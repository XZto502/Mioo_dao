package com.mioo.dao.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Axis lock for a [HorizontalPager] wrapping a vertically scrolling [LazyColumn].
 *
 * Unlike Compose's default bottom-up `Main` pass, this observes raw pointer deltas on
 * `Initial` so a flinging child `LazyColumn` (which starts dragging immediately) cannot
 * swallow horizontal tab swipes.
 */
internal class PagerSwipeIntent(private val slop: Float) {
    enum class Result { WAIT, VERTICAL, HORIZONTAL }

    private var x = 0f
    private var y = 0f

    val totalDx: Float get() = x

    fun add(dx: Float, dy: Float): Result {
        x += dx
        y += dy
        val ax = abs(x)
        val ay = abs(y)
        return when {
            ax > slop * 0.65f && ax > ay * 1.25f -> Result.HORIZONTAL
            ay > slop && ay >= ax -> Result.VERTICAL
            else -> Result.WAIT
        }
    }
}

internal fun resolvePagerTargetPage(
    startPage: Int,
    pageCount: Int,
    totalDx: Float,
    pageWidthPx: Float,
    velocityXPxPerSec: Float
): Int {
    if (pageCount <= 1) return 0
    val lastPage = pageCount - 1
    val width = pageWidthPx.coerceAtLeast(1f)
    val raw = when {
        velocityXPxPerSec <= -400f -> startPage + 1
        velocityXPxPerSec >= 400f -> startPage - 1
        totalDx <= -width * 0.25f -> startPage + 1
        totalDx >= width * 0.25f -> startPage - 1
        else -> startPage
    }
    return raw.coerceIn(0, lastPage)
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pagerHorizontalSwipeArbitration(
    pagerState: PagerState,
    currentListState: () -> LazyListState?,
    scope: CoroutineScope
): Modifier = pointerInput(pagerState, scope) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (pagerState.pageCount <= 1) return@awaitEachGesture

        val intent = PagerSwipeIntent(viewConfiguration.touchSlop)
        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        var claimed = false
        var startPage = pagerState.currentPage
        var totalClaimedDx = 0f
        var deltaChannel: Channel<Float>? = null
        var settleTarget: CompletableDeferred<Int>? = null

        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size != 1) break
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (claimed) change.consume()
                    break
                }

                val dx = change.position.x - change.previousPosition.x
                val dy = change.position.y - change.previousPosition.y
                velocityTracker.addPosition(change.uptimeMillis, change.position)

                if (claimed) {
                    change.consume()
                    totalClaimedDx += dx
                    if (dx != 0f) deltaChannel?.trySend(dx)
                } else {
                    when (intent.add(dx, dy)) {
                        PagerSwipeIntent.Result.VERTICAL -> return@awaitEachGesture
                        PagerSwipeIntent.Result.WAIT -> Unit
                        PagerSwipeIntent.Result.HORIZONTAL -> {
                            val goingNext = intent.totalDx < 0f
                            val canMove = if (goingNext) {
                                pagerState.canScrollForward
                            } else {
                                pagerState.canScrollBackward
                            }
                            if (!canMove) return@awaitEachGesture

                            claimed = true
                            startPage = pagerState.currentPage
                            totalClaimedDx = intent.totalDx
                            change.consume()

                            currentListState()?.let { list ->
                                scope.launch {
                                    list.stopScroll(MutatePriority.PreventUserInput)
                                }
                            }

                            val ch = Channel<Float>(Channel.UNLIMITED)
                            val deferred = CompletableDeferred<Int>()
                            deltaChannel = ch
                            settleTarget = deferred

                            scope.launch {
                                try {
                                    pagerState.scroll(MutatePriority.UserInput) {
                                        for (first in ch) {
                                            var sum = first
                                            while (true) {
                                                val next = ch.tryReceive().getOrNull() ?: break
                                                sum += next
                                            }
                                            // PagerState.scrollableState negates delta before performScroll;
                                            // scrollBy(-sum) moves forward when dragging left (sum < 0).
                                            if (sum != 0f) scrollBy(-sum)
                                        }
                                    }
                                    val target = deferred.await()
                                    val lastPage = (pagerState.pageCount - 1).coerceAtLeast(0)
                                    pagerState.animateScrollToPage(
                                        page = target.coerceIn(0, lastPage),
                                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                                    )
                                } catch (_: Exception) {
                                    // Cancellation or interrupted gesture settles cleanly on next interaction.
                                }
                            }
                            if (totalClaimedDx != 0f) ch.trySend(totalClaimedDx)
                        }
                    }
                }
            }
        } finally {
            if (claimed) {
                val target = if (settleTarget?.isCompleted == false) {
                    val vx = velocityTracker.calculateVelocity().x
                    resolvePagerTargetPage(
                        startPage = startPage,
                        pageCount = pagerState.pageCount,
                        totalDx = totalClaimedDx,
                        pageWidthPx = size.width.toFloat(),
                        velocityXPxPerSec = vx
                    )
                } else {
                    startPage
                }
                if (settleTarget?.isCompleted == false) {
                    settleTarget?.complete(target)
                }
                deltaChannel?.close()
            }
        }
    }
}
