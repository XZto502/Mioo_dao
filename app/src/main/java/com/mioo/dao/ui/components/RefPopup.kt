package com.mioo.dao.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Dialog
import com.mioo.dao.ui.theme.readingBodyStyle
import com.mioo.dao.ui.theme.readingCardColor
import com.mioo.dao.ui.theme.readingCardBorder
import com.mioo.dao.ui.theme.ImmersiveDialogEffect
import com.mioo.dao.ui.theme.MiooMotion
import com.mioo.dao.ui.theme.immersiveDialogProperties
import com.mioo.dao.ui.theme.isReducedMotionEnabled

private data class ReferenceContent(val id: String, val post: PostData?, val loading: Boolean, val error: String?)

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
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = true
        )
    ) {
        ImmersiveDialogEffect()
        BackHandler {
            if (canGoBack) onBack() else onDismiss()
        }
        BoxWithConstraints(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDismiss() }, contentAlignment = Alignment.Center) {
        val maxCardHeight = minOf(LocalConfiguration.current.screenHeightDp.dp * 0.65f, 460.dp)
        AnimatedVisibility(
            visibleState = visibleState,
            enter = MiooMotion.modalEnter(reducedMotion),
            exit = MiooMotion.modalExit(reducedMotion)
        ) {
        AnimatedContent(
            targetState = ReferenceContent(postId, postData, isLoading, errorMessage),
            contentKey = { it.id },
            transitionSpec = { MiooMotion.modalEnter(reducedMotion) togetherWith MiooMotion.modalExit(reducedMotion) },
            label = "referenceTransition"
        ) { reference ->
        val postData = reference.post
        val isLoading = reference.loading
        val errorMessage = reference.error
        val contentScroll = remember(reference.id) { ScrollState(0) }
        Card(
            modifier = modifier
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {}
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .heightIn(max = maxCardHeight),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) androidx.compose.ui.graphics.Color(0xFF16181E) else androidx.compose.ui.graphics.Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Keep the identity visible while the body and image scroll independently.
                if (!isLoading && errorMessage == null && postData != null) {
                    ReadingPostHeader(postData)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
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
                            compactBlankLines = true,
                            trimOuterWhitespace = true,
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
                    }
                }
                }
                if (!isLoading && errorMessage == null && postData != null) {
                    val originalThreadId = postData.resto?.takeIf { it.isNotBlank() && it != "0" && it != "null" }
                        ?: postData.id.takeIf { it.isNotBlank() }
                    if (originalThreadId != null && originalThreadId != currentThreadId) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onViewThreadClick(originalThreadId) }) { Text("跳转原串") }
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
