package com.mioo.dao.utils

import com.mioo.dao.data.model.GithubAsset
import com.mioo.dao.data.model.GithubRelease
import org.junit.Assert.*
import org.junit.Test

class UpdateChannelTest {
    private fun release(version: String, preview: Boolean = false, draft: Boolean = false,
                        files: List<String> = listOf(if (preview) "app-preview.apk" else "app-release.apk")) =
        GithubRelease(version, version, null, "https://github.com/XZto502/Mioo_dao/releases/tag/$version",
            files.map { GithubAsset(it, "https://github.com/XZto502/Mioo_dao/releases/download/$version/$it", 1) }, preview, draft)

    @Test fun stableExcludesPreviewsAndBothChannelsExcludeDrafts() {
        val releases = listOf(release("v1.1.9", draft = true), release("v1.1.8-beta.1", true), release("v1.1.7"))
        assertEquals("v1.1.7", selectUpdateRelease(releases, UpdateChannel.STABLE, false)?.tagName)
        assertEquals("v1.1.8-beta.1", selectUpdateRelease(releases, UpdateChannel.BETA, false)?.tagName)
    }

    @Test fun firstEligibleReleaseWinsEvenIfLaterVersionIsHigher() {
        val releases = listOf(release("v1.1.7-beta.2", true), release("v1.1.7-beta.10", true), release("v1.1.8"))
        assertEquals("v1.1.7-beta.2", selectUpdateRelease(releases, UpdateChannel.BETA, false)?.tagName)
        assertEquals("v1.1.8", selectUpdateRelease(releases, UpdateChannel.STABLE, false)?.tagName)
    }

    @Test fun stableGraduationReachesBetaSubscribersAndDoesNotDowngrade() {
        val selected = selectUpdateRelease(listOf(release("v1.1.7"), release("v1.1.7-beta.10", true)), UpdateChannel.BETA, false)!!
        assertTrue(isNewerVersion("1.1.7-beta.10", selected.tagName))
        assertFalse(isNewerVersion("1.1.8-beta.1", selected.tagName))
    }

    @Test fun packageAssetsStayIsolatedAndPreferred() {
        val releases = listOf(release("v1.1.7-beta.10", true, files = listOf("other.apk", "app-beta.apk", "app-preview.apk")))
        assertEquals(listOf("app-preview.apk"), selectUpdateRelease(releases, UpdateChannel.BETA, false)!!.assets!!.map { it.name })
        assertEquals(listOf("app-beta.apk"), selectUpdateRelease(releases, UpdateChannel.BETA, true)!!.assets!!.map { it.name })
        val stable = listOf(release("v1.1.7", files = listOf("app-beta.apk", "other.apk", "app-release.apk")))
        assertEquals(listOf("app-release.apk"), selectUpdateRelease(stable, UpdateChannel.STABLE, false)!!.assets!!.map { it.name })
    }

    @Test fun missingCompatibleApkDoesNotFallThroughToOlderRelease() {
        val releases = listOf(release("v1.1.8", files = listOf("app-beta.apk", "notes.txt")), release("v1.1.7"))
        assertTrue(selectUpdateRelease(releases, UpdateChannel.STABLE, false)!!.assets!!.isEmpty())
        assertEquals("v1.1.8", selectUpdateRelease(releases, UpdateChannel.STABLE, true)?.tagName)
        assertNull(selectUpdateRelease(listOf(release("v1.1.8"), release("v1.1.7", files = listOf("app-beta.apk"))), UpdateChannel.STABLE, true))
    }

    @Test fun invalidFirstEligibleVersionDoesNotFallThrough() {
        assertNull(selectUpdateRelease(listOf(release("not-a-version"), release("v1.1.8")), UpdateChannel.BETA, false))
        assertNull(selectUpdateRelease(emptyList(), UpdateChannel.BETA, false))
    }
}
