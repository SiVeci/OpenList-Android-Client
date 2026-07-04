package io.openlist.client.feature.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.domain.PreviewRepository
import io.openlist.client.core.model.MarkdownPreviewContent
import io.openlist.client.core.model.PreviewTarget
import io.openlist.client.core.model.TextPreviewContent
import io.openlist.client.core.model.TextPreviewOptions
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
 *
 * [textBodyState]/[markdownBodyState] (S3-T2/T3) are populated once
 * [target] resolves to IN_APP_TEXT/IN_APP_MARKDOWN — kept as separate
 * fields rather than folded into one "body" union because a target's kind
 * never changes after it resolves, so a screen only ever reads the one
 * field matching its own openMode branch.
 */
data class PreviewUiState(
    val isLoading: Boolean = true,
    val target: PreviewTarget? = null,
    val errorMessage: String? = null,
    val textBodyState: PreviewBodyState<TextPreviewContent> = PreviewBodyState.Loading,
    val markdownBodyState: PreviewBodyState<MarkdownPreviewContent> = PreviewBodyState.Loading,
)

/** Loading/content/error states for a text or markdown body fetch, kept
 * generic over the content payload so [PreviewViewModel.loadText] and
 * [PreviewViewModel.loadMarkdown] share the same shape. [Error.isTooLarge]
 * lets the screen branch to a distinct "文件过大" affordance (no retry, only
 * download) versus a generic retryable error, without the screen needing to
 * pattern-match on [DomainError] itself. */
sealed class PreviewBodyState<out T> {
    data object Loading : PreviewBodyState<Nothing>()
    data class Content<T>(val content: T) : PreviewBodyState<T>()
    data class Error(val message: String, val isTooLarge: Boolean) : PreviewBodyState<Nothing>()
}

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

    /** [forceRefresh] bypasses the `preview_cache` row — used by the body's
     * own retry affordance so a retry after e.g. a mid-stream network drop
     * doesn't just replay a half-written cache file. */
    fun loadText(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(textBodyState = PreviewBodyState.Loading) }
            val result = previewRepository.loadText(instanceId, path, TextPreviewOptions(forceRefresh = forceRefresh))
            _uiState.update { it.copy(textBodyState = result.toBodyState()) }
        }
    }

    fun loadMarkdown(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(markdownBodyState = PreviewBodyState.Loading) }
            val result = previewRepository.loadMarkdown(instanceId, path, forceRefresh)
            _uiState.update { it.copy(markdownBodyState = result.toBodyState()) }
        }
    }

    private fun <T> ApiResult<T>.toBodyState(): PreviewBodyState<T> = when (this) {
        is ApiResult.Success -> PreviewBodyState.Content(data)
        is ApiResult.Failure -> PreviewBodyState.Error(
            message = error.toUserMessage(),
            isTooLarge = error == DomainError.PreviewTooLarge,
        )
    }
}
