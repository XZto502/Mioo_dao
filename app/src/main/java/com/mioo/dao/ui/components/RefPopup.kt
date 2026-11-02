package com.mioo.dao.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mioo.dao.ui.theme.readingBodyStyle
import com.mioo.dao.ui.theme.readingCardColor
import com.mioo.dao.ui.theme.readingCardBorder
import com.mioo.dao.ui.theme.ImmersiveDialogEffect
import com.mioo.dao.ui.theme.MiooMotion
import com.mioo.dao.ui.theme.immersiveDialogProperties
import com.mioo.dao.ui.theme.isReducedMotionEnabled

@Composable
fun RefPopup(
    postId: String,
    postData: PostData?,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onQuoteClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onViewThreadClick: (String) -> Unit = {},
    canGoBack: Boolean = false,
    onBack: () -> Unit = onDismiss,
    modifier: Modifier = Modifier,
    currentThreadId: String? = null
) {
    val reducedMotion = isReducedMotionEnabled()
    // Modal: scale from 0.95 + fade (never scale 0); centered origin is correct for modals.
    val visibleState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = immersiveDialogProperties(
            usePlatformDefaultWidth = true,
            dismissOnBackPress = false,
            dismissOnClickOutside = true
        )
    ) {
        ImmersiveDialogEffect()
        BackHandler {
            if (canGoBack) onBack() else onDismiss()
        }
        BoxWithConstraints(Modifier.fillMaxWidth().safeDrawingPadding()) {
        val maxCardHeight = (maxHeight - 48.dp).coerceAtLeast(0.dp)
        val contentScroll = remember(postId) { ScrollState(0) }
        AnimatedVisibility(
            visibleState = visibleState,
            enter = MiooMotion.modalEnter(reducedMotion),
            exit = MiooMotion.modalExit(reducedMotion)
        ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .heightIn(max = maxCardHeight),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = readingCardColor()),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = readingCardBorder()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "引用",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    if (canGoBack) {
                        TextButton(onClick = onBack) {
                            Text("上一层")
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close reference",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 标题与关闭按钮固定；正文、附图与原串入口共用可滚动区域。
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(contentScroll)
                ) {
                // Content State Machine
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    errorMessage != null -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    postData != null -> {
                        // Reuse the board card header for identity, badges, time and number.
                        ReadingPostHeader(postData)

                        Spacer(modifier = Modifier.height(10.dp))

                        // Title if exists
                        if (!postData.title.isNullOrBlank()) {
                            Text(
                                text = postData.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        // Content (HTML)
                        HtmlContent(
                            html = postData.content,
                            onQuoteClick = onQuoteClick,
                            style = readingBodyStyle(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Attached Image
                        postData.imageUrl?.let { imageUrl ->
                            Spacer(modifier = Modifier.height(12.dp))
                            ListThumbAsyncImage(
                                imageUrl = imageUrl,
                                contentDescription = "Attached Thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onImageClick(imageUrl) }
                            )
                        }

                        // View Original Thread right aligned text
                        val targetThreadId = postData.resto
                        val isCurrentThread = currentThreadId != null && targetThreadId == currentThreadId
                        val hasValidThread = !targetThreadId.isNullOrBlank() && targetThreadId != "0" && targetThreadId != "null"
                        if (hasValidThread && !isCurrentThread) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onViewThreadClick(targetThreadId!!) }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "跳转原串",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                }
            }
        }
        }
        }
    }
}
