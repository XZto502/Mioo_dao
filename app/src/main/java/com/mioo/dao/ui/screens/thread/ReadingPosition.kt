package com.mioo.dao.ui.screens.thread

/** 存储索引保留 0=主楼，后续页的 LazyColumn 则不含主楼。 */
internal fun readingLazyIndex(savedIndex: Int, hasMainPost: Boolean): Int =
    (savedIndex - if (hasMainPost) 0 else 1).coerceAtLeast(0)

internal fun shouldLoadPreviousAtEdge(
    dragY: Float,
    isDrag: Boolean,
    canScrollBackward: Boolean,
    startPage: Int,
    isLoading: Boolean,
    isRestoring: Boolean
): Boolean = isDrag && dragY > 0f && !canScrollBackward && startPage > 1 &&
    !isLoading && !isRestoring

internal fun resolveReadingIndex(postId: String?, threadId: String, visibleIds: List<String>, fallback: Int): Int {
    if (postId == threadId) return 0
    val index = visibleIds.indexOf(postId)
    return if (index >= 0) index + 1 else fallback.coerceAtLeast(0)
}
