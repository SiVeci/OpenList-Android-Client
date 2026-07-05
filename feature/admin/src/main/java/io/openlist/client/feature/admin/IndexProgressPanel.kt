package io.openlist.client.feature.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.StatusBadge
import io.openlist.client.core.designsystem.components.StatusTone
import io.openlist.client.core.model.AdminIndexProgress

/**
 * Index progress display panel (v0.5_EXECUTION_PLAN.md §11 S6-T2, S1's
 * component-mapping conclusion: business-coupled, new, lives in
 * `:feature:admin` rather than `core:designsystem`). Shows [AdminIndexProgress
 * .objCount]/the client-derived running/not-running badge/[lastDoneTime]
 * (formatted, or "从未" if null)/[error] (if present). Purely presentational --
 * no repository/ViewModel dependency, matching every other `*Row`/`*Card`
 * composable in this module.
 */
@Composable
fun IndexProgressPanel(progress: AdminIndexProgress, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("索引状态", style = MaterialTheme.typography.titleMedium)
                StatusBadge(
                    text = if (progress.isRunning) "运行中" else "已停止",
                    tone = if (progress.isRunning) StatusTone.RUNNING else StatusTone.NEUTRAL,
                )
            }
            IndexProgressRow(label = "已索引对象数", value = progress.objCount.toString())
            IndexProgressRow(label = "上次完成时间", value = progress.lastDoneTime?.let { formatDate(it) } ?: "从未")
            val error = progress.error
            if (error != null) {
                Spacer(Modifier.size(Spacing.xxs))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun IndexProgressRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Same `yyyy-MM-dd HH:mm` local formatter [io.openlist.client.feature.share
 * .ShareListScreen.formatDate]/[io.openlist.client.feature.files] use
 * elsewhere in the app -- no shared date-formatting util exists in
 * `core:designsystem`/`core:common` (each feature module currently keeps its
 * own private copy), so this follows that same existing precedent rather than
 * introducing a new shared util as an unrelated drive-by change. */
private fun formatDate(epochMillis: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(epochMillis))
