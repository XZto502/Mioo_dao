package com.mioo.dao.ui.screens.feed

import org.junit.Assert.*
import org.junit.Test

class FeedPageAppendTest {
    private data class Item(val id: Int, val content: String)

    @Test fun largeFolderOnlyConvertsNewPageItemsAndKeepsExistingOrder() {
        val original = (1..10000).map { Item(it, "old $it") }
        val page = (9990..10020).map { Item(it, "new $it") }
        val added = newFeedPageItems(original, page) { it.id }
        var converted = 0
        val display = original + added.map { converted++; it }
        assertEquals(20, converted)
        assertEquals((1..10020).toList(), display.map { it.id })
        original.indices.forEach { assertSame(original[it], display[it]) }
        assertEquals("new 10001", display[10000].content)
    }

    @Test fun duplicateAndEmptyPagesKeepFirstOccurrenceSemantics() {
        val original = listOf(Item(1, "original"))
        val page = listOf(Item(1, "replacement"), Item(2, "first"), Item(2, "second"))
        assertEquals(listOf(Item(2, "first")), newFeedPageItems(original, page) { it.id })
        assertTrue(newFeedPageItems(original, emptyList()) { it.id }.isEmpty())
        assertTrue(newFeedPageItems(original, original) { it.id }.isEmpty())
    }

    @Test fun sequentialPagesRetainAllRecordsBeyondOneThousand() {
        var all = emptyList<Item>()
        repeat(100) { page ->
            val fresh = (page * 25 until (page + 1) * 25).map { Item(it, "$it") }
            all = all + newFeedPageItems(all, fresh) { it.id }
        }
        assertEquals((0 until 2500).toList(), all.map { it.id })
        assertEquals(2500, all.map { it.id }.toSet().size)
    }
}
