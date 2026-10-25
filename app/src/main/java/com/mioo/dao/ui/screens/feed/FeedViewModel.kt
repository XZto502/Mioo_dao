package com.mioo.dao.ui.screens.feed

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mioo.dao.data.local.BookmarkEntity
import com.mioo.dao.data.model.FeedFolder
import com.mioo.dao.data.model.Thread
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.data.repository.SettingsRepository
import com.mioo.dao.data.repository.ThreadRepository
import com.mioo.dao.ui.components.BookmarkListItem
import com.mioo.dao.ui.components.ThreadListItem
import com.mioo.dao.ui.components.toBookmarkListItems
import com.mioo.dao.ui.components.toFilteredThreadListItems
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 单次进入夹列表最多补抓多少串的回复数，保护服务器。 */
private const val REPLY_COUNT_ENRICH_LIMIT = 80

@Immutable
data class FeedUiState(
    val bookmarkedThreads: List<BookmarkEntity> = emptyList(),
    val localDisplayItems: List<BookmarkListItem> = emptyList(),
    val remoteThreads: List<Thread> = emptyList(),
    val remoteDisplayItems: List<ThreadListItem> = emptyList(),
    /** 云端订阅串的已知回复数（threadId → count）。feed 接口不带回复数，靠后台逐串补抓。 */
    val remoteReplyCounts: Map<String, Int> = emptyMap(),
    val feedFolders: List<FeedFolder> = emptyList(),
    val isLoading: Boolean = false,
    val selectedFolderId: String? = null
)

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val threadRepository: ThreadRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState

    /** 一次性错误提示事件（取消订阅失败 / 加载更多失败等），由 FeedScreen 弹 Toast */
    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private var remotePage = 1
    private var remoteLoadingMore = false
    private var remoteEndReached = false

    init {
        // Local bookmarks → prebuilt display items
        viewModelScope.launch {
            threadRepository.getBookmarks().collect { bookmarks ->
                val items = withContext(Dispatchers.Default) {
                    bookmarks.toBookmarkListItems()
                }
                _uiState.update {
                    it.copy(
                        bookmarkedThreads = bookmarks,
                        localDisplayItems = items
                    )
                }
            }
        }

        // Only folders — avoid full settings recompose noise
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.feedFolders }
                .distinctUntilChanged()
                .collect { folders ->
                    _uiState.update { it.copy(feedFolders = folders) }
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

    private var remoteFeedJob: kotlinx.coroutines.Job? = null
    private var replyCountJob: kotlinx.coroutines.Job? = null

    fun selectFolder(uuid: String?) {
        _uiState.update {
            it.copy(
                selectedFolderId = uuid,
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
        remotePage = 1
        remoteEndReached = false
        remoteFeedJob?.cancel()
        remoteFeedJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            threadRepository.getFeed(uuid, 1).collect { response ->
                // 请求期间用户已切到别的文件夹 → 丢弃，防止旧数据覆盖新夹
                if (_uiState.value.selectedFolderId != uuid) return@collect
                if (response is XdResponse.Success) {
                    val threads = response.data
                    val knownCounts = threadRepository.getCloudFeedReplyCounts(uuid)
                    _uiState.update {
                        it.copy(
                            remoteThreads = threads,
                            remoteReplyCounts = knownCounts,
                            remoteDisplayItems = buildRemoteItems(threads, knownCounts),
                            isLoading = false
                        )
                    }
                    enrichRemoteReplyCounts(uuid, threads)
                } else {
                    // 失败也清空：不能让上一个夹的数据顶替当前夹展示
                    _uiState.update {
                        it.copy(
                            remoteThreads = emptyList(),
                            remoteDisplayItems = emptyList(),
                            remoteReplyCounts = emptyMap(),
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    /** feed 接口不带回复数：对已知计数缺失的串逐个补抓第 1 页并缓存，250ms 限速。 */
    private fun enrichRemoteReplyCounts(uuid: String, threads: List<Thread>) {
        replyCountJob?.cancel()
        replyCountJob = viewModelScope.launch {
            var consecutiveFailures = 0
            for (thread in threads.take(REPLY_COUNT_ENRICH_LIMIT)) {
                if (_uiState.value.selectedFolderId != uuid) return@launch
                if ((_uiState.value.remoteReplyCounts[thread.idStr] ?: 0) > 0) continue
                val count = threadRepository.refreshCloudFeedReplyCount(thread.idStr)
                if (count != null) {
                    consecutiveFailures = 0
                    _uiState.update { state ->
                        val merged = state.remoteReplyCounts + (thread.idStr to count)
                        state.copy(
                            remoteReplyCounts = merged,
                            remoteDisplayItems = buildRemoteItems(state.remoteThreads, merged)
                        )
                    }
                } else {
                    consecutiveFailures++
                    if (consecutiveFailures >= 3) return@launch
                }
                delay(250)
            }
        }
    }

    private fun buildRemoteItems(
        threads: List<Thread>,
        counts: Map<String, Int>
    ): List<ThreadListItem> {
        val withCounts = threads.map { thread ->
            counts[thread.idStr]
                ?.takeIf { it > (thread.replyCount ?: 0) }
                ?.let { thread.copy(replyCount = it) }
                ?: thread
        }
        return withCounts.toFilteredThreadListItems(
            blockedThreads = emptySet(),
            blockedUsers = emptySet(),
            blockedKeywords = emptyList()
        )
    }

    fun unsubscribeRemote(uuid: String, threadId: String) {
        viewModelScope.launch {
            threadRepository.delFeed(uuid, threadId).collect { response ->
                when (response) {
                    is XdResponse.Success -> loadRemoteFeed(uuid)
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
        remotePage += 1
        viewModelScope.launch {
            try {
                threadRepository.getFeed(uuid, remotePage).collect { response ->
                    if (_uiState.value.selectedFolderId != uuid) return@collect
                    if (response is XdResponse.Success) {
                        val fresh = response.data
                        if (fresh.isEmpty()) {
                            remoteEndReached = true
                            return@collect
                        }
                        val merged = (_uiState.value.remoteThreads + fresh).distinctBy { it.idStr }
                        if (merged.size == _uiState.value.remoteThreads.size) remoteEndReached = true
                        _uiState.update {
                            it.copy(
                                remoteThreads = merged,
                                remoteDisplayItems = buildRemoteItems(merged, it.remoteReplyCounts)
                            )
                        }
                        enrichRemoteReplyCounts(uuid, merged)
                    } else {
                        // 失败回退页码，让用户下拉重试
                        remotePage -= 1
                        _errorEvents.tryEmit("加载更多订阅失败：${(response as? XdResponse.Error)?.message ?: "网络错误"}")
                    }
                }
            } finally {
                remoteLoadingMore = false
            }
        }
    }

    fun unsubscribe(threadId: String) {
        viewModelScope.launch {
            threadRepository.removeBookmark(threadId)
        }
    }

    fun refreshRemote() {
        _uiState.value.selectedFolderId?.let { loadRemoteFeed(it) }
    }
}
