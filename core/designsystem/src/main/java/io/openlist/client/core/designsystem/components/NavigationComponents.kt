package io.openlist.client.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

data class AppNavigationItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val enabled: Boolean = true,
    val badgeCount: Int = 0,
    val onClick: () -> Unit,
)

/**
 * Compact bottom navigation: 52dp of content plus the gesture-bar inset,
 * replacing the 80dp Material3 NavigationBar. Selection is shown by tint
 * instead of the pill indicator (the indicator is what forces the 80dp
 * height); each item's touch target is the full cell, well above 48dp.
 * The 8dp top padding leaves headroom for the badge above the 22dp icon.
 */
@Composable
fun AppNavigationBar(
    items: List<AppNavigationItem>,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(52.dp)
                .selectableGroup(),
        ) {
            items.forEach { item ->
                val tint = when {
                    !item.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    item.selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = item.selected,
                            enabled = item.enabled,
                            role = Role.Tab,
                            onClick = item.onClick,
                        )
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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
                            modifier = Modifier.size(22.dp),
                            tint = tint,
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                    )
                }
            }
        }
    }
}
