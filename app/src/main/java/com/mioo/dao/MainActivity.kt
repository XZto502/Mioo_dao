package com.mioo.dao

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import android.os.Build
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mioo.dao.data.model.GithubRelease
import com.mioo.dao.data.model.ThemeMode
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.data.repository.SettingsRepository
import com.mioo.dao.data.repository.ThreadRepository
import com.mioo.dao.ui.navigation.MiooDaoNavGraph
import com.mioo.dao.ui.theme.MiooDaoTheme
import com.mioo.dao.utils.ThreadLinkParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var threadRepository: ThreadRepository

    private var pendingThreadIdState = mutableStateOf<String?>(null)

    /** Clipboard-detected 8-digit thread id awaiting user confirmation. */
    private var clipboardThreadCandidate = mutableStateOf<String?>(null)

    /**
     * Full clipboard text we already asked about (accept or dismiss).
     * Only suppress while the clipboard content is unchanged.
     */
    private var lastPromptedClipboardText: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent scrims so the gesture "小白条" / nav bar area shows app content.
        applyImmersiveSystemBars(darkTheme = false)

        super.onCreate(savedInstanceState)

        // Edge-to-edge + Compose-owned IME insets (manifest: adjustNothing).
        // Decor does not fit system windows so WindowInsets.ime animates smoothly into Compose.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        pendingThreadIdState.value = parsePendingThreadId(intent)

        // Re-check clipboard each time we are resumed and interactive.
        // Android only reliably allows clipboard reads while focused; a short delay helps.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                delay(250)
                checkClipboardForThreadId()
                // Second pass: some OEMs populate the clip a bit later
                delay(400)
                checkClipboardForThreadId()
            }
        }

        setContent {
            val themeConfig by remember {
                settingsRepository.settings
                    .map {
                        ThemeConfig(
                            themeMode = it.themeMode,
                            themeColor = it.themeColor,
                            fontSizeScale = it.fontSizeScale,
                            glassEffectEnabled = it.glassEffectEnabled
                        )
                    }
                    .distinctUntilChanged()
            }.collectAsState(initial = ThemeConfig())

            val (themeMode, themeColor, fontSizeScale, glassEffectEnabled) = themeConfig
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Keep system bars immersive when app theme changes (not only system UI mode).
            SideEffect {
                applyImmersiveSystemBars(darkTheme)
            }

            var showUpdateDialog by remember { mutableStateOf<GithubRelease?>(null) }
            val pendingThreadId by pendingThreadIdState
            val clipboardThreadId by clipboardThreadCandidate

            LaunchedEffect(Unit) {
                delay(5000)
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
                fontSizeScale = fontSizeScale,
                glassEffectEnabled = glassEffectEnabled
            ) {
                MiooDaoNavGraph(
                    pendingThreadId = pendingThreadId,
                    onPendingThreadConsumed = { pendingThreadIdState.value = null }
                )

                clipboardThreadId?.let { threadId ->
                    AlertDialog(
                        onDismissRequest = {
                            markClipboardPromptHandled()
                        },
                        title = { Text("打开串？") },
                        text = {
                            Text("剪贴板中检测到串号 No.$threadId，是否跳转打开？")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    markClipboardPromptHandled()
                                    pendingThreadIdState.value = threadId
                                }
                            ) {
                                Text("打开")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { markClipboardPromptHandled() }) {
                                Text("忽略")
                            }
                        }
                    )
                }

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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Most reliable moment to read the clipboard on modern Android
            mainHandler.post { checkClipboardForThreadId() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingThreadIdState.value = parsePendingThreadId(intent)
    }

    private fun parsePendingThreadId(intent: Intent?): String? {
        if (intent == null) return null
        // Notification tap (X-island subscription / favorite updates)
        intent.getStringExtra(com.mioo.dao.notification.SubscriptionNotifier.EXTRA_THREAD_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return ThreadLinkParser.parseThreadId(intent)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun markClipboardPromptHandled() {
        lastPromptedClipboardText = readClipboardText()
        clipboardThreadCandidate.value = null
    }

    private fun checkClipboardForThreadId() {
        if (isFinishing || isDestroyed) return
        // Already showing a prompt
        if (clipboardThreadCandidate.value != null) return
        // Don't interrupt an in-flight deep-link / share navigation
        if (pendingThreadIdState.value != null) return
        // Need focus — Android blocks clipboard reads otherwise (returns empty / null)
        if (!hasWindowFocus()) return

        val text = readClipboardText() ?: return
        if (text.isBlank()) return
        // Same clip content already handled
        if (text == lastPromptedClipboardText) return

        val threadId = ThreadLinkParser.parseEightDigitThreadId(text) ?: return
        clipboardThreadCandidate.value = threadId
    }

    private fun readClipboardText(): String? {
        return try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
            // Avoid hasPrimaryClip() early-return quirks on some OEMs; go straight to primaryClip
            val clip = cm.primaryClip ?: return null
            if (clip.itemCount <= 0) return null
            val item = clip.getItemAt(0)
            // Prefer plain text; fall back to coerceToText (handles HTML / URI labels)
            val plain = item.text?.toString()
            if (!plain.isNullOrBlank()) return plain
            item.coerceToText(this)?.toString()
        } catch (_: Exception) {
            null
        }
    }
}

private data class ThemeConfig(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeColor: String = "dynamic",
    val fontSizeScale: Float = 1.0f,
    val glassEffectEnabled: Boolean = true
)

/**
 * Fully transparent status / navigation bars so content and glow draw under the
 * gesture indicator (小白条) and 3-button nav area.
 */
private fun ComponentActivity.applyImmersiveSystemBars(darkTheme: Boolean) {
    val transparent = android.graphics.Color.TRANSPARENT
    enableEdgeToEdge(
        statusBarStyle = if (darkTheme) {
            SystemBarStyle.dark(transparent)
        } else {
            SystemBarStyle.light(transparent, transparent)
        },
        navigationBarStyle = if (darkTheme) {
            // Transparent dark bar — no opaque scrim behind the gesture pill.
            SystemBarStyle.dark(transparent)
        } else {
            SystemBarStyle.light(transparent, transparent)
        }
    )

    window.statusBarColor = transparent
    window.navigationBarColor = transparent
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.navigationBarDividerColor = transparent
    }
    WindowCompat.setDecorFitsSystemWindows(window, false)
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
