package takagi.ru.monica.ime

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.autofill_ng.ui.rememberAppIcon
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.ui.PasswordListInitialLoadingIndicator
import takagi.ru.monica.ui.components.MonicaExpressiveFilterChip
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.util.PasswordGenerator

internal data class MonicaImePasswordEntry(
    val id: Long,
    val title: String,
    val username: String,
    val website: String,
    val packageName: String,
    val password: String,
    val isFavorite: Boolean,
    val sourceLabel: String,
    val totpCode: String = "",
    val keepassDatabaseId: Long? = null,
    val mdbxDatabaseId: Long? = null,
    val bitwardenVaultId: Long? = null
)

internal data class MonicaImeAuthenticatorEntry(
    val id: Long,
    val title: String,
    val issuer: String,
    val accountName: String,
    val code: String,
    val remainingSeconds: Int,
    val isFavorite: Boolean,
    val sourceLabel: String,
    val keepassDatabaseId: Long? = null,
    val mdbxDatabaseId: Long? = null,
    val bitwardenVaultId: Long? = null
)

internal data class MonicaImeCardWalletField(
    val label: String,
    val value: String
)

internal data class MonicaImeCardWalletEntry(
    val id: Long,
    val title: String,
    val subtitle: String,
    val typeLabel: String,
    val isFavorite: Boolean,
    val sourceLabel: String,
    val fields: List<MonicaImeCardWalletField>,
    val keepassDatabaseId: Long? = null,
    val mdbxDatabaseId: Long? = null,
    val bitwardenVaultId: Long? = null
)

internal data class MonicaImeUiState(
    val unlocked: Boolean = false,
    val activePackageName: String = "",
    val activePanel: MonicaImePanel = MonicaImePanel.KEYBOARD,
    val query: String = "",
    val entries: List<MonicaImePasswordEntry> = emptyList(),
    val authenticatorEntries: List<MonicaImeAuthenticatorEntry> = emptyList(),
    val cardWalletEntries: List<MonicaImeCardWalletEntry> = emptyList(),
    val databaseOptions: List<MonicaImeDatabaseOption> = emptyList(),
    val selectedDatabaseScope: MonicaImeDatabaseScope = MonicaImeDatabaseScope.All,
    val errorMessage: String? = null,
    val keyboardMode: MonicaKeyboardMode = MonicaKeyboardMode.LETTERS,
    val isUppercase: Boolean = false,
    val autoLockMinutes: Int = 5,
    val isAutofillPanelVisible: Boolean = false,
    val isAutofillLoading: Boolean = false,
    val pendingClearedInput: String? = null
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
    DOCUMENTS,
    GENERATOR
}

