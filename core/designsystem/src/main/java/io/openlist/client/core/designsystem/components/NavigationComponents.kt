package io.openlist.client.core.designsystem.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class AppNavigationItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val enabled: Boolean = true,
    val badgeCount: Int = 0,
    val onClick: () -> Unit,
)

@Composable
fun AppNavigationBar(
    items: List<AppNavigationItem>,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.selected,
                enabled = item.enabled,
                onClick = item.onClick,
                icon = {
                    BadgedBox(
                        badge = {
                            if (item.badgeCount > 0) {
                                Badge {
                                    Text(item.badgeCount.coerceAtMost(99).toString())
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}
