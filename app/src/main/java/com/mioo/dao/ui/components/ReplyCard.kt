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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush

private val quotePatternCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()

private fun getQuoteRegex(quoteId: String): Regex {
    return quotePatternCache.getOrPut(quoteId) {
        Regex("(?:<font[^>]*>)?\\s*(?:&gt;&gt;|>>)(?:No\\.)?${Regex.escape(quoteId)}\\s*(?:</font>)?", RegexOption.IGNORE_CASE)
    }
}

private val EMPTY_QUOTE_CLICK: (String) -> Unit = {}

sealed interface PostContentBlock {
    data class Text(val html: String) : PostContentBlock
    data class Quote(val quote: PostData) : PostContentBlock
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReplyCard(
    postData: PostData,
    onQuoteClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onCardClick: () -> Unit,
    onCardLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    quotedPosts: StablePostList = StablePostList(emptyList()),
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

            val contentBlocks = androidx.compose.runtime.remember(postData.content, quotedPosts) {
                if (quotedPosts.list.isEmpty()) {
                    listOf(PostContentBlock.Text(postData.content))
                } else {
                    val blocks = mutableListOf<PostContentBlock>()
                    var currentHtml = postData.content
                    quotedPosts.list.forEach { quote ->
                        val quoteRegex = getQuoteRegex(quote.id)
                        val match = quoteRegex.find(currentHtml)

                        if (match != null) {
                            val beforeText = currentHtml.substring(0, match.range.last + 1)
                            currentHtml = currentHtml.substring(match.range.last + 1)

                            if (beforeText.isNotBlank()) {
                                blocks.add(PostContentBlock.Text(beforeText))
                            }
                            blocks.add(PostContentBlock.Quote(quote))
                        } else {
                            // Fallback if regex fails to match (e.g. duplicate quote or already processed)
                            blocks.add(PostContentBlock.Quote(quote))
                        }
                    }
                    if (currentHtml.isNotBlank()) {
                        blocks.add(PostContentBlock.Text(currentHtml))
                    }
                    blocks
                }
            }

            var isExpanded by remember { mutableStateOf(false) }
            var contentHeight by remember { mutableStateOf(0) }
            val density = LocalDensity.current
            val maxCollapseHeight = 260.dp
            val maxCollapseHeightPx = with(density) { maxCollapseHeight.roundToPx() }
            val isCollapsible = contentHeight > maxCollapseHeightPx

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { layoutCoordinates ->
                            if (!isExpanded) {
                                contentHeight = layoutCoordinates.size.height
                            }
                        }
                        .then(
                            if (isCollapsible && !isExpanded) {
                                Modifier.height(maxCollapseHeight)
                            } else {
                                Modifier
                            }
                        )
                        .animateContentSize()
                ) {
                    contentBlocks.forEach { block ->
                        when (block) {
                            is PostContentBlock.Text -> {
                                HtmlContent(
                                    html = block.html,
                                    onQuoteClick = onQuoteClick,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth(),
                                    onTextClick = onCardClick,
                                    onLongClick = onCardLongClick
                                )
                            }
                            is PostContentBlock.Quote -> {
                                val quoteColor = MaterialTheme.colorScheme.primary
                                QuotedPostBox(
                                    quote = block.quote,
                                    quoteColor = quoteColor,
                                    onQuoteClick = onQuoteClick,
                                    onImageClick = onImageClick,
                                    onViewThreadClick = onViewThreadClick,
                                    currentThreadId = currentThreadId
                                )
                            }
                        }
                    }
                }

                if (isCollapsible && !isExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        DaoTheme.colors.replyCardBg.copy(alpha = 0.95f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        androidx.compose.material3.TextButton(
                            onClick = { isExpanded = true },
                            modifier = Modifier.padding(bottom = 0.dp)
                        ) {
                            Text(
                                text = "展开全文",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
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
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val imageRequest = androidx.compose.runtime.remember(imageUrl) {
                        coil.request.ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .size(coil.size.Size(300, 300))
                            .precision(coil.size.Precision.INEXACT)
                            .memoryCacheKey(imageUrl)
                            .diskCacheKey(imageUrl)
                            .build()
                    }
                    ShimmerAsyncImage(
                        model = imageRequest,
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
    val isDeleted = quote.content.contains("该引用不存在") || quote.content.contains("该帖不存在") || quote.content.contains("已被删除")

    Box(modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        // The main bordered container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, quoteColor, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .clickable { /* Consume click to prevent parent card navigation, but do not open RefPopup */ }
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

            // Quoted Content — do NOT pass onQuoteClick so clicking text inside the box won't open RefPopup
            HtmlContent(
                html = quote.content,
                onQuoteClick = EMPTY_QUOTE_CLICK,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
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
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val imageRequest = androidx.compose.runtime.remember(imageUrl) {
                        coil.request.ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .size(coil.size.Size(300, 300))
                            .precision(coil.size.Precision.INEXACT)
                            .build()
                    }
                    ShimmerAsyncImage(
                        model = imageRequest,
                        contentDescription = "Quote Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            // View Original Thread button
            val targetThreadId = quote.resto
            val isCurrentThread = currentThreadId != null && targetThreadId == currentThreadId
            val hasValidThread = !targetThreadId.isNullOrBlank() && targetThreadId != "0" && targetThreadId != "null"
            if (!isDeleted && hasValidThread && !isCurrentThread) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onViewThreadClick(targetThreadId!!) }
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
