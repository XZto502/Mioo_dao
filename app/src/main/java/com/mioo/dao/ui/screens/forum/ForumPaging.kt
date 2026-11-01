package com.mioo.dao.ui.screens.forum

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last

/** A moving board page must not paint an old cache then reorder itself mid-scroll.
 * The repository retains its cached result when the network fails. */
internal fun <T> Flow<T>.settledForumPage(): Flow<T> = flow { emit(last()) }

/** Bumped threads may occur on multiple pages. Keep their original reading position. */
internal fun <T, K> appendForumPage(existing: List<T>, page: List<T>, key: (T) -> K): List<T> {
    val rows = LinkedHashMap<K, T>()
    existing.forEach { rows[key(it)] = it }
    page.forEach { rows[key(it)] = it }
    return rows.values.toList()
}
