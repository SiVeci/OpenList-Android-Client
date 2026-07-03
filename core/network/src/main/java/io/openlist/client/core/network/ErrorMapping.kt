package io.openlist.client.core.network

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.network.dto.ApiResponse
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

/**
 * Central translation of OpenList envelopes and transport exceptions into the
 * app's [ApiResult]/[DomainError] (v0.1_PRD §7.1, §11.1). Repositories call
 * [safeApiCall]; nothing else interprets HTTP/JSON errors.
 */

/**
 * Maps a decoded [ApiResponse] envelope to an [ApiResult] using its `code`.
 * Every v0.1 endpoint's success path always carries a real `data` object per
 * the OpenList source (v0.1_EXECUTION_PLAN.md §8 API contract notes), so a
 * success code with a null body is treated as a malformed response rather
 * than silently coerced to a placeholder value — that coercion previously
 * used an unchecked `Unit as T` cast that could throw ClassCastException at
 * the call site instead of surfacing a normal DomainError here.
 */
fun <T> ApiResponse<T>.toApiResult(): ApiResult<T> {
    return if (code in 200..299 && data != null) {
        ApiResult.Success(data)
    } else if (code in 200..299) {
        ApiResult.Failure(DomainError.Unknown(IllegalStateException("Success response ($code) had no data")))
    } else {
        ApiResult.Failure(codeToDomainError(code, message))
    }
}

/** Runs a suspending API call and normalizes every failure into a [DomainError]. */
suspend fun <T> safeApiCall(block: suspend () -> ApiResponse<T>): ApiResult<T> {
    return try {
        block().toApiResult()
    } catch (t: Throwable) {
        ApiResult.Failure(t.toDomainError())
    }
}

/**
 * Like [safeApiCall], for write endpoints whose success response carries no
 * usable `data` (mkdir/rename/remove always respond with `data: null`; move/
 * copy's `{message, tasks?}` isn't modeled — see OpenListApi doc comments).
 * Success is judged purely by `code`, never by data presence.
 */
suspend fun safeApiCallUnit(block: suspend () -> ApiResponse<*>): ApiResult<Unit> {
    return try {
        val response = block()
        if (response.code in 200..299) {
            ApiResult.Success(Unit)
        } else {
            ApiResult.Failure(codeToDomainError(response.code, response.message))
        }
    } catch (t: Throwable) {
        ApiResult.Failure(t.toDomainError())
    }
}

private fun codeToDomainError(code: Int, message: String): DomainError = when (code) {
    401 -> DomainError.Unauthorized
    // The server reuses HTTP 403 for both real permission-denied responses and
    // naming conflicts (e.g. rename/move/copy without overwrite: `file [x]
    // exists`, server/handles/fsmanage.go) — same status, no distinct code.
    // Only the message tells them apart, so a conflict-shaped message is kept
    // verbatim (OpenListError) instead of being collapsed into the generic
    // "no permission" copy, per v0.2_EXECUTION_PLAN.md §10.3/P1.
    403 -> if (message.contains("exist", ignoreCase = true)) {
        DomainError.OpenListError(code, message)
    } else {
        DomainError.Forbidden
    }
    404 -> DomainError.NotFound
    in 500..599 -> DomainError.ServerError
    else -> DomainError.OpenListError(code, message)
}

fun Throwable.toDomainError(): DomainError = when (this) {
    is SSLHandshakeException, is SSLException -> DomainError.CertificateError
    is SocketTimeoutException -> DomainError.Timeout
    is UnknownHostException -> DomainError.NetworkUnavailable
    is SerializationException -> DomainError.InvalidInstance
    is IOException -> DomainError.NetworkUnavailable
    else -> DomainError.Unknown(this)
}
