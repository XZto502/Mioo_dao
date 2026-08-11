package com.mioo.dao.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.ui.theme.MiooMotion
import com.mioo.dao.ui.theme.graphicsPressScale
import com.mioo.dao.ui.theme.rememberPressScale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThreadCard(
    postData: PostData,
    replyCount: Int,
    onThreadClick: () -> Unit,
    onQuoteClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    /** Truncate body in list for cheaper measure/layout on cold scroll. */
    contentMaxLines: Int = 8,
    /** List cards let the parent Card handle gestures — skip HtmlContent pointerInput. */
    enableHtmlGestures: Boolean = false,
    /**
     * Dense lists: skip press-scale animation (indication only) to cut per-row
     * animateFloatAsState / graphicsLayer cost during fling.
     * (Emil: high-frequency list press — indication only, no scale animation.)
     */
    enablePressScale: Boolean = false,
    /** When false, skip thumbnail (cold-start: defer decode until list settles). */
    showImage: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    val daoColors = DaoTheme.colors
    val outline = colorScheme.outlineVariant.copy(alpha = 0.3f)
    val border = remember(outline) { BorderStroke(0.5.dp, outline) }
    // CardDefaults.* are @Composable — call at composition level, not inside remember {}
    val cardColors = CardDefaults.cardColors(containerColor = daoColors.threadCardBg)
    val cardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    val cardShape = MaterialTheme.shapes.medium
    val bodyStyle = MaterialTheme.typography.bodyMedium
    val interactionSource = remember { MutableInteractionSource() }
    // Dense list default: skip press animation subscriptions entirely (enablePressScale=false)
    val cardModifier = if (enablePressScale) {
        val pressScale = rememberPressScale(
            interactionSource = interactionSource,
            pressedScale = MiooMotion.ScaleCardPress,
            enabled = true
        )
        modifier
            .fillMaxWidth()
            .graphicsPressScale(pressScale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onThreadClick,
                onLongClick = onLongClick
            )
    } else {
        modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onThreadClick,
                onLongClick = onLongClick
            )
    }
    Card(
        modifier = cardModifier,
        shape = cardShape,
        colors = cardColors,
        elevation = cardElevation,
        border = border
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row (User ID, Date, Post ID)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User ID tag — remember colors per role so list fling doesn't re-alloc
                val idBgColor = remember(postData.isAdmin, postData.isPo, colorScheme.surfaceVariant, daoColors.admin, daoColors.po) {
                    when {
                        postData.isAdmin -> daoColors.admin.copy(alpha = 0.15f)
                        postData.isPo -> daoColors.po.copy(alpha = 0.15f)
                        else -> colorScheme.surfaceVariant
                    }
                }
                val idTextColor = remember(postData.isAdmin, postData.isPo, colorScheme.onSurfaceVariant, daoColors.admin, daoColors.po) {
                    when {
                        postData.isAdmin -> daoColors.admin
                        postData.isPo -> daoColors.po
                        else -> colorScheme.onSurfaceVariant
                    }
                }
                Text(
                    text = postData.userId,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = idTextColor,
                    modifier = Modifier
                        .background(idBgColor, shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )

                // PO Tag
                if (postData.isPo) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PO",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier
                            .background(DaoTheme.colors.po, shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // Admin Tag
                if (postData.isAdmin) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ADMIN",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier
                            .background(DaoTheme.colors.admin, shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Date
                Text(
                    text = postData.createdAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Post ID
                Text(
                    text = "No.${postData.id}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subject / Title (if present)
            if (!postData.title.isNullOrBlank()) {
                Text(
                    text = postData.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // HTML content — list mode skips gesture plumbing (card handles clicks)
            HtmlContent(
                html = postData.content,
                onQuoteClick = onQuoteClick,
                style = bodyStyle,
                modifier = Modifier.fillMaxWidth(),
                maxLines = contentMaxLines,
                overflow = TextOverflow.Ellipsis,
                onTextClick = if (enableHtmlGestures) onThreadClick else null,
                onLongClick = if (enableHtmlGestures) onLongClick else null,
                enableGestures = enableHtmlGestures
            )

            // Attached Image Thumbnail (optional — off during cold first frames)
            if (showImage) {
                postData.imageUrl?.let { imageUrl ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onImageClick(imageUrl) }
                    ) {
                        ListThumbAsyncImage(
                            imageUrl = imageUrl,
                            contentDescription = "Thread Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer Row (Reply Count & Sage Status)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Comment,
                        contentDescription = "Replies",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$replyCount replies",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (postData.isSage) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SAGE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier
                            .background(DaoTheme.colors.sage, shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
