package takagi.ru.monica.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
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
    statusDotColor: Color? = null,
    animated: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    if (animated) {
        val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
        val isPressed by resolvedInteractionSource.collectIsPressedAsState()
        val targetCornerRadius = if (selected || isPressed) 12.dp else 20.dp
        val targetContainerColor = when {
            selected -> colorScheme.secondaryContainer
            else -> colorScheme.surfaceContainerLow
        }
        val targetContentColor = when {
            selected -> colorScheme.onSecondaryContainer
            else -> colorScheme.onSurfaceVariant
        }
        val targetBorderColor = when {
            selected -> Color.Transparent
            else -> colorScheme.outlineVariant.copy(alpha = 0.88f)
        }
        val targetBorderWidth = if (selected) 0.dp else 1.dp

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
                MonicaExpressiveFilterChipLabel(
                    label = label,
                    statusDotColor = statusDotColor
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
                MonicaExpressiveFilterChipLabel(
                    label = label,
                    statusDotColor = statusDotColor
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

@Composable
private fun MonicaExpressiveFilterChipLabel(
    label: String,
    statusDotColor: Color?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (statusDotColor != null) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(statusDotColor, CircleShape)
            )
        }
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
