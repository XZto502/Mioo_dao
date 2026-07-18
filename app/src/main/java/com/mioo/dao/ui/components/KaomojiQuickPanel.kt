package com.mioo.dao.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 颜文字快捷面板：Lazy 网格，仅组合可见项。
 */
@Composable
fun KaomojiQuickPanel(
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier,
    heightDp: Int = 220
) {
    val items = remember { KAOMOJI_ITEMS }
    LazyVerticalGrid(
        columns = GridCells.Fixed(KAOMOJI_PER_ROW),
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.Top
    ) {
        items(
            items = items,
            key = { it.raw.hashCode() },
            contentType = { "kaomoji" }
        ) { item ->
            TextButton(
                onClick = { onInsert(item.raw) },
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
            ) {
                Text(
                    text = item.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
