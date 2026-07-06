package io.openlist.client.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.designsystem.components.DirectoryPickerContent
import io.openlist.client.core.designsystem.components.DirectoryPickerEntry
import io.openlist.client.core.domain.AdminIndexRepository
import io.openlist.client.core.domain.DirectoryPickerRepository
import io.openlist.client.core.model.AdminIndexProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which confirmation is currently open on the Index Tab (PRD §8.5: build/
 * update/stop require two-step confirm, clear requires the danger-style
 * confirm per PRD §8.5/§15.2 "清空索引...应使用危险操作样式"). */
sealed class AdminIndexDialog {
    data object BuildConfirm : AdminIndexDialog()
    data object UpdateConfirm : AdminIndexDialog()
    data object StopConfirm : AdminIndexDialog()
    data object ClearConfirm : AdminIndexDialog()
}

/** Index Tab UI state (v0.5_EXECUTION_PLAN.md §11 S6-T2/T3). [progress] is
 * `null` only before the very first load completes -- [isLoading] is the
 * three-state gate for that initial load ([io.openlist.client.core.designsystem
 * .components.LoadingState]/[io.openlist.client.core.designsystem.components
 * .EmptyState]/[io.openlist.client.core.designsystem.components.ErrorBar], same
 * shape as every other admin Tab), while later polls/operations update
 * [progress] in place without ever flipping [isLoading] back to `true`
 * (avoids flashing a full-screen spinner every 4s while the tab stays open).
 */
data class AdminIndexUiState(
    val isLoading: Boolean = true,
    val progress: AdminIndexProgress? = null,
    val errorMessage: String? = null,
    val pollErrorMessage: String? = null,
    val dialog: AdminIndexDialog? = null,
    val dialogLoading: Boolean = false,
    val dialogError: String? = null,
    val snackbarMessage: String? = null,
    /** Path "更新索引" will submit — v1.0 S5-T1, reuses the same directory
     * tree the admin's own session can browse (DirectoryPickerRepository is
     * the ordinary fs/list channel, not an admin-only one). Defaults to "/"
     * (DEC-504's original default, still offered as the common case). */
    val updatePath: String = "/",
    /** Non-null while the path picker sheet is open. */
    val pickerPath: String? = null,
    val pickerContent: DirectoryPickerContent = DirectoryPickerContent.Loading,
)

/**
 * Index Tab ViewModel (v0.5_EXECUTION_PLAN.md §11 S6-T2/T3). Same "bound by an
 * explicit instanceId call, not SavedStateHandle" shape as
 * [AdminStorageListViewModel]/[AdminTaskListViewModel] -- see their KDocs for
 * why. Polling mirrors [AdminTaskListViewModel]'s exact lifecycle shape
 * (independent implementation, not shared code -- per brief, `:feature:admin`
 * Tabs don't share a base class/mixin for this): [startPolling]/[stopPolling]
 * are driven explicitly by [AdminIndexTab]'s `DisposableEffect`, not
 * self-started from [bind], so polling truly starts/stops with the Tab's
 * on-screen presence.
 *
 * Polling interval: 4s, same literal [AdminTaskListViewModel] uses (for
 * consistency across the two admin Tabs that poll -- PRD only specifies a
 * 3-5s range, not an exact value). Only actually calls the network while
 * [AdminIndexProgress.isRunning] is true (checked against the last-known
 * [AdminIndexUiState.progress] on every tick, same "skip the network call
 * when nothing to observe" pattern [AdminTaskListViewModel.hasRunningTasks]
 * uses) and stops after [MAX_CONSECUTIVE_POLL_FAILURES] (3, same threshold
 * constant value as [AdminTaskListViewModel] for consistency) consecutive
 * failures rather than retrying forever.
 */
