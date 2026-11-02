package com.mioo.dao.ui.screens.forum

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CancellationException

/** Retain M3 1.2's indicator/physics, but discard pull distance after an interrupted release.
 * Its default state clears distancePulled only AFTER the suspend return animation. A new drag
 * cancels that animation and can leave invisible distance consuming subsequent upward drags.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal class ForumPullRefreshState(override val positionalThreshold: Float) : PullToRefreshState {
    private var delegate by mutableStateOf(newState(false))
    private var releasing: PullToRefreshState? = null

    private fun newState(refreshing: Boolean) = PullToRefreshState(
        positionalThresholdPx = positionalThreshold,
        initialRefreshing = refreshing
    ).also {
        // In 1.2 initialRefreshing alone does not position the indicator.
        if (refreshing) it.startRefresh()
    }

    override val progress get() = delegate.progress
    override val isRefreshing get() = delegate.isRefreshing
    override val verticalOffset get() = delegate.verticalOffset

    override fun startRefresh() {
        // A toolbar refresh can overlap the previous release animation as well.
        delegate = newState(true)
    }

    override fun endRefresh() {
        // endRefresh() in 1.2 resets the indicator, but not its accumulated drag distance.
        delegate = newState(false)
    }

    private fun prepareForDrag(source: NestedScrollSource) {
        val old = releasing
        if (source == NestedScrollSource.Drag && old != null && delegate === old && !old.isRefreshing) {
            // The previous release is still animating. Give the new gesture its
            // own state before consuming any of its distance; its cancellation
            // must not erase the new pull.
            delegate = newState(false)
        }
    }

    override var nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            prepareForDrag(source)
            return delegate.nestedScrollConnection.onPreScroll(available, source)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            prepareForDrag(source)
            return delegate.nestedScrollConnection.onPostScroll(consumed, available, source)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            val releasedState = delegate
            if (!releasedState.isRefreshing && releasedState.progress == 0f && releasedState.verticalOffset == 0f) {
                return Velocity.Zero
            }
            releasing = releasedState
            return try {
                releasedState.nestedScrollConnection.onPreFling(available)
            } catch (cancelled: CancellationException) {
                // A toolbar refresh/board change may already have replaced this state.
                if (delegate === releasedState) delegate = newState(releasedState.isRefreshing)
                throw cancelled
            } finally {
                if (releasing === releasedState) releasing = null
            }
        }
    }
}
