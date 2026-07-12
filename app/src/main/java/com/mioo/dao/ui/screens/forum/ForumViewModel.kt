package com.mioo.dao.ui.screens.forum

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mioo.dao.data.local.SettingsDataStore
import com.mioo.dao.data.model.ForumGroup
import com.mioo.dao.data.model.Thread
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.data.repository.ForumRepository
import com.mioo.dao.data.repository.SettingsRepository
import com.mioo.dao.data.repository.ThreadRepository
import com.mioo.dao.ui.components.ThreadListItem
import com.mioo.dao.ui.components.toFilteredThreadListItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Immutable
data class ForumUiState(
    val threads: List<Thread> = emptyList(),
    val displayItems: List<ThreadListItem> = emptyList(),
    val forumGroups: List<ForumGroup> = emptyList(),
    val currentForumName: String = "时间线",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLastPage: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ForumViewModel @Inject constructor(
    private val threadRepository: ThreadRepository,
    private val forumRepository: ForumRepository,
    private val settingsDataStore: SettingsDataStore,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    var forumId: String by mutableStateOf("-1")
        private set

    private val _uiState = MutableStateFlow(ForumUiState())
    val uiState: StateFlow<ForumUiState> = _uiState.asStateFlow()

    private var currentPage = 1
    private var blockedThreads: Set<String> = emptySet()
    private var blockedUsers: Set<String> = emptySet()
    private var blockedKeywords: List<String> = emptyList()

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .map { Triple(it.blockedThreads, it.blockedUsers, it.blockedKeywords) }
                .distinctUntilChanged()
                .collect { (threads, users, keywords) ->
                    blockedThreads = threads.toHashSet()
                    blockedUsers = users.toHashSet()
                    blockedKeywords = keywords
                    rebuildDisplayItems()
                }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val (savedId, savedName) = settingsDataStore.getLastForum()
            forumId = savedId
            _uiState.update { it.copy(currentForumName = savedName) }
            // Board list in parallel — do not block first thread page
            launch { loadForumGroupsSync() }
            refresh()
        }
    }

    private suspend fun loadForumGroupsSync() {
        forumRepository.getForumList().collect { response ->
            if (response is XdResponse.Success) {
                _uiState.update { it.copy(forumGroups = response.data) }
            }
        }
    }

    /** Defer network preload so cold-start first fling isn't bandwidth/decoder contended. */
    private fun scheduleSmartPreload(threads: List<Thread>, delayMs: Long = 1600L) {
        viewModelScope.launch {
            delay(delayMs)
            threadRepository.smartPreloadThreads(threads)
        }
    }

    private fun rebuildDisplayItems() {
        viewModelScope.launch {
            val threads = _uiState.value.threads
            if (threads.isEmpty()) {
                _uiState.update { it.copy(displayItems = emptyList()) }
                return@launch
            }
            val items = withContext(Dispatchers.Default) {
                threads.toFilteredThreadListItems(blockedThreads, blockedUsers, blockedKeywords)
            }
            _uiState.update { it.copy(displayItems = items) }
        }
    }

    fun loadForumGroups() {
        viewModelScope.launch {
            forumRepository.getForumList().collect { response ->
                if (response is XdResponse.Success) {
                    _uiState.update { it.copy(forumGroups = response.data) }
                }
            }
        }
    }

    fun selectForum(id: String, name: String) {
        if (forumId == id) return
        forumId = id
        _uiState.update { it.copy(currentForumName = name) }
        viewModelScope.launch {
            settingsDataStore.saveLastForum(id, name)
        }
        refresh()
    }

    fun loadNextPage() {
        val currentState = _uiState.value
        if (currentState.isLoading || currentState.isLastPage) return

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val flow = if (forumId == "-1") {
                threadRepository.getTimeline("1", currentPage)
            } else {
                threadRepository.getThreads(forumId, currentPage)
            }

            flow.collect { response ->
                when (response) {
                    is XdResponse.Success -> {
                        val newThreads = response.data
                        // Append with SWR: replace any ids from this page, keep prior pages
                        val existing = _uiState.value.threads
                        val newIds = newThreads.mapTo(HashSet(newThreads.size)) { it.id }
                        val combinedList = existing.filter { it.id !in newIds } + newThreads
                        val displayItems = withContext(Dispatchers.Default) {
                            combinedList.toFilteredThreadListItems(
                                blockedThreads, blockedUsers, blockedKeywords
                            )
                        }
                        _uiState.update { state ->
                            state.copy(
                                threads = combinedList,
                                displayItems = displayItems,
                                isLoading = false,
                                isLastPage = newThreads.isEmpty(),
                                errorMessage = null
                            )
                        }
                        if (newThreads.isNotEmpty()) {
                            currentPage++
                            scheduleSmartPreload(newThreads, delayMs = 800L)
                        }
                    }
                    is XdResponse.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Failed to load threads: ${response.message}"
                            )
                        }
                    }
                }
            }
        }
    }

    fun refresh() {
        currentPage = 1
        _uiState.update {
            it.copy(
                isRefreshing = true,
                isLastPage = false,
                threads = emptyList(),
                displayItems = emptyList()
            )
        }

        viewModelScope.launch {
            val flow = if (forumId == "-1") {
                threadRepository.getTimeline("1", currentPage)
            } else {
                threadRepository.getThreads(forumId, currentPage)
            }

            flow.collect { response ->
                when (response) {
                    is XdResponse.Success -> {
                        val freshThreads = response.data
                        val displayItems = withContext(Dispatchers.Default) {
                            freshThreads.toFilteredThreadListItems(
                                blockedThreads, blockedUsers, blockedKeywords
                            )
                        }
                        _uiState.update { state ->
                            state.copy(
                                threads = freshThreads,
                                displayItems = displayItems,
                                isRefreshing = false,
                                isLoading = false,
                                isLastPage = freshThreads.isEmpty(),
                                errorMessage = null
                            )
                        }
                        if (freshThreads.isNotEmpty()) {
                            currentPage++
                            scheduleSmartPreload(freshThreads, delayMs = 1600L)
                        }
                    }
                    is XdResponse.Error -> {
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                isLoading = false,
                                errorMessage = "Failed to refresh: ${response.message}"
                            )
                        }
                    }
                }
            }
        }
    }

    fun createThread(title: String, author: String, content: String, imageFile: java.io.File? = null) {
        viewModelScope.launch {
            threadRepository.doPostThread(
                fid = forumId,
                title = if (title.isBlank()) null else title,
                name = if (author.isBlank()) null else author,
                email = null,
                content = content,
                imageFile = imageFile
            ).collect { response ->
                if (response is XdResponse.Success) {
                    refresh()
                }
            }
        }
    }
}
