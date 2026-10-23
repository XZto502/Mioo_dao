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

/**
 * Effective IME bottom offset in px: max(WindowInsets.ime, visible-frame fallback).
 *
 * Some OEM / emulator builds never dispatch IME insets to edge-to-edge windows
 * (source stays invisible with an empty frame), which silently breaks imePadding().
 * The fallback measures the keyboard with the classic getWindowVisibleDisplayFrame
 * trick: root height minus visible-frame bottom, latched when it exceeds 15% of
 * the root height so the navigation bar alone never counts as a keyboard.
 */
@Composable
fun rememberEffectiveImeBottomPx(): Int {
    val view = LocalView.current
    val density = LocalDensity.current
    val insetsIme = WindowInsets.ime.getBottom(density)

    var fallback by remember(view) { mutableIntStateOf(0) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val frame = Rect()
            view.getWindowVisibleDisplayFrame(frame)
            val root = view.rootView
            val rootHeight = root.height
            if (rootHeight > 0) {
                val covered = (rootHeight - frame.bottom).coerceAtLeast(0)
                fallback = if (covered > rootHeight * 15 / 100) covered else 0
            }
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        listener.onGlobalLayout()
        onDispose {
            val observer = view.viewTreeObserver
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
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
