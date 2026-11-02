package com.mioo.dao.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mioo.dao.data.local.HistoryType
import com.mioo.dao.data.model.effectiveTitle
import com.mioo.dao.data.repository.historyPlainText
import com.mioo.dao.ui.components.HtmlContent
import com.mioo.dao.ui.components.ImageViewer
import com.mioo.dao.ui.components.ListThumbAsyncImage
import com.mioo.dao.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onNavigateToThread: (String) -> Unit,
    onImageViewerOpenChange: (Boolean) -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val types = HistoryType.values()
    val pager = rememberPagerState { types.size }
    val type = types[pager.targetPage]
    val query by viewModel.query(type).collectAsState()
    var searching by rememberSaveable { mutableStateOf(false) }
    val showSearch = searching || query.isNotEmpty()
    var clearType by remember { mutableStateOf<HistoryType?>(null) }
    var deleteItem by remember { mutableStateOf<HistoryItem?>(null) }
    var image by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) searchFocus.requestFocus() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(image) { onImageViewerOpenChange(image != null) }
    DisposableEffect(Unit) { onDispose { onImageViewerOpenChange(false) } }
    BackHandler(enabled = showSearch && image == null && clearType == null && deleteItem == null) {
        viewModel.setQuery(type, "")
        searching = false
    }

    Box(modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        if (showSearch) TextField(
                            value = query, onValueChange = { viewModel.setQuery(type, it) },
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus), singleLine = true,
                            placeholder = { Text("搜索${type.label}") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            )
                        ) else Text("历史")
                    },
                    actions = {
                        IconButton(onClick = {
                            if (showSearch) { viewModel.setQuery(type, ""); searching = false }
                            else searching = true
                        }) { Icon(if (showSearch) Icons.Default.Close else Icons.Default.Search, contentDescription = if (showSearch) "关闭搜索" else "搜索历史") }
                        IconButton(onClick = { clearType = type }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "清空${type.label}")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DaoTheme.colors.glassTopBar)
                )
            },
            snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding().padding(bottom = 104.dp)) }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        types.forEachIndexed { index, entry ->
                            val selected = pager.targetPage == index
                            val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, label = "historySegment")
                            Surface(
                                onClick = { scope.launch { pager.animateScrollToPage(index, animationSpec = MiooMotion.tabPagerSpring()) } },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp), color = color
                            ) {
                                Box(Modifier.heightIn(min = 36.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                                    Text(entry.label, style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { types[it].name }) { page ->
                    val category = types[page]
                    val records by viewModel.items(category).collectAsState()
                    val categoryQuery by viewModel.query(category).collectAsState()
                    val list = rememberLazyListState()
                    var previousQuery by rememberSaveable(category.name) { mutableStateOf(categoryQuery) }
                    LaunchedEffect(categoryQuery) {
                        if (previousQuery != categoryQuery) { list.scrollToItem(0); previousQuery = categoryQuery }
                    }
                    if (records.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(bottom = 104.dp), contentAlignment = Alignment.Center) {
                            Text(if (categoryQuery.isBlank()) "暂无${category.label}" else "没有匹配的记录",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else LazyColumn(
                        state = list, modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp,
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 104.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(records, key = { it.id }, contentType = { it.type }) { entry ->
                            HistoryCard(entry, onClick = { viewModel.open(entry, onNavigateToThread) },
                                onLongClick = { deleteItem = entry }, onImageClick = { image = it })
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(item: HistoryItem, onClick: () -> Unit, onLongClick: () -> Unit, onImageClick: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val meta = readingMetaStyle()
    val date = remember(item.timestamp) { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.timestamp)) }
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = readingCardColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), border = readingCardBorder()
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (item.type == HistoryType.BROWSE) HistoryPill(item.forumName)
                        HistoryPill(item.cookieHash.ifBlank { "未记录饼干" }, secondary = true)
                        if (item.type == HistoryType.REPLY) HistoryPill("我的回复")
                        if (item.type == HistoryType.NEW_THREAD) HistoryPill("[PO]")
                    }
                    Text(if (item.type == HistoryType.REPLY) "$date · ${item.forumName}" else date,
                        style = meta, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(item.threadId?.let { "No.$it" } ?: "串号确认中", style = meta,
                    color = if (item.threadId == null) scheme.primary else scheme.onSurfaceVariant)
            }
            if (item.type == HistoryType.NEW_THREAD) {
                Spacer(Modifier.height(8.dp)); HistoryPill(item.forumName)
            }
            Spacer(Modifier.height(10.dp))
            if (item.type == HistoryType.REPLY) {
                val summary = remember(item.threadSummary) { historyPlainText(item.threadSummary).ifBlank { "主串 No.${item.threadId.orEmpty()}" } }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(scheme.secondaryContainer.copy(alpha = 0.3f)).height(IntrinsicSize.Min)) {
                    Box(Modifier.width(2.5.dp).fillMaxHeight().background(scheme.primary.copy(alpha = 0.45f)))
                    Text(summary, Modifier.padding(10.dp), style = readingQuoteStyle(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(10.dp))
            }
            item.title.effectiveTitle()?.let { title ->
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = scheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
            }
            if (item.type == HistoryType.BROWSE) HtmlContent(item.content, onQuoteClick = {}, style = readingBodyStyle(),
                maxLines = 5, overflow = TextOverflow.Ellipsis, enableGestures = false)
            else Text(item.content, style = readingBodyStyle(), maxLines = 8, overflow = TextOverflow.Ellipsis)
            item.image?.let { image ->
                Spacer(Modifier.height(10.dp))
                ListThumbAsyncImage(imageUrl = image, contentDescription = "历史附图",
                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(10.dp)).clickable { onImageClick(image) })
            }
            if (item.type == HistoryType.BROWSE && item.lastReadPage > 1) {
                Spacer(Modifier.height(10.dp)); HistoryPill("读至第 ${item.lastReadPage} 页", maxWidth = 160)
            }
        }
    }
}

@Composable
private fun HistoryPill(text: String, secondary: Boolean = false, maxWidth: Int = 92) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(6.dp), color = if (secondary) scheme.secondaryContainer.copy(alpha = 0.65f) else scheme.primary.copy(alpha = 0.09f)) {
        Text(text, Modifier.widthIn(max = maxWidth.dp).padding(horizontal = 6.dp, vertical = 2.dp), style = readingMetaStyle(),
            color = if (secondary) scheme.onSecondaryContainer else scheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
