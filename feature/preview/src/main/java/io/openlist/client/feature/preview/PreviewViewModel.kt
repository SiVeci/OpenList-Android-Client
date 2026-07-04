package io.openlist.client.feature.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.domain.PreviewRepository
import io.openlist.client.core.model.PreviewTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

/**
 * The preview host page's three states (v0.4_EXECUTION_PLAN.md §11 S2-T2):
 * loading, a resolved [PreviewTarget] ready for [PreviewScreen] to branch on
 * by [PreviewTarget.openMode], or a resolve failure with retry.
 */
data class PreviewUiState(
    val isLoading: Boolean = true,
    val target: PreviewTarget? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class PreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val previewRepository: PreviewRepository,
) : ViewModel() {

    private val instanceId: String = checkNotNull(savedStateHandle["instanceId"])
    private val path: String = savedStateHandle.get<String>("path")
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        ?: "/"

    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState.asStateFlow()

    init {
        resolvePreview()
    }

    /** Re-resolves fresh from the server every time — never reuses a
     * previously-resolved signed URL (V-401), so retry is just "call this
     * again" with no separate cache-bypass flag needed. */
    fun resolvePreview() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = previewRepository.resolvePreview(instanceId, path)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, target = result.data, errorMessage = null)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, target = null, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }
}
