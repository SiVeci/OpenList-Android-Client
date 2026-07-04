package io.openlist.client.feature.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.LoadingState
import io.openlist.client.core.designsystem.components.SecondaryButton
import io.openlist.client.core.model.PreviewSource

/**
 * Builds the Coil memory/disk cache key for a preview image (v0.4_EXECUTION_PLAN.md
 * §11 S2-T3, R-409). A resolved preview URL carries a per-request `sign` query
 * parameter that changes on every re-resolve, so keying the cache on the URL
 * itself would either always miss (if the sign differs) or — worse — collide
 * across instances/paths if a signature were ever reused. Keying on
 * instanceId+path+modifiedAt instead makes the cache hit across repeated
 * visits to the *same* file version while still busting once the file changes.
 */
fun buildPreviewCacheKey(instanceId: String, path: String, modifiedAt: Long?): String =
    "$instanceId:$path:${modifiedAt ?: 0L}"

/**
 * Real in-app image preview (v0.4_EXECUTION_PLAN.md §11 S2-T3). Renders via
 * Coil's AsyncImage, scaled to fit the available space. On load failure, the
 * image is replaced by an [ErrorBar] plus a real download affordance (S4-T3
 * — [onDownload] delegates to [PreviewViewModel.download], the same
 * getFile-then-enqueueDownload flow `:feature:files` uses).
 */
@Composable
fun ImagePreviewSurface(
    source: PreviewSource.RemoteUrl,
    contentDescription: String,
    cacheKey: String,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var loadFailed by remember(cacheKey) { mutableStateOf(false) }
    var isLoading by remember(cacheKey) { mutableStateOf(true) }

    if (loadFailed) {
        Column(modifier = modifier.fillMaxSize()) {
            ErrorBar(message = "图片加载失败")
            SecondaryButton(
                text = "下载",
                onClick = onDownload,
            )
        }
        return
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(source.url)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onLoading = { isLoading = true },
            onSuccess = { isLoading = false },
            onError = {
                isLoading = false
                loadFailed = true
            },
        )
        if (isLoading) {
            LoadingState(modifier = Modifier.fillMaxSize())
        }
    }
}
