package com.mioo.dao.ui.components

import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/**
 * Effective IME bottom offset in px: max(WindowInsets.ime, visible-frame fallback).
 *
 * Some OEM / emulator builds never dispatch IME insets to edge-to-edge windows
 * (source stays invisible with an empty frame), which silently breaks imePadding().
 * The fallback measures the keyboard with the classic getWindowVisibleDisplayFrame
 * trick, latched when it exceeds 25% of the screen so navigation-bar residue
 * never counts as a keyboard.
 *
 * Inside a Dialog window the fallback alone under-reports (~90px): the dialog's
 * own rootView is resized by adjustResize, and visibleFrame.bottom lags the real
 * keyboard top. So the listener ALSO samples the host Activity's decor view
 * (never resized, always reports the true keyboard top) and takes the max of
 * both measurements.
 */
@Composable
fun rememberEffectiveImeBottomPx(): Int {
    val view = LocalView.current
    val density = LocalDensity.current
    val insetsIme = WindowInsets.ime.getBottom(density)

    var fallback by remember(view) { mutableIntStateOf(0) }
    DisposableEffect(view) {
        // Host activity decor: unwrap ContextThemeWrapper chains (dialogs wrap).
        var ctx: android.content.Context? = view.context
        var activity: android.app.Activity? = null
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) {
                activity = ctx
                break
            }
            ctx = ctx.baseContext
        }
        val activityDecor = activity?.window?.decorView

        fun measure(root: android.view.View, screenH: Int): Int {
            val frame = Rect()
            root.getWindowVisibleDisplayFrame(frame)
            val covered = (screenH - frame.bottom).coerceAtLeast(0)
            return if (covered > screenH * 25 / 100) covered else 0
        }

        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val metrics = view.resources.displayMetrics
            val screenH = metrics.heightPixels
            if (screenH <= 0) return@OnGlobalLayoutListener
            val own = measure(view.rootView, screenH)
            val host = activityDecor?.let { measure(it, screenH) } ?: 0
            fallback = maxOf(own, host)
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        activityDecor?.viewTreeObserver?.addOnGlobalLayoutListener(listener)
        listener.onGlobalLayout()
        onDispose {
            val observer = view.viewTreeObserver
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
            activityDecor?.viewTreeObserver?.let { o ->
                if (o.isAlive) o.removeOnGlobalLayoutListener(listener)
            }
        }
    }

    return maxOf(insetsIme, fallback)
}

/**
 * Composer inset: keyboard-height bottom padding while the keyboard is open,
 * otherwise [navigationBarsPadding].
 *
 * The keyboard covers the gesture pill, so the two paddings are never stacked.
 * Uses [rememberEffectiveImeBottomPx] so the lift also works where IME insets
 * dispatch is broken. Pair with `windowSoftInputMode="adjustNothing"`.
 */
fun Modifier.imePaddingAndNavigationBars(): Modifier = composed {
    val density = LocalDensity.current
    val imeBottom = rememberEffectiveImeBottomPx()
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    if (imeBottom > navBottom) {
        Modifier.padding(bottom = with(density) { imeBottom.toDp() })
    } else {
        Modifier.navigationBarsPadding()
    }
}

/** @see imePaddingAndNavigationBars */
fun Modifier.imeLiftOverNavigationBars(): Modifier = imePaddingAndNavigationBars()

fun Modifier.navBarContentPadding(): Modifier = this.navigationBarsPadding()

/** Convenience: keyboard visible by effective height (ignores the gesture-pill band). */
@Composable
fun rememberImeVisible(): Boolean = rememberEffectiveImeBottomPx() > 0
