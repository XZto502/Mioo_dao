package com.mioo.dao.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineTransferMeterTest {
    @Test
    fun firstPageContributesToSpeedAndEstimate() {
        var elapsed = 0L
        val samples = mutableListOf<OfflineTransfer>()
        val meter = OfflineTransferMeter(nowMs = { elapsed }) { samples.add(it) }
        meter.start("下载正文", 0)

        elapsed = 2_000
        meter.setTotal(3)
        meter.received(8_000)
        meter.completeUnit()

        assertEquals(4_000L, samples.last().bytesPerSecond)
        assertEquals(4L, samples.last().secondsRemaining)
    }

    @Test
    fun switchingToImagesResetsThePreviousStageEstimate() {
        var elapsed = 0L
        val samples = mutableListOf<OfflineTransfer>()
        val meter = OfflineTransferMeter(nowMs = { elapsed }) { samples.add(it) }
        meter.start("下载正文", 2)

        elapsed = 2_000
        meter.received(8_000)
        meter.completeUnit()
        meter.start("下载图片", 10)

        assertEquals(0, samples.last().completed)
        assertEquals(0L, samples.last().bytesPerSecond)
        assertNull(samples.last().secondsRemaining)
    }
}
