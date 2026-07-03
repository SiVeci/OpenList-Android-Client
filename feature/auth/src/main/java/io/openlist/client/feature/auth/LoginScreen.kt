package io.openlist.client.feature.auth

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.AppTextField
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.HeroHeader
import io.openlist.client.core.designsystem.components.LoadingState
import io.openlist.client.core.designsystem.components.PrimaryButton
import io.openlist.client.core.designsystem.components.SecondaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onSwitchInstance: () -> Unit,
    onAuthenticated: (instanceId: String) -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.autoProceedInstanceId) {
        uiState.autoProceedInstanceId?.let(onAuthenticated)
    }

    if (uiState.checkingSession) {
        LoadingState(modifier = Modifier.fillMaxSize())
        return
    }

    Scaffold(
        topBar = {
            HeroHeader(
                title = uiState.instanceName,
                subtitle = uiState.instanceBaseUrl,
                onBack = onSwitchInstance,
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
            if (uiState.isTokenMode) {
                AppTextField(
                    value = uiState.tokenInput,
                    onValueChange = viewModel::onTokenInputChange,
                    label = "管理员 Token",
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = "在实例设置中重置得到的 API Token",
                )
                uiState.errorMessage?.let { ErrorBar(message = it) }
                PrimaryButton(
                    text = "登录",
                    onClick = viewModel::loginWithToken,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.tokenInput.isNotBlank() && !uiState.isSubmitting,
                    loading = uiState.isSubmitting,
                )
                TextButton(onClick = viewModel::toggleTokenMode) { Text("改用账号密码登录") }
            } else {
                AppTextField(
                    value = uiState.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = "用户名",
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = "密码",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation(),
                )
                uiState.errorMessage?.let { ErrorBar(message = it) }
                PrimaryButton(
                    text = "登录",
                    onClick = viewModel::loginWithPassword,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.username.isNotBlank() && uiState.password.isNotBlank() && !uiState.isSubmitting,
                    loading = uiState.isSubmitting,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SecondaryButton(text = "游客访问", onClick = viewModel::loginAsGuest, enabled = !uiState.isSubmitting)
                    SecondaryButton(text = "使用 Token 登录", onClick = viewModel::toggleTokenMode, enabled = !uiState.isSubmitting)
                }
            }
            TextButton(onClick = onSwitchInstance) { Text("切换实例") }
        }
    }
}
