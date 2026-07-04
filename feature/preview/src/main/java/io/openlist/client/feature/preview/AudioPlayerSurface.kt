@file:OptIn(UnstableApi::class) // Media3's ExoPlayer surface is broadly @UnstableApi-marked

package io.openlist.client.feature.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.model.MediaSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Audio layout for [MediaPlayerScreen] (v0.4_EXECUTION_PLAN.md §11 S5-T3):
 * file name/path, a play/pause button, a scrubbable progress slider, and
 * current/total time text -- no lyrics (explicitly out of scope per P-411).
 *
 * Shares the exact same [rememberOpenListExoPlayer] player-creation/release/
 * error/position-sampling logic as [MediaPlayerSurface] (this Sprint's
 * mandatory DoD: "no duplicate ExoPlayer init code") -- the only things this
 * composable adds are audio-specific: this hand-rolled transport UI (no
 * [androidx.media3.ui.PlayerView], since there is no video surface to show)
 * and its own local polling of `exoPlayer.currentPosition`/`duration` to
 * drive the slider's live position (independent of, and more frequent than,
 * [rememberOpenListExoPlayer]'s coarser 1s ViewModel-position-sample cadence
 * -- that one exists for seek-back-after-refresh, this one is for a
 * responsive-looking slider).
 */
@Composable
internal fun AudioPlayerSurface(
    mediaSource: MediaSource,
    scopedHeaders: Map<String, String>,
    initialPositionMs: Long,
    onPositionSample: (Long) -> Unit,
    onHttpClientError: suspend () -> MediaSource?,
    onTerminalError: (PlaybackException) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val exoPlayer = rememberOpenListExoPlayer(
        mediaSource = mediaSource,
        scopedHeaders = scopedHeaders,
        initialPositionMs = initialPositionMs,
        onPositionSample = onPositionSample,
        onError = { error ->
            if (MediaPlaybackErrorClassifier.isRetryableHttpClientError(error)) {
                scope.launch { onHttpClientError() }
            } else {
                onTerminalError(error)
            }
        },
    )

    var isPlaying by remember(exoPlayer) { mutableStateOf(exoPlayer.isPlaying) }
    var currentPositionMs by remember(exoPlayer) { mutableLongStateOf(exoPlayer.currentPosition) }
    var durationMs by remember(exoPlayer) { mutableLongStateOf(exoPlayer.duration.coerceAtLeast(0L)) }
    var sliderDragValue by remember(exoPlayer) { mutableFloatStateOf(-1f) }

    LaunchedEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onEvents(player: Player, events: Player.Events) {
                durationMs = player.duration.coerceAtLeast(0L)
            }
        }
        exoPlayer.addListener(listener)
    }

    // Faster local polling than the 1s ViewModel-position-sample cadence in
    // rememberOpenListExoPlayer -- purely cosmetic (a visibly moving
    // slider), not persisted anywhere.
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(250L)
            if (sliderDragValue < 0f) {
                currentPositionMs = exoPlayer.currentPosition
            }
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = mediaSource.title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(Spacing.xxs))
        Text(
            text = mediaSource.path,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        androidx.compose.foundation.layout.Spacer(Modifier.size(Spacing.xxl))

        IconButton(
            onClick = {
                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            },
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(48.dp),
            )
        }

        androidx.compose.foundation.layout.Spacer(Modifier.size(Spacing.md))

        val sliderPositionMs = if (sliderDragValue >= 0f) (sliderDragValue * durationMs).toLong() else currentPositionMs
        Slider(
            value = if (durationMs > 0) sliderPositionMs.toFloat() / durationMs.toFloat() else 0f,
            onValueChange = { fraction -> sliderDragValue = fraction },
            onValueChangeFinished = {
                if (sliderDragValue >= 0f && durationMs > 0) {
                    exoPlayer.seekTo((sliderDragValue * durationMs).toLong())
                }
                sliderDragValue = -1f
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = formatDurationMs(sliderPositionMs), style = MaterialTheme.typography.bodySmall)
            Text(text = formatDurationMs(durationMs), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
