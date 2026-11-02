package com.mioo.dao.ui.screens.forum

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mioo.dao.data.local.DraftEntity
import com.mioo.dao.data.local.PostDraftContent
import com.mioo.dao.ui.components.FreeCopyDialog
import com.mioo.dao.ui.components.ImageViewer
import com.mioo.dao.ui.components.ListThumbImage
import com.mioo.dao.ui.components.PrefetchListImages
import com.mioo.dao.ui.components.ThreadCard
import com.mioo.dao.ui.components.ThreadListItem
import com.mioo.dao.ui.components.toFile
import com.mioo.dao.ui.screens.settings.SettingsViewModel
import com.mioo.dao.ui.theme.readingBackground
import com.mioo.dao.ui.theme.readingTopBarColor
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.ui.theme.ImmersiveDialogEffect
import com.mioo.dao.ui.theme.immersiveDialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ForumScreen(
    viewModel: ForumViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToThread: (threadId: String) -> Unit,
    onColdStartContentReady: () -> Unit = {},
    /** 底部 dock 栏下沉进度 0f(可见)→1f(隐藏)；发串按钮随之向内缩小淡出。 */
    dockSinkFraction: () -> Float = { 0f },
    /** 抽屉打开/正在打开时上报主框架：悬浮 dock 滑出屏幕让位。 */
    onDrawerOpenChange: (Boolean) -> Unit = {},
    onImageViewerOpenChange: (Boolean) -> Unit = {},
    onComposerOpenChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    // Pins + cookies only — theme/font changes must not recompose the board list
    val forumSettings by settingsViewModel.forumScreenSettings.collectAsState()
    val context = LocalContext.current
    // Per-board scroll: rememberSaveable survives navigate→thread→back so position is restored.
    val currentForumId = viewModel.forumId
    val listState = rememberSaveable(currentForumId, saver = LazyListState.Saver) {
        LazyListState()
    }
    val drawerListState = rememberLazyListState()
    var showCreateDialog by remember { mutableStateOf(false) }
    SideEffect { onComposerOpenChange(showCreateDialog) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { onComposerOpenChange(false) }
    }
    var isPostingThread by remember { mutableStateOf(false) }
    var freeCopyText by remember { mutableStateOf<String?>(null) }
    var activeImageUrl by remember { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val emptyStringLambda = remember { { _: String -> } }
    var handledRefreshGeneration by rememberSaveable(currentForumId) { mutableStateOf(0L) }

    val previousForumId = rememberSaveable { mutableStateOf(currentForumId) }
    // True for a short window after board switch — pause prefetch/prewarm during swap
    var boardSwitchQuiet by remember { mutableStateOf(false) }

    suspend fun refreshFromTop() {
        if (listState.layoutInfo.totalItemsCount > 0) {
            listState.scrollToItem(0)
        }
        viewModel.refresh()
    }

    // Splash: also release on empty/error so we never hang without content
    var coldStartNotified by remember { mutableStateOf(false) }
    LaunchedEffect(
        uiState.errorMessage,
        uiState.isLoading,
        uiState.isRefreshing,
        uiState.displayItems.isEmpty()
    ) {
        if (coldStartNotified) return@LaunchedEffect
        val settledEmpty =
            uiState.displayItems.isEmpty() &&
                !uiState.isLoading &&
                !uiState.isRefreshing
        val hasError = !uiState.errorMessage.isNullOrBlank()
        if (settledEmpty || hasError) {
            delay(16)
            coldStartNotified = true
            onColdStartContentReady()
        }
    }
    val notifyColdStartReady = remember(onColdStartContentReady) {
        {
            if (!coldStartNotified) {
                coldStartNotified = true
                onColdStartContentReady()
            }
        }
    }

    val drawerBlocksMainList =
        drawerState.currentValue != DrawerValue.Closed ||
            drawerState.targetValue != DrawerValue.Closed

    // 打开中（含手势拖过阈值）→ 主框架把悬浮 dock 滑出屏幕，避免压在抽屉上拦截点击
    val drawerOverDock = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open
    LaunchedEffect(drawerOverDock) { onDrawerOpenChange(drawerOverDock) }
    LaunchedEffect(activeImageUrl) { onImageViewerOpenChange(activeImageUrl != null) }

    val pullThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 80.dp.toPx() }
    val pullToRefreshState = remember(currentForumId, pullThresholdPx) {
        ForumPullRefreshState(pullThresholdPx)
    }
    // Top pull circle is the sole full-list loading indicator (initial / board switch / refresh).
    val showTopLoading = uiState.isRefreshing ||
        (uiState.isLoading && uiState.threads.isEmpty())

    // User pull: fire refresh only when the VM is not already refreshing.
    LaunchedEffect(pullToRefreshState, pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing && !uiState.isRefreshing) {
            refreshFromTop()
        }
    }
    // Drive the pull indicator from VM state (one-way after user or toolbar starts refresh).
    LaunchedEffect(pullToRefreshState, showTopLoading) {
        if (showTopLoading) {
            if (!pullToRefreshState.isRefreshing) {
                pullToRefreshState.startRefresh()
            }
        } else if (pullToRefreshState.isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }

    // A refreshed page may insert new keyed rows before the old visible item.
    // Scroll after the final page-1 emission is applied, not only before requesting it.
    LaunchedEffect(
        uiState.completedRefreshGeneration,
        uiState.completedRefreshForumId,
        currentForumId
    ) {
        val generation = uiState.completedRefreshGeneration
        if (uiState.completedRefreshForumId != currentForumId || generation <= handledRefreshGeneration) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        listState.scrollToItem(0)
        handledRefreshGeneration = generation
    }

    // Paging via snapshotFlow — does not recompose the whole screen on every scroll frame
    // (derivedStateOf + LaunchedEffect(.value) used to restart the effect on flag churn).
    LaunchedEffect(listState, currentForumId, uiState.isLoading, uiState.isRefreshing, uiState.isLastPage) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
            last >= info.totalItemsCount - 2
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (!nearEnd) return@collect
                val s = viewModel.uiState.value
                if (!s.isLoading && !s.isRefreshing && !s.isLastPage) {
                    viewModel.loadNextPage()
                }
            }
    }

    // Board change: quiet heavy work briefly (no overlapping list transition).
    // Keep short — high-frequency board switches should not feel delayed (Emil: tens/day → reduce).
    LaunchedEffect(currentForumId) {
        if (currentForumId != previousForumId.value) {
            boardSwitchQuiet = true
            listState.scrollToItem(0)
            previousForumId.value = currentForumId
            delay(160)
            boardSwitchQuiet = false
        }
    }

    // Board switcher always opens at the top — no jump to current board / scroll memory
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue != DrawerValue.Open) return@LaunchedEffect
        if (uiState.forumGroups.isEmpty()) {
            viewModel.loadForumGroups()
        }
        runCatching { drawerListState.scrollToItem(0) }
    }

    val allowBackgroundWarm = !drawerBlocksMainList && !boardSwitchQuiet

    fun selectBoard(id: String, name: String) {
        if (id == viewModel.forumId) {
            scope.launch { drawerState.close() }
            return
        }
        // Start content switch immediately; close drawer in parallel for snappier feel
        viewModel.selectForum(id, name)
        scope.launch { drawerState.close() }
    }

    Box(Modifier.fillMaxSize()) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxSize(),
                drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                // Sheet color runs under the gesture pill; content is padded inside.
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                Spacer(modifier = Modifier.statusBarsPadding())
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "切换板块",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    IconButton(
                        onClick = {
                            // Ensure groups load even if cold-start deferral skipped them
                            viewModel.loadForumGroups()
                            Toast.makeText(context, "正在刷新板块列表...", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新板块列表",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val pinnedForumsList = remember(uiState.forumGroups, forumSettings.pinnedForums) {
                    uiState.forumGroups.flatMap { it.forums }
                        .filter { forumSettings.pinnedForums.contains(it.id) }
                        .distinctBy { it.id }
                }
                LazyColumn(
                    state = drawerListState,
                    modifier = Modifier.fillMaxSize(),
                    // 底部垫出悬浮 dock 的遮挡区：末尾板块（版务/百脑汇）能滑出底栏之上
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding() + 112.dp,
                        start = 12.dp,
                        end = 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (pinnedForumsList.isNotEmpty()) {
                        item(key = "header_pinned", contentType = "drawer_header") {
                            Text(
                                text = "置顶版块",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }
                        items(pinnedForumsList, key = { "pinned_${it.id}" }, contentType = { "drawer_forum" }) { forum ->
                            ForumDrawerItem(
                                name = forum.name,
                                isSelected = forum.id == viewModel.forumId,
                                showStar = true,
                                starTint = MaterialTheme.colorScheme.primary,
                                onClick = { selectBoard(forum.id, forum.name) },
                                onLongClick = {
                                    val wasPinned = forumSettings.pinnedForums.contains(forum.id)
                                    settingsViewModel.togglePinForum(forum.id)
                                    Toast.makeText(
                                        context,
                                        if (wasPinned) "已取消置顶" else "已将版块置顶",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                        item(key = "divider_pinned", contentType = "drawer_divider") {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    // Normal Forum Groups — 每组一个 item，展开/收起整体做手风琴动画
                    uiState.forumGroups.forEach { group ->
                        val groupExpanded = forumSettings.expandedForumGroups.contains(group.id)
                        item(key = "group_${group.id}", contentType = "drawer_group") {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { settingsViewModel.toggleForumGroupExpand(group.id) }
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = group.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${group.forums.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                    val chevronRotation by animateFloatAsState(
                                        targetValue = if (groupExpanded) 180f else 0f,
                                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                                        label = "drawerChevron"
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = if (groupExpanded) "收起分类" else "展开分类",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .rotate(chevronRotation)
                                    )
                                }
                                // 收起时 AnimatedVisibility 不组合内容，抽屉的懒加载不受影响
                                AnimatedVisibility(
                                    visible = groupExpanded,
                                    enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                                        expandVertically(
                                            animationSpec = tween(250, easing = FastOutSlowInEasing),
                                            expandFrom = Alignment.Top
                                        ),
                                    exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                                        shrinkVertically(
                                            animationSpec = tween(250, easing = FastOutSlowInEasing),
                                            shrinkTowards = Alignment.Top
                                        )
                                ) {
                                    Column {
                                        group.forums.forEach { forum ->
                                            val isPinned = forumSettings.pinnedForums.contains(forum.id)
                                            ForumDrawerItem(
                                                name = forum.name,
                                                isSelected = forum.id == viewModel.forumId,
                                                showStar = isPinned,
                                                starTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                onClick = { selectBoard(forum.id, forum.name) },
                                                onLongClick = {
                                                    settingsViewModel.togglePinForum(forum.id)
                                                    Toast.makeText(
                                                        context,
                                                        if (isPinned) "已取消置顶" else "已将版块置顶",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
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
    ) {
            Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            // Instant title swap — no AnimatedContent tax on board change
                            Text(
                                text = uiState.currentForumName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "X岛 · nmbxd.com",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open()
                                    else drawerState.close()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "切换板块")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch { refreshFromTop() }
                            },
                            enabled = !uiState.isRefreshing
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = readingTopBarColor()
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .padding(bottom = 120.dp)
                        // dock 下沉时同步向内缩小 + 淡出 + 轻微下沉（绘制时读取，不触发重组）
                        .graphicsLayer {
                            val f = dockSinkFraction().coerceIn(0f, 1f)
                            val shrink = 1f - 0.5f * f
                            scaleX = shrink
                            scaleY = shrink
                            alpha = 1f - f
                            translationY = 24.dp.toPx() * f
                        },
                    // 去掉默认投影：elevation 阴影不随 graphicsLayer 缩放/淡出，
                    // 动画期间会残留成一块黑影
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    ),
                    containerColor = DaoTheme.colors.fabBg,
                    contentColor = DaoTheme.colors.fabContent,
                    shape = androidx.compose.foundation.shape.CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "发串"
                    )
                }
            },
            modifier = modifier,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = readingBackground()
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .nestedScroll(pullToRefreshState.nestedScrollConnection)
            ) {
                // Single list instance — pass only list fields so chrome/drawer flags
                // do not force every row to recompose.
                key(currentForumId) {
                    ForumThreadListPane(
                        forumKey = currentForumId,
                        displayItems = uiState.displayItems,
                        isLoading = uiState.isLoading,
                        isRefreshing = uiState.isRefreshing,
                        isLastPage = uiState.isLastPage,
                        emptyHint = uiState.emptyHint,
                        onContinuePastBlocks = { viewModel.continuePastBlockedPages() },
                        listState = listState,
                        emptyStringLambda = emptyStringLambda,
                        onNavigateToThread = onNavigateToThread,
                        onImageClick = { activeImageUrl = it },
                        onBlockThread = { settingsViewModel.addBlockedThread(it) },
                        onBlockUser = { settingsViewModel.addBlockedUser(it) },
                        onFreeCopy = { freeCopyText = it },
                        warmEnabled = allowBackgroundWarm,
                        onFirstScreenReady = notifyColdStartReady
                    )
                }

                uiState.errorMessage?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Unified loading indicator: only the top pull-to-refresh circle.
                if (pullToRefreshState.isRefreshing || pullToRefreshState.progress > 0f) {
                    PullToRefreshContainer(
                        state = pullToRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
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

    // New threads are saved only by an explicit action, never by typing.
    var pendingPostDraft by remember {
        mutableStateOf<com.mioo.dao.data.local.DraftEntity?>(null)
    }
    var composerSession by remember { mutableIntStateOf(0) }
    fun selectPostDraft(draft: DraftEntity) {
        if (draft.targetId != "new_thread" && draft.targetId != viewModel.forumId) {
            viewModel.selectForum(draft.targetId, draft.targetName)
        }
        pendingPostDraft = draft
        composerSession++
    }
    // 草稿箱 → 发串：总线收到草稿后打开对话框并预填
    val postDraftPrefill by viewModel.postDraftPrefill.collectAsState()
    LaunchedEffect(postDraftPrefill) {
        if (postDraftPrefill != null) {
            selectPostDraft(postDraftPrefill!!)
            showCreateDialog = true
            viewModel.consumePostDraftPrefill()
        }
    }

    if (showCreateDialog) {
        val postDrafts by viewModel.postDrafts.collectAsState()
        key(composerSession) {
        val initialDraft = pendingPostDraft?.let { PostDraftContent.from(it) } ?: PostDraftContent("", "", "")
        CreateThreadEditor(
            cookies = forumSettings.cookiesList,
            selectedCookieIndex = forumSettings.selectedCookieIndex,
            boardName = uiState.currentForumName,
            initialContent = initialDraft.content,
            initialTitle = initialDraft.title,
            initialName = initialDraft.name,
            drafts = postDrafts,
            onSelectDraft = ::selectPostDraft,
            // 草稿箱开关关闭时不提供「存草稿」按钮（参数为 null 时对话框隐藏该入口）
            onSaveDraft = if (forumSettings.draftBoxEnabled) {
                { draftTitle: String, draftAuthor: String, draftContent: String, onDone: (Boolean) -> Unit ->
                    viewModel.savePostDraft(draftTitle, draftAuthor, draftContent) { saved ->
                        if (saved) settingsViewModel.clearNewThreadDraft()
                        Toast.makeText(context, if (saved) "已存入草稿箱" else "草稿保存失败，请重试", Toast.LENGTH_SHORT).show()
                        onDone(saved)
                    }
                }
            } else null,
            onCookieSelect = { settingsViewModel.selectCookie(it) },
            isSubmitting = isPostingThread,
            onDismiss = {
                if (!isPostingThread) {
                    showCreateDialog = false
                    pendingPostDraft = null
                    settingsViewModel.clearNewThreadDraft()
                }
            },
            onSubmit = { title, author, content, imageUri ->
                if (!isPostingThread) {
                    val imageFile = imageUri?.toFile(context)
                    if (imageUri != null && imageFile == null) {
                        Toast.makeText(context, "图片读取失败", Toast.LENGTH_SHORT).show()
                    } else {
                        isPostingThread = true
                        viewModel.createThread(title, author, content, imageFile) { ok, message ->
                            isPostingThread = false
                            if (ok) {
                                settingsViewModel.clearNewThreadDraft()
                                pendingPostDraft?.let { viewModel.deleteDraft(it.id) }
                                pendingPostDraft = null
                                viewModel.deletePostDraftsForCurrentBoard()
                                showCreateDialog = false
                                Toast.makeText(context, "发串成功", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "发串失败: $message", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ForumDrawerItem(
    name: String,
    isSelected: Boolean,
    showStar: Boolean,
    starTint: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Instant selection colors — no per-item animateColorAsState during drawer swipe
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = containerColor,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
        ) {
            if (showStar) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "已置顶",
                    tint = starTint,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) {
                    androidx.compose.ui.text.font.FontWeight.Bold
                } else {
                    androidx.compose.ui.text.font.FontWeight.Normal
                },
                color = contentColor
            )
        }
    }
}

@Composable
private fun ForumThreadListPane(
    forumKey: String,
    displayItems: List<ThreadListItem>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    isLastPage: Boolean,
    emptyHint: String? = null,
    onContinuePastBlocks: () -> Unit = {},
    listState: LazyListState,
    emptyStringLambda: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onBlockThread: (String) -> Unit,
    onBlockUser: (String) -> Unit,
    onFreeCopy: (String) -> Unit,
    warmEnabled: Boolean = true,
    onFirstScreenReady: () -> Unit = {}
) {
    val quoteLinkColor = MaterialTheme.colorScheme.primary
    val isEmptyLoading = displayItems.isEmpty() && (isLoading || isRefreshing)
    val isEmptyIdle = displayItems.isEmpty() && !isLoading && !isRefreshing

    /**
     * listReady / showImages gate *images & prefetch only* — never the row set.
     * Hiding real items behind a 1-row placeholder clamped LazyListState to 0 on back.
     *
     * Split prewarm vs image-enable effects: paging appends used to restart a combined
     * LaunchedEffect and re-delay image enable + re-scan the whole HTML list.
     *
     * showImages uses rememberSaveable so thread detail → back does not flash empty thumbs.
     */
    val listReady = displayItems.isNotEmpty()
    var showImages by rememberSaveable(forumKey) { mutableStateOf(false) }
    var firstScreenNotified by remember(forumKey) { mutableStateOf(false) }
    var blockTarget by remember { mutableStateOf<ThreadListItem?>(null) }

    LaunchedEffect(forumKey, listReady) {
        if (!listReady || firstScreenNotified) return@LaunchedEffect
        firstScreenNotified = true
        // Drop splash on next frame — no artificial 16ms wait
        onFirstScreenReady()
    }

    // Cold head prewarm: only first viewport (4) so first paint wins CPU over Default parse.
    LaunchedEffect(forumKey, listReady, quoteLinkColor) {
        if (!listReady || displayItems.isEmpty()) return@LaunchedEffect
        val head = displayItems.take(4).map { it.postData.content }
        withContext(Dispatchers.Default) {
            com.mioo.dao.ui.components.HtmlParseCache.prewarm(head, quoteLinkColor)
        }
        // Remainder of first page after first composition window
        delay(120)
        if (displayItems.size > 4) {
            val rest = displayItems.drop(4).take(8).map { it.postData.content }
            withContext(Dispatchers.Default) {
                com.mioo.dao.ui.components.HtmlParseCache.prewarm(rest, quoteLinkColor)
            }
        }
    }
    LaunchedEffect(forumKey, listState, quoteLinkColor, warmEnabled) {
        if (!warmEnabled) return@LaunchedEffect
        snapshotFlow {
            if (listState.isScrollInProgress) return@snapshotFlow null
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()?.index ?: listState.firstVisibleItemIndex
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: first
            first to last
        }
            .distinctUntilChanged()
            .collect { range ->
                if (range == null || displayItems.isEmpty()) return@collect
                val (first, last) = range
                val start = first.coerceAtLeast(0)
                val end = (last + 5).coerceAtMost(displayItems.size)
                if (start >= end) return@collect
                val slice = displayItems.subList(start, end).map { it.postData.content }
                withContext(Dispatchers.Default) {
                    com.mioo.dao.ui.components.HtmlParseCache.prewarm(slice, quoteLinkColor)
                }
            }
    }

    // Thumbnails after first paint settles — cold path keeps decode off the critical frame.
    LaunchedEffect(forumKey, listReady, warmEnabled) {
        if (!listReady || !warmEnabled || showImages) return@LaunchedEffect
        delay(450)
        snapshotFlow { listState.isScrollInProgress }
            .first { scrolling -> !scrolling }
        delay(200)
        showImages = true
    }

    val prefetchUrls = remember(displayItems, showImages) {
        if (!showImages) emptyList()
        else displayItems.map { it.postData.imageUrl }
    }
    PrefetchListImages(
        imageUrls = prefetchUrls,
        listState = listState,
        sizePx = ListThumbImage.SIZE_PX,
        ahead = 2,
        initialDelayMs = 500,
        // Prefetch only when not flinging (see PrefetchListImages)
        enabled = warmEnabled && showImages && listReady
    )

    // Block dialog outside items — avoids per-row AlertDialog composition during scroll
    blockTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { blockTarget = null },
            properties = immersiveDialogProperties(),
            title = {
                ImmersiveDialogEffect()
                Text("内容操作")
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            onBlockThread(target.idStr)
                            blockTarget = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("屏蔽此串 (No.${target.idStr})")
                    }
                    TextButton(
                        onClick = {
                            onBlockUser(target.userHash)
                            blockTarget = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("屏蔽发言饼干 (ID: ${target.userHash})")
                    }
                    TextButton(
                        onClick = {
                            onFreeCopy(target.rawContent)
                            blockTarget = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("自由复制帖子内容")
                    }
                    TextButton(
                        onClick = { blockTarget = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("取消")
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Always keep a LazyColumn so pull-to-refresh nested scroll has a scrollable child.
    LazyColumn(
        state = listState,
        // ModalNavigationDrawer already owns its scrim/gestures. Disabling this child
        // as targetValue changes can cancel a diagonal drag before the drawer settles.
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 12.dp,
            bottom = 100.dp
        )
    ) {
        // Only show the empty/loading placeholder when there is truly no data.
        // Never swap a full item list for a 1-row spacer — that clamps scroll to top
        // (especially on navigate → thread → popBackStack recomposition).
        if (isEmptyLoading) {
            item(key = "empty_loading", contentType = "loading") {
                Spacer(modifier = Modifier.height(1.dp))
            }
            return@LazyColumn
        }
        if (isEmptyIdle) {
            item(key = "empty_idle", contentType = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            emptyHint ?: "该板块下暂无帖子。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!emptyHint.isNullOrBlank() && !isLastPage) {
                            TextButton(onClick = onContinuePastBlocks) {
                                Text("继续加载下一页")
                            }
                        }
                    }
                }
            }
            return@LazyColumn
        }
        items(
            items = displayItems,
            key = { it.id },
            // contentType must not depend on showImages — flipping it thrashs the item pool mid-scroll
            contentType = { item -> if (item.hasImage) "thread_image" else "thread_text" }
        ) { item ->
            ForumThreadRow(
                item = item,
                showImage = showImages,
                emptyStringLambda = emptyStringLambda,
                onNavigateToThread = onNavigateToThread,
                onImageClick = onImageClick,
                onLongClick = { blockTarget = item }
            )
        }

        if (!emptyHint.isNullOrBlank() && displayItems.isNotEmpty() && !isLoading) {
            item(key = "blocked_hint", contentType = "hint") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = emptyHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isLastPage) {
                        TextButton(onClick = onContinuePastBlocks) {
                            Text("继续加载下一页")
                        }
                    }
                }
            }
        }

        if (isLoading && displayItems.isNotEmpty()) {
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

        if (isLastPage) {
            item(key = "end_footer", contentType = "end") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "已加载全部帖子。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Isolated list row so parent list chrome / image-gate toggles only recompose rows
 * whose inputs actually change ([ThreadListItem] is @Immutable).
 */
@Composable
private fun ForumThreadRow(
    item: ThreadListItem,
    showImage: Boolean,
    emptyStringLambda: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onLongClick: () -> Unit
) {
    val onThreadClick = remember(item.idStr, onNavigateToThread) {
        { onNavigateToThread(item.idStr) }
    }
    val onImageClickRemembered = remember(onImageClick) {
        { url: String -> onImageClick(url) }
    }
    ThreadCard(
        postData = item.postData,
        replyCount = item.replyCount,
        onThreadClick = onThreadClick,
        onQuoteClick = emptyStringLambda,
        onImageClick = onImageClickRemembered,
        onLongClick = onLongClick,
        // Shorter body = cheaper measure on high-refresh fling
        contentMaxLines = 5,
        showImage = showImage && item.hasImage
    )
}

