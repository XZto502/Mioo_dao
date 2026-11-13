package com.mioo.dao.utils

import org.junit.Assert.*
import org.junit.Test

class AppVersionTest {
    private fun newer(current: String, latest: String) {
        assertTrue("$latest should follow $current", isNewerVersion(current, latest))
        assertFalse("Must not downgrade $latest to $current", isNewerVersion(latest, current))
    }

    @Test fun orderedReleaseStagesAndHotfixes() {
        val versions = listOf("1.1.7-dev", "1.1.7-alpha", "1.1.7-alpha.2", "1.1.7-beta",
            "1.1.7-beta.2", "1.1.7-beta.10", "1.1.7-rc.1", "1.1.7", "1.1.7-hotfix", "1.1.7-hotfix.2")
        versions.forEachIndexed { index, version ->
            versions.drop(index + 1).forEach { newer(version, it) }
            assertFalse(isNewerVersion(version, version))
        }
    }

    @Test fun coreVersionAlwaysTakesPriorityOverSuffix() {
        newer("1.1.7-hotfix.99", "1.1.8-alpha.1")
        newer("1.1.9", "1.1.10-beta")
        newer("1.1.7", "1.1.7.1")
        newer("1.999999999999999999999", "1.1000000000000000000000")
    }

    @Test fun harmlessFormattingAndBuildMetadataDoNotProduceUpdates() {
        listOf(" v1.1.7-BETA.1 " to "1.1.7-beta1", "1.1.7" to "1.1.7.0",
            "1.1.7+build.1" to "1.1.7+build.2", "1.01.007" to "1.1.7").forEach { (a, b) ->
            assertFalse(isNewerVersion(a, b))
            assertFalse(isNewerVersion(b, a))
        }
    }

    @Test fun unknownPreviewLabelsHaveDeterministicNaturalNumberOrdering() {
        newer("1.1.7-preview.2", "1.1.7-preview.10")
        newer("1.1.7-preview.10", "1.1.7")
    }

    @Test fun malformedVersionsNeverTriggerUpdates() {
        listOf("", "latest", "1..7", "1.1.7-", "1.1.7-beta?", "garbage1.2.3").forEach { invalid ->
            assertFalse(isNewerVersion("1.1.7", invalid))
            assertFalse(isNewerVersion(invalid, "1.1.8"))
        }
    }
}
