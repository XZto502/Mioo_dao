package com.mioo.dao.ui.screens.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mioo.dao.data.model.ForumGroup
import com.mioo.dao.data.model.Thread
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.data.repository.ForumRepository
import com.mioo.dao.data.repository.SettingsRepository
import com.mioo.dao.data.repository.ThreadRepository
import com.mioo.dao.ui.components.ThreadListItem
import com.mioo.dao.ui.components.toFilteredThreadListItems
import com.mioo.dao.utils.KeywordMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Immutable
data class HomeUiState(
    val isLoading: Boolean = false,
    val forumGroups: List<ForumGroup> = emptyList(),
    val timelineThreads: List<Thread> = emptyList(),
    val displayItems: List<ThreadListItem> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val forumRepository: ForumRepository,
    private val threadRepository: ThreadRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var blockedThreads: Set<String> = emptySet()
    private var blockedUsers: Set<String> = emptySet()
    private var keywordMatcher: KeywordMatcher = KeywordMatcher.EMPTY

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .map { Triple(it.blockedThreads, it.blockedUsers, it.blockedKeywords) }
                .distinctUntilChanged()
                .collect { (threads, users, keywords) ->
                    blockedThreads = threads.toHashSet()
                    blockedUsers = users.toHashSet()
                    keywordMatcher = KeywordMatcher.build(keywords)
                    rebuildDisplayItems()
                }
        }
        refreshData()
    }

    fun refreshData() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            // Load forum list and timeline in parallel for faster home paint
            coroutineScope {
                val forumsDeferred = async {
                    var result: XdResponse<List<ForumGroup>>? = null
                    forumRepository.getForumList().collect { result = it }
                    result
                }
                val timelineDeferred = async {
                    var result: XdResponse<List<Thread>>? = null
                    threadRepository.getTimeline("1", 1).collect { result = it }
                    result
                }

                val forumResponse = forumsDeferred.await()
                val timelineResponse = timelineDeferred.await()

                var forumGroups = _uiState.value.forumGroups
                var error: String? = null

                when (forumResponse) {
                    is XdResponse.Success -> forumGroups = forumResponse.data
                    is XdResponse.Error -> error = forumResponse.message
                    null -> error = "板块列表加载失败"
                }

                when (timelineResponse) {
                    is XdResponse.Success -> {
                        val threads = timelineResponse.data
                        val items = withContext(Dispatchers.Default) {
                            threads.toFilteredThreadListItems(
                                blockedThreads, blockedUsers, keywordMatcher
                            )
                        }
                        _uiState.update {
                            it.copy(
                                forumGroups = forumGroups,
                                timelineThreads = threads,
                                displayItems = items,
                                isLoading = false,
                                errorMessage = error
                            )
                        }
                        threadRepository.smartPreloadThreads(threads)
                    }
                    is XdResponse.Error -> {
                        _uiState.update {
                            it.copy(
                                forumGroups = forumGroups,
                                isLoading = false,
                                errorMessage = timelineResponse.message.ifBlank { error }
                            )
                        }
                    }
                    null -> {
                        _uiState.update {
                            it.copy(
                                forumGroups = forumGroups,
                                isLoading = false,
                                errorMessage = error ?: "时间线加载失败"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun rebuildDisplayItems() {
        viewModelScope.launch {
            val threads = _uiState.value.timelineThreads
            if (threads.isEmpty()) return@launch
            val items = withContext(Dispatchers.Default) {
                threads.toFilteredThreadListItems(blockedThreads, blockedUsers, keywordMatcher)
            }
            _uiState.update { it.copy(displayItems = items) }
        }
    }
}
