package com.mioo.dao.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.mioo.dao.ui.theme.DaoTheme

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
    enableHtmlGestures: Boolean = false
) {
    val outline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    val border = remember(outline) { BorderStroke(0.5.dp, outline) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onThreadClick,
                onLongClick = onLongClick
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = DaoTheme.colors.threadCardBg
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                // User ID tag
                val idBgColor = when {
                    postData.isAdmin -> DaoTheme.colors.admin.copy(alpha = 0.15f)
                    postData.isPo -> DaoTheme.colors.po.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val idTextColor = when {
                    postData.isAdmin -> DaoTheme.colors.admin
                    postData.isPo -> DaoTheme.colors.po
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
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
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                maxLines = contentMaxLines,
                overflow = TextOverflow.Ellipsis,
                onTextClick = if (enableHtmlGestures) onThreadClick else null,
                onLongClick = if (enableHtmlGestures) onLongClick else null,
                enableGestures = enableHtmlGestures
            )

            // Attached Image Thumbnail
            postData.imageUrl?.let { imageUrl ->
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onImageClick(imageUrl) }
                ) {
                    val context = LocalContext.current
                    val imageRequest = remember(imageUrl) {
                        ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(false)
                            .size(Size(360, 360))
                            .precision(Precision.INEXACT)
                            .memoryCacheKey(imageUrl)
                            .diskCacheKey(imageUrl)
                            // Avoid thrashing decoder during first fling
                            .allowHardware(true)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "Thread Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
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
