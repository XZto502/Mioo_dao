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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Immutable
data class FeedUiState(
    val bookmarkedThreads: List<BookmarkEntity> = emptyList(),
    val localDisplayItems: List<BookmarkListItem> = emptyList(),
    val remoteThreads: List<Thread> = emptyList(),
    val remoteDisplayItems: List<ThreadListItem> = emptyList(),
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

        // Refresh known reply counts for badge (+N 新)
        viewModelScope.launch {
            threadRepository.refreshBookmarkReplyCounts(limit = 25)
        }
    }

    fun refreshReplyBadges() {
        viewModelScope.launch {
            threadRepository.refreshBookmarkReplyCounts(limit = 25)
        }
    }

    fun selectFolder(uuid: String?) {
        _uiState.update {
            it.copy(
                selectedFolderId = uuid,
                remoteThreads = if (uuid == null) emptyList() else it.remoteThreads,
                remoteDisplayItems = if (uuid == null) emptyList() else it.remoteDisplayItems
            )
        }
        if (uuid != null) {
            loadRemoteFeed(uuid)
        }
    }

    private fun loadRemoteFeed(uuid: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            threadRepository.getFeed(uuid, 1).collect { response ->
                if (response is XdResponse.Success) {
                    val threads = response.data
                    val items = withContext(Dispatchers.Default) {
                        threads.toFilteredThreadListItems(
                            blockedThreads = emptySet(),
                            blockedUsers = emptySet(),
                            blockedKeywords = emptyList()
                        )
                    }
                    _uiState.update {
                        it.copy(
                            remoteThreads = threads,
                            remoteDisplayItems = items,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun unsubscribeRemote(uuid: String, threadId: String) {
        viewModelScope.launch {
            threadRepository.delFeed(uuid, threadId).collect {
                loadRemoteFeed(uuid)
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
