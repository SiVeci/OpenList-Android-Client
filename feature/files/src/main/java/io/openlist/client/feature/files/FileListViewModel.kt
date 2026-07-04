package io.openlist.client.feature.files

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.designsystem.components.DirectoryPickerContent
import io.openlist.client.core.designsystem.components.DirectoryPickerEntry
import io.openlist.client.core.designsystem.components.ExpiryOption
import io.openlist.client.core.designsystem.components.buildOperationResultMessage
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.DirectoryPickerRepository
import io.openlist.client.core.domain.FileListResult
import io.openlist.client.core.domain.FileOperationRepository
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.ShareRepository
import io.openlist.client.core.domain.UploadRepository
import io.openlist.client.core.model.BatchOperationFailure
import io.openlist.client.core.model.FileNode
import io.openlist.client.core.model.PreviewKind
import io.openlist.client.core.model.PreviewKindResolver
import io.openlist.client.core.model.UploadStatus
import io.openlist.client.core.model.UploadTask
import io.openlist.client.core.network.OpenListPathCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

/** Kinds that route to the v0.4 in-app preview screen instead of the file
 * detail screen when tapped (v0.4_EXECUTION_PLAN.md §11 S2-T4). PDF/OFFICE/
 * UNKNOWN deliberately stay out of this set — P-404 keeps them on the
 * pre-v0.4 detail-screen path until S4 gives them a real handler. */
internal val PREVIEWABLE_KINDS = setOf(
    PreviewKind.IMAGE,
    PreviewKind.VIDEO,
    PreviewKind.AUDIO,
    PreviewKind.TEXT,
    PreviewKind.MARKDOWN,
)

/** A single file/directory's write-action menu and the mkdir/rename/delete
 * dialogs it opens (v0.2_EXECUTION_PLAN.md §13.3/§13.4/§13.5). */
sealed class FileListDialog {
    data object NewFolder : FileListDialog()
    data class Rename(val node: FileNode) : FileListDialog()
    data class DeleteConfirm(val node: FileNode) : FileListDialog()
    data class BatchDeleteConfirm(val count: Int) : FileListDialog()
}

enum class DirectoryPickerPurpose { MOVE, COPY }

/**
 * State for the move/copy target-directory picker (v0.2_EXECUTION_PLAN.md
 * §13.6/§16), shared by the single-item action-sheet flow and batch
 * selection — both ultimately just move/copy [sourceNames] out of
 * [sourceDir], since a selection can only span one currently-browsed
 * directory. [currentPath] is where the picker is browsing, independent of
 * the file list's own [FileListUiState.currentPath] from the moment it opens.
 */
data class DirectoryPickerUiState(
    val purpose: DirectoryPickerPurpose,
    val sourceDir: String,
    val sourceNames: List<String>,
    val currentPath: String,
    val content: DirectoryPickerContent = DirectoryPickerContent.Loading,
    val isSubmitting: Boolean = false,
)

data class FileListUiState(
    val instanceName: String = "",
    val currentPath: String = "/",
    val nodes: List<FileNode> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val fromCache: Boolean = false,
    val errorMessage: String? = null,
    /** Guest sessions never see write entry points (v0.2_EXECUTION_PLAN.md §8.1).
     * Logged-in sessions show them optimistically and surface 403 reactively
     * (P5's documented fallback) rather than gating on the permission bitmask,
     * since a pre-v0.2 session's bitmask reads 0 until its next /api/me refresh. */
    val canWrite: Boolean = false,
    val actionSheetTarget: FileNode? = null,
    val dialog: FileListDialog? = null,
    val dialogInputValue: String = "",
    val dialogLoading: Boolean = false,
    val dialogError: String? = null,
    val snackbarMessage: String? = null,
    val snackbarIsError: Boolean = false,
    val directoryPicker: DirectoryPickerUiState? = null,
    /** Non-null while the "查看失败项" dialog from a partially-failed batch
     * operation is open (v0.2_EXECUTION_PLAN.md §17). */
    val failureDetails: List<BatchOperationFailure>? = null,
    val selectionMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val uploadTasks: List<UploadTask> = emptyList(),
    val showUploadPanel: Boolean = false,
    val shareCreate: ShareCreateState? = null,
) {
    val allSelected: Boolean get() = nodes.isNotEmpty() && selectedPaths.size == nodes.size
    val hasActiveUploads: Boolean get() = uploadTasks.any { it.status == UploadStatus.PENDING || it.status == UploadStatus.RUNNING }
}

