package io.openlist.client.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.domain.AdminTaskRepository
import io.openlist.client.core.model.AdminTask
import io.openlist.client.core.model.UnifiedTaskStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The 7 backend task types + their Chinese labels (PRD §12.5, V-505). */
enum class AdminTaskType(val apiValue: String, val label: String) {
    UPLOAD("upload", "上传"),
    COPY("copy", "复制"),
    MOVE("move", "移动"),
    OFFLINE_DOWNLOAD("offline_download", "离线下载"),
    OFFLINE_DOWNLOAD_TRANSFER("offline_download_transfer", "离线转存"),
    DECOMPRESS("decompress", "解压"),
    DECOMPRESS_UPLOAD("decompress_upload", "解压上传"),
}

enum class AdminTaskBucket { UNDONE, DONE }

/** Which confirmation is currently open (PRD §8.5: cancel/retry/delete all
 * require two-step confirm). */
sealed class AdminTaskDialog {
    data class CancelConfirm(val task: AdminTask) : AdminTaskDialog()
    data class RetryConfirm(val task: AdminTask) : AdminTaskDialog()
    data class DeleteConfirm(val task: AdminTask) : AdminTaskDialog()

    /** Batch operations (v1.0 S5-T2/T3, DEC-603 subset A) — always scoped to
     * one [type], never "全部类型", since the backend endpoint itself is
     * per-type (there is no "all types" batch call). */
    data class ClearDoneConfirm(val type: AdminTaskType) : AdminTaskDialog()
    data class ClearSucceededConfirm(val type: AdminTaskType) : AdminTaskDialog()
    data class RetryFailedConfirm(val type: AdminTaskType) : AdminTaskDialog()
}

data class AdminTaskListUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val bucket: AdminTaskBucket = AdminTaskBucket.UNDONE,
    /** `null` means "全部类型" (all 7 types). */
    val selectedType: AdminTaskType? = null,
    val tasks: List<AdminTask> = emptyList(),
    val errorMessage: String? = null,
    val pollErrorMessage: String? = null,
    val dialog: AdminTaskDialog? = null,
    val dialogLoading: Boolean = false,
    val dialogError: String? = null,
    val snackbarMessage: String? = null,
) {
    val visibleTasks: List<AdminTask>
        get() = tasks
            .filter { if (bucket == AdminTaskBucket.DONE) it.isDone else !it.isDone }
            .filter { selectedType == null || it.taskType == selectedType.apiValue }

    /** Drives S5-T3's polling gate: only while at least one *undone* task is
     * actually RUNNING/PENDING (mirrors [io.openlist.client.feature.task
     * .TaskCenterUiState.hasRunningRemote]'s same two-state definition) --
     * an Errored/Failing task is still technically "undone" server-side but
     * isn't actively progressing, so it doesn't keep the poll alive. */
    val hasRunningTasks: Boolean
        get() = tasks.any { !it.isDone && (it.state == UnifiedTaskStatus.RUNNING || it.state == UnifiedTaskStatus.PENDING) }
}

/**
 * Admin Tasks Tab ViewModel (v0.5_EXECUTION_PLAN.md §11 S5-T2/T3). Same
 * "bound by an explicit instanceId call" shape as [AdminStorageListViewModel]/
 * [AdminUserListViewModel] -- this ViewModel's own lifetime is tied to the
 * `admin/{instanceId}` nav back-stack entry (via `hiltViewModel()`), which
 * outlives individual Tab switches; polling is deliberately **not**
 * self-started from `init`/`bind` for that reason (unlike [io.openlist.client
 * .feature.task.TaskCenterViewModel], whose own lifetime *is* the screen's
 * visibility) -- [startPolling]/[stopPolling] are instead driven explicitly by
 * [io.openlist.client.feature.admin.AdminTaskTab]'s composition lifecycle
 * (`LaunchedEffect`/`DisposableEffect`), so polling truly starts/stops with
 * the Tab's on-screen presence, not with this ViewModel's.
 */
