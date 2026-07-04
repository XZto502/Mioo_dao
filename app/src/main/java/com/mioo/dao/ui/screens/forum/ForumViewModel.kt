package com.mioo.dao.ui.screens.forum

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
import com.mioo.dao.data.repository.ThreadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ForumUiState(
    val threads: List<Thread> = emptyList(),
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
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    var forumId: String by mutableStateOf("-1")
        private set

    private val _uiState = MutableStateFlow(ForumUiState())
    val uiState: StateFlow<ForumUiState> = _uiState.asStateFlow()

    private var currentPage = 1

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Read last visited board
            val savedId = settingsDataStore.getLastForumId() ?: "-1"
            val savedName = settingsDataStore.getLastForumName() ?: "时间线"
            forumId = savedId
            _uiState.update { it.copy(currentForumName = savedName) }

            // Load data
            loadForumGroups()
            refresh()
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
        _uiState.update { it.copy(currentForumName = name) }
        
        viewModelScope.launch {
            settingsDataStore.saveLastForum(id, name)
        }
        
        refresh()
    }

    fun loadNextPage() {
        val currentState = _uiState.value
        if (currentState.isLoading || currentState.isLastPage) return

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val flow = if (forumId == "-1") {
                threadRepository.getTimeline("1", currentPage)
            } else {
                threadRepository.getThreads(forumId, currentPage)
            }

            flow.collect { response ->
                when (response) {
                    is XdResponse.Success -> {
                        val newThreads = response.data
                        _uiState.update { state ->
                            val combinedList = state.threads + newThreads
                            state.copy(
                                threads = combinedList,
                                isLoading = false,
                                isLastPage = newThreads.isEmpty(),
                                errorMessage = null
                            )
                        }
                        if (newThreads.isNotEmpty()) {
                            currentPage++
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
        }
    }

    fun refresh() {
        currentPage = 1
        _uiState.update { it.copy(isRefreshing = true, isLastPage = false, threads = emptyList()) }
        
        viewModelScope.launch {
            val flow = if (forumId == "-1") {
                threadRepository.getTimeline("1", currentPage)
            } else {
                threadRepository.getThreads(forumId, currentPage)
            }

            flow.collect { response ->
                when (response) {
                    is XdResponse.Success -> {
                        val freshThreads = response.data
                        _uiState.update { state ->
                            state.copy(
                                threads = freshThreads,
                                isRefreshing = false,
                                isLoading = false,
                                isLastPage = freshThreads.isEmpty(),
                                errorMessage = null
                            )
                        }
                        if (freshThreads.isNotEmpty()) {
                            currentPage++
                        }
                    }
                    is XdResponse.Error -> {
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                isLoading = false,
                                errorMessage = "Failed to refresh: ${response.message}"
                            )
                        }
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