@HiltViewModel
class FileListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val filesRepository: FilesRepository,
    private val instanceRepository: InstanceRepository,
    private val authRepository: AuthRepository,
    private val fileOperationRepository: FileOperationRepository,
    private val directoryPickerRepository: DirectoryPickerRepository,
    private val uploadRepository: UploadRepository,
    private val shareRepository: ShareRepository,
) : ViewModel() {

    private val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow(FileListUiState())
    val uiState: StateFlow<FileListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val instance = instanceRepository.getById(instanceId)
            _uiState.update { it.copy(instanceName = instance?.name.orEmpty()) }
        }
        authRepository.observeSession(instanceId)
            .onEach { session -> _uiState.update { it.copy(canWrite = session != null && !session.isGuest) } }
            .launchIn(viewModelScope)
        uploadRepository.observeUploadTasks(instanceId)
            .onEach { tasks -> onUploadTasksChanged(tasks) }
            .launchIn(viewModelScope)
        val startPath = savedStateHandle.get<String>("path")
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            ?: "/"
        navigateTo(startPath)
    }

    fun navigateTo(path: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val normalized = OpenListPathCodec.normalize(path)
            val isSamePath = _uiState.value.currentPath == normalized
            _uiState.update {
                it.copy(
                    currentPath = normalized,
                    // A same-path refresh keeps existing rows visible under
                    // isRefreshing; navigating elsewhere blanks the list until
                    // that path's own cache/network result arrives.
                    nodes = if (isSamePath) it.nodes else emptyList(),
                    isLoading = !isSamePath || it.nodes.isEmpty(),
                    isRefreshing = forceRefresh,
                    errorMessage = null,
                )
            }
            filesRepository.listDirectory(instanceId, normalized, forceRefresh).collect { result ->
                when (result) {
                    is FileListResult.Cached -> _uiState.update {
                        it.copy(nodes = result.nodes, fromCache = true, isLoading = false)
                    }
                    is FileListResult.Fresh -> _uiState.update {
                        it.copy(nodes = result.nodes, fromCache = false, isLoading = false, isRefreshing = false, errorMessage = null)
                    }
                    is FileListResult.Error -> _uiState.update {
                        it.copy(
                            nodes = result.staleCache ?: it.nodes,
                            fromCache = result.staleCache != null,
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = result.error.toUserMessage(),
                        )
                    }
                }
            }
        }
    }

    fun refresh() = navigateTo(_uiState.value.currentPath, forceRefresh = true)

    fun navigateToParent() {
        navigateTo(OpenListPathCodec.parent(_uiState.value.currentPath))
    }

    /** [segmentCount] real path segments to keep — 0 means root. */
    fun navigateToSegmentCount(segmentCount: Int) {
        navigateTo(OpenListPathCodec.pathForSegment(_uiState.value.currentPath, segmentCount))
    }

    /** Tapping a row: navigates/opens detail normally, or toggles selection
     * in batch mode — directories are never entered while selecting
     * (v0.2_EXECUTION_PLAN.md §12.8 point 10). Files whose [PreviewKind] is
     * previewable in-app (image/video/audio/text/markdown) go to
     * [onOpenFile] (the v0.4 preview route) instead of the detail screen;
     * PDF/OFFICE/UNKNOWN keep routing to [onOpenFileDetail] unchanged
     * (v0.4_EXECUTION_PLAN.md §11 S2-T4, P-404). */
    fun onNodeClick(node: FileNode, onOpenFileDetail: (String) -> Unit, onOpenFile: (String) -> Unit) {
        if (_uiState.value.selectionMode) {
            toggleSelection(node)
        } else if (node.isDir) {
            navigateTo(node.path)
        } else if (PreviewKindResolver.resolve(node.name) in PREVIEWABLE_KINDS) {
            onOpenFile(node.path)
        } else {
            onOpenFileDetail(node.path)
        }
    }

    // --- File action menu -------------------------------------------------

    fun openActionSheet(node: FileNode) {
        _uiState.update { it.copy(actionSheetTarget = node) }
    }

    fun dismissActionSheet() {
        _uiState.update { it.copy(actionSheetTarget = null) }
    }

    fun openNewFolderDialog() {
        _uiState.update { it.copy(dialog = FileListDialog.NewFolder, dialogInputValue = "", dialogError = null) }
    }

    fun openRenameDialog(node: FileNode) {
        _uiState.update {
            it.copy(
                actionSheetTarget = null,
                dialog = FileListDialog.Rename(node),
                dialogInputValue = node.name,
                dialogError = null,
            )
        }
    }

    fun openDeleteConfirm(node: FileNode) {
        _uiState.update { it.copy(actionSheetTarget = null, dialog = FileListDialog.DeleteConfirm(node), dialogError = null) }
    }

    fun onDialogValueChange(value: String) {
        _uiState.update { it.copy(dialogInputValue = value) }
    }

    fun dismissDialog() {
        if (_uiState.value.dialogLoading) return
        _uiState.update { it.copy(dialog = null, dialogInputValue = "", dialogError = null) }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /** Confirms whichever [FileListDialog] is currently open. */
    fun confirmDialog() {
        when (val dialog = _uiState.value.dialog) {
            is FileListDialog.NewFolder -> mkdir()
            is FileListDialog.Rename -> rename(dialog.node)
            is FileListDialog.DeleteConfirm -> delete(dialog.node)
            is FileListDialog.BatchDeleteConfirm -> batchDelete()
            null -> Unit
        }
    }

    private fun mkdir() {
        val name = _uiState.value.dialogInputValue.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(dialogLoading = true, dialogError = null) }
            when (val result = fileOperationRepository.mkdir(instanceId, _uiState.value.currentPath, name)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(dialog = null, dialogLoading = false, dialogInputValue = "", snackbarMessage = "新建目录成功")
                    }
                    refresh()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(dialogLoading = false, dialogError = result.error.toUserMessage())
                }
            }
        }
    }

    private fun rename(node: FileNode) {
        val newName = _uiState.value.dialogInputValue.trim()
        if (newName.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(dialogLoading = true, dialogError = null) }
            when (val result = fileOperationRepository.rename(instanceId, node.path, newName)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(dialog = null, dialogLoading = false, dialogInputValue = "", snackbarMessage = "重命名成功")
                    }
                    refresh()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(dialogLoading = false, dialogError = result.error.toUserMessage())
                }
            }
        }
    }

    private fun delete(node: FileNode) {
        viewModelScope.launch {
            _uiState.update { it.copy(dialogLoading = true, dialogError = null) }
            val parent = OpenListPathCodec.parent(node.path)
            when (val result = fileOperationRepository.remove(instanceId, parent, listOf(node.name))) {
                is ApiResult.Success -> {
                    val batch = result.data
                    if (batch.failedCount == 0) {
                        _uiState.update {
                            it.copy(dialog = null, dialogLoading = false, snackbarMessage = "删除成功")
                        }
                        refresh()
                    } else {
                        // Backend responded but this specific item failed (e.g. lost a
                        // race, no permission) — keep the confirm dialog open with why,
                        // never silently mark it as removed (v0.2_EXECUTION_PLAN.md §16).
                        _uiState.update {
                            it.copy(dialogLoading = false, dialogError = batch.failedItems.firstOrNull()?.reason ?: "删除失败")
                        }
                    }
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(dialogLoading = false, dialogError = result.error.toUserMessage())
                }
            }
        }
    }

    // --- Batch selection ----------------------------------------------------

    fun enterSelectionMode(node: FileNode) {
        _uiState.update { it.copy(selectionMode = true, selectedPaths = setOf(node.path)) }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(selectionMode = false, selectedPaths = emptySet()) }
    }

    private fun toggleSelection(node: FileNode) {
        _uiState.update {
            val selected = it.selectedPaths
            it.copy(selectedPaths = if (node.path in selected) selected - node.path else selected + node.path)
        }
    }

    fun toggleSelectAll() {
        _uiState.update {
            if (it.allSelected) it.copy(selectedPaths = emptySet()) else it.copy(selectedPaths = it.nodes.map { n -> n.path }.toSet())
        }
    }

    fun openBatchDeleteConfirm() {
        val count = _uiState.value.selectedPaths.size
        if (count == 0) return
        _uiState.update { it.copy(dialog = FileListDialog.BatchDeleteConfirm(count), dialogError = null) }
    }

    private fun batchDelete() {
        val selected = _uiState.value.selectedPaths
        if (selected.isEmpty()) return
        val dir = _uiState.value.currentPath
        val names = selected.map { OpenListPathCodec.name(it) }
        viewModelScope.launch {
            _uiState.update { it.copy(dialogLoading = true, dialogError = null) }
            when (val result = fileOperationRepository.remove(instanceId, dir, names)) {
                is ApiResult.Success -> {
                    val batch = result.data
                    val message = buildOperationResultMessage(batch.total, batch.successCount, batch.failedCount)
                    _uiState.update {
                        it.copy(
                            dialog = null,
                            dialogLoading = false,
                            selectionMode = false,
                            selectedPaths = emptySet(),
                            snackbarMessage = message.text,
                            snackbarIsError = message.isError,
                            failureDetails = if (batch.failedItems.isNotEmpty()) batch.failedItems else null,
                        )
                    }
                    if (batch.successCount > 0) refresh()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(dialogLoading = false, dialogError = result.error.toUserMessage())
                }
            }
        }
    }

    // --- Move/copy target-directory picker --------------------------------

    fun openMovePicker(node: FileNode) = openDirectoryPicker(DirectoryPickerPurpose.MOVE, OpenListPathCodec.parent(node.path), listOf(node.name))

    fun openCopyPicker(node: FileNode) = openDirectoryPicker(DirectoryPickerPurpose.COPY, OpenListPathCodec.parent(node.path), listOf(node.name))

    fun openBatchMovePicker() {
        val selected = _uiState.value.selectedPaths
        if (selected.isEmpty()) return
        openDirectoryPicker(DirectoryPickerPurpose.MOVE, _uiState.value.currentPath, selected.map { OpenListPathCodec.name(it) })
    }

    fun openBatchCopyPicker() {
        val selected = _uiState.value.selectedPaths
        if (selected.isEmpty()) return
        openDirectoryPicker(DirectoryPickerPurpose.COPY, _uiState.value.currentPath, selected.map { OpenListPathCodec.name(it) })
    }

    private fun openDirectoryPicker(purpose: DirectoryPickerPurpose, sourceDir: String, names: List<String>) {
        _uiState.update {
            it.copy(
                actionSheetTarget = null,
                directoryPicker = DirectoryPickerUiState(
                    purpose = purpose,
                    sourceDir = sourceDir,
                    sourceNames = names,
                    currentPath = it.currentPath,
                ),
            )
        }
        loadDirectoryPickerEntries()
    }

    fun directoryPickerEnter(entry: DirectoryPickerEntry) {
        updatePicker { it.copy(currentPath = entry.path) }
        loadDirectoryPickerEntries()
    }

    fun directoryPickerNavigateToSegment(segmentCount: Int) {
        val picker = _uiState.value.directoryPicker ?: return
        val newPath = OpenListPathCodec.pathForSegment(picker.currentPath, segmentCount)
        updatePicker { it.copy(currentPath = newPath) }
        loadDirectoryPickerEntries()
    }

    fun directoryPickerRefresh() = loadDirectoryPickerEntries()

    fun dismissDirectoryPicker() {
        _uiState.update { it.copy(directoryPicker = null) }
    }

    fun confirmDirectoryPicker() {
        val picker = _uiState.value.directoryPicker ?: return
        viewModelScope.launch {
            updatePicker { it.copy(isSubmitting = true) }
            val result = when (picker.purpose) {
                DirectoryPickerPurpose.MOVE ->
                    fileOperationRepository.move(instanceId, picker.sourceDir, picker.currentPath, picker.sourceNames)
                DirectoryPickerPurpose.COPY ->
                    fileOperationRepository.copy(instanceId, picker.sourceDir, picker.currentPath, picker.sourceNames)
            }
            when (result) {
                is ApiResult.Success -> {
                    val batch = result.data
                    val message = buildOperationResultMessage(batch.total, batch.successCount, batch.failedCount)
                    _uiState.update {
                        it.copy(
                            directoryPicker = null,
                            selectionMode = false,
                            selectedPaths = emptySet(),
                            snackbarMessage = message.text,
                            snackbarIsError = message.isError,
                            failureDetails = if (batch.failedItems.isNotEmpty()) batch.failedItems else null,
                        )
                    }
                    if (batch.successCount > 0) refresh()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(directoryPicker = null, snackbarMessage = result.error.toUserMessage(), snackbarIsError = true)
                }
            }
        }
    }

    private fun updatePicker(transform: (DirectoryPickerUiState) -> DirectoryPickerUiState) {
        _uiState.update { st -> st.directoryPicker?.let { st.copy(directoryPicker = transform(it)) } ?: st }
    }

    private fun loadDirectoryPickerEntries() {
        val picker = _uiState.value.directoryPicker ?: return
        updatePicker { it.copy(content = DirectoryPickerContent.Loading) }
        viewModelScope.launch {
            when (val result = directoryPickerRepository.listDirectories(instanceId, picker.currentPath)) {
                is ApiResult.Success -> {
                    val entries = result.data.map { DirectoryPickerEntry(it.name, it.path) }
                    updatePicker { it.copy(content = DirectoryPickerContent.Content(entries)) }
                }
                is ApiResult.Failure -> updatePicker {
                    it.copy(content = DirectoryPickerContent.Error(result.error.toUserMessage()))
                }
            }
        }
    }

    // --- Upload --------------------------------------------------------------

    /** [uris] are `ACTION_OPEN_DOCUMENT` results the Screen already resolved
     * via its picker launcher; persisting read access happens inside the
     * repository, before the Worker that needs it is ever enqueued (P6). */
    fun enqueueUpload(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            when (val result = uploadRepository.enqueueUpload(instanceId, _uiState.value.currentPath, uris)) {
                is ApiResult.Success -> _uiState.update { it.copy(showUploadPanel = true) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(snackbarMessage = result.error.toUserMessage(), snackbarIsError = true)
                }
            }
        }
    }

    fun openUploadPanel() {
        _uiState.update { it.copy(showUploadPanel = true) }
    }

    fun dismissUploadPanel() {
        _uiState.update { it.copy(showUploadPanel = false) }
    }

    fun cancelUpload(taskId: String) {
        viewModelScope.launch { uploadRepository.cancelUpload(taskId) }
    }

    /** Auto-refreshes the file list when an upload targeting the directory
     * the user is currently looking at just finished (v0.2_EXECUTION_PLAN.md
     * §13.5/§18) — not on every emission, only on a PENDING/RUNNING → SUCCESS
     * transition, so this doesn't refetch on every progress tick. */
    private fun onUploadTasksChanged(tasks: List<UploadTask>) {
        val previousById = _uiState.value.uploadTasks.associateBy { it.id }
        _uiState.update { it.copy(uploadTasks = tasks) }
        val justSucceededHere = tasks.any { task ->
            task.status == UploadStatus.SUCCESS &&
                task.targetDir == _uiState.value.currentPath &&
                previousById[task.id]?.status != UploadStatus.SUCCESS
        }
        if (justSucceededHere) refresh()
    }

    // --- Share creation (v0.3_EXECUTION_PLAN.md §14) -------------------------

    fun openShareCreate(node: FileNode) {
        _uiState.update { it.copy(actionSheetTarget = null, shareCreate = ShareCreateState(targetPath = node.path)) }
    }

    fun dismissShareCreate() {
        _uiState.update { it.copy(shareCreate = null) }
    }

    fun updateShareCreateName(value: String) = updateShareCreate { it.copy(name = value) }
    fun updateShareCreatePassword(value: String) = updateShareCreate { it.copy(password = value) }
    fun updateShareCreateExpiryOption(option: ExpiryOption) = updateShareCreate { it.copy(expiryOption = option) }
    fun updateShareCreateCustomExpiry(millis: Long) = updateShareCreate { it.copy(customExpiryMillis = millis) }
    fun updateShareCreateEnabled(enabled: Boolean) = updateShareCreate { it.copy(enabled = enabled) }

    private fun updateShareCreate(transform: (ShareCreateState) -> ShareCreateState) {
        _uiState.update { st -> st.shareCreate?.let { st.copy(shareCreate = transform(it)) } ?: st }
    }

    fun submitShareCreate() {
        val state = _uiState.value.shareCreate ?: return
        viewModelScope.launch {
            updateShareCreate { it.copy(submitting = true, errorMessage = null) }
            when (val result = submitShareCreate(shareRepository, instanceRepository, instanceId, state)) {
                is ApiResult.Success -> _uiState.update { it.copy(shareCreate = result.data) }
                is ApiResult.Failure -> updateShareCreate { it.copy(submitting = false, errorMessage = result.error.toUserMessage()) }
            }
        }
    }
}
