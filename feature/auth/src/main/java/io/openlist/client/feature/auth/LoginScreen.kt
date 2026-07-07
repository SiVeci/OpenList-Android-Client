package io.openlist.client.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.AppTextField
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.GroupCard
import io.openlist.client.core.designsystem.components.HeroHeader
import io.openlist.client.core.designsystem.components.LoadingState
import io.openlist.client.core.designsystem.components.OtpCodeInput
import io.openlist.client.core.designsystem.components.PrimaryButton
import io.openlist.client.core.designsystem.components.SecondaryButton
import io.openlist.client.core.designsystem.components.SegmentedSelector
import io.openlist.client.core.designsystem.components.StatusBadge
import io.openlist.client.core.designsystem.components.StatusTone

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
                title = "登录实例",
                subtitle = uiState.instanceName.ifBlank { uiState.instanceBaseUrl },
                onBack = onSwitchInstance,
            )
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
            SetupStepper(currentStep = 1)

            InstanceSummary(
                name = uiState.instanceName,
                baseUrl = uiState.instanceBaseUrl,
            )

            if (uiState.needsOtp) {
                OtpStep(uiState = uiState, viewModel = viewModel)
            } else {
                GroupCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Text(
                            text = "选择登录方式",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        SegmentedSelector(
                            options = METHOD_TABS,
                            selectedIndex = uiState.method.ordinal,
                            onSelectedIndexChange = { index -> viewModel.selectMethod(LoginMethod.entries[index]) },
                            enabled = !uiState.isSubmitting,
                        )
                        when (uiState.method) {
                            LoginMethod.PASSWORD -> CredentialsForm(uiState, viewModel, label = "密码")
                            LoginMethod.LDAP -> CredentialsForm(uiState, viewModel, label = "LDAP 密码")
                            LoginMethod.TOKEN -> TokenForm(uiState, viewModel)
                        }
                    }
                }

                if (uiState.method != LoginMethod.TOKEN) {
                    SecondaryButton(
                        text = "游客访问",
                        onClick = viewModel::loginAsGuest,
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SsoHint()
            }

            TextButton(
                onClick = onSwitchInstance,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("切换实例")
            }
        }
    }
}

@Composable
private fun CredentialsForm(uiState: LoginUiState, viewModel: LoginViewModel, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        IconTextField(
            value = uiState.username,
            onValueChange = viewModel::onUsernameChange,
            label = "用户名",
            placeholder = "请输入用户名",
            leadingIcon = Icons.Outlined.Person,
        )
        IconTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label = label,
            placeholder = "请输入$label",
            leadingIcon = Icons.Outlined.Lock,
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
}

@Composable
private fun TokenForm(uiState: LoginUiState, viewModel: LoginViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        IconTextField(
            value = uiState.tokenInput,
            onValueChange = viewModel::onTokenInputChange,
            label = "管理员 Token",
            placeholder = "粘贴 API Token",
            leadingIcon = Icons.Outlined.Security,
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
}

/** Inline OTP second step (DEC-601): same page, same ViewModel-held first-step
 * credentials, no dialog/no separate route. */
@Composable
private fun OtpStep(uiState: LoginUiState, viewModel: LoginViewModel) {
    GroupCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "两步验证",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                StatusBadge(text = "OTP", tone = StatusTone.WARNING)
            }
            Text(
                text = "该账号已开启两步验证，请输入验证码完成登录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OtpCodeInput(
                value = uiState.otpCode,
                onValueChange = viewModel::onOtpCodeChange,
                enabled = !uiState.isSubmitting,
            )
            uiState.errorMessage?.let { ErrorBar(message = it) }
            PrimaryButton(
                text = "验证并登录",
                onClick = viewModel::submit,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.otpCode.isNotBlank() && !uiState.isSubmitting,
                loading = uiState.isSubmitting,
            )
            TextButton(
                onClick = viewModel::cancelOtp,
                enabled = !uiState.isSubmitting,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("返回登录")
            }
        }
    }
}

@Composable
private fun InstanceSummary(name: String, baseUrl: String) {
    GroupCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.ifBlank { baseUrl }.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name.ifBlank { "OpenList 实例" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusBadge(text = "已连接", tone = StatusTone.SUCCESS)
        }
    }
}

@Composable
private fun SsoHint() {
    GroupCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "如果实例使用 SSO 或 WebAuthn，请先在浏览器中完成登录，再使用管理员 Token 继续使用 App。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    label: String,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    supportingText: String? = null,
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        keyboardType = keyboardType,
        visualTransformation = visualTransformation,
        supportingText = supportingText,
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = modifier.fillMaxWidth(),
    )
}
