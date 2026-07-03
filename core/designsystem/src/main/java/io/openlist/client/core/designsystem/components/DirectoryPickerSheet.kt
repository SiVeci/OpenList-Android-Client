package io.openlist.client.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.Spacing

/** One directory entry offered by [DirectoryPickerSheet]. */
data class DirectoryPickerEntry(val name: String, val path: String)

sealed class DirectoryPickerContent {
    data object Loading : DirectoryPickerContent()
    data class Content(val entries: List<DirectoryPickerEntry>) : DirectoryPickerContent()
    data class Error(val message: String) : DirectoryPickerContent()
}

/**
 * General-purpose target-directory picker (v0.2_EXECUTION_PLAN.md §13.6),
 * shared by move/copy now and upload/unarchive/offline-download later. Purely
 * driven by [breadcrumbSegments]/[content] — the caller (a ViewModel) owns
 * navigation history and the actual fs/list call, since this module has no
 * network/domain dependency.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryPickerSheet(
    title: String,
    breadcrumbSegments: List<String>,
    content: DirectoryPickerContent,
    onSegmentClick: (index: Int) -> Unit,
    onEnterDirectory: (DirectoryPickerEntry) -> Unit,
    onSelectCurrent: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    selecting: Boolean = false,
    sheetState: SheetState = androidx.compose.material3.rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
            }
        }
        Breadcrumb(segments = breadcrumbSegments, onSegmentClick = onSegmentClick)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
            when (content) {
                DirectoryPickerContent.Loading -> LoadingState(modifier = Modifier.fillMaxWidth().height(320.dp))
                is DirectoryPickerContent.Error -> ErrorBar(message = content.message, onRetry = onRefresh)
                is DirectoryPickerContent.Content -> if (content.entries.isEmpty()) {
                    EmptyState(title = "没有子目录", modifier = Modifier.fillMaxWidth().height(320.dp))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(content.entries, key = { it.path }) { entry ->
                            ListRowItem(
                                name = entry.name,
                                isDir = true,
                                onClick = { onEnterDirectory(entry) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SecondaryButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
            PrimaryButton(
                text = "选择当前目录",
                onClick = onSelectCurrent,
                modifier = Modifier.weight(1f),
                loading = selecting,
            )
        }
    }
}
