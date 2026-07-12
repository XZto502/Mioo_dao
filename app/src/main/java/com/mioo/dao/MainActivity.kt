package com.mioo.dao

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mioo.dao.data.model.GithubRelease
import com.mioo.dao.data.model.ThemeMode
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.data.repository.SettingsRepository
import com.mioo.dao.data.repository.ThreadRepository
import com.mioo.dao.ui.navigation.MiooDaoNavGraph
import com.mioo.dao.ui.theme.MiooDaoTheme
import com.mioo.dao.utils.ThreadLinkParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var threadRepository: ThreadRepository

    private var pendingThreadIdState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)
        pendingThreadIdState.value = ThreadLinkParser.parseThreadId(intent)

        setContent {
            val themeConfig by remember {
                settingsRepository.settings
                    .map { Triple(it.themeMode, it.themeColor, it.fontSizeScale) }
                    .distinctUntilChanged()
            }.collectAsState(initial = Triple(ThemeMode.SYSTEM, "dynamic", 1.0f))

            val (themeMode, themeColor, fontSizeScale) = themeConfig
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            var showUpdateDialog by remember { mutableStateOf<GithubRelease?>(null) }
            val pendingThreadId by pendingThreadIdState

            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(5000)
                threadRepository.checkLatestRelease().collect { response ->
                    if (response is XdResponse.Success) {
                        val release = response.data
                        val currentVersion = getAppVersionName()
                        if (isNewerVersion(currentVersion, release.tagName)) {
                            showUpdateDialog = release
                        }
                    }
                }
            }

            MiooDaoTheme(
                darkTheme = darkTheme,
                themeColor = themeColor,
                fontSizeScale = fontSizeScale
            ) {
                MiooDaoNavGraph(
                    pendingThreadId = pendingThreadId,
                    onPendingThreadConsumed = { pendingThreadIdState.value = null }
                )

                showUpdateDialog?.let { release ->
                    AlertDialog(
                        onDismissRequest = { showUpdateDialog = null },
                        title = { Text("发现新版本 (${release.tagName})") },
                        text = {
                            Column {
                                if (!release.name.isNullOrBlank()) {
                                    Text(
                                        text = release.name,
                                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                if (!release.body.isNullOrBlank()) {
                                    Text(
                                        text = release.body,
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                                    )
                                } else {
                                    Text("没有提供更新说明。")
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    com.mioo.dao.utils.UpdateDownloader.downloadAndInstall(this@MainActivity, release)
                                    showUpdateDialog = null
                                }
                            ) {
                                Text("立即更新")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUpdateDialog = null }) {
                                Text("以后再说")
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingThreadIdState.value = ThreadLinkParser.parseThreadId(intent)
    }
}

private fun getAppVersionName(): String {
    return com.mioo.dao.BuildConfig.VERSION_NAME
}

private fun isNewerVersion(currentVersion: String, latestVersion: String): Boolean {
    val cleanCurrent = currentVersion.trim().lowercase().removePrefix("v")
    val cleanLatest = latestVersion.trim().lowercase().removePrefix("v")
    if (cleanCurrent == cleanLatest) return false

    val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
    val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }

    val minLength = minOf(currentParts.size, latestParts.size)
    for (i in 0 until minLength) {
        if (latestParts[i] > currentParts[i]) return true
        if (latestParts[i] < currentParts[i]) return false
    }
    return latestParts.size > currentParts.size
}
