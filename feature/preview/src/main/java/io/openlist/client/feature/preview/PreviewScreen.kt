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
import androidx.compose.runtime.LaunchedEffect
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
import io.openlist.client.core.model.PreviewOpenMode
import io.openlist.client.core.model.PreviewSource
import io.openlist.client.core.model.PreviewTarget

/**
 * Preview host page for the `preview/{instanceId}?path={path}` route
 * (v0.4_EXECUTION_PLAN.md §11 S2-T2). Resolves the target once via
 * [PreviewViewModel], then hands off to [PreviewScaffold] to branch on
 * [PreviewTarget.openMode]. Video/audio never render anything here — they
 * immediately forward to the media player route via [onOpenMediaPlayer] and
 * pop this screen off the back stack (S2 scope: the player screen itself is
 * still S1's placeholder; S5 builds the real player).
 */
@Composable
fun PreviewScreen(
    instanceId: String,
    path: String,
    onBack: () -> Unit,
    onOpenMediaPlayer: (path: String) -> Unit,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    PreviewScaffold(
        instanceId = instanceId,
        path = path,
        uiState = uiState,
        onBack = onBack,
        onRetry = { viewModel.resolvePreview() },
        onOpenMediaPlayer = onOpenMediaPlayer,
        onLoadText = { forceRefresh -> viewModel.loadText(forceRefresh) },
        onLoadMarkdown = { forceRefresh -> viewModel.loadMarkdown(forceRefresh) },
        onDownload = { viewModel.download() },
        onResolveExternalOpen = { viewModel.resolveExternalOpen() },
        onDismissExternalOpenSheet = { viewModel.dismissExternalOpenSheet() },
        onExternalOpenError = { message -> viewModel.setExternalOpenError(message) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScaffold(
    instanceId: String,
    path: String,
    uiState: PreviewUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenMediaPlayer: (path: String) -> Unit,
    onLoadText: (forceRefresh: Boolean) -> Unit = {},
    onLoadMarkdown: (forceRefresh: Boolean) -> Unit = {},
    onDownload: () -> Unit = {},
    onResolveExternalOpen: () -> Unit = {},
    onDismissExternalOpenSheet: () -> Unit = {},
    onExternalOpenError: (String) -> Unit = {},
) {
    val target = uiState.target
    Scaffold(
        topBar = {
            AppTopBar(
                title = target?.name ?: "预览",
                subtitle = path,
                onBack = onBack,
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding).fillMaxSize())
            target == null -> ErrorBar(
                message = uiState.errorMessage ?: "加载失败",
                onRetry = onRetry,
                modifier = Modifier.padding(padding).fillMaxWidth(),
            )
            else -> PreviewContentByOpenMode(
                instanceId = instanceId,
                target = target,
                uiState = uiState,
                onOpenMediaPlayer = onOpenMediaPlayer,
                onLoadText = onLoadText,
                onLoadMarkdown = onLoadMarkdown,
                onDownload = onDownload,
                onResolveExternalOpen = onResolveExternalOpen,
                onDismissExternalOpenSheet = onDismissExternalOpenSheet,
                onExternalOpenError = onExternalOpenError,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        }
    }
}

/**
 * Branches on every [PreviewOpenMode] value. IN_APP_IMAGE/IN_APP_TEXT/
 * IN_APP_MARKDOWN are the real renderers delivered so far (S2-T3, S3-T2,
 * S3-T3); IN_APP_VIDEO/IN_APP_AUDIO forward to the (still-placeholder until
 * S5) player route.
 *
 * EXTERNAL_APP and UNSUPPORTED (S4-T3) share one UX: both mean "this app
 * can't render the file", the only difference being *why* — so both show
 * the same "打不开，给兜底选项" affordance ([ExternalOpenFallback]) rather than
 * two near-duplicate composables. DOWNLOAD/WEB are covered defensively for
 * `when` exhaustiveness even though [resolvePreview]'s decision table never
 * currently produces them as a top-level `openMode` (see
 * `PreviewRepositoryImpl.openModeAndFallbacksFor` — they only ever appear
 * inside a target's `fallbacks` list).
 */
@Composable
private fun PreviewContentByOpenMode(
    instanceId: String,
    target: PreviewTarget,
    uiState: PreviewUiState,
    onOpenMediaPlayer: (path: String) -> Unit,
    onLoadText: (forceRefresh: Boolean) -> Unit,
    onLoadMarkdown: (forceRefresh: Boolean) -> Unit,
    onDownload: () -> Unit,
    onResolveExternalOpen: () -> Unit,
    onDismissExternalOpenSheet: () -> Unit,
    onExternalOpenError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (target.openMode) {
        PreviewOpenMode.IN_APP_IMAGE -> {
            val source = target.source as? PreviewSource.RemoteUrl
            if (source == null) {
                EmptyState(
                    title = "图片地址不可用",
                    description = "未能获取到可用的预览地址",
                    modifier = modifier,
                )
            } else {
                ImagePreviewSurface(
                    source = source,
                    contentDescription = target.name,
                    cacheKey = buildPreviewCacheKey(instanceId, target.path, target.modifiedAt),
                    onDownload = onDownload,
                    modifier = modifier,
                )
            }
        }

        PreviewOpenMode.IN_APP_TEXT -> {
            LaunchedEffect(target.path) { onLoadText(false) }
            when (val bodyState = uiState.textBodyState) {
                is PreviewBodyState.Loading -> LoadingState(modifier = modifier)
                is PreviewBodyState.Content -> TextPreviewSurface(content = bodyState.content, onDownload = onDownload, modifier = modifier)
                is PreviewBodyState.Error -> if (bodyState.isTooLarge) {
                    EmptyState(
                        title = "文件过大，无法预览",
                        description = "可下载后查看完整内容",
                        modifier = modifier,
                    )
                } else {
                    ErrorBar(
                        message = bodyState.message,
                        onRetry = { onLoadText(true) },
                        modifier = modifier.fillMaxWidth(),
                    )
                }
            }
        }

        PreviewOpenMode.IN_APP_MARKDOWN -> {
            LaunchedEffect(target.path) { onLoadMarkdown(false) }
            when (val bodyState = uiState.markdownBodyState) {
                is PreviewBodyState.Loading -> LoadingState(modifier = modifier)
                is PreviewBodyState.Content -> MarkdownPreviewSurface(content = bodyState.content, modifier = modifier)
                is PreviewBodyState.Error -> if (bodyState.isTooLarge) {
                    EmptyState(
                        title = "文件过大，无法预览",
                        description = "可下载后查看完整内容",
                        modifier = modifier,
                    )
                } else {
                    ErrorBar(
                        message = bodyState.message,
                        onRetry = { onLoadMarkdown(true) },
                        modifier = modifier.fillMaxWidth(),
                    )
                }
            }
        }

        PreviewOpenMode.IN_APP_VIDEO, PreviewOpenMode.IN_APP_AUDIO -> {
            // No UI of its own: forwards straight to the (still-placeholder)
            // player route. LaunchedEffect keyed on the path so this only
            // fires once per resolved target, not on every recomposition.
            LaunchedEffect(target.path) {
                onOpenMediaPlayer(target.path)
            }
        }

        PreviewOpenMode.EXTERNAL_APP, PreviewOpenMode.UNSUPPORTED, PreviewOpenMode.DOWNLOAD, PreviewOpenMode.WEB -> {
            ExternalOpenFallback(
                target = target,
                uiState = uiState,
                onResolveExternalOpen = onResolveExternalOpen,
                onDismissExternalOpenSheet = onDismissExternalOpenSheet,
                onExternalOpenError = onExternalOpenError,
                onDownload = onDownload,
                modifier = modifier,
            )
        }
    }
}

/**
 * Shared fallback UX for EXTERNAL_APP/UNSUPPORTED (and the defensively
 * covered DOWNLOAD/WEB, which resolvePreview never actually produces as a
 * top-level openMode today): an [EmptyState] with a single "外部打开" action
 * that resolves an [ExternalOpenTarget] on demand (S4-T3) and, once
 * resolved, presents [ExternalOpenSheet] for the user to pick "外部打开" /
 * "下载" / "网页打开". Intent construction lives here (not in the sheet, which
 * stays pure UI) since that's this screen's job per the unified-dispatch
 * architecture.
 */
@Composable
private fun ExternalOpenFallback(
    target: PreviewTarget,
    uiState: PreviewUiState,
    onResolveExternalOpen: () -> Unit,
    onDismissExternalOpenSheet: () -> Unit,
    onExternalOpenError: (String) -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    EmptyState(
        title = if (target.openMode == PreviewOpenMode.UNSUPPORTED) "暂不支持预览该文件" else "该格式暂不支持应用内查看",
        description = uiState.externalOpenError ?: "可以尝试用其他应用打开、下载或在浏览器中查看",
        modifier = modifier,
        action = {
            PrimaryButton(
                text = "外部打开",
                onClick = onResolveExternalOpen,
                loading = uiState.isResolvingExternalOpen,
            )
        },
    )

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

/** `Intent.ACTION_VIEW` with an explicit MIME type so the system chooser
 * only offers apps that claim to handle it. [DomainError.ExternalOpenUnavailable]'s
 * copy surfaces if no such app is installed — this is the only place that
 * error is ever produced, since it's purely a client-side "no app claimed
 * this Intent" condition, not something the backend can report. */
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

/** `Intent.ACTION_VIEW` with no MIME type set, letting the system match by
 * URL scheme alone — for an http(s) URL this virtually always resolves to a
 * browser, unlike [openExternally]'s MIME-scoped chooser. */
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
