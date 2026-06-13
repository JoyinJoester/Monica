package takagi.ru.monica.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.ui.effects.blur.MonicaPlusBlurIslandSurface
import takagi.ru.monica.ui.icons.MonicaIcons
import takagi.ru.monica.ui.screens.CardWalletTab

@Composable
fun CardWalletAddTypeChip(
    current: CardWalletTab,
    onSelect: (CardWalletTab) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    drawContainer: Boolean = true,
    contentColorOverride: Color? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val chipContentDescription = stringResource(R.string.nav_card_wallet)
    val interactionSource = remember { MutableInteractionSource() }
    val options = remember {
        listOf(
            CardWalletTab.BANK_CARDS,
            CardWalletTab.DOCUMENTS,
            CardWalletTab.BILLING_ADDRESSES
        )
    }

    val targetContent = contentColorOverride ?: if (drawContainer) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "CardWalletAddTypeChipArrow"
    )
    val containerColor = if (drawContainer) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = { expanded = true }
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = chipContentDescription }
    ) {
        Icon(
            imageVector = cardWalletAddTypeIcon(current),
            contentDescription = null,
            tint = targetContent,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = cardWalletAddTypeLabel(current),
            color = targetContent,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            tint = targetContent,
            modifier = Modifier
                .size(18.dp)
                .rotate(arrowRotation)
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        options.forEach { option ->
            val isCurrent = option == current
            DropdownMenuItem(
                text = {
                    Text(
                        text = cardWalletAddTypeLabel(option),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = cardWalletAddTypeIcon(option),
                        contentDescription = null,
                        tint = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                trailingIcon = if (isCurrent) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    null
                },
                onClick = {
                    expanded = false
                    if (!isCurrent) onSelect(option)
                }
            )
        }
    }
}

@Composable
fun PlusBlurCardWalletAddTopBar(
    settings: AppSettings,
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    currentType: CardWalletTab,
    isFavorite: Boolean,
    onNavigateBack: () -> Unit,
    onFavoriteClick: () -> Unit,
    onTypeSelect: (CardWalletTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val islandSize = 40.dp
    val topBarHeight = statusBarTop + 52.dp
    var typeMenuExpanded by remember { mutableStateOf(false) }
    val options = remember {
        listOf(
            CardWalletTab.BANK_CARDS,
            CardWalletTab.DOCUMENTS,
            CardWalletTab.BILLING_ADDRESSES
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topBarHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarTop)
                .height(52.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MonicaPlusBlurIslandSurface(
                settings = settings,
                enabledForThisSurface = true,
                modifier = Modifier.size(islandSize),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                fillContent = true,
                hazeState = hazeState,
                hazeStyle = hazeStyle
            ) {
                PlusBlurCardWalletIconButton(
                    icon = MonicaIcons.Navigation.back,
                    contentDescription = stringResource(R.string.back),
                    onClick = onNavigateBack
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Box {
                MonicaPlusBlurIslandSurface(
                    settings = settings,
                    enabledForThisSurface = true,
                    modifier = Modifier.height(islandSize),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(0.dp),
                    hazeState = hazeState,
                    hazeStyle = hazeStyle
                ) {
                    PlusBlurCardWalletTypeButton(
                        current = currentType,
                        expanded = typeMenuExpanded,
                        onClick = { typeMenuExpanded = true }
                    )
                }

                DropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = Color.Transparent,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp
                ) {
                    MonicaPlusBlurIslandSurface(
                        settings = settings,
                        enabledForThisSurface = true,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        hazeState = hazeState,
                        hazeStyle = hazeStyle
                    ) {
                        Column {
                            options.forEach { option ->
                                val isCurrent = option == currentType
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = cardWalletAddTypeLabel(option),
                                            color = if (isCurrent) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = cardWalletAddTypeIcon(option),
                                            contentDescription = null,
                                            tint = if (isCurrent) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    },
                                    trailingIcon = if (isCurrent) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        typeMenuExpanded = false
                                        if (!isCurrent) {
                                            onTypeSelect(option)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            MonicaPlusBlurIslandSurface(
                settings = settings,
                enabledForThisSurface = true,
                modifier = Modifier.size(islandSize),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                fillContent = true,
                hazeState = hazeState,
                hazeStyle = hazeStyle
            ) {
                PlusBlurCardWalletIconButton(
                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(R.string.favorite),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    onClick = onFavoriteClick
                )
            }
        }
    }
}

@Composable
private fun PlusBlurCardWalletTypeButton(
    current: CardWalletTab,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val chipContentDescription = stringResource(R.string.nav_card_wallet)
    val interactionSource = remember { MutableInteractionSource() }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "PlusBlurCardWalletTypeChipArrow"
    )

    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 14.dp)
            .semantics { contentDescription = chipContentDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = cardWalletAddTypeIcon(current),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = cardWalletAddTypeLabel(current),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .rotate(arrowRotation)
        )
    }
}

@Composable
private fun cardWalletAddTypeLabel(type: CardWalletTab): String = stringResource(
    when (type) {
        CardWalletTab.DOCUMENTS -> R.string.item_type_document
        CardWalletTab.BILLING_ADDRESSES -> R.string.billing_address
        else -> R.string.item_type_bank_card
    }
)

private fun cardWalletAddTypeIcon(type: CardWalletTab): ImageVector = when (type) {
    CardWalletTab.DOCUMENTS -> Icons.Default.Badge
    CardWalletTab.BILLING_ADDRESSES -> Icons.Default.Home
    else -> Icons.Default.CreditCard
}

@Composable
private fun PlusBlurCardWalletIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = LocalContentColor.current
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
