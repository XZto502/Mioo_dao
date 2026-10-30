package com.mioo.dao.ui.components

import androidx.compose.ui.graphics.Color
import com.mioo.dao.utils.DiceRulePreset
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class QuietReadingTest {
    private val today = LocalDate.of(2026, 10, 30)

    @Test fun naturalTimesCoverAllDateBuckets() {
        assertEquals("今天 09:03", formatReadingTime("2026-10-30(五)9:03:59", today))
        assertEquals("昨天 16:03", formatReadingTime("2026-10-29(四)16:03", today))
        assertEquals("01/12 16:03", formatReadingTime("2026-1-12 16:03", today))
        assertEquals("25/10/29", formatReadingTime("2025-10-29(三)16:03", today))
    }

    @Test fun yesterdayWorksAcrossYearBoundaryAndMalformedInputIsPreserved() {
        assertEquals("昨天 23:59", formatReadingTime("2025-12-31(三)23:59", LocalDate.of(2026, 1, 1)))
        assertEquals("unknown", formatReadingTime("unknown", today))
        assertEquals("2026-99-99 12:00", formatReadingTime("2026-99-99 12:00", today))
        assertEquals("2026-02-30 12:00", formatReadingTime("2026-02-30 12:00", today))
    }

    @Test fun opticalSpacesAreThinAndIdempotent() {
        val result = withReadingOpticalSpacing("中文Android系统有3个API接口")
        assertEquals("中文\u2009Android\u2009系统有\u20093\u2009个\u2009API\u2009接口", result)
        assertEquals(result, withReadingOpticalSpacing(result))
    }

    @Test fun urlsReferencesAndFacesRemainIntact() {
        val raw = "https://example.com/中文abc?q=中文123 >>123 No.456 ( ﾟ∀。)"
        assertEquals(raw, withReadingOpticalSpacing(raw))
    }

    @Test fun opticalSpacingPreservesLinkQuoteAndDiceAnnotations() {
        val parsed = parseHtmlToAnnotatedString(
            "中文Android<a href=\"https://example.com/中文123\">中文API</a>>>No.22 2[0,9]中文3楼",
            Color.Blue, dicePreset = DiceRulePreset.ANKO_HIGH_GOOD)
        val text = parsed.annotated
        val link = text.getStringAnnotations("LINK_CLICK", 0, text.length).single()
        assertEquals("https://example.com/中文123", link.item)
        assertEquals("中文API", text.text.substring(link.start, link.end))
        val quote = text.getStringAnnotations("QUOTE_CLICK", 0, text.length).single()
        assertEquals(">>No.22", text.text.substring(quote.start, quote.end))
        assertEquals(1, parsed.diceBadges.size)
        assertTrue(text.text.contains("中文\u20093\u2009楼"))
    }
}
