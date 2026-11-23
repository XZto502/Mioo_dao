package com.mioo.dao.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.CompletableDeferred

/** Measures the first rows in bounded viewports before revealing a newly populated list. */
internal class InitialListViewport {
    private var stage by mutableIntStateOf(1)
    private val firstMeasure = CompletableDeferred<Unit>()
    val ready: Boolean get() = stage == 4

    val modifier = Modifier.layout { measurable, constraints ->
        if (!constraints.hasBoundedHeight || stage >= 3) {
            val child = measurable.measure(constraints)
            if (!constraints.hasBoundedHeight) firstMeasure.complete(Unit)
            layout(child.width, child.height) { child.place(0, 0) }
        } else {
            val fullHeight = constraints.maxHeight
            val height = (fullHeight * stage / 3).coerceAtLeast(1).coerceAtMost(fullHeight)
            val child = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
            if (fullHeight > 0) firstMeasure.complete(Unit)
            layout(child.width, fullHeight) { child.place(0, 0) }
        }
    }

    suspend fun prepare() {
        firstMeasure.await()
        while (!ready) {
            withFrameNanos { }
            stage++
        }
    }
}

@Composable
internal fun rememberInitialListViewport(hasContent: Boolean): InitialListViewport {
    val viewport = remember { InitialListViewport() }
    LaunchedEffect(hasContent) {
        if (hasContent) viewport.prepare()
    }
    return viewport
}
