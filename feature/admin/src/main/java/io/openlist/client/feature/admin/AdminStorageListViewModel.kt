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

/** Which confirmation is currently open on the Storage Tab
 * (v0.5_EXECUTION_PLAN.md §11 S4-T3, PRD §8.5): enable/reload-all use a
 * normal-style [io.openlist.client.core.designsystem.components.ConfirmDialog]
 * (PRD §8.5 only calls out disable-storage/clear-index as danger-style; the
 * reload-all row in PRD's own operations table (§12.4 item 7 / the
 * enable/disable/reload-all list) is listed alongside enable as "二次确认"
 * without the danger qualifier "重新加载全部存储应使用危险操作样式" appearing anywhere,
 * so it stays normal-style), disable uses the danger style. */
sealed class AdminStorageDialog {
    data class EnableConfirm(val storage: AdminStorageSummary) : AdminStorageDialog()
    data class DisableConfirm(val storage: AdminStorageSummary) : AdminStorageDialog()
    data object ReloadAllConfirm : AdminStorageDialog()
}

/** Independent loading/loaded/failed state for the "查看驱动信息" sheet
 * (PRD §12.4.8) — [driverName] is carried alongside so the sheet's title
 * stays stable across a retry. */
data class AdminDriverInfoUiState(
    val driverName: String,
    val state: AdminCardState<Map<String, Any?>>,
)

/** Storage Tab UI state (v0.5_EXECUTION_PLAN.md §11 S3-T4/S4-T3). */
data class AdminStorageListUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val storages: List<AdminStorageSummary> = emptyList(),
    val errorMessage: String? = null,
    val selectedStorage: AdminStorageSummary? = null,
    val dialog: AdminStorageDialog? = null,
    val dialogLoading: Boolean = false,
    val dialogError: String? = null,
    val snackbarMessage: String? = null,
    val driverInfo: AdminDriverInfoUiState? = null,
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

    // ---- Enable/disable/reload-all confirmations (S4-T3) ----

    fun requestEnable(storage: AdminStorageSummary) {
        _uiState.update { it.copy(dialog = AdminStorageDialog.EnableConfirm(storage), dialogError = null) }
    }

    fun requestDisable(storage: AdminStorageSummary) {
        _uiState.update { it.copy(dialog = AdminStorageDialog.DisableConfirm(storage), dialogError = null) }
    }

    fun requestReloadAll() {
        _uiState.update { it.copy(dialog = AdminStorageDialog.ReloadAllConfirm, dialogError = null) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialog = null, dialogLoading = false, dialogError = null) }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun confirmDialog() {
        when (val dialog = _uiState.value.dialog) {
            is AdminStorageDialog.EnableConfirm -> setEnabled(dialog.storage, enable = true)
            is AdminStorageDialog.DisableConfirm -> setEnabled(dialog.storage, enable = false)
            is AdminStorageDialog.ReloadAllConfirm -> reloadAll()
            null -> Unit
        }
    }

    /**
     * On success: dismiss the dialog, show a confirmation snackbar, and
     * force-refresh both the list and (if the sheet for this storage is
     * still open) the detail — never just optimistically flip a local flag
     * (brief requirement). On failure: dialog stays open with the backend's
     * message (`dialogError`), nothing else in [AdminStorageListUiState] is
     * touched at all, so the storage's previously-displayed state/detail is
     * left exactly as it was (PRD §13.2.5).
     */
    private fun setEnabled(storage: AdminStorageSummary, enable: Boolean) {
        val id = instanceId ?: return
        _uiState.update { it.copy(dialogLoading = true, dialogError = null) }
        viewModelScope.launch {
            val result = if (enable) {
                adminStorageRepository.enableStorage(id, storage.id)
            } else {
                adminStorageRepository.disableStorage(id, storage.id)
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            dialog = null,
                            dialogLoading = false,
                            snackbarMessage = if (enable) "启用成功" else "禁用成功",
                        )
                    }
                    refresh()
                    refreshSelectedDetail(storage.id)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(dialogLoading = false, dialogError = result.error.toUserMessage())
                }
            }
        }
    }

    /**
     * Success copy is deliberately "已提交" (submitted), never "已完成"
     * (completed) — the backend reload runs asynchronously in a background
     * goroutine and the client has no way to observe its completion
     * (V-503/PRD §13.2.2).
     */
    private fun reloadAll() {
        val id = instanceId ?: return
        _uiState.update { it.copy(dialogLoading = true, dialogError = null) }
        viewModelScope.launch {
            when (val result = adminStorageRepository.reloadAllStorages(id)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(dialog = null, dialogLoading = false, snackbarMessage = "重新加载已提交，正在后台进行")
                    }
                    refresh()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(dialogLoading = false, dialogError = result.error.toUserMessage())
                }
            }
        }
    }

    /** Re-fetches exactly the storage whose detail sheet may be open so it
     * reflects the just-applied enable/disable state, without a second full
     * list round-trip. No-ops if the sheet was dismissed or shows a
     * different storage in the meantime. */
    private fun refreshSelectedDetail(id: Int) {
        val instance = instanceId ?: return
        if (_uiState.value.selectedStorage?.id != id) return
        viewModelScope.launch {
            when (val result = adminStorageRepository.getStorage(instance, id)) {
                is ApiResult.Success -> _uiState.update {
                    if (it.selectedStorage?.id == id) it.copy(selectedStorage = result.data) else it
                }
                is ApiResult.Failure -> Unit // detail sheet keeps its last-known values; list refresh already covers the summary row
            }
        }
    }

    // ---- Driver info (read-only, S4-T4) ----

    fun viewDriverInfo(driver: String) {
        val id = instanceId ?: return
        _uiState.update { it.copy(driverInfo = AdminDriverInfoUiState(driver, AdminCardState.Loading)) }
        viewModelScope.launch {
            when (val result = adminStorageRepository.getDriverInfo(id, driver)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(driverInfo = AdminDriverInfoUiState(driver, AdminCardState.Loaded(result.data)))
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(driverInfo = AdminDriverInfoUiState(driver, AdminCardState.Failed(result.error.toUserMessage())))
                }
            }
        }
    }

    fun dismissDriverInfo() {
        _uiState.update { it.copy(driverInfo = null) }
    }
}
