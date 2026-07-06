package io.openlist.client.feature.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.domain.ExternalOpenRepository
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.PreviewRepository
import io.openlist.client.core.domain.TransferRepository
import io.openlist.client.core.model.ExternalOpenTarget
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
 *
 * [externalOpenTarget]/[showExternalOpenSheet] (S4-T3) back the
 * EXTERNAL_APP/UNSUPPORTED branches' [io.openlist.client.core.designsystem.components.ExternalOpenSheet]:
 * [showExternalOpenSheet] is set once [resolveExternalOpen] succeeds, and
 * [externalOpenTarget] carries the resolved URLs/mimeType the sheet's
 * callbacks need to build Intents.
 */
data class PreviewUiState(
    val isLoading: Boolean = true,
    val target: PreviewTarget? = null,
    val errorMessage: String? = null,
    val textBodyState: PreviewBodyState<TextPreviewContent> = PreviewBodyState.Loading,
    val markdownBodyState: PreviewBodyState<MarkdownPreviewContent> = PreviewBodyState.Loading,
    val isResolvingExternalOpen: Boolean = false,
    val externalOpenTarget: ExternalOpenTarget? = null,
    val externalOpenError: String? = null,
    val showExternalOpenSheet: Boolean = false,
    val downloadState: PreviewDownloadState = PreviewDownloadState.Idle,
)

/** Preview's own three-state download status — deliberately not shared with
 * `:feature:files`' `DownloadUiState` (same shape, different type) since
 * `:feature:preview` must not depend on another feature module. */
sealed class PreviewDownloadState {
    data object Idle : PreviewDownloadState()
    data object Enqueued : PreviewDownloadState()
    data class Failed(val message: String) : PreviewDownloadState()
}

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
    private val externalOpenRepository: ExternalOpenRepository,
    private val filesRepository: FilesRepository,
    private val transferRepository: TransferRepository,
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
            // v1.0 S4: rewrite embedded image refs to their resolved signed
            // URLs before the body ever reaches the renderer — a failure here
            // is impossible by construction (resolveMarkdownImages never
            // throws, unresolvable refs are just left as-is), so this never
            // needs its own error branch.
            val withImages = when (result) {
                is ApiResult.Success -> ApiResult.Success(previewRepository.resolveMarkdownImages(instanceId, result.data))
                is ApiResult.Failure -> result
            }
            _uiState.update { it.copy(markdownBodyState = withImages.toBodyState()) }
        }
    }

    /** Resolves the external-open target (S4-T3) and, on success, flags the
     * UI to present [io.openlist.client.core.designsystem.components.ExternalOpenSheet].
     * Called from the EXTERNAL_APP/UNSUPPORTED branches once the user asks
     * for a fallback action. */
    fun resolveExternalOpen() {
        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingExternalOpen = true, externalOpenError = null) }
            when (val result = externalOpenRepository.resolveExternalOpen(instanceId, path)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isResolvingExternalOpen = false,
                        externalOpenTarget = result.data,
                        showExternalOpenSheet = true,
                        externalOpenError = null,
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        isResolvingExternalOpen = false,
                        externalOpenError = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    /** Dismisses the external-open sheet without discarding the already
     * resolved target — reopening it (e.g. after "外部打开" fails to find an
     * app and the user wants "下载" instead) doesn't need a new network call. */
    fun dismissExternalOpenSheet() {
        _uiState.update { it.copy(showExternalOpenSheet = false) }
    }

    /** Sets a one-off user-facing error message (e.g. `ActivityNotFoundException`
     * caught by the screen when no app can handle the resolved Intent) —
     * kept as a plain field, not a one-shot event channel, matching this
     * ViewModel's existing errorMessage/externalOpenError style. */
    fun setExternalOpenError(message: String) {
        _uiState.update { it.copy(externalOpenError = message) }
    }

    /** Same getFile-then-enqueueDownload combination as
     * [io.openlist.client.feature.files.FileDetailViewModel.download] — fetches
     * a fresh [io.openlist.client.core.model.FileDetail] (this preview target
     * doesn't carry the raw download metadata `TransferRepository` needs) and
     * hands it to the system download manager. */
    fun download() {
        viewModelScope.launch {
            when (val fileResult = filesRepository.getFile(instanceId, path)) {
                is ApiResult.Success -> {
                    when (val enqueueResult = transferRepository.enqueueDownload(instanceId, fileResult.data)) {
                        is ApiResult.Success -> _uiState.update { it.copy(downloadState = PreviewDownloadState.Enqueued) }
                        is ApiResult.Failure -> _uiState.update {
                            it.copy(downloadState = PreviewDownloadState.Failed(enqueueResult.error.toUserMessage()))
                        }
                    }
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(downloadState = PreviewDownloadState.Failed(fileResult.error.toUserMessage()))
                }
            }
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
