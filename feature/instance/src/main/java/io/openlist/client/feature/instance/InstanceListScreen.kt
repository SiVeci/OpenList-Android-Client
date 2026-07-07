package io.openlist.client.feature.instance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.openlist.client.core.designsystem.PillShape
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.EmptyState
import io.openlist.client.core.designsystem.components.GroupCard
import io.openlist.client.core.designsystem.components.InstanceSwitcherChip
import io.openlist.client.core.designsystem.components.PlateTone
import io.openlist.client.core.designsystem.components.PrimaryButton
import io.openlist.client.core.designsystem.components.QuickActionTile
import io.openlist.client.core.designsystem.components.SectionHeader
import io.openlist.client.core.designsystem.components.SheetHeader
import io.openlist.client.core.designsystem.components.StatusBadge
import io.openlist.client.core.designsystem.components.StatusSummaryMetric
import io.openlist.client.core.designsystem.components.StatusSummaryStrip
import io.openlist.client.core.designsystem.components.StatusTone
import io.openlist.client.core.model.AdminEntryVisibility
import io.openlist.client.core.model.Instance
import io.openlist.client.core.model.Session
import io.openlist.client.core.model.TaskSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceListScreen(
    onAddInstance: () -> Unit,
    onOpenInstance: (instanceId: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFiles: (instanceId: String) -> Unit = onOpenInstance,
    onOpenSearch: (instanceId: String) -> Unit = {},
    onOpenTaskCenter: (instanceId: String) -> Unit = {},
    onOpenShareList: (instanceId: String) -> Unit = {},
    onOpenAdmin: (instanceId: String) -> Unit = {},
    viewModel: InstanceListViewModel = hiltViewModel(),
) {
    val homeUiState by viewModel.homeUiState.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val instances = homeUiState.instances
    val sessionsByInstanceId = homeUiState.sessionsByInstanceId
    var pendingDelete by remember { mutableStateOf<Instance?>(null) }
    var showInstanceSwitcher by remember { mutableStateOf(false) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        if (instances.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                HomeHeaderSection(
                    currentInstance = null,
                    onOpenSettings = onOpenSettings,
                    onAddInstance = onAddInstance,
                    onSwitchInstance = { showInstanceSwitcher = true },
                )
                EmptyState(
                    title = "还没有添加实例",
                    description = "添加一个 OpenList 实例地址即可开始浏览文件。",
                    modifier = Modifier.fillMaxWidth(),
                    action = { PrimaryButton(text = "添加实例", onClick = onAddInstance) },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item {
                    HomeHeaderSection(
                        currentInstance = homeUiState.currentInstance,
                        onOpenSettings = onOpenSettings,
                        onAddInstance = onAddInstance,
                        onSwitchInstance = { showInstanceSwitcher = true },
                    )
                }
                item {
                    HomeSearchEntry(
                        enabled = homeUiState.currentInstance != null,
                        onClick = { homeUiState.currentInstance?.let { onOpenSearch(it.id) } },
                        modifier = Modifier.padding(horizontal = Spacing.md),
                    )
                }
                item {
                    HomeActionsSection(
                        currentInstance = homeUiState.currentInstance,
                        adminEntryVisibility = homeUiState.adminEntryVisibility,
                        onOpenFiles = onOpenFiles,
                        onOpenTaskCenter = onOpenTaskCenter,
                        onOpenShareList = onOpenShareList,
                        onOpenAdmin = onOpenAdmin,
                        modifier = Modifier.padding(horizontal = Spacing.md),
                    )
                }
                item {
                    HomeTaskSummarySection(
                        currentInstance = homeUiState.currentInstance,
                        taskSummary = homeUiState.taskSummary,
                        onOpenTaskCenter = onOpenTaskCenter,
                        modifier = Modifier.padding(horizontal = Spacing.md),
                    )
                }
                item {
                    HomeInstancesSection(
                        instances = instances,
                        sessionsByInstanceId = sessionsByInstanceId,
                        connectionStatus = connectionStatus,
                        onAddInstance = onAddInstance,
                        onOpenInstance = { instance ->
                            viewModel.selectInstance(instance)
                            onOpenInstance(instance.id)
                        },
                        onTestConnection = viewModel::testConnection,
                        onDelete = { pendingDelete = it },
                        modifier = Modifier.padding(horizontal = Spacing.md),
                    )
                }
            }
        }
    }

    if (showInstanceSwitcher) {
        ModalBottomSheet(onDismissRequest = { showInstanceSwitcher = false }) {
            SheetHeader(
                title = "切换实例",
                subtitle = "选择一个实例作为当前工作台上下文。",
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
            Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                instances.forEachIndexed { index, instance ->
                    InstanceSwitcherRow(
                        instance = instance,
                        session = sessionsByInstanceId[instance.id],
                        onClick = {
                            viewModel.selectInstance(instance)
                            showInstanceSwitcher = false
                        },
                    )
                    if (index != instances.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
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
private fun HomeHeaderSection(
    currentInstance: Instance?,
    onOpenSettings: () -> Unit,
    onAddInstance: () -> Unit,
    onSwitchInstance: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "OpenList",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        InstanceSwitcherChip(
            label = currentInstance?.name ?: "选择实例",
            onClick = onSwitchInstance,
            enabled = currentInstance != null,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "设置")
        }
        IconButton(onClick = onAddInstance) {
            Icon(Icons.Outlined.Add, contentDescription = "添加实例")
        }
    }
}

@Composable
private fun HomeSearchEntry(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = "搜索文件、路径或分享",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "全部文件",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, PillShape)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun HomeActionsSection(
    currentInstance: Instance?,
    adminEntryVisibility: AdminEntryVisibility,
    onOpenFiles: (instanceId: String) -> Unit,
    onOpenTaskCenter: (instanceId: String) -> Unit,
    onOpenShareList: (instanceId: String) -> Unit,
    onOpenAdmin: (instanceId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupCard(modifier = modifier) {
        SectionHeader(title = "快速入口")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HomeActionTile(
                label = "根目录",
                icon = Icons.Outlined.Folder,
                plateTone = PlateTone.PRIMARY,
                enabled = currentInstance != null,
                onClick = { currentInstance?.let { onOpenFiles(it.id) } },
            )
            HomeActionTile(
                label = "我的分享",
                icon = Icons.Outlined.Share,
                plateTone = PlateTone.SUCCESS,
                enabled = currentInstance != null,
                onClick = { currentInstance?.let { onOpenShareList(it.id) } },
            )
            HomeActionTile(
                label = "任务中心",
                icon = Icons.Outlined.TaskAlt,
                plateTone = PlateTone.WARNING,
                enabled = currentInstance != null,
                onClick = { currentInstance?.let { onOpenTaskCenter(it.id) } },
            )
            HomeActionTile(
                label = "管理台",
                icon = Icons.Outlined.AdminPanelSettings,
                plateTone = PlateTone.INFO,
                enabled = currentInstance != null && adminEntryVisibility == AdminEntryVisibility.ENABLED,
                onClick = { currentInstance?.let { onOpenAdmin(it.id) } },
            )
        }
    }
}

@Composable
private fun RowScope.HomeActionTile(
    label: String,
    icon: ImageVector,
    plateTone: PlateTone,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    QuickActionTile(
        label = label,
        icon = icon,
        plateTone = plateTone,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun HomeTaskSummarySection(
    currentInstance: Instance?,
    taskSummary: TaskSummary,
    onOpenTaskCenter: (instanceId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionHeader(
            title = "任务摘要",
            actionText = "查看全部",
            onActionClick = currentInstance?.let { instance ->
                { onOpenTaskCenter(instance.id) }
            },
        )
        StatusSummaryStrip(
            metrics = listOf(
                StatusSummaryMetric(
                    label = "运行中",
                    value = taskSummary.runningCount.toString(),
                    icon = Icons.Outlined.Refresh,
                    tone = StatusTone.RUNNING,
                ),
                StatusSummaryMetric(
                    label = "待处理",
                    value = taskSummary.pendingCount.toString(),
                    icon = Icons.Outlined.TaskAlt,
                    tone = StatusTone.PENDING,
                ),
                StatusSummaryMetric(
                    label = "失败",
                    value = taskSummary.failedCount.toString(),
                    icon = Icons.Outlined.DeleteOutline,
                    tone = StatusTone.ERROR,
                ),
                StatusSummaryMetric(
                    label = "已完成",
                    value = taskSummary.completedCount.toString(),
                    icon = Icons.Outlined.CheckCircle,
                    tone = StatusTone.SUCCESS,
                ),
            ),
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

@Composable
private fun InstanceSwitcherRow(
    instance: Instance,
    session: Session?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = instance.name,
                    style = MaterialTheme.typography.titleSmall,
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
            LoginStatusLabel(session)
        }
        if (instance.isCurrent) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeInstancesSection(
    instances: List<Instance>,
    sessionsByInstanceId: Map<String, Session>,
    connectionStatus: Map<String, ConnectionCheck>,
    onAddInstance: () -> Unit,
    onOpenInstance: (Instance) -> Unit,
    onTestConnection: (Instance) -> Unit,
    onDelete: (Instance) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionHeader(
            title = "我的实例",
            actionText = "添加",
            onActionClick = onAddInstance,
        )
        GroupCard(modifier = Modifier.padding(top = Spacing.sm)) {
            instances.forEachIndexed { index, instance ->
                InstanceRow(
                    instance = instance,
                    session = sessionsByInstanceId[instance.id],
                    connectionCheck = connectionStatus[instance.id],
                    onClick = { onOpenInstance(instance) },
                    onTestConnection = { onTestConnection(instance) },
                    onDelete = { onDelete(instance) },
                )
                if (index != instances.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
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
    val rowShape = MaterialTheme.shapes.large
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (instance.isCurrent) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), rowShape)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), rowShape)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(
                    if (instance.isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.large,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Storage,
                contentDescription = null,
                tint = if (instance.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
        Column(horizontalAlignment = Alignment.End) {
            Row {
                IconButton(onClick = onTestConnection) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "测试连接")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除实例")
                }
            }
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
