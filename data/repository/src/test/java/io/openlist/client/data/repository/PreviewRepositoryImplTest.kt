package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.PreviewCacheDao
import io.openlist.client.core.database.entity.PreviewCacheEntity
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.Instance
import io.openlist.client.core.model.PreviewFallback
import io.openlist.client.core.model.PreviewKind
import io.openlist.client.core.model.PreviewOpenMode
import io.openlist.client.core.model.PreviewSource
import io.openlist.client.core.model.TextPreviewOptions
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.PreviewHttpClient
import io.openlist.client.core.network.dto.ApiResponse
import io.openlist.client.core.network.dto.FsGetResp
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Covers PreviewRepositoryImpl.resolvePreview's kind -> openMode/fallbacks
 * decision table (v0.4_EXECUTION_PLAN.md §11 S2-T1), the raw_url-blank
 * fallback to a signed /d/ URL, the 401 -> sessionManager.invalidate path,
 * and the InvalidInstance short-circuit. Network/instance collaborators are
 * mocked following FileOperationRepositoryImplTest's established pattern.
 *
 * S3 additions cover [PreviewRepositoryImpl.loadText]'s cache-hit/cache-miss/
 * force-refresh/hard-ceiling paths (network reads are exercised through a
 * real loopback-free OkHttp MockWebServer-less approach isn't used here —
 * see [readCapped]'s own tests below for the actual byte-level truncation
 * logic; loadText's tests instead cover everything *except* the live network
 * call, which safeApiCall-mocking cannot substitute for a raw OkHttp
 * `Call.execute()`), plus [PreviewRepositoryImpl.invalidate]/[invalidateByPrefix].
 */
class PreviewRepositoryImplTest {

    private val api = mockk<OpenListApi>()
    private val instanceRepository = mockk<InstanceRepository>()
    private val clientFactory = mockk<OpenListClientFactory>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val previewCacheDao = mockk<PreviewCacheDao>(relaxed = true)
    private val previewHttpClient = PreviewHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var cacheDir: File
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
        cacheDir = java.nio.file.Files.createTempDirectory("preview-cache-test").toFile()
        val context = mockk<android.content.Context>()
        every { context.cacheDir } returns cacheDir
        repository = PreviewRepositoryImpl(
            instanceRepository = instanceRepository,
            clientFactory = clientFactory,
            instanceContext = InstanceContext(),
            sessionManager = sessionManager,
            previewCacheDao = previewCacheDao,
            previewHttpClient = previewHttpClient,
            json = json,
            context = context,
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

    // ---- loadText (S3-T2) ----

    @Test
    fun `loadText over the hard ceiling fails with PreviewTooLarge without any cache lookup`() = runTest {
        coEvery { api.fsGet(any()) } returns success(objResp(name = "big.txt", size = PreviewRepositoryImpl.TEXT_PREVIEW_HARD_CEILING_BYTES + 1))

        val result = repository.loadText(INSTANCE_ID, "/big.txt", TextPreviewOptions())

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.PreviewTooLarge, (result as ApiResult.Failure).error)
        coVerify(exactly = 0) { previewCacheDao.getByInstanceAndPath(any(), any()) }
    }

    @Test
    fun `loadText hits a fresh matching cache row without any network body read`() = runTest {
        val cachedFile = File(cacheDir, "cached.txt").apply { writeText("hello from cache") }
        val row = cacheRow(path = "/notes.txt", kind = "TEXT", lastModified = null, sizeBytes = 100, localFilePath = cachedFile.absolutePath)
        coEvery { api.fsGet(any()) } returns success(objResp(name = "notes.txt", size = 100, rawUrl = "https://example.com/d/notes.txt"))
        coEvery { previewCacheDao.getByInstanceAndPath(INSTANCE_ID, "/notes.txt") } returns listOf(row)

        val result = repository.loadText(INSTANCE_ID, "/notes.txt", TextPreviewOptions())

        val content = (result as ApiResult.Success).data
        assertEquals("hello from cache", content.text)
        // cachedFile's actual byte length (17) is less than the cached row's
        // recorded sizeBytes (100), so this cached body was itself a
        // truncated read -- isTruncated must surface that, not silently claim completeness.
        assertTrue(content.isTruncated)
        coVerify(exactly = 0) { previewCacheDao.upsert(any()) }
    }

    @Test
    fun `loadText ignores a cache row whose size no longer matches fresh metadata`() = runTest {
        // baseUrl is deliberately not http(s) so OpenListPathCodec.buildDownloadUrl
        // returns null and the network path short-circuits to a Failure before
        // any real HTTP call is attempted -- this isolates the assertion to
        // "the stale cache row was rejected" without a live network dependency.
        coEvery { instanceRepository.getById(INSTANCE_ID) } returns unresolvableUrlInstance()
        val cachedFile = File(cacheDir, "cached.txt").apply { writeText("stale") }
        val staleRow = cacheRow(path = "/notes.txt", kind = "TEXT", lastModified = null, sizeBytes = 999, localFilePath = cachedFile.absolutePath)
        coEvery { api.fsGet(any()) } returns success(objResp(name = "notes.txt", size = 5, rawUrl = ""))
        coEvery { previewCacheDao.getByInstanceAndPath(INSTANCE_ID, "/notes.txt") } returns listOf(staleRow)

        val result = repository.loadText(INSTANCE_ID, "/notes.txt", TextPreviewOptions())

        assertTrue(result is ApiResult.Failure)
    }

