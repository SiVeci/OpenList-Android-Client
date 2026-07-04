package io.openlist.client.feature.preview

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [MediaPlaybackErrorClassifier.isRetryableHttpClientError]
 * (v0.4_EXECUTION_PLAN.md §11 S5-T4, P-412): must return true only for a
 * [PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS] error whose underlying
 * [HttpDataSource.InvalidResponseCodeException.responseCode] is in the 4xx
 * range, and false for every other shape (5xx, non-HTTP errors, no
 * `InvalidResponseCodeException` anywhere in the cause chain).
 *
 * [newInvalidResponseCodeException] builds a *real* exception via its real
 * constructor (`responseCode` is a plain public field, not a getter-backed
 * property, so mockk's method-interception-based `every { ... }` cannot stub
 * it on a mocked instance) -- the only tricky constructor argument is
 * `DataSpec`, which the exception's own source
 * (androidx/media/blob/release/libraries/datasource/.../HttpDataSourceException.java)
 * only ever stores in a field and never calls a method on, so a bare mockk
 * proxy (which never touches `android.net.Uri`) is a safe stand-in for it --
 * no real `DataSpec`/`Uri` construction is needed.
 */
@OptIn(UnstableApi::class) // DataSpec / InvalidResponseCodeException are @UnstableApi
class MediaPlaybackErrorClassifierTest {

    @Test
    fun `4xx bad http status is retryable`() {
        val error = PlaybackException(
            "bad status",
            newInvalidResponseCodeException(403),
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        )

        assertTrue(MediaPlaybackErrorClassifier.isRetryableHttpClientError(error))
    }

    @Test
    fun `401 bad http status is retryable`() {
        val error = PlaybackException(
            "unauthorized",
            newInvalidResponseCodeException(401),
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        )

        assertTrue(MediaPlaybackErrorClassifier.isRetryableHttpClientError(error))
    }

    @Test
    fun `5xx bad http status is not retryable`() {
        val error = PlaybackException(
            "server error",
            newInvalidResponseCodeException(500),
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        )

        assertFalse(MediaPlaybackErrorClassifier.isRetryableHttpClientError(error))
    }

    @Test
    fun `non-http error code is not retryable even with a coincidental cause`() {
        val error = PlaybackException(
            "network dropped",
            java.io.IOException("connection reset"),
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        )

        assertFalse(MediaPlaybackErrorClassifier.isRetryableHttpClientError(error))
    }

    @Test
    fun `bad http status with no InvalidResponseCodeException in the cause chain is not retryable`() {
        val error = PlaybackException(
            "bad status but no detail",
            java.io.IOException("wrapped"),
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        )

        assertFalse(MediaPlaybackErrorClassifier.isRetryableHttpClientError(error))
    }

    @Test
    fun `finds InvalidResponseCodeException nested deeper in the cause chain`() {
        val wrapper = RuntimeException("outer wrapper", newInvalidResponseCodeException(404))
        val error = PlaybackException(
            "bad status",
            wrapper,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        )

        assertTrue(MediaPlaybackErrorClassifier.isRetryableHttpClientError(error))
    }

    /** Builds a real [HttpDataSource.InvalidResponseCodeException] via its
     * real (only) constructor -- see this file's class-level KDoc for why a
     * mocked [DataSpec] is a safe stand-in for the constructor's `dataSpec`
     * argument. */
    private fun newInvalidResponseCodeException(responseCode: Int): HttpDataSource.InvalidResponseCodeException =
        HttpDataSource.InvalidResponseCodeException(
            responseCode,
            /* responseMessage = */ null,
            /* cause = */ null,
            /* headerFields = */ emptyMap(),
            /* dataSpec = */ mockk<DataSpec>(),
            /* responseBody = */ ByteArray(0),
        )
}
