package io.openlist.client.feature.preview

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.designsystem.components.AppTopBar
import io.openlist.client.core.designsystem.components.EmptyState
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.ExternalOpenSheet
import io.openlist.client.core.designsystem.components.LoadingState
import io.openlist.client.core.designsystem.components.PrimaryButton
import io.openlist.client.core.designsystem.components.StatusBadge
import io.openlist.client.core.designsystem.components.StatusTone
import io.openlist.client.core.model.ExternalOpenTarget
import io.openlist.client.core.model.MediaSource
import io.openlist.client.core.model.PreviewKind

/**
 * Media player host page for the `player/{instanceId}?path={path}` route
 * (v0.4_EXECUTION_PLAN.md §11 S5-T2), replacing S1's `MediaPlayerPlaceholderScreen`.
 * Reached only from [PreviewScreen]'s IN_APP_VIDEO/IN_APP_AUDIO forwarding,
 * but is itself a standalone route/ViewModel -- it re-resolves the
 * [MediaSource] from scratch (V-401) rather than receiving one from the
 * caller.
 *
 * Three top-level states: loading, a resolve failure (with retry, or a
 * dedicated "该格式暂不支持播放" + external-open/download fallback for
 * [DomainError.MediaUnsupported]), or a resolved [MediaSource] handed to
 * [MediaPlayerSurface]/[AudioPlayerSurface] depending on [PreviewKind].
 */
@Composable
fun MediaPlayerScreen(
    instanceId: String,
    path: String,
    onBack: () -> Unit,
    viewModel: MediaPlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    MediaPlayerScaffold(
        path = path,
        uiState = uiState,
        onBack = onBack,
        onRetry = { viewModel.resolveMedia() },
        onPositionSample = { positionMs -> viewModel.updateLastKnownPosition(positionMs) },
        onHttpClientError = { viewModel.refreshAfterHttpError() },
        onDownload = { viewModel.download() },
        onResolveExternalOpen = { viewModel.resolveExternalOpen() },
        onDismissExternalOpenSheet = { viewModel.dismissExternalOpenSheet() },
        onExternalOpenError = { message -> viewModel.setExternalOpenError(message) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaPlayerScaffold(
    path: String,
    uiState: MediaPlayerUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPositionSample: (Long) -> Unit,
    onHttpClientError: suspend () -> MediaSource?,
    onDownload: () -> Unit,
    onResolveExternalOpen: () -> Unit,
    onDismissExternalOpenSheet: () -> Unit,
    onExternalOpenError: (String) -> Unit,
) {
    val mediaSource = uiState.mediaSource
    // Landscape immersive mode (handled inside MediaPlayerSurface) hides the
    // system status bar but this Scaffold's own AppTopBar is a separate,
    // in-app-content element -- PRD 12.9 point 2 asks specifically for the
    // *video surface* to go fullscreen/immersive in landscape, so the
    // top bar is intentionally left in place here for both orientations
    // rather than conditionally removed, keeping back-navigation reachable.
    Scaffold(
        topBar = {
            AppTopBar(
                title = mediaSource?.title ?: "播放",
                subtitle = path,
                onBack = onBack,
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding).fillMaxSize())

            mediaSource == null -> MediaPlayerErrorContent(
                uiState = uiState,
                onRetry = onRetry,
                onDownload = onDownload,
                onResolveExternalOpen = onResolveExternalOpen,
                onDismissExternalOpenSheet = onDismissExternalOpenSheet,
                onExternalOpenError = onExternalOpenError,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )

            uiState.kind == PreviewKind.AUDIO -> AudioPlayerSurface(
                mediaSource = mediaSource,
                scopedHeaders = uiState.scopedHeaders,
                initialPositionMs = uiState.lastKnownPositionMs,
                onPositionSample = onPositionSample,
                onHttpClientError = onHttpClientError,
                onTerminalError = { onExternalOpenError("播放失败，请重试或使用外部应用打开") },
                modifier = Modifier.padding(padding).fillMaxSize(),
            )

            else -> MediaPlayerSurface(
                mediaSource = mediaSource,
                scopedHeaders = uiState.scopedHeaders,
                initialPositionMs = uiState.lastKnownPositionMs,
                onPositionSample = onPositionSample,
                onHttpClientError = onHttpClientError,
                onTerminalError = { onExternalOpenError("播放失败，请重试或使用外部应用打开") },
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        }
    }
}

/**
 * Resolve-failure / retries-exhausted terminal state (S5-T4): distinguishes
 * [DomainError.MediaUnsupported] (a dedicated "该格式暂不支持播放" empty state,
 * matching [DomainError.toUserMessage]'s copy) from every other error
 * (generic retry). Both branches offer the same external-open/download
 * fallback via [ExternalOpenSheet], reusing the exact UX shape
 * [PreviewScreen]'s `ExternalOpenFallback` already established for S4.
 */
@Composable
private fun MediaPlayerErrorContent(
    uiState: MediaPlayerUiState,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
    onResolveExternalOpen: () -> Unit,
    onDismissExternalOpenSheet: () -> Unit,
    onExternalOpenError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    if (uiState.isMediaUnsupported) {
        EmptyState(
            title = "该格式暂不支持播放",
            description = uiState.externalOpenError ?: "可以尝试用其他应用打开或下载",
            modifier = modifier,
            action = {
                PrimaryButton(
                    text = "外部打开",
                    onClick = onResolveExternalOpen,
                    loading = uiState.isResolvingExternalOpen,
                )
            },
        )
    } else {
        ErrorBar(
            message = uiState.errorMessage ?: "加载失败",
            onRetry = onRetry,
            modifier = modifier.fillMaxWidth(),
        )
    }

    when (val downloadState = uiState.downloadState) {
        PreviewDownloadState.Idle -> Unit
        PreviewDownloadState.Enqueued -> StatusBadge(text = "已加入下载队列，请在通知栏查看进度", tone = StatusTone.SUCCESS)
        is PreviewDownloadState.Failed -> StatusBadge(text = downloadState.message, tone = StatusTone.ERROR)
    }

    val externalOpenTarget = uiState.externalOpenTarget
    if (uiState.showExternalOpenSheet && externalOpenTarget != null) {
        ExternalOpenSheet(
            canDownload = externalOpenTarget.canDownload,
            onOpenExternal = {
                openExternally(context, externalOpenTarget, onExternalOpenError)
                onDismissExternalOpenSheet()
            },
            onDownload = {
                onDownload()
                onDismissExternalOpenSheet()
            },
            onOpenWeb = {
                openInBrowser(context, externalOpenTarget, onExternalOpenError)
                onDismissExternalOpenSheet()
            },
            onDismiss = onDismissExternalOpenSheet,
        )
    }
}

/** Same Intent-construction shape as PreviewScreen.kt's private
 * `openExternally` -- kept as an independent copy rather than a shared
 * helper per the execution plan's explicit "independent implementation is
 * acceptable, don't over-engineer a shared helper" guidance for this Sprint. */
private fun openExternally(context: android.content.Context, target: ExternalOpenTarget, onError: (String) -> Unit) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(target.externalUri), target.mimeType ?: "*/*")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        onError(DomainError.ExternalOpenUnavailable.toUserMessage())
    }
}

private fun openInBrowser(context: android.content.Context, target: ExternalOpenTarget, onError: (String) -> Unit) {
    val webUrl = target.webUrl ?: target.externalUri
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        onError(DomainError.ExternalOpenUnavailable.toUserMessage())
    }
}
