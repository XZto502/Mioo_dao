package com.mioo.dao.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import com.mioo.dao.ui.theme.isReducedMotionEnabled
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mioo.dao.data.local.HistoryType
import com.mioo.dao.data.model.effectiveTitle
import com.mioo.dao.ui.theme.readingBackground
import com.mioo.dao.ui.theme.readingCardColor
import com.mioo.dao.ui.components.HtmlContent
import com.mioo.dao.ui.components.HtmlParseCache
import com.mioo.dao.ui.components.LocalDicePreset
import com.mioo.dao.ui.components.PostData
import com.mioo.dao.ui.components.ReadingIdentity
import com.mioo.dao.ui.components.ImageViewer
import com.mioo.dao.ui.components.ListThumbAsyncImage
import com.mioo.dao.ui.components.formatReadingTimestamp
import com.mioo.dao.ui.components.slowFlingClick
import com.mioo.dao.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onNavigateToThread: (String) -> Unit,
    onImageViewerOpenChange: (Boolean) -> Unit = {},
    isActiveTab: () -> Boolean = { true },
    reducedMotion: Boolean = isReducedMotionEnabled(),
    viewModel: HistoryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val types = remember { HistoryType.values() }
    val initialType by viewModel.initialType.collectAsState()
    val restoredType = initialType ?: return
    val pager = rememberPagerState(initialPage = types.indexOf(restoredType).coerceAtLeast(0)) { types.size }
    val listStates = types.associateWith { entry ->
        rememberSaveable(entry.name, saver = LazyListState.Saver) { LazyListState() }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { viewModel.saveSelectedType(types[it]) }
    }
    val type = types[pager.targetPage]
    val query by viewModel.query(type).collectAsState()
    var searching by rememberSaveable { mutableStateOf(false) }
    val showSearch = searching || query.isNotEmpty()
    var clearType by remember { mutableStateOf<HistoryType?>(null) }
    var deleteItem by remember { mutableStateOf<HistoryItem?>(null) }
    var image by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var pageSwitchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val searchFocus = remember { FocusRequester() }
    var searchFocusPending by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun closeSearch() {
        searchFocusPending = false
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        viewModel.setQuery(type, "")
        searching = false
    }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(image) { onImageViewerOpenChange(image != null) }
    DisposableEffect(Unit) { onDispose { onImageViewerOpenChange(false) } }
    BackHandler(enabled = showSearch && image == null && clearType == null && deleteItem == null && isActiveTab()) {
        closeSearch()
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = readingBackground(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        AnimatedContent(targetState = showSearch, transitionSpec = {
                            fadeIn(tween(if (reducedMotion) 0 else 180)) togetherWith fadeOut(tween(if (reducedMotion) 0 else 130))
                        }, label = "historySearch") { active ->
                        if (active) TextField(
                            value = query, onValueChange = { viewModel.setQuery(type, it) },
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus)
                                .onGloballyPositioned {
                                    // Scaffold subcomposes its top bar during layout. Request
                                    // focus only after this TextField and its target are attached.
                                    if (searchFocusPending) {
                                        searchFocusPending = false
                                        searchFocus.requestFocus()
                                    }
                                }, singleLine = true,
                            placeholder = { Text("搜索${type.label}") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            )
                        ) else Text("历史", style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * 1.125f), fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (showSearch) closeSearch()
                            else {
                                searchFocusPending = true
                                searching = true
                            }
                        }) { Icon(if (showSearch) Icons.Default.Close else Icons.Default.Search, contentDescription = if (showSearch) "关闭搜索" else "搜索历史") }
                        if (!showSearch) {
                            IconButton(onClick = {
                                viewModel.refresh(type)
                                scope.launch { listStates[type]?.scrollToItem(0) }
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新${type.label}")
                            }
                        }
                        IconButton(onClick = { clearType = type }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "清空${type.label}")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = readingTopBarColor())
                )
            },
            snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding().padding(bottom = 104.dp)) }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEachIndexed { index, entry ->
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
                            Text(entry.label, Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                fontSize = 13.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false)
                        }
                    }
                }

                val pagerTouchGuard = com.mioo.dao.ui.components.rememberTouchGuardConfiguration(
                    com.mioo.dao.ui.components.LocalScrollTouchGuardEnabled.current
                )
                CompositionLocalProvider(
                    androidx.compose.foundation.LocalOverscrollConfiguration provides null,
                    androidx.compose.ui.platform.LocalViewConfiguration provides pagerTouchGuard
                ) {
                HorizontalPager(state = pager, beyondBoundsPageCount = 0, modifier = Modifier.fillMaxSize(), key = { types[it].name }) { page ->
                    val category = types[page]
                    val quoteColor = MaterialTheme.colorScheme.primary
                    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                    val dicePreset = LocalDicePreset.current
                    val pageState by viewModel.pageState(category).collectAsState()
                    LaunchedEffect(category) {
                        snapshotFlow { isActiveTab() }
                            .distinctUntilChanged()
                            .collect { active ->
                                if (active) viewModel.ensureLoaded(category)
                            }
                    }
                    LaunchedEffect(category, pageState.loaded, pageState.items, quoteColor, dark, dicePreset) {
                        if (category == HistoryType.BROWSE && pageState.loaded && pageState.items.isNotEmpty()) {
                            withContext(Dispatchers.Default) {
                                HtmlParseCache.prewarm(pageState.items.take(8).map { it.content }, quoteColor, dark, dicePreset)
                            }
                        }
                    }
                    val recordsLoaded = pageState.loaded
                    val readyRecords = pageState.items
                    val categoryQuery by viewModel.query(category).collectAsState()
                    val list = listStates.getValue(category)
                    var previousQuery by rememberSaveable(category.name) { mutableStateOf(categoryQuery) }
                    LaunchedEffect(categoryQuery) {
                        if (previousQuery != categoryQuery) { list.scrollToItem(0); previousQuery = categoryQuery }
                    }
                    if (recordsLoaded && readyRecords.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(bottom = 104.dp), contentAlignment = Alignment.Center) {
                            Text(if (categoryQuery.isBlank()) "暂无${category.label}" else "没有匹配的记录",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (readyRecords.isNotEmpty()) {
                        val initialViewport = com.mioo.dao.ui.components.rememberInitialListViewport(true)
                        val responsiveConfiguration = com.mioo.dao.ui.components.rememberResponsiveListConfiguration()
                        val responsiveFling = com.mioo.dao.ui.components.rememberResponsiveListFling()
                        CompositionLocalProvider(androidx.compose.ui.platform.LocalViewConfiguration provides responsiveConfiguration) {
                            LazyColumn(
                                state = list,
                                flingBehavior = responsiveFling,
                                userScrollEnabled = initialViewport.ready,
                                modifier = Modifier.fillMaxSize().then(initialViewport.modifier),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp,
                                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 104.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(readyRecords, key = { it.id }, contentType = { it.type }) { entry ->
                                    val onClick = remember(entry, viewModel, onNavigateToThread) {
                                        { viewModel.open(entry, onNavigateToThread) }
                                    }
                                    HistoryCard(
                                        entry,
                                        onClick = onClick,
                                        onLongClick = { deleteItem = entry },
                                        onImageClick = { image = it },
                                        modifier = Modifier.slowFlingClick(responsiveFling, onClick)
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }
        image?.let { ImageViewer(imageUrl = it, onDismiss = { image = null }) }
    }

    clearType?.let { category -> AlertDialog(
        onDismissRequest = { clearType = null }, properties = immersiveDialogProperties(),
        title = { ImmersiveDialogEffect(); Text("清空${category.label}？") },
        text = { Text("将删除这一分类下的全部记录，其他历史分类不受影响。") },
        confirmButton = { TextButton(onClick = { viewModel.clear(category); clearType = null }) { Text("清空", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { clearType = null }) { Text("取消") } }
    ) }
    deleteItem?.let { entry -> AlertDialog(
        onDismissRequest = { deleteItem = null }, properties = immersiveDialogProperties(),
        title = { ImmersiveDialogEffect(); Text("删除这条历史？") },
        text = { Text("仅删除本地记录，原串和已发送的内容会保留。") },
        confirmButton = { TextButton(onClick = { viewModel.delete(entry); deleteItem = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteItem = null }) { Text("取消") } }
    ) }
}

private val collapseBlankLinesRegex = Regex("(\\r?\\n\\s*){2,}")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(
    item: HistoryItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val meta = readingMetaStyle()
    val date = formatReadingTimestamp(item.timestamp, includeSeconds = false)
    Card(
        modifier = modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = readingCardColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = com.mioo.dao.ui.theme.readingCardBorder()
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ReadingIdentity(PostData(id = item.threadId.orEmpty(), userId = item.cookieHash.ifBlank { "未记录饼干" },
                    createdAt = "", content = "", isPo = false,
                    isAdmin = item.cookieHash.equals("Admin", ignoreCase = true)))
                if (item.type == HistoryType.NEW_THREAD) { Spacer(Modifier.width(4.dp)); HistoryPill("PO") }
                Spacer(Modifier.width(6.dp))
                HistoryPill(item.forumName)
                Text(" · $date", modifier = Modifier.weight(1f), style = meta.copy(fontWeight = FontWeight.Normal),
                    color = scheme.onSurfaceVariant.copy(alpha = 0.6f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(6.dp))
                Text(item.threadId?.let { "No.$it" } ?: "串号确认中", style = meta.copy(fontWeight = FontWeight.Normal), maxLines = 1, softWrap = false,
                    color = if (item.threadId == null) scheme.primary else scheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            item.title.effectiveTitle()?.let { title ->
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = scheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
            }
            val bodyStyle = readingBodyStyle()
            if (item.type == HistoryType.BROWSE) HtmlContent(item.content, onQuoteClick = {}, style = bodyStyle,
                maxLines = 5, overflow = TextOverflow.Ellipsis, enableGestures = false, compactBlankLines = true)
            else {
                val preview = remember(item.content) { item.content.replace(collapseBlankLinesRegex, "\n") }
                Text(preview, style = bodyStyle, maxLines = 8, overflow = TextOverflow.Ellipsis)
            }
            item.image?.let { image ->
                Spacer(Modifier.height(10.dp))
                ListThumbAsyncImage(imageUrl = image, contentDescription = "历史附图",
                    preserveRatio = true, modifier = Modifier.clickable { onImageClick(image) })
            }
            if (item.type == HistoryType.BROWSE && item.lastReadPage > 1) {
                Spacer(Modifier.height(10.dp)); HistoryPill("读至第 ${item.lastReadPage} 页", maxWidth = 160)
            }
        }
    }
}

@Composable
private fun HistoryPill(text: String, secondary: Boolean = false, maxWidth: Int = 92) {
    com.mioo.dao.ui.components.ReadingPill(text, maxWidth = maxWidth)
}
