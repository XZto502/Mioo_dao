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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.hilt.navigation.compose.hiltViewModel
import com.mioo.dao.ui.screens.settings.SettingsViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.mioo.dao.ui.components.ImageViewer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mioo.dao.data.model.ForumGroup
import com.mioo.dao.data.model.Thread
import com.mioo.dao.ui.components.PostData
import com.mioo.dao.ui.components.ThreadCard
import com.mioo.dao.ui.components.FreeCopyDialog
import com.mioo.dao.ui.theme.DaoTheme
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

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
    val settingsState by settingsViewModel.settingsState.collectAsState()
    val blockedThreads = settingsState.blockedThreads
    val blockedUsers = settingsState.blockedUsers
    val blockedKeywords = settingsState.blockedKeywords
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Timeline", "Forums")
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
                val filteredThreads = remember(uiState.timelineThreads, blockedThreads, blockedUsers, blockedKeywords) {
                    uiState.timelineThreads.filter { thread ->
                        !blockedThreads.contains(thread.idStr) && 
                        !blockedUsers.contains(thread.userHash) &&
                        blockedKeywords.none { keyword ->
                            thread.title?.contains(keyword, ignoreCase = true) == true ||
                            thread.content?.contains(keyword, ignoreCase = true) == true
                        }
                    }
                }
                when (selectedTab) {
                    0 -> TimelineList(
                        threads = filteredThreads,
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

fun Thread.toPostData(cdnUrl: String = "https://image.nmb.best"): PostData {
    return PostData(
        id = this.idStr,
        title = this.title ?: "",
        userName = this.name ?: "Anonymous",
        userId = this.userHash,
        createdAt = this.now,
        content = this.content,
        imageUrl = if (this.imageUrl != null) "$cdnUrl/image/${this.imageUrl}" else null,
        isPo = false,
        isAdmin = this.isAdmin,
        isSage = this.isSage,
        resto = this.idStr
    )
}

@Composable
fun TimelineList(
    threads: List<Thread>,
    listState: LazyListState,
    onThreadClick: (String) -> Unit,
    onBlockThread: (String) -> Unit,
    onBlockUser: (String) -> Unit
) {
    var freeCopyText by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var activeImageUrl by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    if (threads.isEmpty()) {
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
            items(threads, key = { it.id }) { thread ->
                var showBlockDialog by remember { androidx.compose.runtime.mutableStateOf(false) }

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
                                        onBlockThread(thread.idStr)
                                        showBlockDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("屏蔽此串 (No.${thread.idStr})")
                                }
                                TextButton(
                                    onClick = {
                                        onBlockUser(thread.userHash)
                                        showBlockDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("屏蔽发言饼干 (ID: ${thread.userHash})")
                                }
                                TextButton(
                                    onClick = {
                                        freeCopyText = thread.content
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

                ThreadCard(
                    postData = thread.toPostData(),
                    replyCount = thread.replyCount ?: 0,
                    onThreadClick = { onThreadClick(thread.idStr) },
                    onQuoteClick = {},
                    onImageClick = { imageUrl -> activeImageUrl = imageUrl },
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
            item {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            items(group.forums) { forum ->
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
