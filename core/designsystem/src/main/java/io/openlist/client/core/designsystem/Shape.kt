package io.openlist.client.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// rounded.* from DESIGN.md: buttons/inputs are rectangular (8dp), cards are 12dp,
// pill shape is reserved for tabs/badges/chips only — never regular buttons.
val OpenListShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

val PillShape = RoundedCornerShape(50)
