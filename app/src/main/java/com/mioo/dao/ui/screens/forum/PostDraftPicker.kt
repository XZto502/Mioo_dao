package com.mioo.dao.ui.screens.forum

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mioo.dao.data.local.DraftEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun PostDraftCard(draft: DraftEntity, target: String, onClick: () -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xFF22252D) else Color(0xFFF2F4F8))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(draft.title?.takeIf { it.isNotBlank() } ?: "无标题草稿", Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (target.isNotBlank()) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.weight(0.8f, fill = false)) {
                        Text(target, Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(draft.updatedAt))
                Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, softWrap = false)
            }
            if (draft.content.isNotBlank()) {
                Text(draft.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
internal fun AnimatedVisibilityScope.PostDraftPicker(
    drafts: List<DraftEntity>,
    reducedMotion: Boolean,
    onDismiss: () -> Unit,
    onSelect: (DraftEntity) -> Unit
) {
    // Stay in the editor's Activity window. A second modal window could be
    // placed behind the former full-screen Dialog and appear unresponsive.
    Box(Modifier.fillMaxSize().zIndex(200f)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)).clickable(onClick = onDismiss))
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .animateEnterExit(
                    enter = slideInVertically(tween(if (reducedMotion) 0 else 220)) { it / 5 },
                    exit = slideOutVertically(tween(if (reducedMotion) 0 else 180)) { it / 5 }
                ),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.navigationBarsPadding().padding(top = 24.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("选择发串草稿", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "选择当前板块的草稿，恢复标题、名称和正文。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (drafts.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                        Text("当前板块暂无发串草稿", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "编辑后点顶部保存按钮，或退出时选择“保存草稿”，就会出现在这里。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).weight(1f, fill = false),
                        contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(drafts, key = { it.id }) { draft ->
                            PostDraftCard(draft, draft.targetName) { onSelect(draft) }
                        }
                    }
                }
            }
        }
    }
}
