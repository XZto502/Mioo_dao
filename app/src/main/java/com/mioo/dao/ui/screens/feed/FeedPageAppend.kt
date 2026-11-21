package com.mioo.dao.ui.screens.feed

/** Match first-occurrence paging semantics without rebuilding existing display rows. */
internal fun <T, K> newFeedPageItems(
    existing: List<T>,
    page: List<T>,
    replacing: Set<K> = emptySet(),
    key: (T) -> K
): List<T> {
    val seen = HashSet<K>(existing.size + page.size)
    for (item in existing) {
        val id = key(item)
        if (id !in replacing) seen.add(id)
    }
    return page.filter { seen.add(key(it)) }
}
