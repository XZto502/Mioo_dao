package com.mioo.dao.ui.screens.thread

import org.junit.Assert.*
import org.junit.Test

class ThreadComposerVisibilityTest {
    private fun reveal(previous: Boolean, distance: Int?, up: Boolean = false, count: Int = 20) =
        shouldRevealThreadComposer(previous, ThreadEndObservation(count, distance, 140, up), 64f)

    @Test fun revealsBeforeReachingEnd() {
        assertFalse(reveal(false, 250))
        assertTrue(reveal(false, 200))
        assertTrue(reveal(false, 0))
    }

    @Test fun resizingViewportDoesNotReverseReveal() {
        assertTrue(reveal(true, 340))
        assertTrue(reveal(true, null))
        assertTrue(reveal(true, 340, up = true))
    }

    @Test fun readingBackAwayReleasesLatch() {
        assertFalse(reveal(true, 450, up = true))
        assertFalse(reveal(true, null, up = true))
    }

    @Test fun shortContentShowsComposerAndEmptyListDoesNotLatch() {
        assertTrue(reveal(false, -300, count = 1))
        assertFalse(reveal(true, 0, count = 0))
        assertFalse(reveal(false, null))
    }
}
