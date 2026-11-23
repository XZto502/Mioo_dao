package com.mioo.dao.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.CompletableDeferred

/**
 * Measures the first rows across bounded viewports before revealing a newly populated list.
 *
 * Stage 0 decouples parent state-transition recomposition from the first row's subcomposition
 * and measure/layout by skipping child measurement for one frame while preserving parent size
 * (and without touching LazyListState's initial index/offset). Subsequent stages expand by
 * `1 / steps` of the viewport height (~1 card per frame on 90Hz displays).
 *
 * Attach [modifier] unconditionally to the list so toggling [hasContent] never detaches or
 * re-attaches the LazyColumn's Modifier.Node chain.
 */
internal class InitialListViewport(
    private val steps: Int = DEFAULT_STEPS,
    private val deferFirstRow: Boolean = true,
    initialActive: Boolean = false
) {
    internal var active by mutableStateOf(initialActive)
    private var stage by mutableIntStateOf(if (deferFirstRow) 0 else 1)
    private val firstMeasure = CompletableDeferred<Unit>()
    val ready: Boolean get() = stage > steps

    internal fun viewportHeightForStage(fullHeight: Int, currentStage: Int = stage): Int {
        if (fullHeight <= 0) return 0
        if (currentStage <= 0) return 0
        if (currentStage >= steps) return fullHeight
        return (fullHeight * currentStage / steps).coerceAtLeast(1).coerceAtMost(fullHeight)
    }

    val modifier = Modifier.layout { measurable, constraints ->
        if (!active || !constraints.hasBoundedHeight || stage >= steps) {
            val child = measurable.measure(constraints)
            if (active && (!constraints.hasBoundedHeight || constraints.maxHeight > 0)) {
                firstMeasure.complete(Unit)
            }
            layout(child.width, child.height) { child.place(0, 0) }
        } else {
            val fullHeight = constraints.maxHeight
            val height = viewportHeightForStage(fullHeight)
            if (height <= 0) {
                if (fullHeight > 0) firstMeasure.complete(Unit)
                val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
                layout(width, fullHeight) { }
            } else {
                val child = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                if (fullHeight > 0) firstMeasure.complete(Unit)
                layout(child.width, fullHeight) { child.place(0, 0) }
            }
        }
    }

    suspend fun prepare() {
        firstMeasure.await()
        while (!ready) {
            withFrameNanos { }
            stage++
        }
    }

    companion object {
        const val DEFAULT_STEPS = 6
    }
}

@Composable
internal fun rememberInitialListViewport(
    hasContent: Boolean,
    resetKey: Any? = Unit,
    steps: Int = InitialListViewport.DEFAULT_STEPS,
    deferFirstRow: Boolean = true
): InitialListViewport {
    val viewport = remember(resetKey, steps, deferFirstRow) {
        InitialListViewport(
            steps = steps,
            deferFirstRow = deferFirstRow,
            initialActive = hasContent
        )
    }
    if (viewport.active != hasContent) {
        viewport.active = hasContent
    }
    LaunchedEffect(viewport, hasContent) {
        if (hasContent) viewport.prepare()
    }
    return viewport
}
