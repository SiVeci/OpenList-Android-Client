package io.openlist.client.feature.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.domain.ShareRepository
import io.openlist.client.core.model.Share
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShareListUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val shares: List<Share> = emptyList(),
    val searchQuery: String = "",
    val statusFilter: ShareStatusFilter = ShareStatusFilter.ALL,
    val errorMessage: String? = null,
) {
    val visibleShares: List<Share>
        get() = shares.filterForShareList(searchQuery, statusFilter)
}

enum class ShareStatusFilter(val label: String) {
    ALL("全部"),
    ENABLED("启用"),
    DISABLED("停用"),
}

@HiltViewModel
class ShareListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val shareRepository: ShareRepository,
) : ViewModel() {

    private val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow(ShareListUiState())
    val uiState: StateFlow<ShareListUiState> = _uiState.asStateFlow()

    init {
        // Cache-first (§18.1): show whatever's local instantly, then a
        // network refresh overwrites it below.
        shareRepository.observeShares(instanceId)
            .onEach { shares -> _uiState.update { it.copy(shares = shares) } }
            .launchIn(viewModelScope)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            when (val result = shareRepository.listShares(instanceId)) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onStatusFilterChange(filter: ShareStatusFilter) {
        _uiState.update { it.copy(statusFilter = filter) }
    }
}

internal fun List<Share>.filterForShareList(
    query: String,
    statusFilter: ShareStatusFilter,
): List<Share> {
    val normalizedQuery = query.trim()
    return filter { share ->
        val matchesStatus = when (statusFilter) {
            ShareStatusFilter.ALL -> true
            ShareStatusFilter.ENABLED -> share.enabled
            ShareStatusFilter.DISABLED -> !share.enabled
        }
        val matchesQuery = normalizedQuery.isBlank() ||
            share.name.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
            share.paths.any { it.contains(normalizedQuery, ignoreCase = true) } ||
            share.id.contains(normalizedQuery, ignoreCase = true)
        matchesStatus && matchesQuery
    }
}
