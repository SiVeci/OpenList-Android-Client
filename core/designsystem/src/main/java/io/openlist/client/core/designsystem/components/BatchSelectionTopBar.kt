package io.openlist.client.core.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Top bar shown while the file list is in batch-selection mode
 * (v0.2_EXECUTION_PLAN.md §13.7). Rides on [AppTopBar] so it keeps the same
 * compact 48dp height as the regular file-list header it swaps with; delete
 * uses the error tint so it stays visually distinct from move/copy.
 */
@Composable
fun BatchSelectionTopBar(
    selectedCount: Int,
    onExit: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    allSelected: Boolean = false,
) {
    AppTopBar(
        title = "已选 $selectedCount 项",
        modifier = modifier,
        leading = {
            IconButton(onClick = onExit) {
                Icon(Icons.Outlined.Close, contentDescription = "退出选择")
            }
        },
        actions = {
            IconButton(onClick = onToggleSelectAll) {
                Icon(
                    Icons.Filled.SelectAll,
                    contentDescription = if (allSelected) "取消全选" else "全选",
                )
            }
            IconButton(onClick = onMove, enabled = selectedCount > 0) {
                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "移动")
            }
            IconButton(onClick = onCopy, enabled = selectedCount > 0) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "复制")
            }
            IconButton(onClick = onDelete, enabled = selectedCount > 0) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else disabledContentColor(),
                )
            }
        },
    )
}

@Composable
private fun disabledContentColor() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
