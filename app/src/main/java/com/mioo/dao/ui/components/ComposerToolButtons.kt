package com.mioo.dao.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 发串 / 回复底栏：骰子 · 颜文字 · 图片 三连按钮（同一胶囊内）。
 */
@Composable
fun ComposerToolButtons(
    diceSelected: Boolean,
    kaomojiSelected: Boolean,
    hasImage: Boolean,
    onDiceClick: () -> Unit,
    onKaomojiClick: () -> Unit,
    onImageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val idle = scheme.onSurfaceVariant
    val active = scheme.primary

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            width = 0.5.dp,
            color = scheme.outlineVariant.copy(alpha = 0.55f)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onDiceClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Casino,
                    contentDescription = "骰子",
                    tint = if (diceSelected) active else idle,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(
                onClick = onKaomojiClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = "颜文字",
                    tint = if (kaomojiSelected) active else idle,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(
                onClick = onImageClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = "添加图片",
                    tint = if (hasImage) active else idle,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
