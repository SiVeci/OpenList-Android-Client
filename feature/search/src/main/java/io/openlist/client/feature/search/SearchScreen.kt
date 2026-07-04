package io.openlist.client.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.AppTopBar
import io.openlist.client.core.designsystem.components.EmptyState
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.ListRowItem
import io.openlist.client.core.designsystem.components.LoadingState
import io.openlist.client.core.designsystem.components.SearchBar
import io.openlist.client.core.model.SearchHistoryItem
import io.openlist.client.core.model.SearchResultItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenDirectory: (path: String) -> Unit,
    onOpenFileDetail: (path: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Column {
                AppTopBar(title = "搜索", onBack = onBack)
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                    SearchBar(
                        query = uiState.query,
                        onQueryChange = viewModel::onQueryChange,
                        onSearch = { viewModel.search() },
                        onClear = { viewModel.onQueryChange("") },
                    )
                    Row(
                        modifier = Modifier.padding(top = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        FilterChip(
                            selected = uiState.scope == SearchScope.CURRENT_DIR,
                            onClick = { viewModel.onScopeChange(SearchScope.CURRENT_DIR) },
                            label = { Text("当前目录") },
                        )
                        FilterChip(
                            selected = uiState.scope == SearchScope.GLOBAL,
                            onClick = { viewModel.onScopeChange(SearchScope.GLOBAL) },
                            label = { Text("全局") },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                uiState.isSearching -> LoadingState(modifier = Modifier.fillMaxSize())
                uiState.notAvailable -> ErrorBar(message = "该实例未启用搜索索引", modifier = Modifier.fillMaxWidth())
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
                else -> {
                    Text(
                        text = "在${if (uiState.scope == SearchScope.GLOBAL) "全部文件" else "当前目录"}中搜索到 ${uiState.results.size} 项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.results, key = { it.path }) { result ->
                            ListRowItem(
                                name = result.name,
                                isDir = result.isDir,
                                sizeText = if (result.isDir) null else formatSize(result.size),
                                modifiedText = result.path,
                                onClick = {
                                    if (result.isDir) onOpenDirectory(result.path) else onOpenFileDetail(result.path)
                                },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("搜索历史", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onClear) { Text("清空") }
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.md)) {
            items(history, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(item) }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(item.keyword, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
