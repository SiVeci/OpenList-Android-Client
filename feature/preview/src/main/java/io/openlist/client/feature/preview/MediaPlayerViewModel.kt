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
import io.openlist.client.core.domain.MediaRepository
import io.openlist.client.core.domain.TransferRepository
import io.openlist.client.core.model.ExternalOpenTarget
import io.openlist.client.core.model.MediaSource
import io.openlist.client.core.model.PreviewKind
import io.openlist.client.core.model.PreviewKindResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

/**
 * The media player page's state (v0.4_EXECUTION_PLAN.md §11 S5-T2/S5-T4).
 * [kind] is resolved once from [MediaSource.title] via [PreviewKindResolver]
 * so [MediaPlayerScreen] knows whether to render the video or audio layout
 * (this route is reached directly -- it does not receive the [PreviewKind]
 * PreviewScreen already computed, per V-401 "always re-resolve").
 *
 * [refreshAttempts] and [lastKnownPositionMs] back the P-412 signature-expiry
 * refresh flow (S5-T4): the surface reports position samples up via
 * [MediaPlayerViewModel.updateLastKnownPosition], and on a 4xx playback error
 * the surface calls [MediaPlayerViewModel.refreshAfterHttpError] to decide
 * whether to refresh-and-retry or give up.
 */
data class MediaPlayerUiState(
    val isLoading: Boolean = true,
    val mediaSource: MediaSource? = null,
    val kind: PreviewKind = PreviewKind.UNKNOWN,
    val errorMessage: String? = null,
    val isMediaUnsupported: Boolean = false,
    val refreshAttempts: Int = 0,
    val lastKnownPositionMs: Long = 0L,
    /** Bumped every time a fresh (or refreshed) [MediaSource] is applied, so
     * the surface's `remember(...)` keys can tell "reuse the existing player"
     * apart from "the source changed, rebuild it" without comparing the
     * whole (possibly-identical-looking) [MediaSource] value. */
    val sourceRevision: Int = 0,
    /** Mirrors [MediaSource.headers] for the current [mediaSource] --
     * [MediaRepositoryImpl] computes this (host-scoped, ready-to-attach)
     * value at resolve time, not this ViewModel, per the architecture rule
     * that `:feature:preview` has no direct path to `TokenProvider`/instance
     * base URLs. Only ever non-empty when [MediaSource.headersRequired] is
     * true (fixed false as of this Sprint, see [MediaRepository]'s KDoc). */
    val scopedHeaders: Map<String, String> = emptyMap(),
    val isResolvingExternalOpen: Boolean = false,
    val externalOpenTarget: ExternalOpenTarget? = null,
    val externalOpenError: String? = null,
    val showExternalOpenSheet: Boolean = false,
    val downloadState: PreviewDownloadState = PreviewDownloadState.Idle,
)

@HiltViewModel
class MediaPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val externalOpenRepository: ExternalOpenRepository,
    private val filesRepository: FilesRepository,
    private val transferRepository: TransferRepository,
) : ViewModel() {

    private val instanceId: String = checkNotNull(savedStateHandle["instanceId"])
    private val path: String = savedStateHandle.get<String>("path")
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        ?: "/"

    private val _uiState = MutableStateFlow(MediaPlayerUiState())
    val uiState: StateFlow<MediaPlayerUiState> = _uiState.asStateFlow()

    init {
        resolveMedia()
    }

    /** Re-resolves fresh from the server every time (V-401), same rationale
     * as [PreviewViewModel.resolvePreview] -- this is also what backs the
     * user-facing "retry" affordance on a resolve failure. */
    fun resolveMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, isMediaUnsupported = false) }
            when (val result = mediaRepository.resolveMedia(instanceId, path)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            mediaSource = result.data,
                            kind = PreviewKindResolver.resolve(result.data.title),
                            errorMessage = null,
                            isMediaUnsupported = false,
                            sourceRevision = it.sourceRevision + 1,
                            scopedHeaders = result.data.headers,
                        )
                    }
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        mediaSource = null,
                        errorMessage = result.error.toUserMessage(),
                        isMediaUnsupported = result.error == DomainError.MediaUnsupported,
                    )
                }
            }
        }
    }

    /** Persists the player's current position so a rebuild (refresh retry,
     * or -- defensively, in case `configChanges` handling ever changes --
     * a recreated Activity) can seek back to roughly where the user left
     * off. Called by the surface on a low-frequency sampling cadence, not
     * every frame (see MediaPlayerSurface's polling interval). */
    fun updateLastKnownPosition(positionMs: Long) {
        if (positionMs < 0) return
        _uiState.update { it.copy(lastKnownPositionMs = positionMs) }
    }

    /**
     * P-412 entry point: the surface's `Player.Listener.onPlayerError` calls
     * this once it has classified the error as an HTTP 4xx (see
     * [MediaPlaybackErrorClassifier]). Attempts up to
     * [MAX_REFRESH_ATTEMPTS] refresh-and-retry cycles; beyond that (or on a
     * refresh failure) it settles into the terminal error state instead.
     *
     * Returns the freshly-refreshed [MediaSource] on success so the surface
     * can immediately rebuild its `MediaItem`, or null if the caller should
     * show the terminal error UI (either the attempt budget is spent, or the
     * refresh call itself failed).
     */
    suspend fun refreshAfterHttpError(): MediaSource? {
        val currentAttempts = _uiState.value.refreshAttempts
        if (currentAttempts >= MAX_REFRESH_ATTEMPTS) {
            _uiState.update {
                it.copy(errorMessage = "播放地址已失效，请重试", mediaSource = null)
            }
            return null
        }

        return when (val result = mediaRepository.refreshMediaSource(instanceId, path)) {
            is ApiResult.Success -> {
                _uiState.update {
                    it.copy(
                        mediaSource = result.data,
                        refreshAttempts = currentAttempts + 1,
                        errorMessage = null,
                        isMediaUnsupported = false,
                        sourceRevision = it.sourceRevision + 1,
                        scopedHeaders = result.data.headers,
                    )
                }
                result.data
            }
            is ApiResult.Failure -> {
                _uiState.update {
                    it.copy(
                        errorMessage = result.error.toUserMessage(),
                        mediaSource = null,
                        refreshAttempts = currentAttempts + 1,
                    )
                }
                null
            }
        }
    }

    /** Resolves the external-open target (reused for the terminal-error
     * fallback, same shape as [PreviewViewModel.resolveExternalOpen]). */
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

    fun dismissExternalOpenSheet() {
        _uiState.update { it.copy(showExternalOpenSheet = false) }
    }

    fun setExternalOpenError(message: String) {
        _uiState.update { it.copy(externalOpenError = message) }
    }

    /** Same getFile-then-enqueueDownload combination as
     * [PreviewViewModel.download]. */
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

    companion object {
        /** P-412: at most 2 refresh-and-retry cycles before giving up and
         * showing the terminal error UI. */
        const val MAX_REFRESH_ATTEMPTS = 2
    }
}
