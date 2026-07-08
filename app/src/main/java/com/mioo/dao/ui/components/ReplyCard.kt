package com.mioo.dao.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mioo.dao.ui.theme.DaoTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReplyCard(
    postData: PostData,
    onQuoteClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onCardClick: () -> Unit,
    onCardLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    quotedPosts: List<PostData> = emptyList(),
    onViewThreadClick: (String) -> Unit = {},
    currentThreadId: String? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onCardClick,
                onLongClick = onCardLongClick
            ),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = DaoTheme.colors.replyCardBg
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Row (User ID, badges, timestamp, reply ID)
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

                // PO Badge
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

                // Admin Badge
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

                // Sage Badge
                if (postData.isSage) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SAGE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier
                            .background(DaoTheme.colors.sage, shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Date/Time
                Text(
                    text = postData.createdAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Reply ID number
                Text(
                    text = "No.${postData.id}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reply Subject (highly rare but possible in some formats)
            if (!postData.title.isNullOrBlank()) {
                Text(
                    text = postData.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Parse and split HTML if we have inline quoted posts to show
            if (quotedPosts.isEmpty()) {
                HtmlContent(
                    html = postData.content,
                    onQuoteClick = onQuoteClick,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    onTextClick = onCardClick,
                    onLongClick = onCardLongClick
                )
            } else {
                var currentHtml = postData.content
                val quoteColor = MaterialTheme.colorScheme.primary

                quotedPosts.forEach { quote ->
                    val quoteRegex = Regex("(?:<font[^>]*>)?\\s*(?:&gt;&gt;|>>)(?:No\\.)?${quote.id}\\s*(?:</font>)?", RegexOption.IGNORE_CASE)
                    val match = quoteRegex.find(currentHtml)

                    if (match != null) {
                        val beforeText = currentHtml.substring(0, match.range.last + 1)
                        currentHtml = currentHtml.substring(match.range.last + 1)

                        if (beforeText.isNotBlank()) {
                            HtmlContent(
                                html = beforeText,
                                onQuoteClick = onQuoteClick,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(),
                                onTextClick = onCardClick,
                                onLongClick = onCardLongClick
                            )
                        }

                        QuotedPostBox(
                            quote = quote,
                            quoteColor = quoteColor,
                            onQuoteClick = onQuoteClick,
                            onImageClick = onImageClick,
                            onViewThreadClick = onViewThreadClick,
                            currentThreadId = currentThreadId
                        )
                    } else {
                        // Fallback if regex fails to match (e.g. duplicate quote or already processed)
                        QuotedPostBox(
                            quote = quote,
                            quoteColor = quoteColor,
                            onQuoteClick = onQuoteClick,
                            onImageClick = onImageClick,
                            onViewThreadClick = onViewThreadClick,
                            currentThreadId = currentThreadId
                        )
                    }
                }

                if (currentHtml.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    HtmlContent(
                        html = currentHtml,
                        onQuoteClick = onQuoteClick,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        onTextClick = onCardClick,
                        onLongClick = onCardLongClick
                    )
                }
            }

            // Attached Image Thumbnail
            postData.imageUrl?.let { imageUrl ->
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onImageClick(imageUrl) }
                ) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Reply Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
        }
    }
}

@Composable
fun QuotedPostBox(
    quote: PostData,
    quoteColor: Color,
    onQuoteClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onViewThreadClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    currentThreadId: String? = null
) {
    Box(modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        // The main bordered container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, quoteColor, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .clickable { onQuoteClick(quote.id) }
                .padding(12.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp)) // space for the overlapping label
            
            // User ID and PO badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                val idColor = when {
                    quote.isAdmin -> DaoTheme.colors.admin
                    quote.isPo -> DaoTheme.colors.po
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = quote.userId,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = idColor
                )
                if (quote.isPo) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "po",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier
                            .background(DaoTheme.colors.po, shape = RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            // SAGE big red text
            if (quote.isSage) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "已SAGE",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = DaoTheme.colors.sageTag
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quoted Content
            HtmlContent(
                html = quote.content,
                onQuoteClick = onQuoteClick,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                onTextClick = { onQuoteClick(quote.id) } // fallback click for text
            )

            // Attached Image
            quote.imageUrl?.let { imageUrl ->
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onImageClick(imageUrl) }
                ) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Quote Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // View Original Thread right aligned text
            val isMainThread = quote.resto == null || quote.resto == "0" || quote.resto == quote.id
            if (isMainThread) {
                val targetThreadId = quote.id
                val isCurrentThread = currentThreadId != null && targetThreadId == currentThreadId
                if (targetThreadId != "0" && targetThreadId != "null" && targetThreadId.isNotBlank() && !isCurrentThread) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewThreadClick(targetThreadId) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "查看原串",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Overlapping Quote Number Label
        Text(
            text = "No.${quote.id}",
            style = MaterialTheme.typography.labelSmall,
            color = quoteColor,
            modifier = Modifier
                .offset(x = 12.dp, y = (-7).dp)
                .background(DaoTheme.colors.replyCardBg)
                .padding(horizontal = 4.dp)
        )
    }
}
