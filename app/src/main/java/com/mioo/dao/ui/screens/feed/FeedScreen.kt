package com.mioo.dao.ui.screens.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mioo.dao.ui.screens.forum.ForumViewModel
import com.mioo.dao.ui.theme.readingBackground
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mioo.dao.ui.components.BookmarkListItem
import com.mioo.dao.data.local.CloudFeedThreadEntity
import com.mioo.dao.ui.components.HtmlParseCache
import com.mioo.dao.ui.components.ImageViewer
import com.mioo.dao.ui.components.ListThumbImage
import com.mioo.dao.ui.components.PrefetchListImages
import com.mioo.dao.ui.components.ResponsiveListFling
import com.mioo.dao.ui.components.ThreadCard
import com.mioo.dao.ui.components.ThreadListItem
import com.mioo.dao.ui.components.pagerHorizontalSwipeArbitration
import com.mioo.dao.ui.components.slowFlingClick
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.ui.theme.readingTopBarColor
import com.mioo.dao.ui.theme.MiooMotion
import com.mioo.dao.ui.theme.ImmersiveDialogEffect
import com.mioo.dao.ui.theme.immersiveDialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    onNavigateToThread: (threadId: String) -> Unit,
    onNavigateToOfflineThread: (String) -> Unit = onNavigateToThread,
    onImageViewerOpenChange: (Boolean) -> Unit = {},
    isActiveTab: () -> Boolean = { true },
    modifier: Modifier = Modifier
) {
    var activeImageUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeImageUrl) { onImageViewerOpenChange(activeImageUrl != null) }
    val uiState by viewModel.uiState.collectAsState()
    if (!uiState.selectionReady) return
    val browseState = rememberSaveableStateHolder()
    val forumViewModel: ForumViewModel = hiltViewModel()
    val forumGroups by forumViewModel.forumGroups.collectAsState()
    LaunchedEffect(forumGroups.isEmpty()) {
        if (forumGroups.isEmpty()) forumViewModel.loadForumGroups()
    }
    val forumNames = remember(forumGroups) {
        forumGroups.flatMap { it.forums }.associate { it.id to it.name }
    }
    val cachedFolderPreviews by viewModel.cachedFolderPreviews.collectAsState()
    val folderIds = remember(uiState.feedFolders) { listOf(null) + uiState.feedFolders.map { it.uuid } }
    val pager = rememberPagerState(initialPage = folderIds.indexOf(uiState.selectedFolderId).coerceAtLeast(0)) { folderIds.size }
    val listStates = remember { mutableStateMapOf<String, LazyListState>() }
    val pageSnapshots = remember { mutableStateMapOf<String, FeedUiState>() }
    fun pageKey(id: String?) = id ?: "__offline__"
    SideEffect {
        if (!uiState.isLoading || uiState.remoteDisplayItems.isNotEmpty()) pageSnapshots[pageKey(uiState.selectedFolderId)] = uiState
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
    var pageSwitchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val haptics = LocalHapticFeedback.current
    val emptyStringLambda = remember { { _: String -> } }
    val quoteLinkColor = MaterialTheme.colorScheme.primary
    // 长按/删除按钮弹出的离线串删除确认
    var pendingOfflineDelete by remember {
        mutableStateOf<com.mioo.dao.data.local.OfflineThreadSummary?>(null)
    }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    var requestSearchFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val navigateToThread by rememberUpdatedState(onNavigateToThread)
    fun hideSearchKeyboard() {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }
    fun closeSearch() {
        requestSearchFocus = false
        hideSearchKeyboard()
        searching = false
        query = ""
    }
    val openThread: (String) -> Unit = remember(focusManager, keyboard) {
        { id ->
            if (searching) hideSearchKeyboard()
            navigateToThread(id)
        }
    }
    BackHandler(enabled = searching && activeImageUrl == null && pendingOfflineDelete == null && isActiveTab()) {
        closeSearch()
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
    val prewarmDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    LaunchedEffect(
        uiState.selectedFolderId,
        uiState.localDisplayItems,
        uiState.remoteDisplayItems,
        quoteLinkColor,
        prewarmDarkTheme
    ) {
        val htmls = if (uiState.selectedFolderId == null) {
            uiState.localDisplayItems.map { it.postData.content }
        } else {
            uiState.remoteDisplayItems.map { it.postData.content }
        }
        if (htmls.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                HtmlParseCache.prewarm(htmls, quoteLinkColor, prewarmDarkTheme)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus)
                                .onGloballyPositioned {
                                    if (requestSearchFocus) {
                                        requestSearchFocus = false
                                        searchFocus.requestFocus()
                                    }
                                },
                            singleLine = true,
                            placeholder = { Text("搜索收藏标题或内容") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { hideSearchKeyboard() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    } else {
                        Text("我的收藏", style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * 1.125f), fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (searching) closeSearch()
                        else {
                            requestSearchFocus = true
                            searching = true
                        }
                    }) {
                        Icon(
                            if (searching) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searching) "关闭搜索" else "搜索收藏"
                        )
                    }
                    if (!searching) IconButton(onClick = {
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
                    containerColor = readingTopBarColor()
                )
            )
        },
        modifier = modifier,
        containerColor = readingBackground(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val searchQuery = query.trim()
            if (searchQuery.isNotEmpty()) {
                FeedTitleSearch(
                    query = searchQuery,
                    viewModel = viewModel,
                    state = uiState,
                    onOpenThread = openThread,
                    onImageClick = { activeImageUrl = it }
                )
            } else browseState.SaveableStateProvider("browse") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                folderIds.forEachIndexed { index, id ->
                    val selected = pager.targetPage == index
                    Surface(onClick = {
                        if (!selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            pageSwitchJob?.cancel()
                            pageSwitchJob = scope.launch {
                                pager.animateScrollToPage(index, animationSpec = androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                            }
                        }
                    }, shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
                        Text(if (id == null) "已离线" else uiState.feedFolders.firstOrNull { it.uuid == id }?.name.orEmpty(),
                            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            fontSize = 13.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false)
                    }
                }
            }

            val pagerTouchGuard = com.mioo.dao.ui.components.rememberTouchGuardConfiguration(
                com.mioo.dao.ui.components.LocalScrollTouchGuardEnabled.current
            )
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.foundation.LocalOverscrollConfiguration provides null,
                androidx.compose.ui.platform.LocalViewConfiguration provides pagerTouchGuard
            ) {
            HorizontalPager(
                state = pager,
                beyondBoundsPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .pagerHorizontalSwipeArbitration(
                        pagerState = pager,
                        currentListState = { listStates[pageKey(folderIds.getOrNull(pager.currentPage))] },
                        scope = scope
                    ),
                key = { pageKey(folderIds[it]) }
            ) { page ->
                val folderId = folderIds[page]
                val current = folderId == uiState.selectedFolderId
                val cachedRows by remember(viewModel, folderId) {
                    if (folderId == null) flowOf(emptyList())
                    else viewModel.cloudIndex.map { it.byFolder[folderId].orEmpty() }.distinctUntilChanged()
                }.collectAsState(
                    initial = if (folderId == null) emptyList() else viewModel.cloudIndex.value.byFolder[folderId].orEmpty()
                )
                val pageState = if (current) uiState else pageSnapshots[pageKey(folderId)] ?: run {
                    val preview = folderId?.let { cachedFolderPreviews[it] }.orEmpty()
                    FeedUiState(
                        selectedFolderId = folderId,
                        remoteDisplayItems = preview,
                        isLoading = folderId != null && preview.isEmpty()
                    )
                }
                val listState = rememberSaveable(pageKey(folderId), saver = LazyListState.Saver) { LazyListState() }
                SideEffect { listStates[pageKey(folderId)] = listState }
                val uiState = pageState
                val offlineTransfers by viewModel.offlineTransfers.collectAsState()
                val offlineItems = uiState.offlineSummaries
                val localItems = uiState.localDisplayItems
                val remoteItems = uiState.remoteDisplayItems
                val cachedById = remember(cachedRows) { cachedRows.associateBy { it.threadId } }
            when {
                uiState.isLoading && remoteItems.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.selectedFolderId == null &&
                    uiState.offlineSummaries.isEmpty() && offlineTransfers.isEmpty() &&
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
                uiState.remoteLoadFailed && remoteItems.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("收藏夹加载失败", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { viewModel.refreshRemote() }, enabled = current) {
                                Text("重试")
                            }
                        }
                    }
                }
                uiState.selectedFolderId != null && !uiState.isLoading && remoteItems.isEmpty() -> {
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
                    val feedImageUrls by produceState<List<String?>>(
                        emptyList(),
                        uiState.selectedFolderId,
                        localItems,
                        remoteItems
                    ) {
                        value = withContext(Dispatchers.Default) {
                            if (uiState.selectedFolderId == null) {
                                localItems.map { it.postData.imageUrl }
                            } else {
                                remoteItems.map { it.postData.imageUrl }
                            }
                        }
                    }
                    PrefetchListImages(
                        imageUrls = feedImageUrls,
                        listState = listState,
                        sizePx = ListThumbImage.SIZE_PX,
                        ahead = 5,
                        initialDelayMs = 400,
                        enabled = current && isActiveTab()
                    )

                    // 触底加载更多（本地收藏为全量数据，loadMoreRemote 在 VM 里对 null 夹直接返回）
                    val shouldLoadMore = remember(listState, remoteItems.size, cachedRows.size) {
                        derivedStateOf {
                            val lastLoadedRow = remoteItems.size - 1
                            lastLoadedRow >= 0 && listState.layoutInfo.visibleItemsInfo.any {
                                it.index in (lastLoadedRow - 2)..lastLoadedRow
                            }
                        }
                    }
                    LaunchedEffect(current, remoteItems.size, uiState.loadedRemotePage, shouldLoadMore.value) {
                        if (current && shouldLoadMore.value) viewModel.loadMoreRemote()
                    }

                    val responsiveFling = com.mioo.dao.ui.components.rememberResponsiveListFling()
                    LazyColumn(
                        state = listState,
                        flingBehavior = responsiveFling,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 12.dp,
                            bottom = 100.dp
                        )
                    ) {
                        if (uiState.selectedFolderId == null) {
                            items(offlineTransfers.entries.toList(), key = { "transfer_${it.key}" }) { (id, transfer) ->
                                com.mioo.dao.ui.screens.offline.OfflineTransferRow(id, transfer) { onNavigateToOfflineThread(id) }
                            }
                            items(
                                items = offlineItems,
                                key = { "o_${it.threadId}" },
                                contentType = { "offline_thread" }
                            ) { summary ->
                                OfflineFeedRow(
                                    summary = summary,
                                    forumName = FALLBACK_FORUM_NAME_REGEX.matchEntire(summary.forumName)?.groupValues?.get(1)?.let { forumNames[it] } ?: summary.forumName,
                                    isUpdating = offlineTransfers[summary.threadId]?.let { it.error == null } == true,
                                    onNavigateToThread = onNavigateToOfflineThread,
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
                                items = localItems,
                                key = { it.id },
                                contentType = { item ->
                                    if (item.postData.imageUrl != null) "bookmark_image" else "bookmark_text"
                                }
                            ) { item ->
                                val forumName = item.forumId
                                    ?.takeIf { it.isNotBlank() && it != "0" }
                                    ?.let { fid -> forumNames[fid] ?: "板块 $fid" }
                                LocalBookmarkRow(
                                    item = item,
                                    forumName = forumName,
                                    fling = responsiveFling,
                                    onNavigateToThread = openThread,
                                    onUnsubscribe = { viewModel.unsubscribe(item.id) },
                                    onImageClick = { activeImageUrl = it },
                                    emptyStringLambda = emptyStringLambda
                                )
                            }
                        } else {
                            val folderId = uiState.selectedFolderId!!
                            items(
                                items = remoteItems,
                                key = { it.idStr },
                                contentType = { item ->
                                    if (item.hasImage) "remote_image" else "remote_text"
                                }
                            ) { item ->
                                val cachedCount = cachedById[item.idStr]?.replyCount ?: 0
                                val forumName = item.forumId
                                    ?.takeIf { it.isNotBlank() && it != "0" }
                                    ?.let { fid -> forumNames[fid] ?: "板块 $fid" }
                                RemoteBookmarkRow(
                                    item = if (cachedCount > 0 && cachedCount != item.replyCount)
                                        item.copy(replyCount = cachedCount) else item,
                                    forumName = forumName,
                                    fling = responsiveFling,
                                    onNavigateToThread = openThread,
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
    }
    activeImageUrl?.let { imageUrl ->
        ImageViewer(
            imageUrl = imageUrl,
            onDismiss = { activeImageUrl = null }
        )
    }
    }
}
private val FALLBACK_FORUM_NAME_REGEX = Regex("^板块\\s*(\\d+)$")

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
private fun FeedTitleSearch(
    query: String,
    viewModel: FeedViewModel,
    state: FeedUiState,
    onOpenThread: (String) -> Unit,
    onImageClick: (String) -> Unit
) {
    val cloudIndexSnapshot by viewModel.cloudIndex.collectAsState()
    val fullyIndexedCloudFolders by viewModel.fullyIndexedCloudFolders.collectAsState()
    val cachedThreads by viewModel.cachedFeedPosts.collectAsState()
    val cloudIndex = cloudIndexSnapshot.entries
    val indexingIncomplete = state.feedFolders.any { it.uuid !in fullyIndexedCloudFolders }
    val titleIndex by produceState<FeedTitleIndex?>(
        null, cloudIndex, state.feedFolders, state.offlineSummaries, state.bookmarkedThreads, cachedThreads
    ) {
        value = withContext(Dispatchers.Default) {
            buildFeedTitleIndex(
                cloudIndex,
                state.feedFolders,
                state.offlineSummaries,
                state.bookmarkedThreads,
                cachedThreads
            )
        }
    }
    val results by produceState<Pair<String, List<FeedTitleMatch>>?>(
        null, query, titleIndex
    ) {
        val index = titleIndex ?: return@produceState
        if (value?.first != query) delay(150)
        value = query to withContext(Dispatchers.Default) {
            index.search(query)
        }
    }
    val rows = results?.takeIf { it.first == query }?.second
    val listState = remember(query) { LazyListState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val offlineImageDir = remember { java.io.File(context.filesDir, "offline_images") }
    val responsiveConfiguration = com.mioo.dao.ui.components.rememberResponsiveListConfiguration()
    val responsiveFling = com.mioo.dao.ui.components.rememberResponsiveListFling()
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalViewConfiguration provides responsiveConfiguration
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            flingBehavior = responsiveFling,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 12.dp, 12.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (rows?.isEmpty() == true) {
                item {
                    Text(
                        if (indexingIncomplete) "暂未找到匹配的收藏，部分云端收藏尚未同步"
                        else "没有匹配的收藏",
                        Modifier.fillMaxWidth().padding(top = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            items(rows.orEmpty(), key = { it.threadId }) { row ->
                FeedSearchResultCard(
                    match = row,
                    fling = responsiveFling,
                    offlineImageDir = offlineImageDir,
                    onClick = { onOpenThread(row.threadId) },
                    onImageClick = onImageClick
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedSearchResultCard(
    match: FeedTitleMatch,
    fling: ResponsiveListFling,
    offlineImageDir: java.io.File,
    onClick: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val meta = com.mioo.dao.ui.theme.readingMetaStyle()
    val bodyStyle = com.mioo.dao.ui.theme.readingBodyStyle()
    val onOpen = remember(match.threadId, onClick) { onClick }
    val sourceLabel = remember(match.sources) { match.sources.joinToString(" · ") }
    val dateLabel = remember(match.createdAt, match.timestamp) {
        when {
            match.createdAt.isNotBlank() -> com.mioo.dao.ui.components.formatReadingTime(match.createdAt)
            match.timestamp > 0L -> com.mioo.dao.ui.components.formatReadingTimestamp(match.timestamp, includeSeconds = false)
            else -> ""
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .slowFlingClick(fling, onOpen)
            .combinedClickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = com.mioo.dao.ui.theme.readingCardColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = com.mioo.dao.ui.theme.readingCardBorder()
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                com.mioo.dao.ui.components.ReadingIdentity(
                    com.mioo.dao.ui.components.PostData(
                        id = match.threadId,
                        userId = match.userId.ifBlank { "无名氏" },
                        createdAt = "",
                        content = "",
                        isPo = false,
                        isAdmin = match.userId.equals("Admin", ignoreCase = true)
                    )
                )
                if (sourceLabel.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    com.mioo.dao.ui.components.ReadingPill(sourceLabel, maxWidth = 140)
                }
                if (dateLabel.isNotBlank()) {
                    Text(
                        " · $dateLabel",
                        modifier = Modifier.weight(1f),
                        style = meta.copy(fontWeight = FontWeight.Normal),
                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "No.${match.threadId}",
                    style = meta.copy(fontWeight = FontWeight.Normal),
                    maxLines = 1,
                    softWrap = false,
                    color = scheme.onSurfaceVariant
                )
            }
            if (match.title != null || !match.htmlContent.isNullOrBlank() || !match.plainContent.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
            }
            match.title?.let { title ->
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!match.htmlContent.isNullOrBlank() || !match.plainContent.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                }
            }
            if (!match.htmlContent.isNullOrBlank()) {
                com.mioo.dao.ui.components.HtmlContent(
                    match.htmlContent,
                    onQuoteClick = {},
                    style = bodyStyle,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    enableGestures = false,
                    compactBlankLines = true
                )
            } else if (!match.plainContent.isNullOrBlank()) {
                Text(
                    match.plainContent,
                    style = bodyStyle,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val remoteImage = match.imageUrl
            if (!remoteImage.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                com.mioo.dao.ui.components.ListThumbAsyncImage(
                    imageUrl = remoteImage,
                    contentDescription = "串附图",
                    preserveRatio = true,
                    modifier = Modifier.clickable { onImageClick(remoteImage) }
                )
            } else if (!match.coverFile.isNullOrBlank()) {
                val coverFile = remember(match.coverFile, offlineImageDir) {
                    java.io.File(offlineImageDir, match.coverFile)
                }
                var coverFailed by remember(match.coverFile, offlineImageDir) { mutableStateOf(false) }
                if (!coverFailed) {
                    Spacer(Modifier.height(10.dp))
                    com.mioo.dao.ui.components.LocalImagePreview(
                        coverFile,
                        onError = { coverFailed = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalBookmarkRow(
    item: BookmarkListItem,
    forumName: String?,
    fling: ResponsiveListFling,
    onNavigateToThread: (String) -> Unit,
    onUnsubscribe: () -> Unit,
    onImageClick: (String) -> Unit,
    emptyStringLambda: (String) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val onClick = remember(item.id) { { onNavigateToThread(item.id) } }

    Box {
        ThreadCard(
            modifier = Modifier.slowFlingClick(fling, onClick),
            postData = item.postData,
            replyCount = item.replyCount,
            forumName = forumName,
            onThreadClick = onClick,
            onQuoteClick = emptyStringLambda,
            onImageClick = onImageClick,
            onLongClick = { confirmDelete = true },
            compactPreviewBlankLines = true,
            showReplyCount = false
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteBookmarkRow(
    item: ThreadListItem,
    forumName: String?,
    fling: ResponsiveListFling,
    onNavigateToThread: (String) -> Unit,
    onUnsubscribe: () -> Unit,
    onImageClick: (String) -> Unit,
    emptyStringLambda: (String) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val onClick = remember(item.idStr) { { onNavigateToThread(item.idStr) } }
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .slowFlingClick(fling, onClick)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { confirmDelete = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = com.mioo.dao.ui.theme.readingCardColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.mioo.dao.ui.components.ReadingIdentity(item.postData)
                if (!forumName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    com.mioo.dao.ui.components.ReadingPill(forumName)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "No.${item.idStr}",
                    style = com.mioo.dao.ui.theme.readingMetaStyle(),
                    maxLines = 1,
                    softWrap = false,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            item.postData.title?.takeIf { it.isNotBlank() && it != "无标题" }?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            com.mioo.dao.ui.components.HtmlContent(
                html = item.postData.content,
                onQuoteClick = emptyStringLambda,
                style = com.mioo.dao.ui.theme.readingBodyStyle(),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                enableGestures = false,
                compactBlankLines = true
            )

            item.postData.imageUrl?.let { imageUrl ->
                Spacer(modifier = Modifier.height(10.dp))
                val thumbInteraction = remember { MutableInteractionSource() }
                val onThumbClick = remember(imageUrl, onImageClick) { { onImageClick(imageUrl) } }
                com.mioo.dao.ui.components.ListThumbAsyncImage(
                    imageUrl = imageUrl,
                    contentDescription = "串附图",
                    preserveRatio = true,
                    modifier = Modifier.clickable(
                        interactionSource = thumbInteraction,
                        indication = null,
                        onClick = onThumbClick
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val formattedTime = remember(item.postData.createdAt) {
                    com.mioo.dao.ui.components.formatReadingTime(item.postData.createdAt)
                }
                if (formattedTime.isNotBlank()) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = { confirmDelete = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "取消云端订阅",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
    if (confirmDelete) {
        RemoteUnsubscribeDialog(item.idStr, onDismiss = { confirmDelete = false }, onUnsubscribe = onUnsubscribe)
    }
}

@Composable
private fun RemoteUnsubscribeDialog(threadId: String, onDismiss: () -> Unit, onUnsubscribe: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = immersiveDialogProperties(),
        title = { ImmersiveDialogEffect(); Text("取消订阅？") },
        text = { Text("将从订阅列表移除 No.$threadId") },
        confirmButton = {
            Button(onClick = { onDismiss(); onUnsubscribe() }) { Text("移除") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
