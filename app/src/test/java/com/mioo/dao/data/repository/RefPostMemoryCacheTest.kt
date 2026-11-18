package com.mioo.dao.data.repository

import com.mioo.dao.data.model.XdResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class RefPostMemoryCacheTest {

    @Test
    fun cacheHitSkipsSubsequentFetchCalls() = runBlocking {
        val fetches = AtomicInteger(0)
        val cache = RefPostMemoryCache<String>(scope = this, maxEntries = 8)

        val first = cache.getOrFetch("101") {
            fetches.incrementAndGet()
            XdResponse.Success("reply-101")
        }.first()
        val second = cache.getOrFetch("101") {
            fetches.incrementAndGet()
            XdResponse.Success("reply-101-updated")
        }.first()

        assertEquals(XdResponse.Success("reply-101"), first)
        assertEquals(XdResponse.Success("reply-101"), second)
        assertEquals(1, fetches.get())
    }

    @Test
    fun concurrentRequestsShareOneInFlightFetch() = runBlocking {
        val fetches = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        val cache = RefPostMemoryCache<String>(scope = this, maxEntries = 8)

        val deferreds = (1..10).map {
            async {
                cache.getOrFetch("202") {
                    fetches.incrementAndGet()
                    gate.await()
                    XdResponse.Success("reply-202")
                }.first()
            }
        }
        kotlinx.coroutines.yield()
        gate.complete(Unit)

        val results = deferreds.awaitAll()
        assertTrue(results.all { it == XdResponse.Success("reply-202") })
        assertEquals(1, fetches.get())
    }

    @Test
    fun errorsAreNotCachedAndNextAttemptRetries() = runBlocking {
        val fetches = AtomicInteger(0)
        val cache = RefPostMemoryCache<String>(scope = this, maxEntries = 8)

        val err = cache.getOrFetch("303") {
            fetches.incrementAndGet()
            XdResponse.Error(message = "temporary failure")
        }.first()
        val retry = cache.getOrFetch("303") {
            fetches.incrementAndGet()
            XdResponse.Success("reply-303")
        }.first()

        assertTrue(err is XdResponse.Error)
        assertEquals(XdResponse.Success("reply-303"), retry)
        assertEquals(2, fetches.get())
    }

    @Test
    fun lruEvictsLeastRecentlyUsedAndClearResetsCache() = runBlocking {
        val fetches = AtomicInteger(0)
        val cache = RefPostMemoryCache<String>(scope = this, maxEntries = 2)

        cache.getOrFetch("1") { fetches.incrementAndGet(); XdResponse.Success("v1") }.first()
        cache.getOrFetch("2") { fetches.incrementAndGet(); XdResponse.Success("v2") }.first()
        // Touch "1" so "2" becomes the least recently used entry
        cache.getOrFetch("1") { fetches.incrementAndGet(); XdResponse.Success("v1") }.first()
        cache.getOrFetch("3") { fetches.incrementAndGet(); XdResponse.Success("v3") }.first()

        // "1" should still hit cache; "2" was evicted and must re-fetch
        cache.getOrFetch("1") { fetches.incrementAndGet(); XdResponse.Success("v1") }.first()
        assertEquals(3, fetches.get())

        cache.getOrFetch("2") { fetches.incrementAndGet(); XdResponse.Success("v2") }.first()
        assertEquals(4, fetches.get())

        cache.clear()
        cache.getOrFetch("2") { fetches.incrementAndGet(); XdResponse.Success("v2") }.first()
        assertEquals(5, fetches.get())
    }

    @Test
    fun clearPreventsInFlightFetchFromPopulatingClearedCache() = runBlocking {
        val fetches = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        val cache = RefPostMemoryCache<String>(scope = this, maxEntries = 8)

        val job = async {
            cache.getOrFetch("404") {
                fetches.incrementAndGet()
                gate.await()
                XdResponse.Success("stale")
            }.first()
        }
        kotlinx.coroutines.yield()
        cache.clear()
        gate.complete(Unit)
        assertEquals(XdResponse.Success("stale"), job.await())

        val afterClear = cache.getOrFetch("404") {
            fetches.incrementAndGet()
            XdResponse.Success("fresh")
        }.first()
        assertEquals(XdResponse.Success("fresh"), afterClear)
        assertEquals(2, fetches.get())
    }
}