internal sealed interface MonicaImeDatabaseScope {
    data object All : MonicaImeDatabaseScope
    data object Local : MonicaImeDatabaseScope
    data class KeePass(val databaseId: Long) : MonicaImeDatabaseScope
    data class Mdbx(val databaseId: Long) : MonicaImeDatabaseScope
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
    DOCUMENTS,
    GENERATOR
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
    onSmartFillPassword: (MonicaImePasswordEntry) -> Unit,
    onInsertAuthenticatorCode: (MonicaImeAuthenticatorEntry) -> Unit,
    onInsertCardWalletValue: (MonicaImeCardWalletField) -> Unit,
    onSmartFillCardWallet: (MonicaImeCardWalletEntry) -> Unit,
    onKeyPressed: (String) -> Unit,
    onBackspace: () -> Unit,
    onDeleteAll: () -> Unit,
    onUndoDeleteAll: () -> Unit,
    onEnter: () -> Unit,
    onSpace: () -> Unit,
    onShiftToggle: () -> Unit,
    onKeyboardModeChange: (MonicaKeyboardMode) -> Unit,
    onOpenUnlockApp: () -> Unit,
    onOpenAutofillSettings: () -> Unit,
    onPanelSelected: (MonicaImePanel) -> Unit,
    onSwitchInputMethod: () -> Unit,
    onDismiss: () -> Unit
) {
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val activePanelRequiresUnlock = when (uiState.activePanel) {
        MonicaImePanel.PASSWORDS,
        MonicaImePanel.AUTHENTICATORS,
        MonicaImePanel.DOCUMENTS -> true
        MonicaImePanel.KEYBOARD,
        MonicaImePanel.GENERATOR -> false
    }
    val showPanelContent = uiState.activePanel != MonicaImePanel.KEYBOARD &&
        (!activePanelRequiresUnlock || uiState.unlocked)
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
                        modifier = Modifier.zIndex(0f),
                        uiState = uiState,
                        onPanelSelected = onPanelSelected,
                        onUndoDeleteAll = onUndoDeleteAll,
                        onOpenAutofillSettings = onOpenAutofillSettings,
                        onDismiss = onDismiss
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(MonicaImeContentAreaHeight)
                            .zIndex(30f)
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
                                        onInsertUsername = onInsertUsername,
                                        onInsertTotp = { entry ->
                                            val code = entry.totpCode
                                            if (code.isNotBlank()) onKeyPressed(code)
                                        },
                                        onSmartFillPassword = onSmartFillPassword
                                    )
                                }
                                MonicaImePanel.AUTHENTICATORS -> {
                                    AuthenticatorPane(
                                        modifier = Modifier.fillMaxSize(),
                                        uiState = uiState,
                                        onDatabaseScopeSelected = onDatabaseScopeSelected,
                                        onInsertCode = onInsertAuthenticatorCode
                                    )
                                }
                                MonicaImePanel.DOCUMENTS -> {
                                    CardWalletPane(
                                        modifier = Modifier.fillMaxSize(),
                                        uiState = uiState,
                                        onDatabaseScopeSelected = onDatabaseScopeSelected,
                                        onInsertField = onInsertCardWalletValue,
                                        onSmartFill = onSmartFillCardWallet
                                    )
                                }
                                MonicaImePanel.GENERATOR -> {
                                    ImeGeneratorPane(
                                        modifier = Modifier.fillMaxSize(),
                                        onInsertPassword = onKeyPressed
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
                                onDeleteAll = onDeleteAll,
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
    modifier: Modifier = Modifier,
    uiState: MonicaImeUiState,
    onPanelSelected: (MonicaImePanel) -> Unit,
    onUndoDeleteAll: () -> Unit,
    onOpenAutofillSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val selected = when (uiState.activePanel) {
        MonicaImePanel.KEYBOARD -> MonicaToolbarSelection.MONICA
        MonicaImePanel.PASSWORDS -> MonicaToolbarSelection.PASSWORDS
        MonicaImePanel.AUTHENTICATORS -> MonicaToolbarSelection.AUTHENTICATORS
        MonicaImePanel.DOCUMENTS -> MonicaToolbarSelection.DOCUMENTS
        MonicaImePanel.GENERATOR -> MonicaToolbarSelection.GENERATOR
    }
    val toolbarItems = listOf(
        MonicaToolbarSelection.MONICA,
        MonicaToolbarSelection.PASSWORDS,
        MonicaToolbarSelection.AUTHENTICATORS,
        MonicaToolbarSelection.DOCUMENTS,
        MonicaToolbarSelection.GENERATOR
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
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
                        MonicaToolbarSelection.DOCUMENTS -> stringResource(R.string.nav_card_wallet)
                        MonicaToolbarSelection.GENERATOR -> stringResource(R.string.generator)
                    },
                    imageVector = when (item) {
                        MonicaToolbarSelection.MONICA -> Icons.Default.Keyboard
                        MonicaToolbarSelection.PASSWORDS -> Icons.Default.Key
                        MonicaToolbarSelection.AUTHENTICATORS -> Icons.Default.VerifiedUser
                        MonicaToolbarSelection.DOCUMENTS -> Icons.Default.Badge
                        MonicaToolbarSelection.GENERATOR -> Icons.Default.AutoAwesome
                    },
                    onClick = {
                        when (item) {
                            MonicaToolbarSelection.MONICA -> onPanelSelected(MonicaImePanel.KEYBOARD)
                            MonicaToolbarSelection.PASSWORDS -> onPanelSelected(MonicaImePanel.PASSWORDS)
                            MonicaToolbarSelection.AUTHENTICATORS -> onPanelSelected(MonicaImePanel.AUTHENTICATORS)
                            MonicaToolbarSelection.DOCUMENTS -> onPanelSelected(MonicaImePanel.DOCUMENTS)
                            MonicaToolbarSelection.GENERATOR -> onPanelSelected(MonicaImePanel.GENERATOR)
                        }
                    }
                )
            }
        }

        if (uiState.pendingClearedInput != null) {
            ToolbarCircleButton(
                selected = true,
                onClick = onUndoDeleteAll,
                contentDescription = stringResource(R.string.ime_clear_all_undo_action)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = null
                )
            }
        } else {
            ToolbarCircleButton(
                selected = false,
                onClick = onOpenAutofillSettings,
                contentDescription = stringResource(R.string.autofill)
            ) {
                Icon(Icons.Default.MoreHoriz, contentDescription = null)
            }
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
            .sizeIn(minWidth = 42.dp)
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
private fun ImeGeneratorPane(
    modifier: Modifier = Modifier,
    onInsertPassword: (String) -> Unit
) {
    var length by rememberSaveable { mutableStateOf(16) }
    var generatedPassword by rememberSaveable {
        mutableStateOf(generateImePassword(length))
    }
    var refreshTick by rememberSaveable { mutableStateOf(0) }
    val refreshRotation by animateFloatAsState(
        targetValue = refreshTick * 360f,
        animationSpec = tween(durationMillis = 420),
        label = "imeGeneratorRefreshRotation"
    )

    fun updatePassword(nextLength: Int = length) {
        length = nextLength
        generatedPassword = generateImePassword(nextLength)
        refreshTick += 1
    }

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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.generator),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.generator_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledIconButton(
                    onClick = { updatePassword() },
                    modifier = Modifier
                        .size(42.dp)
                        .graphicsLayer { rotationZ = refreshRotation }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.generate_password)
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                AnimatedContent(
                    targetState = generatedPassword,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(160)) + scaleIn(initialScale = 0.96f)) togetherWith
                            (fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 1.02f)) using
                            SizeTransform(clip = false)
                    },
                    label = "imeGeneratedPassword"
                ) { password ->
                    Text(
                        text = password,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { updatePassword((length - 4).coerceAtLeast(8)) },
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("-")
                }
                Text(
                    text = stringResource(R.string.ime_generator_length_value, length),
                    modifier = Modifier.width(54.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(
                    onClick = { updatePassword((length + 4).coerceAtMost(32)) },
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("+")
                }
                OutlinedButton(
                    onClick = { onInsertPassword(generatedPassword) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.use_password),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun generateImePassword(length: Int): String {
    return PasswordGenerator.generatePassword(
        length = length,
        includeUppercase = true,
        includeLowercase = true,
        includeNumbers = true,
        includeSymbols = true,
        excludeSimilar = true,
        uppercaseMin = 1,
        lowercaseMin = 1,
        numbersMin = 1,
        symbolsMin = 1
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UnlockedVaultPane(
    modifier: Modifier = Modifier,
    uiState: MonicaImeUiState,
    onQueryChanged: (String) -> Unit,
    onDatabaseScopeSelected: (MonicaImeDatabaseScope) -> Unit,
    onInsertPassword: (MonicaImePasswordEntry) -> Unit,
    onInsertUsername: (MonicaImePasswordEntry) -> Unit,
    onInsertTotp: (MonicaImePasswordEntry) -> Unit,
    onSmartFillPassword: (MonicaImePasswordEntry) -> Unit
) {
    val showAutofillLoading = uiState.isAutofillLoading ||
        (uiState.unlocked && uiState.errorMessage == null && uiState.databaseOptions.isEmpty())

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
                        if (showAutofillLoading) {
                            AutofillLoadingState()
                        } else {
                            EmptyVaultState(query = uiState.query)
                        }
                    }
                }
            } else {
                val lazyListState = rememberLazyListState()
                // 计算字母索引：每个首字母对应的第一个条目在列表中的位置
                // 注意：如果有 database_filters item，条目从 index 1 开始
                val filterItemOffset = if (uiState.databaseOptions.isNotEmpty()) 1 else 0
                val letterIndex: List<Pair<String, Int>> = remember(uiState.entries, filterItemOffset) {
                    buildImeLetterIndex(
                        itemCount = uiState.entries.size,
                        itemOffset = filterItemOffset
                    ) { index ->
                        val entry = uiState.entries[index]
                        entry.title.ifBlank {
                            entry.website.ifBlank { entry.username }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentPadding = PaddingValues(
                                start = 14.dp,
                                top = 8.dp,
                                end = 6.dp,
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
                                    onSmartFill = { onSmartFillPassword(entry) },
                                    onInsertPassword = { onInsertPassword(entry) },
                                    onInsertUsername = { onInsertUsername(entry) },
                                    onInsertTotp = { onInsertTotp(entry) }
                                )
                            }
                        }
                        VelocityScrollBar(
                            lazyListState = lazyListState,
                            letterIndex = letterIndex,
                            modifier = Modifier
                                .width(24.dp)
                                .fillMaxHeight()
                                .padding(end = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VelocityScrollBar(
    lazyListState: LazyListState,
    letterIndex: List<Pair<String, Int>>,  // letter -> item index
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var anchorY by remember { mutableFloatStateOf(0f) }
    var currentDragDelta by remember { mutableFloatStateOf(0f) }
    // 当前手指在滚动条上的绝对 Y（用于字母跳转）
    var fingerY by remember { mutableFloatStateOf(0f) }
    var barHeightPx by remember { mutableFloatStateOf(1f) }
    // 当前显示的首字母（拖动时）
    var currentLetter by remember { mutableStateOf("") }

    // 根据手指 Y 位置映射到字母索引
    fun letterAtY(y: Float): Pair<String, Int>? {
        if (letterIndex.isEmpty()) return null
        val ratio = (y / barHeightPx).coerceIn(0f, 1f)
        val idx = (ratio * letterIndex.size).toInt().coerceIn(0, letterIndex.size - 1)
        return letterIndex[idx]
    }

    val trackColor by animateColorAsState(
        targetValue = if (isDragging) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "scrollBarColor"
    )

    // 速度滚动循环（仅在没有字母索引时或字母数量少时作为补充）
    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect
        while (isDragging) {
            // 只有字母索引为空时才用速度模式
            if (letterIndex.isEmpty()) {
                val speed = currentDragDelta * 0.15f
                lazyListState.scrollBy(speed)
            }
            delay(16L)
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { barHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(letterIndex) {
                detectDragGestures(
                    onDragStart = { offset ->
                        anchorY = offset.y
                        fingerY = offset.y
                        isDragging = true
                        currentDragDelta = 0f
                        // 立即跳转到对应字母
                        val target = letterAtY(offset.y)
                        if (target != null) {
                            currentLetter = target.first
                            coroutineScope.launch {
                                lazyListState.scrollToItem(target.second)
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        fingerY = change.position.y
                        currentDragDelta = change.position.y - anchorY
                        val target = letterAtY(change.position.y)
                        if (target != null) {
                            if (target.first != currentLetter) {
                                currentLetter = target.first
                                coroutineScope.launch {
                                    lazyListState.scrollToItem(target.second)
                                }
                            }
                        } else {
                            currentLetter = ""
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        currentDragDelta = 0f
                        currentLetter = ""
                    },
                    onDragCancel = {
                        isDragging = false
                        currentDragDelta = 0f
                        currentLetter = ""
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // 轨道竖条
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight(0.7f)
                .clip(RoundedCornerShape(2.dp))
                .background(trackColor)
        )

        // 拖动时：在滚动条左侧显示首字母气泡
        if (isDragging && currentLetter.isNotEmpty()) {
            val bubbleOffsetY = (fingerY - barHeightPx / 2f)
                .coerceIn(-barHeightPx / 2f + 16f, barHeightPx / 2f - 16f)
            Box(
                modifier = Modifier
                    .offset(x = (-36).dp, y = with(LocalDensity.current) { bubbleOffsetY.toDp() })
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentLetter,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

private fun buildImeLetterIndex(
    itemCount: Int,
    itemOffset: Int = 0,
    titleAt: (Int) -> String
): List<Pair<String, Int>> {
    val result = mutableListOf<Pair<String, Int>>()
    var lastLetter = ""

    repeat(itemCount) { index ->
        val letter = titleAt(index)
            .firstOrNull()
            ?.uppercaseChar()
            ?.let { if (it.isLetter()) it.toString() else "#" }
            ?: "#"

        if (letter != lastLetter) {
            result += letter to (index + itemOffset)
            lastLetter = letter
        }
    }

    return result
}

private fun MonicaImeDatabaseScope.icon(): androidx.compose.ui.graphics.vector.ImageVector {
    return when (this) {
        MonicaImeDatabaseScope.All -> Icons.AutoMirrored.Filled.List
        MonicaImeDatabaseScope.Local -> Icons.Default.Smartphone
        is MonicaImeDatabaseScope.KeePass -> Icons.Default.Key
        is MonicaImeDatabaseScope.Mdbx -> Icons.Default.Storage
        is MonicaImeDatabaseScope.Bitwarden -> Icons.Default.CloudSync
    }
}

@Composable
private fun AutofillLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        PasswordListInitialLoadingIndicator()
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
private fun AuthenticatorPane(
    modifier: Modifier = Modifier,
    uiState: MonicaImeUiState,
    onDatabaseScopeSelected: (MonicaImeDatabaseScope) -> Unit,
    onInsertCode: (MonicaImeAuthenticatorEntry) -> Unit
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
        Column(modifier = Modifier.fillMaxSize()) {
            DatabaseScopeFilterRow(
                uiState = uiState,
                onDatabaseScopeSelected = onDatabaseScopeSelected
            )
            if (uiState.authenticatorEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ImeEmptyState(
                        icon = Icons.Default.VerifiedUser,
                        title = stringResource(R.string.ime_empty_authenticator_title),
                        message = stringResource(R.string.ime_empty_authenticator_message)
                    )
                }
            } else {
                val lazyListState = rememberLazyListState()
                val letterIndex = remember(uiState.authenticatorEntries) {
                    buildImeLetterIndex(itemCount = uiState.authenticatorEntries.size) { index ->
                        val entry = uiState.authenticatorEntries[index]
                        entry.title.ifBlank {
                            entry.issuer.ifBlank {
                                entry.accountName.ifBlank { entry.sourceLabel }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                ) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(
                            start = 14.dp,
                            top = 0.dp,
                            end = 6.dp,
                            bottom = 10.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.authenticatorEntries, key = { it.id }) { entry ->
                            AuthenticatorEntryCard(
                                entry = entry,
                                onInsertCode = { onInsertCode(entry) }
                            )
                        }
                    }
                    VelocityScrollBar(
                        lazyListState = lazyListState,
                        letterIndex = letterIndex,
                        modifier = Modifier
                            .width(24.dp)
                            .fillMaxHeight()
                            .padding(end = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CardWalletPane(
    modifier: Modifier = Modifier,
    uiState: MonicaImeUiState,
    onDatabaseScopeSelected: (MonicaImeDatabaseScope) -> Unit,
    onInsertField: (MonicaImeCardWalletField) -> Unit,
    onSmartFill: (MonicaImeCardWalletEntry) -> Unit
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
        Column(modifier = Modifier.fillMaxSize()) {
            DatabaseScopeFilterRow(
                uiState = uiState,
                onDatabaseScopeSelected = onDatabaseScopeSelected
            )
            if (uiState.cardWalletEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ImeEmptyState(
                        icon = Icons.Default.CreditCard,
                        title = stringResource(R.string.ime_empty_card_wallet_title),
                        message = stringResource(R.string.ime_empty_card_wallet_message)
                    )
                }
            } else {
                val lazyListState = rememberLazyListState()
                val letterIndex = remember(uiState.cardWalletEntries) {
                    buildImeLetterIndex(itemCount = uiState.cardWalletEntries.size) { index ->
                        val entry = uiState.cardWalletEntries[index]
                        entry.title.ifBlank {
                            entry.subtitle.ifBlank { entry.typeLabel }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                ) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(
                            start = 14.dp,
                            top = 0.dp,
                            end = 6.dp,
                            bottom = 10.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.cardWalletEntries, key = { it.id }) { entry ->
                            CardWalletEntryCard(
                                entry = entry,
                                onSmartFill = { onSmartFill(entry) },
                                onInsertField = onInsertField
                            )
                        }
                    }
                    VelocityScrollBar(
                        lazyListState = lazyListState,
                        letterIndex = letterIndex,
                        modifier = Modifier
                            .width(24.dp)
                            .fillMaxHeight()
                            .padding(end = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DatabaseScopeFilterRow(
    uiState: MonicaImeUiState,
    onDatabaseScopeSelected: (MonicaImeDatabaseScope) -> Unit
) {
    if (uiState.databaseOptions.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 8.dp)
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

@Composable
private fun ImeEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AuthenticatorEntryCard(
    entry: MonicaImeAuthenticatorEntry,
    onInsertCode: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onInsertCode)
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
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = listOf(entry.issuer, entry.accountName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { entry.sourceLabel }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = entry.code,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (entry.remainingSeconds > 0) {
                    Text(
                        text = stringResource(R.string.ime_totp_seconds_remaining, entry.remainingSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CardWalletEntryCard(
    entry: MonicaImeCardWalletEntry,
    onSmartFill: () -> Unit,
    onInsertField: (MonicaImeCardWalletField) -> Unit
) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(22.dp)
    val interactionSource = remember(entry.id) { MutableInteractionSource() }

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
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = entry.subtitle.ifBlank { entry.typeLabel },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = entry.typeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { expanded = !expanded }
                        .padding(6.dp)
                )
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
                            onSmartFill()
                        }
                    ) {
                        Text(stringResource(R.string.ime_quick_fill))
                    }
                    entry.fields.forEach { field ->
                        OutlinedButton(
                            onClick = {
                                expanded = false
                                onInsertField(field)
                            }
                        ) {
                            Text(field.label)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PasswordEntryCard(
    entry: MonicaImePasswordEntry,
    onSmartFill: () -> Unit,
    onInsertPassword: () -> Unit,
    onInsertUsername: () -> Unit,
    onInsertTotp: () -> Unit
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
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { expanded = !expanded }
                            .padding(6.dp)
                    )
                }

                if (expanded) {
                    // 紧凑按钮内边距
                    val compactPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 立即填充（不折叠，让用户可以继续操作其他字段）
                        OutlinedButton(
                            onClick = onSmartFill,
                            contentPadding = compactPadding
                        ) {
                            Text(
                                text = stringResource(R.string.ime_quick_fill),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        // 密码（点击后不折叠）
                        OutlinedButton(
                            onClick = onInsertPassword,
                            contentPadding = compactPadding
                        ) {
                            Text(
                                text = stringResource(R.string.password),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        // 用户名（点击后不折叠）
                        if (entry.username.isNotBlank()) {
                            OutlinedButton(
                                onClick = onInsertUsername,
                                contentPadding = compactPadding
                            ) {
                                Text(
                                    text = stringResource(R.string.username),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        // 2FA 验证码（仅当有 TOTP 时显示，点击后不折叠）
                        if (entry.totpCode.isNotBlank()) {
                            OutlinedButton(
                                onClick = onInsertTotp,
                                contentPadding = compactPadding
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = entry.totpCode,
                                    style = MaterialTheme.typography.labelMedium
                                )
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
    onDeleteAll: () -> Unit,
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
                onDeleteAll = onDeleteAll,
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
                onDeleteAll = onDeleteAll,
                onEnter = onEnter,
                onKeyboardModeChange = onKeyboardModeChange
            )
        }

        if (mode == MonicaKeyboardMode.SYMBOLS) {
            MonicaSymbolKeyboard(
                onKeyPressed = onKeyPressed,
                onBackspace = onBackspace,
                onDeleteAll = onDeleteAll,
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
    onDeleteAll: () -> Unit,
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
                    cornerRadius = 8.dp,
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
                    cornerRadius = 8.dp,
                    onClick = { onKeyPressed(output) }
                )
            }

            if (index == 2) {
                MonicaKeyButton(
                    label = "",
                    icon = { Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.ime_key_delete)) },
                    weight = 1.35f,
                    style = MonicaKeyStyle.ACCENT,
                    cornerRadius = 8.dp,
                    onClick = onBackspace,
                    onLongPressRepeat = onBackspace,
                    onSwipeUp = onDeleteAll
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
            cornerRadius = 8.dp,
            onClick = onSwitchInputMethod
        )
        MonicaKeyButton(
            label = "",
            icon = { Icon(Icons.Default.SpaceBar, contentDescription = stringResource(R.string.ime_key_space)) },
            weight = 3.9f,
            cornerRadius = 8.dp,
            onClick = onSpace,
            onLongPressRepeat = onSpace
        )
        MonicaKeyButton(
            label = ".",
            weight = 0.95f,
            cornerRadius = 8.dp,
            onClick = { onKeyPressed(".") }
        )
        MonicaKeyButton(
            label = "",
            icon = { Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = stringResource(R.string.ime_key_enter)) },
            weight = 1.8f,
            style = MonicaKeyStyle.PRIMARY,
            cornerRadius = 8.dp,
            onClick = onEnter
        )
    }
}

@Composable
private fun MonicaNumberKeyboard(
    onKeyPressed: (String) -> Unit,
    onBackspace: () -> Unit,
    onDeleteAll: () -> Unit,
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
                                cornerRadius = 8.dp,
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
                        cornerRadius = 8.dp,
                        onClick = { onKeyPressed("0") }
                    )
                    MonicaKeyButton(
                        label = ".",
                        weight = 1f,
                        cornerRadius = 8.dp,
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
                    cornerRadius = 8.dp,
                    onClick = onBackspace,
                    onLongPressRepeat = onBackspace,
                    onSwipeUp = onDeleteAll
                )
                MonicaKeyButtonBase(
                    modifier = Modifier.fillMaxWidth(),
                    label = "",
                    icon = { Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = stringResource(R.string.ime_key_enter)) },
                    style = MonicaKeyStyle.PRIMARY,
                    cornerRadius = 8.dp,
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
    onDeleteAll: () -> Unit,
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
                    cornerRadius = 8.dp,
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
                cornerRadius = 8.dp,
                onClick = { onKeyPressed(key) }
            )
        }
        MonicaKeyButton(
            label = "",
            icon = { Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.ime_key_delete)) },
            weight = 2f,
            style = MonicaKeyStyle.ACCENT,
            cornerRadius = 8.dp,
            onClick = onBackspace,
            onLongPressRepeat = onBackspace,
            onSwipeUp = onDeleteAll
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
            cornerRadius = 8.dp,
            onClick = { onKeyPressed(",") }
        )
        MonicaKeyButton(
            label = "",
            icon = { Icon(Icons.Default.SpaceBar, contentDescription = stringResource(R.string.ime_key_space)) },
            weight = 3.9f,
            cornerRadius = 8.dp,
            onClick = { onKeyPressed(" ") },
            onLongPressRepeat = { onKeyPressed(" ") }
        )
        MonicaKeyButton(
            label = ".",
            weight = 0.95f,
            cornerRadius = 8.dp,
            onClick = { onKeyPressed(".") }
        )
        MonicaKeyButton(
            label = "",
            icon = { Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = stringResource(R.string.ime_key_enter)) },
            weight = 1.8f,
            style = MonicaKeyStyle.PRIMARY,
            cornerRadius = 8.dp,
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
        cornerRadius = 8.dp,
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
    onLongPressRepeat: (() -> Unit)? = null,
    onSwipeUp: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null
) {
    var pressed by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
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

    Box(
        modifier = modifier
            .height(height)
            .zIndex(if (pressed) 2f else 0f)
            .pointerInput(onClick, onLongPressRepeat, onSwipeUp) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    var lastY = down.position.y
                    var didRepeat = false
                    val repeatJob = onLongPressRepeat?.let { repeatAction ->
                        coroutineScope.launch {
                            delay(360)
                            while (true) {
                                didRepeat = true
                                repeatAction()
                                delay(58)
                            }
                        }
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                        lastY = change.position.y
                        if (!change.pressed) {
                            break
                        }
                    }

                    repeatJob?.cancel()
                    pressed = false
                    val swipeUpDistance = down.position.y - lastY
                    when {
                        onSwipeUp != null && swipeUpDistance > 32.dp.toPx() -> onSwipeUp()
                        !didRepeat -> onClick()
                    }
                }
            }
    ) {
        if (pressed) {
            KeyPressPreview(
                label = label,
                icon = icon,
                contentColor = contentColor,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-62).dp)
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius)),
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
}

@Composable
private fun KeyPressPreview(
    label: String,
    icon: @Composable (() -> Unit)?,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(width = 64.dp, height = 70.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 8.dp,
        tonalElevation = 6.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    icon()
                }
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
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
    onLongPressRepeat: (() -> Unit)? = null,
    onSwipeUp: (() -> Unit)? = null,
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
        onLongPressRepeat = onLongPressRepeat,
        onSwipeUp = onSwipeUp,
        content = content
    )
}
