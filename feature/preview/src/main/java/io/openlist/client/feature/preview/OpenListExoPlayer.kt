@file:OptIn(UnstableApi::class) // Media3's ExoPlayer/DataSource surface is broadly @UnstableApi-marked

package io.openlist.client.feature.preview

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.openlist.client.core.model.MediaSource
import kotlinx.coroutines.delay

/**
 * Single choke point for creating/configuring/releasing the [ExoPlayer]
 * instance backing both the video ([MediaPlayerSurface]) and audio
 * ([AudioPlayerSurface]) layouts (v0.4_EXECUTION_PLAN.md §11 S5-T2/S5-T3 --
 * S5-T3's DoD explicitly requires the two layouts share this logic rather
 * than each hand-rolling their own `ExoPlayer.Builder`/`MediaItem`/dispose
 * sequence).
 *
 * Recreates the player whenever [mediaSource]'s url or [scopedHeaders]
 * change (via `remember(mediaSource.url, scopedHeaders)`) -- both S5-T4's
 * refresh flow (a new signed url after a 4xx) and a from-scratch
 * [MediaPlayerViewModel.resolveMedia] retry produce a new [MediaSource],
 * which must rebuild the underlying [ExoPlayer.setMediaItem] rather than
 * silently keep playing the stale (expired) url.
 *
 * [DisposableEffect] guarantees [ExoPlayer.release] runs on every path that
 * removes this composable from composition: normal back-navigation, the
 * player being swapped out for a fresh instance (the `remember` key
 * changing disposes the *old* effect before the new one runs), and process
 * death/recreation (a fresh Activity means a fresh composition from
 * scratch, so there is no stale player instance to leak in the first
 * place).
 */
@Composable
internal fun rememberOpenListExoPlayer(
    mediaSource: MediaSource,
    scopedHeaders: Map<String, String>,
    initialPositionMs: Long,
    onError: (PlaybackException) -> Unit,
    onPositionSample: (Long) -> Unit,
): ExoPlayer {
    val context = LocalContext.current
    val currentOnError by rememberUpdatedState(onError)

    val exoPlayer = remember(mediaSource.url, scopedHeaders) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory().apply {
            // S5-T1: headersRequired is fixed false today, so scopedHeaders
            // is normally empty -- this branch exists so the DataSource.Factory
            // wiring is real now rather than deferred until a future
            // counter-example forces headersRequired to flip.
            if (mediaSource.headersRequired && scopedHeaders.isNotEmpty()) {
                setDefaultRequestProperties(scopedHeaders)
            }
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(mediaSource.url)))
                if (initialPositionMs > 0) seekTo(initialPositionMs)
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                currentOnError(error)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            // Sample the final position before tearing down so a subsequent
            // refresh-triggered rebuild (S5-T4) can seek back close to where
            // playback stopped, even if the 1s polling loop below hadn't
            // ticked since the last sample.
            onPositionSample(exoPlayer.currentPosition)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Low-frequency position sampling (P-412/plan: "not every frame") so
    // MediaPlayerViewModel.lastKnownPositionMs stays reasonably fresh for a
    // seek-back after a refresh rebuild, without turning this into a
    // per-frame state-update hot loop. 1s cadence is an arbitrary,
    // cheap-enough interval -- there is no product requirement for tighter
    // seek-back precision than that.
    LaunchedPositionSampler(exoPlayer, onPositionSample)

    return exoPlayer
}

@Composable
private fun LaunchedPositionSampler(exoPlayer: ExoPlayer, onPositionSample: (Long) -> Unit) {
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(1000L)
            onPositionSample(exoPlayer.currentPosition)
        }
    }
}
