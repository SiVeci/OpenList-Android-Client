package io.openlist.client.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.domain.AdminStorageRepository
import io.openlist.client.core.model.AdminStorageSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Storage Tab UI state (v0.5_EXECUTION_PLAN.md §11 S3-T4, read-only part
 * only -- enable/disable/reload-all are S4 scope). */
data class AdminStorageListUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val storages: List<AdminStorageSummary> = emptyList(),
    val errorMessage: String? = null,
    val selectedStorage: AdminStorageSummary? = null,
)

/** Same "bound by an explicit instanceId call, not SavedStateHandle" shape as
 * [AdminUserListViewModel] -- see its KDoc for why. */
@HiltViewModel
class AdminStorageListViewModel @Inject constructor(
    private val adminStorageRepository: AdminStorageRepository,
) : ViewModel() {

    private var instanceId: String? = null

    private val _uiState = MutableStateFlow(AdminStorageListUiState())
    val uiState: StateFlow<AdminStorageListUiState> = _uiState.asStateFlow()

    fun bind(instanceId: String) {
        if (this.instanceId == instanceId) return
        this.instanceId = instanceId
        _uiState.value = AdminStorageListUiState()
        load(forceRefresh = false)
    }

    private fun load(forceRefresh: Boolean) {
        val id = instanceId ?: return
        _uiState.update {
            if (forceRefresh) it.copy(isRefreshing = true, errorMessage = null) else it.copy(isLoading = true, errorMessage = null)
        }
        viewModelScope.launch {
            when (val result = adminStorageRepository.getStorages(id, forceRefresh = forceRefresh)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, storages = result.data.storages)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /** Pull-to-refresh: always [forceRefresh] bypassing the 30s admin_cache TTL. */
    fun refresh() = load(forceRefresh = true)

    fun selectStorage(storage: AdminStorageSummary) {
        _uiState.update { it.copy(selectedStorage = storage) }
    }

    fun dismissStorageDetail() {
        _uiState.update { it.copy(selectedStorage = null) }
    }
}
