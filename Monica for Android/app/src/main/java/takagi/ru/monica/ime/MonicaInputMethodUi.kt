package takagi.ru.monica.ime

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import takagi.ru.monica.R
import takagi.ru.monica.autofill_ng.ui.rememberAppIcon
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.ui.components.MonicaExpressiveFilterChip
import takagi.ru.monica.ui.theme.MonicaTheme

internal data class MonicaImePasswordEntry(
    val id: Long,
    val title: String,
    val username: String,
    val website: String,
    val packageName: String,
    val password: String,
    val isFavorite: Boolean,
    val sourceLabel: String,
    val keepassDatabaseId: Long? = null,
    val bitwardenVaultId: Long? = null
)

internal data class MonicaImeUiState(
    val unlocked: Boolean = false,
    val activePackageName: String = "",
    val activePanel: MonicaImePanel = MonicaImePanel.KEYBOARD,
    val query: String = "",
    val entries: List<MonicaImePasswordEntry> = emptyList(),
    val databaseOptions: List<MonicaImeDatabaseOption> = emptyList(),
    val selectedDatabaseScope: MonicaImeDatabaseScope = MonicaImeDatabaseScope.All,
    val errorMessage: String? = null,
    val keyboardMode: MonicaKeyboardMode = MonicaKeyboardMode.LETTERS,
    val isUppercase: Boolean = false,
    val autoLockMinutes: Int = 5,
    val isAutofillPanelVisible: Boolean = false
)

internal enum class MonicaKeyboardMode {
    LETTERS,
    NUMBERS,
    SYMBOLS
}

internal enum class MonicaImePanel {
    KEYBOARD,
    PASSWORDS,
    AUTHENTICATORS,
    DOCUMENTS
}

internal sealed interface MonicaImeDatabaseScope {
    data object All : MonicaImeDatabaseScope
    data object Local : MonicaImeDatabaseScope
    data class KeePass(val databaseId: Long) : MonicaImeDatabaseScope
    data class Bitwarden(val vaultId: Long) : MonicaImeDatabaseScope
}

internal data class MonicaImeDatabaseOption(
    val scope: MonicaImeDatabaseScope,
    val label: String
)

private data class MonicaKeySpec(
    val label: String = "",
    val weight: Float = 1f,
    val onClickValue: String? = null,
    val icon: (@Composable (() -> Unit))? = null,
    val onClick: (() -> Unit)? = null,
    val active: Boolean = false,
    val cornerRadius: Int = 12,
    val style: MonicaKeyStyle = MonicaKeyStyle.STANDARD
)

private enum class MonicaKeyStyle {
    STANDARD,
    ACCENT,
    PRIMARY
}

private enum class MonicaToolbarSelection {
    MONICA,
    PASSWORDS,
    AUTHENTICATORS,
    DOCUMENTS
}

private val MonicaImeContentAreaHeight = 240.dp

