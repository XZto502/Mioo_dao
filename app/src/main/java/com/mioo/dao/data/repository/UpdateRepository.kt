package com.mioo.dao.data.repository

import com.mioo.dao.data.api.GithubApiService
import com.mioo.dao.data.local.SettingsDataStore
import com.mioo.dao.data.model.GithubRelease
import com.mioo.dao.data.model.XdResponse
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
    fun checkLatestRelease(): Flow<XdResponse<GithubRelease>> = flow {
        if (!settingsDataStore.thirdPartyConsent.first().allowThirdPartyServices) {
            emit(XdResponse.Error(message = "第三方服务未启用"))
            return@flow
        }
        val channel = settingsDataStore.updateChannelFlow.first()
        try {
            val releases = githubApiService.getReleases(
                "https://api.github.com/repos/XZto502/Mioo_dao/releases?per_page=10")
            if (!settingsDataStore.thirdPartyConsent.first().allowThirdPartyServices) return@flow
            val release = com.mioo.dao.utils.selectUpdateRelease(releases, channel, com.mioo.dao.BuildConfig.BETA)
            if (release != null) emit(XdResponse.Success(release))
            else emit(XdResponse.Error(message = "暂无适用于当前安装包的通道版本"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(XdResponse.Error(message = "检查更新失败", throwable = e))
        }
    }.flowOn(Dispatchers.IO)
}