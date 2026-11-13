package com.mioo.dao.ui.screens.thread

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class JumpPageContextTest {
    @Test fun emptyOrWrongThreadNeighboursCannotAffectTheFreshTargetWindow() {
        val empty = com.mioo.dao.data.model.Thread(id = 123, replies = emptyList())
        assertFalse(empty.isUsefulJumpContext("123"))
        assertFalse(empty.copy(replies = null).isUsefulJumpContext("123"))
        val context = empty.copy(replies = listOf(com.mioo.dao.data.model.Reply(id = 456)))
        assertTrue(context.isUsefulJumpContext("123"))
        assertFalse(context.isUsefulJumpContext("999"))
    }
    @Test fun cachedNeighbourReturnsWithoutWaitingForItsNetworkRefresh() = runBlocking {
        var networkStarted = false
        val result = jumpPageContext(flow {
            emit("cache")
            networkStarted = true
            CompletableDeferred<Unit>().await()
            emit("network")
        })
        assertEquals("cache", result)
        assertFalse(networkStarted)
    }

    @Test fun uncachedNeighbourCanReturnNetworkData() = runBlocking {
        assertEquals("network", jumpPageContext(flow { emit("network") }))
    }

    @Test fun slowNeighbourTimesOutAndReleasesItsWork() = runBlocking {
        var released = false
        val result = jumpPageContext(flow<String> {
            try { CompletableDeferred<Unit>().await() } finally { released = true }
        }, timeoutMillis = 10)
        assertNull(result)
        assertTrue(released)
    }

    @Test fun newerJumpCancellationIsNotSwallowed() = runBlocking {
        var returned = false
        val started = CompletableDeferred<Unit>()
        val job = launch {
            jumpPageContext(flow<String> {
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
            })
            returned = true
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertFalse(returned)
    }
}
