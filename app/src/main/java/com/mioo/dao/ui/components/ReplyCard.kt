package com.mioo.dao.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.luminance
import com.mioo.dao.ui.theme.readingQuoteStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mioo.dao.ui.theme.readingBodyStyle
import com.mioo.dao.ui.theme.readingMetaStyle
import com.mioo.dao.ui.theme.readingTextStyle
import com.mioo.dao.ui.theme.readingCardColor
import com.mioo.dao.ui.theme.readingCardBorder
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.ui.theme.MiooMotion
import com.mioo.dao.ui.theme.isReducedMotionEnabled
import com.mioo.dao.ui.theme.graphicsPressScale
import com.mioo.dao.ui.theme.rememberPressScale

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
    currentThreadId: String? = null,
    /** Dense reply lists skip press-scale to cut animateFloatAsState work. */
    enablePressScale: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    // Do not call rememberPressScale when disabled — it still subscribes to press + animateFloat.
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
                onClick = onCardClick,
                onLongClick = onCardLongClick
            )
    } else {
        modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onCardClick,
                onLongClick = onCardLongClick
            )
    }
    val border = readingCardBorder()
    Card(
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = readingCardColor()
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = border
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
                    style = readingTextStyle(12f, 16f, FontWeight.Medium),
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
                        style = readingTextStyle(10.5f, 14f, FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f), shape = RoundedCornerShape(4.dp))
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
                    text = remember(postData.createdAt) { formatPostTime(postData.createdAt) },
                    style = readingMetaStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Reply ID number
                Text(
                    text = "No.${postData.id}",
                    style = readingMetaStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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

            // Cached pure split — avoids re-running regex on every unrelated recompose
            val contentBlocks = remember(postData.content, quotedPosts) {
                ContentBlockSplitter.split(postData.content, quotedPosts.list)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                contentBlocks.forEach { block ->
                    when (block) {
                        is PostContentBlock.Text -> {
                            HtmlContent(
                                html = block.html,
                                onQuoteClick = onQuoteClick,
                                style = readingBodyStyle(),
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
                                onLongClick = onCardLongClick,
                                currentThreadId = currentThreadId
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
                    ListThumbAsyncImage(
                        imageUrl = imageUrl,
                        contentDescription = "Reply Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuotedPostBox(
    quote: PostData,
    quoteColor: Color,
    onQuoteClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onViewThreadClick: (String) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    currentThreadId: String? = null
) {
    val isDeleted = quote.content.contains("该引用不存在") || quote.content.contains("该帖不存在") || quote.content.contains("已被删除")

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var expanded by rememberSaveable(quote.id) { mutableStateOf(false) }
    val reducedMotion = isReducedMotionEnabled()
    val toggleExpanded = { expanded = !expanded }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MiooMotion.tweenOut(if (reducedMotion) 0 else MiooMotion.DurationSmall),
        label = "quoteExpansionArrow"
    )
    Surface(
        modifier = modifier.fillMaxWidth().combinedClickable(
            onClick = toggleExpanded, onLongClick = onLongClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isDark) Color(0xFF1A1D24) else Color(0xFFF1F3F6)
    ) {
        Row(Modifier.animateContentSize(
            animationSpec = MiooMotion.tweenOut(if (reducedMotion) 0 else MiooMotion.DurationSmall)
        ).height(IntrinsicSize.Min).padding(10.dp)) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("No.${quote.id}", style = readingMetaStyle(), color = quoteColor,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起引用" else "展开引用",
                        tint = quoteColor,
                        modifier = Modifier.size(18.dp).rotate(arrowRotation))
                }
                if (!expanded) {
                    Text(remember(quote.content) { quotePreviewPlain(quote.content) },
                        style = readingQuoteStyle(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(quote.userId, style = readingTextStyle(12f, 16f, FontWeight.Medium))
                        if (quote.isPo) {
                            Spacer(Modifier.width(6.dp))
                            Text("PO", style = readingTextStyle(10.5f, 14f, FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer,
                                    RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    if (quote.isSage) Text("已SAGE", color = DaoTheme.colors.sageTag,
                        style = readingQuoteStyle())
                    HtmlContent(html = quote.content, onQuoteClick = onQuoteClick,
                        onTextClick = toggleExpanded,
                        onLongClick = onLongClick, style = readingQuoteStyle(),
                        modifier = Modifier.fillMaxWidth())
                    quote.imageUrl?.let { imageUrl ->
                        ListThumbAsyncImage(imageUrl = imageUrl, contentDescription = "引用图片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(120.dp).clip(MaterialTheme.shapes.medium)
                                .clickable { onImageClick(imageUrl) })
                    }
                    val target = quote.resto
                    if (!isDeleted && !target.isNullOrBlank() && target != "0" &&
                        target != "null" && target != currentThreadId) {
                        Text("跳转原串", style = readingMetaStyle(), color = quoteColor,
                            modifier = Modifier.clickable { onViewThreadClick(target) }.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

/** 引用卡片收起态的一行纯文本预览：去 HTML 标签/实体、压空白、截断。 */
private fun quotePreviewPlain(html: String): String {
    val plain = html
        .replace(Regex("(?i)<br\\s*/?>"), " ")
        .replace(Regex("<[^>]*>"), "")
        .replace("&gt;", ">")
        .replace("&lt;", "<")
        .replace("&quot;", "\"")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace(Regex("\\s+"), " ")
        .trim()
    return when {
        plain.isEmpty() -> "（图片或空内容）"
        plain.length > 50 -> plain.take(50) + "…"
        else -> plain
    }
}