@Composable
internal fun MonicaImeContent(
    settings: AppSettings,
    uiState: MonicaImeUiState,
    onQueryChanged: (String) -> Unit,
    onDatabaseScopeSelected: (MonicaImeDatabaseScope) -> Unit,
    onInsertPassword: (MonicaImePasswordEntry) -> Unit,
    onInsertUsername: (MonicaImePasswordEntry) -> Unit,
    onKeyPressed: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onSpace: () -> Unit,
    onShiftToggle: () -> Unit,
    onKeyboardModeChange: (MonicaKeyboardMode) -> Unit,
    onOpenUnlockApp: () -> Unit,
    onPanelSelected: (MonicaImePanel) -> Unit,
    onSwitchInputMethod: () -> Unit,
    onDismiss: () -> Unit
) {
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val activePanelRequiresUnlock = uiState.activePanel != MonicaImePanel.KEYBOARD
    val showPanelContent = activePanelRequiresUnlock && uiState.unlocked
    val showUnlockPanel = activePanelRequiresUnlock && !uiState.unlocked

    MonicaTheme(
        darkTheme = darkTheme,
        colorScheme = settings.colorScheme,
        customPrimaryColor = settings.customPrimaryColor,
        customSecondaryColor = settings.customSecondaryColor,
        customTertiaryColor = settings.customTertiaryColor,
        customNeutralColor = settings.customNeutralColor,
        customNeutralVariantColor = settings.customNeutralVariantColor
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    MonicaImeToolbar(
                        uiState = uiState,
                        onPanelSelected = onPanelSelected,
                        onDismiss = onDismiss
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(MonicaImeContentAreaHeight)
                    ) {
                        if (showPanelContent) {
                            when (uiState.activePanel) {
                                MonicaImePanel.PASSWORDS -> {
                                    UnlockedVaultPane(
                                        modifier = Modifier.fillMaxSize(),
                                        uiState = uiState,
                                        onQueryChanged = onQueryChanged,
                                        onDatabaseScopeSelected = onDatabaseScopeSelected,
                                        onInsertPassword = onInsertPassword,
                                        onInsertUsername = onInsertUsername
                                    )
                                }
                                MonicaImePanel.AUTHENTICATORS -> {
                                    ImeFeaturePlaceholderPane(
                                        modifier = Modifier.fillMaxSize(),
                                        icon = Icons.Default.VerifiedUser,
                                        title = stringResource(R.string.authenticator),
                                        message = stringResource(R.string.ime_authenticator_panel_placeholder)
                                    )
                                }
                                MonicaImePanel.DOCUMENTS -> {
                                    ImeFeaturePlaceholderPane(
                                        modifier = Modifier.fillMaxSize(),
                                        icon = Icons.Default.Badge,
                                        title = stringResource(R.string.documents),
                                        message = stringResource(R.string.ime_documents_panel_placeholder)
                                    )
                                }
                                MonicaImePanel.KEYBOARD -> Unit
                            }
                        }

                        if (showUnlockPanel) {
                            ImeUnlockFloatingPanel(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                errorMessage = uiState.errorMessage,
                                onOpenUnlockApp = onOpenUnlockApp
                            )
                        }

                        if (!showPanelContent && !showUnlockPanel) {
                            MonicaKeyboard(
                                modifier = Modifier.fillMaxSize(),
                                mode = uiState.keyboardMode,
                                isUppercase = uiState.isUppercase,
                                onKeyPressed = onKeyPressed,
                                onBackspace = onBackspace,
                                onEnter = onEnter,
                                onSpace = onSpace,
                                onShiftToggle = onShiftToggle,
                                onKeyboardModeChange = onKeyboardModeChange,
                                onSwitchInputMethod = onSwitchInputMethod
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MonicaImeToolbar(
    uiState: MonicaImeUiState,
    onPanelSelected: (MonicaImePanel) -> Unit,
    onDismiss: () -> Unit
) {
    val selected = when (uiState.activePanel) {
        MonicaImePanel.KEYBOARD -> MonicaToolbarSelection.MONICA
        MonicaImePanel.PASSWORDS -> MonicaToolbarSelection.PASSWORDS
        MonicaImePanel.AUTHENTICATORS -> MonicaToolbarSelection.AUTHENTICATORS
        MonicaImePanel.DOCUMENTS -> MonicaToolbarSelection.DOCUMENTS
    }
    val toolbarItems = listOf(
        MonicaToolbarSelection.MONICA,
        MonicaToolbarSelection.PASSWORDS,
        MonicaToolbarSelection.AUTHENTICATORS,
        MonicaToolbarSelection.DOCUMENTS
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.widthIn(max = 240.dp),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
        ) {
            toolbarItems.forEachIndexed { index, item ->
                ConnectedToolbarButton(
                    selected = selected == item,
                    position = when (index) {
                        0 -> ConnectedToolbarPosition.LEADING
                        toolbarItems.lastIndex -> ConnectedToolbarPosition.TRAILING
                        else -> ConnectedToolbarPosition.MIDDLE
                    },
                    contentDescription = when (item) {
                        MonicaToolbarSelection.MONICA -> stringResource(R.string.ime_toolbar_keyboard)
                        MonicaToolbarSelection.PASSWORDS -> stringResource(R.string.ime_toolbar_autofill)
                        MonicaToolbarSelection.AUTHENTICATORS -> stringResource(R.string.authenticator)
                        MonicaToolbarSelection.DOCUMENTS -> stringResource(R.string.documents)
                    },
                    imageVector = when (item) {
                        MonicaToolbarSelection.MONICA -> Icons.Default.Keyboard
                        MonicaToolbarSelection.PASSWORDS -> Icons.Default.Key
                        MonicaToolbarSelection.AUTHENTICATORS -> Icons.Default.VerifiedUser
                        MonicaToolbarSelection.DOCUMENTS -> Icons.Default.Badge
                    },
                    onClick = {
                        when (item) {
                            MonicaToolbarSelection.MONICA -> onPanelSelected(MonicaImePanel.KEYBOARD)
                            MonicaToolbarSelection.PASSWORDS -> onPanelSelected(MonicaImePanel.PASSWORDS)
                            MonicaToolbarSelection.AUTHENTICATORS -> onPanelSelected(MonicaImePanel.AUTHENTICATORS)
                            MonicaToolbarSelection.DOCUMENTS -> onPanelSelected(MonicaImePanel.DOCUMENTS)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        ToolbarCircleButton(
            selected = false,
            onClick = { },
            contentDescription = null
        ) {
            Icon(Icons.Default.MoreHoriz, contentDescription = null)
        }

        ToolbarCircleButton(
            selected = false,
            onClick = onDismiss,
            contentDescription = stringResource(R.string.ime_toolbar_keyboard)
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
    }
}

private enum class ConnectedToolbarPosition {
    LEADING,
    MIDDLE,
    TRAILING
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.ConnectedToolbarButton(
    selected: Boolean,
    position: ConnectedToolbarPosition,
    contentDescription: String,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val animatedWeight by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
        label = "toolbarWeight"
    )

    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = Modifier
            .zIndex(if (selected) 1f else 0f)
            .weight(animatedWeight)
            .height(46.dp)
            .sizeIn(minWidth = 48.dp)
            .semantics { role = Role.RadioButton },
        shapes = when (position) {
            ConnectedToolbarPosition.LEADING -> ButtonGroupDefaults.connectedLeadingButtonShapes()
            ConnectedToolbarPosition.MIDDLE -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            ConnectedToolbarPosition.TRAILING -> ButtonGroupDefaults.connectedTrailingButtonShapes()
        },
        contentPadding = PaddingValues(4.dp)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun UnlockedVaultPane(
    modifier: Modifier = Modifier,
    uiState: MonicaImeUiState,
    onQueryChanged: (String) -> Unit,
    onDatabaseScopeSelected: (MonicaImeDatabaseScope) -> Unit,
    onInsertPassword: (MonicaImePasswordEntry) -> Unit,
    onInsertUsername: (MonicaImePasswordEntry) -> Unit
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                )
            }

            if (uiState.entries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.databaseOptions.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(uiState.databaseOptions, key = { it.label }) { option ->
                                MonicaExpressiveFilterChip(
                                    selected = uiState.selectedDatabaseScope == option.scope,
                                    onClick = { onDatabaseScopeSelected(option.scope) },
                                    label = option.label,
                                    leadingIcon = option.scope.icon()
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyVaultState(query = uiState.query)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        top = 8.dp,
                        end = 14.dp,
                        bottom = 10.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (uiState.databaseOptions.isNotEmpty()) {
                        item(key = "database_filters") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                items(uiState.databaseOptions, key = { it.label }) { option ->
                                    MonicaExpressiveFilterChip(
                                        selected = uiState.selectedDatabaseScope == option.scope,
                                        onClick = { onDatabaseScopeSelected(option.scope) },
                                        label = option.label,
                                        leadingIcon = option.scope.icon()
                                    )
                                }
                            }
                        }
                    }
                    items(uiState.entries, key = { it.id }) { entry ->
                        PasswordEntryCard(
                            entry = entry,
                            onInsertPassword = { onInsertPassword(entry) },
                            onInsertUsername = { onInsertUsername(entry) }
                        )
                    }
                }
            }
        }
    }
}

private fun MonicaImeDatabaseScope.icon(): androidx.compose.ui.graphics.vector.ImageVector {
    return when (this) {
        MonicaImeDatabaseScope.All -> Icons.AutoMirrored.Filled.List
        MonicaImeDatabaseScope.Local -> Icons.Default.Smartphone
        is MonicaImeDatabaseScope.KeePass -> Icons.Default.Key
        is MonicaImeDatabaseScope.Bitwarden -> Icons.Default.CloudSync
    }
}

@Composable
private fun EmptyVaultState(query: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (query.isBlank()) {
                    stringResource(R.string.ime_empty_title)
                } else {
                    stringResource(R.string.ime_no_matches_title)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (query.isBlank()) {
                    stringResource(R.string.ime_empty_message)
                } else {
                    stringResource(R.string.ime_no_matches_message)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImeFeaturePlaceholderPane(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PasswordEntryCard(
    entry: MonicaImePasswordEntry,
    onInsertPassword: () -> Unit,
    onInsertUsername: () -> Unit
) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val appIcon = entry.packageName
        .takeIf { it.isNotBlank() }
        ?.let { rememberAppIcon(it) }
    val cardShape = RoundedCornerShape(22.dp)
    val interactionSource = remember(entry.id) { MutableInteractionSource() }

    Box(modifier = Modifier.fillMaxWidth()) {
        ElevatedCard(
            shape = cardShape,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        expanded = !expanded
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (appIcon != null) {
                            Image(
                                bitmap = appIcon,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title.ifBlank {
                                entry.website.ifBlank { stringResource(R.string.ime_untitled_account) }
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (entry.username.isNotBlank()) {
                            Text(
                                text = entry.username,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (expanded) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                expanded = false
                                onInsertPassword()
                            }
                        ) {
                            Text(stringResource(R.string.password))
                        }
                        if (entry.username.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    expanded = false
                                    onInsertUsername()
                                }
                            ) {
                                Text(stringResource(R.string.username))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonicaKeyboard(
    modifier: Modifier = Modifier,
    mode: MonicaKeyboardMode,
    isUppercase: Boolean,
    onKeyPressed: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onSpace: () -> Unit,
    onShiftToggle: () -> Unit,
    onKeyboardModeChange: (MonicaKeyboardMode) -> Unit,
    onSwitchInputMethod: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (mode == MonicaKeyboardMode.LETTERS) {
            MonicaLetterKeyboard(
                isUppercase = isUppercase,
                onKeyPressed = onKeyPressed,
                onBackspace = onBackspace,
                onEnter = onEnter,
                onSpace = onSpace,
                onShiftToggle = onShiftToggle,
                onKeyboardModeChange = onKeyboardModeChange,
                onSwitchInputMethod = onSwitchInputMethod
            )
        }

        if (mode == MonicaKeyboardMode.NUMBERS) {
            MonicaNumberKeyboard(
                onKeyPressed = onKeyPressed,
                onBackspace = onBackspace,
                onEnter = onEnter,
                onKeyboardModeChange = onKeyboardModeChange
            )
        }

        if (mode == MonicaKeyboardMode.SYMBOLS) {
            MonicaSymbolKeyboard(
                onKeyPressed = onKeyPressed,
                onBackspace = onBackspace,
                onEnter = onEnter,
                onKeyboardModeChange = onKeyboardModeChange
            )
        }
    }
}

@Composable
private fun MonicaLetterKeyboard(
    isUppercase: Boolean,
    onKeyPressed: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onSpace: () -> Unit,
    onShiftToggle: () -> Unit,
    onKeyboardModeChange: (MonicaKeyboardMode) -> Unit,
    onSwitchInputMethod: () -> Unit
) {
    val rows = listOf(
        "qwertyuiop".toList(),
        "asdfghjkl".toList(),
        "zxcvbnm".toList()
    )

    rows.forEachIndexed { index, chars ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            if (index == 2) {
                MonicaKeyButton(
                    label = "",
                    icon = { Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.ime_key_shift)) },
                    weight = 1.35f,
                    active = isUppercase,
                    style = MonicaKeyStyle.ACCENT,
                    cornerRadius = 14.dp,
                    onClick = onShiftToggle
                )
            }

            chars.forEach { char ->
                val output = if (isUppercase) {
                    char.uppercaseChar().toString()
                } else {
                    char.toString()
                }
                MonicaKeyButton(
                    label = char.uppercaseChar().toString(),
                    weight = 1f,
                    cornerRadius = 12.dp,
                    onClick = { onKeyPressed(output) }
                )
            }

            if (index == 2) {
                MonicaKeyButton(
                    label = "",
                    icon = { Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.ime_key_delete)) },
                    weight = 1.35f,
                    style = MonicaKeyStyle.ACCENT,
                    cornerRadius = 14.dp,
                    onClick = onBackspace
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        KeyboardModeKey(
            activeMode = MonicaKeyboardMode.LETTERS,
            onClick = { onKeyboardModeChange(MonicaKeyboardMode.NUMBERS) }
        )
        MonicaKeyButton(
            label = "",
            icon = { Icon(Icons.Default.Keyboard, contentDescription = stringResource(R.string.ime_key_mode)) },
            weight = 1.05f,
            style = MonicaKeyStyle.ACCENT,
            cornerRadius = 14.dp,
            onClick = onSwitchInputMethod
        )
        MonicaKeyButton(
            label = "",
            icon = { Icon(Icons.Default.SpaceBar, contentDescription = stringResource(R.string.ime_key_space)) },
            weight = 3.9f,
            cornerRadius = 18.dp,
            onClick = onSpace
        )
        MonicaKeyButton(
            label = ".",
            weight = 0.95f,
            cornerRadius = 12.dp,
            onClick = { onKeyPressed(".") }
        )
        MonicaKeyButton(
            label = "",
            icon = { Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = stringResource(R.string.ime_key_enter)) },
            weight = 1.8f,
            style = MonicaKeyStyle.PRIMARY,
            cornerRadius = 20.dp,
            onClick = onEnter
        )
    }
}

@Composable
private fun MonicaNumberKeyboard(
    onKeyPressed: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onKeyboardModeChange: (MonicaKeyboardMode) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val keySpacing = 6.dp
        val rightColumnWidth = (maxWidth - keySpacing * 3) / 4f
        val mainGridWidth = maxWidth - rightColumnWidth - keySpacing

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(keySpacing)
        ) {
            Column(
                modifier = Modifier.width(mainGridWidth),
                verticalArrangement = Arrangement.spacedBy(keySpacing)
            ) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                ).forEach { keys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(keySpacing)
                    ) {
                        keys.forEach { key ->
                            MonicaKeyButton(
                                label = key,
                                weight = 1f,
                                cornerRadius = 10.dp,
                                onClick = { onKeyPressed(key) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(keySpacing)
                ) {
                    KeyboardModeKey(
                        activeMode = MonicaKeyboardMode.NUMBERS,
                        weight = 1f,
                        onClick = { onKeyboardModeChange(nextKeyboardMode(MonicaKeyboardMode.NUMBERS)) }
                    )
                    MonicaKeyButton(
                        label = "0",
                        weight = 1f,
                        cornerRadius = 10.dp,
                        onClick = { onKeyPressed("0") }
                    )
                    MonicaKeyButton(
                        label = ".",
                        weight = 1f,
                        cornerRadius = 10.dp,
                        onClick = { onKeyPressed(".") }
                    )
                }
            }

            Column(
                modifier = Modifier.width(rightColumnWidth),
                verticalArrangement = Arrangement.spacedBy(keySpacing)
            ) {
                MonicaKeyButtonBase(
                    modifier = Modifier.fillMaxWidth(),
                    label = "",
                    icon = { Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.ime_key_delete)) },
                    style = MonicaKeyStyle.ACCENT,
                    cornerRadius = 10.dp,
                    onClick = onBackspace
                )
                MonicaKeyButtonBase(
                    modifier = Modifier.fillMaxWidth(),
                    label = "",
                    icon = { Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = stringResource(R.string.ime_key_enter)) },
                    style = MonicaKeyStyle.PRIMARY,
                    cornerRadius = 10.dp,
                    height = 162.dp,
                    onClick = onEnter
                )
            }
        }
    }
}

@Composable
private fun MonicaSymbolKeyboard(
    onKeyPressed: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onKeyboardModeChange: (MonicaKeyboardMode) -> Unit
) {
    val rows = listOf(
        "1234567890".map { it.toString() },
        listOf("@", "#", "$", "%", "&", "*", "-", "+", "=", "/")
    )

    rows.forEach { keys ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            keys.forEach { key ->
                MonicaKeyButton(
                    label = key,
                    weight = 1f,
                    cornerRadius = 12.dp,
                    onClick = { onKeyPressed(key) }
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("!", "?", "(", ")", "[", "]", "{", "}").forEach { key ->
            MonicaKeyButton(
                label = key,
                weight = 1f,
                cornerRadius = 12.dp,
                onClick = { onKeyPressed(key) }
            )
        }
        MonicaKeyButton(
            label = "",
            icon = { Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.ime_key_delete)) },
            weight = 2f,
            style = MonicaKeyStyle.ACCENT,
            cornerRadius = 12.dp,
            onClick = onBackspace
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        KeyboardModeKey(
            activeMode = MonicaKeyboardMode.SYMBOLS,
            onClick = { onKeyboardModeChange(nextKeyboardMode(MonicaKeyboardMode.SYMBOLS)) }
        )
        MonicaKeyButton(
            label = ",",
            weight = 1.05f,
            cornerRadius = 14.dp,
            onClick = { onKeyPressed(",") }
        )
        MonicaKeyButton(
            label = "",
            icon = { Icon(Icons.Default.SpaceBar, contentDescription = stringResource(R.string.ime_key_space)) },
            weight = 3.9f,
            cornerRadius = 18.dp,
            onClick = { onKeyPressed(" ") }
        )
        MonicaKeyButton(
            label = ".",
            weight = 0.95f,
            cornerRadius = 12.dp,
            onClick = { onKeyPressed(".") }
        )
        MonicaKeyButton(
            label = "",
            icon = { Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = stringResource(R.string.ime_key_enter)) },
            weight = 1.8f,
            style = MonicaKeyStyle.PRIMARY,
            cornerRadius = 20.dp,
            onClick = onEnter
        )
    }
}

private fun nextKeyboardMode(currentMode: MonicaKeyboardMode): MonicaKeyboardMode {
    return when (currentMode) {
        MonicaKeyboardMode.LETTERS -> MonicaKeyboardMode.NUMBERS
        MonicaKeyboardMode.NUMBERS -> MonicaKeyboardMode.SYMBOLS
        MonicaKeyboardMode.SYMBOLS -> MonicaKeyboardMode.LETTERS
    }
}

@Composable
private fun RowScope.KeyboardModeKey(
    activeMode: MonicaKeyboardMode,
    weight: Float = 1.55f,
    onClick: () -> Unit
) {
    MonicaKeyButton(
        label = "",
        weight = weight,
        style = MonicaKeyStyle.ACCENT,
        cornerRadius = 20.dp,
        onClick = onClick
    ) {
        KeyboardModeLabel(activeMode = activeMode)
    }
}

@Composable
private fun KeyboardModeLabel(activeMode: MonicaKeyboardMode) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        KeyboardModeLabelPart("A", activeMode == MonicaKeyboardMode.LETTERS)
        KeyboardModeLabelPart("1", activeMode == MonicaKeyboardMode.NUMBERS)
        KeyboardModeLabelPart("@", activeMode == MonicaKeyboardMode.SYMBOLS)
    }
}

@Composable
private fun KeyboardModeLabelPart(text: String, selected: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.45f)
        }
    )
}

@Composable
private fun ToolbarCircleButton(
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String? = null,
    enabled: Boolean = true,
    label: String? = null,
    content: @Composable (() -> Unit)? = null
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(40.dp),
        shape = CircleShape
    ) {
        if (content != null) {
            content()
        } else {
            Text(
                text = label.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MonicaKeyButtonBase(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    icon: @Composable (() -> Unit)? = null,
    cornerRadius: androidx.compose.ui.unit.Dp = 12.dp,
    style: MonicaKeyStyle = MonicaKeyStyle.STANDARD,
    height: androidx.compose.ui.unit.Dp = 50.dp,
    content: @Composable (() -> Unit)? = null
) {
    val containerColor = when {
        active -> MaterialTheme.colorScheme.primaryContainer
        style == MonicaKeyStyle.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
        style == MonicaKeyStyle.ACCENT -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when {
        active -> MaterialTheme.colorScheme.onPrimaryContainer
        style == MonicaKeyStyle.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
        style == MonicaKeyStyle.ACCENT -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick),
        color = containerColor,
        shadowElevation = 2.dp,
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (content != null) {
                content()
            } else if (icon != null) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    icon()
                }
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun RowScope.MonicaKeyButton(
    label: String,
    weight: Float,
    onClick: () -> Unit,
    active: Boolean = false,
    icon: @Composable (() -> Unit)? = null,
    cornerRadius: androidx.compose.ui.unit.Dp = 12.dp,
    style: MonicaKeyStyle = MonicaKeyStyle.STANDARD,
    height: androidx.compose.ui.unit.Dp = 50.dp,
    content: @Composable (() -> Unit)? = null
) {
    MonicaKeyButtonBase(
        modifier = Modifier
            .weight(weight),
        label = label,
        onClick = onClick,
        active = active,
        icon = icon,
        cornerRadius = cornerRadius,
        style = style,
        height = height,
        content = content
    )
}
