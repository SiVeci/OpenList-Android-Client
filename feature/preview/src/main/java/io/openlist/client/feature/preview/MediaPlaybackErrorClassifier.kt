package io.openlist.client.feature.preview

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource

/**
 * Classifies an ExoPlayer [PlaybackException] as "a signed url that just
 * expired" or not (v0.4_EXECUTION_PLAN.md §11 S5-T4, P-412).
 *
 * Media3 1.4.1 wraps a non-2xx HTTP response as a [PlaybackException] whose
 * `errorCode` is [PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS] (2004),
 * with the underlying [HttpDataSource.InvalidResponseCodeException] (which
 * carries the actual `responseCode`) reachable via `getCause()`. This class
 * only exists as a standalone object (not inlined into the Compose surface)
 * so [isRetryableHttpClientError] is directly unit-testable against
 * hand-built exceptions, with no ExoPlayer instance or Android runtime
 * required.
 *
 * API confirmed against the public androidx/media source at the `release`
 * branch (no local Gradle cache for media3 1.4.1 artifacts was available in
 * this environment -- see the Sprint report's "API verification" note):
 * https://github.com/androidx/media/blob/release/libraries/common/src/main/java/androidx/media3/common/PlaybackException.java
 * https://github.com/androidx/media/blob/release/libraries/datasource/src/main/java/androidx/media3/datasource/HttpDataSource.java
 */
object MediaPlaybackErrorClassifier {

    /**
     * True only for a "bad HTTP status" error whose underlying response code
     * falls in the 4xx range (401/403/404/etc. -- exactly the shape a
     * just-expired `sign` value produces server-side). 5xx and non-HTTP
     * errors (network drop, decoder failure, etc.) are deliberately not
     * retried here -- P-412's refresh-and-retry is specifically an "our
     * signed url expired" recovery, not a generic retry-on-any-error policy.
     */
    @OptIn(UnstableApi::class) // HttpDataSource.InvalidResponseCodeException is @UnstableApi
    fun isRetryableHttpClientError(error: PlaybackException): Boolean {
        if (error.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return false
        val responseCode = findInvalidResponseCode(error) ?: return false
        return responseCode in 400..499
    }

    @OptIn(UnstableApi::class)
    private fun findInvalidResponseCode(error: Throwable): Int? {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
            cause = cause.cause
        }
        return null
    }
}
