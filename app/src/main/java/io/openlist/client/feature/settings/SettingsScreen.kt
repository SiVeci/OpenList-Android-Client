package io.openlist.client.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import io.openlist.client.BuildConfig
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.AppTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenInstances: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val loggingEnabled by viewModel.loggingEnabled.collectAsState()
    val cacheCleared by viewModel.cacheCleared.collectAsState()
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var showClearedNotice by remember { mutableStateOf(false) }

    LaunchedEffect(cacheCleared) {
        if (cacheCleared) {
            showClearedNotice = true
            viewModel.acknowledgeCacheCleared()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "设置", onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxWidth()) {
            SettingsRow(title = "实例管理", subtitle = "添加、切换、删除实例", onClick = onOpenInstances)
            HorizontalDivider()
            SettingsRow(title = "清理缓存", subtitle = "清除所有实例的本地目录缓存", onClick = { showClearCacheConfirm = true })
            HorizontalDivider()
            SettingsSwitchRow(
                title = "调试日志",
                subtitle = if (loggingEnabled) "已开启，请求日志脱敏后写入 logcat" else "已关闭",
                checked = loggingEnabled,
                onCheckedChange = viewModel::setLoggingEnabled,
            )
            HorizontalDivider()
            SettingsRow(title = "关于", subtitle = "OpenList Client v${BuildConfig.VERSION_NAME}", onClick = {})
            HorizontalDivider()
            SettingsRow(title = "开源许可证", subtitle = null, onClick = { showLicenses = true })
        }
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("清理缓存") },
            text = { Text("将清除所有实例的本地目录缓存，登录状态和下载记录不受影响。") },
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

private val OSS_LIBRARIES = listOf(
    "Kotlin Coroutines / Serialization" to "Apache License 2.0",
    "Jetpack Compose / AndroidX" to "Apache License 2.0",
    "Retrofit" to "Apache License 2.0",
    "OkHttp" to "Apache License 2.0",
    "Room" to "Apache License 2.0",
    "Dagger Hilt" to "Apache License 2.0",
    "Coil" to "Apache License 2.0",
)

@Composable
private fun SettingsRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
