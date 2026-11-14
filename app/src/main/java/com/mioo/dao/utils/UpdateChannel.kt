package com.mioo.dao.utils

import com.mioo.dao.data.model.GithubRelease

enum class UpdateChannel { STABLE, BETA }

/** Only offer an APK built for the running package; list order is not version order. */
internal fun selectUpdateRelease(
    releases: List<GithubRelease>, channel: UpdateChannel, parallelBeta: Boolean
): GithubRelease? {
    var best: GithubRelease? = null
    for (release in releases) {
        if (release.draft || (channel == UpdateChannel.STABLE && release.prerelease)) continue
        if (!isNewerVersion("0.0.0", release.tagName)) continue
        val asset = release.assets.orEmpty().firstOrNull {
            if (parallelBeta) it.name == "app-beta.apk"
            else if (release.prerelease) it.name == "app-preview.apk"
            else it.name == "app-release.apk"
        } ?: continue
        if (best == null || isNewerVersion(best.tagName, release.tagName)) {
            best = release.copy(assets = listOf(asset))
        }
    }
    return best
}
