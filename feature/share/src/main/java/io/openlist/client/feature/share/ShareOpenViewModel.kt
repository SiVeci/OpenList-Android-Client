package io.openlist.client.feature.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.domain.ShareRepository
import io.openlist.client.core.model.ShareInboundInfo
import io.openlist.client.core.model.ShareInboundTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Resolution state machine for the "打开分享链接" flow (v1.0_PRD §4.2.D). */
sealed class ShareOpenStatus {
    data object Idle : ShareOpenStatus()
    data class NeedsPassword(val target: ShareInboundTarget) : ShareOpenStatus()
    data class Resolved(val info: ShareInboundInfo, val target: ShareInboundTarget) : ShareOpenStatus()
}

data class ShareOpenUiState(
    val inputUrl: String = "",
    val passwordInput: String = "",
    /** Set once per screen visit if the clipboard looks like a URL when the
     * screen opens (DEC-602) — the user must explicitly tap to use it, this
     * is never auto-submitted. */
    val clipboardSuggestion: String? = null,
    val status: ShareOpenStatus = ShareOpenStatus.Idle,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ShareOpenViewModel @Inject constructor(
    private val shareRepository: ShareRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareOpenUiState())
    val uiState: StateFlow<ShareOpenUiState> = _uiState.asStateFlow()

    fun onUrlChange(value: String) = _uiState.update { it.copy(inputUrl = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(passwordInput = value, errorMessage = null) }

    /** Called once when the screen opens with whatever text (if any) is
     * currently on the clipboard. Only suggests — never reads it again or
     * auto-submits, so a stale/irrelevant clipboard never silently fires a
     * network request. */
    fun onClipboardDetected(text: String) {
        if (looksLikeShareUrl(text)) _uiState.update { it.copy(clipboardSuggestion = text) }
    }

    fun useClipboardSuggestion() {
        val text = _uiState.value.clipboardSuggestion ?: return
        _uiState.update { it.copy(inputUrl = text, clipboardSuggestion = null) }
        submit()
    }

    fun dismissClipboardSuggestion() = _uiState.update { it.copy(clipboardSuggestion = null) }

    fun reset() = _uiState.update { ShareOpenUiState() }

    fun submit() {
        val url = _uiState.value.inputUrl.trim()
        if (url.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val target = shareRepository.resolveInboundUrl(url)
            if (target == null) {
                _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = DomainError.ShareLinkUnsupported.toUserMessage())
                }
                return@launch
            }
            fetchShare(target, password = null)
        }
    }

    fun submitPassword() {
        val target = (_uiState.value.status as? ShareOpenStatus.NeedsPassword)?.target ?: return
        fetchShare(target, password = _uiState.value.passwordInput)
    }

    private fun fetchShare(target: ShareInboundTarget, password: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = shareRepository.getInboundShare(target.instanceId, target.sid, target.path, password)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isSubmitting = false, status = ShareOpenStatus.Resolved(result.data, target))
                }
                is ApiResult.Failure -> {
                    if (result.error == DomainError.SharePasswordRequired) {
                        _uiState.update {
                            it.copy(isSubmitting = false, status = ShareOpenStatus.NeedsPassword(target))
                        }
                    } else {
                        _uiState.update { it.copy(isSubmitting = false, errorMessage = result.error.toUserMessage()) }
                    }
                }
            }
        }
    }

    private fun looksLikeShareUrl(text: String): Boolean {
        val trimmed = text.trim()
        return (trimmed.startsWith("http://") || trimmed.startsWith("https://")) && trimmed.contains("/@s/")
    }
}
