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

        // Minute-precision checks (same-day and overnight)
        assertFalse(
            SubscriptionPollPolicy.isInQuietHours(
                hourOfDay = 9, minuteOfHour = 29, enabled = true,
                startHour = 9, startMinute = 30, endHour = 12, endMinute = 15
            )
        )
        assertTrue(
            SubscriptionPollPolicy.isInQuietHours(
                hourOfDay = 9, minuteOfHour = 30, enabled = true,
                startHour = 9, startMinute = 30, endHour = 12, endMinute = 15
            )
        )
        assertFalse(
            SubscriptionPollPolicy.isInQuietHours(
                hourOfDay = 12, minuteOfHour = 15, enabled = true,
                startHour = 9, startMinute = 30, endHour = 12, endMinute = 15
            )
        )
        assertTrue(
            SubscriptionPollPolicy.isInQuietHours(
                hourOfDay = 23, minuteOfHour = 45, enabled = true,
                startHour = 23, startMinute = 30, endHour = 7, endMinute = 45
            )
        )
        assertTrue(
            SubscriptionPollPolicy.isInQuietHours(
                hourOfDay = 7, minuteOfHour = 44, enabled = true,
                startHour = 23, startMinute = 30, endHour = 7, endMinute = 45
            )
        )
        assertFalse(
            SubscriptionPollPolicy.isInQuietHours(
                hourOfDay = 7, minuteOfHour = 45, enabled = true,
                startHour = 23, startMinute = 30, endHour = 7, endMinute = 45
            )
        )
        assertFalse(
            SubscriptionPollPolicy.isInQuietHours(
                hourOfDay = 8, minuteOfHour = 0, enabled = true,
                startHour = 8, startMinute = 0, endHour = 8, endMinute = 0
            )
        )
    }

    @Test
    fun validatesCheckIntervalAndRejectsOutOfRangeInputs() {
        assertEquals(15, SubscriptionPollPolicy.validateIntervalMinutes("15").minutes)
        assertNull(SubscriptionPollPolicy.validateIntervalMinutes("15").error)
        assertEquals(60, SubscriptionPollPolicy.validateIntervalMinutes(" 60 ").minutes)
        assertEquals(180, SubscriptionPollPolicy.validateIntervalMinutes("180").minutes)

        assertNull(SubscriptionPollPolicy.validateIntervalMinutes("").minutes)
        assertNull(SubscriptionPollPolicy.validateIntervalMinutes("0").minutes)
        assertNull(SubscriptionPollPolicy.validateIntervalMinutes("14").minutes)
        assertNull(SubscriptionPollPolicy.validateIntervalMinutes("181").minutes)
        assertNull(SubscriptionPollPolicy.validateIntervalMinutes("999").minutes)
        assertNull(SubscriptionPollPolicy.validateIntervalMinutes("-5").minutes)
        assertNull(SubscriptionPollPolicy.validateIntervalMinutes("abc").minutes)
    }

    @Test
    fun validatesManualQuietHoursAndRejectsNonsenseValues() {
        val overnight = SubscriptionPollPolicy.validateQuietHoursInput("23", "30", "07", "45")
        assertNull(overnight.error)
        assertEquals(23, overnight.startHour)
        assertEquals(30, overnight.startMinute)
        assertEquals(7, overnight.endHour)
        assertEquals(45, overnight.endMinute)

        val singleDigits = SubscriptionPollPolicy.validateQuietHoursInput("7", "5", "22", "0")
        assertNull(singleDigits.error)
        assertEquals(7, singleDigits.startHour)
        assertEquals(5, singleDigits.startMinute)

        val empty = SubscriptionPollPolicy.validateQuietHoursInput("", "00", "08", "00")
        assertTrue(QuietTimeField.START_HOUR in empty.invalidFields)

        val badHour = SubscriptionPollPolicy.validateQuietHoursInput("24", "00", "08", "00")
        assertTrue(QuietTimeField.START_HOUR in badHour.invalidFields)

        val badEndHour = SubscriptionPollPolicy.validateQuietHoursInput("00", "00", "99", "00")
        assertTrue(QuietTimeField.END_HOUR in badEndHour.invalidFields)

        val badMinute = SubscriptionPollPolicy.validateQuietHoursInput("00", "60", "08", "99")
        assertTrue(QuietTimeField.START_MINUTE in badMinute.invalidFields)
        assertTrue(QuietTimeField.END_MINUTE in badMinute.invalidFields)

        val nonDigit = SubscriptionPollPolicy.validateQuietHoursInput("ab", "00", "08", "00")
        assertTrue(QuietTimeField.START_HOUR in nonDigit.invalidFields)

        val equal = SubscriptionPollPolicy.validateQuietHoursInput("08", "00", "08", "00")
        assertTrue(QuietTimeField.RANGE in equal.invalidFields)
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
