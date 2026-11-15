package com.mioo.dao.utils

import com.mioo.dao.data.model.GithubRelease

enum class UpdateChannel { STABLE, BETA }

/** GitHub list order is authoritative; keep independent beta APKs isolated. */
internal fun selectUpdateRelease(
    releases: List<GithubRelease>, channel: UpdateChannel, parallelBeta: Boolean
): GithubRelease? {
    val release = releases.firstOrNull {
        !it.draft && (channel == UpdateChannel.BETA || !it.prerelease)
    } ?: return null
    if (channelVersionCode(release.tagName) == null) return null
    val apks = release.assets.orEmpty().filter { it.name.endsWith(".apk", ignoreCase = true) }
    val compatible = if (parallelBeta) apks.filter { it.name == "app-beta.apk" }
        else apks.filter { it.name != "app-beta.apk" }
    val preferredName = if (parallelBeta) "app-beta.apk" else if (release.prerelease) "app-preview.apk" else "app-release.apk"
    val asset = compatible.firstOrNull { it.name == preferredName } ?: compatible.firstOrNull()
    if (parallelBeta && asset == null) return null
    return release.copy(assets = listOfNotNull(asset))
}