package com.mioo.dao.ui.components

import com.mioo.dao.ui.screens.thread.readingLazyIndex
import com.mioo.dao.ui.screens.thread.resolveReadingIndex
import com.mioo.dao.ui.screens.thread.shouldLoadPreviousAtEdge
import com.mioo.dao.ui.screens.thread.resolveWindowReadingIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingPositionTest {
    @Test fun jumpToShortLastPageStartsAtItsReplyWithinThePreparedWindow() {
        assertEquals(4, resolveWindowReadingIndex(null, "op",
            listOf("9999999", "last"), listOf("9999999", "a", "b", "last"), 0, 3))
    }

    @Test fun resumeUsesFloorIdentityAfterAdjacentPagesAndFiltersChangeIndexes() {
        val window = listOf("earlier", "a", "saved", "next")
        assertEquals(3, resolveWindowReadingIndex("saved", "op", listOf("a", "saved"), window, 19, 2))
        assertEquals(3, resolveWindowReadingIndex(null, "op", listOf("a", "saved"), window, 2, 2))
        assertEquals(0, resolveWindowReadingIndex("op", "op", listOf("a"), window, 0, 1))
        assertEquals(0, resolveWindowReadingIndex(null, "op", listOf("a"), window, 0, 1))
    }

    @Test fun shortThirdPageCanLoadEarlierRepliesWithoutAnyListMovement() {
        assertTrue(shouldLoadPreviousAtEdge(12f, true, false, 3, false, false))
        assertFalse(shouldLoadPreviousAtEdge(-12f, true, false, 3, false, false))
        assertFalse(shouldLoadPreviousAtEdge(12f, false, false, 3, false, false))
        assertFalse(shouldLoadPreviousAtEdge(12f, true, true, 3, false, false))
        assertFalse(shouldLoadPreviousAtEdge(12f, true, false, 1, false, false))
        assertFalse(shouldLoadPreviousAtEdge(12f, true, false, 3, true, false))
        assertFalse(shouldLoadPreviousAtEdge(12f, true, false, 3, false, true))
    }

    @Test fun laterPagesOmitTheMainPostExactlyOnce() {
        assertEquals(1, readingLazyIndex(1, true))
        assertEquals(0, readingLazyIndex(1, false))
        assertEquals(18, readingLazyIndex(19, false))
        assertEquals(0, readingLazyIndex(0, false))
    }

    @Test fun floorIdentitySurvivesFilteringOrEarlierInsertions() {
        assertEquals(2, resolveReadingIndex("b", "op", listOf("a", "b", "c"), 10))
        assertEquals(1, resolveReadingIndex("b", "op", listOf("b", "c"), 10))
        assertEquals(3, resolveReadingIndex("b", "op", listOf("new", "a", "b"), 1))
        assertEquals(0, resolveReadingIndex("op", "op", listOf("a"), 10))
        assertEquals(4, resolveReadingIndex("removed", "op", listOf("a"), 4))
    }
}
