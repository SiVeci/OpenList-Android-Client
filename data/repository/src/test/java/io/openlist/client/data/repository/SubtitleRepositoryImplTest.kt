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
import io.openlist.client.core.model.SubtitleSourceType
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.FsGetResp
import io.openlist.client.core.network.dto.ObjResp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers [SubtitleRepositoryImpl] (v0.4_EXECUTION_PLAN.md §11 S6-T1).
 *
 * The execution plan's DoD calls for "prefix/extension matching" unit tests,
 * but the *prefix* matching itself is entirely the backend's job — `fs/get`'s
 * `related` field (`FsGetResp.related`) already arrives pre-filtered to
 * same-prefix siblings by the server's own `filterRelated()`. What this
 * repository actually adds, and what these tests focus on instead, is the
 * one piece of real client-side logic: filtering that (extension-agnostic)
 * `related` list down to subtitle-looking extensions (case-insensitive) and
 * mapping the survivors to [io.openlist.client.core.model.SubtitleCandidate].
 */
class SubtitleRepositoryImplTest {

    private val api = mockk<OpenListApi>()
    private val instanceRepository = mockk<InstanceRepository>()
    private val clientFactory = mockk<OpenListClientFactory>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)

    private lateinit var repository: SubtitleRepositoryImpl

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
        repository = SubtitleRepositoryImpl(
            instanceRepository = instanceRepository,
            clientFactory = clientFactory,
            instanceContext = InstanceContext(),
            sessionManager = sessionManager,
        )
    }

    // ---- findCandidates ----

    @Test
    fun `filters related entries down to subtitle extensions only`() = runTest {
        coEvery { api.fsGet(any()) } returns success(
            objResp(
                name = "movie.mp4",
                related = listOf(
                    objRelated("movie.srt"),
                    objRelated("movie.nfo"),
                    objRelated("movie.jpg"),
                    objRelated("movie.vtt"),
                ),
            ),
        )

        val candidates = (repository.findCandidates(INSTANCE_ID, "/videos/movie.mp4") as ApiResult.Success).data

        assertEquals(2, candidates.size)
        assertEquals(setOf("movie.srt", "movie.vtt"), candidates.map { it.name }.toSet())
    }

    @Test
    fun `recognizes subtitle extensions case-insensitively`() = runTest {
        coEvery { api.fsGet(any()) } returns success(
            objResp(name = "movie.mp4", related = listOf(objRelated("movie.SRT"), objRelated("movie.Ass"))),
        )

        val candidates = (repository.findCandidates(INSTANCE_ID, "/videos/movie.mp4") as ApiResult.Success).data

        assertEquals(2, candidates.size)
        assertEquals("srt", candidates.first { it.name == "movie.SRT" }.format)
        assertEquals("ass", candidates.first { it.name == "movie.Ass" }.format)
    }

    @Test
    fun `empty related list yields an empty candidate list, not an error`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "movie.mp4", related = emptyList()))

        val result = repository.findCandidates(INSTANCE_ID, "/videos/movie.mp4")

        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data.isEmpty())
    }

    @Test
    fun `candidate path is joined against the video's parent directory`() = runTest {
        coEvery { api.fsGet(any()) } returns success(
            objResp(name = "movie.mp4", related = listOf(objRelated("movie.srt"))),
        )

        val candidates = (repository.findCandidates(INSTANCE_ID, "/videos/nested/movie.mp4") as ApiResult.Success).data

        assertEquals("/videos/nested/movie.srt", candidates.single().path)
    }

    @Test
    fun `candidates are marked AUTO_DISCOVERED with no guessed language`() = runTest {
        coEvery { api.fsGet(any()) } returns success(
            objResp(name = "movie.mp4", related = listOf(objRelated("movie.srt"))),
        )

        val candidate = (repository.findCandidates(INSTANCE_ID, "/movie.mp4") as ApiResult.Success).data.single()

        assertEquals(SubtitleSourceType.AUTO_DISCOVERED, candidate.source)
        assertNull(candidate.language)
    }

    @Test
    fun `findCandidates 401 invalidates the session and propagates the failure`() = runTest {
        coEvery { api.fsGet(any()) } returns failure(401, "unauthorized")

        val result = repository.findCandidates(INSTANCE_ID, "/movie.mp4")

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.Unauthorized, (result as ApiResult.Failure).error)
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
    }

    @Test
    fun `findCandidates on an unknown instance returns InvalidInstance`() = runTest {
        coEvery { instanceRepository.getById("missing") } returns null

        val result = repository.findCandidates("missing", "/movie.mp4")

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.InvalidInstance, (result as ApiResult.Failure).error)
    }

    // ---- resolveSubtitle ----

    @Test
    fun `resolveSubtitle uses raw_url when present`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "movie.srt", rawUrl = "https://example.com/d/movie.srt"))

        val source = (repository.resolveSubtitle(INSTANCE_ID, "/movie.srt") as ApiResult.Success).data

        assertEquals("https://example.com/d/movie.srt", source.url)
        assertEquals("srt", source.format)
    }

    @Test
    fun `resolveSubtitle falls back to a signed download URL when raw_url is blank`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "movie.vtt", rawUrl = "", sign = "abc123"))

        val source = (repository.resolveSubtitle(INSTANCE_ID, "/movie.vtt") as ApiResult.Success).data

        assertTrue(source.url.contains("/d/"))
        assertTrue(source.url.contains("sign=abc123"))
        assertEquals("vtt", source.format)
    }

    @Test
    fun `resolveSubtitle fails when neither raw_url nor a buildable download URL exists`() = runTest {
        coEvery { instanceRepository.getById(INSTANCE_ID) } returns Instance(
            id = INSTANCE_ID,
            name = "Test",
            baseUrl = "not-a-valid-url",
            createdAt = 0,
            updatedAt = 0,
            lastUsedAt = 0,
            isCurrent = true,
            note = null,
        )
        coEvery { api.fsGet(any()) } returns success(objResp(name = "movie.srt", rawUrl = ""))

        val result = repository.resolveSubtitle(INSTANCE_ID, "/movie.srt")

        assertTrue(result is ApiResult.Failure)
    }

    @Test
    fun `resolveSubtitle 401 invalidates the session and propagates the failure`() = runTest {
        coEvery { api.fsGet(any()) } returns failure(401, "unauthorized")

        val result = repository.resolveSubtitle(INSTANCE_ID, "/movie.srt")

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.Unauthorized, (result as ApiResult.Failure).error)
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
    }

    private fun objRelated(name: String) = ObjResp(name = name, size = 10, isDir = false, modified = "")

    private fun objResp(
        name: String,
        rawUrl: String = "",
        sign: String = "",
        related: List<ObjResp> = emptyList(),
    ) = FsGetResp(
        name = name,
        size = 100,
        isDir = false,
        modified = "",
        sign = sign,
        rawUrl = rawUrl,
        related = related,
    )

    private fun success(data: FsGetResp) = io.openlist.client.core.network.dto.ApiResponse(code = 200, message = "success", data = data)

    private fun failure(code: Int, message: String) = io.openlist.client.core.network.dto.ApiResponse<FsGetResp>(code = code, message = message, data = null)

    private companion object {
        const val INSTANCE_ID = "inst-1"
    }
}
