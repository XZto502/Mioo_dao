package com.mioo.dao.ui.screens.feed

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mioo.dao.data.local.BookmarkEntity
import com.mioo.dao.data.local.CloudFeedThreadEntity
import com.mioo.dao.data.local.SettingsDataStore
import com.mioo.dao.data.model.FeedFolder
import com.mioo.dao.data.model.Thread
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.data.repository.SettingsRepository
import com.mioo.dao.data.repository.ThreadRepository
import com.mioo.dao.ui.components.BookmarkListItem
import com.mioo.dao.ui.components.ThreadListItem
import com.mioo.dao.ui.components.toBookmarkListItems
import com.mioo.dao.ui.components.toFilteredThreadListItems
import com.mioo.dao.ui.components.withReplyCounts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Immutable
data class FeedUiState(
    val bookmarkedThreads: List<BookmarkEntity> = emptyList(),
    /** 已离线串列表（按下载时间倒序）——「已离线」标签的主列表。 */
    val offlineSummaries: List<com.mioo.dao.data.local.OfflineThreadSummary> = emptyList(),
    /** 尚未离线完成的本地收藏（后台巡检补齐中），排在离线列表后展示。 */
    val localDisplayItems: List<BookmarkListItem> = emptyList(),
    val remoteThreads: List<Thread> = emptyList(),
    val remoteDisplayItems: List<ThreadListItem> = emptyList(),
    /** 云端订阅串的已知回复数（threadId → count）。feed 接口不带回复数，靠后台逐串补抓。 */
    val remoteReplyCounts: Map<String, Int> = emptyMap(),
    val feedFolders: List<FeedFolder> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreRemote: Boolean = false,
    val selectedFolderId: String? = null,
    val selectionReady: Boolean = false,
    /** 正在重新离线的串号（更新按钮转圈）。 */
    val offlineUpdatingIds: Set<String> = emptySet()
)

