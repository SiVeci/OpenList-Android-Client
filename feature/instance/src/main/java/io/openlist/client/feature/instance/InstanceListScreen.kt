package io.openlist.client.feature.instance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.EmptyState
import io.openlist.client.core.designsystem.components.HeroHeader
import io.openlist.client.core.designsystem.components.PrimaryButton
import io.openlist.client.core.designsystem.components.StatusBadge
import io.openlist.client.core.designsystem.components.StatusTone
import io.openlist.client.core.model.Instance
import io.openlist.client.core.model.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceListScreen(
    onAddInstance: () -> Unit,
    onOpenInstance: (instanceId: String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: InstanceListViewModel = hiltViewModel(),
) {
    val instances by viewModel.instances.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val sessionsByInstanceId by viewModel.sessionsByInstanceId.collectAsState()
    var pendingDelete by remember { mutableStateOf<Instance?>(null) }

    Scaffold(
        topBar = {
            HeroHeader(
                title = "OpenList",
                subtitle = "你的所有实例，一个入口",
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddInstance) {
                Icon(Icons.Outlined.Add, contentDescription = "添加实例")
            }
        },
    ) { padding ->
        if (instances.isEmpty()) {
            EmptyState(
                title = "还没有添加实例",
                description = "添加一个 OpenList 实例地址即可开始浏览文件",
                modifier = Modifier.padding(padding),
                action = { PrimaryButton(text = "添加实例", onClick = onAddInstance) },
            )
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(instances, key = { it.id }) { instance ->
                    InstanceRow(
                        instance = instance,
                        session = sessionsByInstanceId[instance.id],
                        connectionCheck = connectionStatus[instance.id],
                        onClick = {
                            viewModel.selectInstance(instance)
                            onOpenInstance(instance.id)
                        },
                        onTestConnection = { viewModel.testConnection(instance) },
                        onDelete = { pendingDelete = instance },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    pendingDelete?.let { instance ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除实例") },
            text = { Text("将删除「${instance.name}」及其登录状态、缓存和下载记录，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteInstance(instance)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun InstanceRow(
    instance: Instance,
    session: Session?,
    connectionCheck: ConnectionCheck?,
    onClick: () -> Unit,
    onTestConnection: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = instance.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (instance.isCurrent) {
                        StatusBadge(text = "当前", tone = StatusTone.PRIMARY)
                    }
                }
                Text(
                    text = instance.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "最近访问 ${formatTimestamp(instance.lastUsedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    LoginStatusLabel(session)
                    ConnectionStatusLabel(connectionCheck)
                }
            }
            Row {
                IconButton(onClick = onTestConnection) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "测试连接")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除实例")
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xxs),
        ) {
            TextButton(onClick = onClick) { Text("进入") }
        }
    }
}

@Composable
private fun LoginStatusLabel(session: Session?) {
    when {
        session == null -> StatusBadge(text = "未登录", tone = StatusTone.NEUTRAL)
        session.isGuest -> StatusBadge(text = "游客模式", tone = StatusTone.WARNING)
        else -> StatusBadge(text = "已登录 · ${session.username ?: "未知用户"}", tone = StatusTone.SUCCESS)
    }
}

@Composable
private fun ConnectionStatusLabel(check: ConnectionCheck?) {
    when (check) {
        null -> Unit
        ConnectionCheck.Testing -> Text(
            text = "正在测试连接…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ConnectionCheck.Reachable -> StatusBadge(text = "可访问", tone = StatusTone.SUCCESS)
        is ConnectionCheck.Unreachable -> StatusBadge(text = check.message, tone = StatusTone.ERROR)
    }
}

private fun formatTimestamp(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
