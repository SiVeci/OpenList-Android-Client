package io.openlist.client.feature.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.designsystem.components.ExpiryOption
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.ShareRepository
import io.openlist.client.core.domain.TransferRepository
import io.openlist.client.core.model.FileDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    val canShare: Boolean = false,
    val shareCreate: ShareCreateState? = null,
)

@HiltViewModel
class FileDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val filesRepository: FilesRepository,
    private val instanceRepository: InstanceRepository,
    private val transferRepository: TransferRepository,
    private val shareRepository: ShareRepository,
    private val authRepository: AuthRepository,
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
        // Same optimistic-display gate as FileListViewModel.canWrite — guests
        // never see a create-share entry (P10); other logged-in sessions see
        // it and get a reactive 403 if CanShare turns out to be unset.
        authRepository.observeSession(instanceId)
            .onEach { session -> _uiState.update { it.copy(canShare = session != null && !session.isGuest) } }
            .launchIn(viewModelScope)
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

    // --- Share creation (v0.3_EXECUTION_PLAN.md §14) -------------------------

    fun openShareCreate() {
        _uiState.update { it.copy(shareCreate = ShareCreateState(targetPath = path)) }
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
