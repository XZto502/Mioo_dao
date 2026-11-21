package com.mioo.dao.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import org.junit.Assert.*
import org.junit.Test

class HtmlPreviewTest {
    @Test fun compactPreviewRetainsLinkAndSpoilerRangesAfterTrimming() {
        val source = buildAnnotatedString {
            append("  \n甲\n\n\n")
            pushStringAnnotation("LINK_CLICK", "https://example.com")
            append("链接")
            pop()
            append("\n\n")
            pushStringAnnotation("SPOILER", "0")
            append("隐藏")
            pop()
            append("\n  ")
        }
        val result = prepareHtmlPreview(source, compact = true, trim = true)
        assertEquals("甲\n链接\n隐藏", result.text)
        for ((tag, expected) in listOf("LINK_CLICK" to "链接", "SPOILER" to "隐藏")) {
            val annotation = result.getStringAnnotations(tag, 0, result.length).single()
            assertEquals(expected, result.text.substring(annotation.start, annotation.end))
        }
        assertEquals(source, prepareHtmlPreview(source, compact = false, trim = false))
    }

    @Test fun singleBreakPreviewReusesTextAndEmptyPreviewStaysEmpty() {
        val source = AnnotatedString("甲\n乙")
        assertSame(source, prepareHtmlPreview(source, compact = true, trim = false))
        assertEquals("", prepareHtmlPreview(AnnotatedString(" \n\t"), true, true).text)
    }

    @Test fun htmlParseCachePrewarmSupportsConcurrentReadsAndVariants() {
        val color = androidx.compose.ui.graphics.Color(0xFF336699)
        val samples = (0 until 40).map { "line1-$it<br><br><br>line2-$it &gt;&gt;123456" }
        HtmlParseCache.prewarm(samples, color, darkTheme = false, compactBlankLines = true)
        for (i in samples.indices) {
            val parsed = HtmlParseCache.getOrParse(samples[i], color, darkTheme = false)
            assertTrue(parsed.annotated.text.contains("line1-$i"))
            val compact = HtmlParseCache.displayText(
                samples[i], color, darkTheme = false, dicePreset = null, compact = true, trim = false
            )
            assertEquals("line1-$i\nline2-$i >>123456", compact.text)
        }
    }
}
