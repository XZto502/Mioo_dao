package com.mioo.dao.utils

import org.junit.Assert.*
import org.junit.Test

class AppVersionTest {
    @Test fun betaBuildsAdvanceToStableWithoutDowngrading() {
        assertTrue(isNewerVersion("1.1.7-beta.1", "v1.1.7-beta.2"))
        assertTrue(isNewerVersion("1.1.7-beta.98", "1.1.7"))
        assertFalse(isNewerVersion("1.1.7", "1.1.7-beta.98"))
        assertFalse(isNewerVersion("1.1.7", "1.1.7"))
        assertTrue(isNewerVersion("1.1.7", "1.1.8-beta.1"))
    }

    @Test fun betaNumbersClampToReservedRange() {
        assertEquals(channelVersionCode("1.1.7-beta.1"), channelVersionCode("1.1.7-beta.0"))
        assertEquals(channelVersionCode("1.1.7-beta.1"), channelVersionCode("1.1.7-beta"))
        assertEquals(channelVersionCode("1.1.7-beta.98"), channelVersionCode("1.1.7-beta.99"))
        assertEquals(channelVersionCode("1.1.7-beta.98"), channelVersionCode("1.1.7-beta.999999999999999999999"))
        assertEquals(1_010_799, channelVersionCode("1.1.7"))
    }

    @Test fun formattingAndNonBetaSuffixesShareStableCode() {
        val stable = channelVersionCode("1.1.7")
        listOf(" v1.01.007 ", "V1.1.7+build.1", "1.1.7-rc.1", "1.1.7-hotfix").forEach {
            assertEquals(stable, channelVersionCode(it))
        }
        assertEquals(channelVersionCode("1.1.7-beta.1"), channelVersionCode("1.1.7-BETA1"))
    }

    @Test fun malformedAndOutOfRangeVersionsNeverTriggerUpdates() {
        listOf("", "latest", "1..7", "1.1.7-", "1.1.7-beta?", "1.1.7.1", "1.100.0", "1.0.100", "2101.0.0", "2100.0.0", "999999999999999999999.0.0").forEach { invalid ->
            assertNull(invalid, channelVersionCode(invalid))
            assertFalse(isNewerVersion("1.1.7", invalid))
            assertFalse(isNewerVersion(invalid, "1.1.8"))
        }
        assertEquals(2_099_999_999, channelVersionCode("2099.99.99"))
    }
}
