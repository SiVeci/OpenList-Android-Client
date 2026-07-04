package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.auth.TokenProvider
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
 * Covers MediaRepositoryImpl.resolveMedia/refreshMediaSource
 * (v0.4_EXECUTION_PLAN.md §11 S5-T1): the raw_url-blank fallback to a signed
 * /d/ URL, blank-url -> MediaUnsupported, the fixed headersRequired=false/
 * expiresAt=null/subtitles=emptyList() shape, the 401 -> sessionManager.invalidate
 * path, the InvalidInstance short-circuit, and that refreshMediaSource shares
 * the exact same resolution logic as resolveMedia -- following the same
 * mocked-collaborator pattern as PreviewRepositoryImplTest/ExternalOpenRepositoryImplTest.
 */
class MediaRepositoryImplTest {

    private val api = mockk<OpenListApi>()
    private val instanceRepository = mockk<InstanceRepository>()
    private val clientFactory = mockk<OpenListClientFactory>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val mimeTypeResolver = FakeMimeTypeResolver()
    private val tokenProvider = mockk<TokenProvider>(relaxed = true)

    private lateinit var repository: MediaRepositoryImpl

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
        repository = MediaRepositoryImpl(
            instanceRepository = instanceRepository,
            clientFactory = clientFactory,
            instanceContext = InstanceContext(),
            sessionManager = sessionManager,
            mimeTypeResolver = mimeTypeResolver,
            tokenProvider = tokenProvider,
        )
    }

    @Test
    fun `resolves a MediaSource from raw_url with fixed headersRequired false, null expiresAt and no subtitles`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "movie.mp4", rawUrl = "https://example.com/d/movie.mp4?sign=abc123"))

        val source = (repository.resolveMedia(INSTANCE_ID, "/movie.mp4") as ApiResult.Success).data

        assertEquals(INSTANCE_ID, source.instanceId)
        assertEquals("/movie.mp4", source.path)
        assertEquals("movie.mp4", source.title)
        assertEquals("https://example.com/d/movie.mp4?sign=abc123", source.url)
        assertFalse(source.headersRequired)
        assertNull(source.expiresAt)
        assertTrue(source.subtitles.isEmpty())
    }

    @Test
    fun `headers is empty and TokenProvider is never consulted while headersRequired stays fixed false`() = runTest {
        // Architecture rule (execution plan §5): the header-scoping decision
        // (buildScopedHttpHeaders + TokenProvider) lives here in
        // MediaRepositoryImpl, not in feature:preview's ViewModel. This test
        // locks down both today's observable behavior (empty headers, since
        // headersRequired is fixed false) and the short-circuit itself (no
        // TokenProvider call at all when headersRequired is false).
        coEvery { api.fsGet(any()) } returns success(objResp(name = "movie.mp4", rawUrl = "https://example.com/d/movie.mp4?sign=abc123"))

        val source = (repository.resolveMedia(INSTANCE_ID, "/movie.mp4") as ApiResult.Success).data

        assertTrue(source.headers.isEmpty())
        verify(exactly = 0) { tokenProvider.blockingTokenFor(any()) }
    }

    @Test
    fun `blank raw_url falls back to a signed download URL`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "song.mp3", rawUrl = "", sign = "abc123"))

        val source = (repository.resolveMedia(INSTANCE_ID, "/song.mp3") as ApiResult.Success).data

        assertTrue(source.url.contains("/d/"))
        assertTrue(source.url.contains("sign=abc123"))
    }

    @Test
    fun `blank raw_url and unbuildable download URL fails with MediaUnsupported`() = runTest {
        coEvery { instanceRepository.getById(INSTANCE_ID) } returns unresolvableUrlInstance()
        coEvery { api.fsGet(any()) } returns success(objResp(name = "movie.mp4", rawUrl = ""))

        val result = repository.resolveMedia(INSTANCE_ID, "/movie.mp4")

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.MediaUnsupported, (result as ApiResult.Failure).error)
    }

    @Test
    fun `mime type is guessed from the file extension`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "movie.mp4", rawUrl = "https://example.com/d/movie.mp4"))

        val source = (repository.resolveMedia(INSTANCE_ID, "/movie.mp4") as ApiResult.Success).data

        assertEquals("video/mp4", source.mimeType)
    }

    @Test
    fun `401 invalidates the session and propagates the failure`() = runTest {
        coEvery { api.fsGet(any()) } returns failure(401, "unauthorized")

        val result = repository.resolveMedia(INSTANCE_ID, "/movie.mp4")

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.Unauthorized, (result as ApiResult.Failure).error)
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
    }

    @Test
    fun `unknown instance returns InvalidInstance`() = runTest {
        coEvery { instanceRepository.getById("missing") } returns null

        val result = repository.resolveMedia("missing", "/movie.mp4")

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.InvalidInstance, (result as ApiResult.Failure).error)
    }

    @Test
    fun `refreshMediaSource performs the exact same resolution as resolveMedia`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "movie.mp4", rawUrl = "https://example.com/d/movie.mp4?sign=fresh456"))

        val resolved = (repository.resolveMedia(INSTANCE_ID, "/movie.mp4") as ApiResult.Success).data
        val refreshed = (repository.refreshMediaSource(INSTANCE_ID, "/movie.mp4") as ApiResult.Success).data

        assertEquals(resolved, refreshed)
    }

    @Test
    fun `refreshMediaSource also invalidates the session on a 401`() = runTest {
        coEvery { api.fsGet(any()) } returns failure(401, "unauthorized")

        val result = repository.refreshMediaSource(INSTANCE_ID, "/movie.mp4")

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.Unauthorized, (result as ApiResult.Failure).error)
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
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

    private fun failure(code: Int, message: String) = ApiResponse<FsGetResp?>(code = code, message = message, data = null)

    /** Trivial fake standing in for [AndroidMimeTypeResolver] -- same rationale
     * as [ExternalOpenRepositoryImplTest]'s fake (no Robolectric / android.webkit
     * in this plain-JVM unit test environment). */
    private class FakeMimeTypeResolver : MimeTypeResolver {
        private val table = mapOf(
            "mp4" to "video/mp4",
            "mp3" to "audio/mpeg",
        )

        override fun guessMimeType(extensionLowercase: String): String? = table[extensionLowercase]
    }

    private companion object {
        const val INSTANCE_ID = "inst-1"
    }
}
