package com.mioo.dao.ui.screens.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.rememberAsyncImagePainter
import com.mioo.dao.ui.components.toFile
import com.mioo.dao.ui.components.KAOMOJI_LIST
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import com.mioo.dao.ui.components.FreeCopyDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import com.mioo.dao.data.model.Reply
import com.mioo.dao.data.model.XdWebSearch
import com.mioo.dao.ui.components.HtmlParseCache
import com.mioo.dao.ui.components.PostData
import com.mioo.dao.ui.components.PrefetchListImages
import com.mioo.dao.ui.components.ReplyDisplayItem
import com.mioo.dao.ui.components.StablePostList
import com.mioo.dao.ui.components.ReplyCard
import com.mioo.dao.ui.components.ThreadCard
import com.mioo.dao.ui.components.ImageViewer
import com.mioo.dao.ui.components.RefPopup
import com.mioo.dao.ui.screens.settings.SettingsViewModel
import com.mioo.dao.ui.theme.DaoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun ThreadScreen(
    viewModel: ThreadViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onNavigateToThread: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.settingsState.collectAsState()
    // Recreate list state when thread content first appears so continue-reading can
    // open at the saved index (no paint-at-top then scrollToItem hitch).
    val hasThreadContent = uiState.thread != null
    val listState = remember(viewModel.threadId, hasThreadContent) {
        LazyListState(
            firstVisibleItemIndex = (uiState.pendingScrollIndex ?: 0).coerceAtLeast(0),
            firstVisibleItemScrollOffset = 0
        )
    }
    val quoteCache = viewModel.quoteCache
    // Must re-read startPage when it changes (e.g. prepend earlier pages after resume).
    val listStartPage = uiState.startPage
    val totalPages = viewModel.totalPages
    val currentScrollPage = remember(listStartPage, totalPages) {
        derivedStateOf {
            val firstVisibleIndex = listState.firstVisibleItemIndex
            val page = if (firstVisibleIndex <= 0) {
                listStartPage
            } else {
                // index 0 is OP card; replies start at 1
                val replyIndex = firstVisibleIndex - 1
                listStartPage + (replyIndex / 19)
            }
            page.coerceIn(1, totalPages.coerceAtLeast(1))
        }
    }
    var activeImageUrl by remember { mutableStateOf<String?>(null) }
    var showBookmarkMenu by remember { mutableStateOf(false) }
    var freeCopyText by remember { mutableStateOf<String?>(null) }
    var showPageJumpDialog by remember { mutableStateOf(false) }
    var isSearchMode by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val emptyLambda = remember { {} }
    val quoteLinkColor = MaterialTheme.colorScheme.primary

    // Persist reading progress while scrolling / on leave
    LaunchedEffect(listState, viewModel.threadId) {
        snapshotFlow {
            listState.firstVisibleItemIndex to currentScrollPage.value
        }
            .distinctUntilChanged()
            .debounce(600)
            .collect { (index, page) ->
                viewModel.saveReadingProgress(page = page, firstVisibleIndex = index)
            }
    }
    DisposableEffect(viewModel.threadId, listState) {
        onDispose {
            viewModel.saveReadingProgress(
                page = currentScrollPage.value,
                firstVisibleIndex = listState.firstVisibleItemIndex
            )
        }
    }

    // Consume seed after open; only scrollToItem for mid-session jumps (list state already alive).
    LaunchedEffect(uiState.pendingScrollIndex, uiState.displayItems.size, uiState.isLoading, listState) {
        val index = uiState.pendingScrollIndex ?: return@LaunchedEffect
        if (uiState.isLoading) return@LaunchedEffect
        if (uiState.thread == null) return@LaunchedEffect

        // If LazyListState was created with this index, we are already there — no jump.
        if (listState.firstVisibleItemIndex != index) {
            val targetMinCount = index + 1
            var attempts = 0
            while (listState.layoutInfo.totalItemsCount < targetMinCount && attempts < 8) {
                delay(8)
                attempts++
            }
            val maxIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            val target = index.coerceIn(0, maxIndex)
            if (listState.firstVisibleItemIndex != target) {
                listState.scrollToItem(target)
            }
        }
        viewModel.consumePendingScrollIndex()
    }

    // HTML prewarm: only near-viewport first; rest later (less hitch on resume open)
    LaunchedEffect(uiState.displayItems, uiState.mainPostData?.content, quoteLinkColor, listState.firstVisibleItemIndex) {
        if (uiState.displayItems.isEmpty()) return@LaunchedEffect
        val bodies = uiState.displayItems.map { it.postData.content }
        val focus = listState.firstVisibleItemIndex.coerceAtLeast(0)
        val near = bodies.drop((focus - 1).coerceAtLeast(0)).take(8)
        val head = listOfNotNull(uiState.mainPostData?.content) + near
        withContext(Dispatchers.Default) {
            HtmlParseCache.prewarm(head, quoteLinkColor)
        }
        delay(450)
        withContext(Dispatchers.Default) {
            HtmlParseCache.prewarm(bodies, quoteLinkColor)
        }
    }

    LaunchedEffect(settingsState.blockedThreads) {
        if (settingsState.blockedThreads.contains(viewModel.threadId)) {
            Toast.makeText(context, "该帖子已被屏蔽", Toast.LENGTH_SHORT).show()
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchMode) {
                        androidx.compose.material3.TextField(
                            value = uiState.searchQuery,
                            onValueChange = { query ->
                                viewModel.updateSearchQuery(query)
                            },
                            placeholder = { Text("搜索文本或ID...", style = MaterialTheme.typography.bodyMedium) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
                        )
                    } else {
                        Text(
                            text = uiState.thread?.title ?: "详情",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    if (isSearchMode) {
                        IconButton(onClick = {
                            isSearchMode = false
                            viewModel.updateSearchQuery("")
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    } else {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (isSearchMode) {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "清除搜索")
                            }
                        }
                    } else {
                        IconButton(onClick = { isSearchMode = true }) {
                            Icon(Icons.Default.Search, contentDescription = "串内搜索")
                        }
                        IconButton(onClick = {
                            val tid = viewModel.threadId
                            val link = XdWebSearch.threadUrl(tid)
                            val title = uiState.thread?.title?.takeIf { it.isNotBlank() } ?: "No.$tid"
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, title)
                                putExtra(Intent.EXTRA_TEXT, "$title\n$link\nNo.$tid")
                            }
                            context.startActivity(Intent.createChooser(send, "分享串"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "分享")
                        }
                        if (viewModel.totalPages > 1) {
                            OutlinedButton(
                                onClick = { showPageJumpDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.padding(end = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MenuBook,
                                        contentDescription = "跳转页码",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${currentScrollPage.value}/${viewModel.totalPages}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = {
                                if (uiState.isSubscribed || uiState.feedFolders.isEmpty()) {
                                    viewModel.toggleBookmark(null) { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    showBookmarkMenu = true
                                }
                            }) {
                                Icon(
                                    imageVector = if (uiState.isSubscribed) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = if (uiState.isSubscribed) "取消收藏" else "收藏",
                                    tint = if (uiState.isSubscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            androidx.compose.material3.DropdownMenu(
                                expanded = showBookmarkMenu,
                                onDismissRequest = { showBookmarkMenu = false }
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("本地收藏") },
                                    onClick = {
                                        showBookmarkMenu = false
                                        viewModel.toggleBookmark(null) { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                                uiState.feedFolders.forEach { folder ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(folder.name) },
                                        onClick = {
                                            showBookmarkMenu = false
                                            viewModel.toggleBookmark(folder.uuid) { msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多选项")
                            }

                            androidx.compose.material3.DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("只看楼主") },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.togglePoOnly()
                                    },
                                    leadingIcon = {
                                        if (uiState.showPoOnly) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("只看图片") },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.toggleShowImagesOnly()
                                    },
                                    leadingIcon = {
                                        if (uiState.showImagesOnly) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                                androidx.compose.material3.HorizontalDivider()
                                androidx.compose.material3.DropdownMenuItem(
                                    text = {
                                        if (uiState.isDownloading) {
                                            Text("正在下载...")
                                        } else {
                                            Text("离线下载本串")
                                        }
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        if (!uiState.isDownloading) {
                                            Toast.makeText(context, "正在下载该串至本地...", Toast.LENGTH_SHORT).show()
                                            viewModel.downloadThread { msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null
                                        )
                                    },
                                    enabled = !uiState.isDownloading
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DaoTheme.colors.glassTopBar
                )
            )
        },
        bottomBar = {
            // Isolated collector: typing only recomposes the input bar, not the reply list
            ReplyInputBar(
                viewModel = viewModel,
                cookies = settingsState.cookiesList,
                selectedCookieIndex = settingsState.selectedCookieIndex,
                onCookieSelect = { settingsViewModel.selectCookie(it) }
            )
        },
        modifier = modifier.imePadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (uiState.isLoading && uiState.thread == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.errorMessage != null && uiState.thread == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.errorMessage ?: "发生错误",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                val displayItems = uiState.displayItems
                val poUserHash = uiState.thread?.userHash
                val mainThread = uiState.thread
                val mainPostData = uiState.mainPostData

                // Align image URL list with LazyColumn indices (optional main item at 0)
                val prefetchUrls = remember(mainPostData, displayItems) {
                    buildList(displayItems.size + 1) {
                        add(mainPostData?.imageUrl)
                        displayItems.forEach { add(it.postData.imageUrl) }
                    }
                }
                PrefetchListImages(
                    imageUrls = prefetchUrls,
                    listState = listState,
                    sizePx = 300,
                    ahead = 5,
                    initialDelayMs = 500
                )

                val shouldLoadMore = remember {
                    derivedStateOf {
                        val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                            ?: return@derivedStateOf false
                        lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 2
                    }
                }

                LaunchedEffect(shouldLoadMore.value) {
                    if (shouldLoadMore.value && !uiState.isLoading && !uiState.isLastPage) {
                        viewModel.loadNextPage()
                    }
                }

                // After mid-thread resume/jump, scrolling up loads earlier pages
                // so the page indicator can decrease with content.
                LaunchedEffect(listState, uiState.startPage, uiState.isLoading) {
                    snapshotFlow {
                        Triple(
                            listState.firstVisibleItemIndex,
                            listState.isScrollInProgress,
                            uiState.startPage
                        )
                    }
                        .distinctUntilChanged()
                        .collect { (index, scrolling, startPage) ->
                            if (index > 3) {
                                viewModel.allowPreviousLoad()
                            }
                            // Only request previous page while user is actively scrolling near top
                            if (scrolling &&
                                !uiState.isLoading &&
                                startPage > 1 &&
                                index <= 2
                            ) {
                                viewModel.loadPreviousPage()
                            }
                        }
                }

                // Keep viewport stable when older replies are prepended above
                LaunchedEffect(uiState.prependAnchorCount) {
                    val n = uiState.prependAnchorCount ?: return@LaunchedEffect
                    if (n > 0) {
                        val idx = listState.firstVisibleItemIndex
                        val offset = listState.firstVisibleItemScrollOffset
                        // index 0 is OP; only shift when user was already in the reply list
                        if (idx > 0) {
                            listState.scrollToItem(idx + n, offset)
                        }
                    }
                    viewModel.consumePrependAnchor()
                }

                // Stable callbacks shared across all list items
                val onQuoteClick = remember(viewModel) {
                    { quoteNo: String -> viewModel.showRefPopup(quoteNo) }
                }
                val onImageClick = remember {
                    { url: String -> activeImageUrl = url }
                }
                val onViewThreadClick = remember(onNavigateToThread) {
                    { tid: String -> onNavigateToThread(tid) }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = paddingValues.calculateBottomPadding() + 16.dp
                    )
                ) {
                    if (mainThread != null && mainPostData != null) {
                        item(key = "main_${mainThread.id}", contentType = "main_thread") {
                            var showBlockDialog by remember { mutableStateOf(false) }

                            if (showBlockDialog) {
                                PostActionDialog(
                                    title = "内容操作 No.${mainThread.idStr}",
                                    onDismiss = { showBlockDialog = false },
                                    onQuote = {
                                        viewModel.setQuotedPost(mainThread.idStr)
                                        Toast.makeText(context, "已引用 No.${mainThread.idStr}", Toast.LENGTH_SHORT).show()
                                    },
                                    onBlockThread = {
                                        settingsViewModel.addBlockedThread(mainThread.idStr)
                                    },
                                    onBlockUser = {
                                        settingsViewModel.addBlockedUser(mainThread.userHash)
                                    },
                                    onCopy = {
                                        freeCopyText = mainThread.content
                                    },
                                    blockThreadLabel = "屏蔽此串 (No.${mainThread.idStr})",
                                    blockUserLabel = "屏蔽该发言饼干 (ID: ${mainThread.userHash})",
                                    onShareCard = {
                                        scope.launch {
                                            com.mioo.dao.utils.ShareCardUtil.sharePostCard(context, mainPostData)
                                        }
                                    }
                                )
                            }

                            ThreadCard(
                                postData = mainPostData,
                                replyCount = mainThread.replyCount ?: 0,
                                onThreadClick = emptyLambda,
                                onQuoteClick = onQuoteClick,
                                onImageClick = onImageClick,
                                onLongClick = { showBlockDialog = true }
                            )
                        }
                    }

                    items(
                        items = displayItems,
                        key = { it.id },
                        contentType = { item ->
                            if (item.hasImage) "reply_image" else "reply_text"
                        }
                    ) { item ->
                        ThreadReplyRow(
                            item = item,
                            quoteCache = quoteCache,
                            poUserHash = poUserHash,
                            currentThreadId = viewModel.threadId,
                            onQuoteClick = onQuoteClick,
                            onImageClick = onImageClick,
                            onViewThreadClick = onViewThreadClick,
                            onCardClick = emptyLambda,
                            onRequestQuote = {
                                viewModel.setQuotedPost(item.idStr)
                                Toast.makeText(context, "已引用 No.${item.idStr}", Toast.LENGTH_SHORT).show()
                            },
                            onBlockThread = {
                                settingsViewModel.addBlockedThread(viewModel.threadId)
                            },
                            onBlockUser = {
                                settingsViewModel.addBlockedUser(item.userHash)
                            },
                            onFreeCopy = { freeCopyText = item.rawContent }
                        )
                    }

                    if (uiState.isLoading && displayItems.isNotEmpty()) {
                        item(key = "loading_footer", contentType = "loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }

            freeCopyText?.let { text ->
                FreeCopyDialog(
                    text = text,
                    onDismiss = { freeCopyText = null }
                )
            }

            val imageUrls = remember(uiState.displayItems, uiState.mainPostData) {
                val list = mutableListOf<String>()
                uiState.mainPostData?.imageUrl?.let { list.add(it) }
                uiState.displayItems.forEach { item ->
                    item.postData.imageUrl?.let { list.add(it) }
                }
                list.distinct()
            }

            // Image Viewer Overlay
            activeImageUrl?.let { imageUrl ->
                ImageViewer(
                    initialImageUrl = imageUrl,
                    imageUrls = imageUrls,
                    onDismiss = { activeImageUrl = null }
                )
            }

            // Ref Popup Dialog Overlay
            uiState.refPostId?.let { refId ->
                RefPopup(
                    postId = refId,
                    postData = uiState.refPostData,
                    isLoading = uiState.isRefLoading,
                    errorMessage = uiState.refError,
                    onDismiss = remember { { viewModel.dismissRefPopup() } },
                    onQuoteClick = remember { { quoteNo: String -> viewModel.showRefPopup(quoteNo) } },
                    onImageClick = remember { { url: String -> activeImageUrl = url } },
                    onViewThreadClick = remember { { threadId: String ->
                        viewModel.dismissRefPopup()
                        onNavigateToThread(threadId)
                    } },
                    currentThreadId = viewModel.threadId
                )
            }
        }
    }

    // Page Jump Dialog
    if (showPageJumpDialog) {
        PageJumpDialog(
            currentPage = currentScrollPage.value,
            totalPages = viewModel.totalPages,
            onDismiss = { showPageJumpDialog = false },
            onJump = { page ->
                viewModel.jumpToPage(page)
                showPageJumpDialog = false
            }
        )
    }
}

@Composable
fun PageNavigator(
    currentPage: Int,
    totalPages: Int,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onJumpClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onPrevPage,
            enabled = currentPage > 1
        ) {
            Text("< 上一页", style = MaterialTheme.typography.labelMedium)
        }

        OutlinedButton(
            onClick = onJumpClick,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = "$currentPage / $totalPages",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }

        TextButton(
            onClick = onNextPage,
            enabled = currentPage < totalPages
        ) {
            Text("下一页 >", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun PageJumpDialog(
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳转页码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Quick buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { onJump(1) },
                        enabled = currentPage > 1,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("首页", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = { onJump(currentPage - 1) },
                        enabled = currentPage > 1,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("上一页", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = { onJump(currentPage + 1) },
                        enabled = currentPage < totalPages,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("下一页", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = { onJump(totalPages) },
                        enabled = currentPage < totalPages,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("末页", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Manual input
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { newValue ->
                        inputText = newValue.filter { it.isDigit() }
                    },
                    label = { Text("页码 (1 - $totalPages)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val page = inputText.toIntOrNull()
                    if (page != null && page in 1..totalPages) {
                        onJump(page)
                    }
                },
                enabled = inputText.isNotEmpty() && inputText.toIntOrNull()?.let { it in 1..totalPages } == true
            ) {
                Text("跳转")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * Extract a human-readable display name from a cookie JSON string.
 * Returns the "name" field if available, otherwise falls back to "cookie" or "userhash".
 * Returns the raw cookie string if it's not valid JSON.
 */
fun parseCookieName(cookie: String): String {
    return try {
        if (cookie.startsWith("{")) {
            val json = org.json.JSONObject(cookie)
            var name = json.optString("name", "")
            if (name.isEmpty()) name = json.optString("cookie", "")
            if (name.isEmpty()) name = json.optString("userhash", cookie)
            name
        } else {
            cookie
        }
    } catch (e: Exception) {
        cookie
    }
}

/**
 * Single reply row: only recomposes when this item's data or its observed quote keys change.
 */
@Composable
private fun ThreadReplyRow(
    item: ReplyDisplayItem,
    quoteCache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Reply>,
    poUserHash: String?,
    currentThreadId: String,
    onQuoteClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onViewThreadClick: (String) -> Unit,
    onCardClick: () -> Unit,
    onRequestQuote: () -> Unit,
    onBlockThread: () -> Unit,
    onBlockUser: () -> Unit,
    onFreeCopy: () -> Unit
) {
    // Per-key observation: filling quote X does not recompose rows that only need Y
    val quotedReplies = if (item.quoteIds.isEmpty()) {
        emptyList()
    } else {
        item.quoteIds.mapNotNull { id -> quoteCache[id] }
    }
    val quoteLinkColor = MaterialTheme.colorScheme.primary
    LaunchedEffect(quotedReplies, quoteLinkColor) {
        if (quotedReplies.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                HtmlParseCache.prewarm(quotedReplies.map { it.content }, quoteLinkColor)
            }
        }
    }
    val quotedPostsData = remember(item.quoteIds, quotedReplies, poUserHash) {
        StablePostList(
            quotedReplies.map { quote ->
                val isFollowUp = quote.resto != null && quote.resto > 0L
                PostData(
                    id = quote.idStr,
                    title = quote.title ?: "",
                    userName = quote.name ?: "无名氏",
                    userId = quote.userHash,
                    createdAt = quote.now,
                    content = quote.content,
                    imageUrl = if (quote.imageUrl != null) {
                        "https://image.nmb.best/image/${quote.imageUrl}"
                    } else null,
                    isPo = quote.userHash == poUserHash,
                    isAdmin = quote.isAdmin,
                    isSage = quote.isSage,
                    resto = if (isFollowUp) quote.resto.toString() else quote.idStr
                )
            }
        )
    }

    val rowContext = LocalContext.current
    val rowScope = rememberCoroutineScope()
    var showReplyBlockDialog by remember { mutableStateOf(false) }
    if (showReplyBlockDialog) {
        PostActionDialog(
            title = "内容操作 No.${item.idStr}",
            onDismiss = { showReplyBlockDialog = false },
            onQuote = onRequestQuote,
            onBlockThread = onBlockThread,
            onBlockUser = onBlockUser,
            onCopy = onFreeCopy,
            blockThreadLabel = "屏蔽此串 (No.$currentThreadId)",
            blockUserLabel = "屏蔽该发言饼干 (ID: ${item.userHash})",
            onShareCard = {
                rowScope.launch {
                    com.mioo.dao.utils.ShareCardUtil.sharePostCard(rowContext, item.postData)
                }
            }
        )
    }

    ReplyCard(
        postData = item.postData,
        onQuoteClick = onQuoteClick,
        onImageClick = onImageClick,
        onCardClick = onCardClick,
        onCardLongClick = { showReplyBlockDialog = true },
        quotedPosts = quotedPostsData,
        onViewThreadClick = onViewThreadClick,
        currentThreadId = currentThreadId
    )
}

@Composable
private fun PostActionDialog(
    title: String,
    onDismiss: () -> Unit,
    onQuote: () -> Unit,
    onBlockThread: () -> Unit,
    onBlockUser: () -> Unit,
    onCopy: () -> Unit,
    blockThreadLabel: String,
    blockUserLabel: String,
    onShareCard: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { onQuote(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("引用该帖子") }
                if (onShareCard != null) {
                    TextButton(
                        onClick = { onShareCard(); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("分享为卡片") }
                }
                TextButton(
                    onClick = { onBlockThread(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(blockThreadLabel) }
                TextButton(
                    onClick = { onBlockUser(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(blockUserLabel) }
                TextButton(
                    onClick = { onCopy(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("自由复制内容") }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("取消") }
            }
        },
        confirmButton = {}
    )
}

/**
 * Owns [ThreadViewModel.composerState] collection so keystrokes never invalidate
 * the parent [ThreadScreen] composition scope / LazyColumn.
 */
@Composable
private fun ReplyInputBar(
    viewModel: ThreadViewModel,
    cookies: List<String>,
    selectedCookieIndex: Int,
    onCookieSelect: (Int) -> Unit
) {
    val composerState by viewModel.composerState.collectAsState()
    ReplyInputArea(
        replyText = composerState.replyText,
        quotedPostNo = composerState.quotedPostNo,
        cookies = cookies,
        selectedCookieIndex = selectedCookieIndex,
        isReplying = composerState.isReplying,
        replyError = composerState.replyError,
        onCookieSelect = onCookieSelect,
        onReplyTextChange = remember(viewModel) { { text: String -> viewModel.updateReplyText(text) } },
        onCancelQuote = remember(viewModel) { { viewModel.setQuotedPost(null) } },
        onSend = remember(viewModel, cookies, selectedCookieIndex) {
            { file: java.io.File? ->
                val currentCookie = cookies.getOrNull(selectedCookieIndex) ?: "无名氏"
                viewModel.submitReply(author = parseCookieName(currentCookie), imageFile = file)
            }
        }
    )
}

@Composable
fun ReplyInputArea(
    replyText: String,
    quotedPostNo: String?,
    cookies: List<String>,
    selectedCookieIndex: Int,
    isReplying: Boolean = false,
    replyError: String? = null,
    onCookieSelect: (Int) -> Unit,
    onReplyTextChange: (String) -> Unit,
    onCancelQuote: () -> Unit,
    onSend: (java.io.File?) -> Unit
) {
    var cookieMenuExpanded by remember { mutableStateOf(false) }
    var kaomojiMenuExpanded by remember { mutableStateOf(false) }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var wasReplying by remember { mutableStateOf(false) }

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = replyText,
                selection = TextRange(replyText.length)
            )
        )
    }

    LaunchedEffect(replyText) {
        if (textFieldValue.text != replyText) {
            textFieldValue = textFieldValue.copy(
                text = replyText,
                selection = TextRange(replyText.length)
            )
        }
    }

    LaunchedEffect(isReplying) {
        if (wasReplying && !isReplying && replyError == null) {
            attachedImageUri = null
        }
        wasReplying = isReplying
    }

    LaunchedEffect(replyText) {
        if (replyText.isEmpty()) {
            attachedImageUri = null
        }
    }

    // Show error toast when replyError changes
    androidx.compose.runtime.LaunchedEffect(replyError) {
        if (replyError != null) {
            android.widget.Toast.makeText(context, "回复失败: $replyError", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    Surface(
        color = DaoTheme.colors.glassNavBar,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            AnimatedVisibility(visible = quotedPostNo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "正在回复引用: >>No.$quotedPostNo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    IconButton(
                        onClick = onCancelQuote,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "取消引用",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Image Preview (if attached)
            AnimatedVisibility(visible = attachedImageUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(attachedImageUri),
                            contentDescription = "Preview",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = { attachedImageUri = null },
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.TopEnd)
                                .background(Color.Black.copy(alpha = 0.6f), shape = androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Remove",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            // 1. Outlined Text Field (full width)
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                    onReplyTextChange(newValue.text)
                },
                placeholder = { Text("回复...", style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            kaomojiMenuExpanded = false
                        }
                    },
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Control row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val imagePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    attachedImageUri = uri
                }

                // Cookie selector
                val currentCookie = cookies.getOrNull(selectedCookieIndex) ?: "选择饼干"
                val displayName = remember(currentCookie) { parseCookieName(currentCookie) }
                val displayText = if (displayName.length > 8) displayName.take(8) + "..." else displayName

                Box {
                    OutlinedButton(
                        onClick = { cookieMenuExpanded = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(displayText, style = MaterialTheme.typography.labelLarge)
                    }
                    DropdownMenu(
                        expanded = cookieMenuExpanded,
                        onDismissRequest = { cookieMenuExpanded = false }
                    ) {
                        cookies.forEachIndexed { index, cookie ->
                            val isSelected = index == selectedCookieIndex
                            val itemDisplayName = remember(cookie) { parseCookieName(cookie) }
                            val itemDisplayText = if (itemDisplayName.length > 8) itemDisplayName.take(8) + "..." else itemDisplayName
                            
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = itemDisplayText,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    ) 
                                },
                                onClick = {
                                    onCookieSelect(index)
                                    cookieMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Smiley face (Face)
                IconButton(onClick = { 
                    kaomojiMenuExpanded = !kaomojiMenuExpanded 
                    if (kaomojiMenuExpanded) {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "选择颜文字",
                        tint = if (kaomojiMenuExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Attach Image
                IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "添加图片",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Send Button
                val isSendActive = (replyText.isNotBlank() || attachedImageUri != null) && !isReplying
                val sendIconColor = if (isSendActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                
                IconButton(
                    onClick = { 
                        val file = attachedImageUri?.toFile(context)
                        onSend(file)
                    },
                    enabled = isSendActive
                ) {
                    if (isReplying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "发送",
                            tint = sendIconColor
                        )
                    }
                }
            }

            // 3. Kaomoji Panel Row
            AnimatedVisibility(visible = kaomojiMenuExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(180.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val rows = KAOMOJI_LIST.chunked(3)
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { kaomoji ->
                                TextButton(
                                    onClick = {
                                        val text = textFieldValue.text
                                        val selection = textFieldValue.selection
                                        val start = selection.start
                                        val end = selection.end
                                        val newText = text.substring(0, start) + kaomoji + text.substring(end)
                                        val newCursorPos = start + kaomoji.length
                                        
                                        textFieldValue = TextFieldValue(
                                            text = newText,
                                            selection = TextRange(newCursorPos)
                                        )
                                        onReplyTextChange(newText)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = kaomoji,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            // Fill empty spaces if row has < 3 elements
                            if (row.size < 3) {
                                repeat(3 - row.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
