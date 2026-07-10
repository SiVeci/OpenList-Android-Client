package io.openlist.client.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.OpenListPalette
import io.openlist.client.core.designsystem.Spacing

/**
 * Navy hero band for entry screens (DESIGN.md hero-band-dark): brand-navy
 * background, on-dark headline/subtitle, and the scattered sticky-note dot
 * decoration in the brand color spectrum. The band sits below the status
 * bar (the inset is applied once, globally, by OpenListNavHost), so it must
 * not add its own inset padding or force light status-bar icons.
 */
@Composable
fun HeroHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = OpenListPalette.OnDark,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = OpenListPalette.OnDarkMuted,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
    },
) {
    Box(modifier = modifier.fillMaxWidth().background(OpenListPalette.BrandNavy)) {
        StickyNoteDots(Modifier.matchParentSize())
        Column(
            modifier = Modifier.padding(bottom = Spacing.lg),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xxs),
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = OpenListPalette.OnDark,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                CompositionLocalProvider(LocalContentColor provides OpenListPalette.OnDark) {
                    actions()
                }
            }
            Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
                content()
            }
        }
    }
}

/** Scattered brand-colored dots + tilted sticky notes, kept at low alpha so
 * they read as atmosphere rather than content (DESIGN.md: decoration only). */
@Composable
private fun StickyNoteDots(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        fun dot(color: Color, x: Float, y: Float, radius: Dp) {
            drawCircle(color.copy(alpha = 0.55f), radius.toPx(), Offset(size.width * x, size.height * y))
        }
        fun note(color: Color, x: Float, y: Float, side: Dp, degrees: Float) {
            val px = size.width * x
            val py = size.height * y
            rotate(degrees, pivot = Offset(px, py)) {
                drawRoundRect(
                    color = color.copy(alpha = 0.45f),
                    topLeft = Offset(px, py),
                    size = Size(side.toPx(), side.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
        dot(OpenListPalette.BrandPink, 0.86f, 0.24f, 4.dp)
        dot(OpenListPalette.BrandTeal, 0.66f, 0.14f, 3.dp)
        dot(OpenListPalette.BrandYellow, 0.94f, 0.55f, 5.dp)
        dot(OpenListPalette.BrandPurple, 0.75f, 0.78f, 3.dp)
        dot(OpenListPalette.BrandOrange, 0.58f, 0.60f, 2.5.dp)
        note(OpenListPalette.BrandYellow, 0.80f, 0.42f, 9.dp, 14f)
        note(OpenListPalette.BrandPink, 0.90f, 0.80f, 7.dp, -10f)
    }
}
