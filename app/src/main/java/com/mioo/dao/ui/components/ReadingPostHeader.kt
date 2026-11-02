package com.mioo.dao.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        if (post.isPo) ReadingRoleBadge("PO")
        if (post.isAdmin) ReadingRoleBadge("ADMIN")
        if (post.isSage) ReadingRoleBadge("SAGE")
    }
}

@Composable
private fun ReadingRoleBadge(label: String) {
    val primary = MaterialTheme.colorScheme.primary
    Text(label, style = readingTextStyle(10f, 14f, FontWeight.Bold), color = primary,
        maxLines = 1, softWrap = false,
        modifier = Modifier.padding(start = 4.dp)
            .background(primary.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp))
}

@Composable
internal fun ReadingPostHeader(post: PostData) {
    val colors = MaterialTheme.colorScheme
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
        Text("No.${post.id}", style = readingMetaStyle(),
            color = colors.onSurfaceVariant.copy(alpha = 0.42f),
            maxLines = 1, softWrap = false)
    }
}
