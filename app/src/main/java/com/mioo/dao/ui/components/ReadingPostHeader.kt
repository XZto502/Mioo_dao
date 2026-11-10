package com.mioo.dao.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.ui.theme.readingMetaStyle
import com.mioo.dao.ui.theme.readingTextStyle

@Composable
internal fun ReadingIdentity(post: PostData, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(post.userId,
            style = readingTextStyle(12f, 16f,
                if (post.isPo || post.isAdmin) FontWeight.Bold else FontWeight.Medium)
                .copy(letterSpacing = 0.3.sp),
            color = when {
                post.isAdmin -> DaoTheme.colors.admin
                post.isPo -> colors.onSurface
                else -> colors.onSurfaceVariant.copy(alpha = 0.82f)
            }, maxLines = 1, softWrap = false)
        if (post.isPo) {
            ReadingRoleBadge(
                "PO",
                container = colors.secondaryContainer,
                content = colors.onSecondaryContainer
            )
        }
        if (post.isAdmin) ReadingRoleBadge("ADMIN")
        if (post.isSage) ReadingRoleBadge("SAGE")
    }
}

@Composable
private fun ReadingRoleBadge(
    label: String,
    container: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    content: Color = MaterialTheme.colorScheme.primary
) {
    Text(label, style = readingTextStyle(10f, 14f, FontWeight.Bold), color = content,
        maxLines = 1, softWrap = false,
        modifier = Modifier.padding(start = 4.dp)
            .background(container, RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp))
}

/**
 * Title and non-default signature sit under the cookie header and above the body.
 * Default placeholders（无标题 / 无名氏）stay hidden. A long name wraps on its own line.
 */
@Composable
internal fun ReadingPostSubject(post: PostData) {
    val title = post.title?.trim().orEmpty()
    val name = post.userName?.trim().orEmpty()
    val hasTitle = title.isNotEmpty() && title != "无标题"
    val hasName = name.isNotEmpty() && name != "无名氏" && name != "Anonymous"
    if (!hasTitle && !hasName) {
        Spacer(Modifier.height(10.dp))
        return
    }
    if (!hasTitle) {
        Spacer(Modifier.height(2.dp))
    }
    if (hasTitle) {
        Text(
            text = title,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    if (hasName) {
        if (hasTitle) Spacer(Modifier.height(2.dp))
        Text(
            text = name,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
        )
    }
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReadingPostHeader(post: PostData, onNumberClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val quoteOnClick = onNumberClick
    val numberModifier = if (quoteOnClick != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                quoteOnClick()
            },
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                clipboard.setText(AnnotatedString("No.${post.id}"))
                android.widget.Toast.makeText(context, "串号已复制", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    } else Modifier
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Both identity and number are measured at natural width. Only the clock
        // region may shrink; a long date must never turn an Admin ID into "A…".
        ReadingIdentity(post)
        Row(Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(" · ${formatReadingTime(post.createdAt)}", style = readingMetaStyle().copy(fontWeight = FontWeight.Normal),
                color = colors.onSurfaceVariant.copy(alpha = 0.52f),
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
        }
        Text("No.${post.id}", modifier = numberModifier, style = readingMetaStyle(),
            color = colors.onSurfaceVariant.copy(alpha = 0.42f),
            maxLines = 1, softWrap = false)
    }
}
