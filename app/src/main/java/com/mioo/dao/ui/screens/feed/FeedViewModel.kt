package com.mioo.dao.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mioo.dao.data.local.BookmarkEntity
import com.mioo.dao.data.model.Thread
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.data.repository.SettingsRepository
import com.mioo.dao.data.repository.ThreadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedUiState(
    val bookmarkedThreads: List<BookmarkEntity> = emptyList(),
    val remoteThreads: List<Thread> = emptyList(),
    val isLoading: Boolean = false,
    val selectedFolderId: String? = null
)

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val threadRepository: ThreadRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settingsFlow = settingsRepository.settings

    private val _selectedFolderId = MutableStateFlow<String?>(null)
    private val _remoteThreads = MutableStateFlow<List<Thread>>(emptyList())
    private val _isLoadingRemote = MutableStateFlow(false)

    val uiState: StateFlow<FeedUiState> = combine(
        threadRepository.getBookmarks(),
        _selectedFolderId,
        _remoteThreads,
        _isLoadingRemote
    ) { localBookmarks, folderId, remoteList, isRemoteLoading ->
        FeedUiState(
            bookmarkedThreads = localBookmarks,
            remoteThreads = remoteList,
            selectedFolderId = folderId,
            isLoading = if (folderId == null) false else isRemoteLoading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FeedUiState(isLoading = true)
    )

    fun selectFolder(uuid: String?) {
        _selectedFolderId.value = uuid
        if (uuid != null) {
            loadRemoteFeed(uuid)
        }
    }

    private fun loadRemoteFeed(uuid: String) {
        viewModelScope.launch {
            _isLoadingRemote.value = true
            threadRepository.getFeed(uuid, 1).collect { response ->
                if (response is XdResponse.Success) {
                    _remoteThreads.value = response.data
                }
                _isLoadingRemote.value = false
            }
        }
    }

    fun unsubscribeRemote(uuid: String, threadId: String) {
        viewModelScope.launch {
            threadRepository.delFeed(uuid, threadId).collect {
                // reload
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
        _selectedFolderId.value?.let { uuid ->
            loadRemoteFeed(uuid)
        }
    }
}
