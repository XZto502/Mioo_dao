package com.mioo.dao.ui.theme

import android.app.Activity
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Dialog / sheet windows do not inherit the activity's transparent bars.
 * Without this, those windows paint an opaque strip behind the gesture pill.
 */
fun immersiveDialogProperties(
    usePlatformDefaultWidth: Boolean = true,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true
): DialogProperties = DialogProperties(
    usePlatformDefaultWidth = usePlatformDefaultWidth,
    decorFitsSystemWindows = false,
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside
)

/**
 * Call from inside a [androidx.compose.ui.window.Dialog] or AlertDialog slot
 * (title / text / content) so [LocalView] is the dialog window, not the activity.
 *
 * @param lightAppearance true draws dark status/nav icons. Image viewer passes false.
 */
@Composable
fun ImmersiveDialogEffect(lightAppearance: Boolean = !isSystemInDarkTheme()) {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.applyImmersiveSystemBars(lightAppearance)
    }
}

fun Window.applyImmersiveSystemBars(lightAppearance: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    val transparent = android.graphics.Color.TRANSPARENT
    statusBarColor = transparent
    navigationBarColor = transparent
    addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isStatusBarContrastEnforced = false
        isNavigationBarContrastEnforced = false
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        navigationBarDividerColor = transparent
    }
    WindowCompat.getInsetsController(this, decorView).apply {
        isAppearanceLightStatusBars = lightAppearance
        isAppearanceLightNavigationBars = lightAppearance
    }
}

/**
 * IME and some OEM skins repaint an opaque navigation bar on the next layout.
 * Re-assert transparent bars without touching inset dispatch (Compose owns that).
 */
fun Activity.installImmersiveBarGuard(): android.view.ViewTreeObserver.OnGlobalLayoutListener {
    val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
        val window = window ?: return@OnGlobalLayoutListener
        val transparent = android.graphics.Color.TRANSPARENT
        if (window.statusBarColor != transparent) window.statusBarColor = transparent
        if (window.navigationBarColor != transparent) window.navigationBarColor = transparent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (window.isStatusBarContrastEnforced) window.isStatusBarContrastEnforced = false
            if (window.isNavigationBarContrastEnforced) window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            window.navigationBarDividerColor != transparent
        ) {
            window.navigationBarDividerColor = transparent
        }
    }
    window.decorView.viewTreeObserver.addOnGlobalLayoutListener(listener)
    return listener
}

fun Activity.removeImmersiveBarGuard(
    listener: android.view.ViewTreeObserver.OnGlobalLayoutListener?
) {
    if (listener == null) return
    val observer = window.decorView.viewTreeObserver
    if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
}
