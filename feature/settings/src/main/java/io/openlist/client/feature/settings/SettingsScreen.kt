package io.openlist.client.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.AppTopBar
import io.openlist.client.core.designsystem.components.EntryRow
import io.openlist.client.core.designsystem.components.GroupCard
import io.openlist.client.core.designsystem.components.PlateTone
import io.openlist.client.core.designsystem.components.StatusBadge
import io.openlist.client.core.designsystem.components.StatusTone
import io.openlist.client.core.model.AdminEntryVisibility
import io.openlist.client.core.model.AuthType
import io.openlist.client.core.model.Session
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenInstances: () -> Unit,
    onOpenTaskCenter: (instanceId: String) -> Unit,
    onOpenAdmin: (instanceId: String) -> Unit,
    onOpenShareList: (instanceId: String) -> Unit = {},
    onOpenShareLink: () -> Unit = {},
    onAddInstance: () -> Unit = {},
    onLoggedOut: (instanceId: String) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val loggingEnabled by viewModel.loggingEnabled.collectAsState()
    val cacheCleared by viewModel.cacheCleared.collectAsState()
    val cacheSizeLabel by viewModel.cacheSizeLabel.collectAsState()
    val currentInstanceId by viewModel.currentInstanceId.collectAsState()
    val currentInstanceName by viewModel.currentInstanceName.collectAsState()
    val currentInstanceBaseUrl by viewModel.currentInstanceBaseUrl.collectAsState()
    val currentInstanceLastUsedAt by viewModel.currentInstanceLastUsedAt.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val adminEntryState by viewModel.adminEntryState.collectAsState()
    val connectionCheck by viewModel.connectionCheck.collectAsState()
    val connectionElapsedMillis by viewModel.connectionElapsedMillis.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val loggedOutInstanceId by viewModel.loggedOutInstanceId.collectAsState()
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var showClearedNotice by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(cacheCleared) {
        if (cacheCleared) {
            showClearedNotice = true
            viewModel.acknowledgeCacheCleared()
        }
    }
    LaunchedEffect(loggedOutInstanceId) {
        loggedOutInstanceId?.let { instanceId ->
            viewModel.acknowledgeLoggedOut()
            onLoggedOut(instanceId)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "我的",
                subtitle = currentInstanceName ?: "管理实例、会话与本地设置",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ProfileCard(
                instanceName = currentInstanceName,
                baseUrl = currentInstanceBaseUrl,
                lastUsedAt = currentInstanceLastUsedAt,
                session = currentSession,
                connectionCheck = connectionCheck,
                connectionElapsedMillis = connectionElapsedMillis,
            )

            GroupCard {
                SectionTitle("常用")
                currentInstanceId?.let { instanceId ->
                    if (currentSession?.isGuest != true) {
                        MineEntry(
                            title = "我的分享",
                            subtitle = "查看和管理当前实例的分享",
                            icon = Icons.Outlined.Share,
                            onClick = { onOpenShareList(instanceId) },
                        )
                    }
                    MineEntry(
                        title = "打开分享链接",
                        subtitle = "粘贴分享链接并访问",
                        icon = Icons.Outlined.Link,
                        onClick = onOpenShareLink,
                    )
                    MineEntry(
                            title = "任务中心",
                            subtitle = "查看上传、下载与离线任务",
                            icon = Icons.AutoMirrored.Outlined.Assignment,
                            onClick = { onOpenTaskCenter(instanceId) },
                        )
                } ?: MineEntry(
                    title = "任务中心",
                    subtitle = "请先添加实例",
                    icon = Icons.AutoMirrored.Outlined.Assignment,
                    enabled = false,
                    onClick = {},
                )
                MineEntry(
                    title = "清理缓存",
                    subtitle = "当前应用缓存约 $cacheSizeLabel",
                    icon = Icons.Outlined.CleaningServices,
                    onClick = { showClearCacheConfirm = true },
                )
            }

            GroupCard {
                SectionTitle("实例")
                MineEntry(
                    title = "切换实例",
                    subtitle = "管理、切换或删除 OpenList 实例",
                    icon = Icons.Outlined.SwapHoriz,
                    onClick = onOpenInstances,
                )
                MineEntry(
                    title = "添加实例",
                    subtitle = "连接新的 OpenList 服务地址",
                    icon = Icons.Outlined.Add,
                    onClick = onAddInstance,
                )
                MineEntry(
                    title = if (isTestingConnection) "测试中" else "测试连接",
                    subtitle = connectionSubtitle(connectionCheck, connectionElapsedMillis),
                    icon = Icons.Outlined.CloudDone,
                    enabled = currentInstanceId != null && !isTestingConnection,
                    onClick = viewModel::testCurrentConnection,
                )
            }

            GroupCard {
                SectionTitle("安全与调试")
                SettingsSwitchRow(
                    title = "调试日志",
                    subtitle = if (loggingEnabled) "已开启，请求日志脱敏后写入 logcat" else "已关闭",
                    checked = loggingEnabled,
                    icon = Icons.Outlined.BugReport,
                    onCheckedChange = viewModel::setLoggingEnabled,
                )
                MineEntry(
                    title = "退出登录",
                    subtitle = if (currentSession == null) "当前实例未登录" else "清除当前实例的本地会话",
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    enabled = currentSession != null,
                    onClick = { showLogoutConfirm = true },
                    tone = PlateTone.WARNING,
                )
            }

            GroupCard {
                SectionTitle("管理")
                AdminEntry(
                    state = adminEntryState,
                    instanceId = currentInstanceId,
                    instanceName = currentInstanceName,
                    onOpenAdmin = onOpenAdmin,
                )
            }

            GroupCard {
                SectionTitle("关于")
                MineEntry(
                    title = "版本",
                    subtitle = "OpenList Client v${BuildConfig.VERSION_NAME}",
                    icon = Icons.Outlined.Info,
                    onClick = {},
                )
                MineEntry(
                    title = "开源许可证",
                    subtitle = "查看第三方库许可",
                    icon = Icons.Outlined.Folder,
                    onClick = { showLicenses = true },
                )
            }
        }
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("清理缓存") },
            text = { Text("将清除本地目录缓存；登录状态和下载记录不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCache()
                    showClearCacheConfirm = false
                }) { Text("清理") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录") },
            text = { Text("将清除当前实例的本地会话，之后需要重新登录。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.logoutCurrentSession()
                    showLogoutConfirm = false
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showClearedNotice) {
        AlertDialog(
            onDismissRequest = { showClearedNotice = false },
            title = { Text("已清理") },
            text = { Text("本地目录缓存已清空。") },
            confirmButton = {
                TextButton(onClick = { showClearedNotice = false }) { Text("好的") }
            },
        )
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("开源许可证") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    OSS_LIBRARIES.forEach { (name, license) ->
                        Text(text = name, style = MaterialTheme.typography.bodyMedium)
                        Text(text = license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun ProfileCard(
    instanceName: String?,
    baseUrl: String?,
    lastUsedAt: Long?,
    session: Session?,
    connectionCheck: SettingsConnectionCheck?,
    connectionElapsedMillis: Long?,
) {
    GroupCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EntryAvatar(text = session?.username ?: instanceName ?: "OpenList")
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session?.username ?: "未登录",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = instanceName ?: "暂无当前实例",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (baseUrl != null) {
                    Text(
                        text = baseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            StatusBadge(
                text = roleLabel(session),
                tone = if (session?.isAdmin == true) StatusTone.PRIMARY else StatusTone.NEUTRAL,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "上次使用 ${formatLastUsed(lastUsedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusBadge(
                text = connectionBadgeText(connectionCheck, connectionElapsedMillis),
                tone = when (connectionCheck) {
                    SettingsConnectionCheck.Reachable -> StatusTone.SUCCESS
                    is SettingsConnectionCheck.Unreachable -> StatusTone.ERROR
                    null -> StatusTone.NEUTRAL
                },
            )
        }
    }
}

@Composable
private fun EntryAvatar(text: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = text,
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Spacing.xs),
    )
}

@Composable
private fun MineEntry(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tone: PlateTone = PlateTone.PRIMARY,
) {
    EntryRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = if (enabled) onClick else null,
        plateTone = if (enabled) tone else PlateTone.NEUTRAL,
    )
}

@Composable
private fun AdminEntry(
    state: AdminEntryVisibility,
    instanceId: String?,
    instanceName: String?,
    onOpenAdmin: (instanceId: String) -> Unit,
) {
    when (state) {
        AdminEntryVisibility.HIDDEN -> MineEntry(
            title = "管理台",
            subtitle = "请先添加并登录实例",
            icon = Icons.Outlined.AdminPanelSettings,
            enabled = false,
            onClick = {},
        )
        AdminEntryVisibility.DISABLED_UNAUTHENTICATED -> MineEntry(
            title = "管理台",
            subtitle = instanceName?.let { "$it · 请先登录管理员账号" } ?: "请先登录管理员账号",
            icon = Icons.AutoMirrored.Outlined.Login,
            enabled = false,
            onClick = {},
        )
        AdminEntryVisibility.DISABLED_NOT_ADMIN -> MineEntry(
            title = "管理台",
            subtitle = instanceName?.let { "$it · 需要管理员权限" } ?: "需要管理员权限",
            icon = Icons.Outlined.AdminPanelSettings,
            enabled = false,
            onClick = {},
        )
        AdminEntryVisibility.ENABLED -> {
            if (instanceId != null) {
                MineEntry(
                    title = "管理台",
                    subtitle = instanceName,
                    icon = Icons.Outlined.AdminPanelSettings,
                    onClick = { onOpenAdmin(instanceId) },
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: (Boolean) -> Unit,
) {
    EntryRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

private fun roleLabel(session: Session?): String = when {
    session == null -> "未登录"
    session.isAdmin -> "管理员"
    session.isGuest -> "游客"
    session.authType == AuthType.TOKEN -> "Token"
    else -> "已登录"
}

private fun connectionSubtitle(check: SettingsConnectionCheck?, elapsedMillis: Long?): String = when (check) {
    SettingsConnectionCheck.Reachable -> "连接正常" + (elapsedMillis?.let { " · ${it}ms" } ?: "")
    is SettingsConnectionCheck.Unreachable -> check.message
    null -> "手动检测当前实例连通性"
}

private fun connectionBadgeText(check: SettingsConnectionCheck?, elapsedMillis: Long?): String = when (check) {
    SettingsConnectionCheck.Reachable -> "连接正常" + (elapsedMillis?.let { " · ${it}ms" } ?: "")
    is SettingsConnectionCheck.Unreachable -> "连接失败"
    null -> "未检测"
}

private fun formatLastUsed(lastUsedAt: Long?): String {
    if (lastUsedAt == null || lastUsedAt <= 0L) return "暂无记录"
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastUsedAt))
}

private val OSS_LIBRARIES = listOf(
    "Kotlin Coroutines / Serialization" to "Apache License 2.0",
    "Jetpack Compose / AndroidX" to "Apache License 2.0",
    "Retrofit" to "Apache License 2.0",
    "OkHttp" to "Apache License 2.0",
    "Room" to "Apache License 2.0",
    "Dagger Hilt" to "Apache License 2.0",
    "Coil" to "Apache License 2.0",
)
