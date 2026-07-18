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
import com.mioo.dao.ui.components.PostData
import com.mioo.dao.ui.components.ReplyDisplayItem
import com.mioo.dao.ui.components.decodeHtmlEntities
import com.mioo.dao.ui.components.toPostData
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
    val searchQuery: String = ""
)

/** Top-bar chrome only — bookmark / folders / offline download. */
@Immutable
data class ThreadChromeUiState(
    val isSubscribed: Boolean = false,
    val feedFolders: List<FeedFolder> = emptyList(),
    val isDownloading: Boolean = false
)

/** Nested >>No.xxxx popup — isolated so open/close never invalidates the list. */
@Immutable
data class ThreadRefPopupUiState(
    val refPostId: String? = null,
    val refPostData: PostData? = null,
    val isRefLoading: Boolean = false,
    val refError: String? = null
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

    /**
     * Per-key observable quote map. Compose only invalidates items that read a given id,
     * so filling quote A does not recompose the card that only needs quote B.
     */
    val quoteCache: SnapshotStateMap<String, Reply> = mutableStateMapOf()

    companion object {
        private const val PAGE_SIZE = 19
        private const val DRAFT_DEBOUNCE_MS = 400L
        private const val MAX_PARALLEL_QUOTE_FETCHES = 6
        private val QUOTE_ID_PATTERN: Pattern = Pattern.compile(">>(?:No\\.)?(\\d+)")
        private const val IMAGE_CDN = "https://image.nmb.best"
    }

    private var currentPage = 1
    private var isLastPage = false
    private var draftSaveJob: Job? = null
    private var rebuildJob: Job? = null
    private var progressChecked = false
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
            threadRepository.isBookmarked(threadId).collect { bookmarked ->
                _chromeState.update { it.copy(isSubscribed = bookmarked) }
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

        viewModelScope.launch {
            val draft = settingsRepository.getThreadDraft(threadId)
            if (draft.isNotEmpty()) {
                _composerState.update { it.copy(replyText = draft) }
            }
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
                searchQuery = ""
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

    fun loadNextPage() {
        if (_listState.value.isLoading || isLastPage) return
        loadPage(page = currentPage, mode = LoadMode.APPEND)
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
                displayItems = emptyList(),
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
    private fun loadPage(page: Int, mode: LoadMode) {
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

                        val mainPostData = threadData.toPostData(IMAGE_CDN)
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

                        // Mark bookmark as read with latest reply count (low priority)
                        viewModelScope.launch {
                            threadRepository.markBookmarkRead(
                                threadId,
                                threadData.replyCount ?: 0
                            )
                        }

                        // Defer quote work until after first resume frame to reduce hitch
                        viewModelScope.launch {
                            if (stickyResumeIndex != null) {
                                delay(320)
                            }
                            if (gen != loadGeneration) return@launch
                            seedLocalQuotes(newPosts, threadData)
                            if (mode != LoadMode.PREPEND) {
                                if (newPosts.isNotEmpty() && !isLast) {
                                    if (!quotesScheduled) {
                                        quotesScheduled = true
                                        fetchQuotesForReplies(newPosts)
                                    }
                                } else if (newPosts.isNotEmpty() && !quotesScheduled) {
                                    quotesScheduled = true
                                    fetchQuotesForReplies(newPosts)
                                }
                            } else if (newPosts.isNotEmpty() && !quotesScheduled) {
                                quotesScheduled = true
                                fetchQuotesForReplies(newPosts)
                            }
                        }

                        // Advance bottom cursor only for replace/append, not prepend
                        if (mode != LoadMode.PREPEND) {
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
                    postData = reply.toPostData(isPo, IMAGE_CDN),
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

            // Stream results onto main as each finishes so UI fills progressively
            val semaphore = Semaphore(MAX_PARALLEL_QUOTE_FETCHES)
            coroutineScope {
                networkIds.map { id ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val reply = resolveQuoteFromNetwork(id)
                            withContext(Dispatchers.Main.immediate) {
                                if (id !in quoteCache) {
                                    quoteCache[id] = reply
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private suspend fun resolveQuoteFromNetwork(id: String): Reply {
        var result: Reply? = null
        threadRepository.getRefPost(id).collect { response ->
            result = when (response) {
                is XdResponse.Success -> response.data
                is XdResponse.Error -> missingQuotePlaceholder(id)
            }
        }
        return result ?: missingQuotePlaceholder(id)
    }

    private fun missingQuotePlaceholder(id: String): Reply = Reply(
        id = id.toLongOrNull() ?: 0L,
        fid = null,
        now = "",
        userHash = "",
        name = null,
        email = null,
        title = null,
        content = "该引用不存在或已被删除",
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
        loadThreadDetails()
    }

    fun toggleShowImagesOnly() {
        _listState.update { it.copy(showImagesOnly = !it.showImagesOnly) }
        scheduleRebuildDisplayItems()
    }

    fun updateSearchQuery(query: String) {
        _listState.update { it.copy(searchQuery = query) }
        // Debounce: typing must not rebuild the full filtered list every keystroke
        scheduleRebuildDisplayItems(debounceMs = 220L)
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
                threadRepository.addFeed(folderUuid, threadId).collect {
                    onComplete?.invoke("已保存至云端收藏夹")
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
            settingsRepository.saveThreadDraft(threadId, text)
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

    fun submitReply(author: String = "无名氏", imageFile: File? = null, onSuccess: () -> Unit = {}) {
        val currentComposer = _composerState.value
        if (currentComposer.replyText.isBlank() && imageFile == null) return
        if (currentComposer.isReplying) return

        _composerState.update { it.copy(isReplying = true, replyError = null) }
        viewModelScope.launch {
            try {
                threadRepository.doReplyThread(
                    parent = threadId,
                    title = null,
                    name = if (author.isBlank()) null else author,
                    email = null,
                    content = currentComposer.replyText,
                    imageFile = imageFile
                ).collect { response ->
                    when (response) {
                        is XdResponse.Success -> {
                            draftSaveJob?.cancel()
                            _composerState.update { ReplyComposerState() }
                            settingsRepository.saveThreadDraft(threadId, "")
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

    fun showRefPopup(postId: String) {
        val cached = quoteCache[postId]
        if (cached != null) {
            _refPopupState.value = ThreadRefPopupUiState(
                refPostId = postId,
                isRefLoading = false,
                refPostData = cached.toPostDataForRef(_listState.value.thread?.userHash),
                refError = null
            )
            return
        }

        _refPopupState.value = ThreadRefPopupUiState(
            refPostId = postId,
            isRefLoading = true,
            refPostData = null,
            refError = null
        )
        viewModelScope.launch {
            threadRepository.getRefPost(postId).collect { response ->
                // Ignore stale responses if user dismissed or opened another ref
                if (_refPopupState.value.refPostId != postId) return@collect
                when (response) {
                    is XdResponse.Success -> {
                        val reply = response.data
                        quoteCache[postId] = reply
                        _refPopupState.update {
                            it.copy(
                                isRefLoading = false,
                                refPostData = reply.toPostDataForRef(_listState.value.thread?.userHash)
                            )
                        }
                    }
                    is XdResponse.Error -> {
                        _refPopupState.update {
                            it.copy(isRefLoading = false, refError = response.message)
                        }
                    }
                }
            }
        }
    }

    fun dismissRefPopup() {
        _refPopupState.value = ThreadRefPopupUiState()
    }

    fun downloadThread(onResult: (String) -> Unit) {
        if (_chromeState.value.isDownloading) return
        _chromeState.update { it.copy(isDownloading = true) }
        viewModelScope.launch {
            threadRepository.downloadFullThread(threadId).collect { response ->
                _chromeState.update { it.copy(isDownloading = false) }
                when (response) {
                    is XdResponse.Success -> onResult("下载完成，已加入本地收藏")
                    is XdResponse.Error -> onResult("下载失败: ${response.message}")
                }
            }
        }
    }

    /**
     * Persist progress as (pageNumber, indexWithinThatPageList).
     * [firstVisibleIndex] is the absolute LazyColumn index for the current session
     * (which may contain multiple appended pages starting at [listStartPage]).
     */
    fun saveReadingProgress(page: Int, firstVisibleIndex: Int) {
        val start = _listState.value.startPage.coerceAtLeast(1)
        val (savedPage, localIndex) = absoluteIndexToPageLocal(start, firstVisibleIndex)
        // Prefer UI-computed page when it is consistent; fall back to derived page
        val pageToSave = page.coerceAtLeast(1).let { uiPage ->
            // If UI page matches derived, use localIndex for that page; else re-derive both
            if (uiPage == savedPage) uiPage to localIndex
            else absoluteIndexToPageLocal(start, firstVisibleIndex)
        }
        viewModelScope.launch {
            threadRepository.saveThreadProgress(
                threadId = threadId,
                page = pageToSave.first,
                firstVisibleIndex = pageToSave.second
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
            imageUrl = if (imageUrl != null) "$IMAGE_CDN/image/$imageUrl" else null,
            isPo = poUserHash != null && userHash == poUserHash,
            isAdmin = isAdmin,
            isSage = isSage,
            resto = targetResto
        )
    }
}
