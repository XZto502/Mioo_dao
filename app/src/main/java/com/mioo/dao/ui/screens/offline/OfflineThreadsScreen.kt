package com.mioo.dao.ui.screens.offline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.mioo.dao.data.local.OfflineThreadSummary
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.data.repository.ThreadRepository
import com.mioo.dao.ui.theme.DaoTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class OfflineThreadsViewModel @Inject constructor(
    private val threadRepository: ThreadRepository
) : ViewModel() {

    val summaries: StateFlow<List<OfflineThreadSummary>> =
        threadRepository.getOfflineThreadSummaries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 正在重新拉取离线的串号集合——对应卡片转圈、防重复点击。 */
    private val _updatingIds = MutableStateFlow<Set<String>>(emptySet())
    val updatingIds: StateFlow<Set<String>> = _updatingIds.asStateFlow()

    fun delete(summary: OfflineThreadSummary) {
        viewModelScope.launch {
            threadRepository.deleteOfflineThread(summary.threadId)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            threadRepository.clearAllOfflineThreads()
        }
    }

    /** 重新拉取最新页（复用 downloadFullThread：已缓存的页/图按 key 覆盖或跳过）。 */
    fun update(summary: OfflineThreadSummary, onResult: (String) -> Unit) {
        val id = summary.threadId
        if (id in _updatingIds.value) return
        _updatingIds.update { it + id }
        viewModelScope.launch {
            try {
                threadRepository.downloadFullThread(id).collect { response ->
                    onResult(
                        when (response) {
                            is XdResponse.Success -> "离线缓存已更新"
                            is XdResponse.Error -> "更新失败: ${response.message}"
                        }
                    )
                }
            } finally {
                _updatingIds.update { it - id }
            }
        }
    }
}

/** 角标/副标题统一的体积文案：KB 取整、MB 一位小数。 */
fun formatOfflineBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 KB"
    bytes < 1024L * 1024L -> "${(bytes + 1023) / 1024} KB"
    bytes < 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0)
    else -> String.format(Locale.getDefault(), "%.2f GB", bytes / 1073741824.0)
}

private val downloadedTimeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OfflineThreadsScreen(
    viewModel: OfflineThreadsViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onNavigateToThread: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val summaries by viewModel.summaries.collectAsState()
    val updatingIds by viewModel.updatingIds.collectAsState()
    var showClearAllDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val totalCount = summaries.size
    val totalBytes = summaries.sumOf { it.totalBytes }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "已离线串",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (totalCount > 0) {
                            Text(
                                text = "共 $totalCount 串 · 已用 ${formatOfflineBytes(totalBytes)}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (summaries.isNotEmpty()) {
                        IconButton(onClick = { showClearAllDialog = true }) {
                            Icon(
                                Icons.Outlined.DeleteSweep,
                                contentDescription = "清空全部离线缓存",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DaoTheme.colors.glassTopBar
                )
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { paddingValues ->
        if (summaries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.DownloadDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "暂无离线缓存的串\n可在串详情页右上角「⋮ -> 离线下载本串」中保存",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val density = LocalDensity.current
            val navBarBottom = with(density) {
                WindowInsets.navigationBars.getBottom(density).toDp()
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + navBarBottom + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = summaries,
                    key = { it.threadId }
                ) { summary ->
                    OfflineThreadCard(
                        summary = summary,
                        isUpdating = summary.threadId in updatingIds,
                        offlineImageDir = remember { File(context.filesDir, "offline_images") },
                        onClick = { onNavigateToThread(summary.threadId) },
                        onUpdate = { viewModel.update(summary) { msg ->
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        } },
                        onDelete = { viewModel.delete(summary) },
                        modifier = Modifier.animateItemPlacement()
                    )
                }
            }
        }
    }

    if (showClearAllDialog && summaries.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("清空全部离线缓存") },
            text = {
                Text(
                    text = "将删除 $totalCount 个串的离线数据与图片，共 ${formatOfflineBytes(totalBytes)}，此操作不可恢复。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        viewModel.clearAll()
                    }
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/** 离线串卡片：管理页与收藏页「已离线」标签共用。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfflineThreadCard(
    summary: OfflineThreadSummary,
    isUpdating: Boolean,
    offlineImageDir: File,
    onClick: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = DaoTheme.colors.threadCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 头行：PO 饼干 + 板块名 …… 串号
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary.poUserHash.ifBlank { "???" },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = DaoTheme.colors.po,
                    modifier = Modifier
                        .background(DaoTheme.colors.po.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = summary.forumName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "No.${summary.threadId}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 中段：摘要 + 首图缩略图
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    summary.title?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    if (summary.contentSummary.isNotBlank()) {
                        Text(
                            text = summary.contentSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (summary.title != null) 2 else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                val cover = summary.coverFile
                if (!cover.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(10.dp))
                    val coverFile = remember(cover, offlineImageDir) { File(offlineImageDir, cover) }
                    if (coverFile.isFile) {
                        AsyncImage(
                            model = coverFile,
                            contentDescription = "首图缩略图",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 72.dp, height = 56.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 底行：缓存胶囊 + 下载时间 …… 更新 / 删除
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "已缓存 ${summary.cachedPages} 页 (${summary.replyCount} 回复)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = downloadedTimeFormat.format(Date(summary.downloadedAt)),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onUpdate, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "更新离线缓存",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "删除离线缓存",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
