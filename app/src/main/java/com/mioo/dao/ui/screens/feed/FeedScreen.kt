package com.mioo.dao.ui.screens.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mioo.dao.ui.screens.forum.ForumViewModel
import com.mioo.dao.ui.components.homePageColor
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mioo.dao.ui.components.BookmarkListItem
import com.mioo.dao.ui.components.HtmlParseCache
import com.mioo.dao.ui.components.ImageViewer
import com.mioo.dao.ui.components.ListThumbImage
import com.mioo.dao.ui.components.PrefetchListImages
import com.mioo.dao.ui.components.ThreadCard
import com.mioo.dao.ui.components.ThreadListItem
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.ui.theme.MiooMotion
import com.mioo.dao.ui.theme.ImmersiveDialogEffect
import com.mioo.dao.ui.theme.immersiveDialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    onNavigateToThread: (threadId: String) -> Unit,
    onImageViewerOpenChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeImageUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeImageUrl) { onImageViewerOpenChange(activeImageUrl != null) }
    val uiState by viewModel.uiState.collectAsState()
    if (!uiState.selectionReady) return
    val forumViewModel: ForumViewModel = hiltViewModel()
    val forumState by forumViewModel.uiState.collectAsState()
    LaunchedEffect(forumState.forumGroups.isEmpty()) {
        if (forumState.forumGroups.isEmpty()) forumViewModel.loadForumGroups()
    }
    val forumNames = remember(forumState.forumGroups) {
        forumState.forumGroups.flatMap { it.forums }.associate { it.id to it.name }
    }
    val folderIds = remember(uiState.feedFolders) { listOf(null) + uiState.feedFolders.map { it.uuid } }
    val pager = rememberPagerState(initialPage = folderIds.indexOf(uiState.selectedFolderId).coerceAtLeast(0)) { folderIds.size }
    val listStates = remember { mutableStateMapOf<String, LazyListState>() }
    val pageSnapshots = remember { mutableStateMapOf<String, FeedUiState>() }
    fun pageKey(id: String?) = id ?: "__offline__"
    SideEffect {
        if (!uiState.isLoading) pageSnapshots[pageKey(uiState.selectedFolderId)] = uiState
    }
    LaunchedEffect(folderIds) {
        val selectedIndex = folderIds.indexOf(viewModel.uiState.value.selectedFolderId).coerceAtLeast(0)
        pager.scrollToPage(selectedIndex)
        snapshotFlow { pager.settledPage }.collect { index ->
            val id = folderIds.getOrNull(index)
            if (id != viewModel.uiState.value.selectedFolderId) viewModel.selectFolder(id)
        }
    }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val emptyStringLambda = remember { { _: String -> } }
    val quoteLinkColor = MaterialTheme.colorScheme.primary
    // 长按/删除按钮弹出的离线串删除确认
    var pendingOfflineDelete by remember {
        mutableStateOf<com.mioo.dao.data.local.OfflineThreadSummary?>(null)
    }

    // 订阅操作失败等一次性错误提示
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // 已离线串删除确认（卡片删除按钮触发）
    pendingOfflineDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingOfflineDelete = null },
            properties = immersiveDialogProperties(),
            title = { ImmersiveDialogEffect(); Text("删除离线缓存？") },
            text = {
                Text(
                    text = "将移除 No.${target.threadId} 的本地收藏与离线数据（含图片，" +
                        com.mioo.dao.ui.screens.offline.formatOfflineBytes(target.totalBytes) + "），不可恢复。"
                )
            },
            confirmButton = {
                Button(onClick = {
                    pendingOfflineDelete = null
                    viewModel.deleteOffline(target.threadId)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingOfflineDelete = null }) { Text("取消") }
            }
        )
    }

    // Pre-parse HTML for currently visible feed list
    LaunchedEffect(
        uiState.selectedFolderId,
        uiState.localDisplayItems,
        uiState.remoteDisplayItems,
        quoteLinkColor
    ) {
        val htmls = if (uiState.selectedFolderId == null) {
            uiState.localDisplayItems.map { it.postData.content }
        } else {
            uiState.remoteDisplayItems.map { it.postData.content }
        }
        if (htmls.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                HtmlParseCache.prewarm(htmls, quoteLinkColor)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的收藏", style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * 1.125f), fontWeight = FontWeight.Bold, maxLines = 1) },
                actions = {
                    IconButton(onClick = {
                        scope.launch { listStates[pageKey(uiState.selectedFolderId)]?.animateScrollToItem(0) }
                        if (uiState.selectedFolderId != null) {
                            viewModel.refreshRemote()
                        } else {
                            viewModel.refreshReplyBadges()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DaoTheme.colors.glassTopBar
                )
            )
        },
        modifier = modifier,
        containerColor = homePageColor(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                folderIds.forEachIndexed { index, id ->
                    val selected = pager.targetPage == index
                    Surface(onClick = {
                        if (!selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { pager.animateScrollToPage(index, animationSpec = MiooMotion.tabPagerSpring()) }
                        }
                    }, shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
                        Text(if (id == null) "已离线" else uiState.feedFolders.firstOrNull { it.uuid == id }?.name.orEmpty(),
                            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            fontSize = 13.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false)
                    }
                }
            }

            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { pageKey(folderIds[it]) }) { page ->
                val folderId = folderIds[page]
                val current = folderId == uiState.selectedFolderId
                val pageState = if (current) uiState else pageSnapshots[pageKey(folderId)]
                val listState = rememberSaveable(pageKey(folderId), saver = LazyListState.Saver) { LazyListState() }
                SideEffect { listStates[pageKey(folderId)] = listState }
                if (pageState == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                val uiState = pageState
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.selectedFolderId == null &&
                    uiState.offlineSummaries.isEmpty() &&
                    uiState.localDisplayItems.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "暂无离线串",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "在串详情页点星标或右上角「⋮ → 离线下载本串」即可离线保存。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                uiState.selectedFolderId != null && uiState.remoteDisplayItems.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "该云端收藏夹为空",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    val feedImageUrls = remember(
                        uiState.selectedFolderId,
                        uiState.localDisplayItems,
                        uiState.remoteDisplayItems
                    ) {
                        if (uiState.selectedFolderId == null) {
                            uiState.localDisplayItems.map { it.postData.imageUrl }
                        } else {
                            uiState.remoteDisplayItems.map { it.postData.imageUrl }
                        }
                    }
                    PrefetchListImages(
                        imageUrls = feedImageUrls,
                        listState = listState,
                        sizePx = ListThumbImage.SIZE_PX,
                        ahead = 5,
                        initialDelayMs = 400
                    )

                    // 触底加载更多（本地收藏为全量数据，loadMoreRemote 在 VM 里对 null 夹直接返回）
                    val shouldLoadMore = remember {
                        derivedStateOf {
                            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                                ?: return@derivedStateOf false
                            last.index >= listState.layoutInfo.totalItemsCount - 3
                        }
                    }
                    LaunchedEffect(current, shouldLoadMore.value) {
                        if (current && shouldLoadMore.value) viewModel.loadMoreRemote()
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = 100.dp
                        )
                    ) {
                        if (uiState.selectedFolderId == null) {
                            items(
                                items = uiState.offlineSummaries,
                                key = { "o_${it.threadId}" },
                                contentType = { "offline_thread" }
                            ) { summary ->
                                OfflineFeedRow(
                                    summary = summary,
                                    forumName = Regex("^板块\\s*(\\d+)$").matchEntire(summary.forumName)?.groupValues?.get(1)?.let { forumNames[it] } ?: summary.forumName,
                                    isUpdating = summary.threadId in uiState.offlineUpdatingIds,
                                    onNavigateToThread = onNavigateToThread,
                                    onUpdate = {
                                        viewModel.updateOffline(summary) { msg ->
                                            android.widget.Toast.makeText(
                                                context, msg, android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    onDelete = { pendingOfflineDelete = summary }
                                )
                            }
                            items(
                                items = uiState.localDisplayItems,
                                key = { it.id },
                                contentType = { item ->
                                    if (item.postData.imageUrl != null) "bookmark_image" else "bookmark_text"
                                }
                            ) { item ->
                                LocalBookmarkRow(
                                    item = item,
                                    onNavigateToThread = onNavigateToThread,
                                    onUnsubscribe = { viewModel.unsubscribe(item.id) },
                                    onImageClick = { activeImageUrl = it },
                                    emptyStringLambda = emptyStringLambda
                                )
                            }
                        } else {
                            val folderId = uiState.selectedFolderId!!
                            items(
                                items = uiState.remoteDisplayItems,
                                key = { it.idStr },
                                contentType = { item ->
                                    if (item.hasImage) "remote_image" else "remote_text"
                                }
                            ) { item ->
                                RemoteBookmarkRow(
                                    item = item,
                                    onNavigateToThread = onNavigateToThread,
                                    onUnsubscribe = {
                                        viewModel.unsubscribeRemote(folderId, item.idStr)
                                    },
                                    onImageClick = { activeImageUrl = it },
                                    emptyStringLambda = emptyStringLambda
                                )
                            }
                        }
                    }
                }
            }
                }
            }
        }
    }
    activeImageUrl?.let { imageUrl ->
        ImageViewer(
            imageUrl = imageUrl,
            onDismiss = { activeImageUrl = null }
        )
    }
    }
}
@Composable
private fun OfflineFeedRow(
    summary: com.mioo.dao.data.local.OfflineThreadSummary,
    forumName: String,
    isUpdating: Boolean,
    onNavigateToThread: (String) -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val offlineImageDir = remember { java.io.File(context.filesDir, "offline_images") }
    com.mioo.dao.ui.screens.offline.OfflineThreadCard(
        summary = summary,
        forumName = forumName,
        isUpdating = isUpdating,
        offlineImageDir = offlineImageDir,
        onClick = { onNavigateToThread(summary.threadId) },
        onUpdate = onUpdate,
        onDelete = onDelete
    )
}

