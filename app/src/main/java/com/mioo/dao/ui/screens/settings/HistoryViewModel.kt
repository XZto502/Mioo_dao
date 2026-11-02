package com.mioo.dao.ui.screens.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mioo.dao.data.local.HistoryType
import com.mioo.dao.data.repository.ThreadRepository
import com.mioo.dao.data.repository.ForumRepository
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.ui.components.IslandImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class HistoryItem(
    val id: String, val type: HistoryType, val threadId: String?, val forumName: String,
    val cookieHash: String, val timestamp: Long, val title: String?, val content: String,
    val threadSummary: String = "", val lastReadPage: Int = 1, val image: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ThreadRepository,
    private val forums: ForumRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val opening = mutableSetOf<String>()
    val messages = _messages.asSharedFlow()
    fun query(type: HistoryType): StateFlow<String> = savedState.getStateFlow("history.query.${type.name}", "")
    fun setQuery(type: HistoryType, value: String) { savedState["history.query.${type.name}"] = value }

    private val forumNames = forums.getForumList().map { result ->
        (result as? XdResponse.Success)?.data.orEmpty().flatMap { it.forums }.associate { it.id to it.name }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private fun forumName(fid: String?, stored: String, names: Map<String, String>): String {
        val placeholder = stored.isBlank() || stored == "板块 ${fid.orEmpty()}"
        return if (placeholder) names[fid] ?: stored.ifBlank { "板块 ${fid.orEmpty()}" } else stored
    }

    private val browsing = query(HistoryType.BROWSE).flatMapLatest { text ->
        combine(repository.observeBrowsingHistory(text.trim()), forumNames) { records, names -> records.map { record ->
            val h = record.history
            HistoryItem(h.id, HistoryType.BROWSE, h.id, forumName(h.fid, h.forumName, names),
                h.userid, h.timestamp, h.title, h.content, lastReadPage = record.lastReadPage)
        } }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun actions(type: HistoryType) = query(type).flatMapLatest { text ->
        combine(repository.observePostActions(type, text.trim()), forumNames) { records, names -> records.map { h ->
            val image = h.imagePath?.let(::File)?.takeIf { it.isFile }?.toURI()?.toString() ?: IslandImage.url(h.imageUrl)
            HistoryItem(h.id, type, h.threadId, forumName(h.forumId, h.forumName, names), h.cookieHash, h.timestamp, h.title, h.content,
                threadSummary = h.threadTitle?.takeIf { it.isNotBlank() }?.let { "$it\n${h.threadSummary}" } ?: h.threadSummary,
                image = image)
        } }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val replies = actions(HistoryType.REPLY)
    private val threads = actions(HistoryType.NEW_THREAD)
    fun items(type: HistoryType): StateFlow<List<HistoryItem>> = when (type) {
        HistoryType.BROWSE -> browsing
        HistoryType.REPLY -> replies
        HistoryType.NEW_THREAD -> threads
    }

    init { viewModelScope.launch(Dispatchers.IO) { runCatching { repository.resolvePendingPostActions() } } }

    fun open(item: HistoryItem, navigate: (String) -> Unit) {
        item.threadId?.let { navigate(it); return }
        if (!opening.add(item.id)) return
        _messages.tryEmit("正在确认新串号…")
        viewModelScope.launch {
            try {
                val id = kotlinx.coroutines.withContext(Dispatchers.IO) { repository.resolvePostAction(item.id) }
                if (id != null) navigate(id) else _messages.emit("暂未确认新串号，记录已保留，请稍后重试")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _messages.emit("串号查询失败，请稍后重试")
            } finally {
                opening.remove(item.id)
            }
        }
    }
    fun delete(item: HistoryItem) = mutate {
        if (item.type == HistoryType.BROWSE) repository.deleteBrowsingHistory(item.id) else repository.deletePostAction(item.id)
    }
    fun clear(type: HistoryType) = mutate {
        if (type == HistoryType.BROWSE) repository.clearHistory() else repository.clearPostActions(type)
    }
    private fun mutate(action: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try { action() } catch (e: Exception) {
                if (e is CancellationException) throw e
                _messages.emit("历史记录更新失败，请重试")
            }
        }
    }
}
