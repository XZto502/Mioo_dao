package com.mioo.dao.data.repository

import com.mioo.dao.data.model.GithubAsset
import com.mioo.dao.data.model.GithubRelease
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.utils.UpdateChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UpdateRepositoryTest {

    private fun release(
        tag: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
        assetNames: List<String> = listOf(if (prerelease) "app-preview.apk" else "app-release.apk")
    ) = GithubRelease(
        tagName = tag,
        name = tag,
        body = null,
        htmlUrl = "https://github.com/XZto502/Mioo_dao/releases/tag/$tag",
        assets = assetNames.map {
            GithubAsset(
                name = it,
                browserDownloadUrl = "https://github.com/XZto502/Mioo_dao/releases/download/$tag/$it",
                size = 100L
            )
        },
        prerelease = prerelease,
        draft = draft
    )

    @Test
    fun rejectsWithoutNetworkWhenThirdPartyDisabled() = runBlocking {
        var called = false
        val result = resolveLatestRelease(
            channel = UpdateChannel.STABLE,
            parallelBeta = false,
            isThirdPartyAllowed = { false },
            fetchReleases = {
                called = true
                emptyList()
            },
            fetchManifest = {
                called = true
                release("v1.1.7")
            }
        )
        assertFalse(called)
        assertTrue(result is XdResponse.Error)
        assertEquals("第三方服务未启用", (result as XdResponse.Error).message)
    }

    @Test
    fun stableChannelUsesVersionJsonMirrorsWithFallbackAndSkipsReleasesList() = runBlocking {
        var releasesCalled = false
        val requestedManifests = mutableListOf<String>()
        val result = resolveLatestRelease(
            channel = UpdateChannel.STABLE,
            parallelBeta = false,
            isThirdPartyAllowed = { true },
            fetchReleases = {
                releasesCalled = true
                emptyList()
            },
            fetchManifest = { url ->
                requestedManifests.add(url)
                if (requestedManifests.size == 1) throw IOException("mirror 1 timeout")
                release("v1.1.7", assetNames = listOf("app-release.apk"))
            }
        )
        assertFalse(releasesCalled)
        assertEquals(UpdateRepository.RELEASE_MANIFEST_URLS.take(2), requestedManifests)
        assertTrue(result is XdResponse.Success)
        val success = result as XdResponse.Success
        assertEquals("v1.1.7", success.data.tagName)
        assertEquals("app-release.apk", success.data.assets?.single()?.name)
    }

    @Test
    fun betaChannelOnReleasePackageUsesPerPage100AndSelectsPreviewOrGraduatedStable() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val previewResult = resolveLatestRelease(
            channel = UpdateChannel.BETA,
            parallelBeta = false,
            isThirdPartyAllowed = { true },
            fetchReleases = { url ->
                requestedUrls.add(url)
                listOf(release("v1.1.8-beta.1", prerelease = true, assetNames = listOf("app-preview.apk")))
            },
            fetchManifest = { error("manifest should not be called") }
        )
        assertEquals(listOf(UpdateRepository.RELEASES_API_URL), requestedUrls)
        assertTrue(UpdateRepository.RELEASES_API_URL.contains("per_page=100"))
        assertTrue(previewResult is XdResponse.Success)
        assertEquals("app-preview.apk", (previewResult as XdResponse.Success).data.assets?.single()?.name)

        val graduatedResult = resolveLatestRelease(
            channel = UpdateChannel.BETA,
            parallelBeta = false,
            isThirdPartyAllowed = { true },
            fetchReleases = {
                listOf(
                    release("v1.1.7", prerelease = false, assetNames = listOf("app-release.apk")),
                    release("v1.1.7-beta.40", prerelease = true, assetNames = listOf("app-preview.apk"))
                )
            },
            fetchManifest = { error("manifest should not be called") }
        )
        assertTrue(graduatedResult is XdResponse.Success)
        assertEquals("v1.1.7", (graduatedResult as XdResponse.Success).data.tagName)
        assertEquals("app-release.apk", graduatedResult.data.assets?.single()?.name)
    }

    @Test
    fun betaChannelOnReleasePackageFallsBackToVersionJsonMirrorsWhenGithubApiFails() = runBlocking {
        val requestedManifests = mutableListOf<String>()
        val result = resolveLatestRelease(
            channel = UpdateChannel.BETA,
            parallelBeta = false,
            isThirdPartyAllowed = { true },
            fetchReleases = { throw IOException("rate limited") },
            fetchManifest = { url ->
                requestedManifests.add(url)
                release("v1.1.7", assetNames = listOf("app-release.apk"))
            }
        )
        assertEquals(listOf(UpdateRepository.RELEASE_MANIFEST_URLS.first()), requestedManifests)
        assertTrue(result is XdResponse.Success)
        assertEquals("v1.1.7", (result as XdResponse.Success).data.tagName)
    }

    @Test
    fun parallelBetaPackageIsolatesToBetaApkAndNeverFallsBackToStableManifest() = runBlocking {
        var manifestCalled = false
        val betaHit = resolveLatestRelease(
            channel = UpdateChannel.BETA,
            parallelBeta = true,
            isThirdPartyAllowed = { true },
            fetchReleases = {
                listOf(release("v1.1.7-beta.40", prerelease = true, assetNames = listOf("app-preview.apk", "app-beta.apk")))
            },
            fetchManifest = {
                manifestCalled = true
                release("v1.1.7")
            }
        )
        assertFalse(manifestCalled)
        assertTrue(betaHit is XdResponse.Success)
        assertEquals("app-beta.apk", (betaHit as XdResponse.Success).data.assets?.single()?.name)

        val stableOnlyResult = resolveLatestRelease(
            channel = UpdateChannel.BETA,
            parallelBeta = true,
            isThirdPartyAllowed = { true },
            fetchReleases = {
                listOf(release("v1.1.7", prerelease = false, assetNames = listOf("app-release.apk")))
            },
            fetchManifest = {
                manifestCalled = true
                release("v1.1.7")
            }
        )
        assertFalse(manifestCalled)
        assertTrue(stableOnlyResult is XdResponse.Error)
    }

    @Test
    fun parallelBetaPackageReturnsErrorWhenGithubApiFailsWithoutManifestFallback() = runBlocking {
        var manifestCalled = false
        val result = resolveLatestRelease(
            channel = UpdateChannel.BETA,
            parallelBeta = true,
            isThirdPartyAllowed = { true },
            fetchReleases = { throw IOException("network error") },
            fetchManifest = {
                manifestCalled = true
                release("v1.1.7")
            }
        )
        assertFalse(manifestCalled)
        assertTrue(result is XdResponse.Error)
        assertEquals("检查测试版更新失败", (result as XdResponse.Error).message)
    }
}
