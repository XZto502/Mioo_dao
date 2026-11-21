package com.mioo.dao.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
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
import com.mioo.dao.ui.theme.graphicsPressScale
import com.mioo.dao.ui.theme.rememberPressScale

val LocalThreadCommentPainter = staticCompositionLocalOf<Painter?> { null }
private val ReplyPillShape = RoundedCornerShape(50)

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
    compactPreviewBlankLines: Boolean = false,
    /** List cards let the parent Card handle gestures — skip HtmlContent pointerInput. */
    enableHtmlGestures: Boolean = false,
    /**
     * Dense lists: skip press-scale animation to cut per-row
     * animateFloatAsState / graphicsLayer cost during fling.
     */
    enablePressScale: Boolean = false,
    /** When false, skip thumbnail (cold-start: defer decode until list settles). */
    showImage: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.medium,
    onNumberClick: (() -> Unit)? = null,
    forumName: String? = null,
    showReplyCount: Boolean = true
) {
    val border = readingCardBorder()
    val cardBg = readingCardColor()
    val cardShape = shape
    val baseBodyStyle = readingBodyStyle()
    val bodyStyle = remember(baseBodyStyle) { baseBodyStyle.copy(fontWeight = FontWeight.Medium) }
    val interactionSource = remember { MutableInteractionSource() }
    val styledModifier = modifier
        .fillMaxWidth()
        .background(cardBg, cardShape)
        .let { if (border != null) it.border(border, cardShape) else it }
    // Dense list default: skip press animation subscriptions entirely (enablePressScale=false)
    val cardModifier = if (enablePressScale) {
        val pressScale = rememberPressScale(
            interactionSource = interactionSource,
            pressedScale = MiooMotion.ScaleCardPress,
            enabled = true
        )
        styledModifier
            .graphicsPressScale(pressScale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onThreadClick,
                onLongClick = onLongClick
            )
    } else {
        styledModifier
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onThreadClick,
                onLongClick = onLongClick
            )
    }
    Box(modifier = cardModifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            ReadingPostHeader(postData, onNumberClick, forumName)

            ReadingPostSubject(postData)

            // HTML content — list mode skips gesture plumbing (card handles clicks)
            HtmlContent(
                html = postData.content,
                compactBlankLines = compactPreviewBlankLines,
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
            postData.imageUrl?.let { imageUrl ->
                Spacer(modifier = Modifier.height(10.dp))
                if (showImage) {
                    val thumbInteraction = remember { MutableInteractionSource() }
                    val onThumbClick = remember(imageUrl, onImageClick) { { onImageClick(imageUrl) } }
                    ListThumbAsyncImage(
                        imageUrl,
                        "串附图",
                        preserveRatio = true,
                        modifier = Modifier.clickable(
                            interactionSource = thumbInteraction,
                            indication = null,
                            onClick = onThumbClick
                        )
                    )
                } else {
                    Spacer(Modifier.height(116.dp))
                }
            }

            if (showReplyCount) {
            Spacer(modifier = Modifier.height(12.dp))

            // Footer Row (Reply Count)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(22.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = ReplyPillShape
                        )
                        .padding(start = 8.dp, end = 9.5.dp)
                ) {
                    val commentPainter = LocalThreadCommentPainter.current
                        ?: rememberVectorPainter(Icons.AutoMirrored.Outlined.Comment)
                    Icon(
                        painter = commentPainter,
                        contentDescription = "回复数",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.5.dp))
                    val baseLabelMedium = MaterialTheme.typography.labelMedium
                    val replyBadgeStyle = remember(baseLabelMedium) {
                        baseLabelMedium.copy(
                            fontSize = 11.5.sp,
                            lineHeight = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum",
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    }
                    Text(
                        text = "$replyCount replies",
                        modifier = Modifier.offset(y = (-0.5).dp),
                        style = replyBadgeStyle,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            }
        }
    }
}
