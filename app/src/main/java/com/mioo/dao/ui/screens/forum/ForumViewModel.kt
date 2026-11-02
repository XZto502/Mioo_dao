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
import com.mioo.dao.data.repository.DraftPrefillBus
import com.mioo.dao.data.repository.ForumRepository
import com.mioo.dao.data.repository.SettingsRepository
import com.mioo.dao.data.repository.ThreadRepository
import com.mioo.dao.ui.components.ThreadListItem
import com.mioo.dao.ui.components.toFilteredThreadListItems
import com.mioo.dao.utils.KeywordMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
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
    val completedRefreshGeneration: Long = 0L,
    val completedRefreshForumId: String? = null,
    val isLastPage: Boolean = false,
    val errorMessage: String? = null,
    val emptyHint: String? = null
)

@HiltViewModel
class ForumViewModel @Inject constructor(
    private val threadRepository: ThreadRepository,
    private val forumRepository: ForumRepository,
    private val settingsDataStore: SettingsDataStore,
    private val settingsRepository: SettingsRepository,
    private val draftPrefillBus: DraftPrefillBus
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
    /** Pages skipped because every row was hidden by block rules. Caps auto-follow. */
    private var blockedPageSkips = 0
    private var refreshGeneration = 0L
    private var listGeneration = 0L

    companion object {
        private const val MAX_BLOCKED_PAGE_SKIPS = 6
    }

    init {
        // Cold start critical path only:
        // 1) one DataStore snapshot (board + blocks)
        // 2) timeline/showf (Room cache → network)
        // Everything else (live blocklist watch, drawer catalog, smart preload) is deferred.
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isRefreshing = true) }
            val startupGeneration = listGeneration
            val snap = settingsDataStore.getColdStartSnapshot()
            if (listGeneration != startupGeneration) return@launch
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
                _uiState.update { it.copy(displayItems = emptyList(), emptyHint = null) }
                return@launch
            }
            val matcher = keywordMatcher
            val items = withContext(Dispatchers.Default) {
                threads.toFilteredThreadListItems(blockedThreads, blockedUsers, matcher)
            }
            // 过滤计算是挂起点：期间 loadNextPage 可能已追加新页（threads 引用变化）。
            // 旧快照的 items 覆盖回去会吞掉整页，且 currentPage 已推进无法自愈 → 放弃本次结果。
            if (_uiState.value.threads !== threads) return@launch
            _uiState.update { it.copy(displayItems = items) }
            val state = _uiState.value
            if (items.isEmpty() && !state.isLastPage && !state.isLoading && !state.isRefreshing) {
                blockedPageSkips = 0
                loadNextPage(fromAutoSkip = true)
            } else if (items.isEmpty()) {
                _uiState.update { it.copy(emptyHint = "这些串都被屏蔽规则挡住了") }
            } else {
                _uiState.update { it.copy(emptyHint = null) }
            }
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
        blockedPageSkips = 0
        // Clear previous board content so only the center spinner shows while loading.
        _uiState.update {
            it.copy(
                currentForumName = name,
                threads = emptyList(),
                displayItems = emptyList(),
                isLoading = true,
                isRefreshing = false,
                isLastPage = false,
                errorMessage = null,
                emptyHint = null
            )
        }
        viewModelScope.launch {
            settingsDataStore.saveLastForum(id, name)
        }
        refresh()
    }

    fun loadNextPage(fromAutoSkip: Boolean = false) {
        val currentState = _uiState.value
        if (currentState.isLastPage) return
        // Do not page while a full refresh is in flight (avoids racing page counter / list).
        if (currentState.isLoading || currentState.isRefreshing) return
        if (fromAutoSkip && blockedPageSkips >= MAX_BLOCKED_PAGE_SKIPS) {
            _uiState.update {
                it.copy(isLoading = false, emptyHint = "连续多页都被屏蔽规则挡住了")
            }
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        val pageToLoad = currentPage
        val requestForumId = forumId
        val requestGeneration = ++listGeneration

        listJob?.cancel()
        listJob = viewModelScope.launch {
            val flow = if (requestForumId == "-1") {
                threadRepository.getTimeline("1", pageToLoad)
            } else {
                threadRepository.getThreads(requestForumId, pageToLoad)
            }

            var rawNonEmpty = false
            var addedVisible = false
            var failed = false
            try {
                var gotPage = false
                flow.settledForumPage().collect { response ->
                    // Drop stale responses if user switched boards mid-flight.
                    if (forumId != requestForumId || listGeneration != requestGeneration) return@collect
                    when (response) {
                        is XdResponse.Success -> {
                            val newThreads = response.data
                            // Append with SWR: replace any ids from this page, keep prior pages
                            val existing = _uiState.value.threads
                            val combinedList = appendForumPage(existing, newThreads) { it.id }
                            // Refilter the stable merged order atomically, including block-rule changes.
                            val displayItems = withContext(Dispatchers.Default) {
                                combinedList.toFilteredThreadListItems(
                                    blockedThreads, blockedUsers, keywordMatcher
                                )
                            }
                            if (forumId != requestForumId || listGeneration != requestGeneration) return@collect
                            val existingIds = existing.mapTo(HashSet()) { it.id }
                            val addedVisibleOnPage = displayItems.any { it.id !in existingIds }
                            // Advance only after the final page result has been accepted.
                            // Do NOT smart-preload every paged chunk — competes with fling + images.
                            if (!gotPage && newThreads.isNotEmpty()) {
                                gotPage = true
                                currentPage = pageToLoad + 1
                            }
                            if (newThreads.isNotEmpty()) rawNonEmpty = true
                            if (addedVisibleOnPage) addedVisible = true
                            _uiState.update { state ->
                                state.copy(
                                    threads = combinedList,
                                    displayItems = displayItems,
                                    isLoading = true,
                                    isLastPage = newThreads.isEmpty() && !gotPage,
                                    errorMessage = null,
                                    emptyHint = null
                                )
                            }
                        }
                        is XdResponse.Error -> {
                            failed = true
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "加载串列表失败: ${response.message}"
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                if (forumId == requestForumId && listGeneration == requestGeneration) {
                    val canChain = !failed && rawNonEmpty && !addedVisible &&
                        !_uiState.value.isLastPage &&
                        blockedPageSkips < MAX_BLOCKED_PAGE_SKIPS
                    if (canChain) {
                        blockedPageSkips++
                        _uiState.update { it.copy(isLoading = false, emptyHint = null) }
                        loadNextPage(fromAutoSkip = true)
                    } else {
                        if (addedVisible) blockedPageSkips = 0
                        val hint = when {
                            _uiState.value.displayItems.isNotEmpty() -> null
                            _uiState.value.threads.isNotEmpty() &&
                                blockedPageSkips >= MAX_BLOCKED_PAGE_SKIPS ->
                                "连续多页都被屏蔽规则挡住了"
                            _uiState.value.threads.isNotEmpty() -> "这些串都被屏蔽规则挡住了"
                            else -> null
                        }
                        _uiState.update { it.copy(isLoading = false, emptyHint = hint) }
                    }
                }
            }
        }
    }

    fun continuePastBlockedPages() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isLastPage) return
        blockedPageSkips = 0
        _uiState.update { it.copy(emptyHint = null) }
        loadNextPage()
    }

    fun refresh() {
        val requestForumId = forumId
        val refreshToken = ++refreshGeneration
        val requestGeneration = ++listGeneration
        blockedPageSkips = 0
        // Keep existing threads visible while refreshing so only the top pull indicator
        // shows (not a second full-screen center spinner).
        _uiState.update {
            it.copy(
                isRefreshing = true,
                isLastPage = false,
                errorMessage = null,
                emptyHint = null
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

            var handoffBlocked = false
            var refreshed = false
            try {
                var lastNonEmpty = false
                flow.settledForumPage().collect { response ->
                    if (forumId != requestForumId || listGeneration != requestGeneration) return@collect
                    when (response) {
                        is XdResponse.Success -> {
                            val freshThreads = response.data.distinctBy { it.id }
                            lastNonEmpty = freshThreads.isNotEmpty()
                            val displayItems = withContext(Dispatchers.Default) {
                                freshThreads.toFilteredThreadListItems(
                                    blockedThreads, blockedUsers, keywordMatcher
                                )
                            }
                            if (forumId != requestForumId || listGeneration != requestGeneration) return@collect
                            refreshed = true
                            currentPage = if (freshThreads.isEmpty()) 1 else 2
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
                                    errorMessage = "刷新失败: ${response.message}"
                                )
                            }
                        }
                    }
                }
                if (forumId == requestForumId && listGeneration == requestGeneration) {
                    // Next page to request after a full page-1 refresh.
                    val visible = _uiState.value.displayItems.isNotEmpty()
                    if (lastNonEmpty && visible) {
                        // Well after cold list + first fling
                        scheduleSmartPreload(_uiState.value.threads, delayMs = 3200L)
                    }
                    handoffBlocked = lastNonEmpty && !visible && !_uiState.value.isLastPage
                    if (lastNonEmpty && !visible && _uiState.value.isLastPage) {
                        _uiState.update { it.copy(emptyHint = "这些串都被屏蔽规则挡住了") }
                    }
                }
            } finally {
                if (forumId == requestForumId && listGeneration == requestGeneration) {
                    if (handoffBlocked) {
                        blockedPageSkips = 1
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                isLoading = false,
                                emptyHint = null,
                                completedRefreshGeneration = if (refreshed) refreshToken else it.completedRefreshGeneration,
                                completedRefreshForumId = requestForumId
                            )
                        }
                        loadNextPage(fromAutoSkip = true)
                    } else {
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                isLoading = false,
                                completedRefreshGeneration = if (refreshed) refreshToken else it.completedRefreshGeneration,
                                completedRefreshForumId = requestForumId
                            )
                        }
                    }
                }
            }
        }
    }

    fun createThread(
        title: String,
        author: String,
        content: String,
        imageFile: java.io.File? = null,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                threadRepository.doPostThread(
                    fid = forumId,
                    title = if (title.isBlank()) null else title,
                    name = if (author.isBlank()) null else author,
                    email = null,
                    content = content,
                    imageFile = imageFile
                ).collect { response ->
                    when (response) {
                        is XdResponse.Success -> {
                            if (response.data == "已提交") {
                                onResult(false, "提交结果暂不确定，正文和图片已保留，请先查看是否已经发出")
                            } else {
                                onResult(true, "发串成功")
                                refresh()
                            }
                        }
                        is XdResponse.Error -> {
                            onResult(false, response.message.ifBlank { "发串失败" })
                        }
                    }
                }
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "发串失败")
            }
        }
    }

    // --- 发言草稿箱：发串草稿 ---

    val postDrafts = threadRepository.getDrafts()
        .map { drafts -> drafts.filter { it.type == com.mioo.dao.data.local.DraftEntity.TYPE_POST } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 草稿箱里待恢复的发串草稿（跨屏总线，单例持有）。 */
    val postDraftPrefill: StateFlow<com.mioo.dao.data.local.DraftEntity?>
        get() = draftPrefillBus.pending

    fun consumePostDraftPrefill() {
        draftPrefillBus.consume()
    }

    fun savePostDraft(title: String, author: String, content: String, onDone: (Boolean) -> Unit) {
        val boardId = forumId
        val boardName = _uiState.value.currentForumName
        viewModelScope.launch {
            try {
                threadRepository.savePostDraft(boardId, boardName, author, title, content)
                onDone(true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onDone(false)
            }
        }
    }

    fun deleteDraft(id: Long) {
        viewModelScope.launch {
            threadRepository.deleteDraft(id)
        }
    }

    /** 发送成功后清掉本板块的发串草稿（含手动存的与草稿箱预填的）。 */
    fun deletePostDraftsForCurrentBoard() {
        viewModelScope.launch {
            threadRepository.deletePostDrafts(forumId)
        }
    }
}
