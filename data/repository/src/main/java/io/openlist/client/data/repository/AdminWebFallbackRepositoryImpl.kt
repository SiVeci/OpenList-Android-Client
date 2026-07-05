package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.AdminWebFallbackRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.AdminWebSection
import io.openlist.client.core.model.WebFallbackTarget
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real S7 implementation (v0.5_EXECUTION_PLAN.md §11 S7-T3), replacing the S1
 * stub. Constructs (never fetches/navigates) `${baseUrl}/@manage` -- the one
 * Web admin console path confirmed by V-508 (`server/static/static.go:220`).
 *
 * [section] is informational only ([WebFallbackTarget.sectionLabel]): V-508
 * only confirmed the base `/@manage` path, not any per-section deep-link
 * fragment (e.g. `/@manage/users`) -- no such fragment appears anywhere in
 * the source checkout, so guessing one would be exactly the kind of
 * unconfirmed-URL-shape risk the brief calls out. Every [AdminWebSection]
 * therefore resolves to the identical `/@manage` URL.
 *
 * Sub-path deployments are respected by simple string concatenation, not
 * [okhttp3.HttpUrl.Builder.addPathSegment] -- that method percent-encodes
 * `@` (turning `/@manage` into `/%40manage`), which would silently produce a
 * URL the server's static-file router never registered a route for.
 * [io.openlist.client.core.network.BaseUrlNormalizer] already guarantees
 * every stored [io.openlist.client.core.model.Instance.baseUrl] has no
 * trailing slash, so `"$baseUrl/@manage"` never produces a double slash;
 * `trimEnd('/')` is applied anyway as a defensive belt-and-braces measure in
 * case that invariant ever changes.
 *
 * **Same-origin validation**: [buildAdminUrl] parses both the instance's
 * stored [io.openlist.client.core.model.Instance.baseUrl] and the just-built
 * URL with [toHttpUrlOrNull] and asserts they share scheme+host+port before
 * returning a [WebFallbackTarget] -- structurally this can never fail (the
 * URL is built by direct string concatenation onto that exact same
 * `baseUrl`, with no external input in between), but the check is kept as an
 * explicit, testable assertion of the "URL must be prefixed by the current
 * instance's own base URL" security requirement rather than relying purely
 * on "trust the code path", per the brief's DoD.
 *
 * No token/`Authorization` query param is ever added -- there is nothing in
 * this function that could add one, since the only inputs are the instance's
 * `baseUrl` and a hard-coded literal path segment.
 */
@Singleton
class AdminWebFallbackRepositoryImpl @Inject constructor(
    private val instanceRepository: InstanceRepository,
) : AdminWebFallbackRepository {

    override suspend fun buildAdminUrl(instanceId: String, section: AdminWebSection): ApiResult<WebFallbackTarget> {
        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val baseUrl = instance.baseUrl
        val url = "${baseUrl.trimEnd('/')}$MANAGE_PATH"

        val baseOrigin = baseUrl.toHttpUrlOrNull() ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val builtOrigin = url.toHttpUrlOrNull() ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val sameOrigin = baseOrigin.scheme == builtOrigin.scheme &&
            baseOrigin.host == builtOrigin.host &&
            baseOrigin.port == builtOrigin.port
        if (!sameOrigin) {
            // Not reachable in practice (see class KDoc), but fails closed
            // rather than ever returning a cross-origin WebFallbackTarget.
            return ApiResult.Failure(DomainError.InvalidInstance)
        }

        return ApiResult.Success(
            WebFallbackTarget(
                url = url,
                sectionLabel = section.label(),
                // Always true (PRD/plan): native Token != Web session cookie,
                // so a fresh Web login may be required regardless of the
                // current native session's validity.
                requiresWebLogin = true,
            ),
        )
    }

    private companion object {
        const val MANAGE_PATH = "/@manage"
    }
}

private fun AdminWebSection.label(): String = when (this) {
    AdminWebSection.HOME -> "管理台首页"
    AdminWebSection.USERS -> "用户管理"
    AdminWebSection.STORAGES -> "存储管理"
    AdminWebSection.SETTINGS -> "设置"
    AdminWebSection.TASKS -> "任务管理"
    AdminWebSection.INDEX -> "索引管理"
}
