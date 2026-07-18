package com.mioo.dao.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

/**
 * Smooth keyboard lift for bottom bars / composers.
 *
 * Layout only reserves the **navigation bar** inset (stable). Extra IME height is applied as
 * [graphicsLayer] translation so parents (Scaffold / LazyColumn) are **not** remeasured on every
 * IME animation frame — avoids the janky "whole screen jumps with the keyboard" feel.
 *
 * Pair with activity `windowSoftInputMode="adjustNothing"` (or dialog SOFT_INPUT_ADJUST_NOTHING)
 * so the system does not also resize the window.
 */
fun Modifier.imeLiftOverNavigationBars(): Modifier = composed {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    // Only the amount by which IME exceeds the nav bar should slide the bar up.
    val extraLiftPx = (imeBottom - navBottom).coerceAtLeast(0)
    val translation = remember(extraLiftPx) { -extraLiftPx.toFloat() }

    this
        .navigationBarsPadding()
        .graphicsLayer {
            translationY = translation
        }
}


