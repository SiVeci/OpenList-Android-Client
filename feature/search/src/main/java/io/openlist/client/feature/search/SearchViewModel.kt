package io.openlist.client.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.domain.SearchRepository
import io.openlist.client.core.model.SearchHistoryItem
import io.openlist.client.core.model.SearchResultItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

enum class SearchScope { CURRENT_DIR, GLOBAL }

data class SearchUiState(
    val scopePath: String,
    val query: String = "",
    val scope: SearchScope = SearchScope.CURRENT_DIR,
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<SearchResultItem> = emptyList(),
    val history: List<SearchHistoryItem> = emptyList(),
    val errorMessage: String? = null,
    val notAvailable: Boolean = false,
)

/** P5: submits only on IME "search"/explicit action, never on every keystroke. */
@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val instanceId: String = checkNotNull(savedStateHandle["instanceId"])
    private val initialPath: String = savedStateHandle.get<String>("path")
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        ?: "/"

    private val _uiState = MutableStateFlow(SearchUiState(scopePath = initialPath))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        searchRepository.observeSearchHistory(instanceId)
            .onEach { history -> _uiState.update { it.copy(history = history) } }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun onScopeChange(scope: SearchScope) {
        _uiState.update { it.copy(scope = scope) }
        if (_uiState.value.hasSearched) search()
    }

    fun search() {
        val keyword = _uiState.value.query.trim()
        if (keyword.isEmpty()) return
        val scopePathForRequest = requestScopePath()
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, errorMessage = null, notAvailable = false) }
            searchRepository.saveSearchKeyword(instanceId, keyword, scopePathForRequest)
            when (val result = searchRepository.search(instanceId, keyword, scopePathForRequest)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isSearching = false, hasSearched = true, results = result.data)
                }
                is ApiResult.Failure -> _uiState.update {
                    if (result.error == DomainError.SearchNotAvailable) {
                        it.copy(isSearching = false, hasSearched = true, notAvailable = true, results = emptyList())
                    } else {
                        it.copy(isSearching = false, hasSearched = true, errorMessage = result.error.toUserMessage())
                    }
                }
            }
        }
    }

    fun searchFromHistory(item: SearchHistoryItem) {
        _uiState.update {
            it.copy(
                query = item.keyword,
                scope = if (item.scopePath == null) SearchScope.GLOBAL else SearchScope.CURRENT_DIR,
            )
        }
        search()
    }

    fun deleteHistoryItem(item: SearchHistoryItem) {
        viewModelScope.launch { searchRepository.deleteSearchKeyword(instanceId, item.keyword, item.scopePath) }
    }

    fun clearHistory() {
        viewModelScope.launch { searchRepository.clearSearchHistory(instanceId) }
    }

    /** null (backend "全部文件") for GLOBAL, current directory path otherwise. */
    private fun requestScopePath(): String? =
        if (_uiState.value.scope == SearchScope.GLOBAL) null else _uiState.value.scopePath
}
