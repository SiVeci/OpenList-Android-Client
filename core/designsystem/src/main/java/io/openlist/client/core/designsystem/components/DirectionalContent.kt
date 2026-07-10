package io.openlist.client.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

private const val FullWidthDurationMs = 300
private const val InPageDurationMs = 200
private const val ContextSwapDurationMs = 150

/**
 * In-page content switcher that carries the app-wide spatial rule (2026-07
 * motion pass) down into screens: content whose target lies to the right /
 * deeper comes in from the right, to the left / shallower from the left, and
 * unordered switches ([direction] returning 0) fall back to a quick crossfade.
 *
 * Two weights:
 *  * default — a ~24dp nudge + fade over 200ms for lightweight switches
 *    (tab rows, form variants), one level lighter than page navigation;
 *  * [fullWidth] — a full-width, fade-free band slide over 300ms for
 *    switches that read as "a new page inside this screen", e.g. directory
 *    navigation in the file list. The outgoing pane must render its own
 *    frozen content (not shared live state) for this to look right.
 *
 * [direction] compares outgoing and incoming states: >0 = target is forward
 * (right/deeper), <0 = backward, 0 = unordered. For index-based tabs pass
 * `{ from, to -> to - from }`.
 */
@Composable
fun <T> DirectionalContent(
    targetState: T,
    direction: (initial: T, target: T) -> Int,
    modifier: Modifier = Modifier,
    label: String = "directionalContent",
    fullWidth: Boolean = false,
    content: @Composable (T) -> Unit,
) {
    val nudge = with(LocalDensity.current) { 24.dp.roundToPx() }
    AnimatedContent(
        targetState = targetState,
        modifier = if (fullWidth) modifier.clipToBounds() else modifier,
        transitionSpec = {
            val delta = direction(initialState, targetState)
            val spec = tween<IntOffset>(
                if (fullWidth) FullWidthDurationMs else InPageDurationMs,
                easing = FastOutSlowInEasing,
            )

            fun enter(fromRight: Boolean): EnterTransition {
                val slide = slideInHorizontally(spec) { paneWidth ->
                    val offset = if (fullWidth) paneWidth else nudge
                    if (fromRight) offset else -offset
                }
                return if (fullWidth) slide else slide + fadeIn(tween(InPageDurationMs))
            }

            fun exit(towardsLeft: Boolean): ExitTransition {
                val slide = slideOutHorizontally(spec) { paneWidth ->
                    val offset = if (fullWidth) paneWidth else nudge
                    if (towardsLeft) -offset else offset
                }
                return if (fullWidth) slide else slide + fadeOut(tween(InPageDurationMs))
            }

            when {
                delta > 0 -> enter(fromRight = true) togetherWith exit(towardsLeft = true)
                delta < 0 -> enter(fromRight = false) togetherWith exit(towardsLeft = false)
                else ->
                    fadeIn(tween(ContextSwapDurationMs)) togetherWith fadeOut(tween(ContextSwapDurationMs))
            }
        },
        label = label,
        content = { state -> content(state) },
    )
}
