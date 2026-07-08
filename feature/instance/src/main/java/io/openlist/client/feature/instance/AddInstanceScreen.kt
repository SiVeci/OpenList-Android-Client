package io.openlist.client.feature.instance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.AppTextField
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.GroupCard
import io.openlist.client.core.designsystem.components.HeroHeader
import io.openlist.client.core.designsystem.components.OpenListBrandLockup
import io.openlist.client.core.designsystem.components.OpenListLogoSurface
import io.openlist.client.core.designsystem.components.PrimaryButton
import io.openlist.client.core.designsystem.components.SecondaryButton
import io.openlist.client.core.designsystem.components.StatusBadge
import io.openlist.client.core.designsystem.components.StatusTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInstanceScreen(
    onBack: () -> Unit,
    onSaved: (instanceId: String) -> Unit,
    viewModel: AddInstanceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.savedInstanceId) {
        uiState.savedInstanceId?.let(onSaved)
    }

    Scaffold(
        topBar = {
            HeroHeader(
                title = "连接实例",
                subtitle = "添加你的 OpenList 服务地址",
                onBack = onBack,
            ) {
                OpenListBrandLockup(
                    surface = OpenListLogoSurface.Dark,
                    title = "连接实例",
                    subtitle = "添加你的 OpenList 服务地址",
                    markSize = 56.dp,
                    titleStyle = MaterialTheme.typography.headlineMedium,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SetupStepper(currentStep = 0)

            GroupCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        text = "实例地址",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconTextField(
                        value = uiState.url,
                        onValueChange = viewModel::onUrlChange,
                        onClear = { viewModel.onUrlChange("") },
                        label = "实例地址",
                        placeholder = "https://nas.example.com",
                        leadingIcon = Icons.Outlined.Link,
                        keyboardType = KeyboardType.Uri,
                        supportingText = "仅支持 HTTP/HTTPS，建议使用 HTTPS 以保证安全",
                    )
                    IconTextField(
                        value = uiState.name,
                        onValueChange = viewModel::onNameChange,
                        onClear = { viewModel.onNameChange("") },
                        label = "实例名称（可选）",
                        placeholder = "NAS 主实例",
                        leadingIcon = Icons.Outlined.Business,
                    )
                    IconTextField(
                        value = uiState.note,
                        onValueChange = viewModel::onNoteChange,
                        onClear = { viewModel.onNoteChange("") },
                        label = "备注（可选）",
                        placeholder = "例如：内网 / 公网",
                        leadingIcon = Icons.AutoMirrored.Outlined.Notes,
                    )

                    SecurityHint(isHttp = uiState.url.trim().startsWith("http://", ignoreCase = true))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SecondaryButton(
                            text = if (uiState.isTesting) "测试中" else "测试连接",
                            onClick = viewModel::testConnection,
                            enabled = uiState.url.isNotBlank() && !uiState.isTesting,
                            modifier = Modifier.weight(1f),
                        )
                        ConnectionResultBadge(
                            check = uiState.testResult,
                            elapsedMillis = uiState.testElapsedMillis,
                            isTesting = uiState.isTesting,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (uiState.testResult is ConnectionCheck.Unreachable) {
                        ErrorBar(message = (uiState.testResult as ConnectionCheck.Unreachable).message)
                    }
                    uiState.saveError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            PrimaryButton(
                text = "保存并继续登录",
                onClick = viewModel::save,
                enabled = uiState.url.isNotBlank() && !uiState.isSaving,
                loading = uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SetupStepper(currentStep: Int) {
    val steps = listOf("实例", "登录", "完成")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        steps.forEachIndexed { index, label ->
            StepIndicator(
                index = index,
                label = label,
                selected = index == currentStep,
            )
            if (index != steps.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(
    index: Int,
    label: String,
    selected: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (index + 1).toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IconTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    supportingText: String? = null,
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        keyboardType = keyboardType,
        supportingText = supportingText,
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "清空",
                    )
                }
            }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun SecurityHint(isHttp: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = if (isHttp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = if (isHttp) {
                "当前为明文 HTTP，登录凭据与文件内容不会加密传输"
            } else {
                "仅支持 HTTP/HTTPS，建议使用 HTTPS 以保证安全"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (isHttp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectionResultBadge(
    check: ConnectionCheck?,
    elapsedMillis: Long?,
    isTesting: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(40.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        when (check) {
            ConnectionCheck.Reachable -> StatusBadge(
                text = "可访问" + (elapsedMillis?.let { " · 响应 ${it}ms" } ?: ""),
                tone = StatusTone.SUCCESS,
            )
            is ConnectionCheck.Unreachable -> StatusBadge(
                text = "连接失败",
                tone = StatusTone.ERROR,
            )
            ConnectionCheck.Testing -> StatusBadge(text = "测试中", tone = StatusTone.NEUTRAL)
            null -> if (isTesting) {
                StatusBadge(text = "测试中", tone = StatusTone.NEUTRAL)
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "等待测试",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
