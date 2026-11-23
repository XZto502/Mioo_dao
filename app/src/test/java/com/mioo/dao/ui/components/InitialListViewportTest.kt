package com.mioo.dao.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialListViewportTest {

    @Test
    fun stageHeightsProgressMonotonicallyFromDeferredSkipToFullHeight() {
        val viewport = InitialListViewport(steps = 6, deferFirstRow = true)
        assertFalse(viewport.ready)

        val fullHeight = 2400
        assertEquals(0, viewport.viewportHeightForStage(fullHeight, 0))
        assertEquals(400, viewport.viewportHeightForStage(fullHeight, 1))
        assertEquals(800, viewport.viewportHeightForStage(fullHeight, 2))
        assertEquals(1200, viewport.viewportHeightForStage(fullHeight, 3))
        assertEquals(1600, viewport.viewportHeightForStage(fullHeight, 4))
        assertEquals(2000, viewport.viewportHeightForStage(fullHeight, 5))
        assertEquals(2400, viewport.viewportHeightForStage(fullHeight, 6))
        assertEquals(2400, viewport.viewportHeightForStage(fullHeight, 7))
    }

    @Test
    fun zeroOrNegativeFullHeightReturnsZero() {
        val viewport = InitialListViewport(steps = 6, deferFirstRow = true)
        assertEquals(0, viewport.viewportHeightForStage(0, 0))
        assertEquals(0, viewport.viewportHeightForStage(0, 3))
        assertEquals(0, viewport.viewportHeightForStage(-10, 2))
    }

    @Test
    fun nonDeferredViewportStartsAtFirstSlice() {
        val viewport = InitialListViewport(steps = 4, deferFirstRow = false, initialActive = true)
        assertTrue(viewport.active)
        assertEquals(500, viewport.viewportHeightForStage(2000))
    }
}
