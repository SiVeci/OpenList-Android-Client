package io.openlist.client.core.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Consistent copy for write-operation results (v0.2_EXECUTION_PLAN.md §11.2):
 * all-succeeded / all-failed / partial, the last with a "查看" action to
 * inspect BatchOperationResult's failedItems (wired by the caller, since this
 * module has no dependency on core:model).
 */
data class OperationResultMessage(
    val text: String,
    val isError: Boolean,
    val actionLabel: String? = null,
)

fun buildOperationResultMessage(
    total: Int,
    successCount: Int,
    failedCount: Int,
    viewFailuresActionLabel: String = "查看",
): OperationResultMessage = when {
    failedCount == 0 -> OperationResultMessage(text = "操作成功", isError = false)
    successCount == 0 -> OperationResultMessage(text = "操作失败，共 $total 项", isError = true)
    else -> OperationResultMessage(
        text = "成功 $successCount 项，失败 $failedCount 项",
        isError = true,
        actionLabel = viewFailuresActionLabel,
    )
}

/** Themed Snackbar content for [OperationResultMessage] — plug into a
 * `SnackbarHost { data -> OperationResultSnackbar(...) }` at the call site. */
@Composable
fun OperationResultSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Snackbar(
        modifier = modifier,
        containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.inverseSurface,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.inverseOnSurface,
        action = if (actionLabel != null && onAction != null) {
            { TextButton(onClick = onAction) { Text(actionLabel) } }
        } else {
            null
        },
    ) {
        Text(message)
    }
}
