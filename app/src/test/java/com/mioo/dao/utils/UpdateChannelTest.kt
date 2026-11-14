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

    @Test fun stableExcludesPreviewsAndDrafts() {
        val releases = listOf(release("v1.1.8-beta.1", true), release("v1.1.8", draft = true), release("v1.1.7"))
        assertEquals("v1.1.7", selectUpdateRelease(releases, UpdateChannel.STABLE, false)?.tagName)
    }

    @Test fun betaUsesVersionOrderingInsteadOfPublicationOrder() {
        val releases = listOf(release("v1.1.7-beta.2", true), release("v1.1.7-beta.10", true), release("v1.1.6"))
        assertEquals("v1.1.7-beta.10", selectUpdateRelease(releases, UpdateChannel.BETA, false)?.tagName)
    }

    @Test fun stableGraduationAlsoReachesBetaSubscribers() {
        val releases = listOf(release("v1.1.7-beta.10", true), release("v1.1.7"))
        assertEquals("v1.1.7", selectUpdateRelease(releases, UpdateChannel.BETA, false)?.tagName)
        assertFalse(isNewerVersion("1.1.8-beta.1", selectUpdateRelease(releases, UpdateChannel.STABLE, false)!!.tagName))
    }

    @Test fun separatePackageIsNeverOfferedToPublicTestingChannel() {
        val releases = listOf(release("v1.1.7-beta.10", true, files = listOf("app-beta.apk")),
            release("v1.1.7-beta.9", true, files = listOf("app-beta.apk", "app-preview.apk")))
        val selected = selectUpdateRelease(releases, UpdateChannel.BETA, false)!!
        assertEquals("v1.1.7-beta.9", selected.tagName)
        assertEquals(listOf("app-preview.apk"), selected.assets!!.map { it.name })
        assertEquals("v1.1.7-beta.10", selectUpdateRelease(releases, UpdateChannel.BETA, true)?.tagName)
        assertNull(selectUpdateRelease(releases, UpdateChannel.STABLE, true))
    }

    @Test fun invalidLabelsAndMissingCompatibleAssetsAreIgnored() {
        val releases = listOf(release("not-a-version"), release("v1.1.8", files = emptyList()))
        assertNull(selectUpdateRelease(releases, UpdateChannel.BETA, false))
    }
}
