package com.mioo.dao.ui.components

import org.junit.Assert.*
import org.junit.Test

class ShareCardContextTest {
    private fun post(id: String, content: String = "body") =
        PostData(id = id, userId = "cookie", createdAt = "", content = content)

    @Test fun htmlAndPlainReferencesKeepSourceOrderAndDeduplicate() {
        assertEquals(listOf("22", "33"), shareContextIds(
            post("44", "<a href='/t/22'>&gt;&gt;No.22</a><br>>>33<br>>>No.22"), post("11"), true))
    }

    @Test fun ordinaryReplyIncludesMainButMainNeverQuotesItself() {
        val main = post("11")
        assertEquals(listOf("11"), shareContextIds(post("22"), main, true))
        assertTrue(shareContextIds(main, main, true).isEmpty())
    }

    @Test fun disablingContextLeavesNoImplicitMainOrReferences() {
        assertTrue(shareContextIds(post("22", ">>No.33"), post("11"), false).isEmpty())
        assertTrue(shareContextIds(post("22"), null, true).isEmpty())
    }
    @Test fun quoteEdgeBreaksCannotAddAnEmptyTailBelowTheBar() {
        assertEquals("first<br>last", trimShareQuote("  <br>\nfirst<br>last<br />&nbsp;\n "))
        assertEquals("<b>body</b>", trimShareQuote("\n<b>body</b>\n"))
    }

}
