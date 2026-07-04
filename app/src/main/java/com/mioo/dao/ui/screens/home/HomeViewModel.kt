package com.mioo.dao.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class HomeUiState(
    val isLoading: Boolean = false,
    val forumGroups: List<ForumGroup> = emptyList(),
    val timelineThreads: List<Thread> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val forumRepository: ForumRepository,
    private val threadRepository: ThreadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshData()
    }

    fun refreshData() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            // Fetch forum list
            forumRepository.getForumList().collect { response ->
                when (response) {
                    is XdResponse.Success -> {
                        _uiState.update { it.copy(forumGroups = response.data) }
                    }
                    is XdResponse.Error -> {
                        _uiState.update { it.copy(errorMessage = response.message) }
                    }
                }
            }
            // Fetch timeline (default timeline board ID is usually "1")
            threadRepository.getTimeline("1", 1).collect { response ->
                when (response) {
                    is XdResponse.Success -> {
                        _uiState.update { it.copy(timelineThreads = response.data, isLoading = false) }
                    }
                    is XdResponse.Error -> {
                        _uiState.update { it.copy(errorMessage = response.message, isLoading = false) }
                    }
                }
            }
        }
    }
}
