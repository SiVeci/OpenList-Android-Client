package io.openlist.client.feature.task

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.designsystem.components.DirectoryPickerContent
import io.openlist.client.core.designsystem.components.DirectoryPickerEntry
import io.openlist.client.core.domain.DirectoryPickerRepository
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.OfflineDownloadRepository
import io.openlist.client.core.domain.TaskAggregationRepository
import io.openlist.client.core.model.TaskSource
import io.openlist.client.core.model.UnifiedTask
import io.openlist.client.core.model.UnifiedTaskStatus
import io.openlist.client.core.network.OpenListPathCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TaskTab { ALL, UPLOAD, DOWNLOAD, REMOTE }

data class OfflineDownloadUiState(
    val url: String = "",
    val targetDir: String = "/",
    val tools: List<String> = emptyList(),
    val selectedTool: String? = null,
    val submitting: Boolean = false,
    val errorMessage: String? = null,
    val pickerPath: String? = null,
    val pickerContent: DirectoryPickerContent = DirectoryPickerContent.Loading,
)

data class TaskCenterUiState(
    val tasks: List<UnifiedTask> = emptyList(),
    val selectedTab: TaskTab = TaskTab.ALL,
    val isRefreshing: Boolean = false,
    val remoteErrorMessage: String? = null,
    val cancelConfirmTarget: UnifiedTask? = null,
    val cancelling: Boolean = false,
    val offlineDownload: OfflineDownloadUiState? = null,
    val snackbarMessage: String? = null,
) {
    val filteredTasks: List<UnifiedTask>
        get() = when (selectedTab) {
            TaskTab.ALL -> tasks
            TaskTab.UPLOAD -> tasks.filter { it.source == TaskSource.LOCAL_UPLOAD }
            TaskTab.DOWNLOAD -> tasks.filter { it.source == TaskSource.LOCAL_DOWNLOAD }
            TaskTab.REMOTE -> tasks.filter { it.source == TaskSource.REMOTE }
        }

    val hasRunningRemote: Boolean
        get() = tasks.any { it.source == TaskSource.REMOTE && (it.status == UnifiedTaskStatus.RUNNING || it.status == UnifiedTaskStatus.PENDING) }
}

/**
 * P6 polling: while this ViewModel is alive (i.e. the task-center back-stack
 * entry is on screen — Hilt clears it, and this coroutine with it, once the
 * user navigates away), a 4s tick refreshes remote tasks only when at least
 * one is RUNNING/PENDING.
 */
