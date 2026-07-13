package com.mioo.dao.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mioo.dao.ui.components.BookmarkListItem
import com.mioo.dao.ui.components.HtmlParseCache
import com.mioo.dao.ui.components.ListThumbImage
import com.mioo.dao.ui.components.PrefetchListImages
import com.mioo.dao.ui.components.ThreadCard
import com.mioo.dao.ui.components.ThreadListItem
import com.mioo.dao.ui.theme.DaoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    onNavigateToThread: (threadId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val emptyStringLambda = remember { { _: String -> } }
    val quoteLinkColor = MaterialTheme.colorScheme.primary

    // Pre-parse HTML for currently visible feed list
    LaunchedEffect(
        uiState.selectedFolderId,
        uiState.localDisplayItems,
        uiState.remoteDisplayItems,
        quoteLinkColor
    ) {
        val htmls = if (uiState.selectedFolderId == null) {
            uiState.localDisplayItems.map { it.postData.content }
        } else {
            uiState.remoteDisplayItems.map { it.postData.content }
        }
        if (htmls.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                HtmlParseCache.prewarm(htmls, quoteLinkColor)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的收藏") },
                actions = {
                    IconButton(onClick = {
                        scope.launch { listState.animateScrollToItem(0) }
                        if (uiState.selectedFolderId != null) {
                            viewModel.refreshRemote()
                        } else {
                            viewModel.refreshReplyBadges()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DaoTheme.colors.glassTopBar
                )
            )
        },
        modifier = modifier,
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            androidx.compose.material3.PrimaryScrollableTabRow(
                selectedTabIndex = if (uiState.selectedFolderId == null) {
                    0
                } else {
                    uiState.feedFolders.indexOfFirst { it.uuid == uiState.selectedFolderId } + 1
                },
                edgePadding = 8.dp,
                divider = {}
            ) {
                androidx.compose.material3.Tab(
                    selected = uiState.selectedFolderId == null,
                    onClick = { viewModel.selectFolder(null) },
                    text = { Text("本地收藏") }
                )
                uiState.feedFolders.forEach { folder ->
                    androidx.compose.material3.Tab(
                        selected = uiState.selectedFolderId == folder.uuid,
                        onClick = { viewModel.selectFolder(folder.uuid) },
                        text = { Text(folder.name) }
                    )
                }
            }
            HorizontalDivider()

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.selectedFolderId == null && uiState.localDisplayItems.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "暂无本地收藏",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "在帖子内点击星星图标即可收藏到本地。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                uiState.selectedFolderId != null && uiState.remoteDisplayItems.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "该云端收藏夹为空",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    val feedImageUrls = remember(
                        uiState.selectedFolderId,
                        uiState.localDisplayItems,
                        uiState.remoteDisplayItems
                    ) {
                        if (uiState.selectedFolderId == null) {
                            uiState.localDisplayItems.map { it.postData.imageUrl }
                        } else {
                            uiState.remoteDisplayItems.map { it.postData.imageUrl }
                        }
                    }
                    PrefetchListImages(
                        imageUrls = feedImageUrls,
                        listState = listState,
                        sizePx = ListThumbImage.SIZE_PX,
                        ahead = 5,
                        initialDelayMs = 400
                    )

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = 100.dp
                        )
                    ) {
                        if (uiState.selectedFolderId == null) {
                            items(
                                items = uiState.localDisplayItems,
                                key = { it.id },
                                contentType = { item ->
                                    if (item.postData.imageUrl != null) "bookmark_image" else "bookmark_text"
                                }
                            ) { item ->
                                LocalBookmarkRow(
                                    item = item,
                                    onNavigateToThread = onNavigateToThread,
                                    onUnsubscribe = { viewModel.unsubscribe(item.id) },
                                    emptyStringLambda = emptyStringLambda
                                )
                            }
                        } else {
                            val folderId = uiState.selectedFolderId!!
                            items(
                                items = uiState.remoteDisplayItems,
                                key = { it.idStr },
                                contentType = { item ->
                                    if (item.hasImage) "remote_image" else "remote_text"
                                }
                            ) { item ->
                                RemoteBookmarkRow(
                                    item = item,
                                    onNavigateToThread = onNavigateToThread,
                                    onUnsubscribe = {
                                        viewModel.unsubscribeRemote(folderId, item.idStr)
                                    },
                                    emptyStringLambda = emptyStringLambda
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalBookmarkRow(
    item: BookmarkListItem,
    onNavigateToThread: (String) -> Unit,
    onUnsubscribe: () -> Unit,
    emptyStringLambda: (String) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onUnsubscribe()
                true
            } else {
                false
            }
        }
    )
    val onClick = remember(item.id) { { onNavigateToThread(item.id) } }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            DismissBackground(dismissDirection = dismissState.dismissDirection)
        },
        content = {
            Box {
                ThreadCard(
                    postData = item.postData,
                    replyCount = 0,
                    onThreadClick = onClick,
                    onQuoteClick = emptyStringLambda,
                    onImageClick = emptyStringLambda
                )
                if (item.newReplyCount > 0) {
                    Badge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        containerColor = MaterialTheme.colorScheme.error
                    ) {
                        Text("+${item.newReplyCount}")
                    }
                }
            }
        },
        enableDismissFromStartToEnd = false
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteBookmarkRow(
    item: ThreadListItem,
    onNavigateToThread: (String) -> Unit,
    onUnsubscribe: () -> Unit,
    emptyStringLambda: (String) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onUnsubscribe()
                true
            } else {
                false
            }
        }
    )
    val onClick = remember(item.idStr) { { onNavigateToThread(item.idStr) } }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            DismissBackground(dismissDirection = dismissState.dismissDirection)
        },
        content = {
            ThreadCard(
                postData = item.postData,
                replyCount = item.replyCount,
                onThreadClick = onClick,
                onQuoteClick = emptyStringLambda,
                onImageClick = emptyStringLambda
            )
        },
        enableDismissFromStartToEnd = false
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissBackground(dismissDirection: SwipeToDismissBoxValue) {
    val color = when (dismissDirection) {
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color, shape = MaterialTheme.shapes.medium)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (dismissDirection == SwipeToDismissBoxValue.EndToStart) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
