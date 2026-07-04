package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.Instance
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.ApiResponse
import io.openlist.client.core.network.dto.FsGetResp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers ExternalOpenRepositoryImpl.resolveExternalOpen (v0.4_EXECUTION_PLAN.md
 * §11 S4-T1): the raw_url-blank fallback to a signed /d/ URL, the 401 ->
 * sessionManager.invalidate path, the InvalidInstance short-circuit, and the
 * MIME-guessing delegation — following the same mocked-collaborator pattern
 * as PreviewRepositoryImplTest.
 *
 * The security assertion in the first test (no `Authorization`/`token`
 * substring in either resolved URL field) is the PRD-mandated proof that
 * V-402's "publicly fetchable signed URL, no auth header" conclusion holds
 * for both [io.openlist.client.core.model.ExternalOpenTarget.externalUri] and
 * [io.openlist.client.core.model.ExternalOpenTarget.webUrl].
 */
class ExternalOpenRepositoryImplTest {

    private val api = mockk<OpenListApi>()
    private val instanceRepository = mockk<InstanceRepository>()
    private val clientFactory = mockk<OpenListClientFactory>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val mimeTypeResolver = FakeMimeTypeResolver()

    private lateinit var repository: ExternalOpenRepositoryImpl

    @Before
    fun setUp() {
        val instance = Instance(
            id = INSTANCE_ID,
            name = "Test",
            baseUrl = "https://example.com/",
            createdAt = 0,
            updatedAt = 0,
            lastUsedAt = 0,
            isCurrent = true,
            note = null,
        )
        coEvery { instanceRepository.getById(INSTANCE_ID) } returns instance
        every { clientFactory.apiFor(any()) } returns api
        repository = ExternalOpenRepositoryImpl(
            instanceRepository = instanceRepository,
            clientFactory = clientFactory,
            instanceContext = InstanceContext(),
            sessionManager = sessionManager,
            mimeTypeResolver = mimeTypeResolver,
        )
    }

    @Test
    fun `resolves externalUri and webUrl to the same raw_url with no auth token embedded`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "report.pdf", rawUrl = "https://example.com/d/report.pdf?sign=abc123"))

        val target = (repository.resolveExternalOpen(INSTANCE_ID, "/report.pdf") as ApiResult.Success).data

        assertEquals("https://example.com/d/report.pdf?sign=abc123", target.externalUri)
        assertEquals("https://example.com/d/report.pdf?sign=abc123", target.webUrl)
        assertFalse(target.externalUri.contains("Authorization", ignoreCase = true))
        assertFalse(target.externalUri.contains("token", ignoreCase = true))
        assertFalse(target.webUrl!!.contains("Authorization", ignoreCase = true))
        assertFalse(target.webUrl!!.contains("token", ignoreCase = true))
        assertTrue(target.canDownload)
    }

    @Test
    fun `blank raw_url falls back to a signed download URL for both externalUri and webUrl`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "photo.jpg", rawUrl = "", sign = "abc123"))

        val target = (repository.resolveExternalOpen(INSTANCE_ID, "/photo.jpg") as ApiResult.Success).data

        assertTrue(target.externalUri.contains("/d/"))
        assertTrue(target.externalUri.contains("sign=abc123"))
        assertEquals(target.externalUri, target.webUrl)
        assertFalse(target.externalUri.contains("Authorization", ignoreCase = true))
    }

    @Test
    fun `blank raw_url and unbuildable download URL fails with Unknown`() = runTest {
        coEvery { instanceRepository.getById(INSTANCE_ID) } returns unresolvableUrlInstance()
        coEvery { api.fsGet(any()) } returns success(objResp(name = "photo.jpg", rawUrl = ""))

        val result = repository.resolveExternalOpen(INSTANCE_ID, "/photo.jpg")

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.Unknown(null), (result as ApiResult.Failure).error)
    }

    @Test
    fun `401 invalidates the session and propagates the failure`() = runTest {
        coEvery { api.fsGet(any()) } returns failure(401, "unauthorized")

        val result = repository.resolveExternalOpen(INSTANCE_ID, "/photo.jpg")

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.Unauthorized, (result as ApiResult.Failure).error)
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
    }

    @Test
    fun `unknown instance returns InvalidInstance`() = runTest {
        coEvery { instanceRepository.getById("missing") } returns null

        val result = repository.resolveExternalOpen("missing", "/photo.jpg")

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.InvalidInstance, (result as ApiResult.Failure).error)
    }

    @Test
    fun `mime type is guessed from the file extension for common document types`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "report.pdf", rawUrl = "https://example.com/d/report.pdf"))

        val target = (repository.resolveExternalOpen(INSTANCE_ID, "/report.pdf") as ApiResult.Success).data

        assertEquals("application/pdf", target.mimeType)
    }

    @Test
    fun `mime type is guessed for docx and xlsx extensions`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "report.docx", rawUrl = "https://example.com/d/report.docx"))
        val docxTarget = (repository.resolveExternalOpen(INSTANCE_ID, "/report.docx") as ApiResult.Success).data
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxTarget.mimeType)

        coEvery { api.fsGet(any()) } returns success(objResp(name = "sheet.xlsx", rawUrl = "https://example.com/d/sheet.xlsx"))
        val xlsxTarget = (repository.resolveExternalOpen(INSTANCE_ID, "/sheet.xlsx") as ApiResult.Success).data
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxTarget.mimeType)
    }

    @Test
    fun `mime type is null when the extension is unrecognized or missing`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "archive.zzzz", rawUrl = "https://example.com/d/archive.zzzz"))
        val target = (repository.resolveExternalOpen(INSTANCE_ID, "/archive.zzzz") as ApiResult.Success).data
        assertNull(target.mimeType)

        coEvery { api.fsGet(any()) } returns success(objResp(name = "noextension", rawUrl = "https://example.com/d/noextension"))
        val target2 = (repository.resolveExternalOpen(INSTANCE_ID, "/noextension") as ApiResult.Success).data
        assertNull(target2.mimeType)
    }

    private fun unresolvableUrlInstance() = Instance(
        id = INSTANCE_ID,
        name = "Test",
        baseUrl = "not-a-valid-url",
        createdAt = 0,
        updatedAt = 0,
        lastUsedAt = 0,
        isCurrent = true,
        note = null,
    )

    private fun objResp(
        name: String,
        isDir: Boolean = false,
        rawUrl: String = "",
        sign: String = "",
        size: Long = 100,
    ) = FsGetResp(
        name = name,
        size = size,
        isDir = isDir,
        modified = "",
        sign = sign,
        rawUrl = rawUrl,
    )

    private fun success(data: FsGetResp) = ApiResponse(code = 200, message = "success", data = data)

    private fun failure(code: Int, message: String) = ApiResponse<FsGetResp>(code = code, message = message, data = null)

    /** Trivial fake standing in for [AndroidMimeTypeResolver] — the real
     * implementation calls `android.webkit.MimeTypeMap.getSingleton()`,
     * which is not mocked in this project's plain-JVM unit test environment
     * (no Robolectric, no `isReturnDefaultValues`). This fake covers the
     * handful of extensions this test suite cares about. */
    private class FakeMimeTypeResolver : MimeTypeResolver {
        private val table = mapOf(
            "pdf" to "application/pdf",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "jpg" to "image/jpeg",
        )

        override fun guessMimeType(extensionLowercase: String): String? = table[extensionLowercase]
    }

    private companion object {
        const val INSTANCE_ID = "inst-1"
    }
}
