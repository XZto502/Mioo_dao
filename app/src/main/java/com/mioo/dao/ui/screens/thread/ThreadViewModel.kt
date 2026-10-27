package com.mioo.dao.ui.screens.thread

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mioo.dao.data.model.FeedFolder
import com.mioo.dao.data.model.Reply
import com.mioo.dao.data.model.Thread
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.data.repository.SettingsRepository
import com.mioo.dao.data.repository.ThreadRepository
import com.mioo.dao.data.model.effectiveTitle
import com.mioo.dao.ui.components.IslandImage
import com.mioo.dao.ui.components.PostData
import com.mioo.dao.ui.components.ReplyDisplayItem
import com.mioo.dao.ui.components.decodeHtmlEntities
import com.mioo.dao.ui.components.toPostData
import com.mioo.dao.utils.DiceRulePreset
import com.mioo.dao.utils.KeywordMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * List + filter state. Collected by the LazyColumn body — must not include
 * bookmark / download / ref-popup fields (those live in [ThreadChromeUiState] /
 * [ThreadRefPopupUiState]).
 */
@Immutable
data class ThreadListUiState(
    val thread: Thread? = null,
    val mainPostData: PostData? = null,
    val displayItems: List<ReplyDisplayItem> = emptyList(),
    val showPoOnly: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLastPage: Boolean = false,
    val currentPage: Int = 1,
    val startPage: Int = 1,
    /** After auto-resume page load, UI scrolls to this index once then clears it. */
    val pendingScrollIndex: Int? = null,
    /**
     * After prepending an earlier page, UI should keep the same content under the finger
     * by adding this many list indices (reply rows only; not the main post header).
     */
    val prependAnchorCount: Int? = null,
    val showImagesOnly: Boolean = false,
    val searchQuery: String = "",
    val filterHint: String? = null
)

/** Top-bar chrome only — bookmark / folders / offline download. */
@Immutable
data class ThreadChromeUiState(
    val isSubscribed: Boolean = false,
    /** Thread lives in a 云端收藏夹 (cloud feed cache) — icon also lights up for it. */
    val isCloudSubscribed: Boolean = false,
    val feedFolders: List<FeedFolder> = emptyList(),
    val isDownloading: Boolean = false,
    /** 本串已离线（offline_threads 有标记）——三点菜单显示「更新离线缓存 (已保存)」。 */
    val isOfflineSaved: Boolean = false,
    /** 本串已缓存所属的云端收藏夹 uuid 集合（收藏菜单按夹显示"已收藏进该夹"）。 */
    val cloudFolderUuids: Set<String> = emptySet()
)

/** Nested >>No.xxxx popup — isolated so open/close never invalidates the list. */
@Immutable
data class ThreadRefPopupUiState(
    val refPostId: String? = null,
    val refPostData: PostData? = null,
    val isRefLoading: Boolean = false,
    val refError: String? = null,
    val canGoBack: Boolean = false
)

/** Reply composer state — isolated so typing does not recompose the LazyColumn. */
@Immutable
data class ReplyComposerState(
    val replyText: String = "",
    val quotedPostNo: String? = null,
    val isReplying: Boolean = false,
    val replyError: String? = null
)

