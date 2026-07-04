package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.MediaSource

/**
 * Resolves a video/audio file path into a playable [MediaSource] for S4/S5's
 * ExoPlayer integration (v0.4_EXECUTION_PLAN.md §11, P-402). S1 scope:
 * interface + Hilt wiring only, real resolution logic lands in S4/S5.
 */
interface MediaRepository {
    suspend fun resolveMedia(instanceId: String, path: String): ApiResult<MediaSource>

    /** Re-resolves a fresh, non-expired [MediaSource.url] once the previously
     * resolved one has expired (mirrors [PreviewRepository.refreshPreviewUrl]). */
    suspend fun refreshMediaSource(instanceId: String, path: String): ApiResult<MediaSource>
}
