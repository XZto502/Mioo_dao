package com.mioo.dao.ui.screens.thread

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrependReadingAnchorTest {
    @Test fun anchorsTheFloorAtResponseTimeAfterFurtherDragging() {
        assertEquals(4 to 67, resolvePrependReadingAnchor(
            listOf("42" to -67, "43" to 200),
            listOf("11", "12", "41", "42", "43"), "1", false
        ))
    }

    @Test fun repeatedPromotionCannotSendTheViewportToTheNewPageTop() {
        assertEquals(4 to -80, resolvePrependReadingAnchor(
            listOf("9999999" to -10, "42" to 80),
            listOf("9999999", "11", "12", "42"), "1", false
        ))
    }

    @Test fun returningToPageOneAccountsForTheInsertedMainFloor() {
        val anchor = resolvePrependReadingAnchor(
            listOf("42" to -20), listOf("11", "42"), "1", true
        )!!
        assertEquals(2, readingLazyIndex(anchor.first, true))
        assertEquals(20, anchor.second)
    }

    @Test fun absentOrPromotionalOnlyViewportDoesNotInventAnAnchor() {
        assertNull(resolvePrependReadingAnchor(listOf("9999999" to 0), listOf("11"), "1", false))
        assertNull(resolvePrependReadingAnchor(listOf("gone" to 0), listOf("11"), "1", false))
    }
}
