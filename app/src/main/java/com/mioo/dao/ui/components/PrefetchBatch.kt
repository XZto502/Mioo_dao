package com.mioo.dao.ui.components

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Own only speculative requests; visible image requests remain owned by Coil's UI. */
internal suspend fun holdPrefetchBatch(
    urls: Sequence<String>,
    isCached: (String) -> Boolean,
    enqueue: (String) -> (() -> Unit)
) {
    val releases = mutableListOf<() -> Unit>()
    val seen = mutableSetOf<String>()
    try {
        for (url in urls) {
            currentCoroutineContext().ensureActive()
            if (!seen.add(url) || isCached(url)) continue
            releases.add(enqueue(url))
            if (releases.size % 3 == 0) delay(1)
        }
        awaitCancellation()
    } finally {
        releases.forEach { it() }
    }
}
