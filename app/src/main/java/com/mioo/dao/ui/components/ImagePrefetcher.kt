package com.mioo.dao.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.memory.MemoryCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Prefetch thumbnail bitmaps just ahead of the visible window.
 * Waits for scroll settle to avoid competing with the first fling on cold start.
 */
@Composable
fun PrefetchListImages(
    imageUrls: List<String?>,
    listState: LazyListState,
    @Suppress("UNUSED_PARAMETER") sizePx: Int = ListThumbImage.SIZE_PX,
    ahead: Int = 6,
    /** Delay before first prefetch so cold-start first scroll isn't decoder-bound. */
    initialDelayMs: Long = 450,
    /**
     * When false (drawer open / board switch / page transition), do not enqueue decodes
     * so GPU/CPU stay free for the transition.
     */
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val latestUrls by rememberUpdatedState(imageUrls)

    // Match the thumbnail request's original-size decode and cache keys.
    // Key without imageUrls list reference so paging appends don't tear down active batches or re-wait delay.
    LaunchedEffect(listState, ahead, enabled, initialDelayMs, imageLoader) {
        if (!enabled) return@LaunchedEffect
        delay(initialDelayMs)
        if (!enabled) return@LaunchedEffect

        val memoryCache = imageLoader.memoryCache

        snapshotFlow {
            // Avoid observing layoutInfo during a fling: only the scroll state
            // needs to wake this flow until the list settles again.
            val urlsNow = latestUrls
            if (urlsNow.isEmpty() || listState.isScrollInProgress) return@snapshotFlow null
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()?.index ?: listState.firstVisibleItemIndex
            val last = info.visibleItemsInfo.lastOrNull()?.index
                ?: (first + info.visibleItemsInfo.size.coerceAtLeast(1))
            // Visible rows already enqueue their own requests through AsyncImage.
            val start = (last + 1).coerceAtLeast(0)
            val end = (last + ahead + 1).coerceAtMost(urlsNow.size)
            Triple(start, end, urlsNow.size)
        }
            .distinctUntilChanged()
            .collectLatest { bounds ->
                if (bounds == null || !enabled) return@collectLatest
                val (start, end, _) = bounds
                val urlsNow = latestUrls
                val safeEnd = end.coerceAtMost(urlsNow.size)
                if (start >= safeEnd) return@collectLatest
                val urls = (start until safeEnd).asSequence().mapNotNull { urlsNow.getOrNull(it) }
                    .filter { it.isNotBlank() }.map(IslandImage::listUrl)
                holdPrefetchBatch(
                    urls,
                    isCached = { memoryCache?.get(MemoryCache.Key(it)) != null },
                    enqueue = { url ->
                        val request = imageLoader.enqueue(
                            ListThumbImage.request(context, url) { w, h ->
                                ListThumbImage.recordSize(url, w, h)
                            }
                        )
                        val release: () -> Unit = { request.dispose() }
                        release
                    }
                )
            }
    }
}

fun prefetchImageUrls(
    context: android.content.Context,
    urls: Collection<String?>,
    @Suppress("UNUSED_PARAMETER") sizePx: Int = ListThumbImage.SIZE_PX,
    limit: Int = 8
) {
    val loader = context.imageLoader
    val memoryCache = loader.memoryCache
    var count = 0
    for (url in urls) {
        if (url.isNullOrBlank()) continue
        val listUrl = IslandImage.listUrl(url)
        if (memoryCache?.get(MemoryCache.Key(listUrl)) != null) continue
        loader.enqueue(
            ListThumbImage.request(context, listUrl) { w, h ->
                ListThumbImage.recordSize(listUrl, w, h)
            }
        )
        count++
        if (count >= limit) break
    }
}
