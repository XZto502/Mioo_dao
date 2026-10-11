package com.mioo.dao.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Flat page fill. Scaffolds are transparent, so a slide would otherwise show
 * the previous page through the gaps. This is a solid color, not a frost.
 */
@Composable
internal fun PageBackdrop(
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        propagateMinConstraints = true
    ) {
        content()
    }
}
