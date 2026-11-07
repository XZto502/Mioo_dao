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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import android.os.Build
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mioo.dao.data.model.ThemeMode
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.data.repository.SettingsRepository
import com.mioo.dao.data.repository.ThreadRepository
import com.mioo.dao.ui.navigation.MiooDaoNavGraph
import com.mioo.dao.ui.theme.ImmersiveDialogEffect
import com.mioo.dao.ui.theme.MiooDaoTheme
import com.mioo.dao.ui.theme.immersiveDialogProperties
import com.mioo.dao.ui.theme.installImmersiveBarGuard
import com.mioo.dao.ui.theme.isActivitySystemBarsOverridden
import com.mioo.dao.ui.theme.removeImmersiveBarGuard
import com.mioo.dao.utils.ThreadLinkParser
import com.mioo.dao.utils.UpdatePromptController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

val LocalThirdPartyServicesAllowed = staticCompositionLocalOf { false }

/** Lazy resolve of ThreadRepository so MainActivity cold create does not require it. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ThreadRepositoryEntryPoint {
    fun threadRepository(): ThreadRepository
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    // ThreadRepository is NOT field-injected: avoids pulling the full network/Room graph
    // into Activity creation. Update-check resolves it lazily after cold list settles.

    private var pendingThreadIdState = mutableStateOf<String?>(null)

    /** Clipboard-detected 8-digit thread id awaiting user confirmation. */
    private var clipboardThreadCandidate = mutableStateOf<String?>(null)

    /**
     * Full clipboard text we already asked about (accept or dismiss).
     * Only suppress while the clipboard content is unchanged.
     * 进程级：Activity 重建（深浅色切换/语言切换/进程恢复）后不再对同一段剪贴板重复弹窗。
     */
    companion object {
        @Volatile private var lastPromptedClipboardText: String? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Keep system splash until first board content paints (or timeout). */
    private val keepSplashOnScreen = AtomicBoolean(true)

    /** Re-clears a white navigation scrim after IME / OEM layout passes. */
    private var immersiveBarGuard: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Seamless launch: splash color matches Compose background; dismiss after first list frame.
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen.get() }

        // Transparent scrims so the gesture "小白条" / nav bar area shows app content.
        applyImmersiveSystemBars(darkTheme = false)

        super.onCreate(savedInstanceState)
        immersiveBarGuard = installImmersiveBarGuard()

        // Edge-to-edge + Compose-owned IME insets (manifest: adjustNothing).
        // Decor does not fit system windows so WindowInsets.ime animates smoothly into Compose.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        pendingThreadIdState.value = parsePendingThreadId(intent)

        // Never stick on splash if list is empty/error/slow network (prefer first-content callback)
        lifecycleScope.launch {
            delay(1200)
            keepSplashOnScreen.set(false)
        }

        // Clipboard after cold list is interactive — never compete with first paint.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                delay(2500)
                checkClipboardForThreadId()
                delay(1000)
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
                            lineHeightScale = it.lineHeightScale,
                            chineseFont = it.chineseFont,
                            englishFont = it.englishFont,
                            dataSaverEnabled = it.dataSaverEnabled,
                            glassEffectEnabled = it.glassEffectEnabled
                        )
                    }
                    .distinctUntilChanged()
            }.collectAsState(initial = ThemeConfig())

            val (themeMode, themeColor, fontSizeScale, lineHeightScale, chineseFont, englishFont, dataSaverEnabled, glassEffectEnabled) = themeConfig
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Keep system bars immersive when app theme changes (not only system UI mode).
            SideEffect {
                if (!isActivitySystemBarsOverridden) {
                    applyImmersiveSystemBars(darkTheme)
                }
            }

            val pendingThreadId by pendingThreadIdState
            val clipboardThreadId by clipboardThreadCandidate

            val consent by settingsRepository.thirdPartyConsent.collectAsState()
            val allowThirdParty = consent?.allowThirdPartyServices == true
            // Restarting the effect cancels the startup check if consent is withdrawn.
            LaunchedEffect(allowThirdParty) {
                if (!allowThirdParty) {
                    if (UpdatePromptController.release != null) UpdatePromptController.dismiss()
                    if (consent != null) com.mioo.dao.utils.UpdateDownloader.cancelActiveDownload(applicationContext)
                    return@LaunchedEffect
                }
                // 测试版不检查更新：下载到的正式包包名/签名不同，无法覆盖安装
                if (com.mioo.dao.BuildConfig.BETA) return@LaunchedEffect
                delay(10000)
                val threadRepo = runCatching {
                    EntryPointAccessors.fromApplication(
                        applicationContext,
                        ThreadRepositoryEntryPoint::class.java
                    ).threadRepository()
                }.getOrNull() ?: return@LaunchedEffect
                runCatching {
                    threadRepo.checkLatestRelease().collect { response ->
                        if (response is XdResponse.Success) {
                            val release = response.data
                            val currentVersion = getAppVersionName()
                            if (isNewerVersion(currentVersion, release.tagName)) {
                                // 剪贴板弹窗未处理完不叠加更新弹窗（两个模态会互相压盖）
                                while (clipboardThreadId != null) delay(800)
                                UpdatePromptController.offerAutomatic(release)
                            }
                        }
                    }
                }
            }

            MiooDaoTheme(
                darkTheme = darkTheme,
                themeColor = themeColor,
                fontSizeScale = fontSizeScale,
                lineHeightScale = lineHeightScale,
                chineseFont = chineseFont,
                englishFont = englishFont,
                dataSaverEnabled = dataSaverEnabled,
                glassEffectEnabled = glassEffectEnabled
            ) {
                CompositionLocalProvider(LocalThirdPartyServicesAllowed provides allowThirdParty) {
                    MiooDaoNavGraph(
                        pendingThreadId = pendingThreadId,
                        onPendingThreadConsumed = { pendingThreadIdState.value = null },
                        onColdStartContentReady = {
                            keepSplashOnScreen.set(false)
                        }
                    )
                }

                if (consent?.hasShownComplianceDialog == false) {
                    AlertDialog(
                        onDismissRequest = {},
                        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                        title = { Text("使用须知与第三方服务说明") },
                        text = {
                            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("本软件（喵岛）是由 Sanae-Koishi 开发的第三方客户端，不受 X 岛官方支持。使用时您的数据安全与数据泄露风险无法由 X 岛本身保证。",
                                    fontWeight = FontWeight.Bold)
                                Text("论坛内容来自 X 岛（www.nmbxd.com）。饼干、浏览历史、本地收藏和草稿保存在手机上。发帖时，饼干和发帖内容会发送给 X 岛。云端收藏通过 X 岛服务同步。")
                                Text("可选的第三方服务", fontWeight = FontWeight.Bold)
                                Text("更新检查和下载连接 GitHub、jsDelivr、ghproxy.net 或 ghfast.top 获取版本信息和安装包，不发送饼干、帖子内容或本地记录。")
                                Text("主动使用外部搜索时会将关键词交给所选搜索引擎。主动使用以图搜源时会将公开图片链接交给 SauceNAO、Ascii2d 或 Yandex。")
                                Text("网络兼容模式可能通过阿里 DNS 或腾讯 DNS 解析 X 岛 API 域名。解析服务只收到域名查询。")
                                Text("主动打开帖子或关于页面中的链接时，浏览器会访问对应网站。")
                                Text("拒绝后仍可浏览 X 岛、发帖、回帖和使用收藏与饼干功能。你可以在设置中更改选择。")
                            }
                        },
                        confirmButton = {
                            Button(onClick = { lifecycleScope.launch { settingsRepository.saveThirdPartyConsent(true) } }) {
                                Text("同意并继续")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { lifecycleScope.launch { settingsRepository.saveThirdPartyConsent(false) } }) {
                                Text("拒绝第三方服务")
                            }
                        }
                    )
                }

                clipboardThreadId?.takeIf { consent?.hasShownComplianceDialog == true }?.let { threadId ->
                    AlertDialog(
                        onDismissRequest = {
                            markClipboardPromptHandled()
                        },
                        properties = immersiveDialogProperties(),
                        title = {
                            ImmersiveDialogEffect()
                            Text("打开串？")
                        },
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

                UpdatePromptController.release?.takeIf { allowThirdParty }?.let { release ->
                    AlertDialog(
                        onDismissRequest = { UpdatePromptController.dismiss() },
                        properties = immersiveDialogProperties(),
                        title = {
                            ImmersiveDialogEffect()
                            Text("发现新版本 (${release.tagName})")
                        },
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
                                    if (settingsRepository.thirdPartyConsent.value?.allowThirdPartyServices != true) return@TextButton
                                    UpdatePromptController.startDownload()
                                    com.mioo.dao.utils.UpdateDownloader.downloadAndInstall(this@MainActivity, release)
                                }
                            ) {
                                Text("立即更新")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { UpdatePromptController.dismiss() }) {
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
        removeImmersiveBarGuard(immersiveBarGuard)
        immersiveBarGuard = null
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
    val lineHeightScale: Float = 1.0f,
    val chineseFont: String = "",
    val englishFont: String = "",
    val dataSaverEnabled: Boolean = false,
    val glassEffectEnabled: Boolean = true
)

/**
 * Fully transparent status / navigation bars so content and glow draw under the
 * gesture indicator (dark
 * ) and 3-button nav area.
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

    // 剥掉每段的 -beta / +build 等后缀再转数字，避免 "10-beta" 整段被丢弃导致误判
    val currentParts = cleanCurrent.split(".").mapNotNull { it.substringBefore('-').substringBefore('+').toIntOrNull() }
    val latestParts = cleanLatest.split(".").mapNotNull { it.substringBefore('-').substringBefore('+').toIntOrNull() }

    val minLength = minOf(currentParts.size, latestParts.size)
    for (i in 0 until minLength) {
        if (latestParts[i] > currentParts[i]) return true
        if (latestParts[i] < currentParts[i]) return false
    }
    return latestParts.size > currentParts.size
}
