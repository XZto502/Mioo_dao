package com.mioo.dao.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Prefetch thumbnail bitmaps just ahead of the visible window.
 * Waits for scroll settle to avoid competing with the first fling on cold start.
 */
@Composable
fun PrefetchListImages(
    imageUrls: List<String?>,
    listState: LazyListState,
    sizePx: Int = 360,
    ahead: Int = 6,
    /** Delay before first prefetch so cold-start first scroll isn't decoder-bound. */
    initialDelayMs: Long = 450
) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader

    LaunchedEffect(imageUrls, listState, sizePx, ahead) {
        if (imageUrls.isEmpty()) return@LaunchedEffect
        delay(initialDelayMs)

        snapshotFlow {
            val info = listState.layoutInfo
            val scrolling = listState.isScrollInProgress
            val first = info.visibleItemsInfo.firstOrNull()?.index ?: listState.firstVisibleItemIndex
            val last = info.visibleItemsInfo.lastOrNull()?.index
                ?: (first + info.visibleItemsInfo.size.coerceAtLeast(1))
            Triple(scrolling, first, last)
        }
            .distinctUntilChanged()
            .map { (scrolling, first, last) ->
                // Only warm when finger is up — avoids decode spikes mid-fling
                if (scrolling) null
                else {
                    val start = first.coerceAtLeast(0)
                    val end = (last + ahead + 1).coerceAtMost(imageUrls.size)
                    start until end
                }
            }
            .distinctUntilChanged()
            .collectLatest { range ->
                if (range == null) return@collectLatest
                // Small yield between enqueues so UI frames can run
                var n = 0
                for (index in range) {
                    val url = imageUrls.getOrNull(index) ?: continue
                    if (url.isBlank()) continue
                    imageLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(url)
                            .size(sizePx)
                            .memoryCacheKey(url)
                            .diskCacheKey(url)
                            .allowHardware(true)
                            .build()
                    )
                    n++
                    if (n % 3 == 0) delay(1)
                }
            }
    }
}

fun prefetchImageUrls(
    context: android.content.Context,
    urls: Collection<String?>,
    sizePx: Int = 360,
    limit: Int = 8
) {
    val loader = context.imageLoader
    var count = 0
    for (url in urls) {
        if (url.isNullOrBlank()) continue
        loader.enqueue(
            ImageRequest.Builder(context)
                .data(url)
                .size(sizePx)
                .memoryCacheKey(url)
                .diskCacheKey(url)
                .build()
        )
        count++
        if (count >= limit) break
    }
}