@Immutable
internal class CloudFeedIndexSnapshot(val entries: List<CloudFeedThreadEntity>) {
    val byFolder = entries.groupBy { it.feedUuid }
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val threadRepository: ThreadRepository,
    private val settingsRepository: SettingsRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    internal val cloudIndex = settingsDataStore.feedFoldersFlow
        .map { folders -> folders.map { it.uuid }.distinct().sorted() }
        .distinctUntilChanged()
        .flatMapLatest { uuids ->
            if (uuids.isEmpty()) flowOf(emptyList()) else threadRepository.observeCloudFeedIndex(uuids)
        }
        .map(::CloudFeedIndexSnapshot)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CloudFeedIndexSnapshot(emptyList()))

    val fullyIndexedCloudFolders = threadRepository.observeFullyIndexedCloudFolders()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** 一次性错误提示事件（取消订阅失败 / 加载更多失败等），由 FeedScreen 弹 Toast */
    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private var remotePage = 1
    private var remoteLoadingMore = false
    private var remoteEndReached = false

    init {
        viewModelScope.launch {
            val saved = settingsDataStore.lastFeedFolderFlow.first().ifBlank { null }
            val folders = settingsDataStore.feedFoldersFlow.first()
            val selected = saved?.takeIf { id -> folders.any { it.uuid == id } }
            _uiState.update { it.copy(feedFolders = folders, selectedFolderId = selected, selectionReady = true) }
            if (selected != null) loadRemoteFeed(selected)
            if (saved != selected) settingsDataStore.saveLastFeedFolder(selected)
        }
        // 「已离线」标签：离线串列表 + 尚未离线的存量收藏（后台巡检会逐串补齐）
        viewModelScope.launch {
            combine(
                threadRepository.getOfflineThreadSummaries().distinctUntilChanged(),
                threadRepository.getBookmarks().distinctUntilChanged()
            ) { summaries, bookmarks ->
                val offlineIds = summaries.mapTo(HashSet()) { it.threadId }
                summaries to bookmarks.filter { it.id !in offlineIds }
            }.flowOn(Dispatchers.Default).collectLatest { (summaries, pending) ->
                val items = withContext(Dispatchers.Default) {
                    pending.toBookmarkListItems()
                }
                _uiState.update {
                    it.copy(
                        bookmarkedThreads = pending,
                        offlineSummaries = summaries,
                        localDisplayItems = items
                    )
                }
            }
        }

        // Only folders — avoid full settings recompose noise
        viewModelScope.launch {
            settingsDataStore.feedFoldersFlow
                .distinctUntilChanged()
                .collect { folders ->
                    _uiState.update { it.copy(feedFolders = folders) }
                    val state = _uiState.value
                    if (state.selectionReady && state.selectedFolderId != null && folders.none { it.uuid == state.selectedFolderId }) {
                        selectFolder(null)
                    }
                }
        }

        // Defer badge poll so first paint / fling are not contending for network + Room
        viewModelScope.launch {
            delay(1800)
            threadRepository.refreshBookmarkReplyCounts(limit = 15)
        }
    }

    fun refreshReplyBadges() {
        viewModelScope.launch {
            threadRepository.refreshBookmarkReplyCounts(limit = 25)
        }
    }

    private data class CachedRemoteFolder(
        val threads: List<Thread>,
        val displayItems: List<ThreadListItem>,
        val replyCounts: Map<String, Int>,
        val page: Int,
        val endReached: Boolean,
        val hasMore: Boolean
    )

    private val remoteFolderCache = HashMap<String, CachedRemoteFolder>()

    private var remoteFeedJob: kotlinx.coroutines.Job? = null
    private var remoteLoadMoreJob: kotlinx.coroutines.Job? = null
    private var remoteGeneration = 0L

    fun selectFolder(uuid: String?) {
        viewModelScope.launch { settingsDataStore.saveLastFeedFolder(uuid) }
        remoteFeedJob?.cancel()
        remoteLoadMoreJob?.cancel()
        remoteGeneration++
        remoteLoadingMore = false
        val cached = if (uuid != null) remoteFolderCache[uuid] else null
        if (cached != null) {
            remotePage = cached.page
            remoteEndReached = cached.endReached
            _uiState.update {
                it.copy(
                    selectedFolderId = uuid,
                    isLoading = false,
                    isLoadingMore = false,
                    hasMoreRemote = cached.hasMore,
                    remoteThreads = cached.threads,
                    remoteDisplayItems = cached.displayItems,
                    remoteReplyCounts = cached.replyCounts
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                selectedFolderId = uuid,
                isLoading = false,
                isLoadingMore = false,
                hasMoreRemote = false,
                // 任何切换都清空旧数据：留着会在新文件夹标题下展示上一个夹的帖子
                remoteThreads = emptyList(),
                remoteDisplayItems = emptyList(),
                remoteReplyCounts = emptyMap()
            )
        }
        if (uuid != null) {
            loadRemoteFeed(uuid)
        }
    }

    private fun loadRemoteFeed(uuid: String) {
        val generation = ++remoteGeneration
        remoteLoadMoreJob?.cancel()
        remoteLoadingMore = false
        remotePage = 1
        remoteEndReached = false
        remoteFeedJob?.cancel()
        remoteFeedJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.remoteDisplayItems.isEmpty(), isLoadingMore = false) }
            threadRepository.getFeed(uuid, 1).collect { response ->
                // 请求期间用户已切到别的文件夹 → 丢弃，防止旧数据覆盖新夹
                if (remoteGeneration != generation || _uiState.value.selectedFolderId != uuid) return@collect
                if (response is XdResponse.Success) {
                    val threads = withContext(Dispatchers.Default) {
                        newFeedPageItems(emptyList(), response.data) { it.idStr }
                    }
                    val knownCounts = threadRepository.getCloudFeedReplyCounts(uuid)
                    val items = buildRemoteItems(threads, knownCounts)
                    if (remoteGeneration != generation || _uiState.value.selectedFolderId != uuid) return@collect
                    val hasMore = threads.isNotEmpty()
                    remoteEndReached = !hasMore
                    remoteFolderCache[uuid] = CachedRemoteFolder(
                        threads = threads,
                        displayItems = items,
                        replyCounts = knownCounts,
                        page = 1,
                        endReached = remoteEndReached,
                        hasMore = hasMore
                    )
                    _uiState.update {
                        it.copy(
                            remoteThreads = threads,
                            remoteReplyCounts = knownCounts,
                            remoteDisplayItems = items,
                            isLoading = false,
                            hasMoreRemote = hasMore
                        )
                    }
                } else {
                    if (_uiState.value.remoteDisplayItems.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                remoteThreads = emptyList(),
                                remoteDisplayItems = emptyList(),
                                remoteReplyCounts = emptyMap(),
                                isLoading = false
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
            }
        }
    }

    private suspend fun buildRemoteItems(
        threads: List<Thread>,
        counts: Map<String, Int>
    ): List<ThreadListItem> = withContext(Dispatchers.Default) {
        val withCounts = threads.map { thread ->
            counts[thread.idStr]
                ?.takeIf { it >= 0 }
                ?.let { thread.copy(replyCount = it) }
                ?: thread
        }
        withCounts.toFilteredThreadListItems(
            blockedThreads = emptySet(),
            blockedUsers = emptySet(),
            blockedKeywords = emptyList()
        )
    }

    fun unsubscribeRemote(uuid: String, threadId: String) {
        viewModelScope.launch {
            threadRepository.delFeed(uuid, threadId).collect { response ->
                when (response) {
                    is XdResponse.Success -> {
                        remoteFolderCache[uuid]?.let { cached ->
                            remoteFolderCache[uuid] = cached.copy(
                                threads = cached.threads.filterNot { it.idStr == threadId },
                                displayItems = cached.displayItems.filterNot { it.idStr == threadId }
                            )
                        }
                        _uiState.update { state ->
                            if (state.selectedFolderId != uuid) state else state.copy(
                                remoteThreads = state.remoteThreads.filterNot { it.idStr == threadId },
                                remoteDisplayItems = state.remoteDisplayItems.filterNot { it.idStr == threadId }
                            )
                        }
                    }
                    is XdResponse.Error -> _errorEvents.tryEmit("取消订阅失败：${response.message}")
                }
            }
        }
    }

    fun loadMoreRemote() {
        val uuid = _uiState.value.selectedFolderId ?: return
        // 独立标志：加载更多不动 isLoading（那是首页全屏 spinner 的开关，动了会闪屏）
        if (remoteLoadingMore || remoteEndReached || _uiState.value.isLoading) return
        remoteLoadingMore = true
        _uiState.update { it.copy(isLoadingMore = true) }
        val page = remotePage + 1
        val generation = remoteGeneration
        remoteLoadMoreJob = viewModelScope.launch {
            try {
                threadRepository.getFeed(uuid, page).collect { response ->
                    if (remoteGeneration != generation || _uiState.value.selectedFolderId != uuid) return@collect
                    if (response is XdResponse.Success) {
                        val fresh = response.data
                        if (fresh.isEmpty()) {
                            remoteEndReached = true
                            _uiState.update { it.copy(hasMoreRemote = false) }
                            return@collect
                        }
                        val state = _uiState.value
                        val added = withContext(Dispatchers.Default) {
                            newFeedPageItems(state.remoteThreads, fresh) { it.idStr }
                        }
                        val items = buildRemoteItems(added, state.remoteReplyCounts)
                        if (remoteGeneration != generation || _uiState.value.selectedFolderId != uuid) return@collect
                        if (added.isEmpty()) remoteEndReached = true
                        remotePage = page
                        val nextThreads = state.remoteThreads + added
                        val nextItems = state.remoteDisplayItems + items.withReplyCounts(state.remoteReplyCounts)
                        val hasMore = !remoteEndReached
                        remoteFolderCache[uuid] = CachedRemoteFolder(
                            threads = nextThreads,
                            displayItems = nextItems,
                            replyCounts = state.remoteReplyCounts,
                            page = remotePage,
                            endReached = remoteEndReached,
                            hasMore = hasMore
                        )
                        _uiState.update {
                            it.copy(
                                remoteThreads = nextThreads,
                                remoteDisplayItems = nextItems,
                                hasMoreRemote = hasMore
                            )
                        }
                    } else {
                        // 失败回退页码，让用户下拉重试
                        _errorEvents.tryEmit("加载更多订阅失败：${(response as? XdResponse.Error)?.message ?: "网络错误"}")
                    }
                }
            } finally {
                if (remoteGeneration == generation) {
                    remoteLoadingMore = false
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            }
        }
    }

    fun unsubscribe(threadId: String) {
        viewModelScope.launch {
            threadRepository.removeBookmark(threadId)
        }
    }

    /** 删除单个离线串（同时取消本地收藏、清离线 JSON 与图片）。 */
    fun deleteOffline(threadId: String) {
        viewModelScope.launch {
            threadRepository.deleteOfflineThread(threadId)
        }
    }

    /** 重新拉取最新页（复用整串离线下载，已缓存页/图按 key 覆盖或跳过）。 */
    fun updateOffline(
        summary: com.mioo.dao.data.local.OfflineThreadSummary,
        onResult: (String) -> Unit
    ) {
        val id = summary.threadId
        if (id in _uiState.value.offlineUpdatingIds) return
        _uiState.update { it.copy(offlineUpdatingIds = it.offlineUpdatingIds + id) }
        viewModelScope.launch {
            try {
                threadRepository.downloadFullThread(id).collect { response ->
                    onResult(
                        when (response) {
                            is XdResponse.Success -> "离线缓存已更新"
                            is XdResponse.Error -> "更新失败: ${response.message}"
                        }
                    )
                }
            } finally {
                _uiState.update { it.copy(offlineUpdatingIds = it.offlineUpdatingIds - id) }
            }
        }
    }

    fun refreshRemote() {
        _uiState.value.selectedFolderId?.let { loadRemoteFeed(it) }
    }
}
