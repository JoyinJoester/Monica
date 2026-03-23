package takagi.ru.monica.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun MonicaExpressiveFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    leadingIcon: ImageVector? = null,
    selectedLeadingIcon: ImageVector? = leadingIcon,
    animated: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    if (animated) {
        val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
        val isPressed by resolvedInteractionSource.collectIsPressedAsState()
        val targetCornerRadius = when {
            isPressed -> 8.dp
            selected -> 12.dp
            else -> 20.dp
        }
        val targetContainerColor = when {
            isPressed && selected -> colorScheme.secondaryContainer
            selected -> colorScheme.secondaryContainer
            isPressed -> colorScheme.surfaceContainerHigh
            else -> colorScheme.surfaceContainerLow
        }
        val targetContentColor = when {
            selected -> colorScheme.onSecondaryContainer
            isPressed -> colorScheme.onSurface
            else -> colorScheme.onSurfaceVariant
        }
        val targetBorderColor = when {
            isPressed && selected -> colorScheme.primary.copy(alpha = 0.18f)
            selected -> Color.Transparent
            isPressed -> colorScheme.primary.copy(alpha = 0.20f)
            else -> colorScheme.outlineVariant.copy(alpha = 0.88f)
        }
        val targetBorderWidth = when {
            isPressed && selected -> 1.dp
            selected -> 0.dp
            isPressed -> 1.2.dp
            else -> 1.dp
        }

        val cornerRadius by animateDpAsState(
            targetValue = targetCornerRadius,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "monicaExpressiveFilterChipCornerRadius"
        )
        val containerColor by animateColorAsState(
            targetValue = targetContainerColor,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "monicaExpressiveFilterChipContainer"
        )
        val contentColor by animateColorAsState(
            targetValue = targetContentColor,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "monicaExpressiveFilterChipContent"
        )
        val borderColor by animateColorAsState(
            targetValue = targetBorderColor,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "monicaExpressiveFilterChipBorder"
        )
        val borderWidth by animateDpAsState(
            targetValue = targetBorderWidth,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "monicaExpressiveFilterChipBorderWidth"
        )

        FilterChip(
            selected = selected,
            onClick = onClick,
            shape = RoundedCornerShape(cornerRadius),
            label = {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = (selectedLeadingIcon ?: leadingIcon)?.let { icon ->
                {
                    Icon(
                        imageVector = if (selected) icon else (leadingIcon ?: icon),
                        contentDescription = null
                    )
                }
            },
            interactionSource = resolvedInteractionSource,
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = borderColor,
                selectedBorderColor = borderColor,
                borderWidth = borderWidth,
                selectedBorderWidth = borderWidth
            ),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = containerColor,
                labelColor = contentColor,
                iconColor = contentColor,
                selectedContainerColor = containerColor,
                selectedLabelColor = contentColor,
                selectedLeadingIconColor = contentColor
            ),
            modifier = modifier
        )
    } else {
        val cornerRadius = if (selected) 12.dp else 20.dp
        val containerColor = if (selected) {
            colorScheme.secondaryContainer
        } else {
            colorScheme.surfaceContainerLow
        }
        val contentColor = if (selected) {
            colorScheme.onSecondaryContainer
        } else {
            colorScheme.onSurfaceVariant
        }
        val borderColor = if (selected) {
            Color.Transparent
        } else {
            colorScheme.outlineVariant.copy(alpha = 0.88f)
        }
        val borderWidth = if (selected) 0.dp else 1.dp

        FilterChip(
            selected = selected,
            onClick = onClick,
            shape = RoundedCornerShape(cornerRadius),
            label = {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = (selectedLeadingIcon ?: leadingIcon)?.let { icon ->
                {
                    Icon(
                        imageVector = if (selected) icon else (leadingIcon ?: icon),
                        contentDescription = null
                    )
                }
            },
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = borderColor,
                selectedBorderColor = borderColor,
                borderWidth = borderWidth,
                selectedBorderWidth = borderWidth
            ),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = containerColor,
                labelColor = contentColor,
                iconColor = contentColor,
                selectedContainerColor = containerColor,
                selectedLabelColor = contentColor,
                selectedLeadingIconColor = contentColor
            ),
            modifier = modifier
        )
    }
}
