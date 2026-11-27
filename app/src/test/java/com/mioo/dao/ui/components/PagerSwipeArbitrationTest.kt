package com.mioo.dao.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PagerSwipeArbitrationTest {

    @Test
    fun waitForSmallJitterBeforeCommittingAxis() {
        val intent = PagerSwipeIntent(slop = 20f)
        assertEquals(PagerSwipeIntent.Result.WAIT, intent.add(-4f, 2f))
        assertEquals(PagerSwipeIntent.Result.WAIT, intent.add(2f, -3f))
    }

    @Test
    fun locksVerticalOnPureOrSlantedVerticalDrag() {
        assertEquals(PagerSwipeIntent.Result.VERTICAL, PagerSwipeIntent(20f).add(0f, 25f))
        assertEquals(PagerSwipeIntent.Result.VERTICAL, PagerSwipeIntent(20f).add(15f, 35f))
    }

    @Test
    fun locksHorizontalImmediatelyOnFastOrAngledHorizontalSwipe() {
        assertEquals(PagerSwipeIntent.Result.HORIZONTAL, PagerSwipeIntent(20f).add(-50f, 25f))
        assertEquals(PagerSwipeIntent.Result.HORIZONTAL, PagerSwipeIntent(20f).add(50f, -25f))
    }

    @Test
    fun locksHorizontalAcrossMultipleSmallMoves() {
        val intent = PagerSwipeIntent(slop = 20f)
        assertEquals(PagerSwipeIntent.Result.WAIT, intent.add(-8f, 2f))
        assertEquals(PagerSwipeIntent.Result.HORIZONTAL, intent.add(-8f, 2f))
    }

    @Test
    fun resolvesTargetPageByVelocityAndDragThreshold() {
        // Fast left flick advances page, clamped at last page
        assertEquals(2, resolvePagerTargetPage(startPage = 1, pageCount = 3, totalDx = -40f, pageWidthPx = 1080f, velocityXPxPerSec = -600f))
        assertEquals(2, resolvePagerTargetPage(startPage = 2, pageCount = 3, totalDx = -40f, pageWidthPx = 1080f, velocityXPxPerSec = -600f))

        // Fast right flick goes back, clamped at 0
        assertEquals(0, resolvePagerTargetPage(startPage = 1, pageCount = 3, totalDx = 40f, pageWidthPx = 1080f, velocityXPxPerSec = 600f))
        assertEquals(0, resolvePagerTargetPage(startPage = 0, pageCount = 3, totalDx = 40f, pageWidthPx = 1080f, velocityXPxPerSec = 600f))

        // Slow drag past 25% page width switches page; under 25% snaps back
        assertEquals(1, resolvePagerTargetPage(startPage = 0, pageCount = 3, totalDx = -300f, pageWidthPx = 1080f, velocityXPxPerSec = -100f))
        assertEquals(0, resolvePagerTargetPage(startPage = 0, pageCount = 3, totalDx = -200f, pageWidthPx = 1080f, velocityXPxPerSec = -100f))
        assertEquals(0, resolvePagerTargetPage(startPage = 1, pageCount = 3, totalDx = 300f, pageWidthPx = 1080f, velocityXPxPerSec = 100f))
    }
}
