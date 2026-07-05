package io.openlist.client.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.domain.AdminSettingsRepository
import io.openlist.client.core.model.AdminSettingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which list the Settings Tab is currently displaying (PRD §12.7 "查看默认设置"
 * entry) -- a toggle between the live settings list and the read-only default
 * values list, rather than a separate route/BottomSheet: both lists share the
 * exact same row rendering/masking rules, so a toggle is the simplest UI shape
 * that satisfies "a way to view default settings too" without duplicating the
 * list composable. */
enum class AdminSettingsView { CURRENT, DEFAULT }

/** Settings Tab UI state (v0.5_EXECUTION_PLAN.md §11 S7-T2). [items] always
 * holds whichever of [view]'s two lists was last successfully loaded --
 * switching [view] re-triggers a load for that view if it hasn't been loaded
 * yet, and keeps showing the other view's last-loaded data momentarily
 * cleared while the new one loads (simplicity: no dual-cache of both lists
 * held in memory at once beyond what each load call naturally returns). */
data class AdminSettingsUiState(
    val view: AdminSettingsView = AdminSettingsView.CURRENT,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val items: List<AdminSettingItem> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Settings Tab ViewModel (v0.5_EXECUTION_PLAN.md §11 S7-T2). Same "bound by
 * an explicit instanceId call, not SavedStateHandle" shape as
 * [AdminStorageListViewModel]/[AdminTaskListViewModel] -- see their KDocs for
 * why. Read-only: no save/delete/reset-token method exists here at all (PRD
 * §12.7 "不提供原生保存、删除、重置 Token") -- there is nothing in this class a UI
 * could wire up to mutate a setting even by mistake.
 */
@HiltViewModel
class AdminSettingsListViewModel @Inject constructor(
    private val adminSettingsRepository: AdminSettingsRepository,
) : ViewModel() {

    private var instanceId: String? = null
    private val loadedViews = mutableSetOf<AdminSettingsView>()

    private val _uiState = MutableStateFlow(AdminSettingsUiState())
    val uiState: StateFlow<AdminSettingsUiState> = _uiState.asStateFlow()

    fun bind(instanceId: String) {
        if (this.instanceId == instanceId) return
        this.instanceId = instanceId
        loadedViews.clear()
        _uiState.value = AdminSettingsUiState()
        load(forceRefresh = false)
    }

    /** Pull-to-refresh: always bypasses the 5-minute admin_cache TTL for the
     * currently-selected [AdminSettingsUiState.view]. */
    fun refresh() = load(forceRefresh = true)

    /** Switching views only re-fetches if that view hasn't been loaded yet in
     * this binding -- switching back and forth doesn't re-hit the network
     * every time (the repository's own 5-minute cache would mostly absorb
     * that anyway, but this avoids even the cache round-trip/loading flash). */
    fun selectView(view: AdminSettingsView) {
        if (_uiState.value.view == view) return
        _uiState.update { it.copy(view = view) }
        if (view !in loadedViews) {
            load(forceRefresh = false)
        }
    }

    private fun load(forceRefresh: Boolean) {
        val id = instanceId ?: return
        val view = _uiState.value.view
        _uiState.update {
            if (forceRefresh) it.copy(isRefreshing = true, errorMessage = null) else it.copy(isLoading = true, errorMessage = null)
        }
        viewModelScope.launch {
            val result = when (view) {
                AdminSettingsView.CURRENT -> adminSettingsRepository.getSettings(id, group = null, forceRefresh = forceRefresh)
                AdminSettingsView.DEFAULT -> adminSettingsRepository.getDefaultSettings(id, group = null)
            }
            when (result) {
                is ApiResult.Success -> {
                    loadedViews += view
                    _uiState.update {
                        // Only apply if the view hasn't changed again while this
                        // load was in flight (avoids a stale response for the
                        // previously-selected view clobbering the new one).
                        if (it.view == view) it.copy(isLoading = false, isRefreshing = false, items = result.data) else it
                    }
                }
                is ApiResult.Failure -> _uiState.update {
                    if (it.view == view) it.copy(isLoading = false, isRefreshing = false, errorMessage = result.error.toUserMessage()) else it
                }
            }
        }
    }
}
