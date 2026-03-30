package takagi.ru.monica.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.fragment.app.FragmentActivity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import takagi.ru.monica.R
import takagi.ru.monica.data.AddButtonBehaviorMode
import takagi.ru.monica.data.AddButtonMenuAction
import takagi.ru.monica.data.BottomNavContentTab
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordPageContentType
import takagi.ru.monica.data.PasswordQuickAccessManager
import takagi.ru.monica.data.model.PasskeyBindingCodec
import takagi.ru.monica.data.model.TimelineEvent
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel
import takagi.ru.monica.viewmodel.TotpViewModel
import takagi.ru.monica.viewmodel.CategoryFilter
import takagi.ru.monica.data.Category
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.viewmodel.GeneratorViewModel
import takagi.ru.monica.viewmodel.GeneratorType
import takagi.ru.monica.viewmodel.NoteViewModel
import takagi.ru.monica.viewmodel.PasskeyViewModel
import takagi.ru.monica.viewmodel.TimelineViewModel
import takagi.ru.monica.ui.screens.SettingsScreen
import takagi.ru.monica.ui.screens.GeneratorScreen  // 添加生成器页面导入
import takagi.ru.monica.ui.screens.NoteListScreen
import takagi.ru.monica.ui.screens.NoteListContent
import takagi.ru.monica.ui.screens.PasswordDetailScreen
import takagi.ru.monica.ui.screens.SendScreen
import takagi.ru.monica.ui.screens.CardWalletScreen
import takagi.ru.monica.ui.screens.CardWalletTab
import takagi.ru.monica.ui.screens.BankCardDetailScreen
import takagi.ru.monica.ui.screens.DocumentDetailScreen
import takagi.ru.monica.ui.screens.HistoryTab
import takagi.ru.monica.ui.screens.TimelineScreen
import takagi.ru.monica.ui.screens.PasskeyListScreen
import takagi.ru.monica.ui.gestures.SwipeActions
import takagi.ru.monica.ui.haptic.rememberHapticFeedback
import kotlin.math.absoluteValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import takagi.ru.monica.ui.components.QrCodeDialog
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.components.DraggableBottomNavScaffold
import takagi.ru.monica.ui.components.SwipeableAddFab
import takagi.ru.monica.ui.components.DraggableNavItem
import takagi.ru.monica.ui.components.QuickActionItem
import takagi.ru.monica.ui.components.QuickAddCallback
import takagi.ru.monica.ui.components.SyncStatusIcon
import takagi.ru.monica.ui.components.M3IdentityVerifyDialog
import takagi.ru.monica.ui.components.PasswordQuickAccessItem
import takagi.ru.monica.ui.components.PasswordQuickAccessSheet
import takagi.ru.monica.ui.components.UnifiedCategoryFilterBottomSheet
import takagi.ru.monica.ui.components.UnifiedCategoryFilterSelection
import takagi.ru.monica.ui.components.UnifiedMoveCategoryTarget
import takagi.ru.monica.ui.components.UnifiedMoveToCategoryBottomSheet
import takagi.ru.monica.ui.common.dialog.DeleteConfirmDialog
import takagi.ru.monica.ui.common.layout.DetailPane
import takagi.ru.monica.ui.common.layout.InspectorRow
import takagi.ru.monica.ui.common.layout.ListPane
import takagi.ru.monica.ui.common.pull.PullActionVisualState
import takagi.ru.monica.ui.common.pull.PullGestureIndicator
import takagi.ru.monica.ui.common.pull.rememberPullActionState
import takagi.ru.monica.ui.common.selection.CategoryListItem
import takagi.ru.monica.ui.common.selection.SelectionActionBar
import takagi.ru.monica.ui.common.selection.SelectionModeTopBar
import takagi.ru.monica.ui.main.navigation.BottomNavItem
import takagi.ru.monica.ui.main.navigation.fullLabelRes
import takagi.ru.monica.ui.main.navigation.indexToDefaultTabKey
import takagi.ru.monica.ui.main.navigation.shortLabelRes
import takagi.ru.monica.ui.main.navigation.toBottomNavItem
import takagi.ru.monica.ui.main.layout.AdaptiveMainScaffold
import takagi.ru.monica.ui.password.buildAdditionalInfoPreview
import takagi.ru.monica.ui.password.MultiPasswordEntryCard
import takagi.ru.monica.ui.password.StackedPasswordGroup
import takagi.ru.monica.ui.password.PasswordEntryCard
import takagi.ru.monica.ui.password.StackCardMode
import takagi.ru.monica.ui.password.resolvePasswordPageVisibleTypes
import takagi.ru.monica.ui.password.sanitizeSelectedPasswordPageTypes
import takagi.ru.monica.ui.password.PasswordListAggregateConfig
import takagi.ru.monica.ui.password.getGroupKeyForMode
import takagi.ru.monica.ui.password.getPasswordGroupTitle
import takagi.ru.monica.ui.password.getPasswordInfoKey
import takagi.ru.monica.ui.vaultv2.VaultV2Pane
import takagi.ru.monica.data.bitwarden.BitwardenPendingOperation
import takagi.ru.monica.data.bitwarden.BitwardenSend
import takagi.ru.monica.bitwarden.sync.SyncBlockReason
import takagi.ru.monica.bitwarden.sync.SyncStatus
import takagi.ru.monica.bitwarden.sync.VaultSyncStatus
import takagi.ru.monica.security.SecurityManager
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import takagi.ru.monica.ui.screens.AddEditPasswordScreen
import takagi.ru.monica.ui.screens.AddEditTotpScreen
import takagi.ru.monica.ui.screens.AddEditBankCardScreen
import takagi.ru.monica.ui.screens.AddEditDocumentScreen
import takagi.ru.monica.ui.screens.AddEditNoteScreen
import takagi.ru.monica.ui.screens.AddEditSendScreen
import takagi.ru.monica.ui.theme.MonicaTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class PasswordHistoryPageMode(val tab: HistoryTab?) {
    NONE(null),
    TIMELINE(HistoryTab.TIMELINE),
    TRASH(HistoryTab.TRASH)
}

private val PasswordHistoryPageMode.isVisible: Boolean
    get() = this != PasswordHistoryPageMode.NONE

private val passwordPageContentTypeSetSaver = Saver<Set<PasswordPageContentType>, ArrayList<String>>(
    save = { selectedTypes ->
        ArrayList(selectedTypes.map(PasswordPageContentType::name))
    },
    restore = { savedNames ->
        savedNames
            .mapNotNull { name -> PasswordPageContentType.entries.find { it.name == name } }
            .toSet()
    }
)

