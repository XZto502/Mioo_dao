package com.mioo.dao.notification

import com.mioo.dao.data.local.SubscriptionNotifyMode
import com.mioo.dao.data.local.SubscriptionWatchEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionPollPolicyTest {

    private val now = 1_700_000_000_000L
    private val hourMs = 3600_000L
    private val dayMs = 24L * hourMs

    private fun watch(
        id: String,
        idleMs: Long,
        nextCheckAt: Long = now - 1000L,
        lastCheckedAt: Long = 0L,
        notifyMode: Int = SubscriptionNotifyMode.ALL,
        lastRead: Int = 5,
        lastNotified: Int = 5
    ) = SubscriptionWatchEntity(
        threadId = id,
        poUserHash = "PO123",
        notifyMode = notifyMode,
        lastReadReplyCount = lastRead,
        lastNotifiedReplyCount = lastNotified,
        lastKnownReplyCount = lastNotified,
        lastActivityAt = now - idleMs,
        lastCheckedAt = lastCheckedAt,
        nextCheckAt = nextCheckAt
    )

    @Test
    fun classifiesAllFourTiersAndDelays() {
        assertEquals(WatchTier.HOT, SubscriptionPollPolicy.classifyTier(now - 2 * hourMs, now))
        assertEquals(WatchTier.WARM, SubscriptionPollPolicy.classifyTier(now - 2 * dayMs, now))
        assertEquals(WatchTier.COLD, SubscriptionPollPolicy.classifyTier(now - 10 * dayMs, now))
        assertEquals(WatchTier.DORMANT, SubscriptionPollPolicy.classifyTier(now - 35 * dayMs, now))
        assertEquals(WatchTier.DORMANT, SubscriptionPollPolicy.classifyTier(0L, now))

        assertEquals(60 * 60_000L, SubscriptionPollPolicy.nextCheckDelayMs(WatchTier.HOT, 60))
        assertEquals(15 * 60_000L, SubscriptionPollPolicy.nextCheckDelayMs(WatchTier.HOT, 10))
        assertEquals(15 * 60_000L, SubscriptionPollPolicy.nextCheckDelayMs(WatchTier.HOT, 15))
        assertEquals(4 * hourMs, SubscriptionPollPolicy.nextCheckDelayMs(WatchTier.WARM, 60))
        assertEquals(18 * hourMs, SubscriptionPollPolicy.nextCheckDelayMs(WatchTier.COLD, 60))
        assertEquals(48 * hourMs, SubscriptionPollPolicy.nextCheckDelayMs(WatchTier.DORMANT, 60))
    }

    @Test
    fun selectsBudgetedHotWarmAndRotatesDormantWhileExcludingMuted() {
        val list = listOf(
            watch("muted_hot", idleMs = 1 * hourMs, notifyMode = SubscriptionNotifyMode.MUTE),
            watch("future_hot", idleMs = 1 * hourMs, nextCheckAt = now + 10_000L),
            watch("hot1", idleMs = 1 * hourMs, nextCheckAt = now - 5000L),
            watch("hot2", idleMs = 2 * hourMs, nextCheckAt = now - 4000L),
            watch("warm1", idleMs = 2 * dayMs, nextCheckAt = now - 3000L),
            watch("warm2", idleMs = 3 * dayMs, nextCheckAt = now - 2000L),
            watch("warm3", idleMs = 4 * dayMs, nextCheckAt = now - 1000L),
            watch("dormant_recent", idleMs = 40 * dayMs, lastCheckedAt = now - 1000L),
            watch("dormant_oldest", idleMs = 50 * dayMs, lastCheckedAt = now - 9000L),
            watch("cold_mid", idleMs = 15 * dayMs, lastCheckedAt = now - 5000L)
        )

        val selected = SubscriptionPollPolicy.selectDueWatches(list, now)
        assertEquals(
            listOf("hot1", "hot2", "warm1", "warm2", "dormant_oldest", "cold_mid"),
            selected.map { it.threadId }
        )
    }

    @Test
    fun spillsUnusedSlotsWhenOneBucketIsSparse() {
        val dormantOnly = (1..5).map { idx ->
            watch("d$idx", idleMs = 45 * dayMs, lastCheckedAt = idx.toLong())
        }
        val selected = SubscriptionPollPolicy.selectDueWatches(dormantOnly, now)
        assertEquals(listOf("d1", "d2", "d3", "d4", "d5"), selected.map { it.threadId })
    }

    @Test
    fun quietHoursRespectsDefaultDisabledAndWrapAround() {
        assertFalse(SubscriptionPollPolicy.isInQuietHours(hourOfDay = 3, enabled = false))
        assertTrue(SubscriptionPollPolicy.isInQuietHours(hourOfDay = 0, enabled = true))
        assertTrue(SubscriptionPollPolicy.isInQuietHours(hourOfDay = 7, enabled = true))
        assertFalse(SubscriptionPollPolicy.isInQuietHours(hourOfDay = 8, enabled = true))
        assertTrue(SubscriptionPollPolicy.isInQuietHours(hourOfDay = 23, enabled = true, startHour = 23, endHour = 7))
        assertFalse(SubscriptionPollPolicy.isInQuietHours(hourOfDay = 12, enabled = true, startHour = 23, endHour = 7))
        assertTrue(SubscriptionPollPolicy.isInQuietHours(hourOfDay = 10, enabled = true, startHour = 9, endHour = 12))
        assertFalse(SubscriptionPollPolicy.isInQuietHours(hourOfDay = 12, enabled = true, startHour = 9, endHour = 12))
    }

    @Test
    fun duplicatePeriodicCheckDedupRespectsHalfInterval() {
        val last = now - 5 * 60_000L
        assertTrue(SubscriptionPollPolicy.shouldSkipDuplicatePeriodicCheck(last, now, intervalMinutes = 15))
        assertFalse(SubscriptionPollPolicy.shouldSkipDuplicatePeriodicCheck(now - 8 * 60_000L, now, intervalMinutes = 15))
        assertFalse(SubscriptionPollPolicy.shouldSkipDuplicatePeriodicCheck(0L, now, intervalMinutes = 15))
    }

    @Test
    fun cooldownScalesExponentially() {
        assertEquals(30 * 60_000L, SubscriptionPollPolicy.nextCooldownMs(1))
        assertEquals(2 * hourMs, SubscriptionPollPolicy.nextCooldownMs(2))
        assertEquals(6 * hourMs, SubscriptionPollPolicy.nextCooldownMs(3))
        assertEquals(6 * hourMs, SubscriptionPollPolicy.nextCooldownMs(5))
    }

    @Test
    fun notifyRuleHandlesAllPoOnlyAndMute() {
        val allWatch = watch("1", 1 * hourMs, notifyMode = SubscriptionNotifyMode.ALL, lastRead = 10, lastNotified = 10)
        assertTrue(SubscriptionPollPolicy.shouldNotifyUpdate(allWatch, newReplyCount = 11, hasNewPoReply = false))
        assertFalse(SubscriptionPollPolicy.shouldNotifyUpdate(allWatch, newReplyCount = 10, hasNewPoReply = true))

        val poWatch = watch("2", 1 * hourMs, notifyMode = SubscriptionNotifyMode.PO_ONLY, lastRead = 10, lastNotified = 10)
        assertFalse(SubscriptionPollPolicy.shouldNotifyUpdate(poWatch, newReplyCount = 12, hasNewPoReply = false))
        assertTrue(SubscriptionPollPolicy.shouldNotifyUpdate(poWatch, newReplyCount = 12, hasNewPoReply = true))

        val muteWatch = watch("3", 1 * hourMs, notifyMode = SubscriptionNotifyMode.MUTE, lastRead = 10, lastNotified = 10)
        assertFalse(SubscriptionPollPolicy.shouldNotifyUpdate(muteWatch, newReplyCount = 20, hasNewPoReply = true))
    }

    @Test
    fun extractsPlainPreviewAndCalculatesLastPage() {
        assertNull(SubscriptionPollPolicy.extractPlainPreview("   <br/>  "))
        assertEquals(
            ">>No.12345 hello & world",
            SubscriptionPollPolicy.extractPlainPreview("<font>&gt;&gt;No.12345</font><br/>hello &amp; world")
        )
        assertEquals(1, SubscriptionPollPolicy.calculateLastPage(1))
        assertEquals(1, SubscriptionPollPolicy.calculateLastPage(19))
        assertEquals(2, SubscriptionPollPolicy.calculateLastPage(20))
    }
}
