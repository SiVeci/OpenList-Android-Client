package io.openlist.client.feature.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * v0.4 Sprint 1 placeholder for the `preview/{instanceId}?path={path}` route
 * (v0.4_EXECUTION_PLAN.md §11 S1-T5: "两条路由骨架"). Real image/text/markdown
 * rendering lands in S2/S3; this only proves the route wires up and its
 * arguments decode correctly.
 */
@Composable
fun PreviewPlaceholderScreen(instanceId: String, path: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "预览页占位 (instance=$instanceId, path=$path)",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * v0.4 Sprint 1 placeholder for the `player/{instanceId}?path={path}` route.
 * Real ExoPlayer integration lands in S4/S5 (video/audio playback).
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