@Composable
private fun LocalBookmarkRow(
    item: BookmarkListItem,
    onNavigateToThread: (String) -> Unit,
    onUnsubscribe: () -> Unit,
    onImageClick: (String) -> Unit,
    emptyStringLambda: (String) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val onClick = remember(item.id) { { onNavigateToThread(item.id) } }

    Box {
        ThreadCard(
            postData = item.postData,
            replyCount = item.replyCount,
            onThreadClick = onClick,
            onQuoteClick = emptyStringLambda,
            onImageClick = onImageClick,
            onLongClick = { confirmDelete = true },
            compactPreviewBlankLines = true
        )
        if (item.newReplyCount > 0) {
            Badge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                containerColor = MaterialTheme.colorScheme.error
            ) {
                Text("+${item.newReplyCount}")
            }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                properties = immersiveDialogProperties(),
                title = { ImmersiveDialogEffect(); Text("取消订阅？") },
                text = { Text("将从订阅列表移除 No.${item.id}") },
                confirmButton = {
                    Button(onClick = { confirmDelete = false; onUnsubscribe() }) { Text("移除") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text("取消") }
                }
            )
        }
    }
}

@Composable
private fun RemoteBookmarkRow(
    item: ThreadListItem,
    onNavigateToThread: (String) -> Unit,
    onUnsubscribe: () -> Unit,
    onImageClick: (String) -> Unit,
    emptyStringLambda: (String) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val onClick = remember(item.idStr) { { onNavigateToThread(item.idStr) } }

    ThreadCard(
        postData = item.postData,
        replyCount = item.replyCount,
        onThreadClick = onClick,
        onQuoteClick = emptyStringLambda,
        onImageClick = onImageClick,
        onLongClick = { confirmDelete = true },
        compactPreviewBlankLines = true
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            properties = immersiveDialogProperties(),
            title = { ImmersiveDialogEffect(); Text("取消订阅？") },
            text = { Text("将从订阅列表移除 No.${item.idStr}") },
            confirmButton = {
                Button(onClick = { confirmDelete = false; onUnsubscribe() }) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}
