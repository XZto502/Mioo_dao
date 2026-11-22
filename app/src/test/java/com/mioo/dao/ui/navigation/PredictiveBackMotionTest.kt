package com.mioo.dao.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictiveBackMotionTest {
    @Test
    fun finishDurationScalesProportionallyAndClamps() {
        assertEquals(220, predictiveBackFinishDurationMs(1f))
        assertEquals(110, predictiveBackFinishDurationMs(0.5f))
        assertEquals(65, predictiveBackFinishDurationMs(0.1f))
        assertEquals(65, predictiveBackFinishDurationMs(0f))
        assertEquals(220, predictiveBackFinishDurationMs(1.5f))
    }

    @Test
    fun predictiveLayerProgressPreservesInterruptedStartProgress() {
        // Fresh predictive back from settled 1f
        assertEquals(1f, computePredictiveLayerProgress(1f, 0f), 0.001f)
        assertEquals(0.55f, computePredictiveLayerProgress(1f, 0.5f), 0.001f)
        assertEquals(0.1f, computePredictiveLayerProgress(1f, 1f), 0.001f)

        // Interrupted mid-enter at 0.4f: progress=0 must stay at 0.4f rather than jumping to 1f
        assertEquals(0.4f, computePredictiveLayerProgress(0.4f, 0f), 0.001f)
        val halfway = computePredictiveLayerProgress(0.4f, 0.5f)
        assertTrue(halfway in 0.21f..0.23f)
        assertEquals(0.04f, computePredictiveLayerProgress(0.4f, 1f), 0.001f)
    }
}
