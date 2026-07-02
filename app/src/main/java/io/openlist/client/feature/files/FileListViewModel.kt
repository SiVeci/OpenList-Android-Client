package io.openlist.client.feature.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.domain.FileListResult
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.FileNode
import io.openlist.client.core.network.OpenListPathCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

data class FileListUiState(
    val instanceName: String = "",
    val currentPath: String = "/",
    val nodes: List<FileNode> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val fromCache: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class FileListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val filesRepository: FilesRepository,
    private val instanceRepository: InstanceRepository,
) : ViewModel() {

    private val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow(FileListUiState())
    val uiState: StateFlow<FileListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val instance = instanceRepository.getById(instanceId)
            _uiState.update { it.copy(instanceName = instance?.name.orEmpty()) }
        }
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
}
