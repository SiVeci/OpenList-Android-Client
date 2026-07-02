package io.openlist.client.feature.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.TransferRepository
import io.openlist.client.core.model.FileDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

sealed class DownloadUiState {
    data object Idle : DownloadUiState()
    data object Enqueued : DownloadUiState()
    data class Failed(val message: String) : DownloadUiState()
}

data class FileDetailUiState(
    val instanceName: String = "",
    val isLoading: Boolean = true,
    val detail: FileDetail? = null,
    val errorMessage: String? = null,
    val downloadState: DownloadUiState = DownloadUiState.Idle,
)

@HiltViewModel
class FileDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val filesRepository: FilesRepository,
    private val instanceRepository: InstanceRepository,
    private val transferRepository: TransferRepository,
) : ViewModel() {

    private val instanceId: String = checkNotNull(savedStateHandle["instanceId"])
    private val path: String = savedStateHandle.get<String>("path")
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        ?: "/"

    private val _uiState = MutableStateFlow(FileDetailUiState())
    val uiState: StateFlow<FileDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val instance = instanceRepository.getById(instanceId)
            _uiState.update { it.copy(instanceName = instance?.name.orEmpty()) }
            when (val result = filesRepository.getFile(instanceId, path)) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoading = false, detail = result.data) }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoading = false, errorMessage = result.error.toUserMessage()) }
            }
        }
    }

    fun download() {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch {
            when (val result = transferRepository.enqueueDownload(instanceId, detail)) {
                is ApiResult.Success -> _uiState.update { it.copy(downloadState = DownloadUiState.Enqueued) }
                is ApiResult.Failure -> _uiState.update { it.copy(downloadState = DownloadUiState.Failed(result.error.toUserMessage())) }
            }
        }
    }
}
