package io.openlist.client.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.domain.AdminUserRepository
import io.openlist.client.core.model.AdminUserSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Users Tab UI state (v0.5_EXECUTION_PLAN.md §11 S3-T2). A single page is
 * loaded at a time -- there is no infinite-scroll pagination in this Sprint
 * (no precedent for it anywhere in the app, see IMPLEMENTATION_LOG decision
 * note); [hasNextPage] drives a simple "加载更多" affordance instead.
 */
data class AdminUserListUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val users: List<AdminUserSummary> = emptyList(),
    val page: Int = 1,
    val total: Long = 0,
    val errorMessage: String? = null,
    val selectedUser: AdminUserSummary? = null,
)

/**
 * ViewModel for the Users Tab, owned by [io.openlist.client.feature.admin.AdminViewModel]'s
 * parent scope -- but kept as its own Hilt ViewModel (scoped to the Tab
 * composable's own `hiltViewModel()` call) since the gating/overview state in
 * `AdminViewModel` has nothing to do with paginated user rows. [instanceId] is
 * threaded in explicitly (not read from SavedStateHandle) because this
 * ViewModel is created from within the already-gated ALLOWED branch of
 * [io.openlist.client.feature.admin.AdminHostScreen], which already knows the
 * instanceId -- no separate nav route/args exist for the Users Tab (P-502:
 * "用户/存储/驱动详情用 BottomSheet 不加路由").
 */
@HiltViewModel
class AdminUserListViewModel @Inject constructor(
    private val adminUserRepository: AdminUserRepository,
) : ViewModel() {

    private var instanceId: String? = null

    private val _uiState = MutableStateFlow(AdminUserListUiState())
    val uiState: StateFlow<AdminUserListUiState> = _uiState.asStateFlow()

    /** Idempotent per-instance init -- safe to call every time the Users Tab
     * composable enters composition (e.g. via `LaunchedEffect(instanceId)`). */
    fun bind(instanceId: String) {
        if (this.instanceId == instanceId) return
        this.instanceId = instanceId
        _uiState.value = AdminUserListUiState()
        loadFirstPage()
    }

    private fun loadFirstPage() {
        val id = instanceId ?: return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = adminUserRepository.getUsers(id, page = 1, forceRefresh = false)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, users = result.data.users, page = 1, total = result.data.total)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /** Pull-to-refresh: always [forceRefresh] bypassing the 1-minute admin_cache
     * TTL (PRD §13.1 "用户下拉刷新必须强制请求远程"), resets back to page 1. */
    fun refresh() {
        val id = instanceId ?: return
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = adminUserRepository.getUsers(id, page = 1, forceRefresh = true)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, users = result.data.users, page = 1, total = result.data.total)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isRefreshing = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    val hasNextPage: Boolean
        get() = _uiState.value.users.size.toLong() < _uiState.value.total

    fun loadNextPage() {
        val id = instanceId ?: return
        val state = _uiState.value
        if (state.isLoadingMore || !hasNextPage) return
        val nextPage = state.page + 1
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            when (val result = adminUserRepository.getUsers(id, page = nextPage, forceRefresh = false)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoadingMore = false, users = it.users + result.data.users, page = nextPage, total = result.data.total)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoadingMore = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun selectUser(user: AdminUserSummary) {
        _uiState.update { it.copy(selectedUser = user) }
    }

    fun dismissUserDetail() {
        _uiState.update { it.copy(selectedUser = null) }
    }
}
