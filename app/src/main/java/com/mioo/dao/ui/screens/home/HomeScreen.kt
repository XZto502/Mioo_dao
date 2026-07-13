package com.mioo.dao.ui.screens.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mioo.dao.data.model.ForumGroup
import com.mioo.dao.ui.components.FreeCopyDialog
import com.mioo.dao.ui.components.HtmlParseCache
import com.mioo.dao.ui.components.ImageViewer
import com.mioo.dao.ui.components.PrefetchListImages
import com.mioo.dao.ui.components.ThreadCard
import com.mioo.dao.ui.components.ThreadListItem
import com.mioo.dao.ui.screens.settings.SettingsViewModel
import com.mioo.dao.ui.theme.DaoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToForum: (forumId: String, forumName: String) -> Unit,
    onNavigateToThread: (threadId: String) -> Unit,
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = remember { listOf("Timeline", "Forums") }
    val timelineListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mioo DAO") },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            if (selectedTab == 0) timelineListState.animateScrollToItem(0)
                        }
                        viewModel.refreshData()
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
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                        icon = {
                            Icon(
                                imageVector = if (index == 0) Icons.Outlined.ListAlt else Icons.Outlined.Forum,
                                contentDescription = title
                            )
                        }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTab) {
                    0 -> TimelineList(
                        items = uiState.displayItems,
                        listState = timelineListState,
                        onThreadClick = onNavigateToThread,
                        onBlockThread = { settingsViewModel.addBlockedThread(it) },
                        onBlockUser = { settingsViewModel.addBlockedUser(it) }
                    )
                    1 -> ForumList(
                        forumGroups = uiState.forumGroups,
                        onForumClick = onNavigateToForum
                    )
                }
            }
        }
    }
}

@Composable
fun TimelineList(
    items: List<ThreadListItem>,
    listState: LazyListState,
    onThreadClick: (String) -> Unit,
    onBlockThread: (String) -> Unit,
    onBlockUser: (String) -> Unit
) {
    var freeCopyText by remember { mutableStateOf<String?>(null) }
    var activeImageUrl by remember { mutableStateOf<String?>(null) }
    val emptyStringLambda = remember { { _: String -> } }
    val quoteLinkColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(items, quoteLinkColor) {
        if (items.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                HtmlParseCache.prewarm(items.map { it.postData.content }, quoteLinkColor)
            }
        }
    }

    val prefetchUrls = remember(items) { items.map { it.postData.imageUrl } }
    PrefetchListImages(
        imageUrls = prefetchUrls,
        listState = listState,
        sizePx = com.mioo.dao.ui.components.ListThumbImage.SIZE_PX,
        ahead = 10
    )

    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No active threads", style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
        ) {
            items(
                items = items,
                key = { it.id },
                contentType = { item -> if (item.hasImage) "thread_image" else "thread_text" }
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
                                        freeCopyText = item.rawContent
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
                    { onThreadClick(item.idStr) }
                }
                val onImageClickRemembered = remember {
                    { imageUrl: String -> activeImageUrl = imageUrl }
                }

                ThreadCard(
                    postData = item.postData,
                    replyCount = item.replyCount,
                    onThreadClick = onThreadClickRemembered,
                    onQuoteClick = emptyStringLambda,
                    onImageClick = onImageClickRemembered,
                    onLongClick = { showBlockDialog = true }
                )
            }
        }
    }

    freeCopyText?.let { text ->
        FreeCopyDialog(
            text = text,
            onDismiss = { freeCopyText = null }
        )
    }

    activeImageUrl?.let { imageUrl ->
        ImageViewer(
            imageUrl = imageUrl,
            onDismiss = { activeImageUrl = null }
        )
    }
}

@Composable
fun ForumList(
    forumGroups: List<ForumGroup>,
    onForumClick: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        forumGroups.forEach { group ->
            item(key = "group_${group.id}", contentType = "forum_group_header") {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            items(group.forums, key = { it.id }, contentType = { "forum_card" }) { forum ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onForumClick(forum.id, forum.name) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = forum.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (!forum.msg.isNullOrBlank()) {
                                Text(
                                    text = forum.msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = ">>",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
