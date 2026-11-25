package com.mioo.dao.ui.components

import org.junit.Assert.*
import org.junit.Test

class InlineQuoteLayoutTest {
    @Test fun longMixedContentPreservesReferenceOrderAndExclusionBoundaries() {
        val expected = mutableListOf<String>()
        val html = buildString {
            repeat(100) { index ->
                val id = (20000000 + index).toString()
                append("[h]11223344 >>No.567[/h] ")
                append("<a href=\"https://example.com/87654321\">87654321</a> ")
                append("<span data-id=\"12345678\">正文</span> ")
                append("https://example.com/33445566 ")
                append("$id<br>>>No.$id<br>")
                expected.add(id)
                expected.add(id)
            }
        }
        assertEquals(expected, ContentBlockSplitter.quoteIds(html))
        val quotes = expected.distinct().map { pendingInlineQuote(it) }
        val blocks = ContentBlockSplitter.split(html, quotes)
        assertEquals(expected, blocks.filterIsInstance<PostContentBlock.Quote>().map { it.quote.id })
    }

    @Test fun htmlSpoilersAndAdjacentLinksDoNotHideFollowingReferences() {
        val html = "<span class=\"h\">12345678 >>No.10</span>" +
            "22334455 <a href=\"/t/33445566\">33445566</a> 44556677 >>No.99"
        assertEquals(listOf("22334455", "44556677", "99"), ContentBlockSplitter.quoteIds(html))
    }
    @Test fun bareThreadIdsResolveButUrlsAttributesAndSpoilersDoNot() {
        val html = "正文 69729581<br>>>No.123<br>https://example.com/12345678 " +
            "<a href=\"https://example.com/87654321\">链接</a> [h]11223344[/h]"
        assertEquals(listOf("69729581", "123"), ContentBlockSplitter.quoteIds(html))
        val blocks = ContentBlockSplitter.split(html, listOf(pendingInlineQuote("69729581"), pendingInlineQuote("123")))
        assertEquals(listOf("69729581", "123"), blocks.filterIsInstance<PostContentBlock.Quote>().map { it.quote.id })
        assertTrue(blocks.filterIsInstance<PostContentBlock.Text>().any { it.html.contains("12345678") })
    }

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
