package com.mioo.dao.ui.screens.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mioo.dao.data.model.LocalSearchResult
import com.mioo.dao.data.model.XdWebSearch
import com.mioo.dao.data.repository.ThreadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Immutable
data class RemoteSearchHit(val postId: String, val threadId: String, val title: String, val content: String, val isReply: Boolean)

@Immutable
data class SearchUiState(
    val query: String = "",
    val remoteHits: List<RemoteSearchHit> = emptyList(),
    val remoteLoading: Boolean = false,
    val remoteError: String? = null,
    val remoteSearched: Boolean = false,
    val remotePage: Int = 1,
    val remoteHasMore: Boolean = false,
    val isSearching: Boolean = false,
    val result: LocalSearchResult = LocalSearchResult(),
    val recentQueries: List<String> = emptyList(),
    val preferredEngine: XdWebSearch.Engine = XdWebSearch.Engine.GOOGLE,
    val webSearchUrl: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val threadRepository: ThreadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var remoteJob: Job? = null
    private val searchClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val recent = ArrayDeque<String>()

    fun onQueryChange(query: String) {
        remoteJob?.cancel()
        _uiState.update { it.copy(query = query, webSearchUrl = null, remoteHits = emptyList(), remoteLoading = false, remoteError = null, remoteSearched = false, remotePage = 1, remoteHasMore = false) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(isSearching = false, result = LocalSearchResult()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(280)
            performSearch(query)
        }
    }

    fun searchNow() {
        searchJob?.cancel()
        val q = _uiState.value.query
        if (q.isBlank()) return
        searchJob = viewModelScope.launch { performSearch(q) }
    }

    private suspend fun performSearch(query: String) {
        _uiState.update { it.copy(isSearching = true) }
        val result = threadRepository.searchLocal(query)
        pushRecent(query)
        _uiState.update {
            it.copy(
                isSearching = false,
                result = result,
                recentQueries = recent.toList()
            )
        }
    }

    fun searchRemote(nextPage: Boolean = false) {
        val query = _uiState.value.query.trim()
        if (query.isEmpty() || _uiState.value.remoteLoading) return
        val page = if (nextPage) _uiState.value.remotePage + 1 else 1
        remoteJob?.cancel()
        _uiState.update { it.copy(remoteLoading = true, remoteError = null) }
        remoteJob = viewModelScope.launch {
            try {
                val hits = withContext(Dispatchers.IO) {
                    val url = "https://xds.wol.moe/api/v1/search".toHttpUrl().newBuilder()
                        .addQueryParameter("q", query).addQueryParameter("page", page.toString())
                        .addQueryParameter("size", "20").build()
                    val request = Request.Builder().url(url).header("User-Agent", "MiooDao/1.1.5").build()
                    searchClient.newCall(request).execute().use { response ->
                        val json = JSONObject(response.body?.string().orEmpty())
                        if (!response.isSuccessful) {
                            val detail = json.optString("detail").takeIf { it.isNotBlank() }
                                ?: "HTTP ${response.code}"
                            throw IOException(detail)
                        }
                        val results = json.optJSONArray("results") ?: throw IOException("搜索服务返回格式异常")
                        buildList {
                            for (index in 0 until results.length()) {
                                val item = results.optJSONObject(index) ?: continue
                                val isReply = item.optString("kind") == "reply"
                                val postId = item.optString("id")
                                val threadId = if (isReply) item.optString("thread_id") else postId
                                if (threadId.isBlank() || threadId == "null") continue
                                add(RemoteSearchHit(postId, threadId,
                                    if (isReply) item.optString("thread_title") else item.optString("title"),
                                    item.optString("content"), isReply))
                            }
                        }
                    }
                }
                if (_uiState.value.query.trim() == query) {
                    pushRecent(query)
                    _uiState.update { it.copy(remoteLoading = false, remoteSearched = true,
                        remoteHits = if (nextPage) it.remoteHits + hits else hits,
                        remotePage = page, remoteHasMore = hits.size == 20, recentQueries = recent.toList()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_uiState.value.query.trim() == query) _uiState.update {
                    it.copy(remoteLoading = false, remoteSearched = true, remoteError = e.message ?: "搜索失败，请重试")
                }
            }
        }
    }

    private fun pushRecent(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        recent.remove(q)
        recent.addFirst(q)
        while (recent.size > 12) recent.removeLast()
    }

    fun selectEngine(engine: XdWebSearch.Engine) {
        _uiState.update { it.copy(preferredEngine = engine) }
    }

    /** Open browser with site: restricted engine search. */
    fun openSiteSearch(engine: XdWebSearch.Engine = _uiState.value.preferredEngine) {
        val q = _uiState.value.query.trim()
        if (q.isEmpty()) return
        pushRecent(q)
        _uiState.update {
            it.copy(
                preferredEngine = engine,
                recentQueries = recent.toList(),
                webSearchUrl = XdWebSearch.siteSearchUrl(q, engine)
            )
        }
    }

    fun consumeWebSearchUrl() {
        _uiState.update { it.copy(webSearchUrl = null) }
    }

    fun applyRecent(query: String) {
        _uiState.update { it.copy(query = query) }
        onQueryChange(query)
    }
}
