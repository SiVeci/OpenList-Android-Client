package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.SubtitleCandidate
import io.openlist.client.core.model.SubtitleSource

/**
 * Discovers and resolves subtitle tracks for a video preview
 * (v0.4_EXECUTION_PLAN.md §11, P-402). S1 scope: interface + Hilt wiring
 * only, real discovery/resolution logic lands in S5.
 */
interface SubtitleRepository {
    /** Sibling-file heuristics (e.g. a `.srt` next to the video) — returns
     * candidates, not resolved sources; call [resolveSubtitle] on the one
     * the user picks (or the first auto-discovered one) before handing it
     * to the player. */
    suspend fun findCandidates(instanceId: String, videoPath: String): ApiResult<List<SubtitleCandidate>>

    suspend fun resolveSubtitle(instanceId: String, subtitlePath: String): ApiResult<SubtitleSource>
}