@HiltViewModel
class ThreadViewModel @Inject constructor(
    private val threadRepository: ThreadRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val threadId: String = savedStateHandle.get<String>("threadId")
        ?: throw IllegalArgumentException("threadId is required")

    private val _listState = MutableStateFlow(ThreadListUiState(isLoading = true))
    val listUiState: StateFlow<ThreadListUiState> = _listState.asStateFlow()

    private val _chromeState = MutableStateFlow(ThreadChromeUiState())
    val chromeUiState: StateFlow<ThreadChromeUiState> = _chromeState.asStateFlow()

    private val _refPopupState = MutableStateFlow(ThreadRefPopupUiState())
    val refPopupUiState: StateFlow<ThreadRefPopupUiState> = _refPopupState.asStateFlow()

    private val _composerState = MutableStateFlow(ReplyComposerState())
    val composerState: StateFlow<ReplyComposerState> = _composerState.asStateFlow()

    /** 本串独立选中的饼干下标；null = 未单独选过，跟随全局。绝不写回全局。 */
    private val _threadCookieIndex = MutableStateFlow<Int?>(null)
    val threadCookieIndex: StateFlow<Int?> = _threadCookieIndex.asStateFlow()

    /** 本串的骰点高亮规则（默认仅高亮；按串记忆，点击骰子胶囊可切换）。 */
    private val _dicePreset = MutableStateFlow(DiceRulePreset.HIGHLIGHT_ONLY)
    val dicePreset: StateFlow<DiceRulePreset> = _dicePreset.asStateFlow()

    fun selectThreadCookie(index: Int) {
        _threadCookieIndex.value = index
    }

    /** 切换本串骰点规则并持久化（整串胶囊颜色实时刷新）。 */
    fun setDicePreset(preset: DiceRulePreset) {
        _dicePreset.value = preset
        viewModelScope.launch {
            settingsRepository.saveThreadDicePreset(threadId, preset)
        }
    }

    /**
     * Per-key observable quote map. Compose only invalidates items that read a given id,
     * so filling quote A does not recompose the card that only needs quote B.
     */
    val quoteCache: SnapshotStateMap<String, Reply> = mutableStateMapOf()

    companion object {
        private const val PAGE_SIZE = 19
        private const val DRAFT_DEBOUNCE_MS = 400L
        /** Keep quote network low so open-thread first paint keeps bandwidth/CPU. */
        private const val MAX_PARALLEL_QUOTE_FETCHES = 3
        /** Cap network quote resolves per page load (local/seeded quotes unlimited). */
        private const val MAX_NETWORK_QUOTES_PER_PAGE = 10
        private val QUOTE_ID_PATTERN: Pattern = Pattern.compile(">>(?:No\\.)?(\\d+)")
        private const val MAX_FILTERED_SKIPS = 4
        private const val REPORT_BOARD_FID = "18" // 值班室
    }

    private var currentPage = 1
    private var isLastPage = false
    /** Extra pages requested because a raw page added no visible replies. */
    private var filteredSkips = 0
    private var draftSaveJob: Job? = null
    private var rebuildJob: Job? = null
    private var progressChecked = false
    /** Last replyCount written via markBookmarkRead — skip SWR double Room updates. */
    private var lastMarkedReadCount: Int = -1
    /** Bumps on every new load so stale SWR/network emissions cannot overwrite a newer page. */
    private var loadGeneration: Int = 0
    /**
     * Page-local list index for continue-reading / jump.
     * Seeded once into [ThreadListUiState.pendingScrollIndex] so LazyListState can open there
     * without painting at top first. Not re-applied on SWR network (stable item keys keep scroll).
     */
    private var stickyResumeIndex: Int? = null
    /** Whether [stickyResumeIndex] was already written into list state for this load. */
    private var resumeScrollSeeded: Boolean = false
    /** After prepending, wait until user leaves the top edge before auto-loading earlier pages again. */
    private var suppressPreviousLoad: Boolean = false

    /** Unfiltered replies as returned by the API (after page merge). */
    private var rawPosts: List<Reply> = emptyList()
    private var blockedUsers: Set<String> = emptySet()
    /** Prebuilt multi-pattern matcher for block keywords. */
    private var keywordMatcher: KeywordMatcher = KeywordMatcher.EMPTY

    /** Small content→quoteIds cache so seed/fetch/filter don't re-regex the same HTML. */
    private val quoteIdExtractCache =
        object : android.util.LruCache<String, List<String>>(256) {}

    val totalPages: Int
        get() {
            val replyCount = _listState.value.thread?.replyCount ?: 0
            if (replyCount <= 0) return 1
            return (replyCount + PAGE_SIZE - 1) / PAGE_SIZE
        }

    init {
        viewModelScope.launch {
            // 已收藏 = 本地收藏 或 云端收藏夹成员（云端状态来自 feed 列表缓存）
            kotlinx.coroutines.flow.combine(
                threadRepository.isBookmarked(threadId),
                threadRepository.isInCloudFeed(threadId)
            ) { local, cloud -> local to cloud }
                .collect { (local, cloud) ->
                    _chromeState.update {
                        it.copy(isSubscribed = local, isCloudSubscribed = cloud)
                    }
                }
        }

        viewModelScope.launch {
            // 本串离线状态：下载完成/删除离线后三点菜单文案实时翻转
            threadRepository.isThreadDownloaded(threadId).collect { downloaded ->
                _chromeState.update { it.copy(isOfflineSaved = downloaded) }
            }
        }

        viewModelScope.launch {
            // 本串已缓存所属的云端收藏夹：收藏菜单按夹显示"已收藏进该夹"态
            threadRepository.observeCloudFolderUuids(threadId).collect { uuids ->
                _chromeState.update { it.copy(cloudFolderUuids = uuids) }
            }
        }

        viewModelScope.launch {
            settingsRepository.settings
                .map { it.feedFolders }
                .distinctUntilChanged()
                .collect { folders ->
                    _chromeState.update { it.copy(feedFolders = folders) }
                }
        }

        viewModelScope.launch {
            settingsRepository.settings
                .map { it.blockedUsers to it.blockedKeywords }
                .distinctUntilChanged()
                .collect { (users, keywords) ->
                    blockedUsers = users.toHashSet()
                    keywordMatcher = KeywordMatcher.build(keywords)
                    scheduleRebuildDisplayItems()
                }
        }

        // 草稿箱开关关闭时跳过恢复，避免关闭后旧草稿仍然回填输入框
        if (settingsRepository.settings.value.draftBoxEnabled) {
            viewModelScope.launch {
                val draft = threadRepository.getReplyDraftContent(threadId)
                if (draft.isNotEmpty()) {
                    _composerState.update { it.copy(replyText = draft) }
                }
            }
        }

        // 本串独立饼干选择：进串恢复上次回复所用饼干（仅本串状态，不改全局选中）
        viewModelScope.launch {
            val remembered = settingsRepository.getThreadCookie(threadId) ?: return@launch
            val idx = settingsRepository.settings.value.cookiesList.indexOf(remembered)
            if (idx >= 0) _threadCookieIndex.value = idx
        }

        // 本串骰点规则：按串记忆，未设置过的串回退"仅高亮"
        viewModelScope.launch {
            _dicePreset.value = settingsRepository.getThreadDicePreset(threadId)
                ?: DiceRulePreset.HIGHLIGHT_ONLY
        }

        loadThreadDetails()
    }

    private fun loadThreadDetails() {
        isLastPage = false
        rawPosts = emptyList()
        quoteCache.clear()
        quoteIdExtractCache.evictAll()
        progressChecked = true
        stickyResumeIndex = null
        resumeScrollSeeded = false
        lastMarkedReadCount = -1

        // Stay on spinner until the target page is ready (no empty list / top flash)
        _listState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                thread = null,
                displayItems = emptyList(),
                mainPostData = null,
                isLastPage = false,
                pendingScrollIndex = null,
                prependAnchorCount = null,
                showImagesOnly = false,
                // 保留 searchQuery：发送回复后的 reload 不应静默清掉仍在生效的串内搜索
                //（退出搜索由 UI 显式调用 updateSearchQuery("")）
                searchQuery = _listState.value.searchQuery
            )
        }
        _refPopupState.value = ThreadRefPopupUiState()

        // Read progress first and open that page directly — avoids page1 paint then jump
        viewModelScope.launch {
            val progress = runCatching { threadRepository.getThreadProgress(threadId) }.getOrNull()
            val targetPage = progress?.page?.coerceAtLeast(1) ?: 1
            val localIndex = progress?.firstVisibleIndex?.coerceAtLeast(0) ?: 0
            if (progress != null && (targetPage > 1 || localIndex > 0)) {
                stickyResumeIndex = localIndex
            }

            currentPage = targetPage
            _listState.update {
                it.copy(
                    currentPage = targetPage,
                    startPage = targetPage
                )
            }
            loadPage(page = targetPage, mode = LoadMode.REPLACE)
        }
    }

    fun loadNextPage(resetFilterSkip: Boolean = false) {
        if (resetFilterSkip) filteredSkips = 0
        if (_listState.value.isLoading || isLastPage) return
        loadPage(
            page = currentPage,
            mode = LoadMode.APPEND,
            fromFilterSkip = !resetFilterSkip && filteredSkips > 0
        )
    }

    /** Load the page before [ThreadListUiState.startPage] so scrolling up can decrease the page number. */
    fun loadPreviousPage() {
        val start = _listState.value.startPage
        if (suppressPreviousLoad || start <= 1 || _listState.value.isLoading) return
        loadPage(page = start - 1, mode = LoadMode.PREPEND)
    }

    /** Call when the list has scrolled away from the top edge after a prepend. */
    fun allowPreviousLoad() {
        suppressPreviousLoad = false
    }

    fun jumpToPage(page: Int) {
        val total = totalPages
        if (page < 1 || page > total) return
        // Do not bail on isLoading — resume must cancel in-flight page-1 SWR via loadGeneration

        currentPage = page
        isLastPage = false
        rawPosts = emptyList()
        // Land at top of the jumped page (OP + first replies of that page)
        stickyResumeIndex = 0
        resumeScrollSeeded = false
        _listState.update {
            it.copy(
                // 不清空 displayItems：旧页内容保留到新页数据整体替换（SWR 同款行为），
                // 清空会在 AMOLED 纯黑背景上闪黑帧。落顶由 stickyResumeIndex 种子完成。
                isLastPage = false,
                currentPage = page,
                startPage = page,
                pendingScrollIndex = null,
                prependAnchorCount = null,
                isLoading = true
            )
        }
        loadPage(page = page, mode = LoadMode.REPLACE)
    }

    private enum class LoadMode { REPLACE, APPEND, PREPEND }

    /**
     * @param mode REPLACE: cache-then-network replace; APPEND: infinite scroll down;
     *             PREPEND: load earlier page when user scrolls up after a mid-thread jump.
     */
    private fun loadPage(page: Int, mode: LoadMode, fromFilterSkip: Boolean = false) {
        if (!fromFilterSkip) {
            filteredSkips = 0
            _listState.update { it.copy(filterHint = null) }
        }
        val gen = ++loadGeneration
        _listState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val flow = if (_listState.value.showPoOnly) {
                threadRepository.getPoDetail(threadId, page)
            } else {
                threadRepository.getThreadDetail(threadId, page)
            }

            // Avoid re-fetching quotes for the same page when SWR emits cache then network
            var quotesScheduled = false
            var sawSuccess = false
            var lastRawCount = 0
            var lastAddedVisible = 0
            var lastIsLast = true

            flow.collect { response ->
                // A newer loadPage/jumpToPage has started — drop this emission
                if (gen != loadGeneration) return@collect

                when (response) {
                    is XdResponse.Success -> {
                        val threadData = response.data
                        val newPosts = threadData.replies ?: emptyList()
                        val replyCount = threadData.replyCount ?: 0
                        val totalPagesVal =
                            if (replyCount <= 0) 1 else (replyCount + PAGE_SIZE - 1) / PAGE_SIZE
                        val isLast = newPosts.isEmpty() || page >= totalPagesVal

                        val prevDisplaySize = _listState.value.displayItems.size

                        val combinedPosts = when (mode) {
                            LoadMode.REPLACE -> newPosts
                            LoadMode.PREPEND -> {
                                val newIds = newPosts.mapTo(HashSet(newPosts.size)) { it.id }
                                newPosts + rawPosts.filter { it.id !in newIds }
                            }
                            LoadMode.APPEND -> {
                                if (rawPosts.isEmpty()) {
                                    newPosts
                                } else {
                                    val newIds = newPosts.mapTo(HashSet(newPosts.size)) { it.id }
                                    rawPosts.filter { it.id !in newIds } + newPosts
                                }
                            }
                        }
                        // Stale check again before mutating shared state
                        if (gen != loadGeneration) return@collect
                        rawPosts = combinedPosts

                        val mainPostData = threadData.toPostData()
                        val matcher = keywordMatcher
                        val showImagesOnly = _listState.value.showImagesOnly
                        val searchQuery = _listState.value.searchQuery
                        val displayItems = withContext(Dispatchers.Default) {
                            buildDisplayItems(
                                posts = combinedPosts,
                                poUserHash = threadData.userHash,
                                blockedUsers = blockedUsers,
                                keywordMatcher = matcher,
                                showImagesOnly = showImagesOnly,
                                searchQuery = searchQuery
                            )
                        }
                        if (gen != loadGeneration) return@collect

                        val prependCount = if (mode == LoadMode.PREPEND) {
                            (displayItems.size - prevDisplaySize).coerceAtLeast(0)
                        } else {
                            null
                        }
                        if (mode == LoadMode.PREPEND) {
                            // Prevent instantly chaining all earlier pages while stuck at top
                            suppressPreviousLoad = true
                        }

                        // Seed scroll target once with content so UI can init LazyListState there.
                        // Do not re-seed on SWR network — re-scroll is the main hitch source.
                        val seedPending = if (mode == LoadMode.REPLACE &&
                            stickyResumeIndex != null &&
                            !resumeScrollSeeded
                        ) {
                            resumeScrollSeeded = true
                            stickyResumeIndex
                        } else {
                            null
                        }

                        _listState.update { state ->
                            state.copy(
                                thread = threadData,
                                mainPostData = mainPostData,
                                displayItems = displayItems,
                                isLoading = false,
                                errorMessage = null,
                                // PREPEND must not clobber bottom "last page" flag
                                isLastPage = when (mode) {
                                    LoadMode.PREPEND -> state.isLastPage
                                    else -> isLast
                                },
                                currentPage = when (mode) {
                                    LoadMode.PREPEND -> state.currentPage
                                    else -> page
                                },
                                startPage = when (mode) {
                                    LoadMode.PREPEND -> page
                                    LoadMode.REPLACE -> page
                                    LoadMode.APPEND -> state.startPage
                                },
                                prependAnchorCount = prependCount,
                                pendingScrollIndex = seedPending ?: state.pendingScrollIndex
                            )
                        }

                        // 续读直接进第 N 页时 repo 的 page<=1 历史写入会跳过，这里补记
                        //（saveToHistory 自带去重/节流）
                        if (mode == LoadMode.REPLACE) {
                            viewModelScope.launch {
                                runCatching { threadRepository.saveToHistory(threadData) }
                            }
                        }

                        // Bookmark read: only when subscribed, once per distinct replyCount
                        if (_chromeState.value.isSubscribed &&
                            mode != LoadMode.PREPEND &&
                            lastMarkedReadCount != replyCount
                        ) {
                            lastMarkedReadCount = replyCount
                            viewModelScope.launch {
                                threadRepository.markBookmarkRead(threadId, replyCount)
                            }
                        }

                        // Defer quote network until after first paint; local seeds are free.
                        viewModelScope.launch {
                            if (stickyResumeIndex != null) {
                                delay(280)
                            } else {
                                delay(120)
                            }
                            if (gen != loadGeneration) return@launch
                            seedLocalQuotes(newPosts, threadData)
                            if (newPosts.isEmpty() || quotesScheduled) return@launch
                            quotesScheduled = true
                            // Only resolve quotes for the head of the page over the network
                            fetchQuotesForReplies(newPosts.take(14))
                        }

                        // Advance bottom cursor only for replace/append, not prepend
                        if (mode != LoadMode.PREPEND) {
                            lastRawCount = newPosts.size
                            lastAddedVisible = when (mode) {
                                LoadMode.REPLACE -> displayItems.size
                                LoadMode.APPEND -> (displayItems.size - prevDisplaySize).coerceAtLeast(0)
                                LoadMode.PREPEND -> 1
                            }
                            lastIsLast = isLast
                            sawSuccess = true
                            if (newPosts.isNotEmpty() && !isLast) {
                                currentPage = page + 1
                                isLastPage = false
                            } else {
                                currentPage = page
                                isLastPage = true
                                _listState.update { it.copy(isLastPage = true) }
                            }
                        }
                    }
                    is XdResponse.Error -> {
                        if (gen != loadGeneration) return@collect
                        _listState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = if (rawPosts.isEmpty()) response.message else null,
                                currentPage = if (mode == LoadMode.PREPEND) it.currentPage else page
                            )
                        }
                    }
                }
            }
            if (gen != loadGeneration || mode == LoadMode.PREPEND) return@launch
            val blockedPage = sawSuccess && lastRawCount > 0 && lastAddedVisible <= 0
            val canChain = blockedPage && !lastIsLast && filteredSkips < MAX_FILTERED_SKIPS
            if (canChain) {
                filteredSkips++
                loadPage(page = currentPage, mode = LoadMode.APPEND, fromFilterSkip = true)
            } else if (blockedPage) {
                val msg = if (filteredSkips >= MAX_FILTERED_SKIPS) {
                    "连续多页回复都被屏蔽规则挡住了"
                } else {
                    "这些回复都被屏蔽规则挡住了"
                }
                _listState.update { it.copy(filterHint = msg) }
            } else if (lastAddedVisible > 0) {
                filteredSkips = 0
                _listState.update { it.copy(filterHint = null) }
            }
        }
    }

    fun consumePrependAnchor() {
        if (_listState.value.prependAnchorCount != null) {
            _listState.update { it.copy(prependAnchorCount = null) }
        }
    }

    private fun scheduleRebuildDisplayItems(debounceMs: Long = 0L) {
        rebuildJob?.cancel()
        rebuildJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            // 捕获当前加载代际：rebuild 计算期间若有新一轮 loadPage 启动，
            // 丢弃本次结果——新加载会带最新过滤重建，晚到的旧结果会把它覆盖
            val gen = loadGeneration
            val thread = _listState.value.thread
            val posts = rawPosts
            if (thread == null && posts.isEmpty()) return@launch
            val showImagesOnly = _listState.value.showImagesOnly
            val searchQuery = _listState.value.searchQuery
            val matcher = keywordMatcher
            val items = withContext(Dispatchers.Default) {
                buildDisplayItems(
                    posts = posts,
                    poUserHash = thread?.userHash,
                    blockedUsers = blockedUsers,
                    keywordMatcher = matcher,
                    showImagesOnly = showImagesOnly,
                    searchQuery = searchQuery
                )
            }
            if (gen != loadGeneration) return@launch
            _listState.update { it.copy(displayItems = items) }
        }
    }

    private fun buildDisplayItems(
        posts: List<Reply>,
        poUserHash: String?,
        blockedUsers: Set<String>,
        keywordMatcher: KeywordMatcher,
        showImagesOnly: Boolean = false,
        searchQuery: String = ""
    ): List<ReplyDisplayItem> {
        if (posts.isEmpty()) return emptyList()
        val result = ArrayList<ReplyDisplayItem>(posts.size)
        val q = searchQuery.trim()
        val qLower = q.lowercase()
        val hasKeywords = !keywordMatcher.isEmpty
        for (reply in posts) {
            if (showImagesOnly && reply.img.isNullOrBlank()) continue
            if (reply.userHash in blockedUsers) continue
            // Lowercase content once for keyword block + in-thread search
            val contentLower =
                if (hasKeywords || q.isNotEmpty()) reply.content.lowercase()
                else null
            if (contentLower != null && hasKeywords &&
                keywordMatcher.containsMatch(contentLower, textIsLowercase = true)
            ) {
                continue
            }
            if (q.isNotEmpty() && contentLower != null) {
                val matchesKeyword = qLower in contentLower ||
                        (reply.title?.lowercase()?.contains(qLower) == true) ||
                        (reply.name?.lowercase()?.contains(qLower) == true)
                val matchesUser = reply.userHash.equals(q, ignoreCase = true) ||
                        reply.idStr.equals(q, ignoreCase = true)
                if (!matchesKeyword && !matchesUser) continue
            }
            val isPo = poUserHash != null && reply.userHash == poUserHash
            result.add(
                ReplyDisplayItem(
                    id = reply.id,
                    idStr = reply.idStr,
                    userHash = reply.userHash,
                    postData = reply.toPostData(isPo),
                    quoteIds = extractQuoteIdsFromContent(reply.content),
                    hasImage = !reply.img.isNullOrBlank(),
                    rawContent = reply.content
                )
            )
        }
        return result
    }

    private fun extractQuoteIdsFromContent(content: String): List<String> {
        if (content.isEmpty()) return emptyList()
        // Fast path: no quote marker after entity decode patterns
        if (content.indexOf('>') < 0 && content.indexOf('&') < 0) return emptyList()
        quoteIdExtractCache.get(content)?.let { return it }
        val decoded = content.decodeHtmlEntities()
        val matcher = QUOTE_ID_PATTERN.matcher(decoded)
        if (!matcher.find()) {
            quoteIdExtractCache.put(content, emptyList())
            return emptyList()
        }
        val list = ArrayList<String>(2)
        matcher.reset()
        while (matcher.find()) {
            matcher.group(1)?.let { list.add(it) }
        }
        val result = if (list.isEmpty()) emptyList() else list
        quoteIdExtractCache.put(content, result)
        return result
    }

    private fun seedLocalQuotes(replies: List<Reply>, mainThread: Thread) {
        val postsById = rawPosts.associateBy { it.idStr }
        for (reply in replies) {
            val ids = extractQuoteIdsFromContent(reply.content)
            for (id in ids) {
                if (quoteCache.containsKey(id)) continue
                val local = postsById[id]
                if (local != null) {
                    quoteCache[id] = local.copy(resto = threadId.toLongOrNull())
                    continue
                }
                if (mainThread.idStr == id) {
                    quoteCache[id] = mainThread.toReplyPlaceholder()
                }
            }
        }
    }

    private fun fetchQuotesForReplies(replies: List<Reply>) {
        viewModelScope.launch(Dispatchers.Default) {
            val quoteIds = LinkedHashSet<String>()
            for (reply in replies) {
                quoteIds.addAll(extractQuoteIdsFromContent(reply.content))
            }
            if (quoteIds.isEmpty()) return@launch

            val missingIds = quoteIds.filter { it !in quoteCache }
            if (missingIds.isEmpty()) return@launch

            val postsById = rawPosts.associateBy { it.idStr }
            val mainThread = _listState.value.thread
            val localBatch = HashMap<String, Reply>()
            val networkIds = ArrayList<String>()

            for (id in missingIds) {
                val localReply = postsById[id]
                if (localReply != null) {
                    localBatch[id] = localReply.copy(resto = threadId.toLongOrNull())
                    continue
                }
                if (mainThread != null && mainThread.idStr == id) {
                    localBatch[id] = mainThread.toReplyPlaceholder()
                    continue
                }
                networkIds.add(id)
            }

            if (localBatch.isNotEmpty()) {
                withContext(Dispatchers.Main.immediate) {
                    quoteCache.putAll(localBatch)
                }
            }

            if (networkIds.isEmpty()) return@launch

            // Cap concurrent + total network quote fetches (rest resolve on demand via popup)
            val toFetch = networkIds.take(MAX_NETWORK_QUOTES_PER_PAGE)
            val semaphore = Semaphore(MAX_PARALLEL_QUOTE_FETCHES)
            coroutineScope {
                toFetch.map { id ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            // 失败不缓存：错误占位符一旦入 cache，引用卡会一直显示
                            // 错误文案且弹窗不再走网络重试（重进串才能恢复）
                            val reply = resolveQuoteFromNetwork(id)
                            if (reply != null) {
                                withContext(Dispatchers.Main.immediate) {
                                    if (id !in quoteCache) {
                                        quoteCache[id] = reply
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    /** Network resolve; null on failure so callers do not cache an error placeholder. */
    private suspend fun resolveQuoteFromNetwork(id: String): Reply? {
        var result: Reply? = null
        threadRepository.getRefPost(id).collect { response ->
            result = when (response) {
                is XdResponse.Success -> response.data
                is XdResponse.Error -> null
            }
        }
        return result
    }

    private fun quoteErrorText(message: String?): String {
        val text = message?.trim().orEmpty()
        if (text.isEmpty()) return "该引用不存在或已被删除"
        if (text.contains("Expected BEGIN_") || text.contains("JsonDataException")) {
            return "该引用不存在或已被删除"
        }
        return text.take(80)
    }

    private fun missingQuotePlaceholder(id: String, message: String?): Reply = Reply(
        id = id.toLongOrNull() ?: 0L,
        fid = null,
        now = "",
        userHash = "",
        name = null,
        email = null,
        title = null,
        content = quoteErrorText(message),
        img = null,
        ext = null,
        sage = 0,
        admin = 0,
        hide = 0,
        replyCount = 0,
        resto = null
    )

    private fun Thread.toReplyPlaceholder(): Reply = Reply(
        id = id,
        fid = fid,
        now = now,
        userHash = userHash,
        name = name,
        email = email,
        title = title,
        content = content,
        img = img,
        ext = ext,
        sage = sage,
        admin = admin,
        hide = hide
    )

    fun togglePoOnly() {
        _listState.update { it.copy(showPoOnly = !it.showPoOnly) }
        // 原地切换数据源：保留旧列表直到新数据整体替换——先清空 displayItems 会在
        // AMOLED 纯黑背景上闪整屏黑帧。落点与新视图分页对齐：第 1 页顶部
        //（Po 视图分页与普通视图不同，续读页码没有意义）。
        currentPage = 1
        isLastPage = false
        rawPosts = emptyList()
        stickyResumeIndex = 0
        resumeScrollSeeded = false
        _listState.update {
            it.copy(
                isLastPage = false,
                currentPage = 1,
                startPage = 1,
                pendingScrollIndex = null,
                prependAnchorCount = null
            )
        }
        loadPage(page = 1, mode = LoadMode.REPLACE)
    }

    fun toggleShowImagesOnly() {
        _listState.update { it.copy(showImagesOnly = !it.showImagesOnly) }
        scheduleRebuildDisplayItems()
    }

    /**
     * Apply in-thread filter. Prefer calling from UI after local TextField debounce so
     * keystrokes do not rewrite [listUiState] and recompose the LazyColumn parent.
     */
    fun updateSearchQuery(query: String) {
        if (_listState.value.searchQuery == query) return
        _listState.update { it.copy(searchQuery = query) }
        scheduleRebuildDisplayItems(debounceMs = 0L)
    }

    fun toggleBookmark(folderUuid: String? = null, onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val thread = _listState.value.thread ?: return@launch

            if (folderUuid == null) {
                val isCurrentlySubscribed = _chromeState.value.isSubscribed
                if (isCurrentlySubscribed) {
                    threadRepository.removeBookmark(threadId)
                } else {
                    threadRepository.addBookmark(thread)
                }
            } else {
                threadRepository.addFeed(folderUuid, threadId).collect { response ->
                    when (response) {
                        is XdResponse.Success -> onComplete?.invoke("已保存至云端收藏夹")
                        is XdResponse.Error ->
                            onComplete?.invoke("保存失败: ${response.message.ifBlank { "网络错误" }}")
                    }
                }
            }
        }
    }

    /** Remove this thread from every cloud feed it is cached under. */
    fun removeCloudSubscription(onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            threadRepository.removeCloudSubscriptions(threadId).collect { response ->
                when (response) {
                    is XdResponse.Success ->
                        onComplete?.invoke(
                            if (response.data > 0) "已从云端收藏夹移除" else "云端收藏夹中没有找到该串"
                        )
                    is XdResponse.Error -> onComplete?.invoke("移除失败: ${response.message}")
                }
            }
        }
    }

    fun saveToFolder(folderUuid: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            threadRepository.addFeed(folderUuid, threadId).collect {
                onComplete()
            }
        }
    }

    fun removeBookmarkFromFolder(folderUuid: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            threadRepository.delFeed(folderUuid, threadId).collect {
                onComplete()
            }
        }
    }

    fun updateReplyText(text: String) {
        _composerState.update { state ->
            val quoted = state.quotedPostNo
            // If the user deleted >>No.xxx from the box, drop the "正在引用" chip
            val stillHasQuote = quoted != null && (
                text.contains(">>No.$quoted") ||
                    text.contains(">>$quoted")
                )
            state.copy(
                replyText = text,
                quotedPostNo = if (stillHasQuote) quoted else null
            )
        }
        scheduleDraftSave(text)
    }

    private fun scheduleDraftSave(text: String) {
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(DRAFT_DEBOUNCE_MS)
            // 草稿箱开关关闭时不落盘
            if (!settingsRepository.settings.value.draftBoxEnabled) return@launch
            // 二次确认：等待期间文本又变了（或已被发送成功清空）就不落盘旧值
            if (text != _composerState.value.replyText) return@launch
            // 草稿箱条目：随输入自动更新（同一串只保留一份）
            threadRepository.saveReplyDraft(
                threadId = threadId,
                threadTitle = _listState.value.thread?.title,
                content = text
            )
        }
    }

    fun setQuotedPost(postNo: String?) {
        _composerState.update { state ->
            val quotePrefix = if (postNo != null) ">>No.$postNo\n" else ""
            val newReplyText = if (postNo != null) {
                if (state.replyText.contains(quotePrefix)) {
                    state.replyText
                } else {
                    quotePrefix + state.replyText
                }
            } else {
                val currentQuote = state.quotedPostNo
                if (currentQuote != null) {
                    val currentQuotePrefix = ">>No.$currentQuote\n"
                    state.replyText
                        .replace(currentQuotePrefix, "")
                        .replace(">>No.$currentQuote", "")
                } else {
                    state.replyText
                }
            }

            scheduleDraftSave(newReplyText)

            state.copy(
                quotedPostNo = postNo,
                replyText = newReplyText
            )
        }
    }

    fun submitReply(imageFile: File? = null, onSuccess: () -> Unit = {}) {
        val currentComposer = _composerState.value
        val threadCookie = _threadCookieIndex.value?.let { idx ->
            settingsRepository.settings.value.cookiesList.getOrNull(idx)
        }
        if (currentComposer.replyText.isBlank() && imageFile == null) return
        if (currentComposer.isReplying) return

        _composerState.update { it.copy(isReplying = true, replyError = null) }
        viewModelScope.launch {
            try {
                threadRepository.doReplyThread(
                    parent = threadId,
                    title = null,
                    name = null,
                    email = null,
                    content = currentComposer.replyText,
                    imageFile = imageFile,
                    cookie = threadCookie
                ).collect { response ->
                    when (response) {
                        is XdResponse.Success -> {
                            draftSaveJob?.cancel()
                            if (response.data == "已提交") {
                                // 兜底判定（未确认成功）：保留输入与草稿，防止非常规错误页
                                // 被误判成功后把用户内容清空；刷新列表供用户自行核对
                                _composerState.update { it.copy(isReplying = false) }
                            } else {
                                _composerState.update { ReplyComposerState() }
                                // 发送成功 → 草稿箱中该串草稿一并清除。
                                // 走 draftSaveJob 槽位串行化：保证清空落在任何在途防抖写入之后
                                draftSaveJob?.cancel()
                                // 草稿箱开关关闭时不做清理写入
                                if (settingsRepository.settings.value.draftBoxEnabled) {
                                    draftSaveJob = viewModelScope.launch {
                                        threadRepository.saveReplyDraft(threadId, null, "")
                                    }
                                }
                                // 记录本串本次回复所用饼干（串级覆盖优先，其次全局选中），下次进串默认选中
                                val usedCookie = threadCookie
                                    ?: settingsRepository.settings.value.let { s ->
                                        s.cookiesList.getOrNull(s.selectedCookieIndex)
                                    }
                                if (usedCookie != null) {
                                    settingsRepository.saveThreadCookie(threadId, usedCookie)
                                }
                            }
                            onSuccess()
                            loadThreadDetails()
                        }
                        is XdResponse.Error -> {
                            _composerState.update {
                                it.copy(isReplying = false, replyError = response.message)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _composerState.update {
                    it.copy(isReplying = false, replyError = e.localizedMessage ?: "回复失败")
                }
            }
        }
    }

    /**
     * 串内举报：把对被举报楼层的引用作为新串发到值班室（fid=18）。
     * 独立发串链路：不写历史、不存草稿、不碰 DraftPrefillBus / saveNewThreadDraft，
     * 避免污染正常发串的草稿链路。
     */
    fun submitReport(title: String, content: String, cookie: String?, onResult: (success: Boolean, errorMessage: String?) -> Unit) {
        viewModelScope.launch {
            threadRepository.doPostThread(
                fid = REPORT_BOARD_FID,
                title = title.ifBlank { "举报" },
                name = null,
                email = null,
                content = content,
                imageFile = null,
                cookie = cookie
            ).collect { response ->
                when (response) {
                    is XdResponse.Success -> onResult(true, null)
                    is XdResponse.Error -> onResult(false, response.message)
                }
            }
        }
    }

    private val refBackStack = ArrayDeque<String>()

    fun showRefPopup(postId: String) {
        openRef(postId, recordHistory = true)
    }

    fun popRefPopup() {
        val prev = refBackStack.removeLastOrNull()
        if (prev == null) {
            dismissRefPopup()
        } else {
            openRef(prev, recordHistory = false)
        }
    }

    fun dismissRefPopup() {
        refBackStack.clear()
        _refPopupState.value = ThreadRefPopupUiState()
    }

    private fun openRef(postId: String, recordHistory: Boolean) {
        val current = _refPopupState.value.refPostId
        if (recordHistory && current != null && current != postId) {
            refBackStack.addLast(current)
            while (refBackStack.size > 12) refBackStack.removeFirst()
        }
        val canGoBack = refBackStack.isNotEmpty()
        val cached = quoteCache[postId]
        if (cached != null) {
            _refPopupState.value = ThreadRefPopupUiState(
                refPostId = postId,
                isRefLoading = false,
                refPostData = cached.toPostDataForRef(_listState.value.thread?.userHash),
                refError = null,
                canGoBack = canGoBack
            )
            return
        }

        _refPopupState.value = ThreadRefPopupUiState(
            refPostId = postId,
            isRefLoading = true,
            refPostData = null,
            refError = null,
            canGoBack = canGoBack
        )
        viewModelScope.launch {
            threadRepository.getRefPost(postId).collect { response ->
                if (_refPopupState.value.refPostId != postId) return@collect
                when (response) {
                    is XdResponse.Success -> {
                        val reply = response.data
                        quoteCache[postId] = reply
                        _refPopupState.update {
                            it.copy(
                                isRefLoading = false,
                                refPostData = reply.toPostDataForRef(_listState.value.thread?.userHash),
                                refError = null,
                                canGoBack = refBackStack.isNotEmpty()
                            )
                        }
                    }
                    is XdResponse.Error -> {
                        _refPopupState.update {
                            it.copy(
                                isRefLoading = false,
                                refError = response.message,
                                canGoBack = refBackStack.isNotEmpty()
                            )
                        }
                    }
                }
            }
        }
    }

    fun downloadThread(onResult: (String) -> Unit) {
        if (_chromeState.value.isDownloading) return
        _chromeState.update { it.copy(isDownloading = true) }
        viewModelScope.launch {
            threadRepository.downloadFullThread(threadId).collect { response ->
                _chromeState.update { it.copy(isDownloading = false) }
                when (response) {
                    is XdResponse.Success -> onResult("离线缓存已更新")
                    is XdResponse.Error -> onResult("下载失败: ${response.message}")
                }
            }
        }
    }

    /** 删除本串离线缓存（连带本地收藏与离线图片，收藏页「已离线」列表同步移除）。 */
    fun removeOffline(onResult: (String) -> Unit) {
        viewModelScope.launch {
            threadRepository.deleteOfflineThread(threadId)
            onResult("已删除离线缓存")
        }
    }

    /**
     * Persist progress as (pageNumber, indexWithinThatPageList).
     * [firstVisibleIndex] is the absolute LazyColumn index for the current session
     * (which may contain multiple appended pages starting at [listStartPage]).
     */
    fun saveReadingProgress(page: Int, firstVisibleIndex: Int) {
        val start = _listState.value.startPage.coerceAtLeast(1)
        // UI 的 page 与推导结果可能因过滤偏移而不同；推导值与 rawPosts 分页体系一致，
        // 直接采用（原先的 if/else 两分支对相同入参调用相同函数，是恒等死逻辑）
        val (savedPage, localIndex) = absoluteIndexToPageLocal(start, firstVisibleIndex)
        viewModelScope.launch {
            threadRepository.saveThreadProgress(
                threadId = threadId,
                page = savedPage,
                firstVisibleIndex = localIndex
            )
        }
    }

    /**
     * @return page number (1-based) and LazyColumn index within a single-page list
     *         where item 0 is OP and items 1.. are that page's replies.
     */
    private fun absoluteIndexToPageLocal(listStartPage: Int, firstVisibleIndex: Int): Pair<Int, Int> {
        if (firstVisibleIndex <= 0) {
            return listStartPage.coerceAtLeast(1) to 0
        }
        val replyIndex = firstVisibleIndex - 1
        val page = listStartPage + replyIndex / PAGE_SIZE
        val localReplyIndex = replyIndex % PAGE_SIZE
        // 1 = first reply on that page (0 is reserved for OP header)
        val localListIndex = 1 + localReplyIndex
        return page.coerceAtLeast(1) to localListIndex
    }

    fun consumePendingScrollIndex() {
        if (_listState.value.pendingScrollIndex != null) {
            _listState.update { it.copy(pendingScrollIndex = null) }
        }
        // Drop sticky after first successful seed; item keys preserve scroll across SWR
        stickyResumeIndex = null
    }

    fun clearReadingProgress() {
        viewModelScope.launch {
            threadRepository.clearThreadProgress(threadId)
        }
    }

    private fun Reply.toPostDataForRef(poUserHash: String?): PostData {
        val isFollowUp = resto != null && resto > 0L
        val currentThreadId = threadId
        val isCurrentThreadReply = _listState.value.displayItems.any { it.idStr == idStr }
        val targetResto = when {
            isFollowUp -> resto.toString()
            isCurrentThreadReply -> currentThreadId
            idStr == currentThreadId -> currentThreadId
            else -> idStr
        }
        return PostData(
            id = idStr,
            title = title.effectiveTitle(),
            userName = name ?: "无名氏",
            userId = userHash,
            createdAt = now,
            content = content,
            imageUrl = IslandImage.url(imageUrl),
            isPo = poUserHash != null && userHash == poUserHash,
            isAdmin = isAdmin,
            isSage = isSage,
            resto = targetResto
        )
    }
}