private fun togglePasswordPageContentType(
    currentTypes: Set<PasswordPageContentType>,
    toggledType: PasswordPageContentType,
    visibleTypes: List<PasswordPageContentType>
): Set<PasswordPageContentType> {
    val nextTypes = if (toggledType in currentTypes) {
        currentTypes - toggledType
    } else {
        currentTypes + toggledType
    }
    return sanitizeSelectedPasswordPageTypes(
        visibleTypes = visibleTypes,
        selectedTypes = nextTypes
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedWalletAddScreen(
    selectedType: CardWalletTab,
    onTypeSelected: (CardWalletTab) -> Unit,
    onNavigateBack: () -> Unit,
    bankCardViewModel: BankCardViewModel,
    documentViewModel: DocumentViewModel,
    stateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    initialCategoryId: Long? = null,
    initialKeePassDatabaseId: Long? = null,
    initialKeePassGroupPath: String? = null,
    initialBitwardenVaultId: Long? = null,
    initialBitwardenFolderId: String? = null,
    modifier: Modifier = Modifier
) {
    var isFavorite by remember { mutableStateOf(false) }
    var canSave by remember { mutableStateOf(false) }
    var onSaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var onToggleFavoriteAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val titleRes = when (selectedType) {
        CardWalletTab.DOCUMENTS -> R.string.add_document_title
        else -> R.string.add_bank_card_title
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(titleRes)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                onToggleFavoriteAction?.invoke()
                            },
                            enabled = onToggleFavoriteAction != null
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = stringResource(R.string.favorite),
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType != CardWalletTab.DOCUMENTS,
                        onClick = { onTypeSelected(CardWalletTab.BANK_CARDS) },
                        label = { Text(stringResource(R.string.quick_action_add_card)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CreditCard,
                                contentDescription = null
                            )
                        }
                    )
                    FilterChip(
                        selected = selectedType == CardWalletTab.DOCUMENTS,
                        onClick = { onTypeSelected(CardWalletTab.DOCUMENTS) },
                        label = { Text(stringResource(R.string.quick_action_add_document)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Badge,
                                contentDescription = null
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onSaveAction?.invoke() },
                containerColor = if (canSave) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (canSave) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            ) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (selectedType == CardWalletTab.DOCUMENTS) {
                stateHolder.SaveableStateProvider("wallet_add_document") {
                    AddEditDocumentScreen(
                        viewModel = documentViewModel,
                        documentId = null,
                        onNavigateBack = onNavigateBack,
                        initialCategoryId = initialCategoryId,
                        initialKeePassDatabaseId = initialKeePassDatabaseId,
                        initialKeePassGroupPath = initialKeePassGroupPath,
                        initialBitwardenVaultId = initialBitwardenVaultId,
                        initialBitwardenFolderId = initialBitwardenFolderId,
                        showTopBar = false,
                        showFab = false,
                        onFavoriteStateChanged = { isFavorite = it },
                        onCanSaveChanged = { canSave = it },
                        onSaveActionChanged = { onSaveAction = it },
                        onToggleFavoriteActionChanged = { onToggleFavoriteAction = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                stateHolder.SaveableStateProvider("wallet_add_bank") {
                    AddEditBankCardScreen(
                        viewModel = bankCardViewModel,
                        cardId = null,
                        onNavigateBack = onNavigateBack,
                        initialCategoryId = initialCategoryId,
                        initialKeePassDatabaseId = initialKeePassDatabaseId,
                        initialKeePassGroupPath = initialKeePassGroupPath,
                        initialBitwardenVaultId = initialBitwardenVaultId,
                        initialBitwardenFolderId = initialBitwardenFolderId,
                        showTopBar = false,
                        showFab = false,
                        onFavoriteStateChanged = { isFavorite = it },
                        onCanSaveChanged = { canSave = it },
                        onSaveActionChanged = { onSaveAction = it },
                        onToggleFavoriteActionChanged = { onToggleFavoriteAction = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private data class NewItemStorageDefaults(
    val categoryId: Long? = null,
    val keepassDatabaseId: Long? = null,
    val keepassGroupPath: String? = null,
    val bitwardenVaultId: Long? = null,
    val bitwardenFolderId: String? = null
)

private fun NewItemStorageDefaults.hasAnyValue(): Boolean {
    return categoryId != null ||
        keepassDatabaseId != null ||
        !keepassGroupPath.isNullOrBlank() ||
        bitwardenVaultId != null ||
        !bitwardenFolderId.isNullOrBlank()
}

private fun defaultsFromTotpFilter(filter: takagi.ru.monica.viewmodel.TotpCategoryFilter): NewItemStorageDefaults {
    return when (filter) {
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.Custom -> {
            NewItemStorageDefaults(categoryId = filter.categoryId)
        }
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabase -> {
            NewItemStorageDefaults(keepassDatabaseId = filter.databaseId)
        }
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassGroupFilter -> {
            NewItemStorageDefaults(
                keepassDatabaseId = filter.databaseId,
                keepassGroupPath = filter.groupPath
            )
        }
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVault -> {
            NewItemStorageDefaults(bitwardenVaultId = filter.vaultId)
        }
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenFolderFilter -> {
            NewItemStorageDefaults(
                bitwardenVaultId = filter.vaultId,
                bitwardenFolderId = filter.folderId
            )
        }
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseStarred -> {
            NewItemStorageDefaults(keepassDatabaseId = filter.databaseId)
        }
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseUncategorized -> {
            NewItemStorageDefaults(keepassDatabaseId = filter.databaseId)
        }
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultStarred -> {
            NewItemStorageDefaults(bitwardenVaultId = filter.vaultId)
        }
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultUncategorized -> {
            NewItemStorageDefaults(bitwardenVaultId = filter.vaultId)
        }
        else -> NewItemStorageDefaults()
    }
}

private fun defaultsFromPasswordFilter(filter: CategoryFilter): NewItemStorageDefaults {
    return when (filter) {
        is CategoryFilter.Custom -> {
            NewItemStorageDefaults(categoryId = filter.categoryId)
        }
        is CategoryFilter.KeePassDatabase -> {
            NewItemStorageDefaults(keepassDatabaseId = filter.databaseId)
        }
        is CategoryFilter.KeePassGroupFilter -> {
            NewItemStorageDefaults(
                keepassDatabaseId = filter.databaseId,
                keepassGroupPath = filter.groupPath
            )
        }
        is CategoryFilter.KeePassDatabaseStarred -> {
            NewItemStorageDefaults(keepassDatabaseId = filter.databaseId)
        }
        is CategoryFilter.KeePassDatabaseUncategorized -> {
            NewItemStorageDefaults(keepassDatabaseId = filter.databaseId)
        }
        is CategoryFilter.BitwardenVault -> {
            NewItemStorageDefaults(bitwardenVaultId = filter.vaultId)
        }
        is CategoryFilter.BitwardenFolderFilter -> {
            NewItemStorageDefaults(
                bitwardenVaultId = filter.vaultId,
                bitwardenFolderId = filter.folderId
            )
        }
        is CategoryFilter.BitwardenVaultStarred -> {
            NewItemStorageDefaults(bitwardenVaultId = filter.vaultId)
        }
        is CategoryFilter.BitwardenVaultUncategorized -> {
            NewItemStorageDefaults(bitwardenVaultId = filter.vaultId)
        }
        else -> NewItemStorageDefaults()
    }
}

private data class BitwardenBottomStatusUiState(
    val messageRes: Int? = null,
    val showProgress: Boolean = false
)

private data class BottomMiniHintMessage(
    val id: Long,
    val message: String
)

private data class MainScreenHandlers(
    val passwordAddOpen: () -> Unit,
    val passwordEditOpen: (Long) -> Unit,
    val inlinePasswordEditorBack: () -> Unit,
    val totpAddOpen: () -> Unit,
    val inlineTotpEditorBack: () -> Unit,
    val bankCardAddOpen: () -> Unit,
    val bankCardEditOpen: (Long) -> Unit,
    val inlineBankCardEditorBack: () -> Unit,
    val documentAddOpen: () -> Unit,
    val documentEditOpen: (Long) -> Unit,
    val inlineDocumentEditorBack: () -> Unit,
    val walletAddOpen: () -> Unit,
    val noteOpen: (Long?) -> Unit,
    val inlineNoteEditorBack: () -> Unit,
    val passwordDetailOpen: (Long) -> Unit,
    val totpOpen: (Long) -> Unit,
    val bankCardOpen: (Long) -> Unit,
    val documentOpen: (Long) -> Unit,
    val passkeyOpen: (PasskeyEntry) -> Unit,
    val passkeyUnbind: (PasskeyEntry) -> Unit,
    val confirmPasskeyDelete: () -> Unit,
    val sendOpen: (BitwardenSend) -> Unit,
    val sendAddOpen: () -> Unit,
    val inlineSendEditorBack: () -> Unit,
    val timelineLogOpen: (TimelineEvent.StandardLog) -> Unit
)

// Keep this file as orchestration layer: state wiring + tab routing + pane transitions.
// Feature-specific rendering should stay in dedicated composables/files.
private const val MAX_BOTTOM_MINI_HINTS = 2

@Composable
private fun BottomMiniHintBubble(
    visible: Boolean,
    text: String,
    containerColor: Color,
    textColor: Color
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(animationSpec = tween(180)) + scaleIn(
            initialScale = 0.92f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        exit = slideOutVertically(
            targetOffsetY = { it / 3 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeOut(animationSpec = tween(160)) + scaleOut(
            targetScale = 0.96f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 2.dp,
            color = containerColor
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

private fun isBitwardenPasswordFilter(filter: CategoryFilter): Boolean = when (filter) {
    is CategoryFilter.BitwardenVault,
    is CategoryFilter.BitwardenFolderFilter,
    is CategoryFilter.BitwardenVaultStarred,
    is CategoryFilter.BitwardenVaultUncategorized -> true
    else -> false
}

private fun isBitwardenTotpFilter(filter: takagi.ru.monica.viewmodel.TotpCategoryFilter): Boolean = when (filter) {
    is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVault,
    is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenFolderFilter,
    is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultStarred,
    is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultUncategorized -> true
    else -> false
}

private fun resolveTrashScopeKeyFromPasswordFilter(filter: CategoryFilter): String {
    return when (filter) {
        is CategoryFilter.BitwardenVault -> "bitwarden_${filter.vaultId}"
        is CategoryFilter.BitwardenFolderFilter -> "bitwarden_${filter.vaultId}"
        is CategoryFilter.BitwardenVaultStarred -> "bitwarden_${filter.vaultId}"
        is CategoryFilter.BitwardenVaultUncategorized -> "bitwarden_${filter.vaultId}"
        is CategoryFilter.KeePassDatabase -> "keepass_${filter.databaseId}"
        is CategoryFilter.KeePassGroupFilter -> "keepass_${filter.databaseId}"
        is CategoryFilter.KeePassDatabaseStarred -> "keepass_${filter.databaseId}"
        is CategoryFilter.KeePassDatabaseUncategorized -> "keepass_${filter.databaseId}"
        else -> "local"
    }
}

private fun resolveBitwardenBottomStatusUiState(
    status: VaultSyncStatus?,
    nowMs: Long
): BitwardenBottomStatusUiState? {
    if (status == null) return null

    if (status.blockedReason == SyncBlockReason.AUTH_REQUIRED) {
        return BitwardenBottomStatusUiState(
            messageRes = R.string.bitwarden_status_auth_required
        )
    }
    if (status.blockedReason == SyncBlockReason.VAULT_LOCKED) {
        return BitwardenBottomStatusUiState(
            messageRes = R.string.bitwarden_status_wait_unlock
        )
    }
    if (status.nextRetryAt != null) {
        return BitwardenBottomStatusUiState(
            messageRes = R.string.bitwarden_status_retrying
        )
    }
    if (status.lastError != null) {
        return BitwardenBottomStatusUiState(
            messageRes = R.string.sync_status_failed_short
        )
    }
    if (status.isRunning) {
        return BitwardenBottomStatusUiState(
            messageRes = R.string.sync_status_syncing_short,
            showProgress = true
        )
    }
    if (status.queuedReason != null) {
        return BitwardenBottomStatusUiState(
            messageRes = R.string.bitwarden_status_queued,
            showProgress = true
        )
    }
    val lastSuccess = status.lastSuccessAt
    if (lastSuccess != null && nowMs - lastSuccess in 0..3000L) {
        return BitwardenBottomStatusUiState(
            messageRes = R.string.bitwarden_status_synced
        )
    }

    return null
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun TimelineDetailPane(
    selectedLog: TimelineEvent.StandardLog,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.history),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = selectedLog.summary,
            style = MaterialTheme.typography.titleMedium
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("类型：${selectedLog.itemType.ifBlank { "-" }}")
                Text("操作：${selectedLog.operationType.ifBlank { "-" }}")
                Text(
                    text = "时间戳：${selectedLog.timestamp}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 带有底部导航的主屏幕
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3WindowSizeClassApi::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class
)
@Composable
fun SimpleMainScreen(
    passwordViewModel: PasswordViewModel,
    settingsViewModel: SettingsViewModel,
    totpViewModel: takagi.ru.monica.viewmodel.TotpViewModel,
    bankCardViewModel: takagi.ru.monica.viewmodel.BankCardViewModel,
    documentViewModel: takagi.ru.monica.viewmodel.DocumentViewModel,
    generatorViewModel: GeneratorViewModel = viewModel(), // 添加GeneratorViewModel
    noteViewModel: NoteViewModel = viewModel(),
    passkeyViewModel: PasskeyViewModel,  // Passkey ViewModel
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    securityManager: SecurityManager,
    onNavigateToAddPassword: (Long?) -> Unit,
    onNavigateToAddTotp: (Long?) -> Unit,
    onNavigateToQuickTotpScan: () -> Unit,
    onNavigateToAddBankCard: (Long?) -> Unit,
    onNavigateToAddDocument: (Long?) -> Unit,
    onNavigateToWalletAdd: (CardWalletTab) -> Unit,
    onPreparePasswordAddStorageDefaults: (Long?, Long?, String?, Long?, String?) -> Unit = { _, _, _, _, _ -> },
    onPrepareTotpAddStorageDefaults: (Long?, Long?, String?, Long?, String?) -> Unit = { _, _, _, _, _ -> },
    onPrepareNoteAddStorageDefaults: (Long?, Long?, String?, Long?, String?) -> Unit = { _, _, _, _, _ -> },
    onPrepareWalletAddStorageDefaults: (Long?, Long?, String?, Long?, String?) -> Unit = { _, _, _, _, _ -> },
    onNavigateToAddNote: (Long?) -> Unit,
    onNavigateToPasswordDetail: (Long) -> Unit = {},
    onNavigateToPasskeyDetail: (String) -> Unit,
    onNavigateToBankCardDetail: (Long) -> Unit, // Add this
    onNavigateToDocumentDetail: (Long) -> Unit, // Keep this
    onNavigateToChangePassword: () -> Unit = {},
    onNavigateToSecurityQuestion: () -> Unit = {},
    onNavigateToSyncBackup: () -> Unit = {},
    onNavigateToAutofill: () -> Unit = {},
    onNavigateToPasskeySettings: () -> Unit = {},
    onNavigateToBottomNavSettings: () -> Unit = {},
    onNavigateToColorScheme: () -> Unit = {},
    onSecurityAnalysis: () -> Unit = {},
    onNavigateToDeveloperSettings: () -> Unit = {},
    onNavigateToPermissionManagement: () -> Unit = {},
    onNavigateToMonicaPlus: () -> Unit = {},
    onNavigateToExtensions: () -> Unit = {},
    onNavigateToCommonAccountTemplates: () -> Unit = {},
    onNavigateToPageCustomization: () -> Unit = {},
    onNavigateToBitwardenLogin: () -> Unit = {},
    onClearAllData: (Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    initialTab: Int = 0
) {

    // --- ViewModel wiring and global app-level state ---
    // Bitwarden ViewModel
    val bitwardenViewModel: takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel = viewModel()
    val timelineViewModel: TimelineViewModel = viewModel()
    
    // 双击返回退出相关状态
    var backPressedOnce by remember { mutableStateOf(false) }
    var passwordHistoryPageMode by rememberSaveable { mutableStateOf(PasswordHistoryPageMode.NONE) }
    var passwordHistoryInitialTrashScopeKey by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bitwardenRepository = remember { takagi.ru.monica.bitwarden.repository.BitwardenRepository.getInstance(context) }
    val appSettings by settingsViewModel.settings.collectAsState()
    val passwordPageVisibleContentTypes = remember(
        appSettings.passwordPageAggregateEnabled,
        appSettings.passwordPageVisibleContentTypes
    ) {
        resolvePasswordPageVisibleTypes(
            aggregateEnabled = appSettings.passwordPageAggregateEnabled,
            configuredTypes = appSettings.passwordPageVisibleContentTypes
        )
    }
    var passwordPageSelectedContentTypes by rememberSaveable(
        stateSaver = passwordPageContentTypeSetSaver
    ) {
        mutableStateOf(emptySet())
    }
    
    // --- Global back behavior ---
    // 处理返回键 - 需要按两次才能退出
    // 只有在没有子页面（如添加页面）打开时才启用
    // FAB 展开状态由内部 SwipeableAddFab 管理，这里不需要干预，除非我们需要在 FAB 展开时拦截返回键
    // 目前 SwipeableAddFab 应该自己处理了返回键（如果有 BackHandler）
    // 为了安全起见，我们只在最外层处理
    BackHandler(enabled = true) {
        if (passwordHistoryPageMode.isVisible) {
            passwordHistoryPageMode = PasswordHistoryPageMode.NONE
            passwordHistoryInitialTrashScopeKey = null
            return@BackHandler
        }
        if (backPressedOnce) {
            // 第二次按返回键,退出应用
            (context as? android.app.Activity)?.finish()
        } else {
            // 第一次按返回键,显示提示
            backPressedOnce = true
            Toast.makeText(
                context,
                context.getString(R.string.press_back_again_to_exit),
                Toast.LENGTH_SHORT
            ).show()
            
            // 2秒后重置状态
            scope.launch {
                delay(2000)
                backPressedOnce = false
            }
        }
    }
    
    // --- Cross-tab selection/action-bar state ---
    // 密码列表的选择模式状态
    var isPasswordSelectionMode by remember { mutableStateOf(false) }
    var selectedPasswordCount by remember { mutableIntStateOf(0) }
    var onExitPasswordSelection by remember { mutableStateOf({}) }
    var onSelectAllPasswords by remember { mutableStateOf({}) }
    var onFavoriteSelectedPasswords by remember { mutableStateOf<(() -> Unit)?>(null) }
    var onMoveToCategoryPasswords by remember { mutableStateOf<(() -> Unit)?>(null) }
    var onManualStackPasswords by remember { mutableStateOf<(() -> Unit)?>(null) }
    var onDeleteSelectedPasswords by remember { mutableStateOf({}) }
    var passwordListShowBackToTop by remember { mutableStateOf(false) }
    var passwordScrollToTopRequestKey by remember { mutableIntStateOf(0) }
    var showPasswordQuickAccessSheet by rememberSaveable { mutableStateOf(false) }
    
    LaunchedEffect(passwordPageVisibleContentTypes) {
        passwordPageSelectedContentTypes = sanitizeSelectedPasswordPageTypes(
            visibleTypes = passwordPageVisibleContentTypes,
            selectedTypes = passwordPageSelectedContentTypes
        )
    }
    
    // 密码分组模式: smart(备注>网站>应用>标题), note, website, app, title
    // 从设置中读取，如果设置中没有则默认为 "smart"
    val passwordGroupMode = appSettings.passwordGroupMode


    // 堆叠卡片显示模式: 自动/始终展开（始终展开指逐条显示，不堆叠）
    // 从设置中读取，如果设置中没有则默认为 AUTO
    val stackCardModeKey = appSettings.stackCardMode
    val stackCardMode = remember(stackCardModeKey) {
        runCatching { StackCardMode.valueOf(stackCardModeKey) }.getOrDefault(StackCardMode.AUTO)
    }
    
    // TOTP的选择模式状态
    var isTotpSelectionMode by remember { mutableStateOf(false) }
    var selectedTotpCount by remember { mutableIntStateOf(0) }
    var onExitTotpSelection by remember { mutableStateOf({}) }
    var onSelectAllTotp by remember { mutableStateOf({}) }
    var onMoveToCategoryTotp by remember { mutableStateOf({}) }
    var onDeleteSelectedTotp by remember { mutableStateOf({}) }
    
    // 证件的选择模式状态
    var isDocumentSelectionMode by remember { mutableStateOf(false) }
    var selectedDocumentCount by remember { mutableIntStateOf(0) }
    var onExitDocumentSelection by remember { mutableStateOf({}) }
    var onSelectAllDocuments by remember { mutableStateOf({}) }
    var onMoveToCategoryDocuments by remember { mutableStateOf({}) }
    var onDeleteSelectedDocuments by remember { mutableStateOf({}) }
    
    // 银行卡的选择模式状态
    var isBankCardSelectionMode by remember { mutableStateOf(false) }
    var selectedBankCardCount by remember { mutableIntStateOf(0) }
    var onExitBankCardSelection by remember { mutableStateOf({}) }
    var onSelectAllBankCards by remember { mutableStateOf({}) }
    var onMoveToCategoryBankCards by remember { mutableStateOf({}) }
    var onDeleteSelectedBankCards by remember { mutableStateOf({}) }
    var onFavoriteBankCards by remember { mutableStateOf({}) }  // 添加收藏回调

    // CardWallet state
    var cardWalletSubTab by rememberSaveable { mutableStateOf(CardWalletTab.ALL) }
    var walletUnifiedAddType by rememberSaveable { mutableStateOf(CardWalletTab.BANK_CARDS) }
    val walletAddSaveableStateHolder = rememberSaveableStateHolder()
    val cardWalletSaveableStateHolder = rememberSaveableStateHolder()

    val bottomNavVisibility = appSettings.bottomNavVisibility

    val dataTabItems = appSettings.bottomNavOrder
        .map { it.toBottomNavItem() }
        .filter { item ->
            val tab = item.contentTab
            tab == null || bottomNavVisibility.isVisible(tab)
        }

    val tabs = buildList {
        addAll(dataTabItems)
        add(BottomNavItem.Settings)
    }

    val defaultTabKey = remember(initialTab, tabs) { 
        if (initialTab == 0 && tabs.isNotEmpty()) {
            tabs.first().key
        } else {
            indexToDefaultTabKey(initialTab) 
        }
    }
    var selectedTabKey by rememberSaveable { mutableStateOf(defaultTabKey) }
    var startupAutoTabApplied by rememberSaveable { mutableStateOf(false) }
    val hasCustomBottomNavConfig = remember(appSettings.bottomNavOrder, bottomNavVisibility) {
        appSettings.bottomNavOrder != BottomNavContentTab.DEFAULT_ORDER ||
            bottomNavVisibility != takagi.ru.monica.data.BottomNavVisibility()
    }

    LaunchedEffect(tabs) {
        if (tabs.none { it.key == selectedTabKey }) {
            selectedTabKey = tabs.first().key
        }
    }
    LaunchedEffect(initialTab, tabs, hasCustomBottomNavConfig, startupAutoTabApplied) {
        if (
            initialTab == 0 &&
            !startupAutoTabApplied &&
            hasCustomBottomNavConfig &&
            tabs.isNotEmpty()
        ) {
            selectedTabKey = tabs.first().key
            startupAutoTabApplied = true
        }
    }

    val currentTab = tabs.firstOrNull { it.key == selectedTabKey } ?: tabs.first()

    var passwordPaneState by rememberSaveable(stateSaver = PasswordPaneUiStateSaver) {
        mutableStateOf(PasswordPaneUiState())
    }
    var totpPaneState by rememberSaveable(stateSaver = TotpPaneUiStateSaver) {
        mutableStateOf(TotpPaneUiState())
    }
    var cardWalletPaneState by rememberSaveable(stateSaver = CardWalletPaneUiStateSaver) {
        mutableStateOf(CardWalletPaneUiState())
    }
    var notePaneState by rememberSaveable(stateSaver = NotePaneUiStateSaver) {
        mutableStateOf(NotePaneUiState())
    }
    var sendPaneState by remember { mutableStateOf(SendPaneUiState()) }
    var selectedPasskey by remember { mutableStateOf<PasskeyEntry?>(null) }
    var pendingPasskeyDelete by remember { mutableStateOf<PasskeyEntry?>(null) }
    var selectedTimelineLog by remember { mutableStateOf<TimelineEvent.StandardLog?>(null) }
    val sendState by bitwardenViewModel.sendState.collectAsState()
    val activeBitwardenVault by bitwardenViewModel.activeVault.collectAsState()
    val bitwardenSyncStatusByVault by bitwardenViewModel.syncStatusByVault.collectAsState()
    val totpFilter by totpViewModel.categoryFilter.collectAsState()
    val totpNewItemDefaults = remember(totpFilter) { defaultsFromTotpFilter(totpFilter) }
    var pendingInlinePasswordAddStorageDefaults by remember { mutableStateOf<NewItemStorageDefaults?>(null) }
    var pendingInlineTotpAddStorageDefaults by remember { mutableStateOf<NewItemStorageDefaults?>(null) }
    var pendingInlineNoteAddStorageDefaults by remember { mutableStateOf<NewItemStorageDefaults?>(null) }
    var pendingInlineWalletAddStorageDefaults by remember { mutableStateOf<NewItemStorageDefaults?>(null) }
    val selectedGeneratorType by generatorViewModel.selectedGenerator.collectAsState()
    val symbolGeneratorResult by generatorViewModel.symbolResult.collectAsState()
    val passwordGeneratorResult by generatorViewModel.passwordResult.collectAsState()
    val passphraseGeneratorResult by generatorViewModel.passphraseResult.collectAsState()
    val pinGeneratorResult by generatorViewModel.pinResult.collectAsState()
    val currentGeneratorResult = when (selectedGeneratorType) {
        GeneratorType.SYMBOL -> symbolGeneratorResult
        GeneratorType.PASSWORD -> passwordGeneratorResult
        GeneratorType.PASSPHRASE -> passphraseGeneratorResult
        GeneratorType.PIN -> pinGeneratorResult
    }

    val selectedPasswordId = passwordPaneState.selectedPasswordId
    val inlinePasswordEditorId = passwordPaneState.inlinePasswordEditorId
    val isAddingPasswordInline = passwordPaneState.isAddingPasswordInline
    val selectedTotpId = totpPaneState.selectedTotpId
    val isAddingTotpInline = totpPaneState.isAddingInline
    val selectedBankCardId = cardWalletPaneState.selectedBankCardId
    val inlineBankCardEditorId = cardWalletPaneState.inlineBankCardEditorId
    val isAddingBankCardInline = cardWalletPaneState.isAddingBankCardInline
    val selectedDocumentId = cardWalletPaneState.selectedDocumentId
    val inlineDocumentEditorId = cardWalletPaneState.inlineDocumentEditorId
    val isAddingDocumentInline = cardWalletPaneState.isAddingDocumentInline
    val inlineNoteEditorId = notePaneState.inlineNoteEditorId
    val isAddingNoteInline = notePaneState.isAddingInline
    val selectedSend = sendPaneState.selectedSend
    val isAddingSendInline = sendPaneState.isAddingInline
    val resetPasswordPaneState: () -> Unit = {
        pendingInlinePasswordAddStorageDefaults = null
        passwordPaneState = PasswordPaneUiStateTransitions.reset()
    }
    val openInlinePasswordAdd: () -> Unit = {
        passwordPaneState = PasswordPaneUiStateTransitions.openInlineAdd()
    }
    val openInlinePasswordEditor: (Long) -> Unit = { passwordId ->
        passwordPaneState = PasswordPaneUiStateTransitions.openInlineEditor(passwordId)
    }
    val openInlinePasswordDetail: (Long) -> Unit = { passwordId ->
        passwordPaneState = PasswordPaneUiStateTransitions.openDetail(passwordId)
    }
    val closeInlinePasswordEditor: () -> Unit = {
        pendingInlinePasswordAddStorageDefaults = null
        passwordPaneState = PasswordPaneUiStateTransitions.closeInlineEditor(passwordPaneState)
    }
    val clearSelectedPasswordPaneItem: () -> Unit = {
        passwordPaneState = PasswordPaneUiStateTransitions.clearSelected(passwordPaneState)
    }
    val resetTotpPaneState: () -> Unit = {
        pendingInlineTotpAddStorageDefaults = null
        totpPaneState = TotpPaneUiStateTransitions.reset()
    }
    val openInlineTotpAdd: () -> Unit = {
        totpPaneState = TotpPaneUiStateTransitions.openInlineAdd()
    }
    val openInlineTotpDetail: (Long) -> Unit = { totpId ->
        totpPaneState = TotpPaneUiStateTransitions.openDetail(totpId)
    }
    val resetCardWalletPaneState: () -> Unit = {
        pendingInlineWalletAddStorageDefaults = null
        cardWalletPaneState = CardWalletPaneUiStateTransitions.resetAll()
    }
    val openInlineBankCardAdd: () -> Unit = {
        cardWalletPaneState = CardWalletPaneUiStateTransitions.openBankCardAddInline()
    }
    val openInlineBankCardEditor: (Long) -> Unit = { cardId ->
        cardWalletPaneState = CardWalletPaneUiStateTransitions.openBankCardEditInline(cardId)
    }
    val openInlineBankCardDetail: (Long) -> Unit = { cardId ->
        cardWalletPaneState = CardWalletPaneUiStateTransitions.openBankCardDetail(cardId)
    }
    val closeInlineBankCardEditor: () -> Unit = {
        pendingInlineWalletAddStorageDefaults = null
        cardWalletPaneState = CardWalletPaneUiStateTransitions.closeBankCardEditor(cardWalletPaneState)
    }
    val clearSelectedBankCardPaneItem: () -> Unit = {
        cardWalletPaneState = CardWalletPaneUiStateTransitions.clearSelectedBankCard(cardWalletPaneState)
    }
    val openInlineDocumentAdd: () -> Unit = {
        cardWalletPaneState = CardWalletPaneUiStateTransitions.openDocumentAddInline()
    }
    val openInlineDocumentEditor: (Long) -> Unit = { documentId ->
        cardWalletPaneState = CardWalletPaneUiStateTransitions.openDocumentEditInline(documentId)
    }
    val openInlineDocumentDetail: (Long) -> Unit = { documentId ->
        cardWalletPaneState = CardWalletPaneUiStateTransitions.openDocumentDetail(documentId)
    }
    val closeInlineDocumentEditor: () -> Unit = {
        pendingInlineWalletAddStorageDefaults = null
        cardWalletPaneState = CardWalletPaneUiStateTransitions.closeDocumentEditor(cardWalletPaneState)
    }
    val clearSelectedDocumentPaneItem: () -> Unit = {
        cardWalletPaneState = CardWalletPaneUiStateTransitions.clearSelectedDocument(cardWalletPaneState)
    }
    val resetDocumentPaneState: () -> Unit = {
        pendingInlineWalletAddStorageDefaults = null
        cardWalletPaneState = CardWalletPaneUiStateTransitions.resetDocumentPane(cardWalletPaneState)
    }
    val resetBankCardPaneState: () -> Unit = {
        pendingInlineWalletAddStorageDefaults = null
        cardWalletPaneState = CardWalletPaneUiStateTransitions.resetBankCardPane(cardWalletPaneState)
    }
    val resetNotePaneState: () -> Unit = {
        pendingInlineNoteAddStorageDefaults = null
        notePaneState = NotePaneUiStateTransitions.reset()
    }
    val openInlineNoteEditor: (Long?) -> Unit = { noteId ->
        notePaneState = NotePaneUiStateTransitions.openInlineEditor(noteId)
    }
    val resetSendPaneState: () -> Unit = {
        sendPaneState = SendPaneUiStateTransitions.reset()
    }
    val openInlineSendDetail: (BitwardenSend) -> Unit = { send ->
        sendPaneState = SendPaneUiStateTransitions.openDetail(send)
    }
    val openInlineSendAdd: () -> Unit = {
        sendPaneState = SendPaneUiStateTransitions.openInlineAdd()
    }
    val closeInlineSendEditor: () -> Unit = {
        sendPaneState = SendPaneUiStateTransitions.closeInlineEditor(sendPaneState)
    }

    // 监听滚动以隐藏/显示 FAB
    var isFabVisible by remember { mutableStateOf(true) }
    
    // 如果设置中禁用了此功能，强制显示 FAB
    LaunchedEffect(appSettings.hideFabOnScroll) {
        if (!appSettings.hideFabOnScroll) {
            isFabVisible = true
        }
    }

    // 监听 FAB 展开状态，展开时禁用隐藏逻辑
    var isFabExpanded by remember { mutableStateOf(false) }
    var isFastScrollStripVisible by rememberSaveable(currentTab) { mutableStateOf(false) }
    var fastScrollIndicatorLabel by rememberSaveable(currentTab.key) { mutableStateOf<String?>(null) }
    // 使用 rememberUpdatedState 确保 currentTab 始终是最新的
    val currentTabState = rememberUpdatedState(currentTab)
    // 确保滚动监听器能获取到最新的设置值
    val hideFabOnScrollState = rememberUpdatedState(appSettings.hideFabOnScroll)
    val fastScrollStripVisibleState = rememberUpdatedState(isFastScrollStripVisible)

    // 检测是否有任何选择模式处于激活状态
    var isNoteSelectionMode by remember { mutableStateOf(false) }
    val isAnySelectionMode = isPasswordSelectionMode || isTotpSelectionMode || isDocumentSelectionMode || isBankCardSelectionMode || isNoteSelectionMode
    var generatorRefreshRequestKey by remember { mutableIntStateOf(0) }
    
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            // 使用 onPostScroll 代替 onPreScroll
            // 只有当子视图实际消费了滚动事件时（即真正滚动了内容），我们才根据方向判断显隐
            // 这样可以解决：
            // 1. 在页面顶部无法上滑时，FAB 不会错误隐藏
            // 2. 内容太少不足以滚动时，FAB 保持显示
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // 如果功能未开启，直接返回
                if (!hideFabOnScrollState.value) return Offset.Zero

                // 如果 FAB 已展开，或当前正在使用快滑条，不要触发自动隐藏
                if (isFabExpanded || fastScrollStripVisibleState.value) return Offset.Zero

                val tab = currentTabState.value
                if (tab == BottomNavItem.Passwords || 
                    tab == BottomNavItem.Authenticator || 
                    tab == BottomNavItem.CardWallet ||
                    tab == BottomNavItem.Generator ||
                    tab == BottomNavItem.Send) {
                    
                    // consumed.y < 0 表示内容向上滚动（手指上滑，查看下方内容） -> 隐藏
                    if (consumed.y < -15f) {
                        isFabVisible = false
                    } 
                    // consumed.y > 0 表示内容向下滚动（手指下滑，回到顶部） -> 显示
                    // 注意：如果是 available.y > 0 但 consumed.y == 0，说明已经到顶滑不动了，
                    // 这种情况下我们也不隐藏（保持原状或强制显示），通常保持原状即可，
                    // 但为了体验，如果在顶部尝试下滑（即使没动），也可以强制显示
                    else if (consumed.y > 15f || (available.y > 0f && consumed.y == 0f)) {
                         isFabVisible = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    val currentFilter by passwordViewModel.categoryFilter.collectAsState()
    val passwordNewItemDefaults = remember(currentFilter) { defaultsFromPasswordFilter(currentFilter) }
    val openHistoryPage: () -> Unit = {
        passwordHistoryInitialTrashScopeKey = null
        passwordHistoryPageMode = PasswordHistoryPageMode.TIMELINE
    }
    val openTrashPage: () -> Unit = {
        passwordHistoryInitialTrashScopeKey = null
        passwordHistoryPageMode = PasswordHistoryPageMode.TRASH
    }
    val closeHistoryPage: () -> Unit = {
        passwordHistoryPageMode = PasswordHistoryPageMode.NONE
        passwordHistoryInitialTrashScopeKey = null
    }
    val isPasskeyDataNeeded = currentTab == BottomNavItem.Passkey ||
        selectedPasskey != null ||
        pendingPasskeyDelete != null
    val isQuickAccessDataNeeded = appSettings.passwordListQuickAccessEnabled || showPasswordQuickAccessSheet
    val shouldCollectAllPasswords = isQuickAccessDataNeeded || isPasskeyDataNeeded
    val allPasswords = if (shouldCollectAllPasswords) {
        passwordViewModel.allPasswordsForUi.collectAsState(initial = emptyList()).value
    } else {
        emptyList()
    }
    val passwordQuickAccessManager = remember(context) { PasswordQuickAccessManager(context) }
    val passwordQuickAccessStats = if (isQuickAccessDataNeeded) {
        passwordQuickAccessManager.statsFlow.collectAsState(initial = emptyMap()).value
    } else {
        emptyMap()
    }
    val passwordQuickAccessItems = remember(
        allPasswords,
        passwordQuickAccessStats,
        isQuickAccessDataNeeded
    ) {
        if (!isQuickAccessDataNeeded) {
            emptyList()
        } else {
            allPasswords.mapNotNull { entry ->
                val stat = passwordQuickAccessStats[entry.id] ?: return@mapNotNull null
                PasswordQuickAccessItem(
                    entry = entry,
                    openCount = stat.openCount,
                    lastOpenedAt = stat.lastOpenedAt
                )
            }
        }
    }
    val recentOpenedPasswords = remember(passwordQuickAccessItems) {
        passwordQuickAccessItems
            .sortedByDescending { it.lastOpenedAt }
            .take(80)
    }
    val frequentOpenedPasswords = remember(passwordQuickAccessItems) {
        passwordQuickAccessItems
            .sortedWith(
                compareByDescending<PasswordQuickAccessItem> { it.openCount }
                    .thenByDescending { it.lastOpenedAt }
            )
            .take(80)
    }
    val localPasskeys = if (isPasskeyDataNeeded) {
        passkeyViewModel.allPasskeys.collectAsState(initial = emptyList()).value
    } else {
        emptyList()
    }
    val passkeyTotalCount = localPasskeys.size
    val passkeyBoundCount = localPasskeys.count { it.boundPasswordId != null }
    val passwordById = remember(allPasswords, isPasskeyDataNeeded) {
        if (isPasskeyDataNeeded) {
            allPasswords.associateBy { it.id }
        } else {
            emptyMap()
        }
    }
    val keepassDatabases by localKeePassViewModel.allDatabases.collectAsState()
    val bitwardenVaults by bitwardenViewModel.vaults.collectAsState()
    // 可拖拽导航栏模式开关 (将来可从设置中读取)
    val useDraggableNav = appSettings.useDraggableBottomNav
    
    // 构建导航项列表 (用于可拖拽导航栏)
    val draggableNavItems = remember(tabs, currentTab) {
        tabs.map { item ->
            DraggableNavItem(
                key = item.key,
                icon = item.icon,
                labelRes = item.shortLabelRes(),
                selected = item.key == currentTab.key,
                onClick = { selectedTabKey = item.key }
            )
        }
    }
    
    val activity = LocalContext.current.findActivity()
    val widthSizeClass = activity?.let { calculateWindowSizeClass(it).widthSizeClass }
    val isCompactWidth = widthSizeClass == null || widthSizeClass == WindowWidthSizeClass.Compact
    val wideListPaneWidth = 400.dp
    val wideNavigationRailWidth = 80.dp
    val wideFabHostWidth = wideNavigationRailWidth + wideListPaneWidth

    // --- Navigation/interaction handlers hub ---
    // This function centralizes open/edit/back intents so tab/pane switching stays consistent.
    fun buildMainScreenHandlers(): MainScreenHandlers {
        val handlePasswordAddOpen: () -> Unit = {
            val resolvedDefaults = passwordNewItemDefaults.takeIf { it.hasAnyValue() }
            if (isCompactWidth) {
                pendingInlinePasswordAddStorageDefaults = null
                onPreparePasswordAddStorageDefaults(
                    resolvedDefaults?.categoryId,
                    resolvedDefaults?.keepassDatabaseId,
                    resolvedDefaults?.keepassGroupPath,
                    resolvedDefaults?.bitwardenVaultId,
                    resolvedDefaults?.bitwardenFolderId
                )
                onNavigateToAddPassword(null)
            } else {
                pendingInlinePasswordAddStorageDefaults = resolvedDefaults
                openInlinePasswordAdd()
            }
        }
        val handlePasswordEditOpen: (Long) -> Unit = { passwordId ->
            if (isCompactWidth) {
                onNavigateToAddPassword(passwordId)
            } else {
                openInlinePasswordEditor(passwordId)
            }
        }
        val handleInlinePasswordEditorBack: () -> Unit = {
            closeInlinePasswordEditor()
        }
        val handleTotpAddOpen: () -> Unit = {
            if (isCompactWidth) {
                onNavigateToAddTotp(null)
            } else {
                openInlineTotpAdd()
            }
        }
        val handleInlineTotpEditorBack: () -> Unit = {
            pendingInlineTotpAddStorageDefaults = null
            resetTotpPaneState()
        }
        val handleBankCardAddOpen: () -> Unit = {
            if (isCompactWidth) {
                onNavigateToAddBankCard(null)
            } else {
                openInlineBankCardAdd()
            }
        }
        val handleBankCardEditOpen: (Long) -> Unit = { cardId ->
            if (isCompactWidth) {
                onNavigateToAddBankCard(cardId)
            } else {
                openInlineBankCardEditor(cardId)
            }
        }
        val handleInlineBankCardEditorBack: () -> Unit = {
            pendingInlineWalletAddStorageDefaults = null
            closeInlineBankCardEditor()
        }
        val handleDocumentAddOpen: () -> Unit = {
            if (isCompactWidth) {
                onNavigateToAddDocument(null)
            } else {
                openInlineDocumentAdd()
            }
        }
        val handleDocumentEditOpen: (Long) -> Unit = { documentId ->
            if (isCompactWidth) {
                onNavigateToAddDocument(documentId)
            } else {
                openInlineDocumentEditor(documentId)
            }
        }
        val handleInlineDocumentEditorBack: () -> Unit = {
            pendingInlineWalletAddStorageDefaults = null
            closeInlineDocumentEditor()
        }
        val handleWalletAddOpen: () -> Unit = {
            when (cardWalletSubTab) {
                CardWalletTab.BANK_CARDS -> handleBankCardAddOpen()
                CardWalletTab.DOCUMENTS -> handleDocumentAddOpen()
                CardWalletTab.ALL -> Unit
            }
        }
        val handleNoteOpen: (Long?) -> Unit = { noteId ->
            if (isCompactWidth) {
                onNavigateToAddNote(noteId)
            } else {
                openInlineNoteEditor(noteId)
            }
        }
        val handleInlineNoteEditorBack: () -> Unit = {
            pendingInlineNoteAddStorageDefaults = null
            resetNotePaneState()
        }
        val handlePasswordDetailOpen: (Long) -> Unit = { passwordId ->
            if (appSettings.passwordListQuickAccessEnabled) {
                scope.launch {
                    passwordQuickAccessManager.recordOpen(passwordId)
                }
            }
            if (isCompactWidth) {
                onNavigateToPasswordDetail(passwordId)
            } else {
                openInlinePasswordDetail(passwordId)
            }
        }
        val handleTotpOpen: (Long) -> Unit = { totpId ->
            if (isCompactWidth) {
                onNavigateToAddTotp(totpId)
            } else {
                openInlineTotpDetail(totpId)
            }
        }
        val handleBankCardOpen: (Long) -> Unit = { cardId ->
            if (isCompactWidth) {
                onNavigateToBankCardDetail(cardId)
            } else {
                openInlineBankCardDetail(cardId)
            }
        }
        val handleDocumentOpen: (Long) -> Unit = { documentId ->
            if (isCompactWidth) {
                onNavigateToDocumentDetail(documentId)
            } else {
                openInlineDocumentDetail(documentId)
            }
        }
        val handlePasskeyOpen: (PasskeyEntry) -> Unit = { passkey ->
            if (isCompactWidth) {
                selectedTabKey = BottomNavItem.Passkey.key
            } else {
                selectedPasskey = passkey
            }
        }
        val handlePasskeyUnbind: (PasskeyEntry) -> Unit = { passkey ->
            val boundId = passkey.boundPasswordId
            if (boundId != null) {
                passwordById[boundId]?.let { entry ->
                    val updatedBindings = PasskeyBindingCodec.removeBinding(
                        entry.passkeyBindings,
                        passkey.credentialId
                    )
                    passwordViewModel.updatePasskeyBindings(boundId, updatedBindings)
                }
            }
            if (passkey.syncStatus != "REFERENCE") {
                passkeyViewModel.updateBoundPassword(passkey.credentialId, null)
            }
            if (selectedPasskey?.credentialId == passkey.credentialId) {
                selectedPasskey = selectedPasskey?.copy(boundPasswordId = null)
            }
        }
        val confirmPasskeyDelete: () -> Unit = {
            val passkey = pendingPasskeyDelete
            if (passkey != null) {
                scope.launch {
                    val boundId = passkey.boundPasswordId
                    if (boundId != null) {
                        passwordById[boundId]?.let { entry ->
                            val updatedBindings = PasskeyBindingCodec.removeBinding(
                                entry.passkeyBindings,
                                passkey.credentialId
                            )
                            passwordViewModel.updatePasskeyBindings(boundId, updatedBindings)
                        }
                    }

                    val isReferenceOnly = passkey.syncStatus == "REFERENCE" &&
                        passkey.privateKeyAlias.isBlank() &&
                        passkey.publicKey.isBlank()
                    if (!isReferenceOnly) {
                        val vaultId = passkey.bitwardenVaultId
                        val cipherId = passkey.bitwardenCipherId
                        if (vaultId != null && !cipherId.isNullOrBlank()) {
                            val queueResult = bitwardenRepository.queueCipherDelete(
                                vaultId = vaultId,
                                cipherId = cipherId,
                                itemType = BitwardenPendingOperation.ITEM_TYPE_PASSKEY
                            )
                            if (queueResult.isFailure) {
                                Toast.makeText(
                                    context,
                                    "Bitwarden 删除入队失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }
                        }
                        passkeyViewModel.deletePasskey(passkey)
                    }
                    if (selectedPasskey?.credentialId == passkey.credentialId) {
                        selectedPasskey = null
                    }
                    pendingPasskeyDelete = null
                }
            }
        }
        val handleSendOpen: (BitwardenSend) -> Unit = { send ->
            if (!isCompactWidth) {
                openInlineSendDetail(send)
            }
        }
        val handleSendAddOpen: () -> Unit = {
            if (!isCompactWidth) {
                openInlineSendAdd()
            }
        }
        val handleInlineSendEditorBack: () -> Unit = {
            closeInlineSendEditor()
        }
        val handleTimelineLogOpen: (TimelineEvent.StandardLog) -> Unit = { log ->
            if (!isCompactWidth) {
                selectedTimelineLog = log
            }
        }

        return MainScreenHandlers(
            passwordAddOpen = handlePasswordAddOpen,
            passwordEditOpen = handlePasswordEditOpen,
            inlinePasswordEditorBack = handleInlinePasswordEditorBack,
            totpAddOpen = handleTotpAddOpen,
            inlineTotpEditorBack = handleInlineTotpEditorBack,
            bankCardAddOpen = handleBankCardAddOpen,
            bankCardEditOpen = handleBankCardEditOpen,
            inlineBankCardEditorBack = handleInlineBankCardEditorBack,
            documentAddOpen = handleDocumentAddOpen,
            documentEditOpen = handleDocumentEditOpen,
            inlineDocumentEditorBack = handleInlineDocumentEditorBack,
            walletAddOpen = handleWalletAddOpen,
            noteOpen = handleNoteOpen,
            inlineNoteEditorBack = handleInlineNoteEditorBack,
            passwordDetailOpen = handlePasswordDetailOpen,
            totpOpen = handleTotpOpen,
            bankCardOpen = handleBankCardOpen,
            documentOpen = handleDocumentOpen,
            passkeyOpen = handlePasskeyOpen,
            passkeyUnbind = handlePasskeyUnbind,
            confirmPasskeyDelete = confirmPasskeyDelete,
            sendOpen = handleSendOpen,
            sendAddOpen = handleSendAddOpen,
            inlineSendEditorBack = handleInlineSendEditorBack,
            timelineLogOpen = handleTimelineLogOpen
        )
    }

    val handlers = buildMainScreenHandlers()
    val handlePasswordAddOpen = handlers.passwordAddOpen
    val handlePasswordEditOpen = handlers.passwordEditOpen
    val handleInlinePasswordEditorBack = handlers.inlinePasswordEditorBack
    val handleTotpAddOpen = handlers.totpAddOpen
    val handleInlineTotpEditorBack = handlers.inlineTotpEditorBack
    val handleBankCardAddOpen = handlers.bankCardAddOpen
    val handleBankCardEditOpen = handlers.bankCardEditOpen
    val handleInlineBankCardEditorBack = handlers.inlineBankCardEditorBack
    val handleDocumentAddOpen = handlers.documentAddOpen
    val handleDocumentEditOpen = handlers.documentEditOpen
    val handleInlineDocumentEditorBack = handlers.inlineDocumentEditorBack
    val handleWalletAddOpen = handlers.walletAddOpen
    val handleNoteOpen = handlers.noteOpen
    val handleInlineNoteEditorBack = handlers.inlineNoteEditorBack
    val handlePasswordDetailOpen = handlers.passwordDetailOpen
    val handleTotpOpen = handlers.totpOpen
    val handleBankCardOpen = handlers.bankCardOpen
    val handleDocumentOpen = handlers.documentOpen
    val handlePasskeyOpen = handlers.passkeyOpen
    val handlePasskeyUnbind = handlers.passkeyUnbind
    val confirmPasskeyDelete = handlers.confirmPasskeyDelete
    val handleSendOpen = handlers.sendOpen
    val handleSendAddOpen = handlers.sendAddOpen
    val handleInlineSendEditorBack = handlers.inlineSendEditorBack
    val handleTimelineLogOpen = handlers.timelineLogOpen

    // --- Tab switch cleanup effects ---
    // Reset detail/editor panes on tab changes to avoid stale selection or mixed mode state.
    MainScreenTabResetEffects(
        currentTab = currentTab,
        isCompactWidth = isCompactWidth,
        cardWalletSubTab = cardWalletSubTab,
        passwordHistoryPageMode = passwordHistoryPageMode,
        onResetPasswordPane = {
            resetPasswordPaneState()
            passwordHistoryPageMode = PasswordHistoryPageMode.NONE
        },
        onHideBackToTop = { passwordListShowBackToTop = false },
        onResetTotpPane = {
            resetTotpPaneState()
        },
        onResetCardWalletPaneAll = {
            resetCardWalletPaneState()
        },
        onResetCardWalletDocumentPane = {
            resetDocumentPaneState()
        },
        onResetCardWalletBankCardPane = {
            resetBankCardPaneState()
        },
        onSyncWalletUnifiedAddType = { walletUnifiedAddType = it },
        onResetNotePane = {
            resetNotePaneState()
        },
        onResetPasskeyPane = { selectedPasskey = null },
        onResetSendPane = {
            resetSendPaneState()
        },
        onResetTimelineSelection = { selectedTimelineLog = null }
    )

    val onCardWalletDocumentSelectionModeChange:
        (Boolean, Int, () -> Unit, () -> Unit, () -> Unit, () -> Unit) -> Unit =
        { isSelectionMode, count, onExit, onSelectAll, onMoveToCategory, onDelete ->
            isDocumentSelectionMode = isSelectionMode
            selectedDocumentCount = count
            onExitDocumentSelection = onExit
            onSelectAllDocuments = onSelectAll
            onMoveToCategoryDocuments = onMoveToCategory
            onDeleteSelectedDocuments = onDelete
        }

    val onCardWalletBankCardSelectionModeChange:
        (Boolean, Int, () -> Unit, () -> Unit, () -> Unit, () -> Unit, () -> Unit) -> Unit =
        { isSelectionMode, count, onExit, onSelectAll, onDelete, onFavorite, onMoveToCategory ->
            isBankCardSelectionMode = isSelectionMode
            selectedBankCardCount = count
            onExitBankCardSelection = onExit
            onSelectAllBankCards = onSelectAll
            onMoveToCategoryBankCards = onMoveToCategory
            onDeleteSelectedBankCards = onDelete
            onFavoriteBankCards = onFavorite
        }

    val cardWalletContentState = CardWalletContentState(
        currentTab = cardWalletSubTab,
        onTabSelected = { cardWalletSubTab = it },
        onCardClick = { cardId ->
            handleBankCardOpen(cardId)
        },
        onDocumentClick = { documentId ->
            handleDocumentOpen(documentId)
        },
        onDocumentSelectionModeChange = onCardWalletDocumentSelectionModeChange,
        onBankCardSelectionModeChange = onCardWalletBankCardSelectionModeChange
    )
    
    val isBitwardenPageContext = when (currentTab) {
        BottomNavItem.VaultV2,
        BottomNavItem.Passwords -> isBitwardenPasswordFilter(currentFilter)
        BottomNavItem.Authenticator -> isBitwardenTotpFilter(totpFilter)
        BottomNavItem.CardWallet,
        BottomNavItem.Notes,
        BottomNavItem.Passkey,
        BottomNavItem.Send -> activeBitwardenVault != null
        else -> false
    }
    val activeVaultSyncState = activeBitwardenVault?.id?.let(bitwardenSyncStatusByVault::get)
    val nowMs by produceState(
        initialValue = System.currentTimeMillis(),
        key1 = activeVaultSyncState?.lastSuccessAt
    ) {
        val lastSuccessAt = activeVaultSyncState?.lastSuccessAt ?: run {
            value = System.currentTimeMillis()
            return@produceState
        }
        while (isActive) {
            val current = System.currentTimeMillis()
            value = current
            if (current - lastSuccessAt > 3000L) break
            delay(250)
        }
    }
    val bottomStatusUiState = resolveBitwardenBottomStatusUiState(
        status = activeVaultSyncState,
        nowMs = nowMs
    )
    val shouldHandleBitwardenStatusVisual =
        appSettings.bitwardenBottomStatusBarEnabled &&
            isBitwardenPageContext &&
            !isAnySelectionMode &&
            !isFabExpanded &&
            bottomStatusUiState != null
    val shouldShowBitwardenSyncIndicator =
        shouldHandleBitwardenStatusVisual && (bottomStatusUiState?.showProgress == true)
    var statusHintVisible by remember { mutableStateOf(false) }
    val activeMiniHints = remember { mutableStateListOf<BottomMiniHintMessage>() }
    val queuedMiniHints = remember { mutableStateListOf<BottomMiniHintMessage>() }
    val dismissingHintIds = remember { mutableStateListOf<Long>() }
    var sendHintSeed by remember { mutableLongStateOf(0L) }

    val syncHintVisible = statusHintVisible && shouldHandleBitwardenStatusVisual && bottomStatusUiState?.messageRes != null
    fun tryActivateQueuedMiniHints() {
        val syncOccupiesSlot = syncHintVisible
        val maxCustomHints = (MAX_BOTTOM_MINI_HINTS - if (syncOccupiesSlot) 1 else 0).coerceAtLeast(0)
        while (activeMiniHints.size < maxCustomHints && queuedMiniHints.isNotEmpty()) {
            val nextHint = queuedMiniHints.removeAt(0)
            activeMiniHints += nextHint
            scope.launch {
                delay(2800L)
                dismissingHintIds += nextHint.id
                delay(280L)
                activeMiniHints.removeAll { it.id == nextHint.id }
                dismissingHintIds.removeAll { it == nextHint.id }
                tryActivateQueuedMiniHints()
            }
        }
    }

    val enqueueMiniHint: (String) -> Unit = { message ->
        val hintId = ++sendHintSeed
        queuedMiniHints += BottomMiniHintMessage(
            id = hintId,
            message = message
        )
        tryActivateQueuedMiniHints()
    }
    val handleSendBitwardenEvent: (takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel.BitwardenEvent) -> Boolean = { event ->
        when (event) {
            is takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel.BitwardenEvent.SendCreated -> {
                enqueueMiniHint(event.message)
                true
            }

            is takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel.BitwardenEvent.SendDeleted -> {
                enqueueMiniHint(event.message)
                true
            }

            else -> false
        }
    }
    LaunchedEffect(shouldHandleBitwardenStatusVisual, bottomStatusUiState?.messageRes) {
        if (!shouldHandleBitwardenStatusVisual || bottomStatusUiState?.messageRes == null) {
            statusHintVisible = false
            return@LaunchedEffect
        }
        statusHintVisible = true
        delay(if (bottomStatusUiState.showProgress) 2600L else 3600L)
        statusHintVisible = false
    }
    LaunchedEffect(syncHintVisible, queuedMiniHints.size, activeMiniHints.size) {
        tryActivateQueuedMiniHints()
    }

    // --- Main surface composition ---
    // Decides draggable nav vs classic scaffold and dispatches per-tab content.
    @Composable
    fun RenderMainSurface() {
    // 根据设置选择导航模式
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        if (useDraggableNav && isCompactWidth) {
        // 使用可拖拽底部导航栏
        DraggableBottomNavScaffold(
            navItems = draggableNavItems,
            statusIndicatorVisible = shouldShowBitwardenSyncIndicator,

            quickAddCallback = QuickAddCallback(
                onAddPassword = { title, username, password ->
                    passwordViewModel.quickAddPassword(title, username, password)
                },
                onAddTotp = { name, secret ->
                    totpViewModel.quickAddTotp(name, secret)
                },
                onAddBankCard = { name, number ->
                    bankCardViewModel.quickAddBankCard(name, number)
                },
                onAddNote = { title, content ->
                    noteViewModel.quickAddNote(title, content)
                }
            ),
            floatingActionButton = {}, // FAB 移至外层 Overlay
            content = { paddingValues ->
                CompactDraggableTabContent(
                    paddingValues = paddingValues,
                    currentTab = currentTab,
                    passwordViewModel = passwordViewModel,
                    settingsViewModel = settingsViewModel,
                    securityManager = securityManager,
                    keepassDatabases = keepassDatabases,
                    bitwardenVaults = bitwardenVaults,
                    localKeePassViewModel = localKeePassViewModel,
                    passwordGroupMode = passwordGroupMode,
                    stackCardMode = stackCardMode,
                    onPasswordOpen = handlePasswordDetailOpen,
                    onBankCardOpen = handleBankCardOpen,
                    onDocumentOpen = handleDocumentOpen,
                    onNoteOpen = { handleNoteOpen(it) },
                    onPasskeyOpen = handlePasskeyOpen,
                    onPasswordSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onFavorite, onMoveToCategory, onStack, onDelete ->
                        isPasswordSelectionMode = isSelectionMode
                        selectedPasswordCount = count
                        onExitPasswordSelection = onExit
                        onSelectAllPasswords = onSelectAll
                        onFavoriteSelectedPasswords = onFavorite
                        onMoveToCategoryPasswords = onMoveToCategory
                        onManualStackPasswords = onStack
                        onDeleteSelectedPasswords = onDelete
                    },
                    onBackToTopVisibilityChange = { visible ->
                        passwordListShowBackToTop = visible
                    },
                    passwordScrollToTopRequestKey = passwordScrollToTopRequestKey,
                    totpViewModel = totpViewModel,
                    onTotpOpen = handleTotpOpen,
                    onNavigateToAddTotp = onNavigateToAddTotp,
                    onNavigateToQuickTotpScan = onNavigateToQuickTotpScan,
                    onTotpSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onMoveToCategory, onDelete ->
                        isTotpSelectionMode = isSelectionMode
                        selectedTotpCount = count
                        onExitTotpSelection = onExit
                        onSelectAllTotp = onSelectAll
                        onMoveToCategoryTotp = onMoveToCategory
                        onDeleteSelectedTotp = onDelete
                    },
                    cardWalletSaveableStateHolder = cardWalletSaveableStateHolder,
                    bankCardViewModel = bankCardViewModel,
                    documentViewModel = documentViewModel,
                    cardWalletContentState = cardWalletContentState,
                    generatorViewModel = generatorViewModel,
                    generatorRefreshRequestKey = generatorRefreshRequestKey,
                    onGeneratorRefreshRequestConsumed = { generatorRefreshRequestKey = 0 },
                    noteViewModel = noteViewModel,
                    onNavigateToAddNote = handleNoteOpen,
                    onNavigateToBankCardDetail = onNavigateToBankCardDetail,
                    onNavigateToDocumentDetail = onNavigateToDocumentDetail,
                    onNavigateToPasskeyDetail = onNavigateToPasskeyDetail,
                    onNoteSelectionModeChange = { isSelectionMode ->
                        isNoteSelectionMode = isSelectionMode
                    },
                    timelineViewModel = timelineViewModel,
                    passkeyViewModel = passkeyViewModel,
                    onNavigateToPasswordDetail = onNavigateToPasswordDetail,
                    bitwardenViewModel = bitwardenViewModel,
                    onSendBitwardenEvent = handleSendBitwardenEvent,
                    onNavigateToChangePassword = onNavigateToChangePassword,
                    onNavigateToSecurityQuestion = onNavigateToSecurityQuestion,
                    onNavigateToSyncBackup = onNavigateToSyncBackup,
                    onNavigateToAutofill = onNavigateToAutofill,
                    onNavigateToPasskeySettings = onNavigateToPasskeySettings,
                    onNavigateToBottomNavSettings = onNavigateToBottomNavSettings,
                    onNavigateToColorScheme = onNavigateToColorScheme,
                    onSecurityAnalysis = onSecurityAnalysis,
                    onNavigateToDeveloperSettings = onNavigateToDeveloperSettings,
                    onNavigateToPermissionManagement = onNavigateToPermissionManagement,
                    onNavigateToMonicaPlus = onNavigateToMonicaPlus,
                    onNavigateToExtensions = onNavigateToExtensions,
                    onNavigateToCommonAccountTemplates = onNavigateToCommonAccountTemplates,
                    onNavigateToPageCustomization = onNavigateToPageCustomization,
                    onClearAllData = onClearAllData,
                    cardWalletSubTab = cardWalletSubTab,
                    passwordHistoryPageMode = passwordHistoryPageMode,
                    passwordHistoryInitialTrashScopeKey = passwordHistoryInitialTrashScopeKey,
                    onOpenHistoryPage = openHistoryPage,
                    onOpenTrashPage = openTrashPage,
                    onCloseHistoryPage = closeHistoryPage,
                    isPasswordSelectionMode = isPasswordSelectionMode,
                    selectedPasswordCount = selectedPasswordCount,
                    onExitPasswordSelection = onExitPasswordSelection,
                    onSelectAllPasswords = onSelectAllPasswords,
                    onFavoriteSelectedPasswords = onFavoriteSelectedPasswords,
                    onMoveToCategoryPasswords = onMoveToCategoryPasswords,
                    onManualStackPasswords = onManualStackPasswords,
                    onDeleteSelectedPasswords = onDeleteSelectedPasswords,
                    isTotpSelectionMode = isTotpSelectionMode,
                    selectedTotpCount = selectedTotpCount,
                    onExitTotpSelection = onExitTotpSelection,
                    onSelectAllTotp = onSelectAllTotp,
                    onMoveToCategoryTotp = onMoveToCategoryTotp,
                    onDeleteSelectedTotp = onDeleteSelectedTotp,
                    isBankCardSelectionMode = isBankCardSelectionMode,
                    selectedBankCardCount = selectedBankCardCount,
                    onExitBankCardSelection = onExitBankCardSelection,
                    onSelectAllBankCards = onSelectAllBankCards,
                    onFavoriteBankCards = onFavoriteBankCards,
                    onMoveToCategoryBankCards = onMoveToCategoryBankCards,
                    onDeleteSelectedBankCards = onDeleteSelectedBankCards,
                    isDocumentSelectionMode = isDocumentSelectionMode,
                    selectedDocumentCount = selectedDocumentCount,
                    onExitDocumentSelection = onExitDocumentSelection,
                    onSelectAllDocuments = onSelectAllDocuments,
                    onMoveToCategoryDocuments = onMoveToCategoryDocuments,
                    onDeleteSelectedDocuments = onDeleteSelectedDocuments,
                )
            }
        )
    } else {
        // 使用传统底部导航栏
    Scaffold(
        topBar = {
            // 顶部栏由各自页面内部控制（如 ExpressiveTopBar），这里保持为空以避免叠加
        },
        contentWindowInsets = if (isCompactWidth) {
            ScaffoldDefaults.contentWindowInsets
        } else {
            WindowInsets(0, 0, 0, 0)
        },
        bottomBar = {
            if (isCompactWidth) {
                Column {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = shouldShowBitwardenSyncIndicator,
                        enter = slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ) + fadeIn(),
                        exit = slideOutVertically(
                            targetOffsetY = { it / 2 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ) + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    }

                    NavigationBar(
                        tonalElevation = 0.dp,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        tabs.forEach { item ->
                            val label = stringResource(item.shortLabelRes())
                            NavigationBarItem(
                                icon = {
                                    Icon(item.icon, contentDescription = label)
                                },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                selected = item.key == currentTab.key,
                                onClick = { selectedTabKey = item.key }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {} // FAB 移至外层 Overlay
    ) { paddingValues ->
        val scaffoldBody: @Composable BoxScope.() -> Unit = {
            when (currentTab) {
                BottomNavItem.VaultV2 -> {
                    VaultV2Pane(
                        passwordViewModel = passwordViewModel,
                        totpViewModel = totpViewModel,
                        bankCardViewModel = bankCardViewModel,
                        documentViewModel = documentViewModel,
                        noteViewModel = noteViewModel,
                        passkeyViewModel = passkeyViewModel,
                        onOpenPassword = handlePasswordDetailOpen,
                        onOpenTotp = handleTotpOpen,
                        onOpenBankCard = handleBankCardOpen,
                        onOpenDocument = handleDocumentOpen,
                        onOpenNote = { handleNoteOpen(it) },
                        onOpenPasskey = handlePasskeyOpen,
                        onBackToTopVisibilityChange = { visible ->
                            passwordListShowBackToTop = visible
                        },
                        onFastScrollSectionLabelChange = { label ->
                            fastScrollIndicatorLabel = label
                        },
                        scrollToTopRequestKey = passwordScrollToTopRequestKey,
                        appSettings = appSettings,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                BottomNavItem.Passwords -> {
                    PasswordTabPane(
                        isCompactWidth = isCompactWidth,
                        wideListPaneWidth = wideListPaneWidth,
                        passwordViewModel = passwordViewModel,
                        settingsViewModel = settingsViewModel,
                        securityManager = securityManager,
                        keepassDatabases = keepassDatabases,
                        bitwardenVaults = bitwardenVaults,
                        localKeePassViewModel = localKeePassViewModel,
                        timelineViewModel = timelineViewModel,
                        groupMode = passwordGroupMode,
                        stackCardMode = stackCardMode,
                        visibleContentTypes = passwordPageVisibleContentTypes,
                        selectedContentTypes = passwordPageSelectedContentTypes,
                        onToggleContentType = { type ->
                            passwordPageSelectedContentTypes = togglePasswordPageContentType(
                                currentTypes = passwordPageSelectedContentTypes,
                                toggledType = type,
                                visibleTypes = passwordPageVisibleContentTypes
                            )
                        },
                        onPasswordOpen = handlePasswordDetailOpen,
                        onNavigateToAddTotp = onNavigateToAddTotp,
                        onNavigateToBankCardDetail = onNavigateToBankCardDetail,
                        onNavigateToDocumentDetail = onNavigateToDocumentDetail,
                        onNavigateToAddNote = handleNoteOpen,
                        onNavigateToPasskeyDetail = onNavigateToPasskeyDetail,
                        onOpenHistoryPage = openHistoryPage,
                        onOpenTrashPage = openTrashPage,
                        onOpenCommonAccountTemplatesPage = onNavigateToCommonAccountTemplates,
                        onCloseHistoryPage = closeHistoryPage,
                        passwordHistoryPageMode = passwordHistoryPageMode,
                        passwordHistoryInitialTrashScopeKey = passwordHistoryInitialTrashScopeKey,
                        onTimelineLogSelected = handleTimelineLogOpen,
                        onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onFavorite, onMoveToCategory, onStack, onDelete ->
                            isPasswordSelectionMode = isSelectionMode
                            selectedPasswordCount = count
                            onExitPasswordSelection = onExit
                            onSelectAllPasswords = onSelectAll
                            onFavoriteSelectedPasswords = onFavorite
                            onMoveToCategoryPasswords = onMoveToCategory
                            onManualStackPasswords = onStack
                            onDeleteSelectedPasswords = onDelete
                        },
                        onBackToTopVisibilityChange = { visible ->
                            passwordListShowBackToTop = visible
                        },
                        scrollToTopRequestKey = passwordScrollToTopRequestKey,
                        isAddingPasswordInline = isAddingPasswordInline,
                        inlinePasswordEditorId = inlinePasswordEditorId,
                        selectedPasswordId = selectedPasswordId,
                        passwordNewItemDefaults = pendingInlinePasswordAddStorageDefaults ?: passwordNewItemDefaults,
                        onInlinePasswordEditorBack = handleInlinePasswordEditorBack,
                        totpViewModel = totpViewModel,
                        bankCardViewModel = bankCardViewModel,
                        noteViewModel = noteViewModel,
                        documentViewModel = documentViewModel,
                        passkeyViewModel = passkeyViewModel,
                        disablePasswordVerification = appSettings.disablePasswordVerification,
                        biometricEnabled = appSettings.biometricEnabled,
                        iconCardsEnabled = appSettings.iconCardsEnabled && appSettings.passwordPageIconEnabled,
                        unmatchedIconHandlingStrategy = appSettings.unmatchedIconHandlingStrategy,
                        onClearSelectedPassword = clearSelectedPasswordPaneItem,
                        onEditPassword = handlePasswordEditOpen
                    )
                }
                BottomNavItem.Authenticator -> {
                    AuthenticatorTabPane(
                        isCompactWidth = isCompactWidth,
                        wideListPaneWidth = wideListPaneWidth,
                        totpViewModel = totpViewModel,
                        passwordViewModel = passwordViewModel,
                        localKeePassViewModel = localKeePassViewModel,
                        onTotpOpen = handleTotpOpen,
                        onNavigateToQuickTotpScan = onNavigateToQuickTotpScan,
                        onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onMoveToCategory, onDelete ->
                            isTotpSelectionMode = isSelectionMode
                            selectedTotpCount = count
                            onExitTotpSelection = onExit
                            onSelectAllTotp = onSelectAll
                            onMoveToCategoryTotp = onMoveToCategory
                            onDeleteSelectedTotp = onDelete
                        },
                        isAddingTotpInline = isAddingTotpInline,
                        selectedTotpId = selectedTotpId,
                        totpNewItemDefaults = pendingInlineTotpAddStorageDefaults ?: totpNewItemDefaults,
                        onInlineTotpEditorBack = handleInlineTotpEditorBack
                    )
                }
                BottomNavItem.CardWallet -> {
                    CardWalletPane(
                        isCompactWidth = isCompactWidth,
                        wideListPaneWidth = wideListPaneWidth,
                        saveableStateHolder = cardWalletSaveableStateHolder,
                        bankCardViewModel = bankCardViewModel,
                        documentViewModel = documentViewModel,
                        contentState = cardWalletContentState,
                        isAddingBankCardInline = isAddingBankCardInline,
                        inlineBankCardEditorId = inlineBankCardEditorId,
                        onInlineBankCardEditorBack = handleInlineBankCardEditorBack,
                        isAddingDocumentInline = isAddingDocumentInline,
                        inlineDocumentEditorId = inlineDocumentEditorId,
                        onInlineDocumentEditorBack = handleInlineDocumentEditorBack,
                        selectedBankCardId = selectedBankCardId,
                        onClearSelectedBankCard = clearSelectedBankCardPaneItem,
                        onEditBankCard = handleBankCardEditOpen,
                        selectedDocumentId = selectedDocumentId,
                        onClearSelectedDocument = clearSelectedDocumentPaneItem,
                        onEditDocument = handleDocumentEditOpen,
                        initialCategoryId = pendingInlineWalletAddStorageDefaults?.categoryId,
                        initialKeePassDatabaseId = pendingInlineWalletAddStorageDefaults?.keepassDatabaseId,
                        initialKeePassGroupPath = pendingInlineWalletAddStorageDefaults?.keepassGroupPath,
                        initialBitwardenVaultId = pendingInlineWalletAddStorageDefaults?.bitwardenVaultId,
                        initialBitwardenFolderId = pendingInlineWalletAddStorageDefaults?.bitwardenFolderId
                    )
                }
                BottomNavItem.Generator -> {
                    GeneratorPane(
                        isCompactWidth = isCompactWidth,
                        wideListPaneWidth = wideListPaneWidth,
                        generatorViewModel = generatorViewModel,
                        passwordViewModel = passwordViewModel,
                        externalRefreshRequestKey = generatorRefreshRequestKey,
                        onRefreshRequestConsumed = { generatorRefreshRequestKey = 0 },
                        selectedGenerator = selectedGeneratorType,
                        generatedValue = currentGeneratorResult
                    )
                }
                BottomNavItem.Notes -> {
                    NotePane(
                        isCompactWidth = isCompactWidth,
                        wideListPaneWidth = wideListPaneWidth,
                        noteViewModel = noteViewModel,
                        settingsViewModel = settingsViewModel,
                        securityManager = securityManager,
                        onNavigateToAddNote = handleNoteOpen,
                        onSelectionModeChange = { isSelectionMode ->
                            isNoteSelectionMode = isSelectionMode
                        },
                        isAddingNoteInline = isAddingNoteInline,
                        inlineNoteEditorId = inlineNoteEditorId,
                        onInlineNoteEditorBack = handleInlineNoteEditorBack,
                        initialCategoryId = pendingInlineNoteAddStorageDefaults?.categoryId,
                        initialKeePassDatabaseId = pendingInlineNoteAddStorageDefaults?.keepassDatabaseId,
                        initialKeePassGroupPath = pendingInlineNoteAddStorageDefaults?.keepassGroupPath,
                        initialBitwardenVaultId = pendingInlineNoteAddStorageDefaults?.bitwardenVaultId,
                        initialBitwardenFolderId = pendingInlineNoteAddStorageDefaults?.bitwardenFolderId
                    )
                }
                BottomNavItem.Passkey -> {
                    PasskeyPane(
                        isCompactWidth = isCompactWidth,
                        wideListPaneWidth = wideListPaneWidth,
                        passkeyViewModel = passkeyViewModel,
                        passwordViewModel = passwordViewModel,
                        onNavigateToPasswordDetail = onNavigateToPasswordDetail,
                        onPasskeyOpen = handlePasskeyOpen,
                        selectedPasskey = selectedPasskey,
                        passkeyTotalCount = passkeyTotalCount,
                        passkeyBoundCount = passkeyBoundCount,
                        resolvePasswordTitle = { passwordId -> passwordById[passwordId]?.title },
                        onOpenPasswordDetail = handlePasswordDetailOpen,
                        onUnbindPasskey = handlePasskeyUnbind,
                        onDeletePasskey = { passkey -> pendingPasskeyDelete = passkey }
                    )
                }
                BottomNavItem.Send -> {
                    SendPane(
                        isCompactWidth = isCompactWidth,
                        wideListPaneWidth = wideListPaneWidth,
                        bitwardenViewModel = bitwardenViewModel,
                        sendState = sendState,
                        selectedSend = selectedSend,
                        isAddingSendInline = isAddingSendInline,
                        onSendClick = handleSendOpen,
                        onInlineSendEditorBack = handleInlineSendEditorBack,
                        onCreateSend = { title, text, notes, password, maxAccessCount, hideEmail, hiddenText, expireInDays ->
                            bitwardenViewModel.createTextSend(
                                title = title,
                                text = text,
                                notes = notes,
                                password = password,
                                maxAccessCount = maxAccessCount,
                                hideEmail = hideEmail,
                                hiddenText = hiddenText,
                                expireInDays = expireInDays
                            )
                            handleInlineSendEditorBack()
                        },
                        onBitwardenEvent = handleSendBitwardenEvent
                    )
                }
                BottomNavItem.Settings -> {
                    SettingsTabContent(
                        viewModel = settingsViewModel,
                        onResetPassword = onNavigateToChangePassword,
                        onSecurityQuestions = onNavigateToSecurityQuestion,
                        onNavigateToSyncBackup = onNavigateToSyncBackup,
                        onNavigateToAutofill = onNavigateToAutofill,
                        onNavigateToPasskeySettings = onNavigateToPasskeySettings,
                        onNavigateToBottomNavSettings = onNavigateToBottomNavSettings,
                        onNavigateToColorScheme = onNavigateToColorScheme,
                        onSecurityAnalysis = onSecurityAnalysis,
                        onNavigateToDeveloperSettings = onNavigateToDeveloperSettings,
                        onNavigateToPermissionManagement = onNavigateToPermissionManagement,
                        onNavigateToMonicaPlus = onNavigateToMonicaPlus,
                        onNavigateToExtensions = onNavigateToExtensions,
                        onNavigateToPageCustomization = onNavigateToPageCustomization,
                        onClearAllData = onClearAllData
                    )
                }
            }

            MainScreenSelectionBars(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 20.dp),
                currentTab = currentTab,
                cardWalletSubTab = cardWalletSubTab,
                isPasswordSelectionMode = isPasswordSelectionMode,
                selectedPasswordCount = selectedPasswordCount,
                onExitPasswordSelection = onExitPasswordSelection,
                onSelectAllPasswords = onSelectAllPasswords,
                onFavoriteSelectedPasswords = onFavoriteSelectedPasswords,
                onMoveToCategoryPasswords = onMoveToCategoryPasswords,
                onManualStackPasswords = onManualStackPasswords,
                onDeleteSelectedPasswords = onDeleteSelectedPasswords,
                isTotpSelectionMode = isTotpSelectionMode,
                selectedTotpCount = selectedTotpCount,
                onExitTotpSelection = onExitTotpSelection,
                onSelectAllTotp = onSelectAllTotp,
                onMoveToCategoryTotp = onMoveToCategoryTotp,
                onDeleteSelectedTotp = onDeleteSelectedTotp,
                isBankCardSelectionMode = isBankCardSelectionMode,
                selectedBankCardCount = selectedBankCardCount,
                onExitBankCardSelection = onExitBankCardSelection,
                onSelectAllBankCards = onSelectAllBankCards,
                onFavoriteBankCards = onFavoriteBankCards,
                onMoveToCategoryBankCards = onMoveToCategoryBankCards,
                onDeleteSelectedBankCards = onDeleteSelectedBankCards,
                isDocumentSelectionMode = isDocumentSelectionMode,
                selectedDocumentCount = selectedDocumentCount,
                onExitDocumentSelection = onExitDocumentSelection,
                onSelectAllDocuments = onSelectAllDocuments,
                onMoveToCategoryDocuments = onMoveToCategoryDocuments,
                onDeleteSelectedDocuments = onDeleteSelectedDocuments
            )
        }

        if (isCompactWidth) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                content = scaffoldBody
            )
        } else {
            val railTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val railBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                NavigationRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(wideNavigationRailWidth),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(
                                top = railTopInset + 8.dp,
                                bottom = railBottomInset + 8.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tabs.forEach { item ->
                            val label = stringResource(item.shortLabelRes())
                            NavigationRailItem(
                                selected = item.key == currentTab.key,
                                onClick = { selectedTabKey = item.key },
                                icon = { Icon(item.icon, contentDescription = label) },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    content = scaffoldBody
                )
            }
        }
    }
    }

    val prepareTotpAddStorageDefaults: (Long?, Long?, String?, Long?, String?) -> Unit = { categoryId, keepassDatabaseId, keepassGroupPath, bitwardenVaultId, bitwardenFolderId ->
        if (isCompactWidth) {
            pendingInlineTotpAddStorageDefaults = null
            onPrepareTotpAddStorageDefaults(categoryId, keepassDatabaseId, keepassGroupPath, bitwardenVaultId, bitwardenFolderId)
        } else {
            pendingInlineTotpAddStorageDefaults = NewItemStorageDefaults(
                categoryId = categoryId,
                keepassDatabaseId = keepassDatabaseId,
                keepassGroupPath = keepassGroupPath,
                bitwardenVaultId = bitwardenVaultId,
                bitwardenFolderId = bitwardenFolderId
            ).takeIf { it.hasAnyValue() }
        }
    }
    val preparePasswordAddStorageDefaults: (Long?, Long?, String?, Long?, String?) -> Unit = { categoryId, keepassDatabaseId, keepassGroupPath, bitwardenVaultId, bitwardenFolderId ->
        if (isCompactWidth) {
            pendingInlinePasswordAddStorageDefaults = null
            onPreparePasswordAddStorageDefaults(categoryId, keepassDatabaseId, keepassGroupPath, bitwardenVaultId, bitwardenFolderId)
        } else {
            pendingInlinePasswordAddStorageDefaults = NewItemStorageDefaults(
                categoryId = categoryId,
                keepassDatabaseId = keepassDatabaseId,
                keepassGroupPath = keepassGroupPath,
                bitwardenVaultId = bitwardenVaultId,
                bitwardenFolderId = bitwardenFolderId
            ).takeIf { it.hasAnyValue() }
        }
    }
    val prepareNoteAddStorageDefaults: (Long?, Long?, String?, Long?, String?) -> Unit = { categoryId, keepassDatabaseId, keepassGroupPath, bitwardenVaultId, bitwardenFolderId ->
        if (isCompactWidth) {
            pendingInlineNoteAddStorageDefaults = null
            onPrepareNoteAddStorageDefaults(categoryId, keepassDatabaseId, keepassGroupPath, bitwardenVaultId, bitwardenFolderId)
        } else {
            pendingInlineNoteAddStorageDefaults = NewItemStorageDefaults(
                categoryId = categoryId,
                keepassDatabaseId = keepassDatabaseId,
                keepassGroupPath = keepassGroupPath,
                bitwardenVaultId = bitwardenVaultId,
                bitwardenFolderId = bitwardenFolderId
            ).takeIf { it.hasAnyValue() }
        }
    }
    val prepareWalletAddStorageDefaults: (Long?, Long?, String?, Long?, String?) -> Unit = { categoryId, keepassDatabaseId, keepassGroupPath, bitwardenVaultId, bitwardenFolderId ->
        if (isCompactWidth) {
            pendingInlineWalletAddStorageDefaults = null
            onPrepareWalletAddStorageDefaults(categoryId, keepassDatabaseId, keepassGroupPath, bitwardenVaultId, bitwardenFolderId)
        } else {
            pendingInlineWalletAddStorageDefaults = NewItemStorageDefaults(
                categoryId = categoryId,
                keepassDatabaseId = keepassDatabaseId,
                keepassGroupPath = keepassGroupPath,
                bitwardenVaultId = bitwardenVaultId,
                bitwardenFolderId = bitwardenFolderId
            ).takeIf { it.hasAnyValue() }
        }
    }

    MainScreenFabOverlay(
        currentTab = currentTab,
        isCompactWidth = isCompactWidth,
        wideFabHostWidth = wideFabHostWidth,
        appSettings = appSettings,
        passwordHistoryPageMode = passwordHistoryPageMode,
        isAnySelectionMode = isAnySelectionMode,
        isAddingPasswordInline = isAddingPasswordInline,
        inlinePasswordEditorId = inlinePasswordEditorId,
        isAddingTotpInline = isAddingTotpInline,
        selectedTotpId = selectedTotpId,
        isAddingBankCardInline = isAddingBankCardInline,
        inlineBankCardEditorId = inlineBankCardEditorId,
        selectedBankCardId = selectedBankCardId,
        isAddingDocumentInline = isAddingDocumentInline,
        inlineDocumentEditorId = inlineDocumentEditorId,
        selectedDocumentId = selectedDocumentId,
        isAddingNoteInline = isAddingNoteInline,
        inlineNoteEditorId = inlineNoteEditorId,
        isAddingSendInline = isAddingSendInline,
        isFabVisible = isFabVisible,
        isFabExpanded = isFabExpanded,
        onFabExpandedChange = { expanded -> isFabExpanded = expanded },
        fastScrollStripVisible = isFastScrollStripVisible,
        onFastScrollStripVisibleChange = { visible -> isFastScrollStripVisible = visible },
        fastScrollIndicatorLabel = if (currentTab == BottomNavItem.VaultV2) fastScrollIndicatorLabel else null,
        passwordListShowBackToTop = passwordListShowBackToTop,
        onBackToTop = { passwordScrollToTopRequestKey++ },
        quickAccessEnabled = appSettings.passwordListQuickAccessEnabled,
        showPasswordQuickAccessSheet = showPasswordQuickAccessSheet,
        onShowPasswordQuickAccessSheetChange = { showPasswordQuickAccessSheet = it },
        recentOpenedPasswords = recentOpenedPasswords,
        frequentOpenedPasswords = frequentOpenedPasswords,
        onOpenPasswordFromQuickAccess = handlePasswordDetailOpen,
        cardWalletSubTab = cardWalletSubTab,
        onPasswordAddOpen = handlePasswordAddOpen,
        onTotpAddOpen = handleTotpAddOpen,
        onBankCardAddOpen = handleBankCardAddOpen,
        onWalletAddOpen = handleWalletAddOpen,
        onNavigateToWalletAdd = onNavigateToWalletAdd,
        passwordPageAggregateEnabled = appSettings.passwordPageAggregateEnabled,
        passwordNewItemDefaults = passwordNewItemDefaults,
        onPreparePasswordAddStorageDefaults = preparePasswordAddStorageDefaults,
        onPrepareTotpAddStorageDefaults = prepareTotpAddStorageDefaults,
        onPrepareNoteAddStorageDefaults = prepareNoteAddStorageDefaults,
        onPrepareWalletAddStorageDefaults = prepareWalletAddStorageDefaults,
        onNoteAddOpen = { handleNoteOpen(null) },
        onSendAddOpen = handleSendAddOpen,
        onGeneratorRefresh = { generatorRefreshRequestKey++ },
        passwordViewModel = passwordViewModel,
        totpViewModel = totpViewModel,
        bankCardViewModel = bankCardViewModel,
        localKeePassViewModel = localKeePassViewModel,
        totpNewItemDefaults = totpNewItemDefaults,
        onNavigateToQuickTotpScan = onNavigateToQuickTotpScan,
        walletUnifiedAddType = walletUnifiedAddType,
        onWalletUnifiedAddTypeChange = { walletUnifiedAddType = it },
        documentViewModel = documentViewModel,
        walletAddSaveableStateHolder = walletAddSaveableStateHolder,
        noteViewModel = noteViewModel,
        sendState = sendState,
        bitwardenViewModel = bitwardenViewModel
    )

    val navBarInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val compactBottomOffset = if (useDraggableNav) {
        92.dp + navBarInsetBottom
    } else {
        88.dp + navBarInsetBottom
    }
    val hintModifier = if (isCompactWidth) {
        Modifier
            .align(Alignment.BottomStart)
            .padding(start = 16.dp, bottom = compactBottomOffset + 12.dp)
            .zIndex(4f)
    } else {
        Modifier
            .align(Alignment.BottomStart)
            .padding(start = wideFabHostWidth + 16.dp, bottom = 24.dp + 12.dp)
            .zIndex(4f)
    }
    val statusHintContainerColor = when (currentTab) {
        BottomNavItem.CardWallet -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val statusHintTextColor = when (currentTab) {
        BottomNavItem.CardWallet -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Column(
        modifier = hintModifier.animateContentSize(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BottomMiniHintBubble(
            visible = syncHintVisible,
            text = stringResource(bottomStatusUiState?.messageRes ?: R.string.sync_status_syncing_short),
            containerColor = statusHintContainerColor,
            textColor = statusHintTextColor
        )

        activeMiniHints.forEach { hint ->
            val hintVisible = hint.id !in dismissingHintIds
            BottomMiniHintBubble(
                visible = hintVisible,
                text = hint.message,
                containerColor = statusHintContainerColor,
                textColor = statusHintTextColor
            )
        }
    }
    } // End Outer Box
    }

    RenderMainSurface()

    if (pendingPasskeyDelete != null) {
        val passkey = pendingPasskeyDelete!!
        AlertDialog(
            onDismissRequest = { pendingPasskeyDelete = null },
            title = { Text(stringResource(R.string.passkey_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.passkey_delete_message,
                        passkey.rpName.ifBlank { passkey.rpId },
                        passkey.userName.ifBlank { "-" }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = confirmPasskeyDelete) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPasskeyDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
private fun PasswordTabPane(
    isCompactWidth: Boolean,
    wideListPaneWidth: Dp,
    passwordViewModel: PasswordViewModel,
    settingsViewModel: SettingsViewModel,
    securityManager: SecurityManager,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    bitwardenVaults: List<takagi.ru.monica.data.bitwarden.BitwardenVault>,
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    timelineViewModel: TimelineViewModel,
    groupMode: String,
    stackCardMode: StackCardMode,
    visibleContentTypes: List<PasswordPageContentType>,
    selectedContentTypes: Set<PasswordPageContentType>,
    onToggleContentType: (PasswordPageContentType) -> Unit,
    onPasswordOpen: (Long) -> Unit,
    onNavigateToAddTotp: (Long?) -> Unit,
    onNavigateToBankCardDetail: (Long) -> Unit,
    onNavigateToDocumentDetail: (Long) -> Unit,
    onNavigateToAddNote: (Long?) -> Unit,
    onNavigateToPasskeyDetail: (String) -> Unit,
    onOpenHistoryPage: () -> Unit,
    onOpenTrashPage: () -> Unit,
    onOpenCommonAccountTemplatesPage: () -> Unit,
    onCloseHistoryPage: () -> Unit,
    passwordHistoryPageMode: PasswordHistoryPageMode,
    passwordHistoryInitialTrashScopeKey: String?,
    onTimelineLogSelected: (TimelineEvent.StandardLog) -> Unit,
    onSelectionModeChange: (
        Boolean,
        Int,
        () -> Unit,
        () -> Unit,
        (() -> Unit)?,
        (() -> Unit)?,
        (() -> Unit)?,
        () -> Unit
    ) -> Unit,
    onBackToTopVisibilityChange: (Boolean) -> Unit,
    scrollToTopRequestKey: Int,
    isAddingPasswordInline: Boolean,
    inlinePasswordEditorId: Long?,
    selectedPasswordId: Long?,
    passwordNewItemDefaults: NewItemStorageDefaults,
    onInlinePasswordEditorBack: () -> Unit,
    totpViewModel: takagi.ru.monica.viewmodel.TotpViewModel,
    bankCardViewModel: takagi.ru.monica.viewmodel.BankCardViewModel,
    noteViewModel: NoteViewModel,
    documentViewModel: DocumentViewModel,
    passkeyViewModel: PasskeyViewModel,
    disablePasswordVerification: Boolean,
    biometricEnabled: Boolean,
    iconCardsEnabled: Boolean,
    unmatchedIconHandlingStrategy: takagi.ru.monica.data.UnmatchedIconHandlingStrategy,
    onClearSelectedPassword: () -> Unit,
    onEditPassword: (Long) -> Unit
) {
    val appSettings by settingsViewModel.settings.collectAsState()

    val listPaneContent: @Composable ColumnScope.() -> Unit = {
        PasswordListContent(
            viewModel = passwordViewModel,
            settingsViewModel = settingsViewModel,
            securityManager = securityManager,
            keepassDatabases = keepassDatabases,
            bitwardenVaults = bitwardenVaults,
            localKeePassViewModel = localKeePassViewModel,
            groupMode = groupMode,
            stackCardMode = stackCardMode,
            onRenameCategory = { category ->
                passwordViewModel.updateCategory(category)
            },
            onDeleteCategory = { category ->
                passwordViewModel.deleteCategory(category)
            },
            onPasswordClick = { password ->
                onPasswordOpen(password.id)
            },
            onSelectionModeChange = onSelectionModeChange,
            onBackToTopVisibilityChange = onBackToTopVisibilityChange,
            scrollToTopRequestKey = scrollToTopRequestKey,
            onOpenHistory = onOpenHistoryPage,
            onOpenTrash = onOpenTrashPage,
            onOpenCommonAccountTemplates = onOpenCommonAccountTemplatesPage,
            aggregateConfig = PasswordListAggregateConfig(
                visibleContentTypes = visibleContentTypes,
                selectedContentTypes = selectedContentTypes,
                onToggleContentType = onToggleContentType,
                totpViewModel = totpViewModel,
                bankCardViewModel = bankCardViewModel,
                documentViewModel = documentViewModel,
                noteViewModel = noteViewModel,
                passkeyViewModel = passkeyViewModel,
                onOpenTotp = { onNavigateToAddTotp(it) },
                onOpenBankCard = onNavigateToBankCardDetail,
                onOpenDocument = onNavigateToDocumentDetail,
                onOpenNote = onNavigateToAddNote,
                onOpenPasskey = onNavigateToPasskeyDetail
            )
        )
    }

    if (passwordHistoryPageMode.isVisible) {
        TimelineScreen(
            viewModel = timelineViewModel,
            onLogSelected = onTimelineLogSelected,
            splitPaneMode = false,
            initialTab = passwordHistoryPageMode.tab ?: HistoryTab.TIMELINE,
            initialTrashScopeKey = passwordHistoryInitialTrashScopeKey,
            enableTabSwitch = false,
            showBackButton = true,
            onNavigateBack = onCloseHistoryPage
        )
        return
    }

    if (isCompactWidth) {
        ListPane(
            modifier = Modifier.fillMaxSize(),
            content = listPaneContent
        )
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            ListPane(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(wideListPaneWidth),
                content = listPaneContent
            )
            DetailPane(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (isAddingPasswordInline || inlinePasswordEditorId != null) {
                    AddEditPasswordScreen(
                        viewModel = passwordViewModel,
                        totpViewModel = totpViewModel,
                        bankCardViewModel = bankCardViewModel,
                        localKeePassViewModel = localKeePassViewModel,
                        passwordId = inlinePasswordEditorId,
                        initialCategoryId = passwordNewItemDefaults.categoryId,
                        initialKeePassDatabaseId = passwordNewItemDefaults.keepassDatabaseId,
                        initialKeePassGroupPath = passwordNewItemDefaults.keepassGroupPath,
                        initialBitwardenVaultId = passwordNewItemDefaults.bitwardenVaultId,
                        initialBitwardenFolderId = passwordNewItemDefaults.bitwardenFolderId,
                        onNavigateBack = onInlinePasswordEditorBack
                    )
                } else if (selectedPasswordId == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Select an item to view details",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    CompositionLocalProvider(
                        LocalSharedTransitionScope provides null,
                        LocalAnimatedVisibilityScope provides null
                    ) {
                        PasswordDetailScreen(
                            viewModel = passwordViewModel,
                            passkeyViewModel = passkeyViewModel,
                            passwordId = selectedPasswordId,
                            disablePasswordVerification = disablePasswordVerification,
                            biometricEnabled = biometricEnabled,
                            iconCardsEnabled = iconCardsEnabled,
                            unmatchedIconHandlingStrategy = unmatchedIconHandlingStrategy,
                            enableSharedBounds = false,
                            onNavigateBack = onClearSelectedPassword,
                            onEditPassword = onEditPassword,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthenticatorTabPane(
    isCompactWidth: Boolean,
    wideListPaneWidth: Dp,
    totpViewModel: takagi.ru.monica.viewmodel.TotpViewModel,
    passwordViewModel: PasswordViewModel,
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    onTotpOpen: (Long) -> Unit,
    onNavigateToQuickTotpScan: () -> Unit,
    onSelectionModeChange: (
        Boolean,
        Int,
        () -> Unit,
        () -> Unit,
        () -> Unit,
        () -> Unit
    ) -> Unit,
    isAddingTotpInline: Boolean,
    selectedTotpId: Long?,
    totpNewItemDefaults: NewItemStorageDefaults,
    onInlineTotpEditorBack: () -> Unit
) {
    val listPaneContent: @Composable ColumnScope.() -> Unit = {
        TotpListContent(
            viewModel = totpViewModel,
            passwordViewModel = passwordViewModel,
            onTotpClick = onTotpOpen,
            onDeleteTotp = { totp ->
                totpViewModel.deleteTotpItem(totp)
            },
            onQuickScanTotp = onNavigateToQuickTotpScan,
            onSelectionModeChange = onSelectionModeChange
        )
    }

    if (isCompactWidth) {
        ListPane(
            modifier = Modifier.fillMaxSize(),
            content = listPaneContent
        )
    } else {
        val totpItems by totpViewModel.totpItems.collectAsState()
        val selectedTotpItem = remember(selectedTotpId, totpItems) {
            selectedTotpId?.let { selectedId ->
                totpItems.firstOrNull { it.id == selectedId }
            }
        }
        val selectedTotpData = remember(selectedTotpItem?.itemData) {
            selectedTotpItem?.itemData?.let { itemData ->
                runCatching {
                    kotlinx.serialization.json.Json.decodeFromString<takagi.ru.monica.data.model.TotpData>(itemData)
                }.getOrNull()
            }
        }
        val totpCategories by totpViewModel.categories.collectAsState()

        Row(modifier = Modifier.fillMaxSize()) {
            ListPane(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(wideListPaneWidth),
                content = listPaneContent
            )
            DetailPane(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (isAddingTotpInline) {
                    AddEditTotpScreen(
                        totpId = null,
                        initialData = null,
                        initialTitle = "",
                        initialNotes = "",
                        initialCategoryId = totpNewItemDefaults.categoryId,
                        initialKeePassDatabaseId = totpNewItemDefaults.keepassDatabaseId,
                        initialKeePassGroupPath = totpNewItemDefaults.keepassGroupPath,
                        initialBitwardenVaultId = totpNewItemDefaults.bitwardenVaultId,
                        initialBitwardenFolderId = totpNewItemDefaults.bitwardenFolderId,
                        categories = totpCategories,
                        passwordViewModel = passwordViewModel,
                        localKeePassViewModel = localKeePassViewModel,
                        onSave = { title, notes, totpData, categoryId, keepassDatabaseId, keepassGroupPath, bitwardenVaultId, bitwardenFolderId ->
                            totpViewModel.saveTotpItem(
                                id = null,
                                title = title,
                                notes = notes,
                                totpData = totpData,
                                categoryId = categoryId,
                                keepassDatabaseId = keepassDatabaseId,
                                keepassGroupPath = keepassGroupPath,
                                bitwardenVaultId = bitwardenVaultId,
                                bitwardenFolderId = bitwardenFolderId
                            )
                            onInlineTotpEditorBack()
                        },
                        onNavigateBack = onInlineTotpEditorBack,
                        onScanQrCode = onNavigateToQuickTotpScan,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (selectedTotpId == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Select an item to view details",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (selectedTotpItem == null || selectedTotpItem.id <= 0L || selectedTotpData == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "This item is not available for inline editing",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    AddEditTotpScreen(
                        totpId = selectedTotpItem.id,
                        initialData = selectedTotpData,
                        initialTitle = selectedTotpItem.title,
                        initialNotes = selectedTotpItem.notes,
                        initialCategoryId = selectedTotpData.categoryId,
                        initialKeePassGroupPath = selectedTotpItem.keepassGroupPath,
                        initialBitwardenVaultId = selectedTotpItem.bitwardenVaultId,
                        initialBitwardenFolderId = selectedTotpItem.bitwardenFolderId,
                        categories = totpCategories,
                        passwordViewModel = passwordViewModel,
                        localKeePassViewModel = localKeePassViewModel,
                        onSave = { title, notes, totpData, categoryId, keepassDatabaseId, keepassGroupPath, bitwardenVaultId, bitwardenFolderId ->
                            totpViewModel.saveTotpItem(
                                id = selectedTotpItem.id,
                                title = title,
                                notes = notes,
                                totpData = totpData,
                                categoryId = categoryId,
                                keepassDatabaseId = keepassDatabaseId,
                                keepassGroupPath = keepassGroupPath,
                                bitwardenVaultId = bitwardenVaultId,
                                bitwardenFolderId = bitwardenFolderId
                            )
                        },
                        onNavigateBack = onInlineTotpEditorBack,
                        onScanQrCode = onNavigateToQuickTotpScan,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactDraggableTabContent(
    paddingValues: PaddingValues,
    currentTab: BottomNavItem,
    passwordViewModel: PasswordViewModel,
    settingsViewModel: SettingsViewModel,
    securityManager: SecurityManager,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    bitwardenVaults: List<takagi.ru.monica.data.bitwarden.BitwardenVault>,
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    passwordGroupMode: String,
    stackCardMode: StackCardMode,
    onPasswordOpen: (Long) -> Unit,
    onBankCardOpen: (Long) -> Unit,
    onDocumentOpen: (Long) -> Unit,
    onNoteOpen: (Long) -> Unit,
    onPasskeyOpen: (PasskeyEntry) -> Unit,
    onNavigateToAddTotp: (Long?) -> Unit,
    onNavigateToBankCardDetail: (Long) -> Unit,
    onNavigateToDocumentDetail: (Long) -> Unit,
    onNavigateToPasskeyDetail: (String) -> Unit,
    onPasswordSelectionModeChange: (
        Boolean,
        Int,
        () -> Unit,
        () -> Unit,
        (() -> Unit)?,
        (() -> Unit)?,
        (() -> Unit)?,
        () -> Unit
    ) -> Unit,
    onBackToTopVisibilityChange: (Boolean) -> Unit,
    passwordScrollToTopRequestKey: Int,
    totpViewModel: takagi.ru.monica.viewmodel.TotpViewModel,
    onTotpOpen: (Long) -> Unit,
    onNavigateToQuickTotpScan: () -> Unit,
    onTotpSelectionModeChange: (
        Boolean,
        Int,
        () -> Unit,
        () -> Unit,
        () -> Unit,
        () -> Unit
    ) -> Unit,
    cardWalletSaveableStateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    bankCardViewModel: BankCardViewModel,
    documentViewModel: DocumentViewModel,
    cardWalletContentState: CardWalletContentState,
    generatorViewModel: GeneratorViewModel,
    generatorRefreshRequestKey: Int,
    onGeneratorRefreshRequestConsumed: () -> Unit,
    noteViewModel: NoteViewModel,
    onNavigateToAddNote: (Long?) -> Unit,
    onNoteSelectionModeChange: (Boolean) -> Unit,
    timelineViewModel: TimelineViewModel,
    passkeyViewModel: PasskeyViewModel,
    onNavigateToPasswordDetail: (Long) -> Unit,
    bitwardenViewModel: takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel,
    onSendBitwardenEvent: (takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel.BitwardenEvent) -> Boolean,
    onNavigateToChangePassword: () -> Unit,
    onNavigateToSecurityQuestion: () -> Unit,
    onNavigateToSyncBackup: () -> Unit,
    onNavigateToAutofill: () -> Unit,
    onNavigateToPasskeySettings: () -> Unit,
    onNavigateToBottomNavSettings: () -> Unit,
    onNavigateToColorScheme: () -> Unit,
    onSecurityAnalysis: () -> Unit,
    onNavigateToDeveloperSettings: () -> Unit,
    onNavigateToPermissionManagement: () -> Unit,
    onNavigateToMonicaPlus: () -> Unit,
    onNavigateToExtensions: () -> Unit,
    onNavigateToCommonAccountTemplates: () -> Unit,
    onNavigateToPageCustomization: () -> Unit,
    onClearAllData: (Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    cardWalletSubTab: CardWalletTab,
    passwordHistoryPageMode: PasswordHistoryPageMode,
    passwordHistoryInitialTrashScopeKey: String?,
    onOpenHistoryPage: () -> Unit,
    onOpenTrashPage: () -> Unit,
    onCloseHistoryPage: () -> Unit,
    isPasswordSelectionMode: Boolean,
    selectedPasswordCount: Int,
    onExitPasswordSelection: () -> Unit,
    onSelectAllPasswords: () -> Unit,
    onFavoriteSelectedPasswords: (() -> Unit)?,
    onMoveToCategoryPasswords: (() -> Unit)?,
    onManualStackPasswords: (() -> Unit)?,
    onDeleteSelectedPasswords: () -> Unit,
    isTotpSelectionMode: Boolean,
    selectedTotpCount: Int,
    onExitTotpSelection: () -> Unit,
    onSelectAllTotp: () -> Unit,
    onMoveToCategoryTotp: () -> Unit,
    onDeleteSelectedTotp: () -> Unit,
    isBankCardSelectionMode: Boolean,
    selectedBankCardCount: Int,
    onExitBankCardSelection: () -> Unit,
    onSelectAllBankCards: () -> Unit,
    onFavoriteBankCards: () -> Unit,
    onMoveToCategoryBankCards: () -> Unit,
    onDeleteSelectedBankCards: () -> Unit,
    isDocumentSelectionMode: Boolean,
    selectedDocumentCount: Int,
    onExitDocumentSelection: () -> Unit,
    onSelectAllDocuments: () -> Unit,
    onMoveToCategoryDocuments: () -> Unit,
    onDeleteSelectedDocuments: () -> Unit
) {
    val appSettings by settingsViewModel.settings.collectAsState()
    val currentFilter by passwordViewModel.categoryFilter.collectAsState()
    val passwordNewItemDefaults = remember(currentFilter) { defaultsFromPasswordFilter(currentFilter) }
    val passwordPageVisibleContentTypes = remember(
        appSettings.passwordPageAggregateEnabled,
        appSettings.passwordPageVisibleContentTypes
    ) {
        resolvePasswordPageVisibleTypes(
            aggregateEnabled = appSettings.passwordPageAggregateEnabled,
            configuredTypes = appSettings.passwordPageVisibleContentTypes
        )
    }
    var passwordPageSelectedContentTypes by rememberSaveable(
        stateSaver = passwordPageContentTypeSetSaver
    ) {
        mutableStateOf(emptySet())
    }
    LaunchedEffect(passwordPageVisibleContentTypes) {
        passwordPageSelectedContentTypes = sanitizeSelectedPasswordPageTypes(
            visibleTypes = passwordPageVisibleContentTypes,
            selectedTypes = passwordPageSelectedContentTypes
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        when (currentTab) {
            BottomNavItem.VaultV2 -> {
                VaultV2Pane(
                    passwordViewModel = passwordViewModel,
                    totpViewModel = totpViewModel,
                    bankCardViewModel = bankCardViewModel,
                    documentViewModel = documentViewModel,
                    noteViewModel = noteViewModel,
                    passkeyViewModel = passkeyViewModel,
                    onOpenPassword = onPasswordOpen,
                    onOpenTotp = onTotpOpen,
                    onOpenBankCard = onBankCardOpen,
                    onOpenDocument = onDocumentOpen,
                    onOpenNote = onNoteOpen,
                    onOpenPasskey = onPasskeyOpen,
                    onBackToTopVisibilityChange = onBackToTopVisibilityChange,
                    onFastScrollSectionLabelChange = { },
                    scrollToTopRequestKey = passwordScrollToTopRequestKey,
                    appSettings = appSettings,
                    modifier = Modifier.fillMaxSize()
                )
            }
            BottomNavItem.Passwords -> {
                PasswordTabPane(
                    isCompactWidth = true,
                    wideListPaneWidth = 0.dp,
                    passwordViewModel = passwordViewModel,
                    settingsViewModel = settingsViewModel,
                    securityManager = securityManager,
                    keepassDatabases = keepassDatabases,
                    bitwardenVaults = bitwardenVaults,
                    localKeePassViewModel = localKeePassViewModel,
                    timelineViewModel = timelineViewModel,
                    groupMode = passwordGroupMode,
                    stackCardMode = stackCardMode,
                    visibleContentTypes = passwordPageVisibleContentTypes,
                    selectedContentTypes = passwordPageSelectedContentTypes,
                    onToggleContentType = { type ->
                        passwordPageSelectedContentTypes = togglePasswordPageContentType(
                            currentTypes = passwordPageSelectedContentTypes,
                            toggledType = type,
                            visibleTypes = passwordPageVisibleContentTypes
                        )
                    },
                    onPasswordOpen = onPasswordOpen,
                    onNavigateToAddTotp = onNavigateToAddTotp,
                    onNavigateToBankCardDetail = onNavigateToBankCardDetail,
                    onNavigateToDocumentDetail = onNavigateToDocumentDetail,
                    onNavigateToAddNote = onNavigateToAddNote,
                    onNavigateToPasskeyDetail = onNavigateToPasskeyDetail,
                    onOpenHistoryPage = onOpenHistoryPage,
                    onOpenTrashPage = onOpenTrashPage,
                    onOpenCommonAccountTemplatesPage = onNavigateToCommonAccountTemplates,
                    onCloseHistoryPage = onCloseHistoryPage,
                    passwordHistoryPageMode = passwordHistoryPageMode,
                    passwordHistoryInitialTrashScopeKey = passwordHistoryInitialTrashScopeKey,
                    onTimelineLogSelected = {},
                    onSelectionModeChange = onPasswordSelectionModeChange,
                    onBackToTopVisibilityChange = onBackToTopVisibilityChange,
                    scrollToTopRequestKey = passwordScrollToTopRequestKey,
                    isAddingPasswordInline = false,
                    inlinePasswordEditorId = null,
                    selectedPasswordId = null,
                    passwordNewItemDefaults = passwordNewItemDefaults,
                    onInlinePasswordEditorBack = {},
                    totpViewModel = totpViewModel,
                    bankCardViewModel = bankCardViewModel,
                    noteViewModel = noteViewModel,
                    documentViewModel = documentViewModel,
                    passkeyViewModel = passkeyViewModel,
                    disablePasswordVerification = appSettings.disablePasswordVerification,
                    biometricEnabled = appSettings.biometricEnabled,
                    iconCardsEnabled = appSettings.iconCardsEnabled && appSettings.passwordPageIconEnabled,
                    unmatchedIconHandlingStrategy = appSettings.unmatchedIconHandlingStrategy,
                    onClearSelectedPassword = {},
                    onEditPassword = {}
                )
            }
            BottomNavItem.Authenticator -> {
                TotpListContent(
                    viewModel = totpViewModel,
                    passwordViewModel = passwordViewModel,
                    onTotpClick = onTotpOpen,
                    onDeleteTotp = { totp ->
                        totpViewModel.deleteTotpItem(totp)
                    },
                    onQuickScanTotp = onNavigateToQuickTotpScan,
                    onSelectionModeChange = onTotpSelectionModeChange
                )
            }
            BottomNavItem.CardWallet -> {
                CardWalletContent(
                    saveableStateHolder = cardWalletSaveableStateHolder,
                    bankCardViewModel = bankCardViewModel,
                    documentViewModel = documentViewModel,
                    state = cardWalletContentState
                )
            }
            BottomNavItem.Generator -> {
                GeneratorScreen(
                    onNavigateBack = {},
                    viewModel = generatorViewModel,
                    passwordViewModel = passwordViewModel,
                    externalRefreshRequestKey = generatorRefreshRequestKey,
                    onRefreshRequestConsumed = onGeneratorRefreshRequestConsumed,
                    useExternalRefreshFab = true
                )
            }
            BottomNavItem.Notes -> {
                NoteListScreen(
                    viewModel = noteViewModel,
                    settingsViewModel = settingsViewModel,
                    onNavigateToAddNote = onNavigateToAddNote,
                    securityManager = securityManager,
                    onSelectionModeChange = onNoteSelectionModeChange
                )
            }
            BottomNavItem.Passkey -> {
                PasskeyListScreen(
                    viewModel = passkeyViewModel,
                    passwordViewModel = passwordViewModel,
                    onNavigateToPasswordDetail = onNavigateToPasswordDetail,
                    onPasskeyClick = {}
                )
            }
            BottomNavItem.Send -> {
                SendScreen(
                    bitwardenViewModel = bitwardenViewModel,
                    onBitwardenEvent = onSendBitwardenEvent
                )
            }
            BottomNavItem.Settings -> {
                SettingsTabContent(
                    viewModel = settingsViewModel,
                    onResetPassword = onNavigateToChangePassword,
                    onSecurityQuestions = onNavigateToSecurityQuestion,
                    onNavigateToSyncBackup = onNavigateToSyncBackup,
                    onNavigateToAutofill = onNavigateToAutofill,
                    onNavigateToPasskeySettings = onNavigateToPasskeySettings,
                    onNavigateToBottomNavSettings = onNavigateToBottomNavSettings,
                    onNavigateToColorScheme = onNavigateToColorScheme,
                    onSecurityAnalysis = onSecurityAnalysis,
                    onNavigateToDeveloperSettings = onNavigateToDeveloperSettings,
                    onNavigateToPermissionManagement = onNavigateToPermissionManagement,
                    onNavigateToMonicaPlus = onNavigateToMonicaPlus,
                    onNavigateToExtensions = onNavigateToExtensions,
                    onNavigateToPageCustomization = onNavigateToPageCustomization,
                    onClearAllData = onClearAllData
                )
            }
        }

        MainScreenSelectionBars(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            currentTab = currentTab,
            cardWalletSubTab = cardWalletSubTab,
            isPasswordSelectionMode = isPasswordSelectionMode,
            selectedPasswordCount = selectedPasswordCount,
            onExitPasswordSelection = onExitPasswordSelection,
            onSelectAllPasswords = onSelectAllPasswords,
            onFavoriteSelectedPasswords = onFavoriteSelectedPasswords,
            onMoveToCategoryPasswords = onMoveToCategoryPasswords,
            onManualStackPasswords = onManualStackPasswords,
            onDeleteSelectedPasswords = onDeleteSelectedPasswords,
            isTotpSelectionMode = isTotpSelectionMode,
            selectedTotpCount = selectedTotpCount,
            onExitTotpSelection = onExitTotpSelection,
            onSelectAllTotp = onSelectAllTotp,
            onMoveToCategoryTotp = onMoveToCategoryTotp,
            onDeleteSelectedTotp = onDeleteSelectedTotp,
            isBankCardSelectionMode = isBankCardSelectionMode,
            selectedBankCardCount = selectedBankCardCount,
            onExitBankCardSelection = onExitBankCardSelection,
            onSelectAllBankCards = onSelectAllBankCards,
            onFavoriteBankCards = onFavoriteBankCards,
            onMoveToCategoryBankCards = onMoveToCategoryBankCards,
            onDeleteSelectedBankCards = onDeleteSelectedBankCards,
            isDocumentSelectionMode = isDocumentSelectionMode,
            selectedDocumentCount = selectedDocumentCount,
            onExitDocumentSelection = onExitDocumentSelection,
            onSelectAllDocuments = onSelectAllDocuments,
            onMoveToCategoryDocuments = onMoveToCategoryDocuments,
            onDeleteSelectedDocuments = onDeleteSelectedDocuments
        )
    }
}

@Composable
private fun MainScreenTabResetEffects(
    currentTab: BottomNavItem,
    isCompactWidth: Boolean,
    cardWalletSubTab: CardWalletTab,
    passwordHistoryPageMode: PasswordHistoryPageMode,
    onResetPasswordPane: () -> Unit,
    onHideBackToTop: () -> Unit,
    onResetTotpPane: () -> Unit,
    onResetCardWalletPaneAll: () -> Unit,
    onResetCardWalletDocumentPane: () -> Unit,
    onResetCardWalletBankCardPane: () -> Unit,
    onSyncWalletUnifiedAddType: (CardWalletTab) -> Unit,
    onResetNotePane: () -> Unit,
    onResetPasskeyPane: () -> Unit,
    onResetSendPane: () -> Unit,
    onResetTimelineSelection: () -> Unit,
) {
    // Each effect owns one tab domain reset. Keep them split to avoid hidden coupling.
    LaunchedEffect(currentTab.key, isCompactWidth) {
        if (isCompactWidth || currentTab != BottomNavItem.Passwords) {
            onResetPasswordPane()
        }
    }
    LaunchedEffect(currentTab.key) {
        if (currentTab != BottomNavItem.Passwords && currentTab != BottomNavItem.VaultV2) {
            onHideBackToTop()
        }
    }
    LaunchedEffect(currentTab.key, isCompactWidth) {
        if (isCompactWidth || currentTab != BottomNavItem.Authenticator) {
            onResetTotpPane()
        }
    }
    LaunchedEffect(currentTab.key, isCompactWidth, cardWalletSubTab) {
        if (isCompactWidth || currentTab != BottomNavItem.CardWallet) {
            onResetCardWalletPaneAll()
        } else {
            when (cardWalletSubTab) {
                CardWalletTab.BANK_CARDS -> onResetCardWalletDocumentPane()
                CardWalletTab.DOCUMENTS -> onResetCardWalletBankCardPane()
                CardWalletTab.ALL -> Unit
            }
        }
    }
    LaunchedEffect(cardWalletSubTab) {
        if (cardWalletSubTab == CardWalletTab.BANK_CARDS || cardWalletSubTab == CardWalletTab.DOCUMENTS) {
            onSyncWalletUnifiedAddType(cardWalletSubTab)
        }
    }
    LaunchedEffect(currentTab.key, isCompactWidth) {
        if (isCompactWidth || currentTab != BottomNavItem.Notes) {
            onResetNotePane()
        }
    }
    LaunchedEffect(currentTab.key, isCompactWidth) {
        if (isCompactWidth || currentTab != BottomNavItem.Passkey) {
            onResetPasskeyPane()
        }
    }
    LaunchedEffect(currentTab.key, isCompactWidth) {
        if (isCompactWidth || currentTab != BottomNavItem.Send) {
            onResetSendPane()
        }
    }
    LaunchedEffect(currentTab.key, isCompactWidth, passwordHistoryPageMode) {
        if (isCompactWidth || currentTab != BottomNavItem.Passwords || !passwordHistoryPageMode.isVisible) {
            onResetTimelineSelection()
        }
    }
}

@Composable
private fun BoxScope.MainScreenFabOverlay(
    currentTab: BottomNavItem,
    isCompactWidth: Boolean,
    wideFabHostWidth: Dp,
    appSettings: takagi.ru.monica.data.AppSettings,
    passwordHistoryPageMode: PasswordHistoryPageMode,
    isAnySelectionMode: Boolean,
    isAddingPasswordInline: Boolean,
    inlinePasswordEditorId: Long?,
    isAddingTotpInline: Boolean,
    selectedTotpId: Long?,
    isAddingBankCardInline: Boolean,
    inlineBankCardEditorId: Long?,
    selectedBankCardId: Long?,
    isAddingDocumentInline: Boolean,
    inlineDocumentEditorId: Long?,
    selectedDocumentId: Long?,
    isAddingNoteInline: Boolean,
    inlineNoteEditorId: Long?,
    isAddingSendInline: Boolean,
    isFabVisible: Boolean,
    isFabExpanded: Boolean,
    onFabExpandedChange: (Boolean) -> Unit,
    fastScrollStripVisible: Boolean,
    onFastScrollStripVisibleChange: (Boolean) -> Unit,
    fastScrollIndicatorLabel: String?,
    passwordListShowBackToTop: Boolean,
    onBackToTop: () -> Unit,
    quickAccessEnabled: Boolean,
    showPasswordQuickAccessSheet: Boolean,
    onShowPasswordQuickAccessSheetChange: (Boolean) -> Unit,
    recentOpenedPasswords: List<PasswordQuickAccessItem>,
    frequentOpenedPasswords: List<PasswordQuickAccessItem>,
    onOpenPasswordFromQuickAccess: (Long) -> Unit,
    cardWalletSubTab: CardWalletTab,
    onPasswordAddOpen: () -> Unit,
    onTotpAddOpen: () -> Unit,
    onBankCardAddOpen: () -> Unit,
    onWalletAddOpen: () -> Unit,
    onNavigateToWalletAdd: (CardWalletTab) -> Unit,
    passwordPageAggregateEnabled: Boolean,
    passwordNewItemDefaults: NewItemStorageDefaults,
    onPreparePasswordAddStorageDefaults: (Long?, Long?, String?, Long?, String?) -> Unit,
    onPrepareTotpAddStorageDefaults: (Long?, Long?, String?, Long?, String?) -> Unit,
    onPrepareNoteAddStorageDefaults: (Long?, Long?, String?, Long?, String?) -> Unit,
    onPrepareWalletAddStorageDefaults: (Long?, Long?, String?, Long?, String?) -> Unit,
    onNoteAddOpen: () -> Unit,
    onSendAddOpen: () -> Unit,
    onGeneratorRefresh: () -> Unit,
    passwordViewModel: PasswordViewModel,
    totpViewModel: TotpViewModel,
    bankCardViewModel: BankCardViewModel,
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    totpNewItemDefaults: NewItemStorageDefaults,
    onNavigateToQuickTotpScan: () -> Unit,
    walletUnifiedAddType: CardWalletTab,
    onWalletUnifiedAddTypeChange: (CardWalletTab) -> Unit,
    documentViewModel: DocumentViewModel,
    walletAddSaveableStateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    noteViewModel: NoteViewModel,
    sendState: takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel.SendState,
    bitwardenViewModel: takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel,
) {
    // FAB visibility is computed from tab context + selection mode + detail pane occupancy.
    // This avoids conflicting gestures between fast-scroll, quick access, and expandable add menu.
    val fastScrollStripProgress by passwordViewModel.fastScrollProgress.collectAsState()

    val hasWideDetailSelection = !isCompactWidth && when (currentTab) {
        BottomNavItem.Passwords -> isAddingPasswordInline || inlinePasswordEditorId != null
        BottomNavItem.Authenticator -> isAddingTotpInline || selectedTotpId != null
        BottomNavItem.CardWallet -> isAddingBankCardInline ||
            inlineBankCardEditorId != null ||
            selectedBankCardId != null ||
            isAddingDocumentInline ||
            inlineDocumentEditorId != null ||
            selectedDocumentId != null
        BottomNavItem.Notes -> isAddingNoteInline || inlineNoteEditorId != null
        BottomNavItem.Send -> isAddingSendInline
        else -> false
    }

    val showFab = (
        currentTab == BottomNavItem.VaultV2 ||
            currentTab == BottomNavItem.Passwords ||
            currentTab == BottomNavItem.Authenticator ||
            currentTab == BottomNavItem.CardWallet ||
            currentTab == BottomNavItem.Generator ||
            currentTab == BottomNavItem.Notes ||
            currentTab == BottomNavItem.Send
        ) &&
        !(currentTab == BottomNavItem.Passwords && passwordHistoryPageMode.isVisible) &&
        !isAnySelectionMode &&
        !hasWideDetailSelection

    val isVaultLikeTab = currentTab == BottomNavItem.Passwords || currentTab == BottomNavItem.VaultV2
    val fabOverlayModifier = if (isCompactWidth) {
        Modifier.fillMaxSize().zIndex(5f)
    } else {
        Modifier
            .fillMaxHeight()
            .width(wideFabHostWidth)
            .align(Alignment.TopStart)
            .zIndex(5f)
    }
    val fabContainerColor = when (currentTab) {
        BottomNavItem.CardWallet -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val fabIconTint = when (currentTab) {
        BottomNavItem.CardWallet -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val fabBottomOffset = if (isCompactWidth) 116.dp else 24.dp
    val shouldShowBackToTopFab =
        showFab &&
            isFabVisible &&
            !isFabExpanded &&
            isVaultLikeTab &&
            !isAnySelectionMode &&
            passwordListShowBackToTop &&
            !fastScrollStripVisible
    val shouldShowQuickAccessFab =
        showFab &&
            isFabVisible &&
            !isFabExpanded &&
            isVaultLikeTab &&
            quickAccessEnabled &&
            !isAnySelectionMode &&
            !fastScrollStripVisible

    LaunchedEffect(showFab) {
        if (!showFab) {
            onFastScrollStripVisibleChange(false)
        }
    }

    BackHandler(enabled = fastScrollStripVisible) {
        onFastScrollStripVisibleChange(false)
    }

    AnimatedVisibility(
        visible = showFab && (isFabVisible || fastScrollStripVisible),
        enter = slideInHorizontally(initialOffsetX = { it * 2 }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it * 2 }) + fadeOut(),
        modifier = fabOverlayModifier
    ) {
        val backToTopInteractionSource = remember { MutableInteractionSource() }
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = shouldShowQuickAccessFab,
                enter = scaleIn(
                    initialScale = 0.25f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(animationSpec = tween(durationMillis = 120)),
                exit = scaleOut(
                    targetScale = 0.25f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeOut(animationSpec = tween(durationMillis = 90)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = fabBottomOffset + 88.dp
                    )
            ) {
                SmallFloatingActionButton(
                    onClick = { onShowPasswordQuickAccessSheetChange(true) },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = stringResource(R.string.password_quick_access_title)
                    )
                }
            }

            AnimatedVisibility(
                visible = shouldShowBackToTopFab,
                enter = scaleIn(
                    initialScale = 0.22f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(animationSpec = tween(durationMillis = 120)),
                exit = scaleOut(
                    targetScale = 0.22f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeOut(animationSpec = tween(durationMillis = 90)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 88.dp,
                        bottom = fabBottomOffset + 16.dp
                    )
            ) {
                Box(
                    modifier = Modifier
                        .combinedClickable(
                            interactionSource = backToTopInteractionSource,
                            indication = null,
                            onClick = onBackToTop,
                            onLongClick = {
                                onFastScrollStripVisibleChange(true)
                                if (showPasswordQuickAccessSheet) {
                                    onShowPasswordQuickAccessSheetChange(false)
                                }
                                onFabExpandedChange(false)
                            }
                        )
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        tonalElevation = 6.dp,
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    }
                }
            }

            MainScreenAddFab(
                visible = !fastScrollStripVisible,
                fabBottomOffset = fabBottomOffset,
                fabContainerColor = fabContainerColor,
                fabIconTint = fabIconTint,
                addButtonBehaviorMode = appSettings.addButtonBehaviorMode,
                addButtonMenuOrder = appSettings.addButtonMenuOrder,
                addButtonMenuEnabledActions = appSettings.addButtonMenuEnabledActions,
                currentTab = currentTab,
                isCompactWidth = isCompactWidth,
                cardWalletSubTab = cardWalletSubTab,
                onPasswordAddOpen = onPasswordAddOpen,
                onTotpAddOpen = onTotpAddOpen,
                onBankCardAddOpen = onBankCardAddOpen,
                onWalletAddOpen = onWalletAddOpen,
                onNavigateToWalletAdd = onNavigateToWalletAdd,
                passwordPageAggregateEnabled = passwordPageAggregateEnabled,
                passwordNewItemDefaults = passwordNewItemDefaults,
                onPreparePasswordAddStorageDefaults = onPreparePasswordAddStorageDefaults,
                onPrepareTotpAddStorageDefaults = onPrepareTotpAddStorageDefaults,
                onPrepareNoteAddStorageDefaults = onPrepareNoteAddStorageDefaults,
                onPrepareWalletAddStorageDefaults = onPrepareWalletAddStorageDefaults,
                onNoteAddOpen = onNoteAddOpen,
                onSendAddOpen = onSendAddOpen,
                onGeneratorRefresh = onGeneratorRefresh,
                onExpandStateChanged = onFabExpandedChange,
                passwordViewModel = passwordViewModel,
                totpViewModel = totpViewModel,
                bankCardViewModel = bankCardViewModel,
                localKeePassViewModel = localKeePassViewModel,
                totpNewItemDefaults = totpNewItemDefaults,
                onNavigateToQuickTotpScan = onNavigateToQuickTotpScan,
                walletUnifiedAddType = walletUnifiedAddType,
                onWalletUnifiedAddTypeChange = onWalletUnifiedAddTypeChange,
                documentViewModel = documentViewModel,
                walletAddSaveableStateHolder = walletAddSaveableStateHolder,
                noteViewModel = noteViewModel,
                sendState = sendState,
                bitwardenViewModel = bitwardenViewModel
            )

            AnimatedVisibility(
                visible = fastScrollStripVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 90)),
                exit = fadeOut(animationSpec = tween(durationMillis = 90)),
                modifier = Modifier.matchParentSize()
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            val rightGuardPx = 112.dp.toPx()
                            detectTapGestures(
                                onTap = { offset ->
                                    if (offset.x < size.width - rightGuardPx) {
                                        onFastScrollStripVisibleChange(false)
                                    }
                                }
                            )
                        }
                )
            }

            FastScrollPanel(
                visible = fastScrollStripVisible,
                progress = fastScrollStripProgress,
                indicatorLabel = fastScrollIndicatorLabel,
                onProgressChange = passwordViewModel::requestFastScroll,
                onDismiss = { onFastScrollStripVisibleChange(false) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
            )
        }
    }

    LaunchedEffect(currentTab, quickAccessEnabled, passwordHistoryPageMode) {
        val quickAccessTabAllowed =
            currentTab == BottomNavItem.Passwords || currentTab == BottomNavItem.VaultV2
        val shouldHideBecausePasswordHistory =
            currentTab == BottomNavItem.Passwords && passwordHistoryPageMode.isVisible
        if (
            (
                !quickAccessTabAllowed ||
                    !quickAccessEnabled ||
                    shouldHideBecausePasswordHistory
                ) &&
            showPasswordQuickAccessSheet
        ) {
            onShowPasswordQuickAccessSheetChange(false)
        }
    }

    PasswordQuickAccessSheet(
        visible = showPasswordQuickAccessSheet,
        recentItems = recentOpenedPasswords,
        frequentItems = frequentOpenedPasswords,
        onOpenPassword = onOpenPasswordFromQuickAccess,
        onDismiss = { onShowPasswordQuickAccessSheetChange(false) }
    )
}

@Composable
private fun FastScrollPanel(
    visible: Boolean,
    progress: Float,
    indicatorLabel: String?,
    onProgressChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut(),
        modifier = modifier
    ) {
        val clampedProgress = progress.coerceIn(0f, 1f)
        var gestureAreaHeightPx by remember { mutableStateOf(1) }
        var isTracking by remember { mutableStateOf(false) }
        var trackingTouchYPx by remember { mutableStateOf(0f) }
        val activeTrackColor = MaterialTheme.colorScheme.tertiaryContainer
        val inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer
        val indicatorColor = MaterialTheme.colorScheme.tertiary
        val dotColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.92f)
        val density = LocalDensity.current

        fun progressFromTouchY(y: Float): Float {
            val height = gestureAreaHeightPx.coerceAtLeast(1).toFloat()
            return (y / height).coerceIn(0f, 1f)
        }

        Column(
            modifier = Modifier.width(128.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .width(128.dp)
                    .height(356.dp)
            ) {
                val sliderWidth = 40.dp
                val separatorGap = 10.dp
                val separatorThickness = 4.dp
                val activeHeight = (maxHeight - separatorGap - separatorThickness) * clampedProgress
                val inactiveHeight = (maxHeight - separatorGap - separatorThickness) - activeHeight
                val indicatorBubbleHeight = 52.dp
                val indicatorBubbleWidth = 56.dp
                val maxBubbleOffsetPx = with(density) {
                    (maxHeight - indicatorBubbleHeight).toPx().coerceAtLeast(0f)
                }
                val bubbleOffsetPx = (trackingTouchYPx - with(density) { indicatorBubbleHeight.toPx() / 2f })
                    .coerceIn(0f, maxBubbleOffsetPx)

                Box(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isTracking && !indicatorLabel.isNullOrBlank(),
                        enter = scaleIn(
                            initialScale = 0.9f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + fadeIn(animationSpec = tween(120)),
                        exit = scaleOut(
                            targetScale = 0.94f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeOut(animationSpec = tween(90)),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(
                                x = 0.dp,
                                y = with(density) { bubbleOffsetPx.toDp() }
                            )
                    ) {
                        Surface(
                            modifier = Modifier.size(width = indicatorBubbleWidth, height = indicatorBubbleHeight),
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            tonalElevation = 6.dp,
                            shadowElevation = 2.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = indicatorLabel.orEmpty(),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .width(sliderWidth)
                            .fillMaxHeight()
                            .onSizeChanged { size ->
                                gestureAreaHeightPx = size.height.coerceAtLeast(1)
                            }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    isTracking = true
                                    trackingTouchYPx = down.position.y.coerceIn(0f, gestureAreaHeightPx.toFloat())
                                    onProgressChange(progressFromTouchY(trackingTouchYPx))

                                    var activePointerId = down.id
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == activePointerId }
                                            ?: event.changes.firstOrNull()
                                            ?: break

                                        activePointerId = change.id
                                        trackingTouchYPx = change.position.y.coerceIn(0f, gestureAreaHeightPx.toFloat())
                                        onProgressChange(progressFromTouchY(trackingTouchYPx))

                                        if (!change.pressed) {
                                            break
                                        }
                                        change.consume()
                                    }

                                    isTracking = false
                                }
                            }
                    ) {
                        if (activeHeight > 0.dp) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .width(sliderWidth)
                                    .height(activeHeight)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(activeTrackColor)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 8.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = activeHeight + (separatorGap / 2))
                                .width(28.dp)
                                .height(separatorThickness)
                                .clip(RoundedCornerShape(999.dp))
                                .background(indicatorColor)
                        )

                        if (inactiveHeight > 0.dp) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y = activeHeight + separatorGap + separatorThickness)
                                    .width(sliderWidth)
                                    .height(inactiveHeight)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(inactiveTrackColor)
                            )
                        }
                    }
                }
            }

            SmallFloatingActionButton(
                onClick = onDismiss,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close)
                )
            }
        }
    }
}

@Composable
private fun MainScreenSelectionBars(
    modifier: Modifier,
    currentTab: BottomNavItem,
    cardWalletSubTab: CardWalletTab,
    isPasswordSelectionMode: Boolean,
    selectedPasswordCount: Int,
    onExitPasswordSelection: () -> Unit,
    onSelectAllPasswords: () -> Unit,
    onFavoriteSelectedPasswords: (() -> Unit)?,
    onMoveToCategoryPasswords: (() -> Unit)?,
    onManualStackPasswords: (() -> Unit)?,
    onDeleteSelectedPasswords: () -> Unit,
    isTotpSelectionMode: Boolean,
    selectedTotpCount: Int,
    onExitTotpSelection: () -> Unit,
    onSelectAllTotp: () -> Unit,
    onMoveToCategoryTotp: () -> Unit,
    onDeleteSelectedTotp: () -> Unit,
    isBankCardSelectionMode: Boolean,
    selectedBankCardCount: Int,
    onExitBankCardSelection: () -> Unit,
    onSelectAllBankCards: () -> Unit,
    onFavoriteBankCards: () -> Unit,
    onMoveToCategoryBankCards: () -> Unit,
    onDeleteSelectedBankCards: () -> Unit,
    isDocumentSelectionMode: Boolean,
    selectedDocumentCount: Int,
    onExitDocumentSelection: () -> Unit,
    onSelectAllDocuments: () -> Unit,
    onMoveToCategoryDocuments: () -> Unit,
    onDeleteSelectedDocuments: () -> Unit
) {
    when {
        currentTab == BottomNavItem.Passwords && isPasswordSelectionMode -> {
            SelectionActionBar(
                modifier = modifier,
                selectedCount = selectedPasswordCount,
                onExit = onExitPasswordSelection,
                onSelectAll = onSelectAllPasswords,
                onFavorite = onFavoriteSelectedPasswords,
                onMoveToCategory = onMoveToCategoryPasswords,
                onStack = onManualStackPasswords,
                onDelete = onDeleteSelectedPasswords
            )
        }
        currentTab == BottomNavItem.Authenticator && isTotpSelectionMode -> {
            SelectionActionBar(
                modifier = modifier,
                selectedCount = selectedTotpCount,
                onExit = onExitTotpSelection,
                onSelectAll = onSelectAllTotp,
                onMoveToCategory = onMoveToCategoryTotp,
                onDelete = onDeleteSelectedTotp
            )
        }
        currentTab == BottomNavItem.CardWallet &&
            (cardWalletSubTab == CardWalletTab.BANK_CARDS || cardWalletSubTab == CardWalletTab.ALL) &&
            isBankCardSelectionMode -> {
            SelectionActionBar(
                modifier = modifier,
                selectedCount = selectedBankCardCount,
                onExit = onExitBankCardSelection,
                onSelectAll = onSelectAllBankCards,
                onFavorite = onFavoriteBankCards,
                onMoveToCategory = onMoveToCategoryBankCards,
                onDelete = onDeleteSelectedBankCards
            )
        }
        currentTab == BottomNavItem.CardWallet &&
            cardWalletSubTab == CardWalletTab.DOCUMENTS &&
            isDocumentSelectionMode -> {
            SelectionActionBar(
                modifier = modifier,
                selectedCount = selectedDocumentCount,
                onExit = onExitDocumentSelection,
                onSelectAll = onSelectAllDocuments,
                onMoveToCategory = onMoveToCategoryDocuments,
                onDelete = onDeleteSelectedDocuments
            )
        }
    }
}

@Composable
private fun MainScreenAddFab(
    visible: Boolean,
    fabBottomOffset: Dp,
    fabContainerColor: Color,
    fabIconTint: Color,
    addButtonBehaviorMode: AddButtonBehaviorMode,
    addButtonMenuOrder: List<AddButtonMenuAction>,
    addButtonMenuEnabledActions: List<AddButtonMenuAction>,
    currentTab: BottomNavItem,
    isCompactWidth: Boolean,
    cardWalletSubTab: CardWalletTab,
    onPasswordAddOpen: () -> Unit,
    onTotpAddOpen: () -> Unit,
    onBankCardAddOpen: () -> Unit,
    onWalletAddOpen: () -> Unit,
    onNavigateToWalletAdd: (CardWalletTab) -> Unit,
    passwordPageAggregateEnabled: Boolean,
    passwordNewItemDefaults: NewItemStorageDefaults,
    onPreparePasswordAddStorageDefaults: (Long?, Long?, String?, Long?, String?) -> Unit,
    onPrepareTotpAddStorageDefaults: (Long?, Long?, String?, Long?, String?) -> Unit,
    onPrepareNoteAddStorageDefaults: (Long?, Long?, String?, Long?, String?) -> Unit,
    onPrepareWalletAddStorageDefaults: (Long?, Long?, String?, Long?, String?) -> Unit,
    onNoteAddOpen: () -> Unit,
    onSendAddOpen: () -> Unit,
    onGeneratorRefresh: () -> Unit,
    onExpandStateChanged: (Boolean) -> Unit,
    passwordViewModel: PasswordViewModel,
    totpViewModel: TotpViewModel,
    bankCardViewModel: BankCardViewModel,
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    totpNewItemDefaults: NewItemStorageDefaults,
    onNavigateToQuickTotpScan: () -> Unit,
    walletUnifiedAddType: CardWalletTab,
    onWalletUnifiedAddTypeChange: (CardWalletTab) -> Unit,
    documentViewModel: DocumentViewModel,
    walletAddSaveableStateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    noteViewModel: NoteViewModel,
    sendState: takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel.SendState,
    bitwardenViewModel: takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel
) {
    var showVaultWalletAddScreen by rememberSaveable(currentTab.key) { mutableStateOf(false) }
    val compactWalletAddType = when (cardWalletSubTab) {
        CardWalletTab.DOCUMENTS -> CardWalletTab.DOCUMENTS
        CardWalletTab.BANK_CARDS -> CardWalletTab.BANK_CARDS
        CardWalletTab.ALL -> walletUnifiedAddType
    }
    val shouldApplyPasswordAggregateDefaults =
        currentTab == BottomNavItem.Passwords || currentTab == BottomNavItem.VaultV2
    val aggregateStorageDefaults = if (shouldApplyPasswordAggregateDefaults) {
        passwordNewItemDefaults
    } else {
        null
    }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            initialScale = 0.85f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(),
        exit = scaleOut(targetScale = 0.85f) + fadeOut()
    ) {
        if (currentTab == BottomNavItem.VaultV2 || currentTab == BottomNavItem.Passwords) {
            val menuActions = remember(
                addButtonMenuOrder,
                addButtonMenuEnabledActions,
                onPasswordAddOpen,
                onNoteAddOpen,
                onTotpAddOpen,
                onNavigateToWalletAdd,
                isCompactWidth,
                compactWalletAddType,
                showVaultWalletAddScreen,
                aggregateStorageDefaults,
                onPreparePasswordAddStorageDefaults,
                onPrepareTotpAddStorageDefaults,
                onPrepareNoteAddStorageDefaults,
                onPrepareWalletAddStorageDefaults
            ) {
                addButtonMenuOrder
                    .filter { addButtonMenuEnabledActions.contains(it) }
                    .map { action ->
                        when (action) {
                            AddButtonMenuAction.PASSWORD -> {
                                VaultV2FabMenuAction(
                                    icon = Icons.Default.Lock,
                                    labelRes = R.string.item_type_password,
                                    onClick = {
                                        if (aggregateStorageDefaults != null) {
                                            onPreparePasswordAddStorageDefaults(
                                                aggregateStorageDefaults.categoryId,
                                                aggregateStorageDefaults.keepassDatabaseId,
                                                aggregateStorageDefaults.keepassGroupPath,
                                                aggregateStorageDefaults.bitwardenVaultId,
                                                aggregateStorageDefaults.bitwardenFolderId
                                            )
                                        }
                                        onPasswordAddOpen()
                                    }
                                )
                            }

                            AddButtonMenuAction.NOTE -> {
                                VaultV2FabMenuAction(
                                    icon = Icons.Default.Description,
                                    labelRes = R.string.v2_create_note,
                                    onClick = {
                                        if (aggregateStorageDefaults != null) {
                                            onPrepareNoteAddStorageDefaults(
                                                aggregateStorageDefaults.categoryId,
                                                aggregateStorageDefaults.keepassDatabaseId,
                                                aggregateStorageDefaults.keepassGroupPath,
                                                aggregateStorageDefaults.bitwardenVaultId,
                                                aggregateStorageDefaults.bitwardenFolderId
                                            )
                                        }
                                        onNoteAddOpen()
                                    }
                                )
                            }

                            AddButtonMenuAction.AUTHENTICATOR -> {
                                VaultV2FabMenuAction(
                                    icon = Icons.Default.Security,
                                    labelRes = R.string.item_type_authenticator,
                                    onClick = {
                                        if (aggregateStorageDefaults != null) {
                                            onPrepareTotpAddStorageDefaults(
                                                aggregateStorageDefaults.categoryId,
                                                aggregateStorageDefaults.keepassDatabaseId,
                                                aggregateStorageDefaults.keepassGroupPath,
                                                aggregateStorageDefaults.bitwardenVaultId,
                                                aggregateStorageDefaults.bitwardenFolderId
                                            )
                                        }
                                        onTotpAddOpen()
                                    }
                                )
                            }

                            AddButtonMenuAction.BANK_CARD -> {
                                VaultV2FabMenuAction(
                                    icon = Icons.Default.CreditCard,
                                    labelRes = R.string.add_button_action_card,
                                    onClick = {
                                        if (aggregateStorageDefaults != null) {
                                            onPrepareWalletAddStorageDefaults(
                                                aggregateStorageDefaults.categoryId,
                                                aggregateStorageDefaults.keepassDatabaseId,
                                                aggregateStorageDefaults.keepassGroupPath,
                                                aggregateStorageDefaults.bitwardenVaultId,
                                                aggregateStorageDefaults.bitwardenFolderId
                                            )
                                        }
                                        if (isCompactWidth) {
                                            onNavigateToWalletAdd(compactWalletAddType)
                                        } else {
                                            showVaultWalletAddScreen = true
                                        }
                                    }
                                )
                            }
                        }
                    }
            }
            if (showVaultWalletAddScreen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    UnifiedWalletAddScreen(
                        selectedType = walletUnifiedAddType,
                        onTypeSelected = onWalletUnifiedAddTypeChange,
                        onNavigateBack = { showVaultWalletAddScreen = false },
                        bankCardViewModel = bankCardViewModel,
                        documentViewModel = documentViewModel,
                        stateHolder = walletAddSaveableStateHolder,
                        initialCategoryId = aggregateStorageDefaults?.categoryId,
                        initialKeePassDatabaseId = aggregateStorageDefaults?.keepassDatabaseId,
                        initialKeePassGroupPath = aggregateStorageDefaults?.keepassGroupPath,
                        initialBitwardenVaultId = aggregateStorageDefaults?.bitwardenVaultId,
                        initialBitwardenFolderId = aggregateStorageDefaults?.bitwardenFolderId
                    )
                }
            } else if (addButtonBehaviorMode == AddButtonBehaviorMode.EXPANDABLE_MENU) {
                VaultV2FabMenu(
                    fabBottomOffset = fabBottomOffset,
                    fabContainerColor = fabContainerColor,
                    fabIconTint = fabIconTint,
                    onExpandStateChanged = onExpandStateChanged,
                    menuActions = menuActions
                )
            } else {
                SwipeableAddFab(
                    fabBottomOffset = fabBottomOffset,
                    fabContainerColor = fabContainerColor,
                    modifier = Modifier,
                    onExpandStateChanged = onExpandStateChanged,
                    fabContent = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.add),
                            tint = fabIconTint
                        )
                    },
                    expandedContent = { collapse ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            AddEditPasswordScreen(
                                viewModel = passwordViewModel,
                                totpViewModel = totpViewModel,
                                bankCardViewModel = bankCardViewModel,
                                localKeePassViewModel = localKeePassViewModel,
                                passwordId = null,
                                initialCategoryId = aggregateStorageDefaults?.categoryId,
                                initialKeePassDatabaseId = aggregateStorageDefaults?.keepassDatabaseId,
                                initialKeePassGroupPath = aggregateStorageDefaults?.keepassGroupPath,
                                initialBitwardenVaultId = aggregateStorageDefaults?.bitwardenVaultId,
                                initialBitwardenFolderId = aggregateStorageDefaults?.bitwardenFolderId,
                                onNavigateBack = collapse
                            )
                        }
                    }
                )
            }
        } else {
            SwipeableAddFab(
                // 通过内部参数控制 FAB 位置，确保容器本身是全屏的
                // NavigationBar 高度约 80dp + 系统导航条高度 + 边距
                fabBottomOffset = fabBottomOffset,
                fabContainerColor = fabContainerColor,
                modifier = Modifier,
                onFabClickOverride = when (currentTab) {
                    BottomNavItem.VaultV2 -> if (isCompactWidth) null else ({ onPasswordAddOpen() })
                    BottomNavItem.Passwords -> if (isCompactWidth) null else ({ onPasswordAddOpen() })
                    BottomNavItem.Authenticator -> if (isCompactWidth) null else ({ onTotpAddOpen() })
                    BottomNavItem.CardWallet -> when {
                        isCompactWidth -> ({ onNavigateToWalletAdd(compactWalletAddType) })
                        cardWalletSubTab == CardWalletTab.ALL -> null
                        else -> ({ onWalletAddOpen() })
                    }
                    BottomNavItem.Notes -> if (isCompactWidth) null else ({ onNoteAddOpen() })
                    BottomNavItem.Send -> if (isCompactWidth) null else ({ onSendAddOpen() })
                    BottomNavItem.Generator -> ({ onGeneratorRefresh() })
                    else -> null
                },
                onExpandStateChanged = onExpandStateChanged,
                fabContent = {
                    when (currentTab) {
                        BottomNavItem.VaultV2,
                        BottomNavItem.Passwords,
                        BottomNavItem.Authenticator,
                        BottomNavItem.CardWallet,
                        BottomNavItem.Notes,
                        BottomNavItem.Send -> {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.add),
                                tint = fabIconTint
                            )
                        }
                        BottomNavItem.Generator -> {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.regenerate),
                                tint = fabIconTint
                            )
                        }
                        else -> { /* 不显示 */ }
                    }
                },
                expandedContent = { collapse ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        when (currentTab) {
                            BottomNavItem.VaultV2,
                            BottomNavItem.Passwords -> {
                                AddEditPasswordScreen(
                                    viewModel = passwordViewModel,
                                    totpViewModel = totpViewModel,
                                    bankCardViewModel = bankCardViewModel,
                                    localKeePassViewModel = localKeePassViewModel,
                                    passwordId = null,
                                    initialCategoryId = aggregateStorageDefaults?.categoryId,
                                    initialKeePassDatabaseId = aggregateStorageDefaults?.keepassDatabaseId,
                                    initialKeePassGroupPath = aggregateStorageDefaults?.keepassGroupPath,
                                    initialBitwardenVaultId = aggregateStorageDefaults?.bitwardenVaultId,
                                    initialBitwardenFolderId = aggregateStorageDefaults?.bitwardenFolderId,
                                    onNavigateBack = collapse
                                )
                            }
                            BottomNavItem.Authenticator -> {
                                val totpCategories by totpViewModel.categories.collectAsState()
                                AddEditTotpScreen(
                                    totpId = null,
                                    initialData = null,
                                    initialTitle = "",
                                    initialNotes = "",
                                    initialCategoryId = totpNewItemDefaults.categoryId,
                                    initialKeePassDatabaseId = totpNewItemDefaults.keepassDatabaseId,
                                    initialKeePassGroupPath = totpNewItemDefaults.keepassGroupPath,
                                    initialBitwardenVaultId = totpNewItemDefaults.bitwardenVaultId,
                                    initialBitwardenFolderId = totpNewItemDefaults.bitwardenFolderId,
                                    categories = totpCategories,
                                    passwordViewModel = passwordViewModel,
                                    localKeePassViewModel = localKeePassViewModel,
                                    onSave = { title, notes, totpData, categoryId, keepassDatabaseId, keepassGroupPath, bitwardenVaultId, bitwardenFolderId ->
                                        totpViewModel.saveTotpItem(
                                            id = null,
                                            title = title,
                                            notes = notes,
                                            totpData = totpData,
                                            categoryId = categoryId,
                                            keepassDatabaseId = keepassDatabaseId,
                                            keepassGroupPath = keepassGroupPath,
                                            bitwardenVaultId = bitwardenVaultId,
                                            bitwardenFolderId = bitwardenFolderId
                                        )
                                        collapse()
                                    },
                                    onNavigateBack = collapse,
                                    onScanQrCode = {
                                        collapse()
                                        onNavigateToQuickTotpScan()
                                    }
                                )
                            }
                            BottomNavItem.CardWallet -> {
                                UnifiedWalletAddScreen(
                                    selectedType = walletUnifiedAddType,
                                    onTypeSelected = onWalletUnifiedAddTypeChange,
                                    onNavigateBack = collapse,
                                    bankCardViewModel = bankCardViewModel,
                                    documentViewModel = documentViewModel,
                                    stateHolder = walletAddSaveableStateHolder
                                )
                            }
                            BottomNavItem.Notes -> {
                                AddEditNoteScreen(
                                    noteId = -1L,
                                    onNavigateBack = collapse,
                                    viewModel = noteViewModel
                                )
                            }
                            BottomNavItem.Send -> {
                                AddEditSendScreen(
                                    sendState = sendState,
                                    onNavigateBack = collapse,
                                    onCreate = { title, text, notes, password, maxAccessCount, hideEmail, hiddenText, expireInDays ->
                                        bitwardenViewModel.createTextSend(
                                            title = title,
                                            text = text,
                                            notes = notes,
                                            password = password,
                                            maxAccessCount = maxAccessCount,
                                            hideEmail = hideEmail,
                                            hiddenText = hiddenText,
                                            expireInDays = expireInDays
                                        )
                                        collapse()
                                    }
                                )
                            }
                            BottomNavItem.Generator -> {
                                // Generator 使用全局 FAB 点击回调触发刷新，不走展开页面。
                            }
                            else -> { /* Should not happen */ }
                        }
                    }
                }
            )
        }
    }
}

private data class VaultV2FabMenuAction(
    val icon: ImageVector,
    val labelRes: Int,
    val onClick: () -> Unit,
)

@Composable
private fun VaultV2FabMenu(
    fabBottomOffset: Dp,
    fabContainerColor: Color,
    fabIconTint: Color,
    onExpandStateChanged: (Boolean) -> Unit,
    menuActions: List<VaultV2FabMenuAction>,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val animatedCornerRadius by animateDpAsState(
        targetValue = if (expanded) 28.dp else 16.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "vault_v2_fab_corner"
    )

    fun updateExpanded(next: Boolean) {
        if (expanded == next) return
        expanded = next
        onExpandStateChanged(next)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (expanded) {
                onExpandStateChanged(false)
            }
        }
    }

    BackHandler(enabled = expanded) {
        updateExpanded(false)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(durationMillis = 120)),
            exit = fadeOut(animationSpec = tween(durationMillis = 90)),
            modifier = Modifier.matchParentSize()
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        updateExpanded(false)
                    }
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = fabBottomOffset + 16.dp
                ),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            menuActions.forEachIndexed { index, action ->
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(
                        animationSpec = tween(durationMillis = 160, delayMillis = index * 28)
                    ) + slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = tween(
                            durationMillis = 220,
                            delayMillis = index * 28,
                            easing = LinearEasing
                        )
                    ),
                    exit = fadeOut(animationSpec = tween(durationMillis = 90)) + slideOutVertically(
                        targetOffsetY = { it / 4 },
                        animationSpec = tween(durationMillis = 120)
                    )
                ) {
                    Surface(
                        onClick = {
                            updateExpanded(false)
                            action.onClick()
                        },
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        tonalElevation = 4.dp,
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(action.labelRes),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(animatedCornerRadius),
                color = fabContainerColor,
                contentColor = fabIconTint,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                onClick = { updateExpanded(!expanded) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = stringResource(R.string.add),
                        tint = fabIconTint
                    )
                }
            }
        }
    }
}

private val adaptivePreviewTabs = listOf(
    BottomNavItem.Passwords,
    BottomNavItem.Authenticator,
    BottomNavItem.CardWallet,
    BottomNavItem.Generator,
    BottomNavItem.Notes,
    BottomNavItem.Send,
    BottomNavItem.Passkey,
    BottomNavItem.Settings
)

@Preview(device = "spec:width=411dp,height=891dp", showBackground = true, name = "Adaptive Phone")
@Composable
private fun AdaptiveMainScaffoldPhonePreview() {
    MonicaTheme {
        AdaptiveMainScaffold(
            isCompactWidth = true,
            tabs = adaptivePreviewTabs,
            currentTab = BottomNavItem.Passwords,
            onTabSelected = {}
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Phone Content")
            }
        }
    }
}

@Preview(
    device = "spec:width=1280dp,height=800dp,dpi=240",
    showBackground = true,
    name = "Adaptive Tablet"
)
@Composable
private fun AdaptiveMainScaffoldTabletPreview() {
    MonicaTheme {
        AdaptiveMainScaffold(
            isCompactWidth = false,
            tabs = adaptivePreviewTabs,
            currentTab = BottomNavItem.Passwords,
            onTabSelected = {}
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Tablet Content")
            }
        }
    }
}

@Composable
private fun ListPanePreviewContent(modifier: Modifier = Modifier) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    ListPane(modifier = modifier) {
        ExpressiveTopBar(
            title = "Passwords",
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            isSearchExpanded = isSearchExpanded,
            onSearchExpandedChange = { isSearchExpanded = it },
            searchHint = "Search..."
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(12) { index ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = "Sample Item ${index + 1}",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Preview(device = "spec:width=411dp,height=891dp", showBackground = true, name = "Stage2 ListPane Phone")
@Composable
private fun Stage2ListPanePhonePreview() {
    MonicaTheme {
        ListPanePreviewContent(modifier = Modifier.fillMaxSize())
    }
}

@Preview(
    device = "spec:width=1280dp,height=800dp,dpi=240",
    showBackground = true,
    name = "Stage2 TwoPane Tablet"
)
@Composable
private fun Stage2TwoPaneTabletPreview() {
    MonicaTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            ListPanePreviewContent(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(400.dp)
            )

            DetailPane(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    }
}

@Composable
private fun Stage3TwoPanePreviewContent(selectedPasswordId: Long?) {
    Row(modifier = Modifier.fillMaxSize()) {
        ListPanePreviewContent(
            modifier = Modifier
                .fillMaxHeight()
                .width(400.dp)
        )

        DetailPane(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = selectedPasswordId?.let { "Selected ID: $it" } ?: "Select an item to view details",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(
    device = "spec:width=1280dp,height=800dp,dpi=240",
    showBackground = true,
    name = "Stage3 TwoPane Empty Detail"
)
@Composable
private fun Stage3TwoPaneEmptyDetailPreview() {
    MonicaTheme {
        Stage3TwoPanePreviewContent(selectedPasswordId = null)
    }
}

@Preview(
    device = "spec:width=1280dp,height=800dp,dpi=240",
    showBackground = true,
    name = "Stage3 TwoPane Selected Detail"
)
@Composable
private fun Stage3TwoPaneSelectedDetailPreview() {
    MonicaTheme {
        Stage3TwoPanePreviewContent(selectedPasswordId = 42L)
    }
}
