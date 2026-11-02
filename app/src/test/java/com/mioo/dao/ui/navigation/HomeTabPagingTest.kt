package com.mioo.dao.ui.navigation

import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class HomeTabPagingTest {
    private class Frames : MonotonicFrameClock {
        private var time = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            time += 16_666_667L
            return onFrame(time)
        }
    }

    private class Pages(initial: Float) : ScrollScope {
        var position = initial
        val frames = mutableListOf(initial)
        override fun scrollBy(pixels: Float): Float {
            val before = position
            position = (position + pixels).coerceIn(0f, 1080f)
            frames += position
            return position - before
        }
    }

    @Test fun boardToMoreAnimatesAcrossAllFourTabsWithoutAPreJump() = runBlocking(Frames()) {
        val pages = Pages(0f)
        pages.animateHomeTabDistance(1080f)
        assertEquals(1080f, pages.position, 0.1f)
        assertTrue(pages.frames.any { it in 1f..359f })
        assertTrue(pages.frames.any { it in 361f..719f })
        assertTrue(pages.frames.any { it in 721f..1079f })
        assertTrue(pages.frames.zipWithNext().all { (a, b) -> abs(b - a) < 360f })
    }

    @Test fun reverseAndMidwayRetargetingStartAtTheActualPosition() = runBlocking(Frames()) {
        for (initial in listOf(1080f, 612f)) {
            val pages = Pages(initial)
            pages.animateHomeTabDistance(-initial)
            assertEquals(0f, pages.position, 0.1f)
            assertTrue(pages.frames.size > 2)
            assertTrue(pages.frames.zipWithNext().all { (a, b) -> abs(b - a) < 360f })
        }
    }
}
