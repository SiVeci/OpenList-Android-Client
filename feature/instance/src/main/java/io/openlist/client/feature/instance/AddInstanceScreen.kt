package io.openlist.client.feature.instance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.AppTextField
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.HeroHeader
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
                title = "添加实例",
                subtitle = "连接一个 OpenList 服务",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AppTextField(
                value = uiState.url,
                onValueChange = viewModel::onUrlChange,
                label = "实例地址",
                placeholder = "https://openlist.example.com",
                modifier = Modifier.fillMaxWidth(),
                supportingText = "必须以 http:// 或 https:// 开头，支持部署在子路径",
            )
            if (uiState.url.trim().startsWith("http://", ignoreCase = true)) {
                StatusBadge(text = "明文 HTTP：登录凭据与文件内容将不加密传输，建议仅在可信内网使用", tone = StatusTone.WARNING)
            }
            AppTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = "实例名称（可选）",
                placeholder = "不填写时使用域名",
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextField(
                value = uiState.note,
                onValueChange = viewModel::onNoteChange,
                label = "备注（可选）",
                placeholder = "例如：内网 / 公网",
                modifier = Modifier.fillMaxWidth(),
            )

            when (val test = uiState.testResult) {
                null -> Unit
                ConnectionCheck.Testing -> Unit
                ConnectionCheck.Reachable -> StatusBadge(text = "连接成功", tone = StatusTone.SUCCESS)
                is ConnectionCheck.Unreachable -> ErrorBar(message = test.message)
            }
            uiState.saveError?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton(
                    text = "测试连接",
                    onClick = viewModel::testConnection,
                    enabled = uiState.url.isNotBlank() && !uiState.isTesting,
                )
                PrimaryButton(
                    text = "保存",
                    onClick = viewModel::save,
                    enabled = uiState.url.isNotBlank() && !uiState.isSaving,
                    loading = uiState.isSaving,
                )
            }
        }
    }
}
