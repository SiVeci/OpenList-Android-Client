package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.SubtitleRepository
import io.openlist.client.core.model.SubtitleCandidate
import io.openlist.client.core.model.SubtitleSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.4 Sprint 1 placeholder (v0.4_EXECUTION_PLAN.md §11 S1-T4) — see
 * [PreviewRepositoryImpl]'s KDoc for the shared rationale. Real subtitle
 * discovery/resolution logic lands in S5.
 */
@Singleton
class SubtitleRepositoryImpl @Inject constructor() : SubtitleRepository {

    override suspend fun findCandidates(instanceId: String, videoPath: String): ApiResult<List<SubtitleCandidate>> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun resolveSubtitle(instanceId: String, subtitlePath: String): ApiResult<SubtitleSource> =
        ApiResult.Failure(DomainError.Unknown(null))
}
