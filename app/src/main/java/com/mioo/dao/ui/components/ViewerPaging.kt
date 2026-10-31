package com.mioo.dao.ui.components

internal fun shouldLoadViewerPage(
    imageIndex: Int, imageCount: Int, hasMore: Boolean, loading: Boolean,
    failed: Boolean, loadedPage: Int, requestedPage: Int?
): Boolean = imageCount > 0 && imageIndex >= imageCount - 2 &&
    hasMore && !loading && !failed && requestedPage != loadedPage
