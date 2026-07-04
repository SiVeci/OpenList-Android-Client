package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.MediaRepository
import io.openlist.client.core.model.MediaSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.4 Sprint 1 placeholder (v0.4_EXECUTION_PLAN.md §11 S1-T4) — see
 * [PreviewRepositoryImpl]'s KDoc for the shared rationale. Real ExoPlayer
 * media source resolution lands in S4/S5.
 */
@Singleton
class MediaRepositoryImpl @Inject constructor() : MediaRepository {

    override suspend fun resolveMedia(instanceId: String, path: String): ApiResult<MediaSource> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun refreshMediaSource(instanceId: String, path: String): ApiResult<MediaSource> =
        ApiResult.Failure(DomainError.Unknown(null))
}
