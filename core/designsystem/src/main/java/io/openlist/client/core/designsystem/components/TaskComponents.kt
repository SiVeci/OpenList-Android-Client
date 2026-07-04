package io.openlist.client.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.Spacing

/** Mirrors `UnifiedTaskStatus` (`:core:model`) — kept local since this module
 * has no dependency on it, same precedent as `UploadItemStatus`. */
enum class TaskCardStatus { PENDING, RUNNING, SUCCESS, FAILED, CANCELLED, UNKNOWN }

@Composable
fun TaskStatusBadge(status: TaskCardStatus, modifier: Modifier = Modifier) {
    val (text, tone) = when (status) {
        TaskCardStatus.PENDING -> "等待中" to StatusTone.PENDING
        TaskCardStatus.RUNNING -> "运行中" to StatusTone.RUNNING
        TaskCardStatus.SUCCESS -> "已完成" to StatusTone.SUCCESS
        TaskCardStatus.FAILED -> "失败" to StatusTone.ERROR
        TaskCardStatus.CANCELLED -> "已取消" to StatusTone.NEUTRAL
        TaskCardStatus.UNKNOWN -> "未知" to StatusTone.NEUTRAL
    }
    StatusBadge(text = text, modifier = modifier, tone = tone)
}

/** Same visual language as [UploadProgressItem]'s bar: primary while running,
 * error on failure, success on completion. [progress] is `0f..1f`, or `null`
 * for indeterminate. */
@Composable
fun TaskProgressIndicator(
    status: TaskCardStatus,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    val color = when (status) {
        TaskCardStatus.FAILED -> MaterialTheme.colorScheme.error
        TaskCardStatus.SUCCESS -> io.openlist.client.core.designsystem.OpenListTheme.extendedColors.success
        else -> MaterialTheme.colorScheme.primary
    }
    if (progress != null) {
        val animated by animateFloatAsState(targetValue = progress, label = "taskProgress")
        LinearProgressIndicator(progress = { animated }, modifier = modifier.fillMaxWidth(), color = color)
    } else {
        LinearProgressIndicator(modifier = modifier.fillMaxWidth(), color = color)
    }
}

/**
 * One task-center row (v0.3_EXECUTION_PLAN.md §7.1/§13) — shared by local
 * upload/download and remote tasks (module plan's "TaskCard"/"RemoteTaskCard"
 * are the same shape, not two components). [actions] holds the
 * source-specific trailing controls (cancel / open file / jump to folder).
 */
@Composable
fun TaskCard(
    icon: ImageVector,
    title: String,
    status: TaskCardStatus,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float? = null,
    showProgress: Boolean = status == TaskCardStatus.RUNNING || status == TaskCardStatus.PENDING,
    errorMessage: String? = null,
    onClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TaskStatusBadge(status)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), content = actions)
            }
            if (showProgress) {
                androidx.compose.foundation.layout.Spacer(Modifier.size(Spacing.xs))
                TaskProgressIndicator(status = status, progress = progress)
            }
            if (status == TaskCardStatus.FAILED && errorMessage != null) {
                androidx.compose.foundation.layout.Spacer(Modifier.size(Spacing.xxs))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Underline-style segmented tabs (v0.3_EXECUTION_PLAN.md §7.1: "下划线式,2px
 * ink 底边激活态", explicitly not a pill). [tabs] and [selectedIndex] are
 * caller-owned so this stays free of any `TaskSource`/`UnifiedTask` dependency.
 */
@Composable
fun TaskTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(Spacing.xxs))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (selected) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
}
