package com.mioo.dao.ui.screens.forum

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.mioo.dao.ui.components.ComposerToolButtons
import com.mioo.dao.ui.components.CookieSelector
import com.mioo.dao.ui.components.DiceDialog
import com.mioo.dao.ui.components.FreeCopyDialog
import com.mioo.dao.ui.components.ImageViewer
import com.mioo.dao.ui.components.KaomojiQuickPanel
import com.mioo.dao.ui.components.ListThumbImage
import com.mioo.dao.ui.components.PrefetchListImages
import com.mioo.dao.ui.components.ThreadCard
import com.mioo.dao.ui.components.ThreadListItem
import com.mioo.dao.ui.components.rememberEffectiveImeBottomPx
import com.mioo.dao.ui.components.toFile
import com.mioo.dao.ui.screens.settings.SettingsViewModel
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.ui.theme.ImmersiveDialogEffect
import com.mioo.dao.ui.theme.findDialogWindow
import com.mioo.dao.ui.theme.MiooMotion
import com.mioo.dao.ui.theme.applyImmersiveSystemBars
import com.mioo.dao.ui.theme.immersiveDialogProperties
import com.mioo.dao.ui.theme.isReducedMotionEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ForumScreen(
    viewModel: ForumViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToThread: (threadId: String) -> Unit,
    onColdStartContentReady: () -> Unit = {},
    /** 底部 dock 栏下沉进度 0f(可见)→1f(隐藏)；发串按钮随之向内缩小淡出。 */
    dockSinkFraction: () -> Float = { 0f },
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
    var isPostingThread by remember { mutableStateOf(false) }
    var freeCopyText by remember { mutableStateOf<String?>(null) }
    var activeImageUrl by remember { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val emptyStringLambda = remember { { _: String -> } }

    val previousForumId = rememberSaveable { mutableStateOf(currentForumId) }
    // True for a short window after board switch — pause prefetch/prewarm during swap
    var boardSwitchQuiet by remember { mutableStateOf(false) }

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

    val pullToRefreshState = rememberPullToRefreshState()
    // Top pull circle is the sole full-list loading indicator (initial / board switch / refresh).
    val showTopLoading = uiState.isRefreshing ||
        (uiState.isLoading && uiState.threads.isEmpty())

    // User pull: fire refresh only when the VM is not already refreshing.
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing && !uiState.isRefreshing) {
            viewModel.refresh()
        }
    }
    // Drive the pull indicator from VM state (one-way after user or toolbar starts refresh).
    LaunchedEffect(showTopLoading) {
        if (showTopLoading) {
            if (!pullToRefreshState.isRefreshing) {
                pullToRefreshState.startRefresh()
            }
        } else if (pullToRefreshState.isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }

    // Paging via snapshotFlow — does not recompose the whole screen on every scroll frame
    // (derivedStateOf + LaunchedEffect(.value) used to restart the effect on flag churn).
    LaunchedEffect(listState, currentForumId) {
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
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(12.dp),
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

                    // Normal Forum Groups
                    uiState.forumGroups.forEach { group ->
                        val groupCollapsed = !forumSettings.expandedForumGroups.contains(group.id)
                        item(key = "header_${group.id}", contentType = "drawer_header") {
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
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = if (groupCollapsed) "展开分类" else "收起分类",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .rotate(if (groupCollapsed) 0f else 180f)
                                )
                            }
                        }
                        if (!groupCollapsed) {
                            items(group.forums, key = { "g${group.id}_${it.id}" }, contentType = { "drawer_forum" }) { forum ->
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
                                // Scroll to top only if the list currently has items; never block refresh.
                                if (listState.layoutInfo.totalItemsCount > 0) {
                                    scope.launch {
                                        runCatching { listState.scrollToItem(0) }
                                    }
                                }
                                viewModel.refresh()
                            },
                            enabled = !uiState.isRefreshing
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DaoTheme.colors.glassTopBar
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
            containerColor = Color.Transparent
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
                        userScrollEnabled = !drawerBlocksMainList,
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

    var newThreadDraftText by remember { mutableStateOf("") }
    /** 草稿箱恢复的发串草稿（优先于全局自动草稿预填）。 */
    var pendingPostDraft by remember {
        mutableStateOf<com.mioo.dao.data.local.DraftEntity?>(null)
    }
    LaunchedEffect(showCreateDialog) {
        if (showCreateDialog) {
            newThreadDraftText = settingsViewModel.getNewThreadDraft()
        }
    }
    // 草稿箱 → 发串：总线收到草稿后打开对话框并预填
    val postDraftPrefill by viewModel.postDraftPrefill.collectAsState()
    LaunchedEffect(postDraftPrefill) {
        if (postDraftPrefill != null) {
            pendingPostDraft = postDraftPrefill
            showCreateDialog = true
            viewModel.consumePostDraftPrefill()
        }
    }

    if (showCreateDialog) {
        // 与串内回复栏同源：键盘/导航条高度在 Activity 窗口里量取。
        // Dialog 窗口在这台设备上收不到 ime insets、可见区兜底也不收缩，
        // 槽位如果用对话框自己量到的 0，底栏就会被键盘整个盖住。
        val density = LocalDensity.current
        val imeBottomPx = rememberEffectiveImeBottomPx()
        val navBottomPx = WindowInsets.navigationBars.getBottom(density)
        CreateThreadDialog(
            imeBottomPx = imeBottomPx,
            navBottomPx = navBottomPx,
            cookies = forumSettings.cookiesList,
            selectedCookieIndex = forumSettings.selectedCookieIndex,
            boardName = uiState.currentForumName,
            initialContent = pendingPostDraft?.content ?: newThreadDraftText,
            initialTitle = pendingPostDraft?.title ?: "",
            initialName = pendingPostDraft?.name ?: "",
            onContentChange = { draft ->
                // Debounced inside ViewModel — no per-keystroke coroutine spam
                settingsViewModel.saveNewThreadDraft(draft)
            },
            // 草稿箱开关关闭时不提供「存草稿」按钮（参数为 null 时对话框隐藏该入口）
            onSaveDraft = if (forumSettings.draftBoxEnabled) {
                { draftTitle: String, draftAuthor: String, draftContent: String ->
                    viewModel.savePostDraft(draftTitle, draftAuthor, draftContent) {
                        Toast.makeText(context, "已存入草稿箱", Toast.LENGTH_SHORT).show()
                    }
                }
            } else null,
            onCookieSelect = { settingsViewModel.selectCookie(it) },
            isSubmitting = isPostingThread,
            onDismiss = {
                if (!isPostingThread) {
                    showCreateDialog = false
                    pendingPostDraft = null
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

    activeImageUrl?.let { imageUrl ->
        ImageViewer(
            imageUrl = imageUrl,
            onDismiss = { activeImageUrl = null }
        )
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
    userScrollEnabled: Boolean = true,
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
        userScrollEnabled = userScrollEnabled,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 8.dp,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateThreadDialog(
    // 由 Activity 窗口量取后传入（Dialog 窗口在部分设备上拿不到 ime insets）
    imeBottomPx: Int,
    navBottomPx: Int,
    cookies: List<String>,
    selectedCookieIndex: Int,
    boardName: String = "",
    initialContent: String = "",
    initialTitle: String = "",
    initialName: String = "",
    onContentChange: (String) -> Unit = {},
    onSaveDraft: ((title: String, author: String, content: String) -> Unit)? = null,
    onCookieSelect: (Int) -> Unit,
    isSubmitting: Boolean = false,
    onDismiss: () -> Unit,
    onSubmit: (title: String, author: String, content: String, imageUri: Uri?) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var author by remember { mutableStateOf(initialName) }
    var contentValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialContent,
                selection = TextRange(initialContent.length)
            )
        )
    }
    var cookieMenuExpanded by remember { mutableStateOf(false) }
    var kaomojiExpanded by remember { mutableStateOf(false) }
    var showDiceDialog by remember { mutableStateOf(false) }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var previewAttachedUri by remember { mutableStateOf<Uri?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    /** 打开快捷面板后短时间内忽略 IME 可见（键盘下落动画） */
    var ignoreImeCloseUntilMs by remember { mutableStateOf(0L) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current

    var userEditedContent by remember { mutableStateOf(false) }
    LaunchedEffect(initialContent) {
        // 异步草稿晚到时不得覆盖用户已输入的内容（打开对话框到 DataStore 返回有几十毫秒窗口）
        if (!userEditedContent && contentValue.text != initialContent) {
            contentValue = contentValue.copy(
                text = initialContent,
                selection = TextRange(initialContent.length)
            )
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        attachedImageUri = uri
    }

    val canSubmit = !isSubmitting && (contentValue.text.isNotBlank() || attachedImageUri != null)

    fun submit() {
        if (!canSubmit) return
        val finalAuthor = if (author.isBlank()) "无名氏" else author
        // 成功路径在父层关闭对话框，这里先收起键盘、清焦点
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        onSubmit(title, finalAuthor, contentValue.text, attachedImageUri)
    }

    fun insertAtCursor(snippet: String) {
        userEditedContent = true
        val text = contentValue.text
        val selection = contentValue.selection
        val start = selection.start.coerceIn(0, text.length)
        val end = selection.end.coerceIn(0, text.length)
        val prefix = if (start > 0 && !text[start - 1].isWhitespace()) " " else ""
        val insert = prefix + snippet
        val newText = text.substring(0, start) + insert + text.substring(end)
        val newCursor = start + insert.length
        contentValue = TextFieldValue(
            text = newText,
            selection = TextRange(newCursor)
        )
        onContentChange(newText)
    }

    fun closeToolPanels() {
        if (kaomojiExpanded) kaomojiExpanded = false
    }

    fun requestLeave() {
        // 发送中不允许关闭对话框；也不弹"放弃"确认——原实现会弹确认，
        // 但点"放弃"被父层 isPostingThread 拦截成死点击
        if (isSubmitting) return
        if (confirmDiscard) {
            confirmDiscard = false
            return
        }
        if (kaomojiExpanded) {
            closeToolPanels()
            return
        }
        val dirty = title.isNotBlank() || contentValue.text.isNotBlank() || attachedImageUri != null
        if (dirty) confirmDiscard = true else onDismiss()
    }

    val isDark = isSystemInDarkTheme()
    // 与板块 / 串内页完全同一套毛玻璃色
    val glassTop = DaoTheme.colors.glassTopBar
    val glassBottom = DaoTheme.colors.glassNavBar

    Dialog(
        onDismissRequest = { requestLeave() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler(enabled = !confirmDiscard) { requestLeave() }
        val dialogView = LocalView.current
        val context = LocalContext.current
        ImmersiveDialogEffect(lightAppearance = !isDark)

        // 宿主 Activity 是 adjustNothing，但 Dialog 窗口有独立的 softInputMode：
        // 部分 OEM 默认让对话框 adjustResize → 内容被系统先顶一次，底栏的
        // imePaddingAndNavigationBars 再垫一次 = 双倍抬升，整个栏"飞"到键盘上方。
        // 显式钉死 ADJUST_NOTHING，抬升只由底栏自己的 inset 逻辑负责。
        val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            )
        }

        val hideSystemIme = remember(dialogView, context, keyboardController, focusManager) {
            {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                val window = dialogView.findDialogWindow()
                val token = window?.currentFocus?.windowToken
                    ?: dialogView.windowToken
                    ?: window?.decorView?.windowToken
                if (token != null) {
                    imm?.hideSoftInputFromWindow(token, InputMethodManager.HIDE_NOT_ALWAYS)
                }
            }
        }

        fun openKaomojiPanel() {
            kaomojiExpanded = true
            ignoreImeCloseUntilMs = android.os.SystemClock.uptimeMillis() + 450L
            hideSystemIme()
        }

        fun toggleKaomojiPanel() {
            if (kaomojiExpanded) {
                kaomojiExpanded = false
            } else {
                openKaomojiPanel()
            }
        }

        // 快捷面板展开时压一次键盘（避免多帧重复 hide 造成卡顿）
        LaunchedEffect(kaomojiExpanded) {
            if (kaomojiExpanded) {
                hideSystemIme()
                delay(48)
                hideSystemIme()
            }
        }

        // 【共享底部高度槽位】与串内回复栏同款：底部总占位恒取 max(键盘, 导航条, 面板)，
        // 绝不相加——键盘升起时槽位只在几个值之间平滑取大，玻璃由槽位顶起，杜绝双计飞栏。
        // ime/nav 高度由 Activity 窗口量取后经参数传入，见 CreateThreadDialog 调用处注释。
        var rememberedImeHeightPx by remember { mutableIntStateOf(0) }
        val defaultPanelHeightPx = with(density) { 280.dp.roundToPx() }
        // 面板高度跟随用户键盘实际高度（键盘升过一次后记录），否则兜底 280dp
        val targetPanelHeightPx =
            if (rememberedImeHeightPx > navBottomPx) rememberedImeHeightPx else defaultPanelHeightPx
        val isKeyboardRising = imeBottomPx > navBottomPx + with(density) { 40.dp.roundToPx() }
        LaunchedEffect(imeBottomPx, isKeyboardRising) {
            if (imeBottomPx > rememberedImeHeightPx) {
                rememberedImeHeightPx = imeBottomPx
            }
            // 键盘主动升起 → 同帧关面板（单一动画源）。尊重刚点开面板后的 450ms 窗口：
            // 从键盘态点开面板会先 hideSystemIme，键盘下落动画期间同样为 true，
            // 无窗口会把刚打开的面板立刻关掉。
            if (isKeyboardRising) {
                if (android.os.SystemClock.uptimeMillis() < ignoreImeCloseUntilMs) return@LaunchedEffect
                if (kaomojiExpanded) kaomojiExpanded = false
            }
        }
        // 【核心】底部总占位 = max(键盘, 面板)，绝不相加
        val bottomSlotHeightPx =
            if (kaomojiExpanded) maxOf(imeBottomPx, targetPanelHeightPx) else maxOf(imeBottomPx, navBottomPx)
        val bottomSlotHeightDp = with(density) { bottomSlotHeightPx.toDp() }

        SideEffect {
            val window = dialogView.findDialogWindow() ?: return@SideEffect
            window.applyImmersiveSystemBars(lightAppearance = !isDark)
            // 不遮罩、透明窗体：透出主界面主题光晕
            window.setDimAmount(0f)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            // adjustResize：由 IME insets 抬起底栏，不再叠加 graphicsLayer
            @Suppress("DEPRECATION")
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }

        // 半透明背景叠在主题流光上；顶/底栏用与其它界面相同的 glass 色
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background.copy(alpha = 0.92f))
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "发表新串",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (boardName.isNotBlank()) {
                                    Text(
                                        text = boardName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { requestLeave() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭"
                                )
                            }
                        },
                        actions = {
                            if (onSaveDraft != null) {
                                IconButton(
                                    onClick = { onSaveDraft(title, author, contentValue.text) },
                                    enabled = title.isNotBlank() || contentValue.text.isNotBlank()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = "存草稿"
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = { submit() },
                                enabled = canSubmit,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isSubmitting) "发送中" else "发送")
                            }
                        },
                        windowInsets = WindowInsets.statusBars,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = glassTop,
                            titleContentColor = colorScheme.onSurface,
                            navigationIconContentColor = colorScheme.onSurface,
                            actionIconContentColor = colorScheme.primary,
                            scrolledContainerColor = glassTop
                        )
                    )
                },
                bottomBar = {
                    // 与串内回复栏同款：玻璃 Surface 不挂任何 ime modifier，
                    // 由内容下方的共享槽位把控件顶起来，玻璃始终铺到屏幕底无断层
                    Surface(
                        color = glassBottom,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(
                                color = colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (cookies.isNotEmpty()) {
                                    // 与串内回复栏/举报对话框同一套饼干选择器：
                                    // 40dp 胶囊 + 自绘弹出菜单（贴锚点、四边 8dp、缩放淡出）
                                    CookieSelector(
                                        cookies = cookies,
                                        selectedIndex = selectedCookieIndex,
                                        expanded = cookieMenuExpanded,
                                        onExpandedChange = { cookieMenuExpanded = it },
                                        onSelect = onCookieSelect
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                ComposerToolButtons(
                                    diceSelected = false,
                                    kaomojiSelected = kaomojiExpanded,
                                    hasImage = attachedImageUri != null,
                                    onDiceClick = { showDiceDialog = true },
                                    onKaomojiClick = { toggleKaomojiPanel() },
                                    onImageClick = {
                                        kaomojiExpanded = false
                                        imagePickerLauncher.launch("image/*")
                                    }
                                )
                            }

                            // 【共享底部槽位】max(键盘, 面板)，绝不相加。面板开时直接填充槽位
                            // （无收起动画，高度变化完全由槽位驱动 = 单一动画源，杜绝 ime+面板叠加过冲）。
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(bottomSlotHeightDp)
                            ) {
                            if (kaomojiExpanded) {
                                KaomojiQuickPanel(
                                    onInsert = { insertAtCursor(it) },
                                    // 对话框里 navigationBarsPadding 可能拿不到值，
                                    // 用 Activity 量取的导航条高度显式垫底
                                    modifier = Modifier.padding(
                                        bottom = with(density) { navBottomPx.toDp() }
                                    ),
                                    heightDp = with(density) {
                                        (bottomSlotHeightPx - navBottomPx).coerceAtLeast(0)
                                            .toDp().value.roundToInt()
                                    }
                                )
                            }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                if (state.isFocused) closeToolPanels()
                            },
                        singleLine = true,
                        placeholder = {
                            Text(
                                "标题（选填）",
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        textStyle = MaterialTheme.typography.titleMedium,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outlineVariant,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                if (state.isFocused) closeToolPanels()
                            },
                        singleLine = true,
                        placeholder = {
                            Text(
                                "名称（选填，默认无名氏）",
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outlineVariant,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        BasicTextField(
                            value = contentValue,
                            onValueChange = { newValue ->
                                userEditedContent = true
                                contentValue = newValue
                                onContentChange(newValue.text)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .onFocusChanged { state ->
                                    if (state.isFocused) closeToolPanels()
                                },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (contentValue.text.isEmpty()) {
                                        Text(
                                            text = "正文内容…\n可附带图片，支持插入颜文字",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }

                    val reducedMotion = isReducedMotionEnabled()
                    AnimatedVisibility(
                        visible = attachedImageUri != null,
                        enter = MiooMotion.softExpandEnter(reducedMotion),
                        exit = MiooMotion.softExpandExit(reducedMotion)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        1.dp,
                                        colorScheme.outlineVariant.copy(alpha = 0.6f),
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(attachedImageUri),
                                    contentDescription = "图片预览",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { previewAttachedUri = attachedImageUri }
                                )
                                IconButton(
                                    onClick = { attachedImageUri = null },
                                    // 不要黑色圆底：只留白色 ×
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "移除图片",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "已附加图片",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant
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
                Dialog(
                    onDismissRequest = { previewAttachedUri = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
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
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("是否放弃编辑") },
            text = { Text("标题、正文或图片还没发送。放弃后仍会保留草稿，下次可以继续写。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDismiss()
                }) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") }
            }
        )
    }
}

