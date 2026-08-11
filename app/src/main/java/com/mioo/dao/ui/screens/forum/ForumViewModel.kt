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
    /** Cancel previous smart-preload when board/refresh changes so stale GETs don't hog bandwidth. */
    private var preloadJob: Job? = null
    private var blockedThreads: Set<String> = emptySet()
    private var blockedUsers: Set<String> = emptySet()
    private var keywordMatcher: KeywordMatcher = KeywordMatcher.EMPTY

    init {
        // Cold start critical path only:
        // 1) one DataStore snapshot (board + blocks)
        // 2) timeline/showf (Room cache → network)
        // Everything else (live blocklist watch, drawer catalog, smart preload) is deferred.
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isRefreshing = true) }
            val snap = settingsDataStore.getColdStartSnapshot()
            blockedThreads = snap.blockedThreads.toHashSet()
            blockedUsers = snap.blockedUsers.toHashSet()
            keywordMatcher = KeywordMatcher.build(snap.blockedKeywords)
            forumId = snap.lastForumId
            _uiState.update { it.copy(currentForumName = snap.lastForumName) }
            // First network/disk slot = timeline/showf (SWR cache paints immediately)
            refresh()
        }

        // Live blocklist updates after first paint — must not race the first list fetch.
        viewModelScope.launch {
            delay(900)
            settingsRepository.settings
                .map { Triple(it.blockedThreads, it.blockedUsers, it.blockedKeywords) }
                .distinctUntilChanged()
                .collect { (threads, users, keywords) ->
                    blockedThreads = threads.toHashSet()
                    blockedUsers = users.toHashSet()
                    keywordMatcher = KeywordMatcher.build(keywords)
                    if (_uiState.value.threads.isNotEmpty()) {
                        rebuildDisplayItems()
                    }
                }
        }

        // Board drawer catalog after list fling window — never share bandwidth with cold list.
        viewModelScope.launch {
            delay(2000)
            if (_uiState.value.forumGroups.isEmpty()) {
                loadForumGroupsSync()
            }
        }
    }

    private suspend fun loadForumGroupsSync() {
        forumRepository.getForumList().collect { response ->
            if (response is XdResponse.Success) {
                _uiState.update { it.copy(forumGroups = response.data) }
            }
        }
    }

    /**
     * Defer detail-page network preload until list paint settles.
     * Only the first few rows of the *current page-1 snapshot* — never every paged chunk
     * (that flooded the network mid-scroll in emulator traces).
     */
    private fun scheduleSmartPreload(threads: List<Thread>, delayMs: Long = 2200L) {
        preloadJob?.cancel()
        if (threads.isEmpty()) return
        preloadJob = viewModelScope.launch {
            delay(delayMs)
            // Cap here too so settings preloadCount cannot stampede
            threadRepository.smartPreloadThreads(threads.take(6))
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
        preloadJob?.cancel()
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
                            // Filter only the new page, then merge — avoid re-filtering 1..N on every emit
                            val newDisplay = withContext(Dispatchers.Default) {
                                newThreads.toFilteredThreadListItems(
                                    blockedThreads, blockedUsers, keywordMatcher
                                )
                            }
                            val existingDisplay = _uiState.value.displayItems
                            val displayItems = if (existingDisplay.isEmpty()) {
                                newDisplay
                            } else {
                                val keep = existingDisplay.filter { it.id !in newIds }
                                keep + newDisplay
                            }
                            // Only advance page once per load (SWR may emit cache + network).
                            // Do NOT smart-preload every paged chunk — competes with fling + images.
                            if (!gotPage && newThreads.isNotEmpty()) {
                                gotPage = true
                                currentPage = pageToLoad + 1
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

        // Cancel in-flight page/refresh + detail preload so SWR double-emit cannot race.
        listJob?.cancel()
        preloadJob?.cancel()
        listJob = viewModelScope.launch {
            val flow = if (requestForumId == "-1") {
                threadRepository.getTimeline("1", 1)
            } else {
                threadRepository.getThreads(requestForumId, 1)
            }

            try {
                var lastNonEmpty = false
                var emissionCount = 0
                var lastIds: LongArray? = null
                flow.collect { response ->
                    if (forumId != requestForumId) return@collect
                    when (response) {
                        is XdResponse.Success -> {
                            val freshThreads = response.data
                            lastNonEmpty = freshThreads.isNotEmpty()
                            val ids = LongArray(freshThreads.size) { freshThreads[it].id }
                            val displayItems = withContext(Dispatchers.Default) {
                                freshThreads.toFilteredThreadListItems(
                                    blockedThreads, blockedUsers, keywordMatcher
                                )
                            }
                            // Skip identical SWR second paint, but still repaint when reply
                            // badges change (same id order, different replyCount).
                            val prev = lastIds
                            if (prev != null && prev.contentEquals(ids)) {
                                val prevItems = _uiState.value.displayItems
                                val replyChanged = prevItems.size == displayItems.size &&
                                    prevItems.indices.any {
                                        prevItems[it].replyCount != displayItems[it].replyCount
                                    }
                                if (!replyChanged) {
                                    emissionCount++
                                    return@collect
                                }
                            }
                            lastIds = ids

                            // Network after cache: yield so first cache paint + splash dismiss land first.
                            // Slightly longer than 1 frame so cold-start measure isn't double-stomped.
                            if (emissionCount > 0) {
                                delay(64)
                                if (forumId != requestForumId) return@collect
                            }
                            emissionCount++
                            // Update list; keep isRefreshing until flow ends
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
                        // Well after cold list + first fling
                        scheduleSmartPreload(_uiState.value.threads, delayMs = 3200L)
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
