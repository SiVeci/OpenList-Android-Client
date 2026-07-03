package io.openlist.client.feature.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.designsystem.components.DirectoryPickerContent
import io.openlist.client.core.designsystem.components.DirectoryPickerEntry
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.DirectoryPickerRepository
import io.openlist.client.core.domain.FileListResult
import io.openlist.client.core.domain.FileOperationRepository
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.FileNode
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

/** A single file/directory's write-action menu and the mkdir/rename/delete
 * dialogs it opens (v0.2_EXECUTION_PLAN.md §13.3/§13.4/§13.5). Move/copy go
 * through [DirectoryPickerUiState] instead, since they need a target directory. */
sealed class FileListDialog {
    data object NewFolder : FileListDialog()
    data class Rename(val node: FileNode) : FileListDialog()
    data class DeleteConfirm(val node: FileNode) : FileListDialog()
}

enum class DirectoryPickerPurpose { MOVE, COPY }

/** State for the move/copy target-directory picker (v0.2_EXECUTION_PLAN.md
 * §13.6/§16). [currentPath] is where the picker is currently browsing —
 * starts at the file list's own [FileListUiState.currentPath], independent
 * of it from then on. */
data class DirectoryPickerUiState(
    val purpose: DirectoryPickerPurpose,
    val sourceNode: FileNode,
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
    val directoryPicker: DirectoryPickerUiState? = null,
)

@HiltViewModel
class FileListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val filesRepository: FilesRepository,
    private val instanceRepository: InstanceRepository,
    private val authRepository: AuthRepository,
    private val fileOperationRepository: FileOperationRepository,
    private val directoryPickerRepository: DirectoryPickerRepository,
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

    // --- Move/copy target-directory picker --------------------------------

    fun openMovePicker(node: FileNode) = openDirectoryPicker(DirectoryPickerPurpose.MOVE, node)

    fun openCopyPicker(node: FileNode) = openDirectoryPicker(DirectoryPickerPurpose.COPY, node)

    private fun openDirectoryPicker(purpose: DirectoryPickerPurpose, node: FileNode) {
        _uiState.update {
            it.copy(
                actionSheetTarget = null,
                directoryPicker = DirectoryPickerUiState(purpose = purpose, sourceNode = node, currentPath = it.currentPath),
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
            val srcDir = OpenListPathCodec.parent(picker.sourceNode.path)
            val targetDir = picker.currentPath
            val names = listOf(picker.sourceNode.name)
            val actionLabel = if (picker.purpose == DirectoryPickerPurpose.MOVE) "移动" else "复制"
            val result = when (picker.purpose) {
                DirectoryPickerPurpose.MOVE -> fileOperationRepository.move(instanceId, srcDir, targetDir, names)
                DirectoryPickerPurpose.COPY -> fileOperationRepository.copy(instanceId, srcDir, targetDir, names)
            }
            when (result) {
                is ApiResult.Success -> {
                    val batch = result.data
                    val message = if (batch.successCount > 0) {
                        "${actionLabel}成功"
                    } else {
                        batch.failedItems.firstOrNull()?.reason ?: "${actionLabel}失败"
                    }
                    _uiState.update { it.copy(directoryPicker = null, snackbarMessage = message) }
                    if (batch.successCount > 0) refresh()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(directoryPicker = null, snackbarMessage = result.error.toUserMessage())
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
}
