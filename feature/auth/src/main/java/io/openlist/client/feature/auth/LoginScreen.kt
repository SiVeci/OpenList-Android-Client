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
import io.openlist.client.core.designsystem.components.AdminTabRow
import io.openlist.client.core.designsystem.components.AppTextField
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.HeroHeader
import io.openlist.client.core.designsystem.components.LoadingState
import io.openlist.client.core.designsystem.components.PrimaryButton
import io.openlist.client.core.designsystem.components.SecondaryButton

private val METHOD_TABS = listOf("账号密码", "LDAP", "Token")

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
            if (uiState.needsOtp) {
                OtpStep(uiState = uiState, viewModel = viewModel)
            } else {
                AdminTabRow(
                    tabs = METHOD_TABS,
                    selectedIndex = uiState.method.ordinal,
                    onTabSelected = { index -> viewModel.selectMethod(LoginMethod.entries[index]) },
                )
                when (uiState.method) {
                    LoginMethod.PASSWORD -> CredentialsForm(uiState, viewModel, label = "密码")
                    LoginMethod.LDAP -> CredentialsForm(uiState, viewModel, label = "LDAP 密码")
                    LoginMethod.TOKEN -> TokenForm(uiState, viewModel)
                }
                if (uiState.method != LoginMethod.TOKEN) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SecondaryButton(text = "游客访问", onClick = viewModel::loginAsGuest, enabled = !uiState.isSubmitting)
                    }
                }
                Text(
                    text = "该实例使用 SSO/WebAuthn 登录？请在浏览器中打开实例的 Web 管理台完成登录后，" +
                        "改用「管理员 Token 登录」继续使用 App。",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onSwitchInstance) { Text("切换实例") }
        }
    }
}

@Composable
private fun CredentialsForm(uiState: LoginUiState, viewModel: LoginViewModel, label: String) {
    AppTextField(
        value = uiState.username,
        onValueChange = viewModel::onUsernameChange,
        label = "用户名",
        modifier = Modifier.fillMaxWidth(),
    )
    AppTextField(
        value = uiState.password,
        onValueChange = viewModel::onPasswordChange,
        label = label,
        modifier = Modifier.fillMaxWidth(),
        keyboardType = KeyboardType.Password,
        visualTransformation = PasswordVisualTransformation(),
    )
    uiState.errorMessage?.let { ErrorBar(message = it) }
    PrimaryButton(
        text = "登录",
        onClick = viewModel::submit,
        modifier = Modifier.fillMaxWidth(),
        enabled = uiState.username.isNotBlank() && uiState.password.isNotBlank() && !uiState.isSubmitting,
        loading = uiState.isSubmitting,
    )
}

@Composable
private fun TokenForm(uiState: LoginUiState, viewModel: LoginViewModel) {
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
        onClick = viewModel::submit,
        modifier = Modifier.fillMaxWidth(),
        enabled = uiState.tokenInput.isNotBlank() && !uiState.isSubmitting,
        loading = uiState.isSubmitting,
    )
}

/** Inline OTP second step (DEC-601): same page, same ViewModel-held first-step
 * credentials, no dialog/no separate route — avoids losing state on process
 * recreation/rotation. */
@Composable
private fun OtpStep(uiState: LoginUiState, viewModel: LoginViewModel) {
    Text(
        text = "该账号已开启两步验证，请输入验证码",
        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
    )
    AppTextField(
        value = uiState.otpCode,
        onValueChange = viewModel::onOtpCodeChange,
        label = "两步验证码",
        modifier = Modifier.fillMaxWidth(),
        keyboardType = KeyboardType.Number,
    )
    uiState.errorMessage?.let { ErrorBar(message = it) }
    PrimaryButton(
        text = "验证并登录",
        onClick = viewModel::submit,
        modifier = Modifier.fillMaxWidth(),
        enabled = uiState.otpCode.isNotBlank() && !uiState.isSubmitting,
        loading = uiState.isSubmitting,
    )
    TextButton(onClick = viewModel::cancelOtp) { Text("返回") }
}
