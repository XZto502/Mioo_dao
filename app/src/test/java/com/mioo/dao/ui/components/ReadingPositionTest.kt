package com.mioo.dao.ui.components

import com.mioo.dao.ui.screens.thread.readingLazyIndex
import com.mioo.dao.ui.screens.thread.resolveReadingIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingPositionTest {
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
