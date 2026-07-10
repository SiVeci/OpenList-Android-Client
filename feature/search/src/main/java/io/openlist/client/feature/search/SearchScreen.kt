package io.openlist.client.feature.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.AppTopBar
import io.openlist.client.core.designsystem.components.EmptyState
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.GroupCard
import io.openlist.client.core.designsystem.components.LoadingState
import io.openlist.client.core.designsystem.components.SearchBar
import io.openlist.client.core.designsystem.components.SegmentedSelector
import io.openlist.client.core.designsystem.components.StatusBadge
import io.openlist.client.core.designsystem.components.StatusTone
import io.openlist.client.core.model.PreviewKindResolver
import io.openlist.client.core.model.SearchHistoryItem
import io.openlist.client.core.model.SearchResultItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenDirectory: (path: String) -> Unit,
    onOpenFile: (path: String) -> Unit,
    onOpenFileDetail: (path: String) -> Unit,
    onOpenAdminIndex: (() -> Unit)? = null,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { AppTopBar(title = "搜索", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            SearchControlPanel(
                query = uiState.query,
                scope = uiState.scope,
                scopePath = uiState.scopePath,
                onQueryChange = viewModel::onQueryChange,
                onSearch = { viewModel.search() },
                onClear = { viewModel.onQueryChange("") },
                onScopeChange = viewModel::onScopeChange,
                onOpenDirectory = onOpenDirectory,
            )
            when {
                uiState.isSearching -> LoadingState(modifier = Modifier.fillMaxSize())
                uiState.notAvailable -> IndexHintCard(
                    onOpenAdminIndex = onOpenAdminIndex,
                    modifier = Modifier.padding(Spacing.md),
                )
                uiState.errorMessage != null -> ErrorBar(
                    message = uiState.errorMessage!!,
                    onRetry = { viewModel.search() },
                    modifier = Modifier.fillMaxWidth(),
                )
                !uiState.hasSearched -> HistorySection(
                    history = uiState.history,
                    onSelect = { viewModel.searchFromHistory(it) },
                    onDelete = { viewModel.deleteHistoryItem(it) },
                    onClear = { viewModel.clearHistory() },
                )
                uiState.results.isEmpty() -> EmptyState(
                    title = "没有找到匹配的文件",
                    modifier = Modifier.fillMaxSize(),
                )
                else -> SearchResults(
                    scope = uiState.scope,
                    results = uiState.results,
                    onOpenDirectory = onOpenDirectory,
                    onOpenFile = onOpenFile,
                    onOpenFileDetail = onOpenFileDetail,
                )
            }
        }
    }
}

@Composable
private fun SearchControlPanel(
    query: String,
    scope: SearchScope,
    scopePath: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onScopeChange: (SearchScope) -> Unit,
    onOpenDirectory: (String) -> Unit,
) {
    GroupCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchBar(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                onClear = onClear,
                placeholder = "搜索文件、文件夹或路径",
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onSearch) {
                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(Spacing.xxs))
                Text("搜索")
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        SegmentedSelector(
            options = listOf("当前目录", "全部文件"),
            selectedIndex = if (scope == SearchScope.CURRENT_DIR) 0 else 1,
            onSelectedIndexChange = { index ->
                onScopeChange(if (index == 0) SearchScope.CURRENT_DIR else SearchScope.GLOBAL)
            },
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        PathHintRow(
            path = if (scope == SearchScope.CURRENT_DIR) scopePath else "全实例文件",
            icon = if (scope == SearchScope.CURRENT_DIR) Icons.Outlined.Folder else Icons.Outlined.ManageSearch,
            enabled = scope == SearchScope.CURRENT_DIR,
            onClick = { onOpenDirectory(scopePath) },
        )
    }
}

@Composable
private fun PathHintRow(
    path: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(
            text = path,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (enabled) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun HistorySection(
    history: List<SearchHistoryItem>,
    onSelect: (SearchHistoryItem) -> Unit,
    onDelete: (SearchHistoryItem) -> Unit,
    onClear: () -> Unit,
) {
    if (history.isEmpty()) {
        EmptyState(title = "输入关键词开始搜索", modifier = Modifier.fillMaxSize())
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("搜索历史", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onClear) { Text("全部删除") }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            history.forEach { item ->
                HistoryChip(
                    item = item,
                    onSelect = onSelect,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun HistoryChip(
    item: SearchHistoryItem,
    onSelect: (SearchHistoryItem) -> Unit,
    onDelete: (SearchHistoryItem) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .clickable { onSelect(item) }
                .padding(start = Spacing.sm, end = Spacing.xxs, top = Spacing.xxs, bottom = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(
                text = item.keyword,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = { onDelete(item) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SearchResults(
    scope: SearchScope,
    results: List<SearchResultItem>,
    onOpenDirectory: (path: String) -> Unit,
    onOpenFile: (path: String) -> Unit,
    onOpenFileDetail: (path: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Text(
                text = "在${if (scope == SearchScope.GLOBAL) "全部文件" else "当前目录"}中搜索到 ${results.size} 项",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.xs),
            )
        }
        items(results, key = { it.path }) { result ->
            SearchResultRow(
                result = result,
                onClick = {
                    when {
                        result.isDir -> onOpenDirectory(result.path)
                        PreviewKindResolver.isInAppPreviewable(PreviewKindResolver.resolve(result.name)) -> onOpenFile(result.path)
                        else -> onOpenFileDetail(result.path)
                    }
                },
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    result: SearchResultItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = if (result.isDir) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = result.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ResultBadge(result)
                }
                Text(
                    text = result.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!result.isDir) {
                    Text(
                        text = formatSize(result.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResultBadge(result: SearchResultItem) {
    when {
        result.isDir -> StatusBadge(text = "目录", tone = StatusTone.NEUTRAL)
        PreviewKindResolver.isInAppPreviewable(PreviewKindResolver.resolve(result.name)) ->
            StatusBadge(text = "可预览", tone = StatusTone.SUCCESS)
    }
}

@Composable
private fun IndexHintCard(
    onOpenAdminIndex: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    GroupCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "搜索索引未启用",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "当前实例还没有可用索引。管理员可以在管理台索引页构建或更新索引后再搜索。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onOpenAdminIndex != null) {
                    TextButton(onClick = onOpenAdminIndex) {
                        Text("去更新索引")
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroup = (kotlin.math.ln(bytes.toDouble()) / kotlin.math.ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / Math.pow(1024.0, digitGroup.toDouble())
    return if (digitGroup == 0) "$bytes ${units[0]}" else String.format(java.util.Locale.getDefault(), "%.1f %s", value, units[digitGroup])
}
