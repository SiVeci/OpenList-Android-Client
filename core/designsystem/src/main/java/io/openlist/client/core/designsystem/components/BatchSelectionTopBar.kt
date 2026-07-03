package io.openlist.client.core.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Top bar shown while the file list is in batch-selection mode
 * (v0.2_EXECUTION_PLAN.md §13.7). [selectedCount] is rendered as a pill/badge
 * per DESIGN.md's badge language; delete uses the error tint so it stays
 * visually distinct from move/copy even at icon size.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    TopAppBar(
        modifier = modifier,
        title = {
            StatusBadge(text = "已选 $selectedCount 项", tone = StatusTone.PRIMARY)
        },
        navigationIcon = {
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
                    tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else LocalContentColor(),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun LocalContentColor() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
