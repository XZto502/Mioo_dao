package com.mioo.dao.ui.screens.forum

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ForumPagingTest {
    @Test fun cachedPageDoesNotPaintBeforeNetworkFinishes() = runBlocking {
        val network = CompletableDeferred<Unit>()
        val painted = mutableListOf<List<Int>>()
        val job = launch {
            flow {
                emit(listOf(1, 2))
                network.await()
                emit(listOf(3, 1))
            }.settledForumPage().toList(painted)
        }
        yield()
        assertTrue(painted.isEmpty())
        network.complete(Unit)
        job.join()
        assertEquals(listOf(listOf(3, 1)), painted)
    }

    @Test fun offlineCachedResultIsRetained() = runBlocking {
        assertEquals(listOf(listOf(1)), flow { emit(listOf(1)) }.settledForumPage().toList())
    }

    @Test fun cancelledBoardRequestNeverPublishesItsCache() = runBlocking {
        val painted = mutableListOf<List<Int>>()
        val job = launch {
            flow {
                emit(listOf(1))
                CompletableDeferred<Unit>().await()
            }.settledForumPage().toList(painted)
        }
        yield()
        job.cancelAndJoin()
        assertTrue(painted.isEmpty())
    }

    @Test fun bumpedThreadsUpdateInPlaceInsteadOfMovingToTheEnd() {
        val old = listOf(1 to "old", 2 to "old", 3 to "old")
        val next = listOf(2 to "new", 4 to "new", 4 to "latest")
        assertEquals(listOf(1 to "old", 2 to "new", 3 to "old", 4 to "latest"),
            appendForumPage(old, next) { it.first })
    }
}
