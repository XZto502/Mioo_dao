package com.mioo.dao.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ReadingTimeTest {

    @Test
    fun formatsRecentAndHistoricServerTimestampsConsistentlyWithCache() {
        val now = LocalDateTime.of(2026, 11, 20, 14, 30, 0)
        val rawToday = "2026-11-20(五)14:15:22"
        val rawYesterday = "2026-11-19(四)09:05:10"
        val rawOlderSameYear = "2026-08-01(六)21:40:00"
        val rawPrevYear = "2025-12-31(三)23:59:59"

        prewarmReadingTimes(listOf(rawToday, rawYesterday, rawOlderSameYear, rawPrevYear))

        assertEquals("今天 14:15:22", formatReadingTime(rawToday, now))
        assertEquals("昨天 09:05:10", formatReadingTime(rawYesterday, now))
        assertEquals("08-01 21:40", formatReadingTime(rawOlderSameYear, now))
        assertEquals("25-12-31 23:59", formatReadingTime(rawPrevYear, now))
        // Re-evaluating with a different `now` still computes relative label accurately from cached LocalDateTime
        val nextDay = LocalDateTime.of(2026, 11, 21, 8, 0, 0)
        assertEquals("昨天 14:15:22", formatReadingTime(rawToday, nextDay))
    }

    @Test
    fun returnsInvalidTimestampsVerbatim() {
        val now = LocalDateTime.of(2026, 11, 20, 14, 30, 0)
        assertEquals("invalid-date", formatReadingTime("invalid-date", now))
        assertEquals("", formatReadingTime("", now))
    }
}
