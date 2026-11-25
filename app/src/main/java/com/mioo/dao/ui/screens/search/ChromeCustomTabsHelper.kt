package com.mioo.dao.ui.screens.search

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mioo.dao.data.model.XdWebSearch

private val CHROME_CUSTOM_TAB_PACKAGES = listOf(
    "com.android.chrome",
    "com.chrome.beta",
    "com.chrome.dev",
    "com.chrome.canary"
)

@Stable
internal class CustomTabsWarmController(
    val packageName: String?,
    internal val getSession: () -> CustomTabsSession?
)

internal fun findCustomTabsPackage(context: Context): String? {
    // Prefer real Chrome packages even if another browser is the system default.
    CustomTabsClient.getPackageName(context, CHROME_CUSTOM_TAB_PACKAGES, true)?.let { return it }
    return CustomTabsClient.getPackageName(context, emptyList(), false)
}

internal fun engineOriginUrl(engine: XdWebSearch.Engine): String = when (engine) {
    XdWebSearch.Engine.GOOGLE -> "https://www.google.com/"
    XdWebSearch.Engine.BING -> "https://www.bing.com/"
    XdWebSearch.Engine.DUCKDUCKGO -> "https://duckduckgo.com/"
}

@Composable
internal fun rememberCustomTabsWarmController(
    enabled: Boolean,
    preconnectOrigin: String?
): CustomTabsWarmController {
    val context = LocalContext.current
    val packageName = remember(context, enabled) {
        if (enabled) findCustomTabsPackage(context) else null
    }
    var session by remember { mutableStateOf<CustomTabsSession?>(null) }

    DisposableEffect(context, enabled, packageName) {
        val pkg = packageName
        if (!enabled || pkg == null) {
            session = null
            return@DisposableEffect onDispose {}
        }
        var bound = false
        var activeClient: CustomTabsClient? = null
        val connection = object : CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
                activeClient = client
                runCatching { client.warmup(0L) }
                val newSession = runCatching { client.newSession(null) }.getOrNull()
                session = newSession
                if (!preconnectOrigin.isNullOrBlank()) {
                    runCatching { newSession?.mayLaunchUrl(Uri.parse(preconnectOrigin), null, null) }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                activeClient = null
                session = null
            }
        }
        bound = runCatching {
            CustomTabsClient.bindCustomTabsService(context, pkg, connection)
        }.getOrDefault(false)
        onDispose {
            session = null
            activeClient = null
            if (bound) {
                runCatching { context.unbindService(connection) }
            }
        }
    }

    LaunchedEffect(session, preconnectOrigin) {
        val activeSession = session ?: return@LaunchedEffect
        val origin = preconnectOrigin ?: return@LaunchedEffect
        runCatching { activeSession.mayLaunchUrl(Uri.parse(origin), null, null) }
    }

    return remember(packageName) {
        CustomTabsWarmController(
            packageName = packageName,
            getSession = { session }
        )
    }
}

internal fun launchCustomTabSearch(
    context: Context,
    url: String,
    toolbarColorArgb: Int,
    isDarkTheme: Boolean,
    session: CustomTabsSession? = null,
    preferredPackage: String? = findCustomTabsPackage(context)
): Boolean {
    if (preferredPackage == null) return false
    return runCatching {
        val colorScheme = if (isDarkTheme) {
            CustomTabsIntent.COLOR_SCHEME_DARK
        } else {
            CustomTabsIntent.COLOR_SCHEME_LIGHT
        }
        val colorParams = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(toolbarColorArgb)
            .setNavigationBarColor(toolbarColorArgb)
            .build()
        val customTabsIntent = CustomTabsIntent.Builder(session)
            .setDefaultColorSchemeParams(colorParams)
            .setColorScheme(colorScheme)
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .setBookmarksButtonEnabled(false)
            .setDownloadButtonEnabled(false)
            .setSendToExternalDefaultHandlerEnabled(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .setInstantAppsEnabled(false)
            .build()
        customTabsIntent.intent.setPackage(preferredPackage)
        customTabsIntent.launchUrl(context, Uri.parse(url))
        true
    }.getOrDefault(false)
}

internal fun areXIslandLinksApproved(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val manager = context.getSystemService(DomainVerificationManager::class.java) ?: return false
    val state = runCatching { manager.getDomainVerificationUserState(context.packageName) }.getOrNull()
        ?: return false
    if (!state.isLinkHandlingAllowed) return false
    val hosts = listOf("www.nmbxd1.com", "nmbxd1.com", "www.nmbxd.com", "nmbxd.com")
    return hosts.any { host ->
        val hostState = state.hostToStateMap[host]
        hostState == DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
            hostState == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
    }
}

internal fun openAppDefaultLinksSettings(context: Context) {
    val pkgUri = Uri.parse("package:${context.packageName}")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val opened = runCatching {
            context.startActivity(Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkgUri))
            true
        }.getOrDefault(false)
        if (opened) return
    }
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
    }
}
