package com.mioo.dao.ui.screens.thread

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** One adjacent page only. Preparing it never mutates the visible reading window. */
internal class PreviousPagePrefetch<K, T>(private val scope: CoroutineScope) {
    private var pending: Pair<K, Deferred<T?>>? = null

    fun prepare(key: K, load: suspend () -> T) {
        if (pending?.first == key) return
        clear()
        pending = key to scope.async {
            try {
                load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null // A speculative failure must not interrupt reading; foreground can retry.
            }
        }
    }

    fun take(key: K): Deferred<T?>? {
        if (pending?.first != key) return null
        return pending?.second.also { pending = null }
    }

    fun clear() {
        pending?.second?.cancel()
        pending = null
    }
}
