package io.openlist.client.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import io.openlist.client.core.designsystem.Spacing

/** Quick expiry presets (v0.3_EXECUTION_PLAN.md §13: "永久/1天/7天/30天/自定义"). */
enum class ExpiryOption { NEVER, ONE_DAY, SEVEN_DAYS, THIRTY_DAYS, CUSTOM }

/** Shared by every screen that submits a share create/update request, so the
 * preset → epoch-millis mapping lives in exactly one place. */
fun ExpiryOption.toEpochMillis(customMillis: Long?): Long? = when (this) {
    ExpiryOption.NEVER -> null
    ExpiryOption.ONE_DAY -> System.currentTimeMillis() + java.util.concurrent.TimeUnit.DAYS.toMillis(1)
    ExpiryOption.SEVEN_DAYS -> System.currentTimeMillis() + java.util.concurrent.TimeUnit.DAYS.toMillis(7)
    ExpiryOption.THIRTY_DAYS -> System.currentTimeMillis() + java.util.concurrent.TimeUnit.DAYS.toMillis(30)
    ExpiryOption.CUSTOM -> customMillis
}

/** Best-effort reverse mapping for pre-filling an edit form — an arbitrary
 * expiry (e.g. set from the Web UI) that doesn't land exactly on a preset
 * always falls back to CUSTOM. */
fun expiryOptionFor(expiresAt: Long?): Pair<ExpiryOption, Long?> {
    if (expiresAt == null) return ExpiryOption.NEVER to null
    return ExpiryOption.CUSTOM to expiresAt
}

/**
 * Create/edit share form (v0.3_EXECUTION_PLAN.md §7.1/§13/§9 items 5-6 — the
 * edit sheet reuses this same form, pre-filled). [pathSummary] is read-only
 * (the share's target path can't be changed after creation); on failure the
 * caller keeps this sheet open with [errorMessage] set, never clearing input.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShareFormSheet(
    title: String,
    pathSummary: String,
    name: String,
    onNameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    expiryOption: ExpiryOption,
    onExpiryOptionChange: (ExpiryOption) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    customExpiryText: String? = null,
    onPickCustomExpiry: (() -> Unit)? = null,
    submitText: String = "创建分享",
    submitting: Boolean = false,
    errorMessage: String? = null,
    sheetState: SheetState = androidx.compose.material3.rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text("分享路径", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(pathSummary, style = MaterialTheme.typography.bodyMedium)
            }

            AppTextField(
                value = name,
                onValueChange = onNameChange,
                label = "名称（可选）",
                modifier = Modifier.fillMaxWidth(),
            )

            AppTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = "密码（留空表示无密码）",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Text,
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("过期时间", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    expiryChip("永久", ExpiryOption.NEVER, expiryOption, onExpiryOptionChange)
                    expiryChip("1 天", ExpiryOption.ONE_DAY, expiryOption, onExpiryOptionChange)
                    expiryChip("7 天", ExpiryOption.SEVEN_DAYS, expiryOption, onExpiryOptionChange)
                    expiryChip("30 天", ExpiryOption.THIRTY_DAYS, expiryOption, onExpiryOptionChange)
                    expiryChip(customExpiryText ?: "自定义", ExpiryOption.CUSTOM, expiryOption, onExpiryOptionChange)
                }
                if (expiryOption == ExpiryOption.CUSTOM && onPickCustomExpiry != null) {
                    TextButton(onClick = onPickCustomExpiry) { Text("选择日期") }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("启用", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }

            if (errorMessage != null) {
                Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                PrimaryButton(text = submitText, onClick = onSubmit, modifier = Modifier.weight(1f), loading = submitting)
            }
        }
    }
}

@Composable
private fun expiryChip(label: String, option: ExpiryOption, selected: ExpiryOption, onSelect: (ExpiryOption) -> Unit) {
    FilterChip(
        selected = selected == option,
        onClick = { onSelect(option) },
        label = { Text(label) },
    )
}
