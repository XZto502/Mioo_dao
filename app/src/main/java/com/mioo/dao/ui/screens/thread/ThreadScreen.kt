package com.mioo.dao.ui.screens.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.mioo.dao.ui.theme.MiooMotion
import com.mioo.dao.ui.theme.isReducedMotionEnabled
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.content.Intent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import com.mioo.dao.ui.components.CookieSelector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import com.mioo.dao.ui.theme.readingTextStyle
import com.mioo.dao.ui.theme.readingBackground
import com.mioo.dao.ui.theme.readingTopBarColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
import com.mioo.dao.ui.components.ViewerImage
import com.mioo.dao.ui.components.IslandImage
import com.mioo.dao.ui.components.toPostData
import com.mioo.dao.data.model.effectiveTitle
import com.mioo.dao.ui.screens.settings.SettingsViewModel
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.ui.theme.ImmersiveDialogEffect
import com.mioo.dao.ui.theme.immersiveDialogProperties
import com.mioo.dao.ui.components.LocalDicePreset
import com.mioo.dao.ui.components.LocalDiceRuleMenu
import com.mioo.dao.utils.DiceRulePreset
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.CompositionLocalProvider
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
    val composerHeightPx = remember(viewModel.threadId) { mutableIntStateOf(0) }
    val onComposerHeightChanged = remember(composerHeightPx) {
        { height: Int -> composerHeightPx.intValue = height }
    }
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
    // 串内举报：目标楼层 id（该楼自身），null = 对话框关闭
    var reportTargetNo by remember { mutableStateOf<String?>(null) }
    // 提交状态放屏幕层：失败留在对话框时可再次点发送重试
    var isReportSubmitting by remember { mutableStateOf(false) }
    var showPageJumpDialog by remember { mutableStateOf(false) }
    var showJumpBubble by remember { mutableStateOf(false) }
    var isSearchMode by remember { mutableStateOf(false) }
    // Local text field value — do not write listUi on every keystroke
    var searchFieldText by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }
    // 骰点高亮规则：按串记忆；点任意骰子胶囊弹出切换微菜单
    val dicePreset by viewModel.dicePreset.collectAsState()
    var showDiceRuleMenu by remember { mutableStateOf(false) }
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
    val prewarmDicePreset = if (threadSettings.diceBadgesEnabled) dicePreset else null
    LaunchedEffect(threadPrewarmSig, quoteLinkColor, prewarmDicePreset) {
        if (listUi.displayItems.isEmpty()) return@LaunchedEffect
        val bodies = listUi.displayItems.map { it.postData.content }
        // Head only on open — bulk of the page is warmed by settle-scroll effect below
        val head = listOfNotNull(listUi.mainPostData?.content) + bodies.take(10)
        withContext(Dispatchers.Default) {
            HtmlParseCache.prewarm(head, quoteLinkColor, prewarmDarkTheme, prewarmDicePreset)
        }
        delay(350)
        // Cap bulk warm so long threads don't burn Default while user is already scrolling
        withContext(Dispatchers.Default) {
            HtmlParseCache.prewarm(bodies.take(20), quoteLinkColor, prewarmDarkTheme, prewarmDicePreset)
        }
    }

    // Settled-scroll near-viewport warm only (no mid-fling restart)
    LaunchedEffect(listState, listUi.displayItems, quoteLinkColor, prewarmDicePreset) {
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
                    HtmlParseCache.prewarm(near, quoteLinkColor, prewarmDarkTheme, prewarmDicePreset)
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

    Box(Modifier.fillMaxSize()) {
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
                            style = readingTextStyle(16.5f, 22f, FontWeight.SemiBold).copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (totalPages > 1) {
                            AnimatedContent(
                                targetState = currentScrollPage.value to totalPages,
                                transitionSpec = {
                                    if (reducedMotion) {
                                        fadeIn(tween(1)) togetherWith fadeOut(tween(1))
                                    } else {
                                        val forward = targetState.first >= initialState.first
                                        val enter = slideInVertically(tween(280)) { it / 2 } +
                                            fadeIn(tween(280))
                                        val exit = slideOutVertically(tween(280)) { -it / 2 } +
                                            fadeOut(tween(280))
                                        val enterBack = slideInVertically(tween(280)) { -it / 2 } +
                                            fadeIn(tween(280))
                                        val exitBack = slideOutVertically(tween(280)) { it / 2 } +
                                            fadeOut(tween(280))
                                        if (forward) enter.togetherWith(exit) else enterBack.togetherWith(exitBack)
                                    }
                                },
                                label = "pageSubtitle"
                            ) { (page, total) ->
                                Text(
                                    text = "第 $page / $total 页",
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
                                expanded = showJumpBubble,
                                onExpandedChange = { showJumpBubble = it },
                                onJumpToPage = { viewModel.jumpToPage(it) },
                                onOpenInputDialog = {
                                    showJumpBubble = false
                                    showPageJumpDialog = true
                                }
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
                            onShowOverflowMenuChange = { showOverflowMenu = it },
                            diceRuleLabel = "骰子规则: ${dicePreset.shortLabel}",
                            onOpenDiceRule = { showDiceRuleMenu = true }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = readingTopBarColor()
                )
            )
        },
        bottomBar = {
            // 底栏自己处理 ime / 导航条间距，避免整页 Scaffold 再垫一次。
            ReplyInputBar(
                viewModel = viewModel,
                listState = listState,
                onContentHeightChanged = onComposerHeightChanged,
                cookies = threadSettings.cookiesList,
                selectedCookieIndex = viewModel.threadCookieIndex.collectAsState().value
                    ?.takeIf { it < threadSettings.cookiesList.size }
                    ?: threadSettings.selectedCookieIndex,
                onCookieSelect = { viewModel.selectThreadCookie(it) }
            )
        },
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = readingBackground()
    ) { paddingValues ->
        CompositionLocalProvider(
            // 设置里的骰点高亮开关：关闭时不向渲染层下发规则（正文恢复纯文本）
            LocalDicePreset provides if (threadSettings.diceBadgesEnabled) dicePreset else null,
            LocalDiceRuleMenu provides { showDiceRuleMenu = true }
        ) {
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

                // Reserve the full composer height at the end, even while it is hidden.
                // Only end padding is reserved: the scrolling viewport still fills the screen.
                // Reveal no longer changes the scroll range or interrupts a fling with scrollBy.
                val composerEndInset = with(LocalDensity.current) { composerHeightPx.intValue.toDp() }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = maxOf(composerEndInset, paddingValues.calculateBottomPadding()) + 16.dp
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
                                    onReport = { reportTargetNo = mainThread.idStr },
                                    onShareCard = {
                                        scope.launch {
                                            com.mioo.dao.utils.ShareCardUtil.sharePostCard(context, mainPostData)
                                        }
                                    },
                                    onSwitchDiceRule = if (com.mioo.dao.utils.DiceRoll.containsDice(mainThread.content)) {
                                        { showDiceRuleMenu = true }
                                    } else {
                                        null
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
                                enableHtmlGestures = true,
                                shape = readingGroupShape(false,
                                    mainThread.userHash.isNotBlank() &&
                                        displayItems.firstOrNull()?.userHash == mainThread.userHash)
                            )
                        }
                    }

                    itemsIndexed(
                        items = displayItems,
                        key = { _, item -> item.id },
                        contentType = { _, item ->
                            // Finer slots improve Lazy reuse for quote-heavy replies
                            val q = if (item.quoteIds.isNotEmpty()) "q" else "n"
                            val i = if (item.hasImage) "i" else "t"
                            "reply_${q}_$i"
                        }
                    ) { index, item ->
                        val previousHash = displayItems.getOrNull(index - 1)?.userHash
                            ?: mainThread?.userHash?.takeIf { listUi.startPage <= 1 }
                        val joinsPrevious = item.userHash.isNotBlank() && item.userHash == previousHash
                        val joinsNext = item.userHash.isNotBlank() &&
                            item.userHash == displayItems.getOrNull(index + 1)?.userHash
                        val hasPrevious = index > 0 || (mainThread != null && listUi.startPage <= 1)
                        ThreadReplyRow(
                            modifier = Modifier.padding(top = if (!hasPrevious) 0.dp else if (joinsPrevious) 3.dp else 8.dp),
                            shape = readingGroupShape(joinsPrevious, joinsNext),
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
                            onFreeCopy = { freeCopyText = item.rawContent },
                            onReport = { reportTargetNo = item.idStr },
                            onOpenDiceRule = { showDiceRuleMenu = true }
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

            // 跳页气泡的空白点击层：点任意处带动画收起（气泡本体在顶栏层，不受影响）
            AnimatedVisibility(
                visible = showJumpBubble,
                enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(120)),
                exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(120)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showJumpBubble = false }
                )
            }

            freeCopyText?.let { text ->
                FreeCopyDialog(
                    text = text,
                    onDismiss = { freeCopyText = null }
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
    }

    val viewerImages by remember(listUi.displayItems, listUi.mainPostData, listUi.thread) {
        derivedStateOf {
            val list = mutableListOf<ViewerImage>()
            listUi.mainPostData?.imageUrl?.let { url ->
                list.add(
                    ViewerImage(
                        url = url,
                        postId = listUi.thread?.idStr,
                        snippet = listUi.thread?.content?.let(::plainTextSnippet)
                    )
                )
            }
            listUi.displayItems.forEach { item ->
                item.postData.imageUrl?.let { url ->
                    list.add(
                        ViewerImage(
                            url = url,
                            postId = item.idStr,
                            snippet = plainTextSnippet(item.rawContent)
                        )
                    )
                }
            }
            list.distinctBy { it.url }
        }
    }
    activeImageUrl?.let { imageUrl ->
        ImageViewer(
            initialImageUrl = imageUrl,
            imageMeta = viewerImages,
            onDismiss = { activeImageUrl = null },
            onRequestMorePages = { viewModel.loadNextPage() },
            onLocateFloor = { postId ->
                activeImageUrl = null
                val mainId = listUi.thread?.idStr
                val replyIdx = listUi.displayItems.indexOfFirst { it.idStr == postId }
                val index = when {
                    postId == mainId && listUi.startPage <= 1 -> 0
                    replyIdx >= 0 -> replyIdx + if (listUi.startPage <= 1) 1 else 0
                    else -> -1
                }
                if (index >= 0) {
                    scope.launch { listState.animateScrollToItem(index) }
                } else {
                    Toast.makeText(context, "该楼层不在已加载的页里，翻到对应页再定位", Toast.LENGTH_SHORT).show()
                }
            }
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

    // 与回复栏骰娘同一套 AlertDialog：系统 dim 盖住整屏（含顶栏和底栏），不要自绘半屏遮罩。
    if (showDiceRuleMenu) {
        AlertDialog(
            onDismissRequest = { showDiceRuleMenu = false },
            properties = immersiveDialogProperties(),
            title = {
                ImmersiveDialogEffect()
                Text("本串骰子规则")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DiceRulePreset.entries.forEach { preset ->
                        Md3MenuPillItem(
                            label = preset.menuLabel,
                            leadingIcon = Icons.Default.Casino,
                            selected = preset == dicePreset,
                            onClick = {
                                viewModel.setDicePreset(preset)
                                showDiceRuleMenu = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiceRuleMenu = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 串内举报：饼干取值复用传给 ReplyInputBar 的同一组值（串级覆盖优先，其次全局选中）
    reportTargetNo?.let { target ->
        ReportDialog(
            cookies = threadSettings.cookiesList,
            selectedCookieIndex = viewModel.threadCookieIndex.collectAsState().value
                ?.takeIf { it < threadSettings.cookiesList.size }
                ?: threadSettings.selectedCookieIndex,
            targetNo = target,
            isSubmitting = isReportSubmitting,
            onDismiss = { reportTargetNo = null },
            onSubmit = { title, content, cookie ->
                isReportSubmitting = true
                viewModel.submitReport(title, content, cookie) { success, errorMessage ->
                    isReportSubmitting = false
                    if (success) {
                        Toast.makeText(context, "已发到值班室，感谢反馈", Toast.LENGTH_SHORT).show()
                        reportTargetNo = null
                    } else {
                        Toast.makeText(context, errorMessage ?: "举报发送失败", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

/**
 * M3 1.2.x 兼容的 18dp 圆角胶囊菜单容器。
 * 原生 DropdownMenu 的落点由 provider 按窗口钳制：右缘图标下的窄菜单会被推到
 * 屏幕边上，看起来"错位"。这里改用自绘 Popup 确定性定位——菜单挂在锚点图标
 * 正下方、右缘与图标右缘对齐（与官方版行为一致），进出场与饼干菜单同款缩放淡入。
 * 底色/描边直接给 Surface，无需再主题覆写。
 */
@Composable
private fun Md3DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offsetY: Dp = 6.dp,
    cornerRadius: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // 与底部回复栏胶囊保持一致的深邃炭黑/清爽浅白底色
    val menuBgColor = if (isDark) Color(0xFF17191E) else Color(0xFFF6F7F9)
    val menuBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
    // 菜单挂载状态：收起动画播完才卸载 Popup（isIdle 前不能走 if）
    val menuState = remember { MutableTransitionState(false) }
    menuState.targetState = expanded
    if (menuState.currentState || menuState.targetState || !menuState.isIdle) {
        val density = LocalDensity.current
        // 锚点 IconButton 固定 40dp：菜单顶边 = 图标底边 + offsetY
        val topRightOffset = IntOffset(0, with(density) { (40.dp + offsetY).roundToPx() })
        Popup(
            alignment = Alignment.TopEnd,
            offset = topRightOffset,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true)
        ) {
            val reducedMotion = isReducedMotionEnabled()
            AnimatedVisibility(
                visibleState = menuState,
                enter = if (reducedMotion) {
                    EnterTransition.None
                } else {
                    scaleIn(
                        initialScale = 0.9f,
                        animationSpec = tween(160),
                        transformOrigin = TransformOrigin(1f, 0f)
                    ) + fadeIn(tween(120))
                },
                exit = if (reducedMotion) {
                    ExitTransition.None
                } else {
                    scaleOut(
                        targetScale = 0.94f,
                        animationSpec = tween(140),
                        transformOrigin = TransformOrigin(1f, 0f)
                    ) + fadeOut(tween(120))
                }
            ) {
                Surface(
                    shape = RoundedCornerShape(cornerRadius),
                    color = menuBgColor,
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, menuBorderColor),
                    // Popup 的约束是全屏：fillMaxWidth 的胶囊项会把菜单撑满屏，
                    // 用 IntrinsicSize.Max 收缩到最宽子项的自然宽度（= 之前 DropdownMenu 的 wrap 尺寸）
                    modifier = modifier
                        .widthIn(min = 120.dp)
                        .width(IntrinsicSize.Max)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        content = content
                    )
                }
            }
        }
    }
}

/**
 * 紧凑胶囊菜单项：selected 填 secondaryContainer 底并加粗 + 尾部对号；destructive 为移除类
 * 警示态（浅 errorContainer 底 + error 前景）；disabled 降透明度并禁点。
 */
@Composable
private fun Md3MenuPillItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    selected: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true
) {
    val containerColor = when {
        destructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        destructive -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val leadingTint = when {
        destructive -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alpha = if (enabled) 1f else 0.5f
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            leadingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = leadingTint.copy(alpha = alpha)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                ),
                color = contentColor.copy(alpha = alpha)
            )
            if (selected) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor.copy(alpha = alpha)
                )
            }
        }
    }
}

/** 看图器楼层摘要：剥标签/实体后取前 48 字。 */
private fun plainTextSnippet(html: String): String {
    if (html.isBlank()) return ""
    var text = html.replace(Regex("<[^>]*>"), " ")
    for ((entity, ch) in listOf(
        "&nbsp;" to " ", "&gt;" to ">", "&lt;" to "<",
        "&amp;" to "&", "&quot;" to "\"", "&#39;" to "'"
    )) {
        text = text.replace(entity, ch)
    }
    return text.replace(Regex("\\s+"), " ").trim().take(48)
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
    onShowOverflowMenuChange: (Boolean) -> Unit,
    diceRuleLabel: String,
    onOpenDiceRule: () -> Unit
) {
    val chrome by viewModel.chromeUiState.collectAsState()
    val context = LocalContext.current
    // 「删除离线缓存」二次确认（三点菜单触发）
    var showRemoveOffline by remember { mutableStateOf(false) }

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
        // 星标 = 已离线（本地收藏已并入离线下载）；已离线后星标仅亮着，点击无动作
        val bookmarked = chrome.isSubscribed || chrome.isCloudSubscribed
        IconButton(
            onClick = {
                when {
                    chrome.isDownloading -> Unit
                    // 配了云端收藏夹 → 菜单（云端操作）
                    chrome.isCloudSubscribed || chrome.feedFolders.isNotEmpty() ->
                        onShowBookmarkMenuChange(true)
                    // 已离线 → 星标只是状态展示
                    chrome.isOfflineSaved -> Unit
                    // 未离线 → 星标即离线下载
                    else -> {
                        Toast.makeText(context, "正在下载该串至本地...", Toast.LENGTH_SHORT).show()
                        viewModel.downloadThread { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = if (chrome.isOfflineSaved) "已离线收藏" else "离线下载本串",
                tint = if (bookmarked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }

        Md3DropdownMenu(
            expanded = showBookmarkMenu,
            onDismissRequest = { onShowBookmarkMenuChange(false) }
        ) {
            if (chrome.isCloudSubscribed) {
                Md3MenuPillItem(
                    label = "从云端收藏夹移除",
                    leadingIcon = Icons.Outlined.CloudOff,
                    destructive = true,
                    onClick = {
                        onShowBookmarkMenuChange(false)
                        viewModel.removeCloudSubscription { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            chrome.feedFolders.forEach { folder ->
                val inFolder = folder.uuid in chrome.cloudFolderUuids
                Md3MenuPillItem(
                    label = folder.name,
                    leadingIcon = if (inFolder) Icons.Default.Folder else Icons.Outlined.Folder,
                    selected = inFolder,
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

        Md3DropdownMenu(
            expanded = showOverflowMenu,
            onDismissRequest = { onShowOverflowMenuChange(false) }
        ) {
            Md3MenuPillItem(
                label = "分享",
                leadingIcon = Icons.Outlined.Share,
                onClick = {
                    onShowOverflowMenuChange(false)
                    onShare()
                }
            )
            Md3MenuPillItem(
                label = diceRuleLabel,
                leadingIcon = Icons.Default.Casino,
                onClick = {
                    onShowOverflowMenuChange(false)
                    onOpenDiceRule()
                }
            )
            Md3MenuPillItem(
                label = "只看图片",
                leadingIcon = Icons.Outlined.Image,
                selected = showImagesOnly,
                onClick = {
                    onShowOverflowMenuChange(false)
                    viewModel.toggleShowImagesOnly()
                }
            )
            when {
                chrome.isDownloading -> Md3MenuPillItem(
                    label = "正在下载…",
                    leadingIcon = Icons.Default.Download,
                    enabled = false,
                    onClick = {}
                )
                chrome.isOfflineSaved -> {
                    Md3MenuPillItem(
                        label = "更新离线缓存 (已保存)",
                        leadingIcon = Icons.Default.DownloadDone,
                        onClick = {
                            onShowOverflowMenuChange(false)
                            Toast.makeText(context, "正在下载该串至本地...", Toast.LENGTH_SHORT).show()
                            viewModel.downloadThread { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    Md3MenuPillItem(
                        label = "删除离线缓存",
                        leadingIcon = Icons.Outlined.DeleteOutline,
                        destructive = true,
                        onClick = {
                            onShowOverflowMenuChange(false)
                            showRemoveOffline = true
                        }
                    )
                }
                else -> Md3MenuPillItem(
                    label = "离线下载本串",
                    leadingIcon = Icons.Default.Download,
                    onClick = {
                        onShowOverflowMenuChange(false)
                        Toast.makeText(context, "正在下载该串至本地...", Toast.LENGTH_SHORT).show()
                        viewModel.downloadThread { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        if (showRemoveOffline) {
            AlertDialog(
                onDismissRequest = { showRemoveOffline = false },
                title = { Text("删除离线缓存？") },
                text = { Text("将移除本串的本地收藏与离线数据（含图片），不可恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        showRemoveOffline = false
                        viewModel.removeOffline { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoveOffline = false }) {
                        Text("取消")
                    }
                }
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
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onJumpToPage: (Int) -> Unit,
    onOpenInputDialog: () -> Unit
) {
    // 拖动预览页码；每次打开气泡时重置为当前页
    var sliderPage by remember { mutableStateOf(1f) }
    var anchorBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // 气泡进出场状态：关闭（点空白/返回/跳页）时保持 Popup 在组合里播完退场动画再卸载。
    // targetState 必须在组合期同步赋值——放进 LaunchedEffect 会晚一帧，重开时
    // currentState 残留 true 导致首帧满透明度闪现
    val bubbleVisible = remember { MutableTransitionState(false) }
    bubbleVisible.targetState = expanded
    val keepComposed = expanded || bubbleVisible.currentState != expanded
    LaunchedEffect(expanded) {
        if (expanded) sliderPage = currentPage.toFloat()
    }
    val previewPage = sliderPage.toInt().coerceIn(1, totalPages.coerceAtLeast(1))
    val reducedMotion = isReducedMotionEnabled()
    BackHandler(enabled = expanded) { onExpandedChange(false) }

    Box(modifier = Modifier.size(40.dp)) {
        IconButton(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier
                .size(40.dp)
                .onGloballyPositioned { anchorBounds = it.boundsInWindow() }
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoStories,
                contentDescription = "跳转页码",
                modifier = Modifier.size(24.dp),
                tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }

        // Popup 独立窗口：不会被顶栏裁剪；位置按图标窗口坐标精确计算——
        // 右缘贴屏幕右边距、顶边在图标下方 8dp（与最初 DropdownMenu 的落点一致）
        if (keepComposed) {
            val density = LocalDensity.current
            val bounds = anchorBounds
            val marginPx = with(density) { 12.dp.roundToPx() }
            val bubbleWidthPx = with(density) { 244.dp.roundToPx() }
            val gapPx = with(density) { 8.dp.roundToPx() }
            val windowWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
            val desiredLeft = windowWidthPx - marginPx - bubbleWidthPx
            val desiredTop = (bounds?.bottom?.toInt() ?: 0) + gapPx
            val anchorOffset = if (bounds != null) {
                IntOffset(desiredLeft - bounds.left.toInt(), desiredTop - bounds.top.toInt())
            } else {
                IntOffset.Zero
            }
            Popup(
                alignment = Alignment.TopStart,
                offset = anchorOffset,
                onDismissRequest = { onExpandedChange(false) },
                properties = PopupProperties(focusable = true)
            ) {
                AnimatedVisibility(
                    visibleState = bubbleVisible,
                    enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(180)) + scaleIn(
                        initialScale = 0.9f,
                        animationSpec = tween(180),
                        transformOrigin = TransformOrigin(1f, 0f)
                    ),
                    exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(180)) + scaleOut(
                        targetScale = 0.9f,
                        animationSpec = tween(180),
                        transformOrigin = TransformOrigin(1f, 0f)
                    )
                ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 6.dp,
                modifier = Modifier.width(244.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    // 第 1 行：步进器 + 可点击页码胶囊（点胶囊 = 精确输入）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                onJumpToPage(1)
                                sliderPage = 1f
                            },
                            enabled = currentPage > 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.FirstPage, contentDescription = "首页", modifier = Modifier.size(18.dp))
                        }
                        FilledTonalIconButton(
                            onClick = {
                                onJumpToPage(currentPage - 1)
                                sliderPage = (currentPage - 1).toFloat()
                            },
                            enabled = currentPage > 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一页", modifier = Modifier.size(18.dp))
                        }
                        val highlight = previewPage != currentPage
                        Surface(
                            shape = CircleShape,
                            color = if (highlight) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            onClick = {
                                onExpandedChange(false)
                                onOpenInputDialog()
                            },
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                AnimatedContent(
                                    targetState = previewPage,
                                    transitionSpec = {
                                        if (reducedMotion) {
                                            fadeIn(tween(1)) togetherWith fadeOut(tween(1))
                                        } else {
                                            val forward = targetState >= initialState
                                            val enter = slideInVertically(tween(180)) { it / 2 } + fadeIn(tween(180))
                                            val exit = slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(180))
                                            val enterBack = slideInVertically(tween(180)) { -it / 2 } + fadeIn(tween(180))
                                            val exitBack = slideOutVertically(tween(180)) { it / 2 } + fadeOut(tween(180))
                                            if (forward) enter.togetherWith(exit) else enterBack.togetherWith(exitBack)
                                        }
                                    },
                                    label = "capsulePage"
                                ) { page ->
                                    Text(
                                        text = "$page/$totalPages",
                                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = FontWeight.Bold,
                                        color = if (highlight) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "手动输入页码",
                                    modifier = Modifier.size(11.dp),
                                    tint = if (highlight) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                        FilledTonalIconButton(
                            onClick = {
                                onJumpToPage(currentPage + 1)
                                sliderPage = (currentPage + 1).toFloat()
                            },
                            enabled = currentPage < totalPages,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一页", modifier = Modifier.size(18.dp))
                        }
                        FilledTonalIconButton(
                            onClick = {
                                onJumpToPage(totalPages)
                                sliderPage = totalPages.toFloat()
                            },
                            enabled = currentPage < totalPages,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.LastPage, contentDescription = "末页", modifier = Modifier.size(18.dp))
                        }
                    }

                    // 第 2 行：紧凑滑块（拖动预览，松手跳页并收起）
                    Slider(
                        value = sliderPage,
                        onValueChange = { sliderPage = it },
                    onValueChangeFinished = {
                        onExpandedChange(false)
                        val target = sliderPage.toInt().coerceIn(1, totalPages)
                        if (target != currentPage) {
                            onJumpToPage(target)
                        }
                    },
                        valueRange = 1f..totalPages.toFloat().coerceAtLeast(1f),
                        steps = if (totalPages in 3..20) totalPages - 2 else 0,
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
                }
            }
        }
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
    onFreeCopy: () -> Unit,
    onReport: () -> Unit,
    onOpenDiceRule: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp)
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
            onReport = onReport,
            onShareCard = {
                rowScope.launch {
                    com.mioo.dao.utils.ShareCardUtil.sharePostCard(rowContext, item.postData)
                }
            },
            onSwitchDiceRule = if (com.mioo.dao.utils.DiceRoll.containsDice(item.rawContent)) {
                onOpenDiceRule
            } else {
                null
            }
        )
    }

    ReplyCard(
        modifier = modifier,
        shape = shape,
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
    onShareCard: (() -> Unit)? = null,
    onSwitchDiceRule: (() -> Unit)? = null
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
                if (onSwitchDiceRule != null) {
                    TextButton(
                        onClick = { onSwitchDiceRule(); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("切换本串骰子规则") }
                }
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
 * 串内举报对话框：把 >>No.<被举报楼层> 的引用 + 说明文字作为新串发到值班室（fid=18）。
 */
@Composable
private fun ReportDialog(
    cookies: List<String>,
    selectedCookieIndex: Int,
    targetNo: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (title: String, content: String, cookie: String?) -> Unit
) {
    var titleText by remember { mutableStateOf("") }
    // 预填对被举报楼层的引用；换目标楼层时重置预填内容
    var contentText by remember(targetNo) { mutableStateOf(">>No.$targetNo\n") }
    // 对话框局部饼干选择：不改串级/全局选中
    var reportCookieIndex by remember(selectedCookieIndex) { mutableIntStateOf(selectedCookieIndex) }
    var cookieMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = immersiveDialogProperties(),
        title = {
            ImmersiveDialogEffect()
            Text("举报")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contentText,
                    onValueChange = { contentText = it },
                    label = { Text("内容") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                // Cookie selector
                CookieSelector(
                    cookies = cookies,
                    selectedIndex = reportCookieIndex,
                    expanded = cookieMenuExpanded,
                    onExpandedChange = { cookieMenuExpanded = it },
                    onSelect = { reportCookieIndex = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(titleText, contentText, cookies.getOrNull(reportCookieIndex)) },
                enabled = contentText.isNotBlank() && !isSubmitting
            ) {
                Text("发送")
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
 * Owns [ThreadViewModel.composerState] collection so keystrokes never invalidate
 * the parent [ThreadScreen] composition scope / LazyColumn.
 */
@Composable
private fun ReplyInputBar(
    viewModel: ThreadViewModel,
    listState: LazyListState,
    onContentHeightChanged: (Int) -> Unit,
    cookies: List<String>,
    selectedCookieIndex: Int,
    onCookieSelect: (Int) -> Unit
) {
    val composerState by viewModel.composerState.collectAsState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var scrollingDown by remember(listState) { mutableStateOf(false) }
    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        var userScroll = false
        var wasDragging = false
        snapshotFlow {
            Triple(listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress, dragged to listState.canScrollForward)
        }.collect { (position, scrolling, gesture) ->
            val (dragging, canScrollForward) = gesture
            if (dragging && !wasDragging) userScroll = true
            // Latch the reveal at the end: composer height changes must not hide it again.
            if (!canScrollForward) {
                scrollingDown = false
                userScroll = false
            }
            if (scrolling && userScroll) {
                if (position.first != previousIndex) scrollingDown = position.first > previousIndex
                else if (kotlin.math.abs(position.second - previousOffset) > 2)
                    scrollingDown = position.second > previousOffset
            }
            if (!scrolling && !dragging) userScroll = false
            previousIndex = position.first
            previousOffset = position.second
            wasDragging = dragging
        }
    }
    val hideWhenReading by remember(listState) {
        derivedStateOf { scrollingDown && listState.canScrollForward }
    }
    ReplyInputArea(
        onContentHeightChanged = onContentHeightChanged,
        hideWhenReading = hideWhenReading,
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

private fun readingGroupShape(joinsPrevious: Boolean, joinsNext: Boolean) = RoundedCornerShape(
    topStart = if (joinsPrevious) 6.dp else 16.dp,
    topEnd = if (joinsPrevious) 6.dp else 16.dp,
    bottomStart = if (joinsNext) 6.dp else 16.dp,
    bottomEnd = if (joinsNext) 6.dp else 16.dp
)
