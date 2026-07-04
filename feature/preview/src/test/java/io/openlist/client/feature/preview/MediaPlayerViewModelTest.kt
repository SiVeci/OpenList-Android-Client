package io.openlist.client.feature.preview

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.mockk
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.ExternalOpenRepository
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.MediaRepository
import io.openlist.client.core.domain.SubtitleRepository
import io.openlist.client.core.domain.TransferRepository
import io.openlist.client.core.model.MediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Covers [MediaPlayerViewModel.refreshAfterHttpError]'s retry-attempt-limit
 * logic (v0.4_EXECUTION_PLAN.md §11 S5-T4, P-412): the pure "how many times
 * has this ViewModel refreshed" bookkeeping is the only part of the S5-T4
 * flow that's unit-testable without a live ExoPlayer/Compose surface (see
 * [MediaPlaybackErrorClassifierTest] for the sibling "is this a 4xx" check).
 *
 * Boundary covered: the 1st and 2nd refresh attempts must go through
 * (MAX_REFRESH_ATTEMPTS = 2), and a 3rd must be rejected outright without
 * even calling [MediaRepository.refreshMediaSource] again. A refresh call
 * that itself fails is also covered as its own terminal path.
 */
class MediaPlayerViewModelTest {

    private val mediaRepository = mockk<MediaRepository>()
    private val externalOpenRepository = mockk<ExternalOpenRepository>(relaxed = true)
    private val filesRepository = mockk<FilesRepository>(relaxed = true)
    private val transferRepository = mockk<TransferRepository>(relaxed = true)
    private val subtitleRepository = mockk<SubtitleRepository>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // mediaSource(...) below always resolves to PreviewKind.VIDEO (a
        // "movie.mp4" title), so resolveMedia() always triggers a
        // findCandidates call (S6-T2) -- stubbed explicitly here (rather than
        // relying on the relaxed mock's default) so every existing test's
        // behavior stays exactly as before this Sprint's subtitle wiring.
        coEvery { subtitleRepository.findCandidates(any(), any()) } returns ApiResult.Success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): MediaPlayerViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("instanceId" to INSTANCE_ID, "path" to PATH))
        return MediaPlayerViewModel(
            savedStateHandle = savedStateHandle,
            mediaRepository = mediaRepository,
            externalOpenRepository = externalOpenRepository,
            filesRepository = filesRepository,
            transferRepository = transferRepository,
            subtitleRepository = subtitleRepository,
        )
    }

    @Test
    fun `first and second refresh attempts succeed and bump refreshAttempts`() = runTest {
        coEvery { mediaRepository.resolveMedia(INSTANCE_ID, PATH) } returns ApiResult.Success(mediaSource("sign1"))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { mediaRepository.refreshMediaSource(INSTANCE_ID, PATH) } returns ApiResult.Success(mediaSource("sign2"))
        val firstRefresh = viewModel.refreshAfterHttpError()
        assertNotNull(firstRefresh)
        assertEquals(1, viewModel.uiState.value.refreshAttempts)
        assertEquals("https://example.com/d/movie.mp4?sign=sign2", viewModel.uiState.value.mediaSource?.url)

        coEvery { mediaRepository.refreshMediaSource(INSTANCE_ID, PATH) } returns ApiResult.Success(mediaSource("sign3"))
        val secondRefresh = viewModel.refreshAfterHttpError()
        assertNotNull(secondRefresh)
        assertEquals(2, viewModel.uiState.value.refreshAttempts)
        assertEquals("https://example.com/d/movie.mp4?sign=sign3", viewModel.uiState.value.mediaSource?.url)
    }

    @Test
    fun `third refresh attempt is rejected outright once the budget is spent`() = runTest {
        coEvery { mediaRepository.resolveMedia(INSTANCE_ID, PATH) } returns ApiResult.Success(mediaSource("sign1"))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { mediaRepository.refreshMediaSource(INSTANCE_ID, PATH) } returns ApiResult.Success(mediaSource("sign2"))
        viewModel.refreshAfterHttpError()
        coEvery { mediaRepository.refreshMediaSource(INSTANCE_ID, PATH) } returns ApiResult.Success(mediaSource("sign3"))
        viewModel.refreshAfterHttpError()

        assertEquals(2, viewModel.uiState.value.refreshAttempts)

        // A 3rd call must short-circuit to null WITHOUT invoking
        // refreshMediaSource again -- verified by making any further call
        // return a distinguishable value that would show up in state if it
        // were (wrongly) consulted.
        coEvery { mediaRepository.refreshMediaSource(INSTANCE_ID, PATH) } returns ApiResult.Success(mediaSource("should-not-be-used"))
        val thirdRefresh = viewModel.refreshAfterHttpError()

        assertNull(thirdRefresh)
        assertNull(viewModel.uiState.value.mediaSource)
        assertEquals("播放地址已失效，请重试", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `a refresh call that itself fails settles into the terminal error state`() = runTest {
        coEvery { mediaRepository.resolveMedia(INSTANCE_ID, PATH) } returns ApiResult.Success(mediaSource("sign1"))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { mediaRepository.refreshMediaSource(INSTANCE_ID, PATH) } returns ApiResult.Failure(DomainError.NetworkUnavailable)
        val result = viewModel.refreshAfterHttpError()

        assertNull(result)
        assertNull(viewModel.uiState.value.mediaSource)
        assertEquals("网络不可达，请检查网络连接", viewModel.uiState.value.errorMessage)
        // A failed refresh still counts toward the attempt budget, so it
        // doesn't retry forever against a repository that's reliably failing.
        assertEquals(1, viewModel.uiState.value.refreshAttempts)
    }

    @Test
    fun `scopedHeaders mirrors mediaSource headers exactly, with no ViewModel-side computation`() = runTest {
        // Architecture rule (execution plan §5): feature:preview must not
        // compute headers itself (no TokenProvider/InstanceRepository
        // access here) -- MediaRepositoryImpl is solely responsible, and
        // this ViewModel just carries whatever MediaSource.headers says.
        coEvery { mediaRepository.resolveMedia(INSTANCE_ID, PATH) } returns
            ApiResult.Success(mediaSource("sign1", headers = mapOf("Authorization" to "token-xyz")))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf("Authorization" to "token-xyz"), viewModel.uiState.value.scopedHeaders)
    }

    // ---- Subtitles (S6-T2) ----

    @Test
    fun `resolving a VIDEO source triggers subtitle candidate discovery`() = runTest {
        coEvery { mediaRepository.resolveMedia(INSTANCE_ID, PATH) } returns ApiResult.Success(mediaSource("sign1"))
        val candidate = io.openlist.client.core.model.SubtitleCandidate(
            path = "/movie.srt",
            name = "movie.srt",
            language = null,
            format = "srt",
            source = io.openlist.client.core.model.SubtitleSourceType.AUTO_DISCOVERED,
        )
        coEvery { subtitleRepository.findCandidates(INSTANCE_ID, PATH) } returns ApiResult.Success(listOf(candidate))

        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(candidate), viewModel.uiState.value.subtitleCandidates)
    }

    @Test
    fun `a subtitle candidate lookup failure leaves candidates empty without touching mediaSource or errorMessage`() = runTest {
        coEvery { mediaRepository.resolveMedia(INSTANCE_ID, PATH) } returns ApiResult.Success(mediaSource("sign1"))
        coEvery { subtitleRepository.findCandidates(INSTANCE_ID, PATH) } returns ApiResult.Failure(DomainError.NetworkUnavailable)

        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<Any>(), viewModel.uiState.value.subtitleCandidates)
        assertNotNull(viewModel.uiState.value.mediaSource)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `selectSubtitle resolves and applies the candidate, closing the selector`() = runTest {
        coEvery { mediaRepository.resolveMedia(INSTANCE_ID, PATH) } returns ApiResult.Success(mediaSource("sign1"))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.openSubtitleSelector()

        val candidate = io.openlist.client.core.model.SubtitleCandidate(
            path = "/movie.srt",
            name = "movie.srt",
            language = null,
            format = "srt",
            source = io.openlist.client.core.model.SubtitleSourceType.AUTO_DISCOVERED,
        )
        val resolved = io.openlist.client.core.model.SubtitleSource(path = "/movie.srt", url = "https://example.com/d/movie.srt", format = "srt")
        coEvery { subtitleRepository.resolveSubtitle(INSTANCE_ID, "/movie.srt") } returns ApiResult.Success(resolved)

        viewModel.selectSubtitle(candidate)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(resolved, viewModel.uiState.value.selectedSubtitle)
        assertEquals(false, viewModel.uiState.value.showSubtitleSelector)
    }

    @Test
    fun `a subtitle resolve failure only sets subtitleError, never mediaSource or errorMessage`() = runTest {
        coEvery { mediaRepository.resolveMedia(INSTANCE_ID, PATH) } returns ApiResult.Success(mediaSource("sign1"))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val candidate = io.openlist.client.core.model.SubtitleCandidate(
            path = "/movie.srt",
            name = "movie.srt",
            language = null,
            format = "srt",
            source = io.openlist.client.core.model.SubtitleSourceType.AUTO_DISCOVERED,
        )
        coEvery { subtitleRepository.resolveSubtitle(INSTANCE_ID, "/movie.srt") } returns ApiResult.Failure(DomainError.NotFound)

        viewModel.selectSubtitle(candidate)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedSubtitle)
        assertNotNull(viewModel.uiState.value.subtitleError)
        assertNotNull(viewModel.uiState.value.mediaSource)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `clearSubtitle resets selectedSubtitle to null and closes the selector`() = runTest {
        coEvery { mediaRepository.resolveMedia(INSTANCE_ID, PATH) } returns ApiResult.Success(mediaSource("sign1"))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val resolved = io.openlist.client.core.model.SubtitleSource(path = "/movie.srt", url = "https://example.com/d/movie.srt", format = "srt")
        coEvery { subtitleRepository.resolveSubtitle(INSTANCE_ID, "/movie.srt") } returns ApiResult.Success(resolved)
        viewModel.selectManualSubtitle("/movie.srt")
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.selectedSubtitle)

        viewModel.clearSubtitle()

        assertNull(viewModel.uiState.value.selectedSubtitle)
        assertEquals(false, viewModel.uiState.value.showSubtitleSelector)
    }

    private fun mediaSource(sign: String, headers: Map<String, String> = emptyMap()) = MediaSource(
        instanceId = INSTANCE_ID,
        path = PATH,
        title = "movie.mp4",
        mimeType = "video/mp4",
        url = "https://example.com/d/movie.mp4?sign=$sign",
        headersRequired = false,
        expiresAt = null,
        subtitles = emptyList(),
        headers = headers,
    )

    private companion object {
        const val INSTANCE_ID = "inst-1"
        const val PATH = "/movie.mp4"
    }
}
