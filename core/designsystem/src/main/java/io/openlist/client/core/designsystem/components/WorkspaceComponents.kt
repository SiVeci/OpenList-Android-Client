package io.openlist.client.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.OpenListPalette
import io.openlist.client.core.designsystem.OpenListTheme
import io.openlist.client.core.designsystem.PillShape
import io.openlist.client.core.designsystem.Spacing

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (actionText != null && onActionClick != null) {
            Row(
                modifier = Modifier.clickable(onClick = onActionClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun QuickActionTile(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    subtitle: String? = null,
    plateTone: PlateTone = PlateTone.PRIMARY,
    enabled: Boolean = true,
) {
    val colors = plateColors(plateTone)
    Column(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        IconPlate(
            icon = icon,
            contentDescription = label,
            background = colors.background,
            content = if (enabled) colors.content else MaterialTheme.colorScheme.onSurfaceVariant,
            size = 40.dp,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
            content = content,
        )
    }
}

@Composable
fun EntryRow(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    plateTone: PlateTone = PlateTone.PRIMARY,
    trailing: @Composable RowScope.() -> Unit = {
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    },
) {
    val colors = plateColors(plateTone)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        IconPlate(
            icon = icon,
            contentDescription = null,
            background = colors.background,
            content = colors.content,
            size = 40.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

@Composable
fun InstanceSwitcherChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, PillShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PillShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (leading != null) {
            leading()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

data class StatusSummaryMetric(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val tone: StatusTone = StatusTone.NEUTRAL,
)

@Composable
fun StatusSummaryStrip(
    metrics: List<StatusSummaryMetric>,
    modifier: Modifier = Modifier,
) {
    GroupCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            metrics.forEachIndexed { index, metric ->
                SummaryMetricItem(metric = metric, modifier = Modifier.weight(1f))
                if (index != metrics.lastIndex) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = Spacing.sm)
                            .size(width = 1.dp, height = 28.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
    }
}

@Composable
fun CapabilityChips(
    labels: List<String>,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.NEUTRAL,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEach { label ->
            StatusBadge(text = label, tone = tone)
        }
    }
}

@Composable
private fun SummaryMetricItem(
    metric: StatusSummaryMetric,
    modifier: Modifier = Modifier,
) {
    val colors = summaryColors(metric.tone)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = metric.icon,
            contentDescription = null,
            tint = colors.content,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.padding(start = Spacing.xs)) {
            Text(
                text = metric.value,
                style = MaterialTheme.typography.titleSmall,
                color = colors.content,
                maxLines = 1,
            )
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun summaryColors(tone: StatusTone): PlateColors {
    val extended = OpenListTheme.extendedColors
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        StatusTone.SUCCESS -> PlateColors(extended.success.copy(alpha = 0.12f), extended.success)
        StatusTone.WARNING, StatusTone.PENDING -> PlateColors(extended.warning.copy(alpha = 0.12f), extended.warning)
        StatusTone.ERROR -> PlateColors(scheme.error.copy(alpha = 0.12f), scheme.error)
        // Activity metrics read as strong ink, not purple (产品级中性化, 2026-07-11).
        StatusTone.PRIMARY, StatusTone.RUNNING -> PlateColors(scheme.surfaceVariant, scheme.onSurface)
        StatusTone.NEUTRAL -> PlateColors(scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
}

enum class PlateTone { PRIMARY, SUCCESS, WARNING, ERROR, INFO, NEUTRAL }

private data class PlateColors(val background: Color, val content: Color)

@Composable
private fun plateColors(tone: PlateTone): PlateColors {
    val extended = OpenListTheme.extendedColors
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        // Neutral by default (产品级中性化, 2026-07-11): purple is reserved for
        // the dominant CTA, so the workhorse plate is warm gray + ink.
        PlateTone.PRIMARY -> PlateColors(scheme.surfaceVariant, scheme.onSurface)
        PlateTone.SUCCESS -> PlateColors(extended.success.copy(alpha = 0.12f), extended.success)
        PlateTone.WARNING -> PlateColors(extended.warning.copy(alpha = 0.12f), extended.warning)
        PlateTone.ERROR -> PlateColors(scheme.error.copy(alpha = 0.12f), scheme.error)
        PlateTone.INFO -> pastelStyle(OpenListPalette.TintCream, OpenListPalette.BrandBrown)
            .let { PlateColors(it.container, it.content) }
        PlateTone.NEUTRAL -> PlateColors(scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
}

@Composable
private fun IconPlate(
    icon: ImageVector,
    contentDescription: String?,
    background: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 64.dp,
    shape: Shape = MaterialTheme.shapes.large,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(background, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = content,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}
