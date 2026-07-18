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
import androidx.compose.material.icons.filled.Casino
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
import com.mioo.dao.ui.components.ComposerToolButtons
import com.mioo.dao.ui.components.DiceQuickPanel
import com.mioo.dao.ui.components.KaomojiQuickPanel
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
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
import com.mioo.dao.ui.components.toPostData
import com.mioo.dao.data.model.effectiveTitle
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
    // List-only slice — bookmark / download / ref-popup use separate collectors below
    val listUi by viewModel.listUiState.collectAsState()
    // Slice only cookies + blocked — ignore theme/font/preload churn
    val threadSettings by settingsViewModel.threadScreenSettings.collectAsState()
    // Recreate list state when thread content first appears so continue-reading can
    // open at the saved index (no paint-at-top then scrollToItem hitch).
    val hasThreadContent = listUi.thread != null
    val listState = remember(viewModel.threadId, hasThreadContent) {
        LazyListState(
            firstVisibleItemIndex = (listUi.pendingScrollIndex ?: 0).coerceAtLeast(0),
            firstVisibleItemScrollOffset = 0
        )
    }
    val quoteCache = viewModel.quoteCache
    // Must re-read startPage when it changes (e.g. prepend earlier pages after resume).
    val listStartPage = listUi.startPage
    val totalPages = viewModel.totalPages
    // Kept for progress save only — do NOT read .value in this scope (would recompose whole screen on scroll).
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
    LaunchedEffect(listUi.pendingScrollIndex, listUi.displayItems.size, listUi.isLoading, listState) {
        val index = listUi.pendingScrollIndex ?: return@LaunchedEffect
        if (listUi.isLoading) return@LaunchedEffect
        if (listUi.thread == null) return@LaunchedEffect

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

    // HTML prewarm: do NOT key on firstVisibleItemIndex (restarts every scroll frame).
    // Warm head once when content changes; settle-scroll for a light near-viewport pass.
    LaunchedEffect(listUi.displayItems, listUi.mainPostData?.content, quoteLinkColor) {
        if (listUi.displayItems.isEmpty()) return@LaunchedEffect
        val bodies = listUi.displayItems.map { it.postData.content }
        val head = listOfNotNull(listUi.mainPostData?.content) + bodies.take(8)
        withContext(Dispatchers.Default) {
            HtmlParseCache.prewarm(head, quoteLinkColor)
        }
        // Let enter-transition / first frame finish before bulk prewarm
        delay(500)
        withContext(Dispatchers.Default) {
            HtmlParseCache.prewarm(bodies.take(40), quoteLinkColor)
        }
    }

    // Settled-scroll near-viewport warm only (no mid-fling restart)
    LaunchedEffect(listState, listUi.displayItems, quoteLinkColor) {
        if (listUi.displayItems.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            listState.isScrollInProgress to listState.firstVisibleItemIndex
        }
            .distinctUntilChanged()
            .collect { (scrolling, focus) ->
                if (scrolling) return@collect
                val bodies = listUi.displayItems.map { it.postData.content }
                val near = bodies.drop((focus - 1).coerceAtLeast(0)).take(10)
                if (near.isEmpty()) return@collect
                withContext(Dispatchers.Default) {
                    HtmlParseCache.prewarm(near, quoteLinkColor)
                }
            }
    }

    LaunchedEffect(threadSettings.blockedThreads) {
        if (threadSettings.blockedThreads.contains(viewModel.threadId)) {
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
                            value = listUi.searchQuery,
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
                            text = listUi.thread?.title.effectiveTitle() ?: "详情",
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
                        if (listUi.searchQuery.isNotEmpty()) {
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
                            val title = listUi.thread?.title.effectiveTitle() ?: "No.$tid"
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, title)
                                putExtra(Intent.EXTRA_TEXT, "$title\n$link\nNo.$tid")
                            }
                            context.startActivity(Intent.createChooser(send, "分享串"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "分享")
                        }
                        if (totalPages > 1) {
                            ThreadPageIndicatorButton(
                                listState = listState,
                                listStartPage = listStartPage,
                                totalPages = totalPages,
                                onClick = { showPageJumpDialog = true }
                            )
                        }
                        // Chrome (bookmark / folders / download) collected inside — not listUi
                        ThreadChromeActions(
                            viewModel = viewModel,
                            showPoOnly = listUi.showPoOnly,
                            showImagesOnly = listUi.showImagesOnly,
                            showBookmarkMenu = showBookmarkMenu,
                            onShowBookmarkMenuChange = { showBookmarkMenu = it },
                            showOverflowMenu = showOverflowMenu,
                            onShowOverflowMenuChange = { showOverflowMenu = it }
                        )
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
                cookies = threadSettings.cookiesList,
                selectedCookieIndex = threadSettings.selectedCookieIndex,
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
            if (listUi.isLoading && listUi.thread == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (listUi.errorMessage != null && listUi.thread == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = listUi.errorMessage ?: "发生错误",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                val displayItems = listUi.displayItems
                val poUserHash = listUi.thread?.userHash
                val mainThread = listUi.thread
                val mainPostData = listUi.mainPostData

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
                    sizePx = com.mioo.dao.ui.components.ListThumbImage.SIZE_PX,
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
                    if (shouldLoadMore.value && !listUi.isLoading && !listUi.isLastPage) {
                        viewModel.loadNextPage()
                    }
                }

                // After mid-thread resume/jump, scrolling up loads earlier pages
                // so the page indicator can decrease with content.
                LaunchedEffect(listState, listUi.startPage, listUi.isLoading) {
                    snapshotFlow {
                        Triple(
                            listState.firstVisibleItemIndex,
                            listState.isScrollInProgress,
                            listUi.startPage
                        )
                    }
                        .distinctUntilChanged()
                        .collect { (index, scrolling, startPage) ->
                            if (index > 3) {
                                viewModel.allowPreviousLoad()
                            }
                            // Only request previous page while user is actively scrolling near top
                            if (scrolling &&
                                !listUi.isLoading &&
                                startPage > 1 &&
                                index <= 2
                            ) {
                                viewModel.loadPreviousPage()
                            }
                        }
                }

                // Keep viewport stable when older replies are prepended above
                LaunchedEffect(listUi.prependAnchorCount) {
                    val n = listUi.prependAnchorCount ?: return@LaunchedEffect
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

                val replyIds = remember(displayItems) { displayItems.map { it.idStr }.toSet() }

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
                            // Finer slots improve Lazy reuse for quote-heavy replies
                            val q = if (item.quoteIds.isNotEmpty()) "q" else "n"
                            val i = if (item.hasImage) "i" else "t"
                            "reply_${q}_$i"
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
                            onFreeCopy = { freeCopyText = item.rawContent },
                            replyIds = replyIds
                        )
                    }

                    if (listUi.isLoading && displayItems.isNotEmpty()) {
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

            val imageUrls = remember(listUi.displayItems, listUi.mainPostData) {
                val list = mutableListOf<String>()
                listUi.mainPostData?.imageUrl?.let { list.add(it) }
                listUi.displayItems.forEach { item ->
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

            // Own collector: open/close ref never invalidates the LazyColumn parent
            ThreadRefPopupHost(
                viewModel = viewModel,
                onImageClick = { activeImageUrl = it },
                onNavigateToThread = onNavigateToThread
            )
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

/**
 * Collects [ThreadViewModel.chromeUiState] so bookmark / download toggles do not
 * recompose the list body (which only watches [ThreadViewModel.listUiState]).
 */
@Composable
private fun ThreadChromeActions(
    viewModel: ThreadViewModel,
    showPoOnly: Boolean,
    showImagesOnly: Boolean,
    showBookmarkMenu: Boolean,
    onShowBookmarkMenuChange: (Boolean) -> Unit,
    showOverflowMenu: Boolean,
    onShowOverflowMenuChange: (Boolean) -> Unit
) {
    val chrome by viewModel.chromeUiState.collectAsState()
    val context = LocalContext.current

    Box {
        IconButton(onClick = {
            if (chrome.isSubscribed || chrome.feedFolders.isEmpty()) {
                viewModel.toggleBookmark(null) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            } else {
                onShowBookmarkMenuChange(true)
            }
        }) {
            Icon(
                imageVector = if (chrome.isSubscribed) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = if (chrome.isSubscribed) "取消收藏" else "收藏",
                tint = if (chrome.isSubscribed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }

        DropdownMenu(
            expanded = showBookmarkMenu,
            onDismissRequest = { onShowBookmarkMenuChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text("本地收藏") },
                onClick = {
                    onShowBookmarkMenuChange(false)
                    viewModel.toggleBookmark(null) { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
            chrome.feedFolders.forEach { folder ->
                DropdownMenuItem(
                    text = { Text(folder.name) },
                    onClick = {
                        onShowBookmarkMenuChange(false)
                        viewModel.toggleBookmark(folder.uuid) { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    Box {
        IconButton(onClick = { onShowOverflowMenuChange(true) }) {
            Icon(Icons.Default.MoreVert, contentDescription = "更多选项")
        }

        DropdownMenu(
            expanded = showOverflowMenu,
            onDismissRequest = { onShowOverflowMenuChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text("只看楼主") },
                onClick = {
                    onShowOverflowMenuChange(false)
                    viewModel.togglePoOnly()
                },
                leadingIcon = {
                    if (showPoOnly) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("只看图片") },
                onClick = {
                    onShowOverflowMenuChange(false)
                    viewModel.toggleShowImagesOnly()
                },
                leadingIcon = {
                    if (showImagesOnly) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
            androidx.compose.material3.HorizontalDivider()
            DropdownMenuItem(
                text = {
                    if (chrome.isDownloading) {
                        Text("正在下载...")
                    } else {
                        Text("离线下载本串")
                    }
                },
                onClick = {
                    onShowOverflowMenuChange(false)
                    if (!chrome.isDownloading) {
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
                enabled = !chrome.isDownloading
            )
        }
    }
}

/**
 * Isolated ref-popup collector — filling / dismissing never touches list composition.
 */
@Composable
private fun ThreadRefPopupHost(
    viewModel: ThreadViewModel,
    onImageClick: (String) -> Unit,
    onNavigateToThread: (String) -> Unit
) {
    val ref by viewModel.refPopupUiState.collectAsState()
    val refId = ref.refPostId ?: return
    RefPopup(
        postId = refId,
        postData = ref.refPostData,
        isLoading = ref.isRefLoading,
        errorMessage = ref.refError,
        onDismiss = remember(viewModel) { { viewModel.dismissRefPopup() } },
        onQuoteClick = remember(viewModel) { { quoteNo: String -> viewModel.showRefPopup(quoteNo) } },
        onImageClick = onImageClick,
        onViewThreadClick = remember(viewModel, onNavigateToThread) {
            { threadId: String ->
                viewModel.dismissRefPopup()
                onNavigateToThread(threadId)
            }
        },
        currentThreadId = viewModel.threadId
    )
}

/**
 * Isolated page chip — reading [LazyListState.firstVisibleItemIndex] here only
 * recomposes this button, not the thread LazyColumn / Scaffold body.
 */
@Composable
private fun ThreadPageIndicatorButton(
    listState: LazyListState,
    listStartPage: Int,
    totalPages: Int,
    onClick: () -> Unit
) {
    val page by remember(listStartPage, totalPages) {
        derivedStateOf {
            val firstVisibleIndex = listState.firstVisibleItemIndex
            val raw = if (firstVisibleIndex <= 0) {
                listStartPage
            } else {
                val replyIndex = firstVisibleIndex - 1
                listStartPage + (replyIndex / 19)
            }
            raw.coerceIn(1, totalPages.coerceAtLeast(1))
        }
    }
    OutlinedButton(
        onClick = onClick,
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
                text = "$page/$totalPages",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
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
    onFreeCopy: () -> Unit,
    replyIds: Set<String>
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
    val quotedPostsData = remember(item.quoteIds, quotedReplies, poUserHash, replyIds, currentThreadId) {
        if (quotedReplies.isEmpty()) {
            StablePostList(emptyList())
        } else {
            StablePostList(
                quotedReplies.map { quote ->
                    val base = quote.toPostData(
                        isPo = quote.userHash == poUserHash,
                        cdnUrl = "https://image.nmb.best"
                    )
                    val isFollowUp = quote.resto != null && quote.resto > 0L
                    val isCurrentThreadReply = replyIds.contains(quote.idStr)
                    val targetResto = when {
                        isFollowUp -> quote.resto.toString()
                        isCurrentThreadReply -> currentThreadId
                        quote.idStr == currentThreadId -> currentThreadId
                        else -> quote.idStr
                    }
                    base.copy(resto = targetResto)
                }
            )
        }
    }

    // Warm ContentBlockSplitter cache off the main thread before ReplyCard remembers blocks
    LaunchedEffect(item.rawContent, quotedPostsData) {
        if (quotedPostsData.list.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            com.mioo.dao.ui.components.ContentBlockSplitter.split(
                item.rawContent,
                quotedPostsData.list
            )
        }
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
                ) { Text("引用该串") }
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
    var diceMenuExpanded by remember { mutableStateOf(false) }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var ignoreImeCloseUntilMs by remember { mutableStateOf(0L) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    var wasReplying by remember { mutableStateOf(false) }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = replyText, selection = TextRange(replyText.length)))
    }

    val hideSystemIme = remember(view, context, keyboardController, focusManager) {
        {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            val token = (context as? android.app.Activity)?.currentFocus?.windowToken
                ?: view.windowToken
                ?: (context as? android.app.Activity)?.window?.decorView?.windowToken
            if (token != null) {
                imm?.hideSoftInputFromWindow(token, 0)
            }
        }
    }

    fun insertAtCursor(snippet: String) {
        val text = textFieldValue.text
        val selection = textFieldValue.selection
        val start = selection.start.coerceIn(0, text.length)
        val end = selection.end.coerceIn(0, text.length)
        val prefix = if (start > 0 && !text[start - 1].isWhitespace()) " " else ""
        val insert = prefix + snippet
        val newText = text.substring(0, start) + insert + text.substring(end)
        val newCursor = start + insert.length
        textFieldValue = TextFieldValue(
            text = newText,
            selection = TextRange(newCursor)
        )
        onReplyTextChange(newText)
    }

    fun closeToolPanels() {
        if (kaomojiMenuExpanded) kaomojiMenuExpanded = false
        if (diceMenuExpanded) diceMenuExpanded = false
    }

    // 系统键盘再次抬起 → 关闭快捷面板
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeVisible = imeBottomPx > with(density) { 48.dp.toPx() }
    LaunchedEffect(imeVisible) {
        if (!imeVisible) return@LaunchedEffect
        if (android.os.SystemClock.uptimeMillis() < ignoreImeCloseUntilMs) return@LaunchedEffect
        if (kaomojiMenuExpanded || diceMenuExpanded) {
            closeToolPanels()
        }
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
                        text = "正在引用: >>No.$quotedPostNo",
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
                            diceMenuExpanded = false
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

                // 骰娘 · 颜文字 · 图片（同组）
                ComposerToolButtons(
                    diceSelected = diceMenuExpanded,
                    kaomojiSelected = kaomojiMenuExpanded,
                    hasImage = attachedImageUri != null,
                    onDiceClick = {
                        val open = !diceMenuExpanded
                        diceMenuExpanded = open
                        if (open) {
                            kaomojiMenuExpanded = false
                            ignoreImeCloseUntilMs = android.os.SystemClock.uptimeMillis() + 450L
                            hideSystemIme()
                        }
                    },
                    onKaomojiClick = {
                        val open = !kaomojiMenuExpanded
                        kaomojiMenuExpanded = open
                        if (open) {
                            diceMenuExpanded = false
                            ignoreImeCloseUntilMs = android.os.SystemClock.uptimeMillis() + 450L
                            hideSystemIme()
                        }
                    },
                    onImageClick = {
                        closeToolPanels()
                        imagePickerLauncher.launch("image/*")
                    }
                )

                Spacer(modifier = Modifier.width(4.dp))

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

            if (diceMenuExpanded) {
                DiceQuickPanel(
                    onInsert = { insertAtCursor(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(200.dp)
                )
            }
            if (kaomojiMenuExpanded) {
                KaomojiQuickPanel(
                    onInsert = { insertAtCursor(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    heightDp = 180
                )
            }
        }
    }
}
