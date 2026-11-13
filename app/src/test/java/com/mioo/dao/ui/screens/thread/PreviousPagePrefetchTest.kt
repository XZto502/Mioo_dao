package com.mioo.dao.ui.screens.thread

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class PreviousPagePrefetchTest {
    @Test fun foregroundReusesTheSameInFlightRequest() = runBlocking {
        val prefetch = PreviousPagePrefetch<Int, String>(this)
        val result = CompletableDeferred<String>()
        var requests = 0
        prefetch.prepare(8) { requests++; result.await() }
        prefetch.prepare(8) { error("duplicate request") }
        yield()
        assertNull(prefetch.take(7))
        val pending = prefetch.take(8)!!
        result.complete("fresh page")
        assertEquals("fresh page", pending.await())
        assertEquals(1, requests)
        assertNull(prefetch.take(8))
    }

    @Test fun changingPageOrFilterCancelsObsoleteWork() = runBlocking {
        val prefetch = PreviousPagePrefetch<Pair<Int, Boolean>, String>(this)
        var cancelled = false
        prefetch.prepare(8 to false) {
            try { CompletableDeferred<String>().await() } finally { cancelled = true }
        }
        yield()
        prefetch.prepare(8 to true) { "PO page" }
        yield()
        assertTrue(cancelled)
        assertNull(prefetch.take(8 to false))
        assertEquals("PO page", prefetch.take(8 to true)!!.await())
    }

    @Test fun failureIsIsolatedAndClearCancelsPendingWork() = runBlocking {
        val prefetch = PreviousPagePrefetch<Int, String>(this)
        prefetch.prepare(2) { throw java.io.IOException("offline") }
        assertNull(prefetch.take(2)!!.await())
        prefetch.prepare(1) { CompletableDeferred<String>().await() }
        prefetch.clear()
        assertNull(prefetch.take(1))
    }
}
