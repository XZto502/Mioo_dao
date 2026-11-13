package com.mioo.dao.ui.screens.thread

/** Preserve a visible real floor, not the promotional floor repeated on every API page. */
internal fun resolvePrependReadingAnchor(
    visible: List<Pair<String, Int>>, newIds: List<String>, threadId: String, hasMain: Boolean
): Pair<Int, Int>? {
    for ((key, offset) in visible) {
        if (key == "9999999") continue
        if (hasMain && key == "main_$threadId") return 0 to -offset
        val index = newIds.indexOf(key)
        if (index >= 0) return (index + 1) to -offset
    }
    return null
}

/** Existing rows can be targeted during append/refresh, but not during replacement. */
internal inline fun loadedPageJumpIndex(
    page: Int, startPage: Int, isReplacing: Boolean, findFirstReplyIndex: () -> Int
): Int? {
    if (isReplacing) return null
    if (page == 1 && startPage == 1) return 0
    val index = findFirstReplyIndex()
    return if (index >= 0) index + 1 else null
}

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
