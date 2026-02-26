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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.fragment.app.FragmentActivity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import takagi.ru.monica.R
import takagi.ru.monica.data.BottomNavContentTab
import takagi.ru.monica.data.PasskeyEntry
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
import takagi.ru.monica.ui.password.getGroupKeyForMode
import takagi.ru.monica.ui.password.getPasswordGroupTitle
import takagi.ru.monica.ui.password.getPasswordInfoKey
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnifiedWalletAddScreen(
    selectedType: CardWalletTab,
    onTypeSelected: (CardWalletTab) -> Unit,
    onNavigateBack: () -> Unit,
    bankCardViewModel: BankCardViewModel,
    documentViewModel: DocumentViewModel,
    stateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
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
    val bitwardenVaultId: Long? = null,
    val bitwardenFolderId: String? = null
)

private fun defaultsFromTotpFilter(filter: takagi.ru.monica.viewmodel.TotpCategoryFilter): NewItemStorageDefaults {
    return when (filter) {
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.Custom -> {
            NewItemStorageDefaults(categoryId = filter.categoryId)
        }
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabase -> {
            NewItemStorageDefaults(keepassDatabaseId = filter.databaseId)
        }
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassGroupFilter -> {
            NewItemStorageDefaults(keepassDatabaseId = filter.databaseId)
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

private data class BitwardenBottomStatusUiState(
    val messageRes: Int? = null,
    val showProgress: Boolean = false
)

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
    onNavigateToAddNote: (Long?) -> Unit,
    onNavigateToPasswordDetail: (Long) -> Unit = {},
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
    onNavigateToPageCustomization: () -> Unit = {},
    onNavigateToBitwardenLogin: () -> Unit = {},
    onClearAllData: (Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    initialTab: Int = 0
) {

    // Bitwarden ViewModel
    val bitwardenViewModel: takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel = viewModel()
    val timelineViewModel: TimelineViewModel = viewModel()
    
    // 双击返回退出相关状态
    var backPressedOnce by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bitwardenRepository = remember { takagi.ru.monica.bitwarden.repository.BitwardenRepository.getInstance(context) }
    
    // 处理返回键 - 需要按两次才能退出
    // 只有在没有子页面（如添加页面）打开时才启用
    // FAB 展开状态由内部 SwipeableAddFab 管理，这里不需要干预，除非我们需要在 FAB 展开时拦截返回键
    // 目前 SwipeableAddFab 应该自己处理了返回键（如果有 BackHandler）
    // 为了安全起见，我们只在最外层处理
    BackHandler(enabled = true) {
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
    
    // 密码列表的选择模式状态
    var isPasswordSelectionMode by remember { mutableStateOf(false) }
    var selectedPasswordCount by remember { mutableIntStateOf(0) }
    var onExitPasswordSelection by remember { mutableStateOf({}) }
    var onSelectAllPasswords by remember { mutableStateOf({}) }
    var onFavoriteSelectedPasswords by remember { mutableStateOf({}) }
    var onMoveToCategoryPasswords by remember { mutableStateOf({}) }
    var onDeleteSelectedPasswords by remember { mutableStateOf({}) }
    
    val appSettings by settingsViewModel.settings.collectAsState()
    
    // 密码分组模式: smart(备注>网站>应用>标题), note, website, app, title
    // 从设置中读取，如果设置中没有则默认为 "smart"
    val passwordGroupMode = appSettings.passwordGroupMode


    // 堆叠卡片显示模式: 自动/始终展开（始终展开指逐条显示，不堆叠）
    // 从设置中读取，如果设置中没有则默认为 AUTO
    val stackCardModeKey = appSettings.stackCardMode
    val stackCardMode = remember(stackCardModeKey) {
        runCatching { StackCardMode.valueOf(stackCardModeKey) }.getOrDefault(StackCardMode.AUTO)
    }
    var displayMenuExpanded by remember { mutableStateOf(false) }
    
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
    val currentTabLabel = stringResource(currentTab.fullLabelRes())
    var selectedPasswordId by rememberSaveable { mutableStateOf<Long?>(null) }
    var inlinePasswordEditorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isAddingPasswordInline by rememberSaveable { mutableStateOf(false) }
    var selectedTotpId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isAddingTotpInline by rememberSaveable { mutableStateOf(false) }
    var selectedBankCardId by rememberSaveable { mutableStateOf<Long?>(null) }
    var inlineBankCardEditorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isAddingBankCardInline by rememberSaveable { mutableStateOf(false) }
    var selectedDocumentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var inlineDocumentEditorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isAddingDocumentInline by rememberSaveable { mutableStateOf(false) }
    var inlineNoteEditorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isAddingNoteInline by rememberSaveable { mutableStateOf(false) }
    var isAddingSendInline by rememberSaveable { mutableStateOf(false) }
    var selectedPasskey by remember { mutableStateOf<PasskeyEntry?>(null) }
    var pendingPasskeyDelete by remember { mutableStateOf<PasskeyEntry?>(null) }
    var selectedSend by remember { mutableStateOf<BitwardenSend?>(null) }
    var selectedTimelineLog by remember { mutableStateOf<TimelineEvent.StandardLog?>(null) }
    val sendState by bitwardenViewModel.sendState.collectAsState()
    val activeBitwardenVault by bitwardenViewModel.activeVault.collectAsState()
    val bitwardenSyncStatusByVault by bitwardenViewModel.syncStatusByVault.collectAsState()
    val totpFilter by totpViewModel.categoryFilter.collectAsState()
    val totpNewItemDefaults = remember(totpFilter) { defaultsFromTotpFilter(totpFilter) }
    val nowMs by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }

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
    // 使用 rememberUpdatedState 确保 currentTab 始终是最新的
    val currentTabState = rememberUpdatedState(currentTab)
    // 确保滚动监听器能获取到最新的设置值
    val hideFabOnScrollState = rememberUpdatedState(appSettings.hideFabOnScroll)

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

                // 如果 FAB 已展开，不要隐藏它（防止在添加页面滚动时误触导致页面关闭）
                if (isFabExpanded) return Offset.Zero

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

    val categories by passwordViewModel.categories.collectAsState()
    val currentFilter by passwordViewModel.categoryFilter.collectAsState()
    val allPasswords by passwordViewModel.allPasswords.collectAsState(initial = emptyList())
    val localPasskeys by passkeyViewModel.allPasskeys.collectAsState(initial = emptyList())
    val passkeyTotalCount = localPasskeys.size
    val passkeyBoundCount = localPasskeys.count { it.boundPasswordId != null }
    val passwordById = remember(allPasswords) { allPasswords.associateBy { it.id } }
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
    
    // 获取颜色（在 Composable 上下文中）
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    
    // 构建快捷操作列表
    val quickActions = remember(
        onNavigateToAddPassword,
        onNavigateToAddTotp,
        onNavigateToQuickTotpScan,
        onNavigateToAddBankCard,
        onNavigateToAddDocument,
        onNavigateToAddNote,
        onSecurityAnalysis,
        onNavigateToSyncBackup,
        tertiaryColor,
        secondaryColor
    ) {
        listOf(
            QuickActionItem(
                icon = Icons.Default.Lock,
                labelRes = R.string.quick_action_add_password,
                onClick = { onNavigateToAddPassword(null) }
            ),
            QuickActionItem(
                icon = Icons.Default.Security,
                labelRes = R.string.quick_action_add_totp,
                onClick = { onNavigateToAddTotp(null) }
            ),
            QuickActionItem(
                icon = Icons.Default.QrCodeScanner,
                labelRes = R.string.quick_action_scan_qr,
                onClick = onNavigateToQuickTotpScan
            ),
            QuickActionItem(
                icon = Icons.Default.CreditCard,
                labelRes = R.string.quick_action_add_card,
                onClick = { onNavigateToAddBankCard(null) }
            ),
            QuickActionItem(
                icon = Icons.Default.Badge,
                labelRes = R.string.quick_action_add_document,
                onClick = { onNavigateToAddDocument(null) }
            ),
            QuickActionItem(
                icon = Icons.Default.Note,
                labelRes = R.string.quick_action_add_note,
                onClick = { onNavigateToAddNote(null) }
            ),
            QuickActionItem(
                icon = Icons.Default.AutoAwesome,
                labelRes = R.string.quick_action_generator,
                onClick = { selectedTabKey = BottomNavItem.Generator.key }
            ),
            QuickActionItem(
                icon = Icons.Default.Shield,
                labelRes = R.string.quick_action_security,
                onClick = onSecurityAnalysis,
                tint = tertiaryColor
            ),
            QuickActionItem(
                icon = Icons.Default.CloudUpload,
                labelRes = R.string.quick_action_backup,
                onClick = onNavigateToSyncBackup,
                tint = secondaryColor
            ),
            QuickActionItem(
                icon = Icons.Default.Download,
                labelRes = R.string.quick_action_import,
                onClick = onNavigateToSyncBackup
            ),
            QuickActionItem(
                icon = Icons.Default.Settings,
                labelRes = R.string.quick_action_settings,
                onClick = { selectedTabKey = BottomNavItem.Settings.key }
            )
        )
    }

    val activity = LocalContext.current.findActivity()
    val widthSizeClass = activity?.let { calculateWindowSizeClass(it).widthSizeClass }
    val isCompactWidth = widthSizeClass == null || widthSizeClass == WindowWidthSizeClass.Compact
    val wideListPaneWidth = 400.dp
    val wideNavigationRailWidth = 80.dp
    val wideFabHostWidth = wideNavigationRailWidth + wideListPaneWidth

    val handlePasswordAddOpen: () -> Unit = {
        if (isCompactWidth) {
            onNavigateToAddPassword(null)
        } else {
            isAddingPasswordInline = true
            inlinePasswordEditorId = null
            selectedPasswordId = null
        }
    }
    val handlePasswordEditOpen: (Long) -> Unit = { passwordId ->
        if (isCompactWidth) {
            onNavigateToAddPassword(passwordId)
        } else {
            isAddingPasswordInline = false
            inlinePasswordEditorId = passwordId
        }
    }
    val handleInlinePasswordEditorBack: () -> Unit = {
        isAddingPasswordInline = false
        inlinePasswordEditorId = null
    }
    val handleTotpAddOpen: () -> Unit = {
        if (isCompactWidth) {
            onNavigateToAddTotp(null)
        } else {
            isAddingTotpInline = true
            selectedTotpId = null
        }
    }
    val handleInlineTotpEditorBack: () -> Unit = {
        isAddingTotpInline = false
        selectedTotpId = null
    }
    val handleBankCardAddOpen: () -> Unit = {
        if (isCompactWidth) {
            onNavigateToAddBankCard(null)
        } else {
            isAddingBankCardInline = true
            inlineBankCardEditorId = null
            selectedBankCardId = null
            isAddingDocumentInline = false
            inlineDocumentEditorId = null
            selectedDocumentId = null
        }
    }
    val handleBankCardEditOpen: (Long) -> Unit = { cardId ->
        if (isCompactWidth) {
            onNavigateToAddBankCard(cardId)
        } else {
            isAddingBankCardInline = false
            inlineBankCardEditorId = cardId
            selectedBankCardId = null
            isAddingDocumentInline = false
            inlineDocumentEditorId = null
            selectedDocumentId = null
        }
    }
    val handleInlineBankCardEditorBack: () -> Unit = {
        isAddingBankCardInline = false
        inlineBankCardEditorId = null
    }
    val handleDocumentAddOpen: () -> Unit = {
        if (isCompactWidth) {
            onNavigateToAddDocument(null)
        } else {
            isAddingDocumentInline = true
            inlineDocumentEditorId = null
            selectedDocumentId = null
            isAddingBankCardInline = false
            inlineBankCardEditorId = null
            selectedBankCardId = null
        }
    }
    val handleDocumentEditOpen: (Long) -> Unit = { documentId ->
        if (isCompactWidth) {
            onNavigateToAddDocument(documentId)
        } else {
            isAddingDocumentInline = false
            inlineDocumentEditorId = documentId
            selectedDocumentId = null
            isAddingBankCardInline = false
            inlineBankCardEditorId = null
            selectedBankCardId = null
        }
    }
    val handleInlineDocumentEditorBack: () -> Unit = {
        isAddingDocumentInline = false
        inlineDocumentEditorId = null
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
            if (noteId == null) {
                isAddingNoteInline = true
                inlineNoteEditorId = null
            } else {
                isAddingNoteInline = false
                inlineNoteEditorId = noteId
            }
        }
    }
    val handleInlineNoteEditorBack: () -> Unit = {
        isAddingNoteInline = false
        inlineNoteEditorId = null
    }

    val handlePasswordDetailOpen: (Long) -> Unit = { passwordId ->
        if (isCompactWidth) {
            onNavigateToPasswordDetail(passwordId)
        } else {
            isAddingPasswordInline = false
            inlinePasswordEditorId = null
            selectedPasswordId = passwordId
        }
    }
    val handleTotpOpen: (Long) -> Unit = { totpId ->
        if (isCompactWidth) {
            onNavigateToAddTotp(totpId)
        } else {
            isAddingTotpInline = false
            selectedTotpId = totpId
        }
    }
    val handleBankCardOpen: (Long) -> Unit = { cardId ->
        if (isCompactWidth) {
            onNavigateToBankCardDetail(cardId)
        } else {
            isAddingBankCardInline = false
            inlineBankCardEditorId = null
            selectedBankCardId = cardId
            isAddingDocumentInline = false
            inlineDocumentEditorId = null
            selectedDocumentId = null
        }
    }
    val handleDocumentOpen: (Long) -> Unit = { documentId ->
        if (isCompactWidth) {
            onNavigateToDocumentDetail(documentId)
        } else {
            isAddingDocumentInline = false
            inlineDocumentEditorId = null
            selectedDocumentId = documentId
            isAddingBankCardInline = false
            inlineBankCardEditorId = null
            selectedBankCardId = null
        }
    }
    val handlePasskeyOpen: (PasskeyEntry) -> Unit = { passkey ->
        if (!isCompactWidth) {
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
            isAddingSendInline = false
            selectedSend = send
        }
    }
    val handleSendAddOpen: () -> Unit = {
        if (!isCompactWidth) {
            selectedSend = null
            isAddingSendInline = true
        }
    }
    val handleInlineSendEditorBack: () -> Unit = {
        isAddingSendInline = false
    }
    val handleTimelineLogOpen: (TimelineEvent.StandardLog) -> Unit = { log ->
        if (!isCompactWidth) {
            selectedTimelineLog = log
        }
    }

    LaunchedEffect(currentTab.key, isCompactWidth) {
        if (isCompactWidth || currentTab != BottomNavItem.Passwords) {
            selectedPasswordId = null
            inlinePasswordEditorId = null
            isAddingPasswordInline = false
        }
    }
    LaunchedEffect(currentTab.key, isCompactWidth) {
        if (isCompactWidth || currentTab != BottomNavItem.Authenticator) {
            selectedTotpId = null
            isAddingTotpInline = false
        }
    }
    LaunchedEffect(currentTab.key, isCompactWidth, cardWalletSubTab) {
        if (isCompactWidth || currentTab != BottomNavItem.CardWallet) {
            selectedBankCardId = null
            selectedDocumentId = null
            inlineBankCardEditorId = null
            isAddingBankCardInline = false
            inlineDocumentEditorId = null
            isAddingDocumentInline = false
        } else {
            when (cardWalletSubTab) {
                CardWalletTab.BANK_CARDS -> {
                    selectedDocumentId = null
                    inlineDocumentEditorId = null
                    isAddingDocumentInline = false
                }
                CardWalletTab.DOCUMENTS -> {
                    selectedBankCardId = null
                    inlineBankCardEditorId = null
                    isAddingBankCardInline = false
                }
                CardWalletTab.ALL -> Unit
            }
        }
    }
    LaunchedEffect(cardWalletSubTab) {
        if (cardWalletSubTab == CardWalletTab.BANK_CARDS || cardWalletSubTab == CardWalletTab.DOCUMENTS) {
            walletUnifiedAddType = cardWalletSubTab
        }
    }
    LaunchedEffect(currentTab.key, isCompactWidth) {
        if (isCompactWidth || currentTab != BottomNavItem.Notes) {
            inlineNoteEditorId = null
            isAddingNoteInline = false
        }
    }
    LaunchedEffect(currentTab.key, isCompactWidth) {
        if (isCompactWidth || currentTab != BottomNavItem.Passkey) {
            selectedPasskey = null
        }
    }
    LaunchedEffect(currentTab.key, isCompactWidth) {
        if (isCompactWidth || currentTab != BottomNavItem.Send) {
            selectedSend = null
            isAddingSendInline = false
        }
    }
    LaunchedEffect(currentTab.key, isCompactWidth) {
        if (isCompactWidth || currentTab != BottomNavItem.Timeline) {
            selectedTimelineLog = null
        }
    }

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
        BottomNavItem.Passwords -> isBitwardenPasswordFilter(currentFilter)
        BottomNavItem.Authenticator -> isBitwardenTotpFilter(totpFilter)
        BottomNavItem.CardWallet,
        BottomNavItem.Notes,
        BottomNavItem.Passkey,
        BottomNavItem.Send -> activeBitwardenVault != null
        else -> false
    }
    val activeVaultSyncState = activeBitwardenVault?.id?.let(bitwardenSyncStatusByVault::get)
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
    LaunchedEffect(shouldHandleBitwardenStatusVisual, bottomStatusUiState?.messageRes) {
        if (!shouldHandleBitwardenStatusVisual || bottomStatusUiState?.messageRes == null) {
            statusHintVisible = false
            return@LaunchedEffect
        }
        statusHintVisible = true
        delay(if (bottomStatusUiState.showProgress) 2600L else 3600L)
        statusHintVisible = false
    }

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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (currentTab) {
                        BottomNavItem.Passwords -> {
                            PasswordListContent(
                                viewModel = passwordViewModel,
                                settingsViewModel = settingsViewModel,
                                securityManager = securityManager,
                                keepassDatabases = keepassDatabases,
                                bitwardenVaults = bitwardenVaults,
                                localKeePassViewModel = localKeePassViewModel,
                                groupMode = passwordGroupMode,
                                stackCardMode = stackCardMode,
                                onRenameCategory = { category ->
                                    passwordViewModel.updateCategory(category)
                                },
                                onDeleteCategory = { category ->
                                    passwordViewModel.deleteCategory(category)
                                },
                                onPasswordClick = { password ->
                                    handlePasswordDetailOpen(password.id)
                                },
                                onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onFavorite, onMoveToCategory, onDelete ->
                                    isPasswordSelectionMode = isSelectionMode
                                    selectedPasswordCount = count
                                    onExitPasswordSelection = onExit
                                    onSelectAllPasswords = onSelectAll
                                    onFavoriteSelectedPasswords = onFavorite
                                    onMoveToCategoryPasswords = onMoveToCategory
                                    onDeleteSelectedPasswords = onDelete
                                }
                            )
                        }
                        BottomNavItem.Authenticator -> {
                            TotpListContent(
                                viewModel = totpViewModel,
                                passwordViewModel = passwordViewModel,
                                onTotpClick = { totpId ->
                                    handleTotpOpen(totpId)
                                },
                                onDeleteTotp = { totp ->
                                    totpViewModel.deleteTotpItem(totp)
                                },
                                onQuickScanTotp = onNavigateToQuickTotpScan,
                                onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onMoveToCategory, onDelete ->
                                    isTotpSelectionMode = isSelectionMode
                                    selectedTotpCount = count
                                    onExitTotpSelection = onExit
                                    onSelectAllTotp = onSelectAll
                                    onMoveToCategoryTotp = onMoveToCategory
                                    onDeleteSelectedTotp = onDelete
                                }
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
                                onRefreshRequestConsumed = { generatorRefreshRequestKey = 0 },
                                useExternalRefreshFab = true
                            )
                        }
                        BottomNavItem.Notes -> {
                            NoteListScreen(
                                viewModel = noteViewModel,
                                settingsViewModel = settingsViewModel,
                                onNavigateToAddNote = handleNoteOpen,
                                securityManager = securityManager,
                                onSelectionModeChange = { isSelectionMode ->
                                    isNoteSelectionMode = isSelectionMode
                                }
                            )
                        }
                        BottomNavItem.Timeline -> {
                            TimelineScreen(
                                viewModel = timelineViewModel,
                                splitPaneMode = false
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
                                bitwardenViewModel = bitwardenViewModel
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

                    // Selection Action Bars
                    when {
                        currentTab == BottomNavItem.Passwords && isPasswordSelectionMode -> {
                            SelectionActionBar(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                                selectedCount = selectedPasswordCount,
                                onExit = onExitPasswordSelection,
                                onSelectAll = onSelectAllPasswords,
                                onFavorite = onFavoriteSelectedPasswords,
                                onMoveToCategory = onMoveToCategoryPasswords,
                                onDelete = onDeleteSelectedPasswords
                            )
                        }
                        currentTab == BottomNavItem.Authenticator && isTotpSelectionMode -> {
                            SelectionActionBar(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
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
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
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
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                                selectedCount = selectedDocumentCount,
                                onExit = onExitDocumentSelection,
                                onSelectAll = onSelectAllDocuments,
                                onMoveToCategory = onMoveToCategoryDocuments,
                                onDelete = onDeleteSelectedDocuments
                            )
                        }
                    }
                }
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
                    AnimatedVisibility(
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
                BottomNavItem.Passwords -> {
                    val listPaneContent: @Composable ColumnScope.() -> Unit = {
                        PasswordListContent(
                            viewModel = passwordViewModel,
                            settingsViewModel = settingsViewModel, // Pass SettingsViewModel
                            securityManager = securityManager,
                            keepassDatabases = keepassDatabases,
                            bitwardenVaults = bitwardenVaults,
                            localKeePassViewModel = localKeePassViewModel,
                            groupMode = passwordGroupMode,
                            stackCardMode = stackCardMode,
                            onRenameCategory = { category ->
                                passwordViewModel.updateCategory(category)
                            },
                            onDeleteCategory = { category ->
                                passwordViewModel.deleteCategory(category)
                            },
                            onPasswordClick = { password ->
                                handlePasswordDetailOpen(password.id)
                            },
                            onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onFavorite, onMoveToCategory, onDelete ->
                                isPasswordSelectionMode = isSelectionMode
                                selectedPasswordCount = count
                                onExitPasswordSelection = onExit
                                onSelectAllPasswords = onSelectAll
                                onFavoriteSelectedPasswords = onFavorite
                                onMoveToCategoryPasswords = onMoveToCategory
                                onDeleteSelectedPasswords = onDelete
                            }
                        )
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
                                        onNavigateBack = handleInlinePasswordEditorBack
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
                                            passwordId = selectedPasswordId!!,
                                            disablePasswordVerification = appSettings.disablePasswordVerification,
                                            biometricEnabled = appSettings.biometricEnabled,
                                            onNavigateBack = { selectedPasswordId = null },
                                            onEditPassword = handlePasswordEditOpen,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                BottomNavItem.Authenticator -> {
                    val listPaneContent: @Composable ColumnScope.() -> Unit = {
                        TotpListContent(
                            viewModel = totpViewModel,
                            passwordViewModel = passwordViewModel,
                            onTotpClick = { totpId ->
                                handleTotpOpen(totpId)
                            },
                            onDeleteTotp = { totp ->
                                totpViewModel.deleteTotpItem(totp)
                            },
                            onQuickScanTotp = onNavigateToQuickTotpScan,
                            onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onMoveToCategory, onDelete ->
                                isTotpSelectionMode = isSelectionMode
                                selectedTotpCount = count
                                onExitTotpSelection = onExit
                                onSelectAllTotp = onSelectAll
                                onMoveToCategoryTotp = onMoveToCategory
                                onDeleteSelectedTotp = onDelete
                            }
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
                                        initialBitwardenVaultId = totpNewItemDefaults.bitwardenVaultId,
                                        initialBitwardenFolderId = totpNewItemDefaults.bitwardenFolderId,
                                        categories = totpCategories,
                                        passwordViewModel = passwordViewModel,
                                        localKeePassViewModel = localKeePassViewModel,
                                        onSave = { title, notes, totpData, categoryId, keepassDatabaseId, bitwardenVaultId, bitwardenFolderId ->
                                            totpViewModel.saveTotpItem(
                                                id = null,
                                                title = title,
                                                notes = notes,
                                                totpData = totpData,
                                                categoryId = categoryId,
                                                keepassDatabaseId = keepassDatabaseId,
                                                bitwardenVaultId = bitwardenVaultId,
                                                bitwardenFolderId = bitwardenFolderId
                                            )
                                            handleInlineTotpEditorBack()
                                        },
                                        onNavigateBack = handleInlineTotpEditorBack,
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
                                        initialBitwardenVaultId = selectedTotpItem.bitwardenVaultId,
                                        initialBitwardenFolderId = selectedTotpItem.bitwardenFolderId,
                                        categories = totpCategories,
                                        passwordViewModel = passwordViewModel,
                                        localKeePassViewModel = localKeePassViewModel,
                                        onSave = { title, notes, totpData, categoryId, keepassDatabaseId, bitwardenVaultId, bitwardenFolderId ->
                                            totpViewModel.saveTotpItem(
                                                id = selectedTotpItem.id,
                                                title = title,
                                                notes = notes,
                                                totpData = totpData,
                                                categoryId = categoryId,
                                                keepassDatabaseId = keepassDatabaseId,
                                                bitwardenVaultId = bitwardenVaultId,
                                                bitwardenFolderId = bitwardenFolderId
                                            )
                                        },
                                        onNavigateBack = handleInlineTotpEditorBack,
                                        onScanQrCode = onNavigateToQuickTotpScan,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
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
                        onClearSelectedBankCard = { selectedBankCardId = null },
                        onEditBankCard = handleBankCardEditOpen,
                        selectedDocumentId = selectedDocumentId,
                        onClearSelectedDocument = { selectedDocumentId = null },
                        onEditDocument = handleDocumentEditOpen
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
                        onInlineNoteEditorBack = handleInlineNoteEditorBack
                    )
                }
                BottomNavItem.Timeline -> {
                    TimelineScreen(
                        viewModel = timelineViewModel,
                        onLogSelected = handleTimelineLogOpen,
                        splitPaneMode = !isCompactWidth
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
                        }
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

            when {
                currentTab == BottomNavItem.Passwords && isPasswordSelectionMode -> {
                    SelectionActionBar(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 20.dp),
                        selectedCount = selectedPasswordCount,
                        onExit = onExitPasswordSelection,
                        onSelectAll = onSelectAllPasswords,
                        onFavorite = onFavoriteSelectedPasswords,
                        onMoveToCategory = onMoveToCategoryPasswords,
                        onDelete = onDeleteSelectedPasswords
                    )
                }

                currentTab == BottomNavItem.Authenticator && isTotpSelectionMode -> {
                    SelectionActionBar(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 20.dp),
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
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 20.dp),
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
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 20.dp),
                        selectedCount = selectedDocumentCount,
                        onExit = onExitDocumentSelection,
                        onSelectAll = onSelectAllDocuments,
                        onMoveToCategory = onMoveToCategoryDocuments,
                        onDelete = onDeleteSelectedDocuments
                    )
                }
            }
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

    // 全局 FAB Overlay
    // 放在最外层 Box 中，覆盖在 Scaffold 之上，确保能展开到全屏
    // 仅在特定 Tab 显示，并且不在多选模式下显示
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
        currentTab == BottomNavItem.Passwords ||
            currentTab == BottomNavItem.Authenticator ||
            currentTab == BottomNavItem.CardWallet ||
            currentTab == BottomNavItem.Generator ||
            currentTab == BottomNavItem.Notes ||
            currentTab == BottomNavItem.Send
        ) && !isAnySelectionMode && !hasWideDetailSelection

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
    
    AnimatedVisibility(
        visible = showFab && isFabVisible,
        enter = slideInHorizontally(initialOffsetX = { it * 2 }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it * 2 }) + fadeOut(),
        modifier = fabOverlayModifier
    ) {
        SwipeableAddFab(
            // 通过内部参数控制 FAB 位置，确保容器本身是全屏的
            // NavigationBar 高度约 80dp + 系统导航条高度 + 边距
            fabBottomOffset = if (isCompactWidth) 116.dp else 24.dp,
            fabContainerColor = fabContainerColor,
            modifier = Modifier,
            onFabClickOverride = when (currentTab) {
                BottomNavItem.Passwords -> if (isCompactWidth) null else ({ handlePasswordAddOpen() })
                BottomNavItem.Authenticator -> if (isCompactWidth) null else ({ handleTotpAddOpen() })
                BottomNavItem.CardWallet -> if (isCompactWidth || cardWalletSubTab == CardWalletTab.ALL) {
                    null
                } else {
                    ({ handleWalletAddOpen() })
                }
                BottomNavItem.Notes -> if (isCompactWidth) null else ({ handleNoteOpen(null) })
                BottomNavItem.Send -> if (isCompactWidth) null else ({ handleSendAddOpen() })
                BottomNavItem.Generator -> ({ generatorRefreshRequestKey++ })
                else -> null
            },
            onExpandStateChanged = { expanded -> isFabExpanded = expanded },
            fabContent = { expand ->
            when (currentTab) {
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
                    BottomNavItem.Passwords -> {
                        AddEditPasswordScreen(
                            viewModel = passwordViewModel,
                            totpViewModel = totpViewModel,
                            bankCardViewModel = bankCardViewModel,
                            localKeePassViewModel = localKeePassViewModel,
                            passwordId = null,
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
                            initialBitwardenVaultId = totpNewItemDefaults.bitwardenVaultId,
                            initialBitwardenFolderId = totpNewItemDefaults.bitwardenFolderId,
                            categories = totpCategories,
                            passwordViewModel = passwordViewModel,
                            localKeePassViewModel = localKeePassViewModel,
                            onSave = { title, notes, totpData, categoryId, keepassDatabaseId, bitwardenVaultId, bitwardenFolderId ->
                                totpViewModel.saveTotpItem(
                                    id = null,
                                    title = title,
                                    notes = notes,
                                    totpData = totpData,
                                    categoryId = categoryId,
                                    keepassDatabaseId = keepassDatabaseId,
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
                            onTypeSelected = { walletUnifiedAddType = it },
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
    } // End if (showFab)

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
    AnimatedVisibility(
        visible = statusHintVisible && shouldHandleBitwardenStatusVisual && bottomStatusUiState?.messageRes != null,
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
        ) + fadeOut(),
        modifier = hintModifier
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 2.dp,
            color = fabContainerColor
        ) {
            Text(
                text = stringResource(bottomStatusUiState?.messageRes ?: R.string.sync_status_syncing_short),
                style = MaterialTheme.typography.labelLarge,
                color = fabIconTint,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
    } // End Outer Box

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

private val adaptivePreviewTabs = listOf(
    BottomNavItem.Passwords,
    BottomNavItem.Authenticator,
    BottomNavItem.CardWallet,
    BottomNavItem.Generator,
    BottomNavItem.Notes,
    BottomNavItem.Send,
    BottomNavItem.Timeline,
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





