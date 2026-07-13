package com.mioo.dao.ui.screens.forum

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.rememberAsyncImagePainter
import com.mioo.dao.ui.components.toFile
import com.mioo.dao.ui.components.KAOMOJI_LIST
import com.mioo.dao.ui.components.FreeCopyDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mioo.dao.ui.components.ThreadCard
import com.mioo.dao.ui.components.ImageViewer
import com.mioo.dao.ui.components.ListThumbImage
import com.mioo.dao.ui.components.PrefetchListImages
import com.mioo.dao.ui.screens.settings.SettingsViewModel
import com.mioo.dao.ui.theme.DaoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ForumScreen(
    viewModel: ForumViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToThread: (threadId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.settingsState.collectAsState()
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

    val flatForumIds = remember(uiState.forumGroups, settingsState.pinnedForums) {
        val pinned = uiState.forumGroups.flatMap { it.forums }
            .filter { settingsState.pinnedForums.contains(it.id) }
            .distinctBy { it.id }
            .map { it.id }
        val rest = uiState.forumGroups.flatMap { group -> group.forums.map { it.id } }
        (pinned + rest).distinct()
    }

    val drawerBlocksMainList =
        drawerState.currentValue != DrawerValue.Closed ||
            drawerState.targetValue != DrawerValue.Closed

    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh()
        }
    }
    LaunchedEffect(uiState.isRefreshing) {
        if (uiState.isRefreshing) {
            pullToRefreshState.startRefresh()
        } else {
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
                val pinnedForumsList = remember(uiState.forumGroups, settingsState.pinnedForums) {
                    uiState.forumGroups.flatMap { it.forums }
                        .filter { settingsState.pinnedForums.contains(it.id) }
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
                                    val wasPinned = settingsState.pinnedForums.contains(forum.id)
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
                            val isPinned = settingsState.pinnedForums.contains(forum.id)
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
        val context = androidx.compose.ui.platform.LocalContext.current
        CreateThreadDialog(
            cookies = settingsState.cookiesList,
            selectedCookieIndex = settingsState.selectedCookieIndex,
            initialContent = newThreadDraftText,
            onContentChange = { draft ->
                scope.launch {
                    settingsViewModel.saveNewThreadDraft(draft)
                }
            },
            onCookieSelect = { settingsViewModel.selectCookie(it) },
            onDismiss = { showCreateDialog = false },
            onSubmit = { title, author, content, imageUri ->
                val imageFile = imageUri?.toFile(context)
                viewModel.createThread(title, author, content, imageFile)
                scope.launch {
                    settingsViewModel.saveNewThreadDraft("")
                }
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
    if (uiState.threads.isEmpty() && (uiState.isLoading || uiState.isRefreshing)) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
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
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发表新串") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (cookies.isNotEmpty()) {
                    Box {
                        OutlinedButton(
                            onClick = { cookieMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Cookie, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            val currentCookie = cookies.getOrNull(selectedCookieIndex) ?: "选择饼干"
                            val displayName = parseCookieName(currentCookie)
                            Text(if (displayName.length > 8) displayName.take(8) + "..." else displayName)
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
                                            text = if (displayName.length > 8) displayName.take(8) + "..." else displayName,
                                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
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
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("作者（选填）") },
                    placeholder = { Text("无名氏") },
                    modifier = Modifier.fillMaxWidth()
                )
                 OutlinedTextField(
                    value = contentValue,
                    onValueChange = { newValue ->
                        contentValue = newValue
                        onContentChange(newValue.text)
                    },
                    label = { Text("内容") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5
                )

                // Quick Kaomoji Selection
                var kaomojiMenuExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { kaomojiMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Face, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("插入颜文字")
                    }
                    DropdownMenu(
                        expanded = kaomojiMenuExpanded,
                        onDismissRequest = { kaomojiMenuExpanded = false }
                    ) {
                        val rows = KAOMOJI_LIST.chunked(3)
                        Column(modifier = Modifier.padding(8.dp)) {
                            rows.forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { kaomoji ->
                                        TextButton(
                                            onClick = {
                                                val text = contentValue.text
                                                val selection = contentValue.selection
                                                val start = selection.start
                                                val end = selection.end
                                                val newText = text.substring(0, start) + kaomoji + text.substring(end)
                                                val newCursorPos = start + kaomoji.length
                                                
                                                contentValue = TextFieldValue(
                                                    text = newText,
                                                    selection = TextRange(newCursorPos)
                                                )
                                                onContentChange(newText)
                                                kaomojiMenuExpanded = false
                                            }
                                        ) {
                                            Text(kaomoji, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Image Attachment
                Text(
                    text = "附加图片",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (attachedImageUri != null) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
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
                                .size(24.dp)
                                .align(Alignment.TopEnd)
                                .background(Color.Black.copy(alpha = 0.6f), shape = androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Remove",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("选择图片")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalAuthor = if (author.isBlank()) "无名氏" else author
                    val contentText = contentValue.text
                    if (contentText.isNotBlank() || attachedImageUri != null) {
                        onSubmit(title, finalAuthor, contentText, attachedImageUri)
                    }
                },
                enabled = contentValue.text.isNotBlank() || attachedImageUri != null
            ) {
                Text("发表")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
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

