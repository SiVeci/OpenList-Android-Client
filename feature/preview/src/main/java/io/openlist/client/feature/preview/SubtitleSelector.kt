package io.openlist.client.feature.preview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.ListRowItem
import io.openlist.client.core.model.FileNode
import io.openlist.client.core.model.SubtitleCandidate

/**
 * Bottom sheet for the video player's subtitle entry point (v0.4_EXECUTION_PLAN.md
 * §11 S6-T2, D-05 explicitly excludes subtitle search/online download/style
 * editing/timeline calibration — this is discovery + selection only).
 *
 * Three sections, top to bottom:
 *  1. "关闭字幕" — always shown, clears whatever is currently selected.
 *  2. Auto-discovered candidates ([SubtitleRepository.findCandidates],
 *     already extension-filtered server-`related`-field results) — each row
 *     shows the file name and format.
 *  3. A collapsed-by-default "从当前目录选择" entry that, once expanded, lists
 *     every non-directory file in the video's own directory (no extension
 *     filtering at all — the user might pick a subtitle file this app's
 *     heuristics didn't recognize, so nothing is excluded here beyond
 *     directories themselves). [onLoadDirectoryEntries] is only invoked the
 *     first time this section is expanded (`remember` guards against
 *     re-fetching on every recomposition/collapse-expand cycle).
 *
 * This composable owns no repository/ViewModel access itself — it is a pure
 * UI shell over callbacks, consistent with [io.openlist.client.core.designsystem.components.ExternalOpenSheet]/
 * [io.openlist.client.core.designsystem.components.FileActionSheet]'s
 * established shape, but lives in `:feature:preview` rather than
 * `:core:designsystem` because assembling/filtering [SubtitleCandidate] data
 * is preview-specific business logic (§9.1 placement rule), not a generic
 * reusable shell.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubtitleSelector(
    candidates: List<SubtitleCandidate>,
    selectedSubtitlePath: String?,
    onSelectCandidate: (SubtitleCandidate) -> Unit,
    onSelectManualFile: (path: String) -> Unit,
    onClearSubtitle: () -> Unit,
    onLoadDirectoryEntries: (onLoaded: (List<FileNode>) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    var manualBrowseExpanded by remember { mutableStateOf(false) }
    var manualEntries by remember { mutableStateOf<List<FileNode>?>(null) }
    var manualLoading by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "字幕",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )

            SubtitleRow(
                label = "关闭字幕",
                icon = Icons.Filled.SubtitlesOff,
                selected = selectedSubtitlePath == null,
                onClick = onClearSubtitle,
            )

            if (candidates.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                candidates.forEach { candidate ->
                    SubtitleRow(
                        label = candidate.name,
                        subtitle = candidate.format?.uppercase(),
                        selected = selectedSubtitlePath == candidate.path,
                        onClick = { onSelectCandidate(candidate) },
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "从当前目录选择",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (manualBrowseExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (manualBrowseExpanded) "收起" else "展开",
                    modifier = Modifier.clickable { manualBrowseExpanded = !manualBrowseExpanded },
                )
            }

            if (manualBrowseExpanded) {
                LaunchedEffect(Unit) {
                    if (manualEntries == null && !manualLoading) {
                        manualLoading = true
                        onLoadDirectoryEntries { entries ->
                            manualEntries = entries
                            manualLoading = false
                        }
                    }
                }

                when {
                    manualLoading && manualEntries == null -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(Spacing.sm))
                    }
                    manualEntries.isNullOrEmpty() -> Text(
                        text = "该目录下没有其他文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    )
                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        contentPadding = PaddingValues(bottom = Spacing.xs),
                    ) {
                        items(manualEntries.orEmpty(), key = { it.path }) { node ->
                            ListRowItem(
                                name = node.name,
                                isDir = false,
                                selected = selectedSubtitlePath == node.path,
                                onClick = { onSelectManualFile(node.path) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun SubtitleRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) {
            Icon(imageVector = Icons.Filled.Check, contentDescription = "已选择", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
