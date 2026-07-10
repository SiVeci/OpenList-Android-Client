package io.openlist.client.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.OpenListPalette
import io.openlist.client.core.designsystem.OpenListTheme
import io.openlist.client.core.designsystem.PillShape
import io.openlist.client.core.designsystem.Spacing

enum class StatusTone { NEUTRAL, SUCCESS, WARNING, ERROR, PRIMARY, RUNNING, PENDING }

@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.NEUTRAL,
) {
    val (background, content) = badgeColors(tone)
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = modifier
            .background(background, PillShape)
            .padding(horizontal = Spacing.xs, vertical = 2.dp),
    )
}

@Composable
private fun badgeColors(tone: StatusTone): Pair<Color, Color> {
    val extended = OpenListTheme.extendedColors
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        StatusTone.NEUTRAL -> scheme.surfaceVariant to scheme.onSurfaceVariant
        StatusTone.SUCCESS -> extended.success.copy(alpha = 0.14f) to extended.success
        StatusTone.WARNING -> extended.warning.copy(alpha = 0.14f) to extended.warning
        StatusTone.ERROR -> scheme.error.copy(alpha = 0.14f) to scheme.error
        // DESIGN.md pill-tab-active (ink-deep chip, on-dark text) instead of a
        // solid purple badge — purple stays reserved for the dominant CTA.
        StatusTone.PRIMARY -> scheme.inverseSurface to scheme.inverseOnSurface
        // badge-tag lavender+purple-800 (v0.3_EXECUTION_PLAN.md §7.1) — running tasks.
        StatusTone.RUNNING -> OpenListPalette.TintLavender to OpenListPalette.BrandPurple800
        // badge-tag peach+orange-deep — pending/waiting tasks.
        StatusTone.PENDING -> OpenListPalette.TintPeach to OpenListPalette.BrandOrangeDeep
    }
}
