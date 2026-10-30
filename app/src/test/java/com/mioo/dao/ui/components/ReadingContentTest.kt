package com.mioo.dao.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.mioo.dao.utils.DiceRulePreset
import org.junit.Assert.*
import org.junit.Test

class ReadingContentTest {
    private fun post(id: String) = PostData(id = id, userId = "cookie", createdAt = "", content = "quote $id")
    private fun parse(html: String) = parseHtmlToAnnotatedString(html, Color.Blue).annotated

    @Test fun resolvedReferencesFollowSourceOrderWithoutDuplicateLabels() {
        val blocks = ContentBlockSplitter.split(
            "<font color=\"blue\">&gt;&gt;No.22</font><br><br>正文<br>>>No.11",
            listOf(post("11"), post("22")))
        assertEquals(listOf(PostContentBlock.Quote(post("22")), PostContentBlock.Text("正文"),
            PostContentBlock.Quote(post("11"))), blocks)
    }

    @Test fun missingQuotesStayClickableAndCachedUnrelatedQuotesAreNotInserted() {
        val html = ">>No.12345<br>正文"
        assertEquals(listOf(PostContentBlock.Text(html)), ContentBlockSplitter.split(html, listOf(post("123"))))
        val annotated = parse(html)
        assertEquals("12345", annotated.getStringAnnotations("QUOTE_CLICK", 0, annotated.length).single().item)
    }

    @Test fun anchorWrappedQuotePreservesSurroundingContentAndUnresolvedReferences() {
        val blocks = ContentBlockSplitter.split(
            "before<br><a href=\"/t/22\">&gt;&gt;No.22</a><br>>>No.99<br>after", listOf(post("22")))
        assertEquals(listOf(PostContentBlock.Text("before"), PostContentBlock.Quote(post("22")),
            PostContentBlock.Text(">>No.99<br>after")), blocks)
    }

    @Test fun repeatedBreaksBecomeTwelveSpGapsWithoutMovingLinkAnnotations() {
        val annotated = parse("甲<br><br><br>乙\n\n\n<a href=\"https://example.com\">链接</a>")
            .withReadingParagraphGaps(1f)
        assertEquals("甲\n\n乙\n\n链接", annotated.text)
        assertEquals(listOf(12.sp, 12.sp), annotated.paragraphStyles.map { it.item.lineHeight })
        val link = annotated.getStringAnnotations("LINK_CLICK", 0, annotated.length).single()
        assertEquals("链接", annotated.text.substring(link.start, link.end))
        assertEquals(6, link.start)
    }

    @Test fun paragraphGapsRespectReadingScaleAndSingleBreaksStaySingle() {
        val annotated = parse("甲\n乙\n\n丙").withReadingParagraphGaps(1.25f)
        assertEquals("甲\n乙\n\n丙", annotated.text)
        assertEquals(15.sp, annotated.paragraphStyles.single().item.lineHeight)
    }

    @Test fun facesProtectSpacesAndPunctuationButOrdinaryParenthesesStayUnchanged() {
        listOf("( ﾟ∀。)", "(;´ヮ`)", "|∀ﾟ)", "( ´_ゝ`)").forEach { face ->
            val protected = protectReadingKaomoji(face)
            assertFalse(protected.contains(' '))
            assertTrue(protected.contains('\u2060'))
            assertEquals(face.replace(' ', '\u00A0'), protected.replace("\u2060", ""))
        }
        assertEquals("(hello world)", protectReadingKaomoji("(hello world)"))
        assertEquals("(这是正常说明。)", protectReadingKaomoji("(这是正常说明。)"))
    }

    @Test fun protectingFacesDoesNotInvalidateSubsequentQuoteOrDiceOffsets() {
        val parsed = parseHtmlToAnnotatedString("( ﾟ∀。) 2[0,9]<br><br>>>No.22", Color.Blue,
            dicePreset = DiceRulePreset.ANKO_HIGH_GOOD)
        val annotated = parsed.annotated.withReadingParagraphGaps(1f)
        assertEquals(1, parsed.diceBadges.size)
        val quote = annotated.getStringAnnotations("QUOTE_CLICK", 0, annotated.length).single()
        assertEquals(">>No.22", annotated.text.substring(quote.start, quote.end))
        assertEquals("22", quote.item)
        assertEquals(12.sp, annotated.paragraphStyles.single().item.lineHeight)
    }
}
