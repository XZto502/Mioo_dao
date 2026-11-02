package com.mioo.dao.ui.screens.forum

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mioo.dao.data.local.DraftEntity
import com.mioo.dao.ui.theme.ImmersiveDialogEffect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostDraftPicker(
    drafts: List<DraftEntity>,
    onDismiss: () -> Unit,
    onSelect: (DraftEntity) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ImmersiveDialogEffect()
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("选择发串草稿", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "恢复标题、名称和正文，并切换到原板块。每个板块保留一份发串草稿。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (drafts.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("暂无发串草稿", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "编辑后点顶部保存按钮，或退出时选择“保存草稿”，就会出现在这里。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(drafts, key = { it.id }) { draft ->
                    Card(
                        onClick = { onSelect(draft) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                draft.title?.takeIf { it.isNotBlank() } ?: "无标题草稿",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                listOfNotNull(draft.targetName.takeIf { it.isNotBlank() }, draft.name?.takeIf { it.isNotBlank() }).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            if (draft.content.isNotBlank()) Text(
                                draft.content, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(draft.updatedAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
