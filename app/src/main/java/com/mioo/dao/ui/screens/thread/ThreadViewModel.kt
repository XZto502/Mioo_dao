package com.mioo.dao.ui.screens.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mioo.dao.data.model.Reply
import com.mioo.dao.data.model.Thread
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.data.repository.SettingsRepository
import com.mioo.dao.data.repository.ThreadRepository
import com.mioo.dao.ui.components.PostData
import com.mioo.dao.ui.components.decodeHtmlEntities
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import java.io.File
import javax.inject.Inject

data class ThreadUiState(
    val thread: Thread? = null,
    val posts: List<Reply> = emptyList(),
    val showPoOnly: Boolean = false,
    val isSubscribed: Boolean = false,
    val feedFolders: List<com.mioo.dao.data.model.FeedFolder> = emptyList(),
    val replyText: String = "",
    val quotedPostNo: String? = null,
    val isLoading: Boolean = false,
    val isReplying: Boolean = false,
    val replyError: String? = null,
    val errorMessage: String? = null,
    val refPostId: String? = null,
    val refPostData: PostData? = null,
    val isRefLoading: Boolean = false,
    val refError: String? = null,
    val quoteCache: Map<String, Reply> = emptyMap(),
    val isDownloading: Boolean = false
)

@HiltViewModel
class ThreadViewModel @Inject constructor(
    private val threadRepository: ThreadRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val threadId: String = savedStateHandle.get<String>("threadId")
        ?: throw IllegalArgumentException("threadId is required")

    private val _uiState = MutableStateFlow(ThreadUiState(isLoading = true))
    val uiState: StateFlow<ThreadUiState> = _uiState.asStateFlow()

    init {
        // Observe subscription status
        viewModelScope.launch {
            threadRepository.isBookmarked(threadId).collect { bookmarked ->
                _uiState.update { it.copy(isSubscribed = bookmarked) }
            }
        }
        
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(feedFolders = settings.feedFolders) }
            }
        }
        
        viewModelScope.launch {
            val draft = settingsRepository.getThreadDraft(threadId)
            if (draft.isNotEmpty()) {
                _uiState.update { it.copy(replyText = draft) }
            }
        }
        
        loadThreadDetails()
    }

    private fun loadThreadDetails() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val flow = if (_uiState.value.showPoOnly) {
                threadRepository.getPoDetail(threadId, 1)
            } else {
                threadRepository.getThreadDetail(threadId, 1)
            }
            
            flow.collect { response ->
                when (response) {
                    is XdResponse.Success -> {
                        val threadData = response.data
                        val postsList = threadData.replies ?: emptyList()
                        _uiState.update { state ->
                            state.copy(
                                thread = threadData,
                                posts = postsList,
                                isLoading = false,
                                errorMessage = null
                            )
                        }
                        fetchQuotesForReplies(postsList)
                    }
                    is XdResponse.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = response.message
                            )
                        }
                    }
                }
            }
        }
    }

    private fun fetchQuotesForReplies(replies: List<Reply>) {
        val quoteIds = replies.flatMap { reply ->
            val decodedContent = reply.content.decodeHtmlEntities()
            val matcher = Pattern.compile(">>(?:No\\.)?(\\d+)").matcher(decodedContent)
            val ids = mutableListOf<String>()
            while (matcher.find()) {
                matcher.group(1)?.let { ids.add(it) }
            }
            ids
        }.distinct()

        quoteIds.forEach { id ->
            if (_uiState.value.quoteCache.containsKey(id)) return@forEach
            
            // Check if it exists locally in replies
            val localReply = _uiState.value.posts.firstOrNull { it.idStr == id }
            if (localReply != null) {
                val updatedReply = localReply.copy(resto = threadId.toLongOrNull())
                _uiState.update { it.copy(quoteCache = it.quoteCache + (id to updatedReply)) }
                return@forEach
            }
            
            // Check if it's the main thread
            val mainThread = _uiState.value.thread
            if (mainThread != null && mainThread.idStr == id) {
                val threadAsReply = Reply(
                    id = mainThread.id,
                    fid = mainThread.fid,
                    now = mainThread.now,
                    userHash = mainThread.userHash,
                    name = mainThread.name,
                    email = mainThread.email,
                    title = mainThread.title,
                    content = mainThread.content,
                    img = mainThread.img,
                    ext = mainThread.ext,
                    sage = mainThread.sage,
                    admin = mainThread.admin,
                    hide = mainThread.hide
                )
                _uiState.update { it.copy(quoteCache = it.quoteCache + (id to threadAsReply)) }
                return@forEach
            }

            // Otherwise, fetch from repository
            viewModelScope.launch {
                threadRepository.getRefPost(id).collect { response ->
                    if (response is XdResponse.Success) {
                        _uiState.update { it.copy(quoteCache = it.quoteCache + (id to response.data)) }
                    }
                }
            }
        }
    }

    fun togglePoOnly() {
        _uiState.update { it.copy(showPoOnly = !it.showPoOnly) }
        loadThreadDetails()
    }

    fun toggleBookmark(folderUuid: String? = null, onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val thread = _uiState.value.thread ?: return@launch
            
            if (folderUuid == null) {
                // Local save
                val isCurrentlySubscribed = _uiState.value.isSubscribed
                if (isCurrentlySubscribed) {
                    threadRepository.removeBookmark(threadId)
                } else {
                    threadRepository.addBookmark(thread)
                }
            } else {
                // Remote save
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
        _uiState.update { it.copy(replyText = text) }
        viewModelScope.launch {
            settingsRepository.saveThreadDraft(threadId, text)
        }
    }

    fun setQuotedPost(postNo: String?) {
        _uiState.update { state ->
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
            
            viewModelScope.launch {
                settingsRepository.saveThreadDraft(threadId, newReplyText)
            }
            
            state.copy(
                quotedPostNo = postNo,
                replyText = newReplyText
            )
        }
    }

    fun submitReply(author: String = "无名氏", imageFile: File? = null, onSuccess: () -> Unit = {}) {
        val currentState = _uiState.value
        if (currentState.replyText.isBlank() && imageFile == null) return
        if (currentState.isReplying) return

        _uiState.update { it.copy(isReplying = true, replyError = null) }
        viewModelScope.launch {
            try {
                threadRepository.doReplyThread(
                    parent = threadId,
                    title = null,
                    name = if (author.isBlank()) null else author,
                    email = null,
                    content = currentState.replyText,
                    imageFile = imageFile
                ).collect { response ->
                    when (response) {
                        is XdResponse.Success -> {
                            _uiState.update { it.copy(replyText = "", quotedPostNo = null, isReplying = false, replyError = null) }
                            viewModelScope.launch {
                                settingsRepository.saveThreadDraft(threadId, "")
                            }
                            onSuccess()
                            loadThreadDetails()
                        }
                        is XdResponse.Error -> {
                            _uiState.update { it.copy(isReplying = false, replyError = response.message) }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isReplying = false, replyError = e.localizedMessage ?: "回复失败") }
            }
        }
    }

    fun showRefPopup(postId: String) {
        _uiState.update { it.copy(refPostId = postId, isRefLoading = true, refPostData = null, refError = null) }
        viewModelScope.launch {
            threadRepository.getRefPost(postId).collect { response ->
                when (response) {
                    is XdResponse.Success -> {
                        val reply = response.data
                        val isFollowUp = reply.resto != null && reply.resto > 0L
                        val postData = PostData(
                            id = reply.idStr,
                            title = reply.title ?: "",
                            userName = reply.name ?: "无名氏",
                            userId = reply.userHash,
                            createdAt = reply.now,
                            content = reply.content,
                            imageUrl = if (reply.imageUrl != null) "https://image.nmb.best/image/${reply.imageUrl}" else null,
                            isPo = false,
                            isAdmin = reply.isAdmin,
                            isSage = reply.isSage,
                            resto = if (isFollowUp) reply.resto.toString() else reply.idStr
                        )
                        _uiState.update { it.copy(isRefLoading = false, refPostData = postData) }
                    }
                    is XdResponse.Error -> {
                        _uiState.update { it.copy(isRefLoading = false, refError = response.message) }
                    }
                }
            }
        }
    }

    fun dismissRefPopup() {
        _uiState.update { it.copy(refPostId = null, refPostData = null, refError = null) }
    }

    fun downloadThread(onResult: (String) -> Unit) {
        if (_uiState.value.isDownloading) return
        _uiState.update { it.copy(isDownloading = true) }
        viewModelScope.launch {
            threadRepository.downloadFullThread(threadId).collect { response ->
                _uiState.update { it.copy(isDownloading = false) }
                when (response) {
                    is XdResponse.Success -> {
                        onResult("下载完成，已加入本地收藏")
                    }
                    is XdResponse.Error -> {
                        onResult("下载失败: ${response.message}")
                    }
                }
            }
        }
    }
}
