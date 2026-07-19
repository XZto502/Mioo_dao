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
import com.mioo.dao.utils.KeywordMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var listJob: Job? = null
    private var blockedThreads: Set<String> = emptySet()
    private var blockedUsers: Set<String> = emptySet()
    private var keywordMatcher: KeywordMatcher = KeywordMatcher.EMPTY

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .map { Triple(it.blockedThreads, it.blockedUsers, it.blockedKeywords) }
                .distinctUntilChanged()
                .collect { (threads, users, keywords) ->
                    blockedThreads = threads.toHashSet()
                    blockedUsers = users.toHashSet()
                    keywordMatcher = KeywordMatcher.build(keywords)
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
            val matcher = keywordMatcher
            val items = withContext(Dispatchers.Default) {
                threads.toFilteredThreadListItems(blockedThreads, blockedUsers, matcher)
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
        // Clear previous board content so only the center spinner shows while loading.
        _uiState.update {
            it.copy(
                currentForumName = name,
                threads = emptyList(),
                displayItems = emptyList(),
                isLoading = true,
                isRefreshing = false,
                isLastPage = false,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            settingsDataStore.saveLastForum(id, name)
        }
        refresh()
    }

    fun loadNextPage() {
        val currentState = _uiState.value
        // Do not page while a full refresh is in flight (avoids racing page counter / list).
        if (currentState.isLoading || currentState.isRefreshing || currentState.isLastPage) return

        _uiState.update { it.copy(isLoading = true) }
        val pageToLoad = currentPage
        val requestForumId = forumId

        listJob?.cancel()
        listJob = viewModelScope.launch {
            val flow = if (requestForumId == "-1") {
                threadRepository.getTimeline("1", pageToLoad)
            } else {
                threadRepository.getThreads(requestForumId, pageToLoad)
            }

            try {
                var gotPage = false
                flow.collect { response ->
                    // Drop stale responses if user switched boards mid-flight.
                    if (forumId != requestForumId) return@collect
                    when (response) {
                        is XdResponse.Success -> {
                            val newThreads = response.data
                            // Append with SWR: replace any ids from this page, keep prior pages
                            val existing = _uiState.value.threads
                            val newIds = newThreads.mapTo(HashSet(newThreads.size)) { it.id }
                            val combinedList = existing.filter { it.id !in newIds } + newThreads
                            val displayItems = withContext(Dispatchers.Default) {
                                combinedList.toFilteredThreadListItems(
                                    blockedThreads, blockedUsers, keywordMatcher
                                )
                            }
                            // Only advance page once per load (SWR may emit cache + network).
                            if (!gotPage && newThreads.isNotEmpty()) {
                                gotPage = true
                                currentPage = pageToLoad + 1
                                scheduleSmartPreload(newThreads, delayMs = 800L)
                            }
                            _uiState.update { state ->
                                state.copy(
                                    threads = combinedList,
                                    displayItems = displayItems,
                                    isLoading = false,
                                    isLastPage = newThreads.isEmpty() && !gotPage,
                                    errorMessage = null
                                )
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
            } finally {
                if (forumId == requestForumId) {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun refresh() {
        val requestForumId = forumId
        currentPage = 1
        // Keep existing threads visible while refreshing so only the top pull indicator
        // shows (not a second full-screen center spinner).
        _uiState.update {
            it.copy(
                isRefreshing = true,
                isLastPage = false,
                errorMessage = null
            )
        }

        // Cancel in-flight page/refresh so SWR double-emit cannot race page counter.
        listJob?.cancel()
        listJob = viewModelScope.launch {
            val flow = if (requestForumId == "-1") {
                threadRepository.getTimeline("1", 1)
            } else {
                threadRepository.getThreads(requestForumId, 1)
            }

            try {
                var lastNonEmpty = false
                flow.collect { response ->
                    if (forumId != requestForumId) return@collect
                    when (response) {
                        is XdResponse.Success -> {
                            val freshThreads = response.data
                            lastNonEmpty = freshThreads.isNotEmpty()
                            val displayItems = withContext(Dispatchers.Default) {
                                freshThreads.toFilteredThreadListItems(
                                    blockedThreads, blockedUsers, keywordMatcher
                                )
                            }
                            // Update list immediately (cache then network) but keep
                            // isRefreshing=true until the whole flow completes — avoids
                            // ending the pull indicator on the first cache hit.
                            _uiState.update { state ->
                                state.copy(
                                    threads = freshThreads,
                                    displayItems = displayItems,
                                    isLoading = false,
                                    isLastPage = freshThreads.isEmpty(),
                                    errorMessage = null
                                )
                            }
                        }
                        is XdResponse.Error -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "Failed to refresh: ${response.message}"
                                )
                            }
                        }
                    }
                }
                if (forumId == requestForumId) {
                    // Next page to request after a full page-1 refresh.
                    currentPage = if (lastNonEmpty) 2 else 1
                    if (lastNonEmpty) {
                        scheduleSmartPreload(_uiState.value.threads, delayMs = 1600L)
                    }
                }
            } finally {
                if (forumId == requestForumId) {
                    _uiState.update {
                        it.copy(isRefreshing = false, isLoading = false)
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
