package com.mioo.dao.ui.screens.feed

internal fun <T> applyFeedChange(rows: List<T>, id: String, added: T?, key: (T) -> String): List<T> {
    val exists = rows.any { key(it) == id }
    return when {
        added == null && exists -> rows.filterNot { key(it) == id }
        added != null && !exists -> listOf(added) + rows
        else -> rows
    }
}

internal fun <T> mergeChangedFirstPage(
    current: List<T>, received: List<T>, changed: Set<String>, key: (T) -> String
): List<T> {
    if (changed.isEmpty()) return received
    return current.filter { key(it) in changed } + received.filterNot { key(it) in changed }
}

internal fun <T> appendFeedRows(
    rows: List<T>, added: List<T>, replacing: Set<String>, key: (T) -> String
): List<T> {
    if (replacing.isEmpty()) return if (added.isEmpty()) rows else rows + added
    return buildList(rows.size + added.size) {
        for (row in rows) {
            if (key(row) !in replacing) add(row)
        }
        addAll(added)
    }
}

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
