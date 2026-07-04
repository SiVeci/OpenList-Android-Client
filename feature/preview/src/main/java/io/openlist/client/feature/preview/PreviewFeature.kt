package io.openlist.client.feature.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * v0.4 Sprint 1 placeholder for the `player/{instanceId}?path={path}` route.
 * Real ExoPlayer integration lands in S4/S5 (video/audio playback). The
 * sibling `preview/{instanceId}?path={path}` route's placeholder
 * (`PreviewPlaceholderScreen`) was replaced by the real `PreviewScreen` in S2
 * (see PreviewScreen.kt) — this one stays until S5.
 */
@Composable
fun MediaPlayerPlaceholderScreen(instanceId: String, path: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "播放器占位 (instance=$instanceId, path=$path)",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
