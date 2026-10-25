package com.mioo.dao.ui.screens.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.mioo.dao.ui.theme.MiooMotion
import com.mioo.dao.ui.theme.isReducedMotionEnabled
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoStories
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextRange
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.rememberAsyncImagePainter
import com.mioo.dao.ui.components.toFile
import com.mioo.dao.ui.components.rememberEffectiveImeBottomPx
import com.mioo.dao.ui.components.ComposerToolButtons
import com.mioo.dao.ui.components.DiceDialog
import com.mioo.dao.ui.components.KaomojiQuickPanel
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.sp
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
import android.content.ClipData
import android.content.ClipboardManager
import com.mioo.dao.ui.components.IslandImage
import com.mioo.dao.ui.components.toPostData
import com.mioo.dao.data.model.effectiveTitle
import com.mioo.dao.ui.screens.settings.SettingsViewModel
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.ui.theme.ImmersiveDialogEffect
import com.mioo.dao.ui.theme.immersiveDialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
        // pendingScrollIndex 的索引模型：0=OP、1..=回复；跳到第2页及以上时 OP 不占位
        val seeded = (listUi.pendingScrollIndex ?: 0).coerceAtLeast(0)
        val lazyIndex = if (listUi.startPage > 1) (seeded - 1).coerceAtLeast(0) else seeded
        LazyListState(
            firstVisibleItemIndex = lazyIndex,
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
                // startPage==1 时 index 0 是 OP 卡；跳页/续读落在第2页及以上时 OP 不占位
                val replyIndex = if (listStartPage <= 1) firstVisibleIndex - 1 else firstVisibleIndex
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
    // Local text field value — do not write listUi on every keystroke
    var searchFieldText by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val emptyLambda = remember { {} }
    val quoteLinkColor = MaterialTheme.colorScheme.primary
    // Keep the opening frame to chrome + spinner. The reply list is what stalls
    // the first frames of the slide on a cold process.
    val reducedMotion = isReducedMotionEnabled()
    var allowHeavyList by remember { mutableStateOf(reducedMotion) }
    LaunchedEffect(reducedMotion) {
        if (!reducedMotion) {
            // A frame past the slide, so list measure does not land on the last animation frame.
            delay(MiooMotion.PageDurationMillis.toLong() + 32L)
        }
        allowHeavyList = true
    }

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

        // pendingScrollIndex：0=OP、1..=回复；第2页及以上 OP 不占位，目标减一
        val lazyIndex = if (listUi.startPage > 1) (index - 1).coerceAtLeast(0) else index

        // If LazyListState was created with this index, we are already there — no jump.
        if (listState.firstVisibleItemIndex != lazyIndex) {
            val targetMinCount = lazyIndex + 1
            var attempts = 0
            while (listState.layoutInfo.totalItemsCount < targetMinCount && attempts < 8) {
                delay(8)
                attempts++
            }
            val maxIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            val target = lazyIndex.coerceIn(0, maxIndex)
            if (listState.firstVisibleItemIndex != target) {
                listState.scrollToItem(target)
            }
        }
        viewModel.consumePendingScrollIndex()
    }

    // HTML prewarm: key on content signature, not the list instance (avoids restart on
    // every StateFlow emission that rebuilds an equal list). Head first, then bulk.
    // darkTheme 必须与 HtmlContent 消费端一致，否则暗色下缓存 key 永远不命中，预热全白做
    val prewarmDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val threadPrewarmSig = remember(
        listUi.displayItems.size,
        listUi.displayItems.firstOrNull()?.id,
        listUi.displayItems.lastOrNull()?.id,
        listUi.mainPostData?.content,
        prewarmDarkTheme
    ) {
        "${listUi.displayItems.size}:${listUi.displayItems.firstOrNull()?.id}:" +
            "${listUi.displayItems.lastOrNull()?.id}:${listUi.mainPostData?.content?.length ?: 0}"
    }
    LaunchedEffect(threadPrewarmSig, quoteLinkColor) {
        if (listUi.displayItems.isEmpty()) return@LaunchedEffect
        val bodies = listUi.displayItems.map { it.postData.content }
        // Head only on open — bulk of the page is warmed by settle-scroll effect below
        val head = listOfNotNull(listUi.mainPostData?.content) + bodies.take(10)
        withContext(Dispatchers.Default) {
            HtmlParseCache.prewarm(head, quoteLinkColor, prewarmDarkTheme)
        }
        delay(350)
        // Cap bulk warm so long threads don't burn Default while user is already scrolling
        withContext(Dispatchers.Default) {
            HtmlParseCache.prewarm(bodies.take(20), quoteLinkColor, prewarmDarkTheme)
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
                    HtmlParseCache.prewarm(near, quoteLinkColor, prewarmDarkTheme)
                }
            }
    }

    LaunchedEffect(threadSettings.blockedThreads) {
        if (threadSettings.blockedThreads.contains(viewModel.threadId)) {
            Toast.makeText(context, "该帖子已被屏蔽", Toast.LENGTH_SHORT).show()
            onBackClick()
        }
    }

    // Debounce filter into VM — keystrokes only recompose the top bar TextField
    LaunchedEffect(searchFieldText, isSearchMode) {
        if (!isSearchMode) return@LaunchedEffect
        delay(220)
        viewModel.updateSearchQuery(searchFieldText)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchMode) {
                        androidx.compose.material3.TextField(
                            value = searchFieldText,
                            onValueChange = { query ->
                                searchFieldText = query
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
                    // 上下双行：页码放副标题行，不再与主标题争抢横向宽度
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = listUi.thread?.title.effectiveTitle() ?: "详情",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 15.sp,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (totalPages > 1) {
                            Text(
                                text = "第 ${currentScrollPage.value} / $totalPages 页",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    lineHeight = 11.sp,
                                    fontFeatureSettings = "tnum",
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
                },
                navigationIcon = {
                    if (isSearchMode) {
                        IconButton(onClick = {
                            isSearchMode = false
                            searchFieldText = ""
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
                        if (searchFieldText.isNotEmpty()) {
                            IconButton(onClick = {
                                searchFieldText = ""
                                viewModel.updateSearchQuery("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "清除搜索")
                            }
                        }
                    } else {
                        IconButton(
                            onClick = {
                                isSearchMode = true
                                searchFieldText = listUi.searchQuery
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "串内搜索")
                        }
                        if (totalPages > 1) {
                            ThreadPageJumpButton(
                                totalPages = totalPages,
                                currentPage = currentScrollPage.value,
                                onJumpToPage = { viewModel.jumpToPage(it) },
                                onOpenInputDialog = { showPageJumpDialog = true }
                            )
                        }
                        // Chrome (Po filter / bookmark / folders / download) collected inside — not listUi
                        ThreadChromeActions(
                            viewModel = viewModel,
                            showPoOnly = listUi.showPoOnly,
                            showImagesOnly = listUi.showImagesOnly,
                            onShare = {
                                val tid = viewModel.threadId
                                // 分享统一用主站域名
                                val link = XdWebSearch.threadUrlAlt(tid)
                                val title = listUi.thread?.title.effectiveTitle() ?: "No.$tid"
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, title)
                                    putExtra(Intent.EXTRA_TEXT, "$title\n$link\nNo.$tid")
                                }
                                context.startActivity(Intent.createChooser(send, "分享串"))
                            },
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
            // 底栏自己处理 ime / 导航条间距，避免整页 Scaffold 再垫一次。
            ReplyInputBar(
                viewModel = viewModel,
                cookies = threadSettings.cookiesList,
                selectedCookieIndex = viewModel.threadCookieIndex.collectAsState().value
                    ?.takeIf { it < threadSettings.cookiesList.size }
                    ?: threadSettings.selectedCookieIndex,
                onCookieSelect = { viewModel.selectThreadCookie(it) }
            )
        },
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (!allowHeavyList || (listUi.isLoading && listUi.thread == null)) {
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
                        // prepend 只发生在 startPage>1 时 → 补偿后 startPage==1 说明串首卡片
                        // 同时出现在了顶部，额外多移一位
                        val opAppeared = listUi.startPage <= 1
                        listState.scrollToItem(idx + n + if (opAppeared) 1 else 0, offset)
                    }
                    viewModel.consumePrependAnchor()
                }

                // Stable callbacks shared across all list items
                val onQuoteClick = remember(viewModel) {
                    { quoteNo: String -> viewModel.showRefPopup(quoteNo) }
                }
                val onImageClick = remember {
                    { url: String -> activeImageUrl = IslandImage.fullSize(url) }
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
                    // 跳页/续读落在第 2 页及以上时不再重复展示串首卡片，列表直接以该页回复开头；
                    // 向上翻回第 1 页时串首卡片随 startPage==1 重新出现
                    if (mainThread != null && mainPostData != null && listUi.startPage <= 1) {
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
                                    blockUserLabel = "屏蔽该饼干 (ID: ${mainThread.userHash})",
                                    onReport = { copyThreadReportLink(context, mainThread.idStr) },
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
                                onLongClick = { showBlockDialog = true },
                                // 详情页主楼：完整正文 + 引用链接可点（列表预览才截断/关手势）
                                contentMaxLines = Int.MAX_VALUE,
                                enableHtmlGestures = true
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
                            inlineQuotesEnabled = threadSettings.quoteAutoDisplayEnabled,
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

                    val filterHint = listUi.filterHint
                    if (!filterHint.isNullOrBlank() && !listUi.isLoading) {
                        item(key = "filter_hint", contentType = "hint") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = filterHint,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!listUi.isLastPage) {
                                    TextButton(onClick = { viewModel.loadNextPage(resetFilterSkip = true) }) {
                                        Text("继续加载下一页")
                                    }
                                }
                            }
                        }
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
    onShare: () -> Unit,
    showBookmarkMenu: Boolean,
    onShowBookmarkMenuChange: (Boolean) -> Unit,
    showOverflowMenu: Boolean,
    onShowOverflowMenuChange: (Boolean) -> Unit
) {
    val chrome by viewModel.chromeUiState.collectAsState()
    val context = LocalContext.current

    // 只看 Po：[HD]/[CC] 式线性徽章；高亮色只代表已激活的过滤/收藏状态
    IconButton(
        onClick = { viewModel.togglePoOnly() },
        modifier = Modifier.size(40.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 22.dp, height = 18.dp)
                .then(
                    if (showPoOnly) {
                        Modifier.background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(4.dp)
                        )
                    } else {
                        Modifier.border(
                            1.8.dp,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "PO",
                color = if (showPoOnly) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontSize = 9.5.sp,
                    lineHeight = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both
                    )
                )
            )
        }
    }

    Box {
        // 已收藏 = 本地收藏 或 云端收藏夹成员（缓存）
        val bookmarked = chrome.isSubscribed || chrome.isCloudSubscribed
        IconButton(
            onClick = {
                when {
                    // 本地已收藏 → 直接取消本地收藏（原有行为）
                    chrome.isSubscribed -> viewModel.toggleBookmark(null) { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    // 仅云端收藏（或配置了云端文件夹）→ 打开菜单，供选择本地/云端操作
                    chrome.isCloudSubscribed || chrome.feedFolders.isNotEmpty() ->
                        onShowBookmarkMenuChange(true)
                    else -> viewModel.toggleBookmark(null) { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = if (bookmarked) "取消收藏" else "收藏",
                tint = if (bookmarked) {
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
            if (chrome.isCloudSubscribed) {
                DropdownMenuItem(
                    text = { Text("从云端收藏夹移除") },
                    onClick = {
                        onShowBookmarkMenuChange(false)
                        viewModel.removeCloudSubscription { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
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
        IconButton(
            onClick = { onShowOverflowMenuChange(true) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = "更多选项")
        }

        DropdownMenu(
            expanded = showOverflowMenu,
            onDismissRequest = { onShowOverflowMenuChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text("分享") },
                onClick = {
                    onShowOverflowMenuChange(false)
                    onShare()
                },
                leadingIcon = {
                    Icon(Icons.Default.Share, contentDescription = null)
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
        canGoBack = ref.canGoBack,
        onBack = remember(viewModel) { { viewModel.popRefPopup() } },
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
 * 跳页入口：24dp 图标与搜索/收藏图标同基线；点击弹出滑块快捷跳页气泡。
 * 当前页常驻展示在顶栏 title 侧（currentScrollPage），不在此处堆叠文字。
 */
@Composable
private fun ThreadPageJumpButton(
    totalPages: Int,
    currentPage: Int,
    onJumpToPage: (Int) -> Unit,
    onOpenInputDialog: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // 拖动预览页码；每次打开气泡时重置为当前页
    var sliderPage by remember(expanded) { mutableStateOf(currentPage.toFloat()) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoStories,
                contentDescription = "跳转页码",
                modifier = Modifier.size(24.dp),
                tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(272.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // 顶部：页码状态 + 精确输入入口
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "第 ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${sliderPage.toInt()}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = " / $totalPages 页",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            expanded = false
                            onOpenInputDialog()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("输入页码", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // 中部：进度滑块（拖动只预览，松手才跳页）
                Slider(
                    value = sliderPage,
                    onValueChange = { sliderPage = it },
                    onValueChangeFinished = {
                        val target = sliderPage.toInt().coerceIn(1, totalPages)
                        if (target != currentPage) {
                            expanded = false
                            onJumpToPage(target)
                        }
                    },
                    valueRange = 1f..totalPages.toFloat().coerceAtLeast(1f)
                )

                // 底部：四枚快捷直达
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = { expanded = false; onJumpToPage(1) },
                        enabled = currentPage > 1,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("首页", style = MaterialTheme.typography.labelMedium) }
                    TextButton(
                        onClick = { expanded = false; onJumpToPage(currentPage - 1) },
                        enabled = currentPage > 1,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("上一页", style = MaterialTheme.typography.labelMedium) }
                    TextButton(
                        onClick = { expanded = false; onJumpToPage(currentPage + 1) },
                        enabled = currentPage < totalPages,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("下一页", style = MaterialTheme.typography.labelMedium) }
                    TextButton(
                        onClick = { expanded = false; onJumpToPage(totalPages) },
                        enabled = currentPage < totalPages,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("末页", style = MaterialTheme.typography.labelMedium) }
                }
            }
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
        properties = immersiveDialogProperties(),
        title = {
            ImmersiveDialogEffect()
            Text("跳转页码")
        },
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
    inlineQuotesEnabled: Boolean,
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
    val quotedReplies = if (!inlineQuotesEnabled || item.quoteIds.isEmpty()) {
        emptyList()
    } else {
        item.quoteIds.mapNotNull { id -> quoteCache[id] }
    }
    // Resolve resto via API fields only — avoids rebuilding a reply-id Set on every page append.
    // No per-row LaunchedEffect prewarm: HtmlParseCache.getOrParse + ContentBlockSplitter
    // fill on first bind; row-level effects caused Default thrash during fling.
    val quotedPostsData = remember(item.quoteIds, quotedReplies, poUserHash, currentThreadId) {
        if (quotedReplies.isEmpty()) {
            StablePostList(emptyList())
        } else {
            StablePostList(
                quotedReplies.map { quote ->
                    val base = quote.toPostData(
                        isPo = quote.userHash == poUserHash
                    )
                    val targetResto = when {
                        quote.resto != null && quote.resto > 0L -> quote.resto.toString()
                        quote.idStr == currentThreadId -> currentThreadId
                        else -> quote.idStr
                    }
                    base.copy(resto = targetResto)
                }
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
            blockUserLabel = "屏蔽该饼干 (ID: ${item.userHash})",
            onReport = { copyThreadReportLink(rowContext, currentThreadId) },
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
    onReport: () -> Unit,
    blockThreadLabel: String,
    blockUserLabel: String,
    onShareCard: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = immersiveDialogProperties(),
        title = {
            ImmersiveDialogEffect()
            Text(title)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { onQuote(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("引用回复") }
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
                ) { Text("复制文本") }
                TextButton(
                    onClick = { onReport(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("举报") }
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
        onSend = remember(viewModel) {
            { file: java.io.File? ->
                viewModel.submitReply(imageFile = file)
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
    var showDiceDialog by remember { mutableStateOf(false) }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var previewAttachedUri by remember { mutableStateOf<Uri?>(null) }
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
    }

    val toolPanelOpen = kaomojiMenuExpanded
    BackHandler(enabled = toolPanelOpen) {
        closeToolPanels()
    }

    // 【共享底部高度槽位】底部总占位恒取 max(键盘, 面板)，绝不相加——
    // 从颜文字/骰子面板切到系统键盘时，槽位只在两个值之间平滑取大变化，永不过冲。
    val imeBottomPx = rememberEffectiveImeBottomPx()
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density)
    var rememberedImeHeightPx by remember { mutableIntStateOf(0) }
    val defaultPanelHeightPx = with(density) { 280.dp.roundToPx() }
    // 面板高度跟随用户键盘实际高度（键盘升过一次后记录），否则兜底 280dp
    val targetPanelHeightPx =
        if (rememberedImeHeightPx > navBarBottomPx) rememberedImeHeightPx else defaultPanelHeightPx
    val isKeyboardRising = imeBottomPx > navBarBottomPx + with(density) { 40.dp.roundToPx() }
    LaunchedEffect(imeBottomPx, isKeyboardRising) {
        if (imeBottomPx > rememberedImeHeightPx) {
            rememberedImeHeightPx = imeBottomPx
        }
        // 键盘升起 → 同帧关面板（单一动画源）。这是键盘主动升起，不是用户点面板按钮。
        // 仍尊重刚点开面板后的 450ms 窗口：从键盘态点开面板会先 hideSystemIme，
        // 键盘下落动画期间 isKeyboardRising 同样为 true，无窗口会把刚打开的面板立刻关掉。
        if (isKeyboardRising) {
            if (android.os.SystemClock.uptimeMillis() < ignoreImeCloseUntilMs) return@LaunchedEffect
            if (kaomojiMenuExpanded) kaomojiMenuExpanded = false
        }
    }
    // 【核心】底部总占位 = max(键盘, 面板)，绝不相加
    val bottomSlotHeightPx =
        if (toolPanelOpen) maxOf(imeBottomPx, targetPanelHeightPx) else maxOf(imeBottomPx, navBarBottomPx)
    val bottomSlotHeightDp = with(density) { bottomSlotHeightPx.toDp() }

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
            // 发送成功后收起输入法与颜文字面板
            hideSystemIme()
            if (kaomojiMenuExpanded) kaomojiMenuExpanded = false
        }
        wasReplying = isReplying
    }

    // 注意：不要在 replyText 变空时清 attachedImageUri —— 用户"选图→输入→删光文字"
    // 的正常操作会把附件静默清掉。附件只在发送成功路径（isReplying 回落）清理。

    // Show error toast when replyError changes
    androidx.compose.runtime.LaunchedEffect(replyError) {
        if (replyError != null) {
            android.widget.Toast.makeText(context, "回复失败: $replyError", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // 毛玻璃贴屏幕物理底边，由内容下方的共享槽位把控件顶起来：
    // 无键盘无面板时槽位 = 导航条高度（与现在贴导航条一致）；
    // 键盘/面板谁高用谁，玻璃始终铺到屏幕底无断层。
    Surface(
        color = DaoTheme.colors.glassNavBar,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
            val reducedMotion = isReducedMotionEnabled()
            AnimatedVisibility(
                visible = quotedPostNo != null,
                enter = MiooMotion.softExpandEnter(reducedMotion),
                exit = MiooMotion.softExpandExit(reducedMotion)
            ) {
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
            AnimatedVisibility(
                visible = attachedImageUri != null,
                enter = MiooMotion.softExpandEnter(reducedMotion),
                exit = MiooMotion.softExpandExit(reducedMotion)
            ) {
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
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { previewAttachedUri = attachedImageUri }
                        )
                        IconButton(
                            onClick = { attachedImageUri = null },
                            // 不要黑色圆底：只留白色 ×，靠图标自身可见性
                            modifier = Modifier
                                .size(18.dp)
                                .align(Alignment.TopEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Remove",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
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
                            closeToolPanels()
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
                    diceSelected = false,
                    kaomojiSelected = kaomojiMenuExpanded,
                    hasImage = attachedImageUri != null,
                    onDiceClick = { showDiceDialog = true },
                    onKaomojiClick = {
                        val open = !kaomojiMenuExpanded
                        kaomojiMenuExpanded = open
                        if (open) {
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
                        if (attachedImageUri != null && file == null) {
                            android.widget.Toast.makeText(context, "图片读取失败", android.widget.Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
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

            }

            // 【共享底部槽位】max(键盘, 面板)，绝不相加。面板开时直接填充槽位
            // （无收起动画，高度变化完全由槽位驱动 = 单一动画源，杜绝 ime+面板叠加过冲）。
            // 毛玻璃铺底直达屏幕底，面板内容 navigationBarsPadding 防撞小白条。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomSlotHeightDp)
            ) {
                if (toolPanelOpen) {
                    KaomojiQuickPanel(
                        onInsert = { insertAtCursor(it) },
                        modifier = Modifier.navigationBarsPadding(),
                        heightDp = with(density) {
                            (bottomSlotHeightPx - navBarBottomPx).coerceAtLeast(0)
                                .toDp().value.roundToInt()
                        }
                    )
                }
            }
        }
    }

    if (showDiceDialog) {
        DiceDialog(
            onDismiss = { showDiceDialog = false },
            onInsert = { expr ->
                insertAtCursor(expr)
                showDiceDialog = false
            }
        )
    }

    previewAttachedUri?.let { uri ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { previewAttachedUri = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { previewAttachedUri = null },
                contentAlignment = Alignment.Center
            ) {
                coil.compose.AsyncImage(
                    model = uri,
                    contentDescription = "附件预览",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun copyThreadReportLink(context: android.content.Context, threadId: String) {
    val url = "https://www.nmbxd.com/t/$threadId"
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("mioodao-report", url))
    Toast.makeText(context, "已复制串链接，可到网页版举报该串", Toast.LENGTH_LONG).show()
}
