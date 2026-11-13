package com.mioo.dao.ui.screens.feed

/** Match first-occurrence paging semantics without rebuilding existing display rows. */
internal fun <T, K> newFeedPageItems(existing: List<T>, page: List<T>, key: (T) -> K): List<T> {
    val seen = existing.mapTo(HashSet(existing.size + page.size), key)
    return page.filter { seen.add(key(it)) }
}