@HiltViewModel
class AdminTaskListViewModel @Inject constructor(
    private val adminTaskRepository: AdminTaskRepository,
) : ViewModel() {

    private var instanceId: String? = null
    private var pollingJob: Job? = null
    private var consecutiveFailures = 0

    private val _uiState = MutableStateFlow(AdminTaskListUiState())
    val uiState: StateFlow<AdminTaskListUiState> = _uiState.asStateFlow()

    fun bind(instanceId: String) {
        if (this.instanceId == instanceId) return
        this.instanceId = instanceId
        _uiState.value = AdminTaskListUiState()
        adminTaskRepository.observeAdminTasks(instanceId)
            .onEach { tasks -> _uiState.update { it.copy(tasks = tasks) } }
            .launchIn(viewModelScope)
        refresh()
    }

    fun selectBucket(bucket: AdminTaskBucket) {
        _uiState.update { it.copy(bucket = bucket) }
        // Done tasks are pulled on demand (R-505) -- only when the user is
        // actually looking at a specific type's done list, never "all 7" at
        // once (that would defeat the whole point of not polling done lists).
        val type = _uiState.value.selectedType
        if (bucket == AdminTaskBucket.DONE && type != null) {
            refreshDoneForType(type)
        }
    }

    fun selectType(type: AdminTaskType?) {
        _uiState.update { it.copy(selectedType = type) }
        if (_uiState.value.bucket == AdminTaskBucket.DONE && type != null) {
            refreshDoneForType(type)
        }
    }

    /** Pull-to-refresh: re-fetches undone (all 7 types) always, plus the
     * currently-selected type's done list if the user is on the Done view. */
    fun refresh() {
        val id = instanceId ?: return
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = adminTaskRepository.refreshUndone(id)) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = result.error.toUserMessage())
                }
            }
            _uiState.value.selectedType?.let { type ->
                if (_uiState.value.bucket == AdminTaskBucket.DONE) refreshDoneForType(type)
            }
        }
    }

    private fun refreshDoneForType(type: AdminTaskType) {
        val id = instanceId ?: return
        viewModelScope.launch {
            when (val result = adminTaskRepository.refreshDone(id, type.apiValue)) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.toUserMessage()) }
            }
        }
    }

    // ---- Polling (S5-T3) ----------------------------------------------------

    /** Idempotent: a second call while already polling is a no-op. Resets the
     * consecutive-failure counter and clears any stale poll error so a fresh
     * entry into the Tab always gets a clean slate. */
    fun startPolling() {
        if (pollingJob?.isActive == true) return
        consecutiveFailures = 0
        _uiState.update { it.copy(pollErrorMessage = null) }
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val id = instanceId ?: continue
                if (!_uiState.value.hasRunningTasks) continue
                when (val result = adminTaskRepository.refreshUndone(id)) {
                    is ApiResult.Success -> consecutiveFailures = 0
                    is ApiResult.Failure -> {
                        consecutiveFailures++
                        if (consecutiveFailures >= MAX_CONSECUTIVE_POLL_FAILURES) {
                            _uiState.update { it.copy(pollErrorMessage = result.error.toUserMessage()) }
                            stopPolling()
                        }
                    }
                }
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    // ---- Cancel/retry/delete confirmations (PRD §8.5) -----------------------

    fun requestCancel(task: AdminTask) {
        _uiState.update { it.copy(dialog = AdminTaskDialog.CancelConfirm(task), dialogError = null) }
    }

    fun requestRetry(task: AdminTask) {
        _uiState.update { it.copy(dialog = AdminTaskDialog.RetryConfirm(task), dialogError = null) }
    }

    fun requestDelete(task: AdminTask) {
        _uiState.update { it.copy(dialog = AdminTaskDialog.DeleteConfirm(task), dialogError = null) }
    }

    /** Batch action buttons only show when a specific type is selected (no
     * "全部类型" batch call exists), so these three always have a non-null
     * [AdminTaskListUiState.selectedType] to read here. */
    fun requestClearDone() {
        val type = _uiState.value.selectedType ?: return
        _uiState.update { it.copy(dialog = AdminTaskDialog.ClearDoneConfirm(type), dialogError = null) }
    }

    fun requestClearSucceeded() {
        val type = _uiState.value.selectedType ?: return
        _uiState.update { it.copy(dialog = AdminTaskDialog.ClearSucceededConfirm(type), dialogError = null) }
    }

    fun requestRetryFailed() {
        val type = _uiState.value.selectedType ?: return
        _uiState.update { it.copy(dialog = AdminTaskDialog.RetryFailedConfirm(type), dialogError = null) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialog = null, dialogLoading = false, dialogError = null) }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * Dispatches to the repository call matching the open dialog. Per V-505/
     * the interface KDoc, none of these are locally gated on the task's
     * cached state -- whatever the dialog is open for is sent to the backend
     * exactly as confirmed, and the backend's own response (success or a
     * verbatim error message) is authoritative.
     */
    fun confirmDialog() {
        val id = instanceId ?: return
        val dialog = _uiState.value.dialog ?: return
        val call: suspend () -> ApiResult<Unit> = when (dialog) {
            is AdminTaskDialog.CancelConfirm -> { { adminTaskRepository.cancelTask(id, dialog.task.taskType, dialog.task.id) } }
            is AdminTaskDialog.RetryConfirm -> { { adminTaskRepository.retryTask(id, dialog.task.taskType, dialog.task.id) } }
            is AdminTaskDialog.DeleteConfirm -> { { adminTaskRepository.deleteTaskRecord(id, dialog.task.taskType, dialog.task.id) } }
            is AdminTaskDialog.ClearDoneConfirm -> { { adminTaskRepository.clearDone(id, dialog.type.apiValue) } }
            is AdminTaskDialog.ClearSucceededConfirm -> { { adminTaskRepository.clearSucceeded(id, dialog.type.apiValue) } }
            is AdminTaskDialog.RetryFailedConfirm -> { { adminTaskRepository.retryFailed(id, dialog.type.apiValue) } }
        }
        val successMessage = when (dialog) {
            is AdminTaskDialog.CancelConfirm -> "已取消"
            is AdminTaskDialog.RetryConfirm -> "已提交重试"
            is AdminTaskDialog.DeleteConfirm -> "已删除"
            is AdminTaskDialog.ClearDoneConfirm -> "已清理「${dialog.type.label}」的已完成记录"
            is AdminTaskDialog.ClearSucceededConfirm -> "已清理「${dialog.type.label}」的已成功记录"
            is AdminTaskDialog.RetryFailedConfirm -> "已提交重试「${dialog.type.label}」的失败任务"
        }
        _uiState.update { it.copy(dialogLoading = true, dialogError = null) }
        viewModelScope.launch {
            when (val result = call()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(dialog = null, dialogLoading = false, snackbarMessage = successMessage)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(dialogLoading = false, dialogError = result.error.toUserMessage())
                }
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 4_000L

        // No existing shared constant for this: TaskCenterViewModel's own 4s
        // remote poll never stops on repeated failure at all (it just retries
        // forever). PRD §14.3.4 requires a stop-on-repeated-failure behavior
        // that has no precedent elsewhere in the app, so 3 is a new,
        // S5-local decision (reasonable "a few tries, then give up" default).
        const val MAX_CONSECUTIVE_POLL_FAILURES = 3
    }
}
