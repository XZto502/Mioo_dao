package com.mioo.dao.ui.components

import org.junit.Assert.*
import org.junit.Test

class InlineQuoteLayoutTest {
    @Test fun resolvingQuoteKeepsItsSlotAndSurroundingBodyBlocks() {
        val html = "before<br>&gt;&gt;No.123<br>after<br>>>No.456<br>end"
        val pending = listOf(pendingInlineQuote("123"), pendingInlineQuote("456"))
        val loaded = pending.map { it.copy(content = "real quote", isQuotePlaceholder = false) }
        val before = ContentBlockSplitter.split(html, pending)
        val after = ContentBlockSplitter.split(html, loaded)
        assertEquals(before.map { it::class }, after.map { it::class })
        assertEquals(before.filterIsInstance<PostContentBlock.Text>(), after.filterIsInstance<PostContentBlock.Text>())
        assertEquals(listOf("123", "456"), before.filterIsInstance<PostContentBlock.Quote>().map { it.quote.id })
        assertTrue(before.filterIsInstance<PostContentBlock.Quote>().all { it.quote.isQuotePlaceholder })
        assertFalse(after.filterIsInstance<PostContentBlock.Quote>().any { it.quote.isQuotePlaceholder })
    }

    @Test fun repeatedReferencesReserveEveryOriginalPosition() {
        val blocks = ContentBlockSplitter.split(">>No.123<br>body<br>>>No.123", listOf(pendingInlineQuote("123")))
        assertEquals(2, blocks.filterIsInstance<PostContentBlock.Quote>().size)
        assertEquals(listOf(PostContentBlock.Text("body")), blocks.filterIsInstance<PostContentBlock.Text>())
    }
}
