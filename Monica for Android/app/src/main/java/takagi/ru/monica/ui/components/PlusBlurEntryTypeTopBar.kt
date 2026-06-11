package takagi.ru.monica.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.ui.effects.blur.MonicaPlusBlurIslandSurface
import takagi.ru.monica.ui.icons.MonicaIcons

@Composable
fun PlusBlurEntryTypeTopBar(
    settings: AppSettings,
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    currentEntryType: EntryTypeChipOption,
    modifier: Modifier = Modifier,
    entryTypeEnabled: Boolean = true,
    isFavorite: Boolean,
    onNavigateBack: () -> Unit,
    onFavoriteChange: (Boolean) -> Unit,
    onEntryTypeSelect: (EntryTypeChipOption) -> Unit
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val islandSize = 40.dp
    val topBarHeight = statusBarTop + 52.dp
    var entryTypeMenuExpanded by remember { mutableStateOf(false) }

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
                PlusBlurIslandIconButton(
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
                    PlusBlurEntryTypeButton(
                        current = currentEntryType,
                        expanded = entryTypeMenuExpanded,
                        enabled = entryTypeEnabled,
                        onClick = { entryTypeMenuExpanded = true }
                    )
                }

                DropdownMenu(
                    expanded = entryTypeMenuExpanded,
                    onDismissRequest = { entryTypeMenuExpanded = false },
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
                            EntryTypeChipOption.entries.forEach { option ->
                                val label = plusBlurEntryTypeLabel(option)
                                val isCurrent = option == currentEntryType
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
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
                                            imageVector = plusBlurEntryTypeIcon(option),
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
                                        entryTypeMenuExpanded = false
                                        if (!isCurrent) {
                                            onEntryTypeSelect(option)
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
                PlusBlurIslandIconButton(
                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(R.string.favorite),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    onClick = { onFavoriteChange(!isFavorite) }
                )
            }
        }
    }
}

@Composable
private fun PlusBlurEntryTypeButton(
    current: EntryTypeChipOption,
    expanded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val chipContentDescription = stringResource(R.string.entry_type_chip_content_description)
    val interactionSource = remember { MutableInteractionSource() }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "PlusBlurEntryTypeChipArrow"
    )

    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 14.dp)
            .semantics { contentDescription = chipContentDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = plusBlurEntryTypeIcon(current),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = plusBlurEntryTypeLabel(current),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
private fun plusBlurEntryTypeLabel(option: EntryTypeChipOption): String = stringResource(
    when (option) {
        EntryTypeChipOption.PASSWORD -> R.string.entry_type_password
        EntryTypeChipOption.WIFI -> R.string.entry_type_wifi
        EntryTypeChipOption.SSH_KEY -> R.string.entry_type_ssh_key
        EntryTypeChipOption.BARCODE -> R.string.entry_type_barcode
    }
)

private fun plusBlurEntryTypeIcon(option: EntryTypeChipOption): ImageVector = when (option) {
    EntryTypeChipOption.PASSWORD -> Icons.Default.Password
    EntryTypeChipOption.WIFI -> Icons.Default.Wifi
    EntryTypeChipOption.SSH_KEY -> Icons.Default.Key
    EntryTypeChipOption.BARCODE -> Icons.Default.QrCode2
}

@Composable
private fun PlusBlurIslandIconButton(
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
