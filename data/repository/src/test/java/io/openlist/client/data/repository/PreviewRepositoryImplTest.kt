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
import io.openlist.client.core.model.PreviewFallback
import io.openlist.client.core.model.PreviewKind
import io.openlist.client.core.model.PreviewOpenMode
import io.openlist.client.core.model.PreviewSource
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.ApiResponse
import io.openlist.client.core.network.dto.FsGetResp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers PreviewRepositoryImpl.resolvePreview's kind -> openMode/fallbacks
 * decision table (v0.4_EXECUTION_PLAN.md §11 S2-T1), the raw_url-blank
 * fallback to a signed /d/ URL, the 401 -> sessionManager.invalidate path,
 * and the InvalidInstance short-circuit. Network/instance collaborators are
 * mocked following FileOperationRepositoryImplTest's established pattern.
 */
class PreviewRepositoryImplTest {

    private val api = mockk<OpenListApi>()
    private val instanceRepository = mockk<InstanceRepository>()
    private val clientFactory = mockk<OpenListClientFactory>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)

    private lateinit var repository: PreviewRepositoryImpl

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
        repository = PreviewRepositoryImpl(
            instanceRepository = instanceRepository,
            clientFactory = clientFactory,
            instanceContext = InstanceContext(),
            sessionManager = sessionManager,
        )
    }

    @Test
    fun `image resolves to IN_APP_IMAGE with download and external app fallbacks`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "photo.jpg", rawUrl = "https://example.com/d/photo.jpg"))

        val target = (repository.resolvePreview(INSTANCE_ID, "/photo.jpg") as ApiResult.Success).data

        assertEquals(PreviewKind.IMAGE, target.kind)
        assertEquals(PreviewOpenMode.IN_APP_IMAGE, target.openMode)
        assertEquals(listOf(PreviewFallback.DOWNLOAD, PreviewFallback.EXTERNAL_APP), target.fallbacks)
    }

    @Test
    fun `text resolves to IN_APP_TEXT with download and external app fallbacks`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "notes.txt", rawUrl = "https://example.com/d/notes.txt"))

        val target = (repository.resolvePreview(INSTANCE_ID, "/notes.txt") as ApiResult.Success).data

        assertEquals(PreviewKind.TEXT, target.kind)
        assertEquals(PreviewOpenMode.IN_APP_TEXT, target.openMode)
        assertEquals(listOf(PreviewFallback.DOWNLOAD, PreviewFallback.EXTERNAL_APP), target.fallbacks)
    }

    @Test
    fun `markdown resolves to IN_APP_MARKDOWN with download and external app fallbacks`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "README.md", rawUrl = "https://example.com/d/README.md"))

        val target = (repository.resolvePreview(INSTANCE_ID, "/README.md") as ApiResult.Success).data

        assertEquals(PreviewKind.MARKDOWN, target.kind)
        assertEquals(PreviewOpenMode.IN_APP_MARKDOWN, target.openMode)
        assertEquals(listOf(PreviewFallback.DOWNLOAD, PreviewFallback.EXTERNAL_APP), target.fallbacks)
    }

    @Test
    fun `video resolves to IN_APP_VIDEO with download and external app fallbacks`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "movie.mp4", rawUrl = "https://example.com/d/movie.mp4"))

        val target = (repository.resolvePreview(INSTANCE_ID, "/movie.mp4") as ApiResult.Success).data

        assertEquals(PreviewKind.VIDEO, target.kind)
        assertEquals(PreviewOpenMode.IN_APP_VIDEO, target.openMode)
        assertEquals(listOf(PreviewFallback.DOWNLOAD, PreviewFallback.EXTERNAL_APP), target.fallbacks)
    }

    @Test
    fun `audio resolves to IN_APP_AUDIO with download and external app fallbacks`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "song.mp3", rawUrl = "https://example.com/d/song.mp3"))

        val target = (repository.resolvePreview(INSTANCE_ID, "/song.mp3") as ApiResult.Success).data

        assertEquals(PreviewKind.AUDIO, target.kind)
        assertEquals(PreviewOpenMode.IN_APP_AUDIO, target.openMode)
        assertEquals(listOf(PreviewFallback.DOWNLOAD, PreviewFallback.EXTERNAL_APP), target.fallbacks)
    }

    @Test
    fun `pdf resolves to EXTERNAL_APP with download and web fallbacks`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "report.pdf", rawUrl = "https://example.com/d/report.pdf"))

        val target = (repository.resolvePreview(INSTANCE_ID, "/report.pdf") as ApiResult.Success).data

        assertEquals(PreviewKind.PDF, target.kind)
        assertEquals(PreviewOpenMode.EXTERNAL_APP, target.openMode)
        assertEquals(listOf(PreviewFallback.DOWNLOAD, PreviewFallback.WEB), target.fallbacks)
    }

    @Test
    fun `office resolves to EXTERNAL_APP with download and web fallbacks`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "report.docx", rawUrl = "https://example.com/d/report.docx"))

        val target = (repository.resolvePreview(INSTANCE_ID, "/report.docx") as ApiResult.Success).data

        assertEquals(PreviewKind.OFFICE, target.kind)
        assertEquals(PreviewOpenMode.EXTERNAL_APP, target.openMode)
        assertEquals(listOf(PreviewFallback.DOWNLOAD, PreviewFallback.WEB), target.fallbacks)
    }

    @Test
    fun `unknown resolves to UNSUPPORTED with download, external app and web fallbacks`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "archive.zip", rawUrl = "https://example.com/d/archive.zip"))

        val target = (repository.resolvePreview(INSTANCE_ID, "/archive.zip") as ApiResult.Success).data

        assertEquals(PreviewKind.UNKNOWN, target.kind)
        assertEquals(PreviewOpenMode.UNSUPPORTED, target.openMode)
        assertEquals(listOf(PreviewFallback.DOWNLOAD, PreviewFallback.EXTERNAL_APP, PreviewFallback.WEB), target.fallbacks)
    }

    @Test
    fun `blank raw_url falls back to a signed download URL`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "photo.jpg", rawUrl = "", sign = "abc123"))

        val target = (repository.resolvePreview(INSTANCE_ID, "/photo.jpg") as ApiResult.Success).data

        val source = target.source as PreviewSource.RemoteUrl
        assertTrue(source.url.contains("/d/"))
        assertTrue(source.url.contains("sign=abc123"))
        assertEquals(false, source.headersRequired)
    }

    @Test
    fun `directory response resolves defensively to UNSUPPORTED with no source or fallbacks`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "folder", isDir = true, rawUrl = ""))

        val target = (repository.resolvePreview(INSTANCE_ID, "/folder") as ApiResult.Success).data

        assertEquals(PreviewKind.UNKNOWN, target.kind)
        assertEquals(PreviewOpenMode.UNSUPPORTED, target.openMode)
        assertNull(target.source)
        assertEquals(emptyList<Any>(), target.fallbacks)
    }

    @Test
    fun `401 invalidates the session and propagates the failure`() = runTest {
        coEvery { api.fsGet(any()) } returns failure(401, "unauthorized")

        val result = repository.resolvePreview(INSTANCE_ID, "/photo.jpg")

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.Unauthorized, (result as ApiResult.Failure).error)
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
    }

    @Test
    fun `unknown instance returns InvalidInstance`() = runTest {
        coEvery { instanceRepository.getById("missing") } returns null

        val result = repository.resolvePreview("missing", "/photo.jpg")

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.InvalidInstance, (result as ApiResult.Failure).error)
    }

    private fun objResp(
        name: String,
        isDir: Boolean = false,
        rawUrl: String = "",
        sign: String = "",
    ) = FsGetResp(
        name = name,
        size = 100,
        isDir = isDir,
        modified = "",
        sign = sign,
        rawUrl = rawUrl,
    )

    private fun success(data: FsGetResp) = ApiResponse(code = 200, message = "success", data = data)

    private fun failure(code: Int, message: String) = ApiResponse<FsGetResp?>(code = code, message = message, data = null)

    private companion object {
        const val INSTANCE_ID = "inst-1"
    }
}
