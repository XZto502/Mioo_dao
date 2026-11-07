package com.mioo.dao.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mioo.dao.ui.theme.MiooMotion
import com.mioo.dao.ui.theme.graphicsPressScale
import com.mioo.dao.ui.theme.rememberPressScale

/**
 * 发串与回复共用的工具胶囊。
 */
@Composable
fun ComposerToolButtons(
    diceSelected: Boolean,
    kaomojiSelected: Boolean,
    hasImage: Boolean,
    onDiceClick: () -> Unit,
    onKaomojiClick: () -> Unit,
    onImageClick: () -> Unit,
    onDoodleClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
) {
    val scheme = MaterialTheme.colorScheme
    val idle = scheme.onSurfaceVariant
    val active = scheme.primary

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        border = BorderStroke(
            width = 0.5.dp,
            color = scheme.outlineVariant.copy(alpha = 0.55f)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PressIconButton(onClick = onDiceClick) {
                Icon(
                    imageVector = Icons.Default.Casino,
                    contentDescription = "骰子",
                    tint = if (diceSelected) active else idle,
                    modifier = Modifier.size(22.dp)
                )
            }
            PressIconButton(onClick = onKaomojiClick) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = "颜文字",
                    tint = if (kaomojiSelected) active else idle,
                    modifier = Modifier.size(22.dp)
                )
            }
            PressIconButton(onClick = onImageClick) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = "添加图片",
                    tint = if (hasImage) active else idle,
                    modifier = Modifier.size(22.dp)
                )
            }
            PressIconButton(onClick = onDoodleClick) {
                Icon(Icons.Outlined.Brush, "涂鸦", Modifier.size(22.dp), tint = idle)
            }
        }
    }
}

@Composable
private fun PressIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource, MiooMotion.ScalePress)
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .graphicsPressScale(scale),
        interactionSource = interactionSource
    ) {
        content()
    }
}