@HiltViewModel
class TaskCenterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskAggregationRepository: TaskAggregationRepository,
    private val offlineDownloadRepository: OfflineDownloadRepository,
    private val directoryPickerRepository: DirectoryPickerRepository,
    private val filesRepository: FilesRepository,
) : ViewModel() {

    private val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow(TaskCenterUiState())
    val uiState: StateFlow<TaskCenterUiState> = _uiState.asStateFlow()

    init {
        taskAggregationRepository.observeAllTasks(instanceId)
            .onEach { tasks -> _uiState.update { it.copy(tasks = tasks) } }
            .launchIn(viewModelScope)
        refresh()
        startPolling()
    }

    fun selectTab(tab: TaskTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, remoteErrorMessage = null) }
            taskAggregationRepository.refreshDownloadStatuses(instanceId)
            when (val result = taskAggregationRepository.refreshRemoteTasks(instanceId)) {
                is ApiResult.Success -> _uiState.update { it.copy(isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    // Local tasks (already in `tasks` via the Flow) stay visible —
                    // a remote-fetch failure never blanks what's already shown (§18.3).
                    it.copy(isRefreshing = false, remoteErrorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(POLL_INTERVAL_MS)
                if (_uiState.value.hasRunningRemote) {
                    taskAggregationRepository.refreshRemoteTasks(instanceId)
                }
            }
        }
    }

    // --- Cancel ---------------------------------------------------------------

    fun openCancelConfirm(task: UnifiedTask) {
        _uiState.update { it.copy(cancelConfirmTarget = task) }
    }

    fun dismissCancelConfirm() {
        _uiState.update { it.copy(cancelConfirmTarget = null) }
    }

    fun confirmCancel() {
        val task = _uiState.value.cancelConfirmTarget ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(cancelling = true) }
            when (val result = taskAggregationRepository.cancelTask(instanceId, task.id, task.source)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(cancelling = false, cancelConfirmTarget = null, snackbarMessage = "已取消")
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(cancelling = false, cancelConfirmTarget = null, snackbarMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // --- Retry (v1.0_PRD §4.2.C.1) --------------------------------------------

    /** No confirmation dialog — unlike cancel, retrying is low-risk/reversible
     * (worst case it fails again with the same reason shown). */
    fun retryTask(task: UnifiedTask) {
        viewModelScope.launch {
            when (val result = taskAggregationRepository.retryTask(instanceId, task.id, task.source)) {
                is ApiResult.Success -> _uiState.update { it.copy(snackbarMessage = "已重新开始上传") }
                is ApiResult.Failure -> _uiState.update { it.copy(snackbarMessage = result.error.toUserMessage()) }
            }
        }
    }

    // --- Open target (S6-T4, P-407) --------------------------------------------

    /**
     * "跳转目录"/row-tap entry point for a completed task with a known
     * [UnifiedTask.path]. B-405: pre-S6 code unconditionally treated every
     * `path` as a directory; this resolves it via [FilesRepository.getFile]
     * (creator/owner's own normal permissions, same call every other v0.4
     * entry point already makes) and only *then* decides directory vs. file.
     *
     * Directory -> [onOpenDirectory], unchanged from the pre-S6 behavior
     * (zero regression for the existing "SUCCESS task with a path always
     * opened its folder" case).
     *
     * Non-directory file -> **always** [onOpenFile], regardless of
     * [io.openlist.client.core.model.PreviewKindResolver.isInAppPreviewable].
     * This is a deliberate v0.4 "统一分发" (unified dispatch) choice: the
     * task center has no "file detail" concept/route of its own to fall back
     * to, and [io.openlist.client.feature.preview.PreviewScreen] already
     * handles PDF/OFFICE/UNKNOWN itself via EXTERNAL_APP/UNSUPPORTED +
     * download/external-open fallbacks (S4). Routing every non-directory
     * task target through the one preview entry point avoids adding a
     * task-center-specific "file detail" special case that would duplicate
     * that fallback logic for no benefit.
     *
     * A [ApiResult.Failure] (the path was moved/deleted, etc.) surfaces via
     * the existing [TaskCenterUiState.snackbarMessage] mechanism and leaves
     * the user on the task center -- no navigation happens.
     */
    fun openTaskTarget(task: UnifiedTask, onOpenDirectory: (String) -> Unit, onOpenFile: (String) -> Unit) {
        val path = task.path ?: return
        viewModelScope.launch {
            when (val result = filesRepository.getFile(instanceId, path)) {
                is ApiResult.Success -> {
                    if (result.data.isDir) onOpenDirectory(path) else onOpenFile(path)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(snackbarMessage = "无法打开，文件可能已被移动或删除")
                }
            }
        }
    }

    // --- Offline download (v0.3_EXECUTION_PLAN.md §17) ------------------------

    fun openOfflineDownloadSheet() {
        _uiState.update { it.copy(offlineDownload = OfflineDownloadUiState()) }
        viewModelScope.launch {
            when (val result = offlineDownloadRepository.listTools(instanceId)) {
                is ApiResult.Success -> updateOfflineDownload {
                    it.copy(tools = result.data, selectedTool = result.data.firstOrNull())
                }
                is ApiResult.Failure -> Unit // tool list is optional UX sugar; submit still works without it
            }
        }
    }

    fun dismissOfflineDownloadSheet() {
        _uiState.update { it.copy(offlineDownload = null) }
    }

    fun updateOfflineDownloadUrl(value: String) = updateOfflineDownload { it.copy(url = value, errorMessage = null) }
    fun updateOfflineDownloadTool(tool: String) = updateOfflineDownload { it.copy(selectedTool = tool) }

    private fun updateOfflineDownload(transform: (OfflineDownloadUiState) -> OfflineDownloadUiState) {
        _uiState.update { st -> st.offlineDownload?.let { st.copy(offlineDownload = transform(it)) } ?: st }
    }

    fun openDirectoryPicker() {
        updateOfflineDownload { it.copy(pickerPath = it.targetDir) }
        loadPickerEntries()
    }

    fun directoryPickerEnter(entry: DirectoryPickerEntry) {
        updateOfflineDownload { it.copy(pickerPath = entry.path) }
        loadPickerEntries()
    }

    fun directoryPickerNavigateToSegment(segmentCount: Int) {
        val path = _uiState.value.offlineDownload?.pickerPath ?: return
        updateOfflineDownload { it.copy(pickerPath = OpenListPathCodec.pathForSegment(path, segmentCount)) }
        loadPickerEntries()
    }

    fun directoryPickerRefresh() = loadPickerEntries()

    fun dismissDirectoryPicker() {
        updateOfflineDownload { it.copy(pickerPath = null) }
    }

    fun confirmDirectoryPicker() {
        val path = _uiState.value.offlineDownload?.pickerPath ?: return
        updateOfflineDownload { it.copy(targetDir = path, pickerPath = null) }
    }

    private fun loadPickerEntries() {
        val path = _uiState.value.offlineDownload?.pickerPath ?: return
        updateOfflineDownload { it.copy(pickerContent = DirectoryPickerContent.Loading) }
        viewModelScope.launch {
            when (val result = directoryPickerRepository.listDirectories(instanceId, path)) {
                is ApiResult.Success -> {
                    val entries = result.data.map { DirectoryPickerEntry(it.name, it.path) }
                    updateOfflineDownload { it.copy(pickerContent = DirectoryPickerContent.Content(entries)) }
                }
                is ApiResult.Failure -> updateOfflineDownload {
                    it.copy(pickerContent = DirectoryPickerContent.Error(result.error.toUserMessage()))
                }
            }
        }
    }

    fun submitOfflineDownload() {
        val state = _uiState.value.offlineDownload ?: return
        val url = state.url.trim()
        if (!isValidDownloadUrl(url)) {
            updateOfflineDownload { it.copy(errorMessage = "请输入有效的 http/https/magnet 链接") }
            return
        }
        viewModelScope.launch {
            updateOfflineDownload { it.copy(submitting = true, errorMessage = null) }
            val result = offlineDownloadRepository.addOfflineDownload(instanceId, listOf(url), state.targetDir, state.selectedTool)
            when (result) {
                is ApiResult.Success -> _uiState.update { it.copy(offlineDownload = null, snackbarMessage = "已提交离线下载") }
                is ApiResult.Failure -> updateOfflineDownload { it.copy(submitting = false, errorMessage = result.error.toUserMessage()) }
            }
        }
    }

    private fun isValidDownloadUrl(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://") || url.startsWith("magnet:")

    private companion object {
        const val POLL_INTERVAL_MS = 4_000L
    }
}
