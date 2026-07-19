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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.mioo.dao.ui.components.ComposerToolButtons
import com.mioo.dao.ui.components.DiceQuickPanel
import com.mioo.dao.ui.components.FreeCopyDialog
import com.mioo.dao.ui.components.ImageViewer
import com.mioo.dao.ui.components.KaomojiQuickPanel
import com.mioo.dao.ui.components.ListThumbImage
import com.mioo.dao.ui.components.PrefetchListImages
import com.mioo.dao.ui.components.ThreadCard
import com.mioo.dao.ui.components.imeLiftOverNavigationBars
import com.mioo.dao.ui.components.toFile
import com.mioo.dao.ui.screens.settings.SettingsViewModel
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.ui.theme.MiooMotion
import com.mioo.dao.ui.theme.isReducedMotionEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ForumScreen(
    viewModel: ForumViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToThread: (threadId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    // Pins + cookies only — theme/font changes must not recompose the board list
    val forumSettings by settingsViewModel.forumScreenSettings.collectAsState()
    val context = LocalContext.current
    // Fresh LazyListState per board so scroll position & keys don't thrash across switches
    val currentForumId = viewModel.forumId
    val listState = remember(currentForumId) { LazyListState() }
    val drawerListState = rememberLazyListState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var freeCopyText by remember { mutableStateOf<String?>(null) }
    var activeImageUrl by remember { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val emptyStringLambda = remember { { _: String -> } }

    val previousForumId = rememberSaveable { mutableStateOf(currentForumId) }
    // True for a short window after board switch — pause prefetch/prewarm during swap
    var boardSwitchQuiet by remember { mutableStateOf(false) }

    val flatForumIds = remember(uiState.forumGroups, forumSettings.pinnedForums) {
        val pinned = uiState.forumGroups.flatMap { it.forums }
            .filter { forumSettings.pinnedForums.contains(it.id) }
            .distinctBy { it.id }
            .map { it.id }
        val rest = uiState.forumGroups.flatMap { group -> group.forums.map { it.id } }
        (pinned + rest).distinct()
    }

    val drawerBlocksMainList =
        drawerState.currentValue != DrawerValue.Closed ||
            drawerState.targetValue != DrawerValue.Closed

    val pullToRefreshState = rememberPullToRefreshState()
    // Top pull circle is the sole full-list loading indicator (initial / board switch / refresh).
    val showTopLoading = uiState.isRefreshing ||
        (uiState.isLoading && uiState.threads.isEmpty())
    // User gesture: only call refresh if the ViewModel is not already loading.
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing && !showTopLoading) {
            viewModel.refresh()
        }
    }
    // Keep the top circle in sync with ViewModel loading state.
    LaunchedEffect(showTopLoading) {
        if (showTopLoading) {
            pullToRefreshState.startRefresh()
        } else if (pullToRefreshState.isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }

    // Scroll to end detection for paging support
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false
            lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 2
        }
    }

    LaunchedEffect(shouldLoadMore.value, currentForumId) {
        if (shouldLoadMore.value && !uiState.isLoading && !uiState.isLastPage) {
            viewModel.loadNextPage()
        }
    }

    // Board change: quiet heavy work briefly (no overlapping list transition)
    LaunchedEffect(currentForumId) {
        if (currentForumId != previousForumId.value) {
            boardSwitchQuiet = true
            previousForumId.value = currentForumId
            delay(280)
            boardSwitchQuiet = false
        }
    }

    // When drawer opens, jump (no animateScroll) so open gesture stays smooth
    LaunchedEffect(drawerState.currentValue, currentForumId, flatForumIds) {
        if (drawerState.currentValue != DrawerValue.Open) return@LaunchedEffect
        val index = flatForumIds.indexOf(currentForumId)
        if (index >= 0) {
            val target = (index + 2).coerceAtMost(
                (drawerListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            )
            runCatching { drawerListState.scrollToItem(target) }
        }
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
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Spacer(modifier = Modifier.height(16.dp))
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
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (pinnedForumsList.isNotEmpty()) {
                        item(key = "header_pinned", contentType = "drawer_header") {
                            Text(
                                text = "置顶版块",
                                style = MaterialTheme.typography.labelMedium,
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
                        item(key = "header_${group.id}", contentType = "drawer_header") {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }
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
                        IconButton(onClick = {
                            // Instant jump to top before refresh (no scroll animation)
                            scope.launch { listState.scrollToItem(0) }
                            viewModel.refresh()
                        }) {
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
                    modifier = Modifier.padding(bottom = 120.dp),
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
                // Single list instance — no enter/exit overlap of two LazyColumns
                key(currentForumId) {
                    ForumThreadListPane(
                        forumKey = currentForumId,
                        uiState = uiState,
                        listState = listState,
                        emptyStringLambda = emptyStringLambda,
                        onNavigateToThread = onNavigateToThread,
                        onImageClick = { activeImageUrl = it },
                        onBlockThread = { settingsViewModel.addBlockedThread(it) },
                        onBlockUser = { settingsViewModel.addBlockedUser(it) },
                        onFreeCopy = { freeCopyText = it },
                        userScrollEnabled = !drawerBlocksMainList,
                        warmEnabled = allowBackgroundWarm
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
    LaunchedEffect(showCreateDialog) {
        if (showCreateDialog) {
            newThreadDraftText = settingsViewModel.getNewThreadDraft()
        }
    }

    if (showCreateDialog) {
        CreateThreadDialog(
            cookies = forumSettings.cookiesList,
            selectedCookieIndex = forumSettings.selectedCookieIndex,
            boardName = uiState.currentForumName,
            initialContent = newThreadDraftText,
            onContentChange = { draft ->
                // Debounced inside ViewModel — no per-keystroke coroutine spam
                settingsViewModel.saveNewThreadDraft(draft)
            },
            onCookieSelect = { settingsViewModel.selectCookie(it) },
            onDismiss = { showCreateDialog = false },
            onSubmit = { title, author, content, imageUri ->
                val imageFile = imageUri?.toFile(context)
                viewModel.createThread(title, author, content, imageFile)
                settingsViewModel.clearNewThreadDraft()
                showCreateDialog = false
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
    uiState: ForumUiState,
    listState: LazyListState,
    emptyStringLambda: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onBlockThread: (String) -> Unit,
    onBlockUser: (String) -> Unit,
    onFreeCopy: (String) -> Unit,
    userScrollEnabled: Boolean = true,
    warmEnabled: Boolean = true
) {
    // Empty while loading/refreshing: no center spinner — top pull circle is the only indicator.
    if (uiState.threads.isEmpty() && (uiState.isLoading || uiState.isRefreshing)) {
        return
    }

    if (uiState.threads.isEmpty() && !uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("该板块下暂无帖子。", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val displayItems = uiState.displayItems
    val quoteLinkColor = MaterialTheme.colorScheme.primary
    LaunchedEffect(displayItems, quoteLinkColor, forumKey, warmEnabled) {
        if (!warmEnabled || displayItems.isEmpty()) return@LaunchedEffect
        val bodies = displayItems.map { it.postData.content }
        withContext(Dispatchers.Default) {
            com.mioo.dao.ui.components.HtmlParseCache.prewarm(
                bodies.take(6),
                quoteLinkColor
            )
        }
        delay(800)
        if (!warmEnabled) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            com.mioo.dao.ui.components.HtmlParseCache.prewarm(
                bodies.drop(6).take(20),
                quoteLinkColor
            )
        }
    }
    val prefetchUrls = remember(displayItems) {
        displayItems.map { it.postData.imageUrl }
    }
    PrefetchListImages(
        imageUrls = prefetchUrls,
        listState = listState,
        sizePx = ListThumbImage.SIZE_PX,
        ahead = 5,
        initialDelayMs = 600,
        enabled = warmEnabled
    )

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
        items(
            items = displayItems,
            key = { it.id },
            contentType = { item ->
                if (item.hasImage) "thread_image" else "thread_text"
            }
        ) { item ->
            var showBlockDialog by remember { mutableStateOf(false) }

            if (showBlockDialog) {
                AlertDialog(
                    onDismissRequest = { showBlockDialog = false },
                    title = { Text("内容操作") },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    onBlockThread(item.idStr)
                                    showBlockDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("屏蔽此串 (No.${item.idStr})")
                            }
                            TextButton(
                                onClick = {
                                    onBlockUser(item.userHash)
                                    showBlockDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("屏蔽发言饼干 (ID: ${item.userHash})")
                            }
                            TextButton(
                                onClick = {
                                    onFreeCopy(item.rawContent)
                                    showBlockDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("自由复制帖子内容")
                            }
                            TextButton(
                                onClick = { showBlockDialog = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("取消")
                            }
                        }
                    },
                    confirmButton = {}
                )
            }

            val onThreadClickRemembered = remember(item.idStr) {
                { onNavigateToThread(item.idStr) }
            }
            val onImageClickRemembered = remember {
                { imageUrl: String -> onImageClick(imageUrl) }
            }
            val onLongClickRemembered = remember {
                { showBlockDialog = true }
            }

            ThreadCard(
                postData = item.postData,
                replyCount = item.replyCount,
                onThreadClick = onThreadClickRemembered,
                onQuoteClick = emptyStringLambda,
                onImageClick = onImageClickRemembered,
                onLongClick = onLongClickRemembered
            )
        }

        if (uiState.isLoading && displayItems.isNotEmpty()) {
            item(key = "loading_footer") {
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

        if (uiState.isLastPage) {
            item(key = "end_footer") {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateThreadDialog(
    cookies: List<String>,
    selectedCookieIndex: Int,
    boardName: String = "",
    initialContent: String = "",
    onContentChange: (String) -> Unit = {},
    onCookieSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (title: String, author: String, content: String, imageUri: Uri?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
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
    var diceExpanded by remember { mutableStateOf(false) }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    /** 打开快捷面板后短时间内忽略 IME 可见（键盘下落动画） */
    var ignoreImeCloseUntilMs by remember { mutableStateOf(0L) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current

    LaunchedEffect(initialContent) {
        if (contentValue.text != initialContent) {
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

    val canSubmit = contentValue.text.isNotBlank() || attachedImageUri != null

    fun submit() {
        if (!canSubmit) return
        val finalAuthor = if (author.isBlank()) "无名氏" else author
        onSubmit(title, finalAuthor, contentValue.text, attachedImageUri)
    }

    fun insertAtCursor(snippet: String) {
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
        if (diceExpanded) diceExpanded = false
    }

    BackHandler(onBack = onDismiss)

    val isDark = isSystemInDarkTheme()
    // 与板块 / 串内页完全同一套毛玻璃色
    val glassTop = DaoTheme.colors.glassTopBar
    val glassBottom = DaoTheme.colors.glassNavBar

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        val dialogView = LocalView.current
        val context = LocalContext.current

        val hideSystemIme = remember(dialogView, context, keyboardController, focusManager) {
            {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                val window = (dialogView.parent as? DialogWindowProvider)?.window
                val token = window?.currentFocus?.windowToken
                    ?: dialogView.windowToken
                    ?: window?.decorView?.windowToken
                if (token != null) {
                    imm?.hideSoftInputFromWindow(token, InputMethodManager.HIDE_NOT_ALWAYS)
                }
            }
        }

        fun openKaomojiPanel() {
            diceExpanded = false
            kaomojiExpanded = true
            ignoreImeCloseUntilMs = android.os.SystemClock.uptimeMillis() + 450L
            hideSystemIme()
        }

        fun openDicePanel() {
            kaomojiExpanded = false
            diceExpanded = true
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

        fun toggleDicePanel() {
            if (diceExpanded) {
                diceExpanded = false
            } else {
                openDicePanel()
            }
        }

        // 快捷面板展开时压一次键盘（避免多帧重复 hide 造成卡顿）
        LaunchedEffect(kaomojiExpanded, diceExpanded) {
            if (kaomojiExpanded || diceExpanded) {
                hideSystemIme()
                delay(48)
                hideSystemIme()
            }
        }

        // 系统键盘再次抬起 → 关闭颜文字 / 骰娘面板
        val imeBottomPx = WindowInsets.ime.getBottom(density)
        val imeThresholdPx = with(density) { 48.dp.toPx() }
        val imeVisible = imeBottomPx > imeThresholdPx
        LaunchedEffect(imeVisible) {
            if (!imeVisible) return@LaunchedEffect
            if (android.os.SystemClock.uptimeMillis() < ignoreImeCloseUntilMs) return@LaunchedEffect
            if (kaomojiExpanded || diceExpanded) {
                closeToolPanels()
            }
        }

        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            // 不遮罩、透明窗体：透出主界面主题光晕
            window.setDimAmount(0f)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            // ADJUST_NOTHING：由 Compose WindowInsets.ime 抬起底栏，避免系统缩放与 insets 冲突
            @Suppress("DEPRECATION")
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
            }
            WindowCompat.getInsetsController(window, dialogView).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }

        // 半透明背景叠在主题流光上；顶/底栏用与其它界面相同的 glass 色
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background.copy(alpha = 0.78f))
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
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭"
                                )
                            }
                        },
                        actions = {
                            FilledTonalButton(
                                onClick = { submit() },
                                enabled = canSubmit,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("发送")
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
                    // 导航条占布局高度；IME 用 graphicsLayer 上移，避免正文区随键盘逐帧 reflow
                    Surface(
                        color = glassBottom,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            // 底色延伸到手势小白条下方；控件再加 navigationBarsPadding
                            .imeLiftOverNavigationBars()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
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
                                    Box {
                                        OutlinedButton(
                                            onClick = { cookieMenuExpanded = true },
                                            contentPadding = PaddingValues(
                                                horizontal = 12.dp,
                                                vertical = 6.dp
                                            ),
                                            shape = RoundedCornerShape(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Cookie,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val currentCookie =
                                                cookies.getOrNull(selectedCookieIndex) ?: "选择饼干"
                                            val displayName = parseCookieName(currentCookie)
                                            Text(
                                                text = if (displayName.length > 8) {
                                                    displayName.take(8) + "…"
                                                } else {
                                                    displayName
                                                },
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = cookieMenuExpanded,
                                            onDismissRequest = { cookieMenuExpanded = false }
                                        ) {
                                            cookies.forEachIndexed { index, cookie ->
                                                val isSelected = index == selectedCookieIndex
                                                val displayName = parseCookieName(cookie)
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = if (displayName.length > 8) {
                                                                displayName.take(8) + "…"
                                                            } else {
                                                                displayName
                                                            },
                                                            fontWeight = if (isSelected) {
                                                                FontWeight.Bold
                                                            } else {
                                                                FontWeight.Normal
                                                            },
                                                            color = if (isSelected) {
                                                                colorScheme.primary
                                                            } else {
                                                                colorScheme.onSurface
                                                            }
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
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                ComposerToolButtons(
                                    diceSelected = diceExpanded,
                                    kaomojiSelected = kaomojiExpanded,
                                    hasImage = attachedImageUri != null,
                                    onDiceClick = { toggleDicePanel() },
                                    onKaomojiClick = { toggleKaomojiPanel() },
                                    onImageClick = {
                                        kaomojiExpanded = false
                                        diceExpanded = false
                                        imagePickerLauncher.launch("image/*")
                                    }
                                )
                            }

                            // Soft expand: ease-out, scale from 0.95, exit faster than enter
                            val panelReducedMotion = isReducedMotionEnabled()
                            AnimatedVisibility(
                                visible = diceExpanded,
                                enter = MiooMotion.softExpandEnter(panelReducedMotion),
                                exit = MiooMotion.softExpandExit(panelReducedMotion)
                            ) {
                                DiceQuickPanel(
                                    onInsert = { insertAtCursor(it) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                )
                            }
                            AnimatedVisibility(
                                visible = kaomojiExpanded,
                                enter = MiooMotion.softExpandEnter(panelReducedMotion),
                                exit = MiooMotion.softExpandExit(panelReducedMotion)
                            ) {
                                KaomojiQuickPanel(
                                    onInsert = { insertAtCursor(it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    heightDp = 220
                                )
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
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = { attachedImageUri = null },
                                    modifier = Modifier
                                        .size(26.dp)
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.55f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "移除图片",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
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
        }
    }
}

private fun parseCookieName(cookie: String): String {
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

