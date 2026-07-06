package io.openlist.client.core.network

import io.openlist.client.core.common.DomainError
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * v1.0_EXECUTION_PLAN.md §4.3.2 / S1-T1: bare (non-envelope) HTTP responses
 * from a reverse proxy or a direct-link endpoint must not collapse into
 * DomainError.Unknown ("出现未知错误") — they should carry the same semantics
 * as an in-envelope error of the same HTTP status.
 */
class ErrorMappingTest {

    private fun httpException(code: Int, contentType: String?, body: String?): HttpException {
        val responseBody = (body ?: "").toResponseBody(contentType?.toMediaTypeOrNull())
        return HttpException(Response.error<Any>(code, responseBody))
    }

    @Test
    fun `bare 401 maps to Unauthorized`() {
        val error = httpException(401, "text/plain", "Unauthorized").toDomainError()
        assertEquals(DomainError.Unauthorized, error)
    }

    @Test
    fun `bare 403 without conflict wording maps to Forbidden`() {
        val error = httpException(403, "text/plain", "Forbidden").toDomainError()
        assertEquals(DomainError.Forbidden, error)
    }

    @Test
    fun `bare 404 maps to NotFound`() {
        val error = httpException(404, "text/html", "<html>404</html>").toDomainError()
        assertEquals(DomainError.NotFound, error)
    }

    @Test
    fun `bare 5xx maps to ServerError`() {
        val error = httpException(502, "text/html", "<html>bad gateway</html>").toDomainError()
        assertEquals(DomainError.ServerError, error)
    }

    @Test
    fun `bare 416 with text plain body keeps readable message (V-612)`() {
        val error = httpException(416, "text/plain", "invalid range: failed to overlap").toDomainError()
        assertTrue(error is DomainError.OpenListError)
        error as DomainError.OpenListError
        assertEquals(416, error.code)
        assertEquals("invalid range: failed to overlap", error.message)
    }

    @Test
    fun `bare error with no body at all falls back to HTTP status text`() {
        val error = httpException(405, null, null).toDomainError()
        assertTrue(error is DomainError.OpenListError)
        error as DomainError.OpenListError
        assertEquals(405, error.code)
        assertEquals("HTTP 405", error.message)
    }

    @Test
    fun `no longer collapses HttpException into Unknown`() {
        val error = httpException(418, "text/plain", "I'm a teapot").toDomainError()
        assertTrue(error !is DomainError.Unknown)
    }
}
