package com.mioo.dao.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class KaomojiOrderTest {
    @Test fun dragCanMoveInBothDirections() {
        val order = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), moveKaomoji(order, "a", "c"))
        assertEquals(listOf("d", "a", "b", "c"), moveKaomoji(order, "d", "a"))
        assertEquals(order, moveKaomoji(order, "missing", "a"))
    }

    @Test fun updatesKeepUserOrderAndAppendNewItems() {
        assertEquals(listOf("c", "a", "b", "new"),
            restoreKaomojiOrder(listOf("c", "removed", "a", "a"), listOf("a", "b", "c", "new")))
    }
}
