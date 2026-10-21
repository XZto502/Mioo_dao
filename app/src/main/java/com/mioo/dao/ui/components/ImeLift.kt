package com.mioo.dao.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity

/**
 * Composer inset: [imePadding] while the keyboard is open, otherwise [navigationBarsPadding].
 *
 * The two insets overlap (the keyboard covers the gesture pill), so they are not stacked.
 * Pair with activity `windowSoftInputMode="adjustResize"`. Do not also translate the bar.
 */
fun Modifier.imePaddingAndNavigationBars(): Modifier = composed {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    if (imeBottom > navBottom) imePadding() else navigationBarsPadding()
}

/** @see imePaddingAndNavigationBars */
fun Modifier.imeLiftOverNavigationBars(): Modifier = imePaddingAndNavigationBars()

fun Modifier.navBarContentPadding(): Modifier = this.navigationBarsPadding()
