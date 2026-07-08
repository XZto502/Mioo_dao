package com.mioo.dao.ui.screens.feed

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mioo.dao.data.local.BookmarkEntity
import com.mioo.dao.data.model.Thread
import com.mioo.dao.ui.components.PostData
import com.mioo.dao.ui.components.ThreadCard
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.mioo.dao.ui.theme.DaoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    onNavigateToThread: (threadId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settingsFlow.collectAsState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = LocalContext.current


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的收藏") },
                actions = {
                    if (uiState.selectedFolderId != null) {
                        IconButton(onClick = { 
                            scope.launch { listState.animateScrollToItem(0) }
                            viewModel.refreshRemote()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
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
            // Folders Tab Row
            androidx.compose.material3.PrimaryScrollableTabRow(
                selectedTabIndex = if (uiState.selectedFolderId == null) 0 else settings.feedFolders.indexOfFirst { it.uuid == uiState.selectedFolderId } + 1,
                edgePadding = 8.dp,
                divider = {}
            ) {
                androidx.compose.material3.Tab(
                    selected = uiState.selectedFolderId == null,
                    onClick = { viewModel.selectFolder(null) },
                    text = { Text("本地收藏") }
                )
                settings.feedFolders.forEach { folder ->
                    androidx.compose.material3.Tab(
                        selected = uiState.selectedFolderId == folder.uuid,
                        onClick = { viewModel.selectFolder(folder.uuid) },
                        text = { Text(folder.name) }
                    )
                }
            }
            HorizontalDivider()

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.selectedFolderId == null && uiState.bookmarkedThreads.isEmpty()) {
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
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else if (uiState.selectedFolderId != null && uiState.remoteThreads.isEmpty()) {
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
            } else {
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
                        items(uiState.bookmarkedThreads, key = { it.id }) { bookmark ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        viewModel.unsubscribe(bookmark.id)
                                        true
                                    } else {
                                        false
                                    }
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val color = when (dismissState.dismissDirection) {
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
                                        if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                },
                                content = {
                                    val postData = remember(bookmark) { bookmark.toPostData() }
                                    val onThreadClickRemembered = remember(bookmark) { { onNavigateToThread(bookmark.id) } }
                                    ThreadCard(
                                        postData = postData,
                                        replyCount = 0,
                                        onThreadClick = onThreadClickRemembered,
                                        onQuoteClick = {},
                                        onImageClick = {}
                                    )
                                },
                                enableDismissFromStartToEnd = false
                            )
                        }
                    } else {
                        items(uiState.remoteThreads, key = { it.idStr }) { thread ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        viewModel.unsubscribeRemote(uiState.selectedFolderId!!, thread.idStr)
                                        true
                                    } else {
                                        false
                                    }
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val color = when (dismissState.dismissDirection) {
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
                                        if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                },
                                content = {
                                    val postData = remember(thread) { thread.toPostData(null) }
                                    val onThreadClickRemembered = remember(thread) { { onNavigateToThread(thread.idStr) } }
                                    ThreadCard(
                                        postData = postData,
                                        replyCount = thread.replyCount ?: 0,
                                        onThreadClick = onThreadClickRemembered,
                                        onQuoteClick = {},
                                        onImageClick = {}
                                    )
                                },
                                enableDismissFromStartToEnd = false
                            )
                        }
                    }
                }
            }
        }
    }
}

fun BookmarkEntity.toPostData(): PostData {
    return PostData(
        id = this.id,
        title = this.title ?: "",
        userName = this.name ?: "Anonymous",
        userId = this.userid,
        createdAt = this.now,
        content = this.content,
        imageUrl = null,
        isPo = false,
        isAdmin = false,
        isSage = false,
        resto = this.id
    )
}

fun Thread.toPostData(cdnPath: String?): PostData {
    return PostData(
        id = this.idStr,
        title = this.title ?: "",
        userName = this.name ?: "Anonymous",
        userId = this.userHash,
        createdAt = this.now,
        content = this.content,
        imageUrl = this.imageUrl?.let { "$cdnPath$it" },
        isPo = false,
        isAdmin = this.isAdmin,
        isSage = this.isSage,
        resto = this.idStr
    )
}
