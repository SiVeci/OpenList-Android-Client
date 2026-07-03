package io.openlist.client.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.Spacing

/**
 * Confirmation dialog for destructive/irreversible actions (v0.2_EXECUTION_PLAN.md
 * §13.5). [danger] gives the confirm button the error-tone styling required for
 * delete-class actions; cancel always stays in the default style so the two are
 * never visually confusable. Dismissing (including tapping outside) never calls
 * [onConfirm] — only the confirm button does.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "确定",
    dismissText: String = "取消",
    danger: Boolean = false,
    loading: Boolean = false,
    errorMessage: String? = null,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (errorMessage != null) {
                    Text(
                        errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !loading,
                colors = if (danger) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(confirmText)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text(dismissText)
            }
        },
    )
}

/**
 * Single-field input dialog shared by "new directory" and "rename"
 * (v0.2_EXECUTION_PLAN.md §13.4). Confirm stays enabled/disabled logic is the
 * caller's call (e.g. blank-name check) via [confirmEnabled]; failures keep the
 * dialog open with [errorMessage] shown so the user's input isn't lost.
 */
@Composable
fun TextInputDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    confirmText: String = "确定",
    dismissText: String = "取消",
    confirmEnabled: Boolean = value.isNotBlank(),
    errorMessage: String? = null,
    loading: Boolean = false,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(title) },
        text = {
            AppTextField(
                value = value,
                onValueChange = onValueChange,
                label = label,
                isError = errorMessage != null,
                supportingText = errorMessage,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled && !loading) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(confirmText)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text(dismissText)
            }
        },
    )
}
