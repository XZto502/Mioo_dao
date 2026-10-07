package com.mioo.dao.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mioo.dao.data.model.GithubRelease

/**
 * One update dialog for the whole app.
 *
 * The startup check and the About-screen "检查更新" button used to each open
 * their own "发现新版本" dialog, so dismissing one left the other on screen.
 */
object UpdatePromptController {
    var release by mutableStateOf<GithubRelease?>(null)
        private set

    private var autoConsumed = false
    private var downloadStarted = false

    fun offerAutomatic(release: GithubRelease) {
        if (autoConsumed || downloadStarted || this.release != null) return
        autoConsumed = true
        this.release = release
    }

    /** @return false when a download is already running and no dialog should open. */
    fun offerManual(release: GithubRelease): Boolean {
        if (downloadStarted) return false
        autoConsumed = true
        this.release = release
        return true
    }

    fun dismiss() {
        autoConsumed = true
        release = null
    }

    fun startDownload() {
        downloadStarted = true
        autoConsumed = true
        release = null
    }
}
