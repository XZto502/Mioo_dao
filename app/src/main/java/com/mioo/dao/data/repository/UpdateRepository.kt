package com.mioo.dao.data.repository

import com.mioo.dao.data.api.GithubApiService
import com.mioo.dao.data.local.SettingsDataStore
import com.mioo.dao.data.model.GithubRelease
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.utils.UpdateChannel
import com.mioo.dao.utils.selectUpdateRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/** Owns consent-gated release lookup; forum data does not depend on GitHub. */
@Singleton
class UpdateRepository @Inject constructor(
    private val githubApiService: GithubApiService,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        internal const val RELEASES_API_URL =
            "https://api.github.com/repos/XZto502/Mioo_dao/releases?per_page=100"
        internal val RELEASE_MANIFEST_URLS = listOf(
            "https://ghproxy.net/https://raw.githubusercontent.com/XZto502/Mioo_dao/main/version.json",
            "https://ghfast.top/https://raw.githubusercontent.com/XZto502/Mioo_dao/main/version.json",
            "https://cdn.jsdelivr.net/gh/XZto502/Mioo_dao@main/version.json",
            "https://raw.githubusercontent.com/XZto502/Mioo_dao/main/version.json",
            "https://api.github.com/repos/XZto502/Mioo_dao/releases/latest"
        )
    }

    fun checkLatestRelease(): Flow<XdResponse<GithubRelease>> = flow {
        val result = resolveLatestRelease(
            channel = settingsDataStore.updateChannelFlow.first(),
            parallelBeta = com.mioo.dao.BuildConfig.BETA,
            isThirdPartyAllowed = { settingsDataStore.thirdPartyConsent.first().allowThirdPartyServices },
            fetchReleases = { githubApiService.getReleases(it) },
            fetchManifest = { githubApiService.getLatestRelease(it) }
        )
        emit(result)
    }.flowOn(Dispatchers.IO)
}

internal suspend fun resolveLatestRelease(
    channel: UpdateChannel,
    parallelBeta: Boolean,
    isThirdPartyAllowed: suspend () -> Boolean,
    fetchReleases: suspend (String) -> List<GithubRelease>,
    fetchManifest: suspend (String) -> GithubRelease
): XdResponse<GithubRelease> {
    if (!isThirdPartyAllowed()) {
        return XdResponse.Error(message = "第三方服务未启用")
    }
    var lastError: Exception? = null
    if (channel == UpdateChannel.BETA || parallelBeta) {
        try {
            val releases = fetchReleases(UpdateRepository.RELEASES_API_URL)
            if (!isThirdPartyAllowed()) {
                return XdResponse.Error(message = "第三方服务未启用")
            }
            val selected = selectUpdateRelease(releases, channel, parallelBeta)
            if (selected != null) {
                return XdResponse.Success(selected)
            }
            if (parallelBeta) {
                return XdResponse.Error(message = "此独立测试包暂无对应通道的更新，正式包不能覆盖此包")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e
            if (parallelBeta) {
                return XdResponse.Error(message = "检查测试版更新失败", throwable = e)
            }
        }
    }
    for (url in UpdateRepository.RELEASE_MANIFEST_URLS) {
        if (!isThirdPartyAllowed()) {
            return XdResponse.Error(message = "第三方服务未启用")
        }
        try {
            val manifest = fetchManifest(url)
            if (!isThirdPartyAllowed()) {
                return XdResponse.Error(message = "第三方服务未启用")
            }
            val compatible = selectUpdateRelease(listOf(manifest), UpdateChannel.STABLE, parallelBeta = false)
            if (compatible != null) {
                return XdResponse.Success(compatible)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e
        }
    }
    return XdResponse.Error(
        message = lastError?.localizedMessage ?: "检查更新失败",
        throwable = lastError
    )
}
