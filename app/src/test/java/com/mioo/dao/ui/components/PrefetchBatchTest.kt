package com.mioo.dao.ui.components

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class PrefetchBatchTest {
    @Test fun cancellationReleasesOutstandingRequestsAndDeduplicatesUrls() = runBlocking {
        val requested = mutableListOf<String>()
        val released = mutableListOf<String>()
        val batch = launch {
            holdPrefetchBatch(sequenceOf("cached", "a", "a", "b"), { it == "cached" }) { url ->
                requested.add(url)
                val release: () -> Unit = { released.add(url); Unit }
                release
            }
        }
        yield()
        assertEquals(listOf("a", "b"), requested)
        assertTrue(released.isEmpty())
        batch.cancelAndJoin()
        assertEquals(requested, released)
    }

    @Test fun failedEnqueueReleasesEarlierRequests() = runBlocking {
        val released = mutableListOf<String>()
        try {
            holdPrefetchBatch(sequenceOf("a", "b"), { false }) { url ->
                if (url == "b") error("enqueue failed")
                val release: () -> Unit = { released.add(url); Unit }
                release
            }
            fail("Expected enqueue failure")
        } catch (expected: IllegalStateException) {
            assertEquals("enqueue failed", expected.message)
        }
        assertEquals(listOf("a"), released)
    }
}
