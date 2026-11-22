package com.mioo.dao.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveListMotionTest {

    @Test
    fun slowReleaseSettlesNearFingerWhileNormalAndReverseFlicksKeepMomentum() {
        val density = 3f
        // Very slow release (<= 60 dp/s) settles gently.
        assertEquals(0.15f * (40f * density), scaleResponsiveFlingVelocity(40f * density, density), 0.1f)
        // Medium/quick reverse swipe (250 dp/s) keeps most of its velocity instead of dying.
        val quickReverse = scaleResponsiveFlingVelocity(-250f * density, density)
        assertTrue(quickReverse < -250f * density * 0.90f)
        // Fast fling retains responsive boost capped at 16000 px/s.
        assertEquals(16000f, scaleResponsiveFlingVelocity(20000f, density), 0.01f)
        assertEquals(-16000f, scaleResponsiveFlingVelocity(-20000f, density), 0.01f)
    }

    @Test
    fun velocityThresholdPreventsSubPixelTailSteppingAcrossRefreshRates() {
        assertEquals(126f, responsiveFlingVelocityThresholdPx(density = 3.25f, refreshRateHz = 120f), 0.01f)
        assertEquals(94.5f, responsiveFlingVelocityThresholdPx(density = 2.0f, refreshRateHz = 90f), 0.01f)
        assertEquals(63f, responsiveFlingVelocityThresholdPx(density = 2.0f, refreshRateHz = 60f), 0.01f)
        assertEquals(72f, responsiveFlingVelocityThresholdPx(density = 6.0f, refreshRateHz = 60f), 0.01f)
    }
}
