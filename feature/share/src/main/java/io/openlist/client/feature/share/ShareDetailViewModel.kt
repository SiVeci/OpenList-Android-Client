package io.openlist.client.feature.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.designsystem.components.ExpiryOption
import io.openlist.client.core.designsystem.components.expiryOptionFor
import io.openlist.client.core.designsystem.components.toEpochMillis
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.ShareRepository
import io.openlist.client.core.model.Share
import io.openlist.client.core.model.ShareWriteRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShareEditState(
    val name: String,
    val password: String,
    val expiryOption: ExpiryOption,
    val customExpiryMillis: Long? = null,
    val enabled: Boolean,
    val submitting: Boolean = false,
    val errorMessage: String? = null,
)

data class ShareDetailUiState(
    val isLoading: Boolean = true,
    val share: Share? = null,
    val shareUrl: String? = null,
    val errorMessage: String? = null,
    val showDeleteConfirm: Boolean = false,
    val deleting: Boolean = false,
    val toggling: Boolean = false,
    val editSheet: ShareEditState? = null,
    val snackbarMessage: String? = null,
    val deleted: Boolean = false,
)

@HiltViewModel
class ShareDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val shareRepository: ShareRepository,
    private val instanceRepository: InstanceRepository,
) : ViewModel() {

    private val instanceId: String = checkNotNull(savedStateHandle["instanceId"])
    private val shareId: String = checkNotNull(savedStateHandle["shareId"])

    private val _uiState = MutableStateFlow(ShareDetailUiState())
    val uiState: StateFlow<ShareDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val instance = instanceRepository.getById(instanceId)
            when (val result = shareRepository.getShare(instanceId, shareId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        share = result.data,
                        shareUrl = instance?.let { i -> shareRepository.buildShareUrl(i.baseUrl, shareId) },
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun toggleEnabled() {
        val share = _uiState.value.share ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(toggling = true) }
            val result = if (share.enabled) {
                shareRepository.disableShare(instanceId, shareId)
            } else {
                shareRepository.enableShare(instanceId, shareId)
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(toggling = false, share = it.share?.copy(enabled = !share.enabled)) }
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(toggling = false, snackbarMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun openDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun confirmDelete() {
        viewModelScope.launch {
            _uiState.update { it.copy(deleting = true) }
            when (val result = shareRepository.deleteShare(instanceId, shareId)) {
                is ApiResult.Success -> _uiState.update { it.copy(deleting = false, showDeleteConfirm = false, deleted = true) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(deleting = false, showDeleteConfirm = false, snackbarMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun openEditSheet() {
        val share = _uiState.value.share ?: return
        val (option, customMillis) = expiryOptionFor(share.expiresAt)
        _uiState.update {
            it.copy(
                editSheet = ShareEditState(
                    name = share.name.orEmpty(),
                    password = share.password.orEmpty(),
                    expiryOption = option,
                    customExpiryMillis = customMillis,
                    enabled = share.enabled,
                ),
            )
        }
    }

    fun dismissEditSheet() {
        _uiState.update { it.copy(editSheet = null) }
    }

    fun updateEditName(value: String) = updateEdit { it.copy(name = value) }
    fun updateEditPassword(value: String) = updateEdit { it.copy(password = value) }
    fun updateEditExpiryOption(option: ExpiryOption) = updateEdit { it.copy(expiryOption = option) }
    fun updateEditCustomExpiry(millis: Long) = updateEdit { it.copy(customExpiryMillis = millis) }
    fun updateEditEnabled(enabled: Boolean) = updateEdit { it.copy(enabled = enabled) }

    private fun updateEdit(transform: (ShareEditState) -> ShareEditState) {
        _uiState.update { st -> st.editSheet?.let { st.copy(editSheet = transform(it)) } ?: st }
    }

    fun submitEdit() {
        val edit = _uiState.value.editSheet ?: return
        val request = ShareWriteRequest(
            paths = _uiState.value.share?.paths ?: return,
            name = edit.name.trim().ifBlank { null },
            password = edit.password.trim().ifBlank { null },
            expiresAt = edit.expiryOption.toEpochMillis(edit.customExpiryMillis),
            disabled = !edit.enabled,
        )
        viewModelScope.launch {
            updateEdit { it.copy(submitting = true, errorMessage = null) }
            when (val result = shareRepository.updateShare(instanceId, shareId, request)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(share = result.data, editSheet = null, snackbarMessage = "分享已更新")
                }
                is ApiResult.Failure -> updateEdit { it.copy(submitting = false, errorMessage = result.error.toUserMessage()) }
            }
        }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