    @Test
    fun `loadText forceRefresh bypasses an otherwise-fresh cache row`() = runTest {
        // Same unresolvable-URL trick as above: forceRefresh=true must skip
        // the cache row and reach (and fail fast at) the URL-resolution step,
        // proving the cache short-circuit was NOT taken -- a genuine cache
        // hit would have returned Success("hello from cache") instead.
        coEvery { instanceRepository.getById(INSTANCE_ID) } returns unresolvableUrlInstance()
        val cachedFile = File(cacheDir, "cached.txt").apply { writeText("hello from cache") }
        val row = cacheRow(path = "/notes.txt", kind = "TEXT", lastModified = null, sizeBytes = 100, localFilePath = cachedFile.absolutePath)
        coEvery { api.fsGet(any()) } returns success(objResp(name = "notes.txt", size = 100, rawUrl = ""))
        coEvery { previewCacheDao.getByInstanceAndPath(INSTANCE_ID, "/notes.txt") } returns listOf(row)

        val result = repository.loadText(INSTANCE_ID, "/notes.txt", TextPreviewOptions(forceRefresh = true))

        assertTrue(result is ApiResult.Failure)
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

    @Test
    fun `loadText 401 from the metadata fetch invalidates the session`() = runTest {
        coEvery { api.fsGet(any()) } returns failure(401, "unauthorized")

        val result = repository.loadText(INSTANCE_ID, "/notes.txt", TextPreviewOptions())

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.Unauthorized, (result as ApiResult.Failure).error)
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
    }

    // ---- invalidate / invalidateByPrefix (S3-T4) ----

    @Test
    fun `invalidate deletes the cached file and the exact-path row`() = runTest {
        val file = File(cacheDir, "victim.txt").apply { writeText("x") }
        val row = cacheRow(path = "/a.txt", kind = "TEXT", lastModified = null, sizeBytes = 1, localFilePath = file.absolutePath)
        coEvery { previewCacheDao.getByInstanceAndPath(INSTANCE_ID, "/a.txt") } returns listOf(row)

        repository.invalidate(INSTANCE_ID, "/a.txt")

        assertFalse(file.exists())
        coVerify(exactly = 1) { previewCacheDao.deleteByInstanceAndPath(INSTANCE_ID, "/a.txt") }
    }

    @Test
    fun `invalidate tolerates an already-missing file without throwing`() = runTest {
        val row = cacheRow(path = "/a.txt", kind = "TEXT", lastModified = null, sizeBytes = 1, localFilePath = File(cacheDir, "missing.txt").absolutePath)
        coEvery { previewCacheDao.getByInstanceAndPath(INSTANCE_ID, "/a.txt") } returns listOf(row)

        repository.invalidate(INSTANCE_ID, "/a.txt")

        coVerify(exactly = 1) { previewCacheDao.deleteByInstanceAndPath(INSTANCE_ID, "/a.txt") }
    }

    @Test
    fun `invalidateByPrefix deletes every matched row's file then the prefix rows`() = runTest {
        val fileA = File(cacheDir, "a.txt").apply { writeText("a") }
        val fileB = File(cacheDir, "b.txt").apply { writeText("b") }
        val rowA = cacheRow(path = "/dir/a.txt", kind = "TEXT", lastModified = null, sizeBytes = 1, localFilePath = fileA.absolutePath)
        val rowB = cacheRow(path = "/dir/b.md", kind = "MARKDOWN", lastModified = null, sizeBytes = 1, localFilePath = fileB.absolutePath)
        coEvery { previewCacheDao.getByPathPrefix(INSTANCE_ID, "/dir") } returns listOf(rowA, rowB)

        repository.invalidateByPrefix(INSTANCE_ID, "/dir")

        assertFalse(fileA.exists())
        assertFalse(fileB.exists())
        coVerify(exactly = 1) { previewCacheDao.deleteByPathPrefix(INSTANCE_ID, "/dir") }
    }

    private fun cacheRow(
        path: String,
        kind: String,
        lastModified: Long?,
        sizeBytes: Long,
        localFilePath: String,
    ) = PreviewCacheEntity(
        id = "row-$path-$kind",
        instanceId = INSTANCE_ID,
        path = path,
        kind = kind,
        mimeType = null,
        lastModified = lastModified,
        cacheKey = "$INSTANCE_ID:$path:$kind",
        localFilePath = localFilePath,
        sizeBytes = sizeBytes,
        etag = null,
        expiresAt = System.currentTimeMillis() + 60_000L,
        cachedAt = System.currentTimeMillis(),
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

    private companion object {
        const val INSTANCE_ID = "inst-1"
    }
}
