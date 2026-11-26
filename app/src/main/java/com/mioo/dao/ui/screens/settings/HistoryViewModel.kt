package com.mioo.dao.ui.screens.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mioo.dao.data.local.HistoryType
import com.mioo.dao.data.local.SettingsDataStore
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.data.repository.ForumRepository
import com.mioo.dao.data.repository.ThreadRepository
import com.mioo.dao.ui.components.IslandImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class HistoryItem(
    val id: String, val type: HistoryType, val threadId: String?, val forumName: String,
    val cookieHash: String, val timestamp: Long, val title: String?, val content: String,
    val threadSummary: String = "", val lastReadPage: Int = 1, val image: String? = null
)

data class HistoryPageState(
    val loaded: Boolean = false,
    val items: List<HistoryItem> = emptyList(),
    val isRefreshing: Boolean = false
)

internal fun removeHistoryItem(state: HistoryPageState, id: String): HistoryPageState =
    state.copy(items = state.items.filterNot { it.id == id })

internal fun clearHistoryPage(state: HistoryPageState): HistoryPageState =
    state.copy(loaded = true, items = emptyList(), isRefreshing = false)

internal fun patchResolvedThreadId(state: HistoryPageState, itemId: String, threadId: String): HistoryPageState =
    state.copy(items = state.items.map { item ->
        if (item.id == itemId) item.copy(threadId = threadId) else item
    })

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ThreadRepository,
    private val forums: ForumRepository,
    private val savedState: SavedStateHandle,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    val initialType = settingsDataStore.lastHistoryTypeFlow.map { value ->
        HistoryType.values().firstOrNull { it.name == value } ?: HistoryType.BROWSE
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun saveSelectedType(type: HistoryType) {
        viewModelScope.launch { settingsDataStore.saveLastHistoryType(type.name) }
    }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val opening = mutableSetOf<String>()
    val messages = _messages.asSharedFlow()

    fun query(type: HistoryType): StateFlow<String> = savedState.getStateFlow("history.query.${type.name}", "")

    fun setQuery(type: HistoryType, value: String) {
        savedState["history.query.${type.name}"] = value
        if (pageStates.getValue(type).value.loaded) {
            refresh(type, resolvePending = false)
        }
    }

    private val forumNames = forums.getForumList().map { result ->
        (result as? XdResponse.Success)?.data.orEmpty().flatMap { it.forums }.associate { it.id to it.name }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val pageStates: Map<HistoryType, MutableStateFlow<HistoryPageState>> =
        HistoryType.values().associateWith { MutableStateFlow(HistoryPageState()) }
    private val loadJobs = mutableMapOf<HistoryType, Job>()

    fun pageState(type: HistoryType): StateFlow<HistoryPageState> = pageStates.getValue(type).asStateFlow()

    private fun forumName(fid: String?, stored: String, names: Map<String, String>): String {
        val placeholder = stored.isBlank() || stored == "板块 ${fid.orEmpty()}"
        return if (placeholder) names[fid] ?: stored.ifBlank { "板块 ${fid.orEmpty()}" } else stored
    }

    private suspend fun resolveForumNames(): Map<String, String> {
        val live = forumNames.value
        if (live.isNotEmpty()) return live
        return runCatching { forums.getCachedForumList() }.getOrNull().orEmpty()
            .flatMap { it.forums }
            .associate { it.id to it.name }
    }

    fun ensureLoaded(type: HistoryType) {
        val current = pageStates.getValue(type).value
        if (!current.loaded && !current.isRefreshing) {
            refresh(type, resolvePending = false)
        }
    }

    fun refresh(type: HistoryType, resolvePending: Boolean = true) {
        loadJobs[type]?.cancel()
        val stateFlow = pageStates.getValue(type)
        stateFlow.update { it.copy(isRefreshing = true) }
        loadJobs[type] = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (resolvePending && type != HistoryType.BROWSE) {
                    runCatching { repository.resolvePendingPostActions() }
                }
                val q = query(type).value.trim()
                val names = resolveForumNames()
                val items = when (type) {
                    HistoryType.BROWSE -> repository.observeBrowsingHistory(q).first().map { record ->
                        val h = record.history
                        HistoryItem(
                            id = h.id,
                            type = HistoryType.BROWSE,
                            threadId = h.id,
                            forumName = forumName(h.fid, h.forumName, names),
                            cookieHash = h.userid,
                            timestamp = h.timestamp,
                            title = h.title,
                            content = h.content,
                            lastReadPage = record.lastReadPage
                        )
                    }
                    HistoryType.REPLY, HistoryType.NEW_THREAD -> repository.observePostActions(type, q).first().map { h ->
                        val image = h.imagePath?.let(::File)?.takeIf { it.isFile }?.toURI()?.toString()
                            ?: IslandImage.url(h.imageUrl)
                        HistoryItem(
                            id = h.id,
                            type = type,
                            threadId = h.threadId,
                            forumName = forumName(h.forumId, h.forumName, names),
                            cookieHash = h.cookieHash,
                            timestamp = h.timestamp,
                            title = h.title,
                            content = h.content,
                            threadSummary = h.threadTitle?.takeIf { it.isNotBlank() }?.let { "$it\n${h.threadSummary}" }
                                ?: h.threadSummary,
                            image = image
                        )
                    }
                }
                stateFlow.value = HistoryPageState(loaded = true, items = items, isRefreshing = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                stateFlow.update { it.copy(isRefreshing = false) }
                _messages.emit("历史记录刷新失败，请重试")
            }
        }
    }

    init { viewModelScope.launch(Dispatchers.IO) { runCatching { repository.resolvePendingPostActions() } } }

    fun open(item: HistoryItem, navigate: (String) -> Unit) {
        item.threadId?.let { navigate(it); return }
        if (!opening.add(item.id)) return
        _messages.tryEmit("正在确认新串号…")
        viewModelScope.launch {
            try {
                val id = withContext(Dispatchers.IO) { repository.resolvePostAction(item.id) }
                if (id != null) {
                    pageStates.getValue(item.type).update { patchResolvedThreadId(it, item.id, id) }
                    navigate(id)
                } else {
                    _messages.emit("暂未确认新串号，记录已保留，请稍后重试")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _messages.emit("串号查询失败，请稍后重试")
            } finally {
                opening.remove(item.id)
            }
        }
    }

    fun delete(item: HistoryItem) = mutate(onSuccess = {
        pageStates.getValue(item.type).update { removeHistoryItem(it, item.id) }
    }) {
        if (item.type == HistoryType.BROWSE) repository.deleteBrowsingHistory(item.id)
        else repository.deletePostAction(item.id)
    }

    fun clear(type: HistoryType) = mutate(onSuccess = {
        pageStates.getValue(type).update { clearHistoryPage(it) }
    }) {
        if (type == HistoryType.BROWSE) repository.clearHistory() else repository.clearPostActions(type)
    }

    private fun mutate(onSuccess: () -> Unit, action: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                action()
                onSuccess()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _messages.emit("历史记录更新失败，请重试")
            }
        }
    }
}
