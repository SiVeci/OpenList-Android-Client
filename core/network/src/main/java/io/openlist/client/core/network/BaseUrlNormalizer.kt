package io.openlist.client.core.network

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Normalizes user-entered instance addresses (v0.1_PRD §5.1.1 / §6.3): trims
 * whitespace, requires an http(s) scheme, and strips the trailing slash so both
 * "https://host/" and "https://host" (and sub-path deployments) compare and
 * store identically. [OpenListClientFactory] re-adds a single trailing slash
 * itself since Retrofit's baseUrl requires one.
 */
object BaseUrlNormalizer {

    data class Normalized(val baseUrl: String, val displayName: String)

    fun normalize(raw: String): ApiResult<Normalized> {
        val trimmed = raw.trim()
        if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) {
            return ApiResult.Failure(DomainError.InvalidInstance)
        }
        val httpUrl = trimmed.toHttpUrlOrNull() ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val normalized = httpUrl.toString().trimEnd('/')
        if (normalized.isEmpty()) return ApiResult.Failure(DomainError.InvalidInstance)
        return ApiResult.Success(Normalized(baseUrl = normalized, displayName = httpUrl.host))
    }
}
