package com.mioo.dao.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Dialog / sheet windows do not inherit the activity's transparent bars.
 * Without this, those windows paint an opaque strip behind the gesture pill.
 */
/**
 * 宿主 Activity 系统栏临时覆盖标志（如看图器全屏看图时涂黑）。
 * 为 true 时抑制 installImmersiveBarGuard 与全局主题对 Activity 栏色的透明/浅色重置。
 */
@Volatile
var isActivitySystemBarsOverridden: Boolean = false

/**
 * 安全地从 [Context] 解包出宿主 [Activity]。
 */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * 看图器等全屏纯黑组件专用：在组件打开期间把宿主 Activity 的状态栏和导航栏强制涂黑、图标置亮，
 * 关闭时恢复原本的栏色与图标外观。
 * （Dialog 在部分机型上不覆盖 inset 区，直接给 Activity 涂黑才能彻底避免露底/浅色条带）。
 */
@Composable
fun BlackActivitySystemBarsEffect() {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(context, view) {
        val activity = context.findActivity() ?: view.context.findActivity()
        val window = activity?.window
        if (window == null) {
            return@DisposableEffect onDispose { }
        }

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        val originalStatusBarColor = window.statusBarColor
        val originalNavigationBarColor = window.navigationBarColor
        val originalLightStatusBars = insetsController.isAppearanceLightStatusBars
        val originalLightNavigationBars = insetsController.isAppearanceLightNavigationBars

        isActivitySystemBarsOverridden = true

        fun applyBlack() {
            window.statusBarColor = android.graphics.Color.BLACK
            window.navigationBarColor = android.graphics.Color.BLACK
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }

        applyBlack()

        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            applyBlack()
        }
        val decor = window.decorView
        decor.viewTreeObserver.addOnGlobalLayoutListener(listener)

        onDispose {
            if (decor.viewTreeObserver.isAlive) {
                decor.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
            isActivitySystemBarsOverridden = false
            window.statusBarColor = originalStatusBarColor
            window.navigationBarColor = originalNavigationBarColor
            insetsController.isAppearanceLightStatusBars = originalLightStatusBars
            insetsController.isAppearanceLightNavigationBars = originalLightNavigationBars
        }
    }
}

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
 * Compose hosts dialog content on the [DialogWindowProvider] itself, so
 * [LocalView]'s parent is the decor view and a single parent cast misses the window.
 * Walk up until the provider is found.
 */
fun View.findDialogWindow(): Window? {
    var current: View? = this
    while (current != null) {
        if (current is DialogWindowProvider) return current.window
        current = current.parent as? View
    }
    return null
}

/**
 * Call from inside a [androidx.compose.ui.window.Dialog] or AlertDialog slot
 * (title / text / content) so [LocalView] is the dialog window, not the activity.
 *
 * @param lightAppearance true draws dark status/nav icons. Image viewer passes false.
 */
@Composable
fun ImmersiveDialogEffect(lightAppearance: Boolean = !isSystemInDarkTheme()) {
    val view = LocalView.current
    DisposableEffect(view, lightAppearance) {
        val window = view.findDialogWindow()
        if (window == null) {
            onDispose { }
        } else {
            window.applyImmersiveSystemBars(lightAppearance)
            // Dialog theme and IME repaint an opaque nav bar on the next layout.
            val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
                val current = view.findDialogWindow() ?: return@OnGlobalLayoutListener
                current.reassertTransparentBars()
            }
            val decor = window.decorView
            decor.viewTreeObserver.addOnGlobalLayoutListener(listener)
            onDispose {
                val observer = decor.viewTreeObserver
                if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
            }
        }
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
private fun Window.reassertTransparentBars() {
    val transparent = android.graphics.Color.TRANSPARENT
    if (statusBarColor != transparent) statusBarColor = transparent
    if (navigationBarColor != transparent) navigationBarColor = transparent
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        if (isStatusBarContrastEnforced) isStatusBarContrastEnforced = false
        if (isNavigationBarContrastEnforced) isNavigationBarContrastEnforced = false
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        navigationBarDividerColor != transparent
    ) {
        navigationBarDividerColor = transparent
    }
}

fun Activity.installImmersiveBarGuard(): android.view.ViewTreeObserver.OnGlobalLayoutListener {
    val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
        if (isActivitySystemBarsOverridden) return@OnGlobalLayoutListener
        val window = window ?: return@OnGlobalLayoutListener
        window.reassertTransparentBars()
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
