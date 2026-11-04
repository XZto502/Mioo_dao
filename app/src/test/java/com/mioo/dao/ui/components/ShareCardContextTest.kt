package com.mioo.dao.ui.components

import org.junit.Assert.*
import org.junit.Test

class ShareCardContextTest {
    private fun post(id: String, content: String = "body") =
        PostData(id = id, userId = "cookie", createdAt = "", content = content)

    @Test fun htmlAndPlainReferencesKeepSourceOrderAndDeduplicate() {
        assertEquals(listOf("22", "33"), shareContextIds(
            post("44", "<a href='/t/22'>&gt;&gt;No.22</a><br>>>33<br>>>No.22"), true))
    }

    @Test fun ordinaryReplyNeverImplicitlyIncludesMain() {
        val main = post("11")
        assertTrue(shareContextIds(post("22"), true).isEmpty())
        assertTrue(shareContextIds(main, true).isEmpty())
    }

    @Test fun disablingContextLeavesNoImplicitMainOrReferences() {
        assertTrue(shareContextIds(post("22", ">>No.33"), false).isEmpty())
        assertTrue(shareContextIds(post("22"), true).isEmpty())
    }
    @Test fun quoteEdgeBreaksCannotAddAnEmptyTailBelowTheBar() {
        assertEquals("first<br>last", trimShareQuote("  <br>\nfirst<br>last<br />&nbsp;\n "))
        assertEquals("<b>body</b>", trimShareQuote("\n<b>body</b>\n"))
    }

    @Test fun shareQuotesRemainBetweenTheirOriginalTextSegments() {
        val upstream = post("11")
        val reply = post("22", "before<br>>>No.11<br>after<br>>>No.11<br>end")
        assertEquals(listOf(PostContentBlock.Text("before"), PostContentBlock.Quote(upstream),
            PostContentBlock.Text("after"), PostContentBlock.Quote(upstream), PostContentBlock.Text("end")),
            shareContentBlocks(reply, mapOf("11" to upstream), true))
    }

    @Test fun missingAndDisabledReferencesRemainInTheOriginalBody() {
        val reply = post("22", "before<br>>>No.99<br>after")
        assertEquals(listOf(PostContentBlock.Text(reply.content)), shareContentBlocks(reply, emptyMap(), true))
        assertEquals(listOf(PostContentBlock.Text(reply.content)), shareContentBlocks(reply, mapOf("99" to post("99")), false))
    }

}
