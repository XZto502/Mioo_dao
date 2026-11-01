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

/** Convert page-local legacy positions into a window containing adjacent pages. */
internal fun resolveWindowReadingIndex(
    postId: String?, threadId: String, targetPageIds: List<String>,
    windowIds: List<String>, localIndex: Int, targetPage: Int
): Int {
    if (postId == threadId || (postId == null && localIndex == 0 && targetPage == 1)) return 0
    val anchor = postId?.takeIf { it != "9999999" && it in windowIds }
        ?: targetPageIds.getOrNull((localIndex - 1).coerceAtLeast(0))?.takeUnless { it == "9999999" }
        ?: targetPageIds.firstOrNull { it != "9999999" }
        ?: targetPageIds.lastOrNull()
    return resolveReadingIndex(anchor, threadId, windowIds, 0)
}
