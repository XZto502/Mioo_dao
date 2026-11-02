package com.mioo.dao.ui.components

import androidx.compose.ui.graphics.Color
import com.mioo.dao.utils.DiceRulePreset
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class QuietReadingTest {
    private val now = LocalDate.of(2026, 10, 30).atTime(12, 0)

    @Test fun naturalTimesCoverAllDateBuckets() {
        assertEquals("今天 09:03:59", formatReadingTime("2026-10-30(五)9:03:59", now))
        assertEquals("昨天 16:03:00", formatReadingTime("2026-10-29(四)16:03", now))
        assertEquals("01-12 16:03", formatReadingTime("2026-1-12 16:03:17", now))
        assertEquals("25-10-29 16:03", formatReadingTime("2025-10-29(三)16:03:17", now))
    }

    @Test fun yesterdayWorksAcrossYearBoundaryAndMalformedInputIsPreserved() {
        assertEquals("昨天 23:59:00", formatReadingTime("2025-12-31(三)23:59", LocalDate.of(2026, 1, 1).atTime(12, 0)))
        assertEquals("unknown", formatReadingTime("unknown", now))
        assertEquals("2026-99-99 12:00", formatReadingTime("2026-99-99 12:00", now))
        assertEquals("2026-02-30 12:00", formatReadingTime("2026-02-30 12:00", now))
    }

    @Test fun postConversionDoesNotDiscardServerSecondsBeforeFormatting() {
        val raw = "2026-10-29(四)16:03:17"
        assertEquals(raw, formatPostTime(" $raw "))
        assertEquals(raw, com.mioo.dao.data.model.Thread(id = 123, now = raw).toPostData().createdAt)
        assertEquals(raw, com.mioo.dao.data.model.Reply(id = 124, now = raw, userHash = "cookie", content = "reply").toPostData(false).createdAt)
    }

    @Test fun rollingSevenDaysUsesTheExactTimeBoundaryRatherThanCalendarWeeks() {
        assertEquals("10/23 12:00:01", formatReadingTime("2026-10-23(五)12:00:01", now))
        assertEquals("10-23 12:00", formatReadingTime("2026-10-23(五)12:00:00", now))
        assertEquals("10-23 11:59", formatReadingTime("2026-10-23(五)11:59:59", now))
        assertEquals("10/27 16:03:42", formatReadingTime("2026-10-27(二)16:03:42", now))
        assertEquals("今天 12:00:01", formatReadingTime("2026-10-30 12:00:01", now))
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val boundary = now.minusDays(7).atZone(zone).toInstant().toEpochMilli()
        assertEquals("10-23 12:00", formatReadingTimestamp(boundary, now, zone))
        assertEquals("10/23 12:00:01", formatReadingTimestamp(boundary + 1000, now, zone))
    }

    @Test fun recentPostsKeepSecondsAcrossTheNewYear() {
        val newYear = LocalDate.of(2026, 1, 4).atTime(12, 0)
        assertEquals("25-12-30 13:00:19", formatReadingTime("2025-12-30 13:00:19", newYear))
        assertEquals("25-12-28 12:00", formatReadingTime("2025-12-28 12:00:00", newYear))
    }

    @Test fun historyEpochAndServerStringUseTheSameSecondPrecision() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val time = java.time.LocalDateTime.of(2026, 10, 30, 9, 27, 15)
        assertEquals("今天 09:27:15", formatReadingTimestamp(time.atZone(zone).toInstant().toEpochMilli(), now, zone))
        assertEquals("今天 09:27:15", formatReadingTime("2026-10-30（五）09:27:15", now))
        assertEquals("2026-10-30 09:27:60", formatReadingTime("2026-10-30 09:27:60", now))
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
