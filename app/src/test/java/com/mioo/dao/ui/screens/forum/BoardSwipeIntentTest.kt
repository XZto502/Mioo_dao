package com.mioo.dao.ui.screens.forum

import org.junit.Assert.assertEquals
import org.junit.Test

class BoardSwipeIntentTest {
    @Test fun jitterDoesNotOpenDrawer() {
        val intent = BoardSwipeIntent(10f)
        repeat(20) {
            assertEquals(BoardSwipeIntent.Result.WAIT, intent.add(2f, 1f))
            assertEquals(BoardSwipeIntent.Result.WAIT, intent.add(-2f, -1f))
        }
    }

    @Test fun clearRightDragOpensWithoutWaitingForListToBecomeIdle() {
        val intent = BoardSwipeIntent(10f)
        assertEquals(BoardSwipeIntent.Result.WAIT, intent.add(8f, 2f))
        assertEquals(BoardSwipeIntent.Result.OPEN, intent.add(12f, 3f))
    }

    @Test fun verticalAndLeftwardGesturesAreNotClaimed() {
        assertEquals(BoardSwipeIntent.Result.VERTICAL, BoardSwipeIntent(10f).add(12f, 18f))
        assertEquals(BoardSwipeIntent.Result.VERTICAL, BoardSwipeIntent(10f).add(-20f, 1f))
        assertEquals(BoardSwipeIntent.Result.WAIT, BoardSwipeIntent(10f).add(20f, 15f))
    }
}
