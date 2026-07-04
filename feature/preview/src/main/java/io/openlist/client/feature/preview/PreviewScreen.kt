package io.openlist.client.feature.preview

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
import androidx.hilt.navigation.compose.hiltViewModel
import io.openlist.client.core.designsystem.components.AppTopBar
import io.openlist.client.core.designsystem.components.EmptyState
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.LoadingState
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
                onOpenMediaPlayer = onOpenMediaPlayer,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        }
    }
}

/**
 * Branches on every [PreviewOpenMode] value. IN_APP_IMAGE is the only real
 * renderer this Sprint delivers (S2-T3); every other branch is a
 * non-crashing placeholder with clear copy, left in place for later sprints
 * to replace in-line:
 *  - IN_APP_TEXT / IN_APP_MARKDOWN -> S3.
 *  - IN_APP_VIDEO / IN_APP_AUDIO -> forwards to the player route (S5 builds
 *    the real player; the route itself is still S1's placeholder screen).
 *  - EXTERNAL_APP / DOWNLOAD / WEB / UNSUPPORTED -> S4 (ExternalOpenSheet).
 */
@Composable
private fun PreviewContentByOpenMode(
    instanceId: String,
    target: PreviewTarget,
    onOpenMediaPlayer: (path: String) -> Unit,
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
                    modifier = modifier,
                )
            }
        }

        PreviewOpenMode.IN_APP_TEXT -> EmptyState(
            title = "文本预览即将上线",
            description = "本版本暂不支持在应用内查看，可下载后查看",
            modifier = modifier,
        )

        PreviewOpenMode.IN_APP_MARKDOWN -> EmptyState(
            title = "Markdown 预览即将上线",
            description = "本版本暂不支持在应用内查看，可下载后查看",
            modifier = modifier,
        )

        PreviewOpenMode.IN_APP_VIDEO, PreviewOpenMode.IN_APP_AUDIO -> {
            // No UI of its own: forwards straight to the (still-placeholder)
            // player route. LaunchedEffect keyed on the path so this only
            // fires once per resolved target, not on every recomposition.
            LaunchedEffect(target.path) {
                onOpenMediaPlayer(target.path)
            }
        }

        PreviewOpenMode.EXTERNAL_APP, PreviewOpenMode.DOWNLOAD, PreviewOpenMode.WEB -> EmptyState(
            title = "该格式暂不支持应用内查看",
            description = "后续版本将支持用外部应用打开或下载查看",
            modifier = modifier,
        )

        PreviewOpenMode.UNSUPPORTED -> EmptyState(
            title = "暂不支持预览该文件",
            description = "该文件类型当前无法识别或不受支持",
            modifier = modifier,
        )
    }
}
