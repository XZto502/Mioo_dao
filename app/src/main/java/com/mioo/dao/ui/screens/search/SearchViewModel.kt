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
import javax.inject.Inject

@Immutable
data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val result: LocalSearchResult = LocalSearchResult(),
    val recentQueries: List<String> = emptyList(),
    val preferredEngine: XdWebSearch.Engine = XdWebSearch.Engine.GOOGLE
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val threadRepository: ThreadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private val recent = ArrayDeque<String>()

    fun onQueryChange(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(query = query, isSearching = false, result = LocalSearchResult()) }
            return
        }
        _uiState.update { it.copy(query = query) }
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

    /** Build site: restricted search URL & update recent queries. */
    fun prepareSiteSearchUrl(engine: XdWebSearch.Engine = _uiState.value.preferredEngine): String? {
        val q = _uiState.value.query.trim()
        if (q.isEmpty()) return null
        pushRecent(q)
        _uiState.update {
            it.copy(
                preferredEngine = engine,
                recentQueries = recent.toList()
            )
        }
        return XdWebSearch.siteSearchUrl(q, engine)
    }

    fun applyRecent(query: String) {
        onQueryChange(query)
    }
}
