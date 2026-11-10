package com.mioo.dao.ui.screens.drafts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.mioo.dao.ui.screens.settings.secondaryPageBackground
import com.mioo.dao.ui.screens.settings.secondaryCardBackground
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mioo.dao.data.local.DraftEntity
import com.mioo.dao.data.repository.DraftPrefillBus
import com.mioo.dao.data.repository.ThreadRepository
import com.mioo.dao.ui.theme.DaoTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DraftViewModel @Inject constructor(
    private val threadRepository: ThreadRepository,
    private val draftPrefillBus: DraftPrefillBus
) : ViewModel() {

    val drafts: StateFlow<List<DraftEntity>> = threadRepository.getDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { threadRepository.deleteDraft(id) }
    }

    /** Hand a POST draft to the forum composer via the cross-screen bus. */
    fun usePostDraft(draft: DraftEntity) {
        draftPrefillBus.offer(draft)
    }
}

private val draftTimeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftBoxScreen(
    viewModel: DraftViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onUseReplyDraft: (DraftEntity) -> Unit,
    /** Called after a POST draft was handed to the bus; host pops home and switches to 板块 tab. */
    onUsePostDraft: () -> Unit,
    modifier: Modifier = Modifier
) {
    val drafts by viewModel.drafts.collectAsState()
    var replyToDelete by remember { mutableStateOf<DraftEntity?>(null) }
    replyToDelete?.let { draft ->
        AlertDialog(
            onDismissRequest = { replyToDelete = null },
            title = { Text("删除回复草稿？") },
            text = { Text("这份未发送的回复将从草稿箱移除。") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(draft.id); replyToDelete = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { replyToDelete = null }) { Text("取消") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("草稿箱", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DaoTheme.colors.glassTopBar
                )
            )
        },
        containerColor = secondaryPageBackground(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { paddingValues ->
        if (drafts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无草稿\n回复内容会自动保存；发串时可点保存按钮，或退出时选择“保存草稿”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(drafts, key = { it.id }) { draft ->
                    DraftRow(
                        draft = draft,
                        onClick = {
                            if (draft.isReply) {
                                onUseReplyDraft(draft)
                            } else {
                                viewModel.usePostDraft(draft)
                                onUsePostDraft()
                            }
                        },
                        onDelete = {
                            if (draft.isReply) replyToDelete = draft else viewModel.delete(draft.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DraftRow(
    draft: DraftEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isReply = draft.isReply
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(secondaryCardBackground())
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isReply) Icons.AutoMirrored.Filled.Reply else Icons.Default.Edit,
            contentDescription = if (isReply) "回复草稿" else "发串草稿",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isReply) {
                    val label = if (draft.type == DraftEntity.TYPE_ARCHIVED_REPLY) "旧回复" else "回复"
                    "$label · ${draft.targetName.ifBlank { "No." + draft.targetId }}"
                } else {
                    "发串 · " + draft.targetName.ifBlank { "草稿" }
                },
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!isReply && !draft.title.isNullOrBlank()) {
                Text(
                    text = draft.title ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = draft.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = draftTimeFormat.format(Date(draft.updatedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除草稿",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
