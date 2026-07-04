@file:OptIn(UnstableApi::class) // Media3's ExoPlayer/PlayerView surface is broadly @UnstableApi-marked

package io.openlist.client.feature.preview

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.windowInsetsPadding
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.model.MediaSource
import io.openlist.client.core.model.SubtitleSource
import kotlinx.coroutines.launch

/**
 * Video layout for [MediaPlayerScreen] (v0.4_EXECUTION_PLAN.md §11 S5-T2).
 * Delegates all ExoPlayer create/configure/release/error/position-sampling
 * logic to [rememberOpenListExoPlayer] (shared with [AudioPlayerSurface] per
 * S5-T3's DoD) and only adds video-specific concerns: the [PlayerView]
 * itself, landscape immersive/fullscreen chrome (PRD §12.9 point 2), and
 * (S6-T2) a floating subtitle entry button -- [AudioPlayerSurface]
 * deliberately has none of this (P-411 keeps subtitles/lyrics out of the
 * audio surface).
 *
 * `PlayerView.useController` defaults to `true`, which per Media3's public
 * behavior (androidx/media/blob/release/libraries/ui/.../PlayerView.java)
 * means the standard `PlayerControlView` overlay (play/pause, seek bar,
 * position/duration text) renders automatically -- no bespoke transport
 * controls are hand-built here. This has been verified against the public
 * androidx/media source and documentation (no local Gradle cache for media3
 * 1.4.1 artifacts was available in this environment), but the actual
 * on-device rendering has NOT been verified -- there is no build/run
 * environment available for this Sprint (see the Sprint report's caveats).
 */
@Composable
internal fun MediaPlayerSurface(
    mediaSource: MediaSource,
    scopedHeaders: Map<String, String>,
    initialPositionMs: Long,
    onPositionSample: (Long) -> Unit,
    onHttpClientError: suspend () -> MediaSource?,
    onTerminalError: (PlaybackException) -> Unit,
    modifier: Modifier = Modifier,
    subtitleSource: SubtitleSource? = null,
    onOpenSubtitleSelector: (() -> Unit)? = null,
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
        subtitleSource = subtitleSource,
    )

    ImmersiveLandscapeEffect()

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            update = { view -> view.player = exoPlayer },
        )

        if (onOpenSubtitleSelector != null) {
            FilledIconButton(
                onClick = onOpenSubtitleSelector,
                colors = IconButtonDefaults.filledIconButtonColors(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(Spacing.md),
            ) {
                Icon(imageVector = Icons.Filled.Subtitles, contentDescription = "字幕")
            }
        }
    }
}

/**
 * PRD §12.9 point 2: landscape orientation gets an immersive, fullscreen
 * player -- system status/navigation bars hidden, restored on leaving
 * landscape or leaving the screen entirely. Follows the system's current
 * orientation rather than adding an explicit in-app fullscreen toggle
 * button (simpler, lower-risk, per the execution plan's own guidance).
 *
 * Implementation uses `WindowCompat.getInsetsController` +
 * `WindowInsetsControllerCompat.hide/show(WindowInsetsCompat.Type.systemBars())`,
 * the standard AndroidX approach for immersive mode (confirmed against
 * public Android developer documentation). This has NOT been verified on a
 * real device/emulator in this environment -- no build/run environment was
 * available for this Sprint (see the Sprint report's caveats). If
 * [LocalContext.current] is not an [Activity] (e.g. a preview host), this
 * is a no-op rather than a crash.
 */
@Composable
private fun ImmersiveLandscapeEffect() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(isLandscape) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val window = activity.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (isLandscape) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? Activity ?: return@onDispose
            val window = activity.window ?: return@onDispose
            WindowCompat.getInsetsController(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