@HiltViewModel
class AdminIndexListViewModel @Inject constructor(
    private val adminIndexRepository: AdminIndexRepository,
    private val directoryPickerRepository: DirectoryPickerRepository,
) : ViewModel() {

    private var instanceId: String? = null
    private var pollingJob: Job? = null
    private var consecutiveFailures = 0

    private val _uiState = MutableStateFlow(AdminIndexUiState())
    val uiState: StateFlow<AdminIndexUiState> = _uiState.asStateFlow()

    fun bind(instanceId: String) {
        if (this.instanceId == instanceId) return
        this.instanceId = instanceId
        _uiState.value = AdminIndexUiState()
        refresh()
    }

    /** Used for both the initial load and pull-to-refresh/manual retry -- the
     * request itself is identical ([AdminIndexRepository.getProgress] is never
     * cached, PRD §13.1.4), only [AdminIndexUiState.isLoading] differs
     * depending on whether [AdminIndexUiState.progress] is already populated. */
    fun refresh() {
        val id = instanceId ?: return
        _uiState.update { if (it.progress == null) it.copy(isLoading = true, errorMessage = null) else it.copy(errorMessage = null) }
        viewModelScope.launch {
            when (val result = adminIndexRepository.getProgress(id)) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoading = false, progress = result.data, errorMessage = null) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    // ---- Polling (S6-T3) -----------------------------------------------------

    /** Idempotent: a second call while already polling is a no-op. Resets the
     * consecutive-failure counter and clears any stale poll error so a fresh
     * entry into the Tab always gets a clean slate (mirrors
     * [AdminTaskListViewModel.startPolling]). */
    fun startPolling() {
        if (pollingJob?.isActive == true) return
        consecutiveFailures = 0
        _uiState.update { it.copy(pollErrorMessage = null) }
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val id = instanceId ?: continue
                if (_uiState.value.progress?.isRunning != true) continue
                when (val result = adminIndexRepository.getProgress(id)) {
                    is ApiResult.Success -> {
                        consecutiveFailures = 0
                        _uiState.update { it.copy(progress = result.data) }
                    }
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

    // ---- Build/update/stop/clear confirmations (PRD §8.5) --------------------

    fun requestBuild() {
        _uiState.update { it.copy(dialog = AdminIndexDialog.BuildConfirm, dialogError = null) }
    }

    fun requestUpdate() {
        _uiState.update { it.copy(dialog = AdminIndexDialog.UpdateConfirm, dialogError = null) }
    }

    fun requestStop() {
        _uiState.update { it.copy(dialog = AdminIndexDialog.StopConfirm, dialogError = null) }
    }

    fun requestClear() {
        _uiState.update { it.copy(dialog = AdminIndexDialog.ClearConfirm, dialogError = null) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialog = null, dialogLoading = false, dialogError = null) }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * Dispatches to the repository call matching the open dialog. v1.0 S5-T1:
     * "更新索引" now submits [AdminIndexUiState.updatePath] (picked via
     * [openPathPicker], defaulting to DEC-504's "/") instead of always relying
     * on [AdminIndexRepository.updateIndex]'s default arguments; `maxDepth`
     * still uses the repository default (`-1`, confirmed reliable by V-609 —
     * DEC-604 resolved to keep it, no UI control needed for it). On success:
     * dismiss + snackbar + an immediate [refresh] (not waiting for the next
     * poll tick, per brief) -- build/update/stop/clear all mutate server-side
     * progress state that [refresh] alone can observe.
     */
    fun confirmDialog() {
        val id = instanceId ?: return
        val dialog = _uiState.value.dialog ?: return
        val call: suspend () -> ApiResult<Unit> = when (dialog) {
            AdminIndexDialog.BuildConfirm -> { { adminIndexRepository.buildIndex(id) } }
            AdminIndexDialog.UpdateConfirm -> { { adminIndexRepository.updateIndex(id, paths = listOf(_uiState.value.updatePath)) } }
            AdminIndexDialog.StopConfirm -> { { adminIndexRepository.stopIndex(id) } }
            AdminIndexDialog.ClearConfirm -> { { adminIndexRepository.clearIndex(id) } }
        }
        val successMessage = when (dialog) {
            AdminIndexDialog.BuildConfirm -> "重建索引已提交，正在后台进行"
            AdminIndexDialog.UpdateConfirm -> "更新索引已提交，正在后台进行"
            AdminIndexDialog.StopConfirm -> "已请求停止索引"
            AdminIndexDialog.ClearConfirm -> "索引已清空"
        }
        _uiState.update { it.copy(dialogLoading = true, dialogError = null) }
        viewModelScope.launch {
            when (val result = call()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(dialog = null, dialogLoading = false, snackbarMessage = successMessage) }
                    refresh()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(dialogLoading = false, dialogError = result.error.toUserMessage())
                }
            }
        }
    }

    // ---- Update-index path picker (v1.0 S5-T1) --------------------------------

    fun openPathPicker() {
        _uiState.update { it.copy(pickerPath = it.updatePath) }
        loadPickerEntries()
    }

    fun pickerEnterDirectory(entry: DirectoryPickerEntry) {
        _uiState.update { it.copy(pickerPath = entry.path) }
        loadPickerEntries()
    }

    fun pickerNavigateToSegment(segmentCount: Int) {
        val path = _uiState.value.pickerPath ?: return
        _uiState.update { it.copy(pickerPath = pathForSegmentCount(path, segmentCount)) }
        loadPickerEntries()
    }

    fun pickerRefresh() = loadPickerEntries()

    fun dismissPathPicker() {
        _uiState.update { it.copy(pickerPath = null) }
    }

    fun confirmPathPicker() {
        val path = _uiState.value.pickerPath ?: return
        _uiState.update { it.copy(updatePath = path, pickerPath = null) }
    }

    private fun loadPickerEntries() {
        val id = instanceId ?: return
        val path = _uiState.value.pickerPath ?: return
        _uiState.update { it.copy(pickerContent = DirectoryPickerContent.Loading) }
        viewModelScope.launch {
            when (val result = directoryPickerRepository.listDirectories(id, path)) {
                is ApiResult.Success -> {
                    val entries = result.data.map { DirectoryPickerEntry(it.name, it.path) }
                    _uiState.update { it.copy(pickerContent = DirectoryPickerContent.Content(entries)) }
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(pickerContent = DirectoryPickerContent.Error(result.error.toUserMessage()))
                }
            }
        }
    }

    /** `:feature:admin` deliberately has no dependency on `core:network`
     * (see build.gradle.kts comment), so this doesn't reuse
     * `OpenListPathCodec` — same trivial segment-rebuild logic, kept local. */
    private fun pathForSegmentCount(path: String, count: Int): String {
        val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
        if (count <= 0 || segments.isEmpty()) return "/"
        return "/" + segments.take(count.coerceAtMost(segments.size)).joinToString("/")
    }

    private companion object {
        const val POLL_INTERVAL_MS = 4_000L

        // Same threshold [AdminTaskListViewModel] uses (S5) -- no dedicated
        // shared constant exists (`:feature:admin` Tabs don't share code per
        // brief), kept as an independent literal here for the same reason.
        const val MAX_CONSECUTIVE_POLL_FAILURES = 3
    }
}
