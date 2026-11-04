package com.mioo.dao.ui.screens.forum

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class ForumPullRefreshStateTest {
    private class Frames : MonotonicFrameClock {
        private var time = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            time += 16_666_667L
            return onFrame(time)
        }
    }

    private class PausedFrames : MonotonicFrameClock {
        val animationStarted = CompletableDeferred<Unit>()
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            animationStarted.complete(Unit)
            awaitCancellation()
        }
    }

    @Test fun interruptedReturnDoesNotConsumeTheNextUpwardDrag() {
        val frames = PausedFrames()
        runBlocking(frames) {
            val state = ForumPullRefreshState(80f)
            val scroll = state.nestedScrollConnection
            scroll.onPostScroll(Offset.Zero, Offset(0f, 100f), NestedScrollSource.Drag)
            assertTrue(state.progress > 0f)
            val release = launch { scroll.onPreFling(Velocity.Zero) }
            frames.animationStarted.await()
            release.cancelAndJoin()
            assertEquals(0f, state.progress, 0f)
            assertEquals(0f, state.verticalOffset, 0f)
            assertEquals(Offset.Zero, scroll.onPreScroll(Offset(0f, -30f), NestedScrollSource.Drag))
            assertFalse(state.isRefreshing)
        }
    }

    @Test fun firstNewPullSurvivesAnOlderReturnBeingCancelled() {
        val frames = PausedFrames()
        runBlocking(frames) {
            val state = ForumPullRefreshState(80f)
            val scroll = state.nestedScrollConnection
            scroll.onPostScroll(Offset.Zero, Offset(0f, 100f), NestedScrollSource.Drag)
            val oldRelease = launch { scroll.onPreFling(Velocity.Zero) }
            frames.animationStarted.await()
            // The next gesture can arrive before the old release observes cancellation.
            scroll.onPreScroll(Offset(0f, 200f), NestedScrollSource.Drag)
            scroll.onPostScroll(Offset.Zero, Offset(0f, 200f), NestedScrollSource.Drag)
            oldRelease.cancelAndJoin()
            assertTrue(state.progress > 1f)
            scroll.onPreFling(Velocity.Zero)
            assertTrue(state.isRefreshing)
        }
    }

    @Test fun refreshCompletionClearsDragDistanceAndPassesOrdinaryFlings() = runBlocking(Frames()) {
        val state = ForumPullRefreshState(80f)
        val scroll = state.nestedScrollConnection
        scroll.onPostScroll(Offset.Zero, Offset(0f, 200f), NestedScrollSource.Drag)
        scroll.onPreFling(Velocity(0f, 700f))
        assertTrue(state.isRefreshing)
        state.endRefresh()
        assertEquals(0f, state.progress, 0f)
        assertEquals(Offset.Zero, scroll.onPreScroll(Offset(0f, -100f), NestedScrollSource.Drag))
        assertEquals(Velocity.Zero, scroll.onPreFling(Velocity(0f, 700f)))
        assertEquals(Velocity.Zero, scroll.onPreFling(Velocity(0f, -700f)))
    }

    @Test fun oldCancelledReleaseDoesNotResetANewerRefresh() {
        val frames = PausedFrames()
        runBlocking(frames) {
            val state = ForumPullRefreshState(80f)
            state.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 80f), NestedScrollSource.Drag)
            val release = launch { state.nestedScrollConnection.onPreFling(Velocity.Zero) }
            frames.animationStarted.await()
            state.endRefresh()
            state.startRefresh()
            release.cancelAndJoin()
            assertTrue(state.isRefreshing)
            assertEquals(80f, state.verticalOffset, 0f)
        }
    }

    @Test fun toolbarRefreshCompletesWithoutSwallowingListDrags() {
        val state = ForumPullRefreshState(80f)
        state.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 100f), NestedScrollSource.Drag)
        state.startRefresh()
        assertEquals(Offset.Zero, state.nestedScrollConnection.onPreScroll(Offset(0f, -30f), NestedScrollSource.Drag))
        state.endRefresh()
        assertEquals(Offset.Zero, state.nestedScrollConnection.onPreScroll(Offset(0f, -30f), NestedScrollSource.Drag))
    }
}
