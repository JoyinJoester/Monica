package takagi.ru.monica.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.R
import takagi.ru.monica.data.BottomNavContentTab
import takagi.ru.monica.data.CategorySelectionUiMode
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordListTopModule
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.OperationLogItemType
import takagi.ru.monica.data.model.PasskeyBindingCodec
import takagi.ru.monica.data.model.TIMELINE_FIELD_BATCH_COPY_PAYLOAD
import takagi.ru.monica.data.model.TIMELINE_FIELD_BATCH_MOVE_PAYLOAD
import takagi.ru.monica.data.model.TimelineBatchCopyPayload
import takagi.ru.monica.data.model.TimelineBatchMovePayload
import takagi.ru.monica.data.model.TimelinePasswordLocationState
import takagi.ru.monica.data.model.TimelineEvent
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.utils.FieldChange
import takagi.ru.monica.utils.OperationLogger
import takagi.ru.monica.utils.decodeKeePassPathForDisplay
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
import takagi.ru.monica.ui.components.CreateCategoryDialog
import takagi.ru.monica.ui.components.MonicaExpressiveFilterChip
import takagi.ru.monica.ui.components.UnifiedCategoryFilterChipMenuDropdown
import takagi.ru.monica.ui.components.UnifiedCategoryFilterChipMenuOffset
import takagi.ru.monica.ui.components.UnifiedCategoryFilterBottomSheet
import takagi.ru.monica.ui.components.UnifiedCategoryFilterSelection
import takagi.ru.monica.ui.components.UnifiedMoveAction
import takagi.ru.monica.ui.components.UnifiedMoveCategoryTarget
import takagi.ru.monica.ui.components.UnifiedMoveToCategoryBottomSheet
import takagi.ru.monica.ui.components.UNIFIED_MOVE_ARCHIVE_SENTINEL_CATEGORY_ID
import takagi.ru.monica.ui.components.rememberUnifiedCategoryFilterChipMenuWidth
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
import takagi.ru.monica.ui.icons.MonicaIcons
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
import takagi.ru.monica.bitwarden.sync.SyncStatus
import takagi.ru.monica.security.SecurityManager
import sh.calvin.reorderable.rememberReorderableLazyListState
import takagi.ru.monica.ui.screens.AddEditPasswordScreen
import takagi.ru.monica.ui.screens.AddEditTotpScreen
import takagi.ru.monica.ui.screens.AddEditBankCardScreen
import takagi.ru.monica.ui.screens.AddEditDocumentScreen
import takagi.ru.monica.ui.screens.AddEditNoteScreen
import takagi.ru.monica.ui.screens.AddEditSendScreen
import takagi.ru.monica.ui.theme.MonicaTheme
import java.util.concurrent.CancellationException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val stringSetSaver = Saver<Set<String>, ArrayList<String>>(
    save = { value -> ArrayList(value) },
    restore = { saved -> saved.toSet() }
)

private const val FAST_SCROLL_LOG_TAG = "PasswordFastScroll"

private val timelineBatchJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
}

private fun toLocationState(entry: PasswordEntry): TimelinePasswordLocationState {
    return TimelinePasswordLocationState(
        id = entry.id,
        categoryId = entry.categoryId,
        keepassDatabaseId = entry.keepassDatabaseId,
        keepassGroupPath = entry.keepassGroupPath,
        bitwardenVaultId = entry.bitwardenVaultId,
        bitwardenFolderId = entry.bitwardenFolderId,
        bitwardenLocalModified = entry.bitwardenLocalModified,
        isArchived = entry.isArchived,
        archivedAtMillis = entry.archivedAt?.time
    )
}

private fun toMovedLocationState(
    entry: PasswordEntry,
    target: UnifiedMoveCategoryTarget
): TimelinePasswordLocationState {
    val archivedAt = if (target is UnifiedMoveCategoryTarget.MonicaCategory &&
        target.categoryId == UNIFIED_MOVE_ARCHIVE_SENTINEL_CATEGORY_ID
    ) {
        entry.archivedAt?.time ?: System.currentTimeMillis()
    } else {
        null
    }

    return when (target) {
        UnifiedMoveCategoryTarget.Uncategorized -> TimelinePasswordLocationState(
            id = entry.id,
            categoryId = null,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            bitwardenVaultId = null,
            bitwardenFolderId = null,
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAtMillis = null
        )

        is UnifiedMoveCategoryTarget.MonicaCategory -> {
            if (target.categoryId == UNIFIED_MOVE_ARCHIVE_SENTINEL_CATEGORY_ID) {
                TimelinePasswordLocationState(
                    id = entry.id,
                    categoryId = null,
                    keepassDatabaseId = null,
                    keepassGroupPath = null,
                    bitwardenVaultId = null,
                    bitwardenFolderId = null,
                    bitwardenLocalModified = false,
                    isArchived = true,
                    archivedAtMillis = archivedAt
                )
            } else {
                TimelinePasswordLocationState(
                    id = entry.id,
                    categoryId = target.categoryId,
                    keepassDatabaseId = null,
                    keepassGroupPath = null,
                    bitwardenVaultId = null,
                    bitwardenFolderId = null,
                    bitwardenLocalModified = false,
                    isArchived = false,
                    archivedAtMillis = null
                )
            }
        }

        is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> TimelinePasswordLocationState(
            id = entry.id,
            categoryId = null,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            bitwardenVaultId = target.vaultId,
            bitwardenFolderId = "",
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAtMillis = null
        )

        is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> TimelinePasswordLocationState(
            id = entry.id,
            categoryId = null,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            bitwardenVaultId = target.vaultId,
            bitwardenFolderId = target.folderId,
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAtMillis = null
        )

        is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> TimelinePasswordLocationState(
            id = entry.id,
            categoryId = null,
            keepassDatabaseId = target.databaseId,
            keepassGroupPath = null,
            bitwardenVaultId = null,
            bitwardenFolderId = null,
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAtMillis = null
        )

        is UnifiedMoveCategoryTarget.KeePassGroupTarget -> TimelinePasswordLocationState(
            id = entry.id,
            categoryId = null,
            keepassDatabaseId = target.databaseId,
            keepassGroupPath = target.groupPath,
            bitwardenVaultId = null,
            bitwardenFolderId = null,
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAtMillis = null
        )
    }
}

private fun buildCopiedEntryForTarget(
    entry: PasswordEntry,
    target: UnifiedMoveCategoryTarget
): PasswordEntry {
    val now = Date()
    return when (target) {
        UnifiedMoveCategoryTarget.Uncategorized -> entry.copy(
            id = 0,
            createdAt = now,
            updatedAt = now,
            categoryId = null,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            bitwardenVaultId = null,
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAt = null,
            isDeleted = false,
            deletedAt = null
        )

        is UnifiedMoveCategoryTarget.MonicaCategory -> {
            if (target.categoryId == UNIFIED_MOVE_ARCHIVE_SENTINEL_CATEGORY_ID) {
                entry.copy(
                    id = 0,
                    createdAt = now,
                    updatedAt = now,
                    categoryId = null,
                    keepassDatabaseId = null,
                    keepassGroupPath = null,
                    bitwardenVaultId = null,
                    bitwardenCipherId = null,
                    bitwardenFolderId = null,
                    bitwardenRevisionDate = null,
                    bitwardenLocalModified = false,
                    isArchived = true,
                    archivedAt = now,
                    isDeleted = false,
                    deletedAt = null
                )
            } else {
                entry.copy(
                    id = 0,
                    createdAt = now,
                    updatedAt = now,
                    categoryId = target.categoryId,
                    keepassDatabaseId = null,
                    keepassGroupPath = null,
                    bitwardenVaultId = null,
                    bitwardenCipherId = null,
                    bitwardenFolderId = null,
                    bitwardenRevisionDate = null,
                    bitwardenLocalModified = false,
                    isArchived = false,
                    archivedAt = null,
                    isDeleted = false,
                    deletedAt = null
                )
            }
        }

        is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> entry.copy(
            id = 0,
            createdAt = now,
            updatedAt = now,
            categoryId = null,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            bitwardenVaultId = target.vaultId,
            bitwardenCipherId = null,
            bitwardenFolderId = "",
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAt = null,
            isDeleted = false,
            deletedAt = null
        )

        is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> entry.copy(
            id = 0,
            createdAt = now,
            updatedAt = now,
            categoryId = null,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            bitwardenVaultId = target.vaultId,
            bitwardenCipherId = null,
            bitwardenFolderId = target.folderId,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAt = null,
            isDeleted = false,
            deletedAt = null
        )

        is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> entry.copy(
            id = 0,
            createdAt = now,
            updatedAt = now,
            categoryId = null,
            keepassDatabaseId = target.databaseId,
            keepassGroupPath = null,
            bitwardenVaultId = null,
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAt = null,
            isDeleted = false,
            deletedAt = null
        )

        is UnifiedMoveCategoryTarget.KeePassGroupTarget -> entry.copy(
            id = 0,
            createdAt = now,
            updatedAt = now,
            categoryId = null,
            keepassDatabaseId = target.databaseId,
            keepassGroupPath = target.groupPath,
            bitwardenVaultId = null,
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAt = null,
            isDeleted = false,
            deletedAt = null
        )
    }
}

private fun buildMoveTargetLabel(
    context: Context,
    target: UnifiedMoveCategoryTarget,
    categories: List<Category>,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>
): String {
    return when (target) {
        UnifiedMoveCategoryTarget.Uncategorized -> context.getString(R.string.category_none)
        is UnifiedMoveCategoryTarget.MonicaCategory -> {
            if (target.categoryId == UNIFIED_MOVE_ARCHIVE_SENTINEL_CATEGORY_ID) {
                context.getString(R.string.archive_page_title)
            } else {
                categories.find { it.id == target.categoryId }?.name
                    ?: context.getString(R.string.filter_monica)
            }
        }

        is UnifiedMoveCategoryTarget.BitwardenVaultTarget,
        is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> context.getString(R.string.filter_bitwarden)

        is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> {
            keepassDatabases.find { it.id == target.databaseId }?.name ?: "KeePass"
        }

        is UnifiedMoveCategoryTarget.KeePassGroupTarget -> decodeKeePassPathForDisplay(target.groupPath)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PasswordListContent(
    viewModel: PasswordViewModel,
    settingsViewModel: SettingsViewModel,
    securityManager: SecurityManager,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    bitwardenVaults: List<takagi.ru.monica.data.bitwarden.BitwardenVault>,
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    groupMode: String = "none",
    stackCardMode: StackCardMode,
    onRenameCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onPasswordClick: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onSelectionModeChange: (
        isSelectionMode: Boolean,
        selectedCount: Int,
        onExit: () -> Unit,
        onSelectAll: () -> Unit,
        onFavorite: () -> Unit,
        onMoveToCategory: () -> Unit,
        onStack: () -> Unit,
        onDelete: () -> Unit
    ) -> Unit,
    onBackToTopVisibilityChange: (Boolean) -> Unit = {},
    scrollToTopRequestKey: Int = 0,
    onOpenHistory: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
    onOpenCommonAccountTemplates: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val passwordEntries by viewModel.passwordEntries.collectAsState()
    val allPasswords by viewModel.allPasswordsForUi.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val currentFilter by viewModel.categoryFilter.collectAsState()
    // settings
    val appSettings by settingsViewModel.settings.collectAsState()
    val listState = rememberLazyListState()
    val backToTopVisibilityCallback by rememberUpdatedState(onBackToTopVisibilityChange)
    val fastScrollRequestKey by viewModel.fastScrollRequestKey.collectAsState()
    val fastScrollProgress by viewModel.fastScrollProgress.collectAsState()

    // "仅本地" 的核心目标是给用户看待上传清单，不应该出现堆叠容器。
    // 因此这里强制扁平展示，仅在该筛选下生效，不影响其他页面。
    val isLocalOnlyView = currentFilter is CategoryFilter.LocalOnly
    val isAllView = currentFilter is CategoryFilter.All
    // Bitwarden pages use pull-to-search only; disable pull-to-sync behavior.
    val isBitwardenDatabaseView = false && when (currentFilter) {
        is CategoryFilter.BitwardenVault,
        is CategoryFilter.BitwardenFolderFilter,
        is CategoryFilter.BitwardenVaultStarred,
        is CategoryFilter.BitwardenVaultUncategorized -> true
        else -> false
    }
    val selectedBitwardenVaultId = when (val filter = currentFilter) {
        is CategoryFilter.BitwardenVault -> filter.vaultId
        is CategoryFilter.BitwardenFolderFilter -> filter.vaultId
        is CategoryFilter.BitwardenVaultStarred -> filter.vaultId
        is CategoryFilter.BitwardenVaultUncategorized -> filter.vaultId
        else -> null
    }
    val selectedKeePassDatabaseId = when (val filter = currentFilter) {
        is CategoryFilter.KeePassDatabase -> filter.databaseId
        is CategoryFilter.KeePassGroupFilter -> filter.databaseId
        is CategoryFilter.KeePassDatabaseStarred -> filter.databaseId
        is CategoryFilter.KeePassDatabaseUncategorized -> filter.databaseId
        else -> null
    }
    val keepassGroupsForSelectedDbFlow = remember(selectedKeePassDatabaseId, localKeePassViewModel) {
        selectedKeePassDatabaseId?.let { databaseId ->
            localKeePassViewModel.getGroups(databaseId)
        } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val keepassGroupsForSelectedDb by keepassGroupsForSelectedDbFlow.collectAsState(initial = emptyList())
    val isKeePassDatabaseView = selectedKeePassDatabaseId != null
    val bitwardenViewModel: takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel = viewModel()
    val bitwardenSyncStatusByVault by bitwardenViewModel.syncStatusByVault.collectAsState()
    val selectedBitwardenFoldersFlow = remember(selectedBitwardenVaultId, viewModel) {
        selectedBitwardenVaultId?.let(viewModel::getBitwardenFolders)
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val selectedBitwardenFolders by selectedBitwardenFoldersFlow.collectAsState(initial = emptyList())
    val isTopBarSyncing = selectedBitwardenVaultId?.let { vaultId ->
        bitwardenSyncStatusByVault[vaultId]?.isRunning == true
    } == true
    val isArchiveView = currentFilter is CategoryFilter.Archived
    val effectiveGroupMode = if (isLocalOnlyView) "none" else groupMode
    val effectiveStackCardMode = if (isLocalOnlyView) StackCardMode.ALWAYS_EXPANDED else stackCardMode
    val quickFoldersEnabledForCurrentFilter =
        appSettings.passwordListQuickFoldersEnabled && !isAllView
    val quickFolderPathBannerEnabledForCurrentFilter =
        appSettings.passwordListQuickFolderPathBannerEnabled && !isAllView
    
    // 选择模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedPasswords by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showMoveToCategoryDialog by remember { mutableStateOf(false) }
    var showManualStackConfirmDialog by remember { mutableStateOf(false) }
    var selectedManualStackMode by remember { mutableStateOf(ManualStackDialogMode.STACK) }
    
    // 详情对话框状态
    var showDetailDialog by remember { mutableStateOf(false) }
    var selectedPasswordForDetail by remember { mutableStateOf<takagi.ru.monica.data.PasswordEntry?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? FragmentActivity
    val biometricHelper = remember { BiometricHelper(context) }
    val canUseBiometric = activity != null && appSettings.biometricEnabled && biometricHelper.isBiometricAvailable()
    val database = remember { takagi.ru.monica.data.PasswordDatabase.getDatabase(context) }
    val bitwardenRepository = remember { takagi.ru.monica.bitwarden.repository.BitwardenRepository.getInstance(context) }

    // Top actions menu and display options sheet state
    var topActionsMenuExpanded by remember { mutableStateOf(false) }
    var showDisplayOptionsSheet by remember { mutableStateOf(false) }
    // Search state hoisted for morphing animation
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }

    // 如果搜索框展开，按返回键关闭搜索框
    val focusManager = LocalFocusManager.current
    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
        viewModel.updateSearchQuery("")
        focusManager.clearFocus()
    }

    // Handle back press for selection mode
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedPasswords = setOf()
    }

    // 在归档页按返回键时，先退出归档回到密码主列表
    BackHandler(enabled = isArchiveView && !isSelectionMode && !isSearchExpanded) {
        viewModel.setCategoryFilter(CategoryFilter.All)
    }
    // Category sheet state
    var isCategorySheetVisible by rememberSaveable { mutableStateOf(false) }
    var categoryPillBoundsInWindow by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    LaunchedEffect(isArchiveView) {
        if (isArchiveView && isCategorySheetVisible) {
            isCategorySheetVisible = false
        }
    }
    
    // 添加触觉反馈
    val haptic = rememberHapticFeedback()
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    // Pull-to-search/sync state (shared implementation)
    val triggerDistance = remember(density) { with(density) { 40.dp.toPx() } }
    val syncTriggerDistance = remember(density) { with(density) { 72.dp.toPx() } }
    val maxDragDistance = remember(density) { with(density) { 100.dp.toPx() } }
    val pullAction = rememberPullActionState(
        isBitwardenDatabaseView = isBitwardenDatabaseView,
        isSearchExpanded = isSearchExpanded,
        searchTriggerDistance = triggerDistance,
        syncTriggerDistance = syncTriggerDistance,
        maxDragDistance = maxDragDistance,
        bitwardenRepository = bitwardenRepository,
        onSearchTriggered = { isSearchExpanded = true }
    )
    
    // 添加单项删除对话框状态
    var itemToDelete by remember { mutableStateOf<takagi.ru.monica.data.PasswordEntry?>(null) }
    var singleItemPasswordInput by remember { mutableStateOf("") }
    var showSingleItemPasswordVerify by remember { mutableStateOf(false) }
    
    // 添加已删除项ID集合（用于在验证前隐藏项）
    var deletedItemIds by remember { mutableStateOf(setOf<Long>()) }
    
    // 堆叠展开状态 - 记录哪些分组已展开（托管到 ViewModel，导航返回后保持）
    val expandedGroups by viewModel.expandedGroups.collectAsState()
    var quickFilterFavorite by rememberSaveable { mutableStateOf(false) }
    var quickFilter2fa by rememberSaveable { mutableStateOf(false) }
    var quickFilterNotes by rememberSaveable { mutableStateOf(false) }
    var quickFilterUncategorized by rememberSaveable { mutableStateOf(false) }
    var quickFilterLocalOnly by rememberSaveable { mutableStateOf(false) }
    var quickFilterManualStackOnly by rememberSaveable { mutableStateOf(false) }
    var quickFilterNeverStack by rememberSaveable { mutableStateOf(false) }
    var quickFilterUnstacked by rememberSaveable { mutableStateOf(false) }
    var manualStackGroupByEntryId by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var noStackEntryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var lastCustomFieldEntryIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    val configuredQuickFilterItems = appSettings.passwordListQuickFilterItems
    val quickFolderStyle = appSettings.passwordListQuickFolderStyle
    var quickFolderRootKey by rememberSaveable {
        mutableStateOf(currentFilter.toQuickFolderRootKeyOrNull() ?: QUICK_FOLDER_ROOT_ALL)
    }
    val outsideTapInteractionSource = remember { MutableInteractionSource() }
    val canCollapseExpandedGroups = effectiveStackCardMode == StackCardMode.AUTO && expandedGroups.isNotEmpty()
    var shouldShowBackToTop by remember { mutableStateOf(false) }
    val backToTopEstimatedScrollPx by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
            val firstVisibleItemSize =
                layoutInfo.visibleItemsInfo.firstOrNull()?.size?.coerceAtLeast(1) ?: viewportHeight
            (listState.firstVisibleItemIndex * firstVisibleItemSize) + listState.firstVisibleItemScrollOffset
        }
    }
    val backToTopViewportHeight by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
        }
    }
    
    // 当分组模式改变时,重置展开状态（初始值用 null 标记，重建时不误清空）
    val prevGroupMode = remember { mutableStateOf<String?>(null) }
    val prevStackCardMode = remember { mutableStateOf<StackCardMode?>(null) }
    LaunchedEffect(effectiveGroupMode, effectiveStackCardMode) {
        val prev1 = prevGroupMode.value
        val prev2 = prevStackCardMode.value
        if (prev1 != null && prev2 != null &&
            (effectiveGroupMode != prev1 || effectiveStackCardMode != prev2)) {
            viewModel.clearExpandedGroups()
        }
        prevGroupMode.value = effectiveGroupMode
        prevStackCardMode.value = effectiveStackCardMode
    }

    LaunchedEffect(backToTopEstimatedScrollPx, backToTopViewportHeight) {
        val viewportHeight = backToTopViewportHeight
        val showThreshold = viewportHeight * 2
        val hideThreshold = (viewportHeight * 1.6f).toInt()
        shouldShowBackToTop = if (shouldShowBackToTop) {
            listState.firstVisibleItemIndex > 0 || backToTopEstimatedScrollPx >= hideThreshold
        } else {
            backToTopEstimatedScrollPx >= showThreshold
        }
    }

    LaunchedEffect(shouldShowBackToTop) {
        backToTopVisibilityCallback(shouldShowBackToTop)
    }

    DisposableEffect(Unit) {
        onDispose {
            backToTopVisibilityCallback(false)
        }
    }

    LaunchedEffect(scrollToTopRequestKey) {
        if (scrollToTopRequestKey > 0) {
            listState.animateScrollToItem(index = 0)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (fastScrollRequestKey <= 0 || totalItems <= 0) {
                null
            } else {
                val clampedProgress = fastScrollProgress.coerceIn(0f, 1f)
                (clampedProgress * (totalItems - 1))
                    .roundToInt()
                    .coerceIn(0, totalItems - 1)
            }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .conflate()
            .collectLatest { targetIndex ->
                if (listState.firstVisibleItemIndex == targetIndex) return@collectLatest
                runCatching {
                    listState.scrollToItem(index = targetIndex)
                }.onFailure { throwable ->
                    if (throwable is CancellationException) return@onFailure
                    Log.e(
                        FAST_SCROLL_LOG_TAG,
                        "scrollToItem failed: targetIndex=$targetIndex totalItems=${listState.layoutInfo.totalItemsCount}",
                        throwable
                    )
                }
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems <= 1) {
                0f
            } else {
                (listState.firstVisibleItemIndex.toFloat() / (totalItems - 1).toFloat()).coerceIn(0f, 1f)
            }
        }
            .distinctUntilChanged()
            .collect { progress: Float ->
                viewModel.updateFastScrollProgress(progress)
            }
    }

    LaunchedEffect(configuredQuickFilterItems) {
        if (takagi.ru.monica.data.PasswordListQuickFilterItem.FAVORITE !in configuredQuickFilterItems) {
            quickFilterFavorite = false
        }
        if (takagi.ru.monica.data.PasswordListQuickFilterItem.TWO_FA !in configuredQuickFilterItems) {
            quickFilter2fa = false
        }
        if (takagi.ru.monica.data.PasswordListQuickFilterItem.NOTES !in configuredQuickFilterItems) {
            quickFilterNotes = false
        }
        if (takagi.ru.monica.data.PasswordListQuickFilterItem.UNCATEGORIZED !in configuredQuickFilterItems) {
            quickFilterUncategorized = false
        }
        if (takagi.ru.monica.data.PasswordListQuickFilterItem.LOCAL_ONLY !in configuredQuickFilterItems) {
            quickFilterLocalOnly = false
        }
        if (takagi.ru.monica.data.PasswordListQuickFilterItem.MANUAL_STACK_ONLY !in configuredQuickFilterItems) {
            quickFilterManualStackOnly = false
        }
        if (takagi.ru.monica.data.PasswordListQuickFilterItem.NEVER_STACK !in configuredQuickFilterItems) {
            quickFilterNeverStack = false
        }
        if (takagi.ru.monica.data.PasswordListQuickFilterItem.UNSTACKED !in configuredQuickFilterItems) {
            quickFilterUnstacked = false
        }
    }

    LaunchedEffect(currentFilter) {
        currentFilter.toQuickFolderRootKeyOrNull()?.let { key ->
            quickFolderRootKey = key
        }
    }

    val quickFolderNodes = remember(categories) {
        buildPasswordQuickFolderNodes(categories)
    }
    val quickFolderNodeByPath = remember(quickFolderNodes) {
        quickFolderNodes.associateBy { it.path }
    }
    val quickFolderCurrentPath = remember(currentFilter, quickFolderNodes) {
        when (val filter = currentFilter) {
            is CategoryFilter.Custom -> quickFolderNodes
                .firstOrNull { it.category.id == filter.categoryId }
                ?.path

            else -> null
        }
    }
    val quickFolderRootFilter = remember(quickFolderRootKey) {
        quickFolderRootKey.toQuickFolderRootFilter()
    }
    val quickFolderSourceEntries = remember(searchQuery, passwordEntries, allPasswords) {
        if (searchQuery.isNotBlank()) {
            passwordEntries
        } else {
            allPasswords
        }
    }
    val baseQuickFolderPasswordCountByCategoryId = rememberAsyncComputed(
        quickFolderSourceEntries,
        initialValue = emptyMap()
    ) {
        buildLocalQuickFolderPasswordCountByCategoryId(quickFolderSourceEntries)
    }
    val quickFolderPasswordCountByCategoryId = remember(
        baseQuickFolderPasswordCountByCategoryId,
        quickFoldersEnabledForCurrentFilter,
        currentFilter
    ) {
        if (!quickFoldersEnabledForCurrentFilter || !currentFilter.supportsQuickFolders()) {
            emptyMap()
        } else {
            baseQuickFolderPasswordCountByCategoryId
        }
    }
    val categoryMenuQuickFolderPasswordCountByCategoryId = remember(
        baseQuickFolderPasswordCountByCategoryId,
        currentFilter
    ) {
        if (!currentFilter.supportsQuickFolders()) {
            emptyMap()
        } else {
            baseQuickFolderPasswordCountByCategoryId
        }
    }
    val quickFolderShortcuts = rememberAsyncComputed(
        appSettings.passwordListQuickFoldersEnabled,
        quickFolderStyle,
        currentFilter,
        quickFolderCurrentPath,
        quickFolderNodes,
        quickFolderNodeByPath,
        quickFolderRootFilter,
        quickFolderPasswordCountByCategoryId,
        allPasswords,
        passwordEntries,
        searchQuery,
        keepassDatabases,
        keepassGroupsForSelectedDb,
        bitwardenVaults,
        selectedBitwardenFolders,
        categories,
        initialValue = emptyList()
    ) {
        buildQuickFolderShortcuts(
            context = context,
            quickFoldersEnabledForCurrentFilter = quickFoldersEnabledForCurrentFilter,
            includeBackNavigation = false,
            currentFilter = currentFilter,
            quickFolderStyle = quickFolderStyle,
            quickFolderCurrentPath = quickFolderCurrentPath,
            quickFolderNodes = quickFolderNodes,
            quickFolderNodeByPath = quickFolderNodeByPath,
            quickFolderRootFilter = quickFolderRootFilter,
            quickFolderPasswordCountByCategoryId = quickFolderPasswordCountByCategoryId,
            allPasswords = allPasswords,
            searchScopedPasswords = passwordEntries,
            isSearchActive = searchQuery.isNotBlank(),
            keepassDatabases = keepassDatabases,
            keepassGroupsForSelectedDb = keepassGroupsForSelectedDb,
            bitwardenVaults = bitwardenVaults,
            selectedBitwardenFolders = selectedBitwardenFolders,
            categories = categories
        )
    }
    val categoryMenuQuickFolderShortcuts = rememberAsyncComputed(
        currentFilter,
        quickFolderCurrentPath,
        quickFolderNodes,
        quickFolderNodeByPath,
        categoryMenuQuickFolderPasswordCountByCategoryId,
        allPasswords,
        passwordEntries,
        searchQuery,
        keepassDatabases,
        keepassGroupsForSelectedDb,
        bitwardenVaults,
        selectedBitwardenFolders,
        categories,
        initialValue = emptyList()
    ) {
        buildCategoryMenuFolderShortcuts(
            context = context,
            currentFilter = currentFilter,
            quickFolderCurrentPath = quickFolderCurrentPath,
            quickFolderNodes = quickFolderNodes,
            quickFolderNodeByPath = quickFolderNodeByPath,
            quickFolderPasswordCountByCategoryId = categoryMenuQuickFolderPasswordCountByCategoryId,
            allPasswords = allPasswords,
            searchScopedPasswords = passwordEntries,
            isSearchActive = searchQuery.isNotBlank(),
            keepassDatabases = keepassDatabases,
            keepassGroupsForSelectedDb = keepassGroupsForSelectedDb,
            bitwardenVaults = bitwardenVaults,
            selectedBitwardenFolders = selectedBitwardenFolders,
            categories = categories
        )
    }
    val quickFolderBreadcrumbs = rememberAsyncComputed(
        appSettings.passwordListQuickFolderPathBannerEnabled,
        currentFilter,
        quickFolderCurrentPath,
        quickFolderNodeByPath,
        quickFolderRootFilter,
        keepassDatabases,
        bitwardenVaults,
        selectedBitwardenFolders,
        categories,
        initialValue = emptyList()
    ) {
        buildQuickFolderBreadcrumbs(
            context = context,
            quickFolderPathBannerEnabledForCurrentFilter = quickFolderPathBannerEnabledForCurrentFilter,
            currentFilter = currentFilter,
            quickFolderCurrentPath = quickFolderCurrentPath,
            quickFolderNodeByPath = quickFolderNodeByPath,
            quickFolderRootFilter = quickFolderRootFilter,
            keepassDatabases = keepassDatabases,
            bitwardenVaults = bitwardenVaults,
            selectedBitwardenFolders = selectedBitwardenFolders,
            categories = categories
        )
    }

    val shouldLoadManualStackMetadata =
        effectiveStackCardMode != StackCardMode.ALWAYS_EXPANDED ||
            quickFilterManualStackOnly ||
            quickFilterNeverStack ||
            quickFilterUnstacked
    val emptyStateMessage = remember(
        currentFilter,
        quickFoldersEnabledForCurrentFilter,
        quickFolderShortcuts
    ) {
        resolvePasswordListEmptyStateMessage(
            currentFilter = currentFilter,
            quickFoldersEnabledForCurrentFilter = quickFoldersEnabledForCurrentFilter,
            hasQuickFolderShortcuts = quickFolderShortcuts.isNotEmpty()
        )
    }
    val effectiveManualStackGroupByEntryId =
        if (shouldLoadManualStackMetadata) manualStackGroupByEntryId else emptyMap()
    val effectiveNoStackEntryIds =
        if (shouldLoadManualStackMetadata) noStackEntryIds else emptySet()

    val buildGroupedPasswordsForEntries: (List<takagi.ru.monica.data.PasswordEntry>) -> Map<String, List<takagi.ru.monica.data.PasswordEntry>> =
        { sourceEntries ->
            val filteredEntries = sourceEntries

            // 步骤1: 先按"除密码外的信息"合并；始终展开模式下跳过合并，逐条显示
            val mergedByInfo = if (effectiveStackCardMode == StackCardMode.ALWAYS_EXPANDED) {
                filteredEntries.sortedBy { it.sortOrder }.map { listOf(it) }
            } else {
                filteredEntries
                    .groupBy { entry ->
                                if (entry.id in effectiveNoStackEntryIds) {
                                    "$NO_STACK_GROUP_KEY_PREFIX${entry.id}"
                                } else {
                                    effectiveManualStackGroupByEntryId[entry.id]
                                        ?.let { groupId -> "$MANUAL_STACK_GROUP_KEY_PREFIX$groupId" }
                                        ?: getPasswordInfoKey(entry)
                                }
                            }
                    .map { (_, entries) ->
                        entries.sortedBy { it.sortOrder }
                    }
            }

            // 步骤2: 再按显示模式分组
            val groupedAndSorted = if (isLocalOnlyView) {
                filteredEntries
                    .sortedBy { it.sortOrder }
                    .associate { entry -> "entry_${entry.id}" to listOf(entry) }
            } else {
                when (effectiveGroupMode) {
                    "title" -> {
                        mergedByInfo
                            .groupBy { entries ->
                                val first = entries.first()
                                if (first.id in effectiveNoStackEntryIds) {
                                    "$NO_STACK_GROUP_KEY_PREFIX${first.id}"
                                } else {
                                    effectiveManualStackGroupByEntryId[first.id]
                                        ?.let { groupId -> "$MANUAL_STACK_GROUP_KEY_PREFIX$groupId" }
                                        ?: first.title.ifBlank { context.getString(R.string.untitled) }
                                }
                            }
                            .mapValues { (_, groups) -> groups.flatten() }
                            .toList()
                            .sortedWith(compareByDescending<Pair<String, List<takagi.ru.monica.data.PasswordEntry>>> { (_, passwords) ->
                                val infoKeyGroups = passwords.groupBy { getPasswordInfoKey(it) }
                                val cardType = when {
                                    infoKeyGroups.size > 1 -> 3
                                    infoKeyGroups.size == 1 && passwords.size > 1 -> 2
                                    else -> 1
                                }
                                val anyFavorited = passwords.any { it.isFavorite }
                                val favoriteBonus = if (anyFavorited) 10 else 0
                                favoriteBonus.toDouble() + cardType.toDouble()
                            }.thenBy { (title, _) ->
                                title
                            })
                            .toMap()
                    }

                    else -> {
                        mergedByInfo
                            .groupBy { entries ->
                                val first = entries.first()
                                if (first.id in effectiveNoStackEntryIds) {
                                    "$NO_STACK_GROUP_KEY_PREFIX${first.id}"
                                } else {
                                    effectiveManualStackGroupByEntryId[first.id]
                                        ?.let { groupId -> "$MANUAL_STACK_GROUP_KEY_PREFIX$groupId" }
                                        ?: getGroupKeyForMode(
                                            first,
                                            effectiveGroupMode,
                                            appSettings.passwordWebsiteStackMatchMode
                                        )
                                }
                            }
                            .mapValues { (_, groups) -> groups.flatten() }
                            .toList()
                            .sortedWith(compareByDescending<Pair<String, List<takagi.ru.monica.data.PasswordEntry>>> { (_, passwords) ->
                                val infoKeyGroups = passwords.groupBy { getPasswordInfoKey(it) }
                                val cardType = when {
                                    infoKeyGroups.size > 1 -> 3
                                    infoKeyGroups.size == 1 && passwords.size > 1 -> 2
                                    else -> 1
                                }
                                val anyFavorited = passwords.any { it.isFavorite }
                                val favoriteBonus = if (anyFavorited) 10 else 0
                                favoriteBonus.toDouble() + cardType.toDouble()
                            }.thenBy { (_, passwords) ->
                                passwords.firstOrNull()?.sortOrder ?: Int.MAX_VALUE
                            })
                            .toMap()
                    }
                }
            }

            if (effectiveStackCardMode == StackCardMode.ALWAYS_EXPANDED) {
                groupedAndSorted.values.flatten()
                    .map { entry -> "entry_${entry.id}" to listOf(entry) }
                    .toMap()
            } else {
                groupedAndSorted
            }
        }
    
    val visiblePasswordEntries = remember(
        passwordEntries,
        deletedItemIds,
        quickFoldersEnabledForCurrentFilter,
        currentFilter,
        configuredQuickFilterItems,
        quickFilterFavorite,
        quickFilter2fa,
        quickFilterNotes,
        quickFilterUncategorized,
        quickFilterLocalOnly,
        quickFilterManualStackOnly,
        quickFilterNeverStack,
        quickFilterUnstacked,
        effectiveStackCardMode,
        effectiveManualStackGroupByEntryId,
        effectiveNoStackEntryIds
    ) {
        var filtered = passwordEntries.filter { it.id !in deletedItemIds }

        if (quickFoldersEnabledForCurrentFilter) {
            filtered = applyQuickFolderRootVisibility(
                entries = filtered,
                currentFilter = currentFilter
            )
        }

        if (quickFilterFavorite && takagi.ru.monica.data.PasswordListQuickFilterItem.FAVORITE in configuredQuickFilterItems) {
            filtered = filtered.filter { it.isFavorite }
        }
        if (quickFilter2fa && takagi.ru.monica.data.PasswordListQuickFilterItem.TWO_FA in configuredQuickFilterItems) {
            filtered = filtered.filter { it.authenticatorKey.isNotBlank() }
        }
        if (quickFilterNotes && takagi.ru.monica.data.PasswordListQuickFilterItem.NOTES in configuredQuickFilterItems) {
            filtered = filtered.filter { it.notes.isNotBlank() }
        }
        if (quickFilterUncategorized && takagi.ru.monica.data.PasswordListQuickFilterItem.UNCATEGORIZED in configuredQuickFilterItems) {
            filtered = filtered.filter { it.categoryId == null }
        }
        if (quickFilterLocalOnly && takagi.ru.monica.data.PasswordListQuickFilterItem.LOCAL_ONLY in configuredQuickFilterItems) {
            filtered = filtered.filter {
                it.keepassDatabaseId == null && it.bitwardenVaultId == null
            }
        }
        if (quickFilterManualStackOnly && takagi.ru.monica.data.PasswordListQuickFilterItem.MANUAL_STACK_ONLY in configuredQuickFilterItems) {
            filtered = filtered.filter { effectiveManualStackGroupByEntryId.containsKey(it.id) }
        }
        if (quickFilterNeverStack && takagi.ru.monica.data.PasswordListQuickFilterItem.NEVER_STACK in configuredQuickFilterItems) {
            filtered = filtered.filter { it.id in effectiveNoStackEntryIds }
        }
        if (quickFilterUnstacked &&
            takagi.ru.monica.data.PasswordListQuickFilterItem.UNSTACKED in configuredQuickFilterItems &&
            effectiveStackCardMode != StackCardMode.ALWAYS_EXPANDED
        ) {
            val singleCardEntryIds = buildGroupedPasswordsForEntries(filtered)
                .values
                .asSequence()
                .filter { group -> group.size == 1 }
                .flatten()
                .map { it.id }
                .toSet()
            filtered = filtered.filter { it.id in singleCardEntryIds }
        }
        filtered
    }

    LaunchedEffect(visiblePasswordEntries) {
        if (selectedPasswords.isEmpty()) return@LaunchedEffect
        val visibleIds = visiblePasswordEntries.map { it.id }.toSet()
        selectedPasswords = selectedPasswords.intersect(visibleIds)
    }

    LaunchedEffect(passwordEntries, deletedItemIds, shouldLoadManualStackMetadata) {
        if (!shouldLoadManualStackMetadata) {
            manualStackGroupByEntryId = emptyMap()
            noStackEntryIds = emptySet()
            lastCustomFieldEntryIds = emptyList()
            return@LaunchedEffect
        }
        val entriesSnapshot = passwordEntries
        val deletedIdsSnapshot = deletedItemIds
        val allIds = withContext(Dispatchers.Default) {
            entriesSnapshot
                .asSequence()
                .map { it.id }
                .filter { id -> id !in deletedIdsSnapshot }
                .toList()
        }
        if (allIds.isEmpty()) {
            manualStackGroupByEntryId = emptyMap()
            noStackEntryIds = emptySet()
            lastCustomFieldEntryIds = emptyList()
            return@LaunchedEffect
        }
        if (allIds == lastCustomFieldEntryIds) {
            return@LaunchedEffect
        }
        lastCustomFieldEntryIds = allIds
        val fieldMap = withContext(Dispatchers.IO) {
            viewModel.getCustomFieldsByEntryIds(allIds)
        }
        val (manualStackMap, noStackIds) = withContext(Dispatchers.Default) {
            val manualStack = fieldMap.mapNotNull { (entryId, fields) ->
                val groupId = fields.firstOrNull {
                    it.title == MONICA_MANUAL_STACK_GROUP_FIELD_TITLE
                }?.value?.takeIf { value -> value.isNotBlank() }
                groupId?.let { entryId to it }
            }.toMap()
            val noStack = fieldMap.mapNotNull { (entryId, fields) ->
                val hasNoStack = fields.any {
                    it.title == MONICA_NO_STACK_FIELD_TITLE && it.value != "0"
                }
                if (hasNoStack) entryId else null
            }.toSet()
            manualStack to noStack
        }
        manualStackGroupByEntryId = manualStackMap
        noStackEntryIds = noStackIds
    }
    
    // 根据分组模式对密码进行分组（后台线程计算，避免阻塞首滑）
    var groupedPasswords by remember {
        mutableStateOf<Map<String, List<takagi.ru.monica.data.PasswordEntry>>>(emptyMap())
    }
    LaunchedEffect(
        visiblePasswordEntries,
        effectiveGroupMode,
        appSettings.passwordWebsiteStackMatchMode,
        effectiveStackCardMode,
        effectiveManualStackGroupByEntryId,
        effectiveNoStackEntryIds
    ) {
        val sourceEntries = visiblePasswordEntries
        if (sourceEntries.isEmpty()) {
            groupedPasswords = emptyMap()
            return@LaunchedEffect
        }
        groupedPasswords = withContext(Dispatchers.Default) {
            buildGroupedPasswordsForEntries(sourceEntries)
        }
    }

    // 定义回调函数
    val exitSelection = {
        isSelectionMode = false
        selectedPasswords = setOf()
    }
    
    val selectAll = {
        selectedPasswords = if (selectedPasswords.size == visiblePasswordEntries.size) {
            setOf()
        } else {
            visiblePasswordEntries.map { it.id }.toSet()
        }
    }
    
    val favoriteSelected: () -> Unit = {
        // 智能批量收藏/取消收藏
        coroutineScope.launch {
            val selectedEntries = passwordEntries.filter { selectedPasswords.contains(it.id) }
            
            // 检查是否所有选中的密码都已收藏
            val allFavorited = selectedEntries.all { it.isFavorite }
            
            // 如果全部已收藏,则取消收藏;否则全部设为收藏
            val newFavoriteState = !allFavorited
            
            selectedEntries.forEach { entry ->
                viewModel.toggleFavorite(entry.id, newFavoriteState)
            }
            
            // 显示提示
            val message = if (newFavoriteState) {
                context.getString(R.string.batch_favorited, selectedEntries.size)
            } else {
                context.getString(R.string.batch_unfavorited, selectedEntries.size)
            }
            
            android.widget.Toast.makeText(
                context,
                message,
                android.widget.Toast.LENGTH_SHORT
            ).show()
            
            // 退出选择模式
            isSelectionMode = false
            selectedPasswords = setOf()
        }
        Unit
    }
    
    val moveToCategory = {
        showMoveToCategoryDialog = true
    }

    val stackSelected = {
        if (selectedPasswords.isEmpty()) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.multi_del_select_items),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            selectedManualStackMode = ManualStackDialogMode.STACK
            showManualStackConfirmDialog = true
        }
    }
    
    val deleteSelected = {
        showBatchDeleteDialog = true
    }
    
    // 通知父组件选择模式状态变化
    LaunchedEffect(isSelectionMode, selectedPasswords.size) {
        onSelectionModeChange(
            isSelectionMode,
            selectedPasswords.size,
            exitSelection,
            selectAll,
            favoriteSelected,
            moveToCategory,
            stackSelected,
            deleteSelected
        )
    }

    PasswordBatchMoveSheet(
        visible = showMoveToCategoryDialog,
        categories = categories,
        keepassDatabases = keepassDatabases,
        bitwardenVaults = bitwardenVaults,
        database = database,
        localKeePassViewModel = localKeePassViewModel,
        securityManager = securityManager,
        selectedPasswords = selectedPasswords,
        passwordEntries = passwordEntries,
        viewModel = viewModel,
        context = context,
        coroutineScope = coroutineScope,
        onRenameCategory = onRenameCategory,
        onDeleteCategory = onDeleteCategory,
        onDismiss = { showMoveToCategoryDialog = false },
        onSelectionCleared = {
            isSelectionMode = false
            selectedPasswords = emptySet()
        }
    )

    PasswordListTopSection(
        currentFilter = currentFilter,
        categories = categories,
        keepassDatabases = keepassDatabases,
        bitwardenVaults = bitwardenVaults,
        viewModel = viewModel,
        localKeePassViewModel = localKeePassViewModel,
        bitwardenViewModel = bitwardenViewModel,
        selectedBitwardenVaultId = selectedBitwardenVaultId,
        isTopBarSyncing = isTopBarSyncing,
        isArchiveView = isArchiveView,
        isKeePassDatabaseView = isKeePassDatabaseView,
        searchQuery = searchQuery,
        isSearchExpanded = isSearchExpanded,
        onSearchExpandedChange = { isSearchExpanded = it },
        onSearchQueryChange = viewModel::updateSearchQuery,
        topActionsMenuExpanded = topActionsMenuExpanded,
        onTopActionsMenuExpandedChange = { topActionsMenuExpanded = it },
        isCategorySheetVisible = isCategorySheetVisible,
        onCategorySheetVisibleChange = { isCategorySheetVisible = it },
        categoryPillBoundsInWindow = categoryPillBoundsInWindow,
        onCategoryPillBoundsChange = { categoryPillBoundsInWindow = it },
        showDisplayOptionsSheet = showDisplayOptionsSheet,
        onShowDisplayOptionsSheetChange = { showDisplayOptionsSheet = it },
        configuredQuickFilterItems = configuredQuickFilterItems,
        quickFilterFavorite = quickFilterFavorite,
        onQuickFilterFavoriteChange = { quickFilterFavorite = it },
        quickFilter2fa = quickFilter2fa,
        onQuickFilter2faChange = { quickFilter2fa = it },
        quickFilterNotes = quickFilterNotes,
        onQuickFilterNotesChange = { quickFilterNotes = it },
        quickFilterUncategorized = quickFilterUncategorized,
        onQuickFilterUncategorizedChange = { quickFilterUncategorized = it },
        quickFilterLocalOnly = quickFilterLocalOnly,
        onQuickFilterLocalOnlyChange = { quickFilterLocalOnly = it },
        quickFilterManualStackOnly = quickFilterManualStackOnly,
        onQuickFilterManualStackOnlyChange = { quickFilterManualStackOnly = it },
        quickFilterNeverStack = quickFilterNeverStack,
        onQuickFilterNeverStackChange = { quickFilterNeverStack = it },
        quickFilterUnstacked = quickFilterUnstacked,
        onQuickFilterUnstackedChange = { quickFilterUnstacked = it },
        categoryMenuQuickFolderShortcuts = categoryMenuQuickFolderShortcuts,
        stackCardMode = stackCardMode,
        groupMode = groupMode,
        passwordCardDisplayMode = appSettings.passwordCardDisplayMode,
        settingsViewModel = settingsViewModel,
        context = context,
        activity = activity,
        biometricHelper = biometricHelper,
        canUseBiometric = canUseBiometric,
        coroutineScope = coroutineScope,
        bitwardenRepository = bitwardenRepository,
        onRenameCategory = onRenameCategory,
        onDeleteCategory = onDeleteCategory,
        onOpenCommonAccountTemplates = onOpenCommonAccountTemplates,
        onOpenHistory = onOpenHistory,
        onOpenTrash = onOpenTrash
    )

        // 密码列表 - 使用堆叠分组视图
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    enabled = canCollapseExpandedGroups,
                    interactionSource = outsideTapInteractionSource,
                    indication = null
                ) {
                    viewModel.clearExpandedGroups()
                }
        ) {
            val searchProgress = (pullAction.currentOffset / triggerDistance).coerceIn(0f, 1f)
            val syncProgress = ((pullAction.currentOffset - triggerDistance) / (syncTriggerDistance - triggerDistance))
                .coerceIn(0f, 1f)
            val pullVisualState = when {
                isBitwardenDatabaseView && pullAction.isBitwardenSyncing -> PullActionVisualState.SYNCING
                isBitwardenDatabaseView && pullAction.showSyncFeedback -> PullActionVisualState.SYNC_DONE
                isBitwardenDatabaseView && pullAction.syncHintArmed -> PullActionVisualState.SYNC_READY
                pullAction.currentOffset >= triggerDistance -> PullActionVisualState.SEARCH_READY
                else -> PullActionVisualState.IDLE
            }
            val pullHintText = when (pullVisualState) {
                PullActionVisualState.SYNCING -> stringResource(R.string.pull_syncing_bitwarden)
                PullActionVisualState.SYNC_DONE -> pullAction.syncFeedbackMessage.ifBlank {
                    if (pullAction.syncFeedbackIsSuccess) {
                        stringResource(R.string.pull_sync_success)
                    } else {
                        stringResource(R.string.sync_status_failed_full)
                    }
                }
                PullActionVisualState.SYNC_READY -> stringResource(R.string.pull_release_to_sync_bitwarden)
                PullActionVisualState.SEARCH_READY -> if (isBitwardenDatabaseView) {
                    stringResource(R.string.pull_release_to_search)
                } else {
                    null
                }
                PullActionVisualState.IDLE -> null
            }
            val shouldPinIndicator = isBitwardenDatabaseView && (
                pullAction.syncHintArmed || pullAction.isBitwardenSyncing || pullAction.showSyncFeedback
            )
            val revealHeightTarget = with(density) {
                if (isBitwardenDatabaseView) {
                    val pullHeight = pullAction.currentOffset.toDp().coerceIn(0.dp, 112.dp)
                    if (shouldPinIndicator) {
                        maxOf(pullHeight, 92.dp)
                    } else {
                        pullHeight
                    }
                } else {
                    0.dp
                }
            }
            val revealHeight by animateDpAsState(
                targetValue = revealHeightTarget,
                animationSpec = tween(durationMillis = 220),
                label = "pull_reveal_height"
            )
            val showPullIndicator = pullHintText != null && revealHeight > 0.5.dp
            val contentPullOffset = if (isBitwardenDatabaseView) {
                // Keep sync indicator layout, but move list slightly to avoid sticky drag feel.
                (pullAction.currentOffset * 0.28f).toInt()
            } else {
                pullAction.currentOffset.toInt()
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(revealHeight),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (showPullIndicator) {
                        PullGestureIndicator(
                            state = pullVisualState,
                            searchProgress = searchProgress,
                            syncProgress = syncProgress,
                            text = pullHintText ?: "",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 8.dp)
                        )
                    }
                }
                if (showPullIndicator) {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                val hasVisibleQuickFilters =
                    appSettings.passwordListQuickFiltersEnabled && configuredQuickFilterItems.isNotEmpty()
                val showPinnedQuickFolderPathBanner = quickFolderBreadcrumbs.isNotEmpty()
                val hasScrollableHeaderContent =
                    hasVisibleQuickFilters || quickFolderShortcuts.isNotEmpty() || showPinnedQuickFolderPathBanner

                if (showPinnedQuickFolderPathBanner) {
                    PasswordQuickFolderBreadcrumbBanner(
                        breadcrumbs = quickFolderBreadcrumbs,
                        currentFilter = currentFilter,
                        onNavigate = { target -> viewModel.setCategoryFilter(target) }
                    )
                }

                if (visiblePasswordEntries.isEmpty() && searchQuery.isEmpty() && !hasScrollableHeaderContent) {
                    // Empty state with pull-to-search
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .offset { androidx.compose.ui.unit.IntOffset(0, contentPullOffset) }
                            .pointerInput(isBitwardenDatabaseView) {
                                detectDragGesturesAfterLongPress(
                                    onDrag = { _, _ -> } // Consume long press to prevent issues
                                )
                            }
                            .pointerInput(isBitwardenDatabaseView) {
                                 detectVerticalDragGestures(
                                    onVerticalDrag = { _, dragAmount ->
                                        pullAction.onVerticalDrag(dragAmount)
                                    },
                                    onDragEnd = {
                                        pullAction.onDragEnd()
                                    },
                                    onDragCancel = {
                                        pullAction.onDragCancel()
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        PasswordListEmptyState(message = emptyStateMessage)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .offset { androidx.compose.ui.unit.IntOffset(0, contentPullOffset) }
                            .nestedScroll(pullAction.nestedScrollConnection),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                    if (hasVisibleQuickFilters) {
                        item(key = "quick_filters") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(top = 2.dp, bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                configuredQuickFilterItems.forEach { item ->
                                    when (item) {
                                        takagi.ru.monica.data.PasswordListQuickFilterItem.FAVORITE -> {
                                            PasswordQuickFilterChip(
                                                selected = quickFilterFavorite,
                                                onClick = { quickFilterFavorite = !quickFilterFavorite },
                                                label = stringResource(R.string.password_list_quick_filter_favorite),
                                                leadingIcon = Icons.Default.FavoriteBorder,
                                                selectedLeadingIcon = Icons.Default.Favorite
                                            )
                                        }

                                        takagi.ru.monica.data.PasswordListQuickFilterItem.TWO_FA -> {
                                            PasswordQuickFilterChip(
                                                selected = quickFilter2fa,
                                                onClick = { quickFilter2fa = !quickFilter2fa },
                                                label = stringResource(R.string.password_list_quick_filter_2fa),
                                                leadingIcon = Icons.Default.Security
                                            )
                                        }

                                        takagi.ru.monica.data.PasswordListQuickFilterItem.NOTES -> {
                                            PasswordQuickFilterChip(
                                                selected = quickFilterNotes,
                                                onClick = { quickFilterNotes = !quickFilterNotes },
                                                label = stringResource(R.string.password_list_quick_filter_notes),
                                                leadingIcon = Icons.Default.Description
                                            )
                                        }

                                        takagi.ru.monica.data.PasswordListQuickFilterItem.UNCATEGORIZED -> {
                                            PasswordQuickFilterChip(
                                                selected = quickFilterUncategorized,
                                                onClick = { quickFilterUncategorized = !quickFilterUncategorized },
                                                label = stringResource(R.string.password_list_quick_filter_uncategorized)
                                            )
                                        }

                                        takagi.ru.monica.data.PasswordListQuickFilterItem.LOCAL_ONLY -> {
                                            PasswordQuickFilterChip(
                                                selected = quickFilterLocalOnly,
                                                onClick = { quickFilterLocalOnly = !quickFilterLocalOnly },
                                                label = stringResource(R.string.password_list_quick_filter_local_only),
                                                leadingIcon = Icons.Default.Key
                                            )
                                        }

                                        takagi.ru.monica.data.PasswordListQuickFilterItem.MANUAL_STACK_ONLY -> {
                                            PasswordQuickFilterChip(
                                                selected = quickFilterManualStackOnly,
                                                onClick = { quickFilterManualStackOnly = !quickFilterManualStackOnly },
                                                label = stringResource(R.string.password_list_quick_filter_manual_stack_only),
                                                leadingIcon = Icons.Default.Apps
                                            )
                                        }

                                        takagi.ru.monica.data.PasswordListQuickFilterItem.NEVER_STACK -> {
                                            PasswordQuickFilterChip(
                                                selected = quickFilterNeverStack,
                                                onClick = { quickFilterNeverStack = !quickFilterNeverStack },
                                                label = stringResource(R.string.password_list_quick_filter_never_stack),
                                                leadingIcon = Icons.Default.LinearScale
                                            )
                                        }

                                        takagi.ru.monica.data.PasswordListQuickFilterItem.UNSTACKED -> {
                                            PasswordQuickFilterChip(
                                                selected = quickFilterUnstacked,
                                                onClick = { quickFilterUnstacked = !quickFilterUnstacked },
                                                label = stringResource(R.string.password_list_quick_filter_unstacked),
                                                leadingIcon = Icons.Default.Straighten
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (quickFolderShortcuts.isNotEmpty()) {
                        val quickFolderUseM3CardStyle =
                            quickFolderStyle == takagi.ru.monica.data.PasswordListQuickFolderStyle.M3_CARD
                        item(key = "quick_folder_shortcuts") {
                            PasswordQuickFolderShortcutsSection(
                                shortcuts = quickFolderShortcuts,
                                currentFilter = currentFilter,
                                useM3CardStyle = quickFolderUseM3CardStyle,
                                onNavigate = { target -> viewModel.setCategoryFilter(target) }
                            )
                        }
                    }

                    if (visiblePasswordEntries.isEmpty() && searchQuery.isEmpty()) {
                        item(key = "empty_state_with_quick_headers") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 84.dp, bottom = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                PasswordListEmptyState(message = emptyStateMessage)
                            }
                        }
                    } else {
                    groupedPasswords.forEach { (groupKey, passwords) ->
                    val isExpanded = when (effectiveStackCardMode) {
                        StackCardMode.AUTO -> expandedGroups.contains(groupKey)
                        StackCardMode.ALWAYS_EXPANDED -> true
                    }

                    item(key = "group_$groupKey") {
                        StackedPasswordGroup(
                            website = groupKey,
                            passwords = passwords,
                            isExpanded = isExpanded,
                            stackCardMode = effectiveStackCardMode,
                            enableSharedBounds = false,
                            swipedItemId = itemToDelete?.id,
                            onToggleExpand = {
                                if (effectiveStackCardMode == StackCardMode.AUTO) {
                                    viewModel.toggleExpandedGroup(groupKey)
                                }
                            },
                        onPasswordClick = { password ->
                            if (isSelectionMode) {
                                // 选择模式：切换选择状态
                                selectedPasswords = if (selectedPasswords.contains(password.id)) {
                                    selectedPasswords - password.id
                                } else {
                                    selectedPasswords + password.id
                                }
                            } else {
                                // 普通模式：显示详情页面
                                onPasswordClick(password)
                            }
                        },
                        onSwipeLeft = { password ->
                            // 防止连续滑动导致 itemToDelete 被覆盖
                            if (itemToDelete == null) {
                                // 左滑删除
                                haptic.performWarning()
                                itemToDelete = password
                            }
                        },
                        onSwipeRight = { password ->
                            // 右滑选择
                            haptic.performSuccess()
                            if (!isSelectionMode) {
                                isSelectionMode = true
                            }
                            selectedPasswords = if (selectedPasswords.contains(password.id)) {
                                selectedPasswords - password.id
                            } else {
                                selectedPasswords + password.id
                            }
                        },
                        onGroupSwipeRight = { groupPasswords ->
                            // 右滑选择整组
                            haptic.performSuccess()
                            if (!isSelectionMode) {
                                isSelectionMode = true
                            }
                            
                            // 检查是否整组都已选中
                            val allSelected = groupPasswords.all { selectedPasswords.contains(it.id) }
                            
                            selectedPasswords = if (allSelected) {
                                // 如果全选了，则取消全选
                                selectedPasswords - groupPasswords.map { it.id }.toSet()
                            } else {
                                // 否则全选（补齐未选中的）
                                selectedPasswords + groupPasswords.map { it.id }
                            }
                        },
                        onToggleFavorite = { password ->
                            viewModel.toggleFavorite(password.id, !password.isFavorite)
                        },
                        onToggleGroupFavorite = {
                            // 智能切换整组收藏状态
                            coroutineScope.launch {
                                val allFavorited = passwords.all { it.isFavorite }
                                val newState = !allFavorited
                                
                                passwords.forEach { password ->
                                    viewModel.toggleFavorite(password.id, newState)
                                }
                                
                                val message = if (newState) {
                                    context.getString(R.string.group_favorited, passwords.size)
                                } else {
                                    context.getString(R.string.group_unfavorited, passwords.size)
                                }
                                
                                android.widget.Toast.makeText(
                                    context,
                                    message,
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onToggleGroupCover = { password ->
                            // 切换封面状态并移到顶部
                            coroutineScope.launch {
                                val websiteKey = password.website.ifBlank { context.getString(R.string.filter_uncategorized) }
                                val newCoverState = !password.isGroupCover
                                
                                if (newCoverState) {
                                    // 设置为封面时,同时移到组内第一位
                                    val groupPasswords = passwords
                                    val currentIndex = groupPasswords.indexOfFirst { it.id == password.id }
                                    
                                    if (currentIndex > 0) {
                                        // 需要移动到顶部
                                        val reordered = groupPasswords.toMutableList()
                                        val item = reordered.removeAt(currentIndex)
                                        reordered.add(0, item) // 移到第一位
                                        
                                        // 更新sortOrder
                                        val allPasswords = passwordEntries
                                        val firstItemInGroup = allPasswords.firstOrNull {
                                            it.website.ifBlank { context.getString(R.string.filter_uncategorized) } == websiteKey
                                        } ?: return@launch
                                        val startSortOrder = allPasswords.indexOf(firstItemInGroup)
                                        
                                        viewModel.updateSortOrders(
                                            reordered.mapIndexed { idx, entry -> 
                                                entry.id to (startSortOrder + idx)
                                            }
                                        )
                                    }
                                }
                                
                                // 设置/取消封面
                                viewModel.toggleGroupCover(password.id, websiteKey, newCoverState)
                            }
                        },
                        isSelectionMode = isSelectionMode,
                        selectedPasswords = selectedPasswords,
                        onToggleSelection = { id ->
                            selectedPasswords = if (selectedPasswords.contains(id)) {
                                selectedPasswords - id
                            } else {
                                selectedPasswords + id
                            }
                        },
                        onOpenMultiPasswordDialog = { passwords ->
                            // 导航到详情页面 (现在详情页面支持多密码)
                            onPasswordClick(passwords.first())
                        },
                        onLongClick = { password ->
                            // 长按进入多选模式
                            haptic.performLongPress()
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedPasswords = setOf(password.id)
                            }
                        },
                        iconCardsEnabled = appSettings.iconCardsEnabled && appSettings.passwordPageIconEnabled,
                        unmatchedIconHandlingStrategy = appSettings.unmatchedIconHandlingStrategy,
                        passwordCardDisplayMode = appSettings.passwordCardDisplayMode,
                        passwordCardDisplayFields = appSettings.passwordCardDisplayFields,
                        showAuthenticator = appSettings.passwordCardShowAuthenticator,
                        hideOtherContentWhenAuthenticator = appSettings.passwordCardHideOtherContentWhenAuthenticator,
                        totpTimeOffsetSeconds = appSettings.totpTimeOffset,
                        smoothAuthenticatorProgress = appSettings.validatorSmoothProgress
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
    
    PasswordListDialogs(
        showManualStackConfirmDialog = showManualStackConfirmDialog,
        onShowManualStackConfirmDialogChange = { showManualStackConfirmDialog = it },
        selectedPasswords = selectedPasswords,
        selectedManualStackMode = selectedManualStackMode,
        onSelectedManualStackModeChange = { selectedManualStackMode = it },
        viewModel = viewModel,
        context = context,
        coroutineScope = coroutineScope,
        onSelectionCleared = {
            isSelectionMode = false
            selectedPasswords = emptySet()
        },
        showBatchDeleteDialog = showBatchDeleteDialog,
        onShowBatchDeleteDialogChange = { showBatchDeleteDialog = it },
        passwordInput = passwordInput,
        onPasswordInputChange = {
            passwordInput = it
            passwordError = false
        },
        passwordError = passwordError,
        onPasswordErrorChange = { passwordError = it },
        passwordEntries = passwordEntries,
        canUseBiometric = canUseBiometric,
        activity = activity,
        biometricHelper = biometricHelper,
        itemToDelete = itemToDelete,
        onItemToDeleteChange = { itemToDelete = it },
        appSettings = appSettings,
        singleItemPasswordInput = singleItemPasswordInput,
        onSingleItemPasswordInputChange = { singleItemPasswordInput = it },
        showSingleItemPasswordVerify = showSingleItemPasswordVerify,
        onShowSingleItemPasswordVerifyChange = { showSingleItemPasswordVerify = it }
    )
}
}

@Composable
private fun PasswordBatchMoveSheet(
    visible: Boolean,
    categories: List<Category>,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    bitwardenVaults: List<takagi.ru.monica.data.bitwarden.BitwardenVault>,
    database: takagi.ru.monica.data.PasswordDatabase,
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    securityManager: SecurityManager,
    selectedPasswords: Set<Long>,
    passwordEntries: List<takagi.ru.monica.data.PasswordEntry>,
    viewModel: PasswordViewModel,
    context: Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onRenameCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onDismiss: () -> Unit,
    onSelectionCleared: () -> Unit
) {
    UnifiedMoveToCategoryBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        categories = categories,
        keepassDatabases = keepassDatabases,
        bitwardenVaults = bitwardenVaults,
        getBitwardenFolders = { vaultId -> database.bitwardenFolderDao().getFoldersByVaultFlow(vaultId) },
        getKeePassGroups = localKeePassViewModel::getGroups,
        allowCopy = true,
        allowArchiveTarget = true,
        onTargetSelected = { target, action ->
            val selectedIds = selectedPasswords.toList()
            val selectedEntries = passwordEntries.filter { it.id in selectedPasswords }
            val isArchiveTarget = target is UnifiedMoveCategoryTarget.MonicaCategory &&
                target.categoryId == UNIFIED_MOVE_ARCHIVE_SENTINEL_CATEGORY_ID
            if (action == UnifiedMoveAction.COPY) {
                val copiedIds = mutableListOf<Long>()
                var remaining = selectedEntries.size
                selectedEntries.forEach { entry ->
                    val copiedEntry = buildCopiedEntryForTarget(entry, target)
                    viewModel.addPasswordEntryWithResult(
                        entry = copiedEntry,
                        includeDetailedLog = false
                    ) { createdId ->
                        if (createdId != null && createdId > 0) {
                            copiedIds.add(createdId)
                        }
                        remaining -= 1
                        if (remaining == 0 && copiedIds.isNotEmpty()) {
                            val payload = TimelineBatchCopyPayload(
                                copiedEntryIds = copiedIds.toList()
                            )
                            OperationLogger.logUpdate(
                                itemType = OperationLogItemType.PASSWORD,
                                itemId = System.currentTimeMillis(),
                                itemTitle = context.getString(
                                    R.string.timeline_batch_copy_title,
                                    copiedIds.size
                                ),
                                changes = listOf(
                                    FieldChange(
                                        fieldName = context.getString(R.string.timeline_field_batch_copy),
                                        oldValue = "0",
                                        newValue = copiedIds.size.toString()
                                    ),
                                    FieldChange(
                                        fieldName = TIMELINE_FIELD_BATCH_COPY_PAYLOAD,
                                        oldValue = "{}",
                                        newValue = timelineBatchJson.encodeToString(payload)
                                    )
                                )
                            )
                        }
                    }
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.selected_items, selectedEntries.size),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val oldStates = selectedEntries.map(::toLocationState)
                val newStates = selectedEntries.map { toMovedLocationState(it, target) }
                when {
                    isArchiveTarget -> {
                        viewModel.archivePasswords(selectedIds)
                        Toast.makeText(
                            context,
                            context.getString(R.string.archive_page_title),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    target == UnifiedMoveCategoryTarget.Uncategorized -> {
                        viewModel.unarchivePasswords(selectedIds)
                        viewModel.movePasswordsToCategory(selectedIds, null)
                        Toast.makeText(context, context.getString(R.string.category_none), Toast.LENGTH_SHORT).show()
                    }
                    target is UnifiedMoveCategoryTarget.MonicaCategory -> {
                        viewModel.unarchivePasswords(selectedIds)
                        viewModel.movePasswordsToCategory(selectedIds, target.categoryId)
                        val name = categories.find { it.id == target.categoryId }?.name ?: ""
                        Toast.makeText(context, "${context.getString(R.string.move_to_category)} $name", Toast.LENGTH_SHORT).show()
                    }
                    target is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> {
                        viewModel.unarchivePasswords(selectedIds)
                        viewModel.movePasswordsToBitwardenFolder(selectedIds, target.vaultId, "")
                        Toast.makeText(context, context.getString(R.string.filter_bitwarden), Toast.LENGTH_SHORT).show()
                    }
                    target is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> {
                        viewModel.unarchivePasswords(selectedIds)
                        viewModel.movePasswordsToBitwardenFolder(selectedIds, target.vaultId, target.folderId)
                        Toast.makeText(context, context.getString(R.string.filter_bitwarden), Toast.LENGTH_SHORT).show()
                    }
                    target is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> {
                        coroutineScope.launch {
                            try {
                                val result = localKeePassViewModel.movePasswordEntriesToKdbx(
                                    databaseId = target.databaseId,
                                    groupPath = null,
                                    entries = selectedEntries,
                                    decryptPassword = { encrypted -> securityManager.decryptData(encrypted) ?: "" }
                                )
                                if (result.isSuccess) {
                                    viewModel.unarchivePasswords(selectedIds)
                                    viewModel.movePasswordsToKeePassDatabase(selectedIds, target.databaseId)
                                    Toast.makeText(
                                        context,
                                        "${context.getString(R.string.move_to_category)} ${keepassDatabases.find { it.id == target.databaseId }?.name ?: "KeePass"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.webdav_operation_failed, result.exceptionOrNull()?.message ?: ""),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.webdav_operation_failed, e.message ?: ""),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    target is UnifiedMoveCategoryTarget.KeePassGroupTarget -> {
                        coroutineScope.launch {
                            try {
                                val result = localKeePassViewModel.movePasswordEntriesToKdbx(
                                    databaseId = target.databaseId,
                                    groupPath = target.groupPath,
                                    entries = selectedEntries,
                                    decryptPassword = { encrypted -> securityManager.decryptData(encrypted) ?: "" }
                                )
                                if (result.isSuccess) {
                                    viewModel.unarchivePasswords(selectedIds)
                                    viewModel.movePasswordsToKeePassGroup(selectedIds, target.databaseId, target.groupPath)
                                    Toast.makeText(
                                        context,
                                        "${context.getString(R.string.move_to_category)} ${decodeKeePassPathForDisplay(target.groupPath)}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.webdav_operation_failed, result.exceptionOrNull()?.message ?: ""),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.webdav_operation_failed, e.message ?: ""),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
                if (selectedEntries.isNotEmpty()) {
                    val payload = TimelineBatchMovePayload(
                        oldStates = oldStates,
                        newStates = newStates
                    )
                    OperationLogger.logUpdate(
                        itemType = OperationLogItemType.PASSWORD,
                        itemId = System.currentTimeMillis(),
                        itemTitle = context.getString(
                            R.string.timeline_batch_move_title,
                            selectedEntries.size
                        ),
                        changes = listOf(
                            FieldChange(
                                fieldName = context.getString(R.string.timeline_field_batch_move),
                                oldValue = context.getString(R.string.timeline_batch_source_multiple),
                                newValue = buildMoveTargetLabel(
                                    context = context,
                                    target = target,
                                    categories = categories,
                                    keepassDatabases = keepassDatabases
                                )
                            ),
                            FieldChange(
                                fieldName = TIMELINE_FIELD_BATCH_MOVE_PAYLOAD,
                                oldValue = timelineBatchJson.encodeToString(payload),
                                newValue = timelineBatchJson.encodeToString(payload)
                            )
                        )
                    )
                }
            }
            onDismiss()
            onSelectionCleared()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordListTopSection(
    currentFilter: CategoryFilter,
    categories: List<Category>,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    bitwardenVaults: List<takagi.ru.monica.data.bitwarden.BitwardenVault>,
    viewModel: PasswordViewModel,
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    bitwardenViewModel: takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel,
    selectedBitwardenVaultId: Long?,
    isTopBarSyncing: Boolean,
    isArchiveView: Boolean,
    isKeePassDatabaseView: Boolean,
    searchQuery: String,
    isSearchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    topActionsMenuExpanded: Boolean,
    onTopActionsMenuExpandedChange: (Boolean) -> Unit,
    isCategorySheetVisible: Boolean,
    onCategorySheetVisibleChange: (Boolean) -> Unit,
    categoryPillBoundsInWindow: androidx.compose.ui.geometry.Rect?,
    onCategoryPillBoundsChange: (androidx.compose.ui.geometry.Rect?) -> Unit,
    showDisplayOptionsSheet: Boolean,
    onShowDisplayOptionsSheetChange: (Boolean) -> Unit,
    configuredQuickFilterItems: List<takagi.ru.monica.data.PasswordListQuickFilterItem>,
    quickFilterFavorite: Boolean,
    onQuickFilterFavoriteChange: (Boolean) -> Unit,
    quickFilter2fa: Boolean,
    onQuickFilter2faChange: (Boolean) -> Unit,
    quickFilterNotes: Boolean,
    onQuickFilterNotesChange: (Boolean) -> Unit,
    quickFilterUncategorized: Boolean,
    onQuickFilterUncategorizedChange: (Boolean) -> Unit,
    quickFilterLocalOnly: Boolean,
    onQuickFilterLocalOnlyChange: (Boolean) -> Unit,
    quickFilterManualStackOnly: Boolean,
    onQuickFilterManualStackOnlyChange: (Boolean) -> Unit,
    quickFilterNeverStack: Boolean,
    onQuickFilterNeverStackChange: (Boolean) -> Unit,
    quickFilterUnstacked: Boolean,
    onQuickFilterUnstackedChange: (Boolean) -> Unit,
    categoryMenuQuickFolderShortcuts: List<PasswordQuickFolderShortcut>,
    stackCardMode: StackCardMode,
    groupMode: String,
    passwordCardDisplayMode: takagi.ru.monica.data.PasswordCardDisplayMode,
    settingsViewModel: SettingsViewModel,
    context: Context,
    activity: FragmentActivity?,
    biometricHelper: BiometricHelper,
    canUseBiometric: Boolean,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    bitwardenRepository: takagi.ru.monica.bitwarden.repository.BitwardenRepository,
    onRenameCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onOpenCommonAccountTemplates: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTrash: () -> Unit
) {
    val appSettings by settingsViewModel.settings.collectAsState()
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    Column {
        val title = when (val filter = currentFilter) {
            is CategoryFilter.All -> "ALL"
            is CategoryFilter.Archived -> stringResource(R.string.archive_page_title)
            is CategoryFilter.Local -> stringResource(R.string.filter_monica)
            is CategoryFilter.LocalOnly -> stringResource(R.string.filter_local_only)
            is CategoryFilter.Starred -> stringResource(R.string.filter_starred)
            is CategoryFilter.Uncategorized -> stringResource(R.string.filter_uncategorized)
            is CategoryFilter.LocalStarred -> "${stringResource(R.string.filter_monica)} · ${stringResource(R.string.filter_starred)}"
            is CategoryFilter.LocalUncategorized -> "${stringResource(R.string.filter_monica)} · ${stringResource(R.string.filter_uncategorized)}"
            is CategoryFilter.Custom -> categories.find { it.id == filter.categoryId }?.name ?: stringResource(R.string.filter_all)
            is CategoryFilter.KeePassDatabase -> keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"
            is CategoryFilter.KeePassGroupFilter -> decodeKeePassPathForDisplay(filter.groupPath)
            is CategoryFilter.KeePassDatabaseStarred -> "${keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"} · ${stringResource(R.string.filter_starred)}"
            is CategoryFilter.KeePassDatabaseUncategorized -> "${keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"} · ${stringResource(R.string.filter_uncategorized)}"
            is CategoryFilter.BitwardenVault -> "Bitwarden"
            is CategoryFilter.BitwardenFolderFilter -> "Bitwarden"
            is CategoryFilter.BitwardenVaultStarred -> "${stringResource(R.string.filter_bitwarden)} · ${stringResource(R.string.filter_starred)}"
            is CategoryFilter.BitwardenVaultUncategorized -> "${stringResource(R.string.filter_bitwarden)} · ${stringResource(R.string.filter_uncategorized)}"
        }

        ExpressiveTopBar(
            title = title,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            isSearchExpanded = isSearchExpanded,
            onSearchExpandedChange = onSearchExpandedChange,
            searchHint = stringResource(R.string.search_passwords_hint),
            onActionPillBoundsChanged = if (isArchiveView) null else onCategoryPillBoundsChange,
            actions = {
                if (isArchiveView) {
                    IconButton(onClick = { viewModel.setCategoryFilter(CategoryFilter.All) }) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.nav_passwords_short),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isArchiveView) {
                    if (appSettings.categorySelectionUiMode == CategorySelectionUiMode.CHIP_MENU) {
                        IconButton(onClick = { onCategorySheetVisibleChange(true) }) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = stringResource(R.string.category),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        IconButton(onClick = { onCategorySheetVisibleChange(true) }) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = stringResource(R.string.category),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(onClick = { onSearchExpandedChange(true) }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { onTopActionsMenuExpandedChange(true) }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!isArchiveView && appSettings.categorySelectionUiMode == CategorySelectionUiMode.CHIP_MENU) {
                        UnifiedCategoryFilterChipMenuDropdown(
                            expanded = isCategorySheetVisible,
                            onDismissRequest = { onCategorySheetVisibleChange(false) },
                            offset = UnifiedCategoryFilterChipMenuOffset
                        ) {
                            PasswordListCategoryChipMenu(
                                currentFilter = currentFilter,
                                keepassDatabases = keepassDatabases,
                                bitwardenVaults = bitwardenVaults,
                                configuredQuickFilterItems = configuredQuickFilterItems,
                                quickFilterFavorite = quickFilterFavorite,
                                onQuickFilterFavoriteChange = onQuickFilterFavoriteChange,
                                quickFilter2fa = quickFilter2fa,
                                onQuickFilter2faChange = onQuickFilter2faChange,
                                quickFilterNotes = quickFilterNotes,
                                onQuickFilterNotesChange = onQuickFilterNotesChange,
                                quickFilterUncategorized = quickFilterUncategorized,
                                onQuickFilterUncategorizedChange = onQuickFilterUncategorizedChange,
                                quickFilterLocalOnly = quickFilterLocalOnly,
                                onQuickFilterLocalOnlyChange = onQuickFilterLocalOnlyChange,
                                quickFilterManualStackOnly = quickFilterManualStackOnly,
                                onQuickFilterManualStackOnlyChange = onQuickFilterManualStackOnlyChange,
                                quickFilterNeverStack = quickFilterNeverStack,
                                onQuickFilterNeverStackChange = onQuickFilterNeverStackChange,
                                quickFilterUnstacked = quickFilterUnstacked,
                                onQuickFilterUnstackedChange = onQuickFilterUnstackedChange,
                                quickFolderShortcuts = categoryMenuQuickFolderShortcuts,
                                topModulesOrder = appSettings.passwordListTopModulesOrder,
                                onTopModulesOrderChange = settingsViewModel::updatePasswordListTopModulesOrder,
                                onQuickFilterItemsOrderChange = settingsViewModel::updatePasswordListQuickFilterItems,
                                launchAnchorBounds = null,
                                onDismiss = { onCategorySheetVisibleChange(false) },
                                onSelectFilter = viewModel::setCategoryFilter,
                                categories = categories,
                                onCreateCategory = {
                                    onCategorySheetVisibleChange(false)
                                    showCreateCategoryDialog = true
                                },
                                onRenameCategory = onRenameCategory,
                                onDeleteCategory = onDeleteCategory
                            )
                        }
                    }
                    MaterialTheme(
                        shapes = MaterialTheme.shapes.copy(
                            extraSmall = RoundedCornerShape(20.dp),
                            small = RoundedCornerShape(20.dp)
                        )
                    ) {
                        DropdownMenu(
                            expanded = topActionsMenuExpanded,
                            onDismissRequest = { onTopActionsMenuExpandedChange(false) },
                            offset = DpOffset(x = 48.dp, y = 6.dp),
                            modifier = Modifier
                                .widthIn(min = 220.dp, max = 260.dp)
                                .shadow(10.dp, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                        ) {
                            if (isKeePassDatabaseView) {
                                DropdownMenuItem(
                                    text = {
                                        Text("${stringResource(R.string.refresh)} ${stringResource(R.string.filter_keepass)}")
                                    },
                                    leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) },
                                    onClick = {
                                        onTopActionsMenuExpandedChange(false)
                                        viewModel.refreshKeePassFromSourceForCurrentContext()
                                    }
                                )
                            }
                            if (selectedBitwardenVaultId != null) {
                                DropdownMenuItem(
                                    text = { Text("同步bitwarden数据库") },
                                    leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) },
                                    enabled = !isTopBarSyncing,
                                    onClick = {
                                        if (isTopBarSyncing) return@DropdownMenuItem
                                        val vaultId = selectedBitwardenVaultId ?: return@DropdownMenuItem
                                        onTopActionsMenuExpandedChange(false)
                                        bitwardenViewModel.requestManualSync(vaultId)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.display_options_menu_title)) },
                                leadingIcon = { Icon(Icons.Default.DashboardCustomize, contentDescription = null) },
                                onClick = {
                                    onTopActionsMenuExpandedChange(false)
                                    onShowDisplayOptionsSheetChange(true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_account_title)) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                onClick = {
                                    onTopActionsMenuExpandedChange(false)
                                    onOpenCommonAccountTemplates()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.timeline_title)) },
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                onClick = {
                                    onTopActionsMenuExpandedChange(false)
                                    onOpenHistory()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.timeline_trash_title)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    onTopActionsMenuExpandedChange(false)
                                    onOpenTrash()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.archive_page_title)) },
                                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                onClick = {
                                    onTopActionsMenuExpandedChange(false)
                                    viewModel.setCategoryFilter(CategoryFilter.Archived)
                                }
                            )
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        val unifiedSelectedFilter = when (val filter = currentFilter) {
            is CategoryFilter.All -> UnifiedCategoryFilterSelection.All
            is CategoryFilter.Archived -> UnifiedCategoryFilterSelection.All
            is CategoryFilter.Local -> UnifiedCategoryFilterSelection.Local
            is CategoryFilter.LocalOnly -> UnifiedCategoryFilterSelection.Local
            is CategoryFilter.Starred -> UnifiedCategoryFilterSelection.Starred
            is CategoryFilter.Uncategorized -> UnifiedCategoryFilterSelection.Uncategorized
            is CategoryFilter.LocalStarred -> UnifiedCategoryFilterSelection.LocalStarred
            is CategoryFilter.LocalUncategorized -> UnifiedCategoryFilterSelection.LocalUncategorized
            is CategoryFilter.Custom -> UnifiedCategoryFilterSelection.Custom(filter.categoryId)
            is CategoryFilter.BitwardenVault -> UnifiedCategoryFilterSelection.BitwardenVaultFilter(filter.vaultId)
            is CategoryFilter.BitwardenFolderFilter -> UnifiedCategoryFilterSelection.BitwardenFolderFilter(filter.vaultId, filter.folderId)
            is CategoryFilter.BitwardenVaultStarred -> UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter(filter.vaultId)
            is CategoryFilter.BitwardenVaultUncategorized -> UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter(filter.vaultId)
            is CategoryFilter.KeePassDatabase -> UnifiedCategoryFilterSelection.KeePassDatabaseFilter(filter.databaseId)
            is CategoryFilter.KeePassGroupFilter -> UnifiedCategoryFilterSelection.KeePassGroupFilter(filter.databaseId, filter.groupPath)
            is CategoryFilter.KeePassDatabaseStarred -> UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter(filter.databaseId)
            is CategoryFilter.KeePassDatabaseUncategorized -> UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter(filter.databaseId)
        }
        if (isCategorySheetVisible && !isArchiveView) {
            when (appSettings.categorySelectionUiMode) {
                CategorySelectionUiMode.BOTTOM_SHEET -> UnifiedCategoryFilterBottomSheet(
                visible = true,
                onDismiss = { onCategorySheetVisibleChange(false) },
                selected = unifiedSelectedFilter,
                showLocalOnlyQuickFilter = true,
                isLocalOnlyQuickFilterSelected = currentFilter is CategoryFilter.LocalOnly,
                onSelectLocalOnlyQuickFilter = { viewModel.setCategoryFilter(CategoryFilter.LocalOnly) },
                onSelect = { selection ->
                    when (selection) {
                        is UnifiedCategoryFilterSelection.All -> viewModel.setCategoryFilter(CategoryFilter.All)
                        is UnifiedCategoryFilterSelection.Local -> viewModel.setCategoryFilter(CategoryFilter.Local)
                        is UnifiedCategoryFilterSelection.Starred -> viewModel.setCategoryFilter(CategoryFilter.Starred)
                        is UnifiedCategoryFilterSelection.Uncategorized -> viewModel.setCategoryFilter(CategoryFilter.Uncategorized)
                        is UnifiedCategoryFilterSelection.LocalStarred -> viewModel.setCategoryFilter(CategoryFilter.LocalStarred)
                        is UnifiedCategoryFilterSelection.LocalUncategorized -> viewModel.setCategoryFilter(CategoryFilter.LocalUncategorized)
                        is UnifiedCategoryFilterSelection.Custom -> viewModel.setCategoryFilter(CategoryFilter.Custom(selection.categoryId))
                        is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> viewModel.setCategoryFilter(CategoryFilter.BitwardenVault(selection.vaultId))
                        is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> viewModel.setCategoryFilter(CategoryFilter.BitwardenFolderFilter(selection.folderId, selection.vaultId))
                        is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> viewModel.setCategoryFilter(CategoryFilter.BitwardenVaultStarred(selection.vaultId))
                        is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> viewModel.setCategoryFilter(CategoryFilter.BitwardenVaultUncategorized(selection.vaultId))
                        is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> viewModel.setCategoryFilter(CategoryFilter.KeePassDatabase(selection.databaseId))
                        is UnifiedCategoryFilterSelection.KeePassGroupFilter -> viewModel.setCategoryFilter(CategoryFilter.KeePassGroupFilter(selection.databaseId, selection.groupPath))
                        is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> viewModel.setCategoryFilter(CategoryFilter.KeePassDatabaseStarred(selection.databaseId))
                        is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> viewModel.setCategoryFilter(CategoryFilter.KeePassDatabaseUncategorized(selection.databaseId))
                    }
                },
                launchAnchorBounds = categoryPillBoundsInWindow,
                categories = categories,
                keepassDatabases = keepassDatabases,
                bitwardenVaults = bitwardenVaults,
                getBitwardenFolders = viewModel::getBitwardenFolders,
                getKeePassGroups = localKeePassViewModel::getGroups,
                onVerifyMasterPassword = { input ->
                    SecurityManager(context).verifyMasterPassword(input)
                },
                onRequestBiometricVerify = if (canUseBiometric) {
                    { onSuccess, onError ->
                        val hostActivity = activity
                        if (hostActivity == null) {
                            onError(context.getString(R.string.biometric_not_available))
                        } else {
                            biometricHelper.authenticate(
                                activity = hostActivity,
                                title = context.getString(R.string.verify_identity),
                                subtitle = context.getString(R.string.verify_to_delete),
                                onSuccess = { onSuccess() },
                                onError = { error -> onError(error) },
                                onFailed = {}
                            )
                        }
                    }
                } else {
                    null
                },
                onCreateCategoryWithName = { name -> viewModel.addCategory(name) },
                onCreateBitwardenFolder = { vaultId, name ->
                    coroutineScope.launch {
                        val result = bitwardenRepository.createFolder(vaultId, name)
                        result.exceptionOrNull()?.let { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onRenameBitwardenFolder = { vaultId, folderId, newName ->
                    coroutineScope.launch {
                        val result = bitwardenRepository.renameFolder(vaultId, folderId, newName)
                        result.exceptionOrNull()?.let { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onDeleteBitwardenFolder = { vaultId, folderId ->
                    coroutineScope.launch {
                        val result = bitwardenRepository.deleteFolder(vaultId, folderId)
                        result.exceptionOrNull()?.let { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onRenameCategory = onRenameCategory,
                onDeleteCategory = onDeleteCategory,
                onCreateKeePassGroup = { databaseId, parentPath, name ->
                    localKeePassViewModel.createGroup(
                        databaseId = databaseId,
                        groupName = name,
                        parentPath = parentPath
                    ) { result ->
                        result.exceptionOrNull()?.let { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onRenameKeePassGroup = { databaseId, groupPath, newName ->
                    localKeePassViewModel.renameGroup(
                        databaseId = databaseId,
                        groupPath = groupPath,
                        newName = newName
                    ) { result ->
                        result.exceptionOrNull()?.let { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onDeleteKeePassGroup = { databaseId, groupPath ->
                    localKeePassViewModel.deleteGroup(
                        databaseId = databaseId,
                        groupPath = groupPath
                    ) { result ->
                        result.exceptionOrNull()?.let { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                )
                CategorySelectionUiMode.CHIP_MENU -> Unit
            }
        }

        if (showDisplayOptionsSheet) {
            PasswordDisplayOptionsSheet(
                stackCardMode = stackCardMode,
                groupMode = groupMode,
                passwordCardDisplayMode = passwordCardDisplayMode,
                onDismiss = { onShowDisplayOptionsSheetChange(false) },
                onStackCardModeSelected = { mode ->
                    settingsViewModel.updateStackCardMode(mode.name)
                },
                onGroupModeSelected = { modeKey ->
                    settingsViewModel.updatePasswordGroupMode(modeKey)
                },
                onPasswordCardDisplayModeSelected = { mode ->
                    settingsViewModel.updatePasswordCardDisplayMode(mode)
                }
            )
        }

        if (showCreateCategoryDialog) {
            CreateCategoryDialog(
                visible = true,
                onDismiss = { showCreateCategoryDialog = false },
                categories = categories,
                keepassDatabases = keepassDatabases,
                bitwardenVaults = bitwardenVaults,
                getKeePassGroups = localKeePassViewModel::getGroups,
                onCreateCategoryWithName = { name -> viewModel.addCategory(name) },
                onCreateBitwardenFolder = { vaultId, name ->
                    coroutineScope.launch {
                        val result = bitwardenRepository.createFolder(vaultId, name)
                        result.exceptionOrNull()?.let { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onCreateKeePassGroup = { databaseId, parentPath, name ->
                    localKeePassViewModel.createGroup(
                        databaseId = databaseId,
                        groupName = name,
                        parentPath = parentPath
                    ) { result ->
                        result.exceptionOrNull()?.let { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun PasswordQuickFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    leadingIcon: ImageVector? = null,
    selectedLeadingIcon: ImageVector? = leadingIcon,
    animated: Boolean = true
) {
    MonicaExpressiveFilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        interactionSource = interactionSource,
        leadingIcon = leadingIcon,
        selectedLeadingIcon = selectedLeadingIcon,
        animated = animated
    )
}

@Composable
private fun PasswordQuickFilterChipItem(
    item: takagi.ru.monica.data.PasswordListQuickFilterItem,
    categoryEditMode: Boolean,
    quickFilterFavorite: Boolean,
    onQuickFilterFavoriteChange: (Boolean) -> Unit,
    quickFilter2fa: Boolean,
    onQuickFilter2faChange: (Boolean) -> Unit,
    quickFilterNotes: Boolean,
    onQuickFilterNotesChange: (Boolean) -> Unit,
    quickFilterUncategorized: Boolean,
    onQuickFilterUncategorizedChange: (Boolean) -> Unit,
    quickFilterLocalOnly: Boolean,
    onQuickFilterLocalOnlyChange: (Boolean) -> Unit,
    quickFilterManualStackOnly: Boolean,
    onQuickFilterManualStackOnlyChange: (Boolean) -> Unit,
    quickFilterNeverStack: Boolean,
    onQuickFilterNeverStackChange: (Boolean) -> Unit,
    quickFilterUnstacked: Boolean,
    onQuickFilterUnstackedChange: (Boolean) -> Unit,
    interactionSource: MutableInteractionSource? = null,
    modifier: Modifier = Modifier
) {
    when (item) {
        takagi.ru.monica.data.PasswordListQuickFilterItem.FAVORITE -> {
            PasswordQuickFilterChip(
                selected = quickFilterFavorite,
                onClick = {
                    if (!categoryEditMode) {
                        onQuickFilterFavoriteChange(!quickFilterFavorite)
                    }
                },
                label = stringResource(R.string.password_list_quick_filter_favorite),
                modifier = modifier,
                interactionSource = interactionSource,
                leadingIcon = Icons.Outlined.FavoriteBorder,
                selectedLeadingIcon = Icons.Default.Favorite
            )
        }

        takagi.ru.monica.data.PasswordListQuickFilterItem.TWO_FA -> {
            PasswordQuickFilterChip(
                selected = quickFilter2fa,
                onClick = {
                    if (!categoryEditMode) {
                        onQuickFilter2faChange(!quickFilter2fa)
                    }
                },
                label = stringResource(R.string.password_list_quick_filter_2fa),
                modifier = modifier,
                interactionSource = interactionSource,
                leadingIcon = Icons.Default.Security
            )
        }

        takagi.ru.monica.data.PasswordListQuickFilterItem.NOTES -> {
            PasswordQuickFilterChip(
                selected = quickFilterNotes,
                onClick = {
                    if (!categoryEditMode) {
                        onQuickFilterNotesChange(!quickFilterNotes)
                    }
                },
                label = stringResource(R.string.password_list_quick_filter_notes),
                modifier = modifier,
                interactionSource = interactionSource,
                leadingIcon = Icons.Default.Description
            )
        }

        takagi.ru.monica.data.PasswordListQuickFilterItem.UNCATEGORIZED -> {
            PasswordQuickFilterChip(
                selected = quickFilterUncategorized,
                onClick = {
                    if (!categoryEditMode) {
                        onQuickFilterUncategorizedChange(!quickFilterUncategorized)
                    }
                },
                label = stringResource(R.string.password_list_quick_filter_uncategorized),
                modifier = modifier,
                interactionSource = interactionSource,
                leadingIcon = Icons.Default.FolderOff
            )
        }

        takagi.ru.monica.data.PasswordListQuickFilterItem.LOCAL_ONLY -> {
            PasswordQuickFilterChip(
                selected = quickFilterLocalOnly,
                onClick = {
                    if (!categoryEditMode) {
                        onQuickFilterLocalOnlyChange(!quickFilterLocalOnly)
                    }
                },
                label = stringResource(R.string.password_list_quick_filter_local_only),
                modifier = modifier,
                interactionSource = interactionSource,
                leadingIcon = Icons.Default.Key
            )
        }

        takagi.ru.monica.data.PasswordListQuickFilterItem.MANUAL_STACK_ONLY -> {
            PasswordQuickFilterChip(
                selected = quickFilterManualStackOnly,
                onClick = {
                    if (!categoryEditMode) {
                        onQuickFilterManualStackOnlyChange(!quickFilterManualStackOnly)
                    }
                },
                label = stringResource(R.string.password_list_quick_filter_manual_stack_only),
                modifier = modifier,
                interactionSource = interactionSource,
                leadingIcon = Icons.Default.Apps
            )
        }

        takagi.ru.monica.data.PasswordListQuickFilterItem.NEVER_STACK -> {
            PasswordQuickFilterChip(
                selected = quickFilterNeverStack,
                onClick = {
                    if (!categoryEditMode) {
                        onQuickFilterNeverStackChange(!quickFilterNeverStack)
                    }
                },
                label = stringResource(R.string.password_list_quick_filter_never_stack),
                modifier = modifier,
                interactionSource = interactionSource,
                leadingIcon = Icons.Default.LinearScale
            )
        }

        takagi.ru.monica.data.PasswordListQuickFilterItem.UNSTACKED -> {
            PasswordQuickFilterChip(
                selected = quickFilterUnstacked,
                onClick = {
                    if (!categoryEditMode) {
                        onQuickFilterUnstackedChange(!quickFilterUnstacked)
                    }
                },
                label = stringResource(R.string.password_list_quick_filter_unstacked),
                modifier = modifier,
                interactionSource = interactionSource,
                leadingIcon = Icons.Default.Straighten
            )
        }
    }
}

private fun <T> reorderList(list: List<T>, fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex == toIndex || fromIndex !in list.indices || toIndex !in list.indices) {
        return list
    }
    return list.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}

private fun <T> reorderListByInsertion(list: List<T>, item: T, insertionIndex: Int): List<T> {
    if (item !in list) {
        return list
    }
    return list.toMutableList().apply {
        remove(item)
        add(insertionIndex.coerceIn(0, size), item)
    }
}

@Composable
private fun PasswordQuickFilterEditGrid(
    items: List<takagi.ru.monica.data.PasswordListQuickFilterItem>,
    measuredSizes: MutableMap<takagi.ru.monica.data.PasswordListQuickFilterItem, IntSize>,
    availableWidth: Dp,
    quickFilterFavorite: Boolean,
    onQuickFilterFavoriteChange: (Boolean) -> Unit,
    quickFilter2fa: Boolean,
    onQuickFilter2faChange: (Boolean) -> Unit,
    quickFilterNotes: Boolean,
    onQuickFilterNotesChange: (Boolean) -> Unit,
    quickFilterUncategorized: Boolean,
    onQuickFilterUncategorizedChange: (Boolean) -> Unit,
    quickFilterLocalOnly: Boolean,
    onQuickFilterLocalOnlyChange: (Boolean) -> Unit,
    quickFilterManualStackOnly: Boolean,
    onQuickFilterManualStackOnlyChange: (Boolean) -> Unit,
    quickFilterNeverStack: Boolean,
    onQuickFilterNeverStackChange: (Boolean) -> Unit,
    quickFilterUnstacked: Boolean,
    onQuickFilterUnstackedChange: (Boolean) -> Unit,
    onOrderCommitted: (List<takagi.ru.monica.data.PasswordListQuickFilterItem>) -> Unit
) {
    val density = LocalDensity.current
    val gridSpacing = 8.dp
    val itemHeight = 48.dp
    val availableWidthPx = with(density) { availableWidth.toPx() }
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val gridSpacingPx = with(density) { gridSpacing.toPx() }

    var draggingItem by remember { mutableStateOf<takagi.ru.monica.data.PasswordListQuickFilterItem?>(null) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }
    var dragPointerPosition by remember { mutableStateOf(Offset.Zero) }
    var dragTouchOffset by remember { mutableStateOf(Offset.Zero) }
    var pendingCommittedOrder by remember {
        mutableStateOf<List<takagi.ru.monica.data.PasswordListQuickFilterItem>?>(null)
    }
    var dragSnapshotOrder by remember {
        mutableStateOf<List<takagi.ru.monica.data.PasswordListQuickFilterItem>>(emptyList())
    }
    var dragSnapshotOffsets by remember {
        mutableStateOf<Map<takagi.ru.monica.data.PasswordListQuickFilterItem, Offset>>(emptyMap())
    }

    val displayOrder = pendingCommittedOrder ?: items
    val previewSourceOrder = if (draggingItem != null && dragSnapshotOrder.isNotEmpty()) {
        dragSnapshotOrder
    } else {
        displayOrder
    }
    val previewOrder = remember(previewSourceOrder, draggingItem, dragTargetIndex) {
        val dragged = draggingItem
        if (dragged != null && dragTargetIndex >= 0) {
            reorderListByInsertion(previewSourceOrder, dragged, dragTargetIndex)
        } else {
            previewSourceOrder
        }
    }
    val activeOrder = if (draggingItem != null) previewOrder else displayOrder

    fun itemSize(item: takagi.ru.monica.data.PasswordListQuickFilterItem): IntSize {
        return measuredSizes[item] ?: IntSize(
            width = ((availableWidthPx - gridSpacingPx) / 2f).roundToInt().coerceAtLeast(120),
            height = itemHeightPx.roundToInt()
        )
    }

    fun computeLayout(
        order: List<takagi.ru.monica.data.PasswordListQuickFilterItem>
    ): Pair<Map<takagi.ru.monica.data.PasswordListQuickFilterItem, Offset>, Float> {
        val offsets = linkedMapOf<takagi.ru.monica.data.PasswordListQuickFilterItem, Offset>()
        var x = 0f
        var y = 0f
        var rowHeight = 0f

        order.forEach { item ->
            val size = itemSize(item)
            val itemWidth = size.width.toFloat()
            val itemHeightValue = maxOf(size.height.toFloat(), itemHeightPx)
            if (x > 0f && x + itemWidth > availableWidthPx) {
                x = 0f
                y += rowHeight + gridSpacingPx
                rowHeight = 0f
            }
            offsets[item] = Offset(x, y)
            x += itemWidth + gridSpacingPx
            rowHeight = maxOf(rowHeight, itemHeightValue)
        }

        val totalHeight = if (offsets.isEmpty()) itemHeightPx else y + rowHeight
        return offsets to totalHeight
    }

    val layoutInfo = remember(activeOrder, measuredSizes, availableWidth) {
        computeLayout(activeOrder)
    }
    val targetOffsets = layoutInfo.first
    val gridHeight = with(density) { layoutInfo.second.toDp() }

    fun insertionIndexFor(
        point: Offset,
        order: List<takagi.ru.monica.data.PasswordListQuickFilterItem>,
        offsets: Map<takagi.ru.monica.data.PasswordListQuickFilterItem, Offset>,
        ignoredItem: takagi.ru.monica.data.PasswordListQuickFilterItem? = null
    ): Int {
        val candidates = order.filter { it != ignoredItem }
        if (candidates.isEmpty()) {
            return 0
        }

        val fallbackIndex = dragTargetIndex.takeIf { it in 0..candidates.size }
            ?: order.indexOf(ignoredItem).takeIf { it >= 0 }
            ?: candidates.size

        candidates.forEachIndexed { index, item ->
            val topLeft = offsets[item] ?: return@forEachIndexed
            val size = itemSize(item)
            val centerX = topLeft.x + size.width / 2f
            val centerY = topLeft.y + size.height / 2f
            val rowThreshold = size.height * 0.45f

            if (point.y < centerY - rowThreshold) {
                return index
            }
            if (kotlin.math.abs(point.y - centerY) <= rowThreshold && point.x < centerX) {
                return index
            }
        }

        return candidates.size.coerceAtLeast(fallbackIndex)
    }

    fun resetDragState(clearSnapshot: Boolean = true) {
        draggingItem = null
        dragTargetIndex = -1
        dragPointerPosition = Offset.Zero
        dragTouchOffset = Offset.Zero
        if (clearSnapshot) {
            dragSnapshotOrder = emptyList()
            dragSnapshotOffsets = emptyMap()
        }
    }

    LaunchedEffect(items, pendingCommittedOrder) {
        if (pendingCommittedOrder != null && items == pendingCommittedOrder) {
            pendingCommittedOrder = null
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .requiredWidth(availableWidth)
                .height(gridHeight)
        ) {
            items.forEach { item ->
                val targetPosition = targetOffsets[item] ?: dragSnapshotOffsets[item] ?: Offset.Zero
                val chipSize = itemSize(item)
                val itemAlpha = if (draggingItem == item) 0f else 1f
                val animatedOffset by animateIntOffsetAsState(
                    targetValue = IntOffset(targetPosition.x.roundToInt(), targetPosition.y.roundToInt()),
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    ),
                    label = "password_menu_quick_filter_position"
                )

                Box(
                    modifier = Modifier
                        .offset { animatedOffset }
                        .requiredSize(
                            width = with(density) { chipSize.width.toDp() },
                            height = with(density) { chipSize.height.toDp() }
                        )
                        .alpha(itemAlpha),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(item, displayOrder) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val snapshotOrder = displayOrder
                                        val startIndex = snapshotOrder.indexOf(item)
                                        if (startIndex == -1) return@detectDragGesturesAfterLongPress
                                        val snapshotOffsets = computeLayout(snapshotOrder).first
                                        val startTopLeft = snapshotOffsets[item] ?: Offset.Zero
                                        draggingItem = item
                                        dragTargetIndex = startIndex
                                        dragPointerPosition = startTopLeft + offset
                                        dragTouchOffset = offset
                                        dragSnapshotOrder = snapshotOrder
                                        dragSnapshotOffsets = snapshotOffsets
                                    },
                                    onDragCancel = {
                                        resetDragState()
                                    },
                                    onDragEnd = {
                                        val dragged = draggingItem
                                        val insertionIndex = dragTargetIndex
                                        val sourceOrder = if (dragSnapshotOrder.isNotEmpty()) {
                                            dragSnapshotOrder
                                        } else {
                                            displayOrder
                                        }
                                        if (dragged == null || insertionIndex < 0) {
                                            resetDragState()
                                            return@detectDragGesturesAfterLongPress
                                        }
                                        val reordered = reorderListByInsertion(sourceOrder, dragged, insertionIndex)
                                        pendingCommittedOrder = reordered
                                        resetDragState()
                                        if (reordered != items) {
                                            onOrderCommitted(reordered)
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (draggingItem != item) return@detectDragGesturesAfterLongPress
                                        dragPointerPosition += Offset(dragAmount.x, dragAmount.y)

                                        val snapshotOrder = if (dragSnapshotOrder.isNotEmpty()) {
                                            dragSnapshotOrder
                                        } else {
                                            displayOrder
                                        }
                                        val snapshotOffsets = if (dragSnapshotOffsets.isNotEmpty()) {
                                            dragSnapshotOffsets
                                        } else {
                                            computeLayout(snapshotOrder).first
                                        }
                                        dragTargetIndex = insertionIndexFor(
                                            point = dragPointerPosition,
                                            order = snapshotOrder,
                                            offsets = snapshotOffsets,
                                            ignoredItem = item
                                        )
                                    }
                                )
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        PasswordQuickFilterChipItem(
                            item = item,
                            categoryEditMode = true,
                            quickFilterFavorite = quickFilterFavorite,
                            onQuickFilterFavoriteChange = onQuickFilterFavoriteChange,
                            quickFilter2fa = quickFilter2fa,
                            onQuickFilter2faChange = onQuickFilter2faChange,
                            quickFilterNotes = quickFilterNotes,
                            onQuickFilterNotesChange = onQuickFilterNotesChange,
                            quickFilterUncategorized = quickFilterUncategorized,
                            onQuickFilterUncategorizedChange = onQuickFilterUncategorizedChange,
                            quickFilterLocalOnly = quickFilterLocalOnly,
                            onQuickFilterLocalOnlyChange = onQuickFilterLocalOnlyChange,
                            quickFilterManualStackOnly = quickFilterManualStackOnly,
                            onQuickFilterManualStackOnlyChange = onQuickFilterManualStackOnlyChange,
                            quickFilterNeverStack = quickFilterNeverStack,
                            onQuickFilterNeverStackChange = onQuickFilterNeverStackChange,
                            quickFilterUnstacked = quickFilterUnstacked,
                            onQuickFilterUnstackedChange = onQuickFilterUnstackedChange,
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                val size = coordinates.size
                                if (measuredSizes[item] != size) {
                                    measuredSizes[item] = size
                                }
                            }
                        )
                    }
                }
            }

            draggingItem?.let { item ->
                val placeholderOffset = targetOffsets[item] ?: dragSnapshotOffsets[item] ?: Offset.Zero
                val placeholderSize = itemSize(item)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                placeholderOffset.x.roundToInt(),
                                placeholderOffset.y.roundToInt()
                            )
                        }
                        .requiredSize(
                            width = with(density) { placeholderSize.width.toDp() },
                            height = with(density) { placeholderSize.height.toDp() }
                        )
                        .alpha(0.22f)
                        .zIndex(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    PasswordQuickFilterChipItem(
                        item = item,
                        categoryEditMode = true,
                        quickFilterFavorite = quickFilterFavorite,
                        onQuickFilterFavoriteChange = onQuickFilterFavoriteChange,
                        quickFilter2fa = quickFilter2fa,
                        onQuickFilter2faChange = onQuickFilter2faChange,
                        quickFilterNotes = quickFilterNotes,
                        onQuickFilterNotesChange = onQuickFilterNotesChange,
                        quickFilterUncategorized = quickFilterUncategorized,
                        onQuickFilterUncategorizedChange = onQuickFilterUncategorizedChange,
                        quickFilterLocalOnly = quickFilterLocalOnly,
                        onQuickFilterLocalOnlyChange = onQuickFilterLocalOnlyChange,
                        quickFilterManualStackOnly = quickFilterManualStackOnly,
                        onQuickFilterManualStackOnlyChange = onQuickFilterManualStackOnlyChange,
                        quickFilterNeverStack = quickFilterNeverStack,
                        onQuickFilterNeverStackChange = onQuickFilterNeverStackChange,
                        quickFilterUnstacked = quickFilterUnstacked,
                        onQuickFilterUnstackedChange = onQuickFilterUnstackedChange,
                        modifier = Modifier
                    )
                }

                val overlayOffset = dragPointerPosition - dragTouchOffset
                val overlaySize = itemSize(item)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                overlayOffset.x.roundToInt(),
                                overlayOffset.y.roundToInt()
                            )
                        }
                        .requiredSize(
                            width = with(density) { overlaySize.width.toDp() },
                            height = with(density) { overlaySize.height.toDp() }
                        )
                        .zIndex(2f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    PasswordQuickFilterChipItem(
                        item = item,
                        categoryEditMode = true,
                        quickFilterFavorite = quickFilterFavorite,
                        onQuickFilterFavoriteChange = onQuickFilterFavoriteChange,
                        quickFilter2fa = quickFilter2fa,
                        onQuickFilter2faChange = onQuickFilter2faChange,
                        quickFilterNotes = quickFilterNotes,
                        onQuickFilterNotesChange = onQuickFilterNotesChange,
                        quickFilterUncategorized = quickFilterUncategorized,
                        onQuickFilterUncategorizedChange = onQuickFilterUncategorizedChange,
                        quickFilterLocalOnly = quickFilterLocalOnly,
                        onQuickFilterLocalOnlyChange = onQuickFilterLocalOnlyChange,
                        quickFilterManualStackOnly = quickFilterManualStackOnly,
                        onQuickFilterManualStackOnlyChange = onQuickFilterManualStackOnlyChange,
                        quickFilterNeverStack = quickFilterNeverStack,
                        onQuickFilterNeverStackChange = onQuickFilterNeverStackChange,
                        quickFilterUnstacked = quickFilterUnstacked,
                        onQuickFilterUnstackedChange = onQuickFilterUnstackedChange,
                        modifier = Modifier
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordMenuSection(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
    toggleEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(durationMillis = 160),
        label = "password_menu_section_arrow"
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val baseHeaderModifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 2.dp)
        Row(
            modifier = if (toggleEnabled) {
                baseHeaderModifier
                    .clickable { onExpandedChange(!expanded) }
                    .then(headerModifier)
            } else {
                baseHeaderModifier.then(headerModifier)
            },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(180)) + fadeIn(animationSpec = tween(120)),
            exit = shrinkVertically(animationSpec = tween(140)) + fadeOut(animationSpec = tween(100))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasswordListCategoryChipMenu(
    currentFilter: CategoryFilter,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    bitwardenVaults: List<takagi.ru.monica.data.bitwarden.BitwardenVault>,
    configuredQuickFilterItems: List<takagi.ru.monica.data.PasswordListQuickFilterItem>,
    quickFilterFavorite: Boolean,
    onQuickFilterFavoriteChange: (Boolean) -> Unit,
    quickFilter2fa: Boolean,
    onQuickFilter2faChange: (Boolean) -> Unit,
    quickFilterNotes: Boolean,
    onQuickFilterNotesChange: (Boolean) -> Unit,
    quickFilterUncategorized: Boolean,
    onQuickFilterUncategorizedChange: (Boolean) -> Unit,
    quickFilterLocalOnly: Boolean,
    onQuickFilterLocalOnlyChange: (Boolean) -> Unit,
    quickFilterManualStackOnly: Boolean,
    onQuickFilterManualStackOnlyChange: (Boolean) -> Unit,
    quickFilterNeverStack: Boolean,
    onQuickFilterNeverStackChange: (Boolean) -> Unit,
    quickFilterUnstacked: Boolean,
    onQuickFilterUnstackedChange: (Boolean) -> Unit,
    quickFolderShortcuts: List<PasswordQuickFolderShortcut>,
    topModulesOrder: List<PasswordListTopModule>,
    onTopModulesOrderChange: (List<PasswordListTopModule>) -> Unit,
    onQuickFilterItemsOrderChange: (List<takagi.ru.monica.data.PasswordListQuickFilterItem>) -> Unit,
    launchAnchorBounds: androidx.compose.ui.geometry.Rect?,
    onDismiss: () -> Unit,
    onSelectFilter: (CategoryFilter) -> Unit,
    categories: List<Category> = emptyList(),
    onCreateCategory: (() -> Unit)? = null,
    onRenameCategory: ((Category) -> Unit)? = null,
    onDeleteCategory: ((Category) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val databaseScrollState = rememberScrollState()
    val menuWidth = rememberUnifiedCategoryFilterChipMenuWidth()
    var showDeferredFolderSection by remember { mutableStateOf(false) }
    var quickFiltersExpanded by rememberSaveable { mutableStateOf(true) }
    var foldersExpanded by rememberSaveable { mutableStateOf(true) }
    var categoryEditMode by remember { mutableStateOf(false) }
    var categoryActionTarget by remember { mutableStateOf<Category?>(null) }
    var renameCategoryTarget by remember { mutableStateOf<Category?>(null) }
    var renameCategoryInput by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        showDeferredFolderSection = true
    }
    val quickFilterItems = remember(configuredQuickFilterItems) {
        configuredQuickFilterItems.ifEmpty { takagi.ru.monica.data.PasswordListQuickFilterItem.DEFAULT_ORDER }
    }
    var quickFilterOrder by remember(quickFilterItems) { mutableStateOf(quickFilterItems) }
    val quickFilterMeasuredSizes = remember {
        mutableStateMapOf<takagi.ru.monica.data.PasswordListQuickFilterItem, IntSize>()
    }
    var moduleOrder by remember(topModulesOrder) {
        mutableStateOf(PasswordListTopModule.sanitizeOrder(topModulesOrder))
    }
    var draggingModule by remember { mutableStateOf<PasswordListTopModule?>(null) }
    var settlingModule by remember { mutableStateOf<PasswordListTopModule?>(null) }
    var moduleDragOffset by remember { mutableStateOf(Offset.Zero) }
    var moduleReorderEpoch by remember { mutableIntStateOf(0) }
    val moduleSettleOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val modulePlacementOffsets = remember {
        mutableMapOf<PasswordListTopModule, Animatable<Offset, AnimationVector2D>>()
    }
    val previousModuleBounds = remember { mutableMapOf<PasswordListTopModule, androidx.compose.ui.geometry.Rect>() }
    val lastModuleAnimatedEpoch = remember { mutableMapOf<PasswordListTopModule, Int>() }
    val moduleBounds = remember { mutableMapOf<PasswordListTopModule, androidx.compose.ui.geometry.Rect>() }

    LaunchedEffect(quickFilterItems) {
        quickFilterOrder = quickFilterItems
    }
    LaunchedEffect(topModulesOrder) {
        if (draggingModule == null) {
            moduleOrder = PasswordListTopModule.sanitizeOrder(topModulesOrder)
        }
    }
    LaunchedEffect(categoryEditMode) {
        if (!categoryEditMode) {
            draggingModule = null
            settlingModule = null
            moduleDragOffset = Offset.Zero
            moduleSettleOffset.stop()
            moduleSettleOffset.snapTo(Offset.Zero)
            modulePlacementOffsets.values.forEach { animatable ->
                animatable.stop()
                coroutineScope.launch { animatable.snapTo(Offset.Zero) }
            }
            lastModuleAnimatedEpoch.clear()
        }
    }

    val availableModules = remember(showDeferredFolderSection, quickFolderShortcuts, quickFilterOrder) {
        buildList {
            if (quickFilterOrder.isNotEmpty()) add(PasswordListTopModule.QUICK_FILTERS)
            if (showDeferredFolderSection && quickFolderShortcuts.isNotEmpty()) add(PasswordListTopModule.QUICK_FOLDERS)
        }
    }
    val orderedModules = remember(moduleOrder, availableModules) {
        moduleOrder.filter { it in availableModules }
    }

    fun settleModule(module: PasswordListTopModule) {
        val startOffset = moduleDragOffset
        draggingModule = null
        moduleDragOffset = Offset.Zero
        settlingModule = module
        coroutineScope.launch {
            try {
                moduleSettleOffset.stop()
                moduleSettleOffset.snapTo(startOffset)
                moduleSettleOffset.animateTo(
                    targetValue = Offset.Zero,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                )
            } catch (_: CancellationException) {
            } finally {
                if (settlingModule == module) {
                    settlingModule = null
                }
                moduleSettleOffset.snapTo(Offset.Zero)
            }
        }
    }

    fun animateModulePlacementIfNeeded(
        module: PasswordListTopModule,
        newRect: androidx.compose.ui.geometry.Rect
    ) {
        val previousRect = previousModuleBounds.put(module, newRect)
        if (previousRect == null || draggingModule == module || settlingModule == module) return
        if (moduleReorderEpoch == 0 || lastModuleAnimatedEpoch[module] == moduleReorderEpoch) return
        val delta = previousRect.topLeft - newRect.topLeft
        if (delta == Offset.Zero) return
        lastModuleAnimatedEpoch[module] = moduleReorderEpoch
        val animatable = modulePlacementOffsets.getOrPut(module) {
            Animatable(Offset.Zero, Offset.VectorConverter)
        }
        coroutineScope.launch {
            try {
                animatable.stop()
                animatable.snapTo(delta)
                animatable.animateTo(
                    targetValue = Offset.Zero,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                )
            } catch (_: CancellationException) {
            }
        }
    }

    fun updateModuleBounds(
        module: PasswordListTopModule,
        rect: androidx.compose.ui.geometry.Rect
    ) {
        val previousRect = moduleBounds[module]
        if (previousRect == rect) return
        moduleBounds[module] = rect
    }

    fun swapModuleIfNeeded(module: PasswordListTopModule) {
        val draggedRect = moduleBounds[module] ?: return
        val probe = draggedRect.center + moduleDragOffset
        val target = orderedModules.firstOrNull { candidate ->
            candidate != module && moduleBounds[candidate]?.contains(probe) == true
        } ?: return
        val targetRect = moduleBounds[target] ?: return
        val fromIndex = moduleOrder.indexOf(module)
        val toIndex = moduleOrder.indexOf(target)
        if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) return
        val reordered = reorderList(moduleOrder, fromIndex, toIndex)
        moduleOrder = reordered
        moduleReorderEpoch += 1
        onTopModulesOrderChange(reordered)
        moduleDragOffset += draggedRect.center - targetRect.center
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
                    Text(
                        text = stringResource(R.string.category_selection_menu_databases),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(databaseScrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MonicaExpressiveFilterChip(
                            selected = currentFilter is CategoryFilter.All,
                            onClick = { onSelectFilter(CategoryFilter.All) },
                            label = stringResource(R.string.category_all),
                            leadingIcon = Icons.Default.List
                        )
                        MonicaExpressiveFilterChip(
                            selected = currentFilter.isMonicaDatabaseFilter(),
                            onClick = { onSelectFilter(CategoryFilter.Local) },
                            label = stringResource(R.string.category_selection_menu_local_database),
                            leadingIcon = Icons.Default.Smartphone
                        )
                        keepassDatabases.forEach { database ->
                            MonicaExpressiveFilterChip(
                                selected = currentFilter.isKeePassDatabaseFilter(database.id),
                                onClick = { onSelectFilter(CategoryFilter.KeePassDatabase(database.id)) },
                                label = database.name,
                                leadingIcon = Icons.Default.Key
                            )
                        }
                        bitwardenVaults.forEach { vault ->
                            MonicaExpressiveFilterChip(
                                selected = currentFilter.isBitwardenVaultFilter(vault.id),
                                onClick = { onSelectFilter(CategoryFilter.BitwardenVault(vault.id)) },
                                label = vault.email.ifBlank { "Bitwarden" },
                                leadingIcon = Icons.Default.CloudSync
                            )
                        }
                    }

        orderedModules.forEach { module ->
            key(module) {
                val modulePlacementOffset = modulePlacementOffsets[module]?.value ?: Offset.Zero
                val moduleDisplayOffset = when {
                    draggingModule == module -> moduleDragOffset
                    settlingModule == module -> moduleSettleOffset.value
                    else -> modulePlacementOffset
                }
                when (module) {
                PasswordListTopModule.QUICK_FILTERS -> {
                    PasswordMenuSection(
                        title = stringResource(R.string.category_selection_menu_quick_filters),
                        expanded = quickFiltersExpanded,
                        onExpandedChange = { quickFiltersExpanded = it },
                        modifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                val rect = coordinates.boundsInWindow()
                                updateModuleBounds(module, rect)
                                animateModulePlacementIfNeeded(module, rect)
                            }
                            .graphicsLayer {
                                translationX = moduleDisplayOffset.x
                                translationY = moduleDisplayOffset.y
                            }
                            .zIndex(if (draggingModule == module || settlingModule == module) 1f else 0f),
                        headerModifier = Modifier.pointerInput(categoryEditMode, module) {
                            if (!categoryEditMode) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    settlingModule = null
                                    coroutineScope.launch {
                                        moduleSettleOffset.stop()
                                        moduleSettleOffset.snapTo(Offset.Zero)
                                    }
                                    draggingModule = module
                                    moduleDragOffset = Offset.Zero
                                },
                                onDragCancel = {
                                    settleModule(module)
                                },
                                onDragEnd = {
                                    settleModule(module)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    moduleDragOffset += Offset(dragAmount.x, dragAmount.y)
                                    swapModuleIfNeeded(module)
                                }
                            )
                        },
                        toggleEnabled = !categoryEditMode
                    ) {
                        if (categoryEditMode) {
                            val horizontalContentPadding = 32.dp
                            PasswordQuickFilterEditGrid(
                                items = quickFilterOrder,
                                measuredSizes = quickFilterMeasuredSizes,
                                availableWidth = (menuWidth - horizontalContentPadding).coerceAtLeast(220.dp),
                                quickFilterFavorite = quickFilterFavorite,
                                onQuickFilterFavoriteChange = onQuickFilterFavoriteChange,
                                quickFilter2fa = quickFilter2fa,
                                onQuickFilter2faChange = onQuickFilter2faChange,
                                quickFilterNotes = quickFilterNotes,
                                onQuickFilterNotesChange = onQuickFilterNotesChange,
                                quickFilterUncategorized = quickFilterUncategorized,
                                onQuickFilterUncategorizedChange = onQuickFilterUncategorizedChange,
                                quickFilterLocalOnly = quickFilterLocalOnly,
                                onQuickFilterLocalOnlyChange = onQuickFilterLocalOnlyChange,
                                quickFilterManualStackOnly = quickFilterManualStackOnly,
                                onQuickFilterManualStackOnlyChange = onQuickFilterManualStackOnlyChange,
                                quickFilterNeverStack = quickFilterNeverStack,
                                onQuickFilterNeverStackChange = onQuickFilterNeverStackChange,
                                quickFilterUnstacked = quickFilterUnstacked,
                                onQuickFilterUnstackedChange = onQuickFilterUnstackedChange,
                                onOrderCommitted = { reordered ->
                                    if (reordered != quickFilterOrder) {
                                        quickFilterOrder = reordered
                                        onQuickFilterItemsOrderChange(reordered)
                                    }
                                }
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickFilterOrder.forEach { item ->
                                    key(item) {
                                        Box(
                                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                                val size = coordinates.size
                                                if (quickFilterMeasuredSizes[item] != size) {
                                                    quickFilterMeasuredSizes[item] = size
                                                }
                                            }
                                        ) {
                                            PasswordQuickFilterChipItem(
                                                item = item,
                                                categoryEditMode = false,
                                                quickFilterFavorite = quickFilterFavorite,
                                                onQuickFilterFavoriteChange = onQuickFilterFavoriteChange,
                                                quickFilter2fa = quickFilter2fa,
                                                onQuickFilter2faChange = onQuickFilter2faChange,
                                                quickFilterNotes = quickFilterNotes,
                                                onQuickFilterNotesChange = onQuickFilterNotesChange,
                                                quickFilterUncategorized = quickFilterUncategorized,
                                                onQuickFilterUncategorizedChange = onQuickFilterUncategorizedChange,
                                                quickFilterLocalOnly = quickFilterLocalOnly,
                                                onQuickFilterLocalOnlyChange = onQuickFilterLocalOnlyChange,
                                                quickFilterManualStackOnly = quickFilterManualStackOnly,
                                                onQuickFilterManualStackOnlyChange = onQuickFilterManualStackOnlyChange,
                                                quickFilterNeverStack = quickFilterNeverStack,
                                                onQuickFilterNeverStackChange = onQuickFilterNeverStackChange,
                                                quickFilterUnstacked = quickFilterUnstacked,
                                                onQuickFilterUnstackedChange = onQuickFilterUnstackedChange
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                PasswordListTopModule.QUICK_FOLDERS -> {
                    PasswordMenuSection(
                        title = stringResource(R.string.category_selection_menu_folders),
                        expanded = foldersExpanded,
                        onExpandedChange = { foldersExpanded = it },
                        modifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                val rect = coordinates.boundsInWindow()
                                updateModuleBounds(module, rect)
                                animateModulePlacementIfNeeded(module, rect)
                            }
                            .graphicsLayer {
                                translationX = moduleDisplayOffset.x
                                translationY = moduleDisplayOffset.y
                            }
                            .zIndex(if (draggingModule == module || settlingModule == module) 1f else 0f),
                        headerModifier = Modifier.pointerInput(categoryEditMode, module) {
                            if (!categoryEditMode) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    settlingModule = null
                                    coroutineScope.launch {
                                        moduleSettleOffset.stop()
                                        moduleSettleOffset.snapTo(Offset.Zero)
                                    }
                                    draggingModule = module
                                    moduleDragOffset = Offset.Zero
                                },
                                onDragCancel = {
                                    settleModule(module)
                                },
                                onDragEnd = {
                                    settleModule(module)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    moduleDragOffset += Offset(dragAmount.x, dragAmount.y)
                                    swapModuleIfNeeded(module)
                                }
                            )
                        },
                        toggleEnabled = !categoryEditMode
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickFolderShortcuts.forEach { shortcut ->
                                val editableCategory = (shortcut.targetFilter as? CategoryFilter.Custom)
                                    ?.let { filter -> categories.firstOrNull { it.id == filter.categoryId } }
                                MonicaExpressiveFilterChip(
                                    selected = shortcut.targetFilter == currentFilter,
                                    onClick = {
                                        if (categoryEditMode && editableCategory != null) {
                                            categoryActionTarget = editableCategory
                                        } else {
                                            onSelectFilter(shortcut.targetFilter)
                                        }
                                    },
                                    label = shortcut.title,
                                    leadingIcon = if (categoryEditMode && editableCategory != null) {
                                        Icons.Default.Edit
                                    } else if (shortcut.isBack) {
                                        Icons.AutoMirrored.Filled.KeyboardArrowLeft
                                    } else {
                                        Icons.Default.Folder
                                    }
                                )
                            }
                        }
                    }
                }
            }
            }
        }

        if (
            onCreateCategory != null ||
            ((onRenameCategory != null || onDeleteCategory != null) && categories.isNotEmpty())
        ) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onCreateCategory != null) {
                    OutlinedButton(
                        onClick = {
                            categoryEditMode = false
                            onDismiss()
                            onCreateCategory()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.add_category))
                    }
                }
                if ((onRenameCategory != null || onDeleteCategory != null) && categories.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { categoryEditMode = !categoryEditMode },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (categoryEditMode) {
                                stringResource(R.string.cancel)
                            } else {
                                stringResource(R.string.edit_category)
                            }
                        )
                    }
                }
            }
        }

        if (categoryActionTarget != null) {
            val target = categoryActionTarget!!
            AlertDialog(
                onDismissRequest = { categoryActionTarget = null },
                title = { Text(stringResource(R.string.edit_category)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = target.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (onRenameCategory != null) {
                            FilledTonalButton(
                                onClick = {
                                    categoryActionTarget = null
                                    renameCategoryTarget = target
                                    renameCategoryInput = target.name
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.rename_category))
                            }
                        }
                        if (onDeleteCategory != null) {
                            FilledTonalButton(
                                onClick = {
                                    categoryActionTarget = null
                                    onDismiss()
                                    onDeleteCategory(target)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.delete))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { categoryActionTarget = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (renameCategoryTarget != null) {
            val target = renameCategoryTarget!!
            AlertDialog(
                onDismissRequest = { renameCategoryTarget = null },
                title = { Text(stringResource(R.string.rename_category)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = renameCategoryInput,
                            onValueChange = { renameCategoryInput = it },
                            label = { Text(stringResource(R.string.category_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val newName = renameCategoryInput.trim()
                            if (newName.isBlank()) return@TextButton
                            onRenameCategory?.invoke(target.copy(name = newName))
                            renameCategoryTarget = null
                        }
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameCategoryTarget = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

private fun CategoryFilter.isMonicaDatabaseFilter(): Boolean = when (this) {
    is CategoryFilter.Local,
    is CategoryFilter.Starred,
    is CategoryFilter.Uncategorized,
    is CategoryFilter.LocalStarred,
    is CategoryFilter.LocalUncategorized,
    is CategoryFilter.Custom -> true
    else -> false
}

private fun CategoryFilter.isKeePassDatabaseFilter(databaseId: Long): Boolean = when (this) {
    is CategoryFilter.KeePassDatabase -> this.databaseId == databaseId
    is CategoryFilter.KeePassGroupFilter -> this.databaseId == databaseId
    is CategoryFilter.KeePassDatabaseStarred -> this.databaseId == databaseId
    is CategoryFilter.KeePassDatabaseUncategorized -> this.databaseId == databaseId
    else -> false
}

private fun CategoryFilter.isBitwardenVaultFilter(vaultId: Long): Boolean = when (this) {
    is CategoryFilter.BitwardenVault -> this.vaultId == vaultId
    is CategoryFilter.BitwardenFolderFilter -> this.vaultId == vaultId
    is CategoryFilter.BitwardenVaultStarred -> this.vaultId == vaultId
    is CategoryFilter.BitwardenVaultUncategorized -> this.vaultId == vaultId
    else -> false
}

@Composable
private fun PasswordListDialogs(
    showManualStackConfirmDialog: Boolean,
    onShowManualStackConfirmDialogChange: (Boolean) -> Unit,
    selectedPasswords: Set<Long>,
    selectedManualStackMode: ManualStackDialogMode,
    onSelectedManualStackModeChange: (ManualStackDialogMode) -> Unit,
    viewModel: PasswordViewModel,
    context: Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onSelectionCleared: () -> Unit,
    showBatchDeleteDialog: Boolean,
    onShowBatchDeleteDialogChange: (Boolean) -> Unit,
    passwordInput: String,
    onPasswordInputChange: (String) -> Unit,
    passwordError: Boolean,
    onPasswordErrorChange: (Boolean) -> Unit,
    passwordEntries: List<takagi.ru.monica.data.PasswordEntry>,
    canUseBiometric: Boolean,
    activity: FragmentActivity?,
    biometricHelper: BiometricHelper,
    itemToDelete: takagi.ru.monica.data.PasswordEntry?,
    onItemToDeleteChange: (takagi.ru.monica.data.PasswordEntry?) -> Unit,
    appSettings: takagi.ru.monica.data.AppSettings,
    singleItemPasswordInput: String,
    onSingleItemPasswordInputChange: (String) -> Unit,
    showSingleItemPasswordVerify: Boolean,
    onShowSingleItemPasswordVerifyChange: (Boolean) -> Unit
) {
    if (showManualStackConfirmDialog) {
        AlertDialog(
            onDismissRequest = { onShowManualStackConfirmDialogChange(false) },
            title = { Text(text = stringResource(R.string.batch_stack_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(
                            R.string.batch_stack_confirm_message,
                            selectedPasswords.size
                        )
                    )
                    ManualStackDialogMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectedManualStackModeChange(mode) },
                            verticalAlignment = Alignment.Top
                        ) {
                            RadioButton(
                                selected = selectedManualStackMode == mode,
                                onClick = { onSelectedManualStackModeChange(mode) }
                            )
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                Text(
                                    text = stringResource(mode.titleRes),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(mode.descRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            val selectedIds = selectedPasswords.toList()
                            val mode = when (selectedManualStackMode) {
                                ManualStackDialogMode.STACK -> PasswordViewModel.ManualStackMode.STACK
                                ManualStackDialogMode.AUTO_STACK -> PasswordViewModel.ManualStackMode.AUTO_STACK
                                ManualStackDialogMode.NEVER_STACK -> PasswordViewModel.ManualStackMode.NEVER_STACK
                            }
                            val handledCount = viewModel.applyManualStackMode(selectedIds, mode)
                            if (handledCount > 0) {
                                val toastRes = when (selectedManualStackMode) {
                                    ManualStackDialogMode.STACK -> R.string.batch_stack_success
                                    ManualStackDialogMode.AUTO_STACK -> R.string.batch_stack_auto_success
                                    ManualStackDialogMode.NEVER_STACK -> R.string.batch_stack_never_success
                                }
                                Toast.makeText(
                                    context,
                                    context.getString(toastRes, handledCount),
                                    Toast.LENGTH_SHORT
                                ).show()
                                onSelectionCleared()
                            }
                            onShowManualStackConfirmDialogChange(false)
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowManualStackConfirmDialogChange(false) }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showBatchDeleteDialog) {
        val biometricAction = if (canUseBiometric) {
            {
                val hostActivity = activity
                if (hostActivity == null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.biometric_not_available),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    biometricHelper.authenticate(
                        activity = hostActivity,
                        title = context.getString(R.string.verify_identity),
                        subtitle = context.getString(R.string.verify_to_delete),
                        onSuccess = {
                            coroutineScope.launch {
                                val toDelete = passwordEntries.filter { selectedPasswords.contains(it.id) }
                                toDelete.forEach { viewModel.deletePasswordEntry(it) }
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.deleted_items, toDelete.size),
                                    Toast.LENGTH_SHORT
                                ).show()
                                onSelectionCleared()
                                onPasswordInputChange("")
                                onPasswordErrorChange(false)
                                onShowBatchDeleteDialogChange(false)
                            }
                        },
                        onError = { error ->
                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                        },
                        onFailed = {}
                    )
                }
            }
        } else {
            null
        }
        M3IdentityVerifyDialog(
            title = stringResource(R.string.verify_identity),
            message = stringResource(R.string.batch_delete_passwords_message, selectedPasswords.size),
            passwordValue = passwordInput,
            onPasswordChange = {
                onPasswordInputChange(it)
                onPasswordErrorChange(false)
            },
            onDismiss = {
                onShowBatchDeleteDialogChange(false)
                onPasswordInputChange("")
                onPasswordErrorChange(false)
            },
            onConfirm = {
                if (SecurityManager(context).verifyMasterPassword(passwordInput)) {
                    coroutineScope.launch {
                        val toDelete = passwordEntries.filter { selectedPasswords.contains(it.id) }
                        toDelete.forEach { viewModel.deletePasswordEntry(it) }
                        Toast.makeText(
                            context,
                            context.getString(R.string.deleted_items, toDelete.size),
                            Toast.LENGTH_SHORT
                        ).show()
                        onSelectionCleared()
                        onPasswordInputChange("")
                        onPasswordErrorChange(false)
                        onShowBatchDeleteDialogChange(false)
                    }
                } else {
                    onPasswordErrorChange(true)
                }
            },
            confirmText = stringResource(R.string.delete),
            destructiveConfirm = true,
            isPasswordError = passwordError,
            passwordErrorText = stringResource(R.string.current_password_incorrect),
            onBiometricClick = biometricAction,
            biometricHintText = if (biometricAction == null) {
                context.getString(R.string.biometric_not_available)
            } else {
                null
            }
        )
    }

    itemToDelete?.let { item ->
        DeleteConfirmDialog(
            itemTitle = item.title,
            itemType = stringResource(R.string.item_type_password),
            biometricEnabled = appSettings.biometricEnabled,
            onDismiss = {
                onItemToDeleteChange(null)
            },
            onConfirmWithPassword = { password ->
                onSingleItemPasswordInputChange(password)
                onShowSingleItemPasswordVerifyChange(true)
            },
            onConfirmWithBiometric = {
                coroutineScope.launch {
                    viewModel.deletePasswordEntry(item)
                    Toast.makeText(
                        context,
                        context.getString(R.string.deleted),
                        Toast.LENGTH_SHORT
                    ).show()
                    onItemToDeleteChange(null)
                }
            }
        )
    }

    if (showSingleItemPasswordVerify && itemToDelete != null) {
        val pendingDeleteItem = itemToDelete
        LaunchedEffect(pendingDeleteItem.id, showSingleItemPasswordVerify) {
            val securityManager = SecurityManager(context)
            if (securityManager.verifyMasterPassword(singleItemPasswordInput)) {
                viewModel.deletePasswordEntry(pendingDeleteItem)
                Toast.makeText(
                    context,
                    context.getString(R.string.deleted),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.current_password_incorrect),
                    Toast.LENGTH_SHORT
                ).show()
            }
            onItemToDeleteChange(null)
            onSingleItemPasswordInputChange("")
            onShowSingleItemPasswordVerifyChange(false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordDisplayOptionsSheet(
    stackCardMode: StackCardMode,
    groupMode: String,
    passwordCardDisplayMode: takagi.ru.monica.data.PasswordCardDisplayMode,
    onDismiss: () -> Unit,
    onStackCardModeSelected: (StackCardMode) -> Unit,
    onGroupModeSelected: (String) -> Unit,
    onPasswordCardDisplayModeSelected: (takagi.ru.monica.data.PasswordCardDisplayMode) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun dismissDisplayOptionsSheet(afterDismiss: (() -> Unit)? = null) {
        coroutineScope.launch {
            if (sheetState.isVisible) {
                sheetState.hide()
            }
            onDismiss()
            afterDismiss?.invoke()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { dismissDisplayOptionsSheet() },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.display_options_menu_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            Text(
                text = stringResource(R.string.stack_mode_menu_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            listOf(StackCardMode.AUTO, StackCardMode.ALWAYS_EXPANDED).forEach { mode ->
                val selected = mode == stackCardMode
                val (modeTitle, desc, icon) = when (mode) {
                    StackCardMode.AUTO -> Triple(
                        stringResource(R.string.stack_mode_auto),
                        stringResource(R.string.stack_mode_auto_desc),
                        Icons.Default.AutoAwesome,
                    )

                    StackCardMode.ALWAYS_EXPANDED -> Triple(
                        stringResource(R.string.stack_mode_expand),
                        stringResource(R.string.stack_mode_expand_desc),
                        Icons.Default.UnfoldMore,
                    )
                }
                takagi.ru.monica.ui.components.SettingsOptionItem(
                    title = modeTitle,
                    description = desc,
                    icon = icon,
                    selected = selected,
                    onClick = {
                        dismissDisplayOptionsSheet {
                            onStackCardModeSelected(mode)
                        }
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Text(
                text = stringResource(R.string.group_mode_menu_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            val groupModes = listOf(
                "smart" to Triple(
                    stringResource(R.string.group_mode_smart),
                    stringResource(R.string.group_mode_smart_desc),
                    Icons.Default.DashboardCustomize,
                ),
                "note" to Triple(
                    stringResource(R.string.group_mode_note),
                    stringResource(R.string.group_mode_note_desc),
                    Icons.Default.Description,
                ),
                "website" to Triple(
                    stringResource(R.string.group_mode_website),
                    stringResource(R.string.group_mode_website_desc),
                    Icons.Default.Language,
                ),
                "app" to Triple(
                    stringResource(R.string.group_mode_app),
                    stringResource(R.string.group_mode_app_desc),
                    Icons.Default.Apps,
                ),
                "title" to Triple(
                    stringResource(R.string.group_mode_title),
                    stringResource(R.string.group_mode_title_desc),
                    Icons.Default.Title,
                )
            )
            groupModes.forEach { (modeKey, meta) ->
                val selected = groupMode == modeKey
                val (modeTitle, desc, icon) = meta
                takagi.ru.monica.ui.components.SettingsOptionItem(
                    title = modeTitle,
                    description = desc,
                    icon = icon,
                    selected = selected,
                    onClick = {
                        dismissDisplayOptionsSheet {
                            onGroupModeSelected(modeKey)
                        }
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Text(
                text = stringResource(R.string.password_card_display_mode_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            listOf(
                takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL,
                takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_USERNAME,
                takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_ONLY
            ).forEach { mode ->
                val selected = mode == passwordCardDisplayMode
                val (modeTitle, desc, icon) = when (mode) {
                    takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL -> Triple(
                        stringResource(R.string.display_mode_all),
                        stringResource(R.string.display_mode_all_desc),
                        Icons.Default.Visibility,
                    )

                    takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_USERNAME -> Triple(
                        stringResource(R.string.display_mode_title_username),
                        stringResource(R.string.display_mode_title_username_desc),
                        Icons.Default.Person,
                    )

                    takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_ONLY -> Triple(
                        stringResource(R.string.display_mode_title_only),
                        stringResource(R.string.display_mode_title_only_desc),
                        Icons.Default.Title,
                    )
                }
                takagi.ru.monica.ui.components.SettingsOptionItem(
                    title = modeTitle,
                    description = desc,
                    icon = icon,
                    selected = selected,
                    onClick = {
                        dismissDisplayOptionsSheet {
                            onPasswordCardDisplayModeSelected(mode)
                        }
                    }
                )
            }
        }
    }
}

private const val QUICK_FOLDER_ROOT_ALL = "all"
private const val QUICK_FOLDER_ROOT_LOCAL = "local"
private const val QUICK_FOLDER_ROOT_STARRED = "starred"
private const val QUICK_FOLDER_ROOT_UNCATEGORIZED = "uncategorized"
private const val QUICK_FOLDER_ROOT_LOCAL_STARRED = "local_starred"
private const val QUICK_FOLDER_ROOT_LOCAL_UNCATEGORIZED = "local_uncategorized"
private const val MONICA_MANUAL_STACK_GROUP_FIELD_TITLE = "__monica_manual_stack_group"
private const val MONICA_NO_STACK_FIELD_TITLE = "__monica_no_stack"
private const val MANUAL_STACK_GROUP_KEY_PREFIX = "manual_stack:"
private const val NO_STACK_GROUP_KEY_PREFIX = "no_stack:"

private enum class ManualStackDialogMode(
    val titleRes: Int,
    val descRes: Int
) {
    STACK(
        titleRes = R.string.batch_stack_mode_stack,
        descRes = R.string.batch_stack_mode_stack_desc
    ),
    AUTO_STACK(
        titleRes = R.string.batch_stack_mode_auto,
        descRes = R.string.batch_stack_mode_auto_desc
    ),
    NEVER_STACK(
        titleRes = R.string.batch_stack_mode_never,
        descRes = R.string.batch_stack_mode_never_desc
    )
}

private data class PasswordQuickFolderNode(
    val category: Category,
    val path: String,
    val parentPath: String?,
    val displayName: String
)

private data class PasswordQuickFolderShortcut(
    val key: String,
    val title: String,
    val subtitle: String,
    val isBack: Boolean,
    val targetFilter: CategoryFilter,
    val passwordCount: Int?
)

private data class PasswordQuickFolderBreadcrumb(
    val key: String,
    val title: String,
    val targetFilter: CategoryFilter,
    val isCurrent: Boolean
)

@Composable
private fun <T> rememberAsyncComputed(
    vararg keys: Any?,
    initialValue: T,
    compute: suspend () -> T
): T {
    val state = remember { mutableStateOf(initialValue) }
    val latestCompute by rememberUpdatedState(compute)

    LaunchedEffect(*keys) {
        state.value = withContext(Dispatchers.Default) {
            latestCompute()
        }
    }

    return state.value
}

private fun normalizePasswordQuickFolderPath(path: String): String {
    return path
        .split("/")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString("/")
}

private fun buildLocalQuickFolderPasswordCountByCategoryId(
    entries: List<takagi.ru.monica.data.PasswordEntry>
): Map<Long, Int> {
    return entries
        .asSequence()
        .mapNotNull { entry ->
            val isLocalEntry = entry.keepassDatabaseId == null && entry.bitwardenVaultId == null
            if (!isLocalEntry) {
                null
            } else {
                entry.categoryId
            }
        }
        .groupingBy { it }
        .eachCount()
}

private fun passwordQuickFolderParentPath(path: String): String? {
    val normalized = normalizePasswordQuickFolderPath(path)
    if (!normalized.contains('/')) return null
    return normalized.substringBeforeLast('/').ifBlank { null }
}

private fun buildPasswordQuickFolderNodes(categories: List<Category>): List<PasswordQuickFolderNode> {
    return categories
        .sortedWith(
            compareBy<Category>({ it.sortOrder }, { it.id })
        )
        .mapNotNull { category ->
            val normalizedPath = normalizePasswordQuickFolderPath(category.name)
            if (normalizedPath.isBlank()) {
                null
            } else {
                PasswordQuickFolderNode(
                    category = category,
                    path = normalizedPath,
                    parentPath = passwordQuickFolderParentPath(normalizedPath),
                    displayName = normalizedPath.substringAfterLast('/')
                )
            }
        }
        .distinctBy { it.path }
}

private fun buildQuickFolderShortcuts(
    context: Context,
    quickFoldersEnabledForCurrentFilter: Boolean,
    includeBackNavigation: Boolean,
    currentFilter: CategoryFilter,
    quickFolderStyle: takagi.ru.monica.data.PasswordListQuickFolderStyle,
    quickFolderCurrentPath: String?,
    quickFolderNodes: List<PasswordQuickFolderNode>,
    quickFolderNodeByPath: Map<String, PasswordQuickFolderNode>,
    quickFolderRootFilter: CategoryFilter,
    quickFolderPasswordCountByCategoryId: Map<Long, Int>,
    allPasswords: List<takagi.ru.monica.data.PasswordEntry>,
    searchScopedPasswords: List<takagi.ru.monica.data.PasswordEntry>,
    isSearchActive: Boolean,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    keepassGroupsForSelectedDb: List<takagi.ru.monica.utils.KeePassGroupInfo>,
    bitwardenVaults: List<takagi.ru.monica.data.bitwarden.BitwardenVault>,
    selectedBitwardenFolders: List<takagi.ru.monica.data.bitwarden.BitwardenFolder>,
    categories: List<Category>
): List<PasswordQuickFolderShortcut> {
    if (!quickFoldersEnabledForCurrentFilter || !currentFilter.supportsQuickFolders()) {
        return emptyList()
    }

    val shortcuts = mutableListOf<PasswordQuickFolderShortcut>()
    val quickFolderSourceEntries = if (isSearchActive) {
        searchScopedPasswords
    } else {
        allPasswords
    }
    if (includeBackNavigation && currentFilter is CategoryFilter.Custom) {
        val parentPath = quickFolderCurrentPath?.let(::passwordQuickFolderParentPath)
        val parentTarget = if (parentPath != null) {
            quickFolderNodeByPath[parentPath]?.category?.let { CategoryFilter.Custom(it.id) }
                ?: quickFolderRootFilter
        } else {
            quickFolderRootFilter
        }
        shortcuts += PasswordQuickFolderShortcut(
            key = "back_${quickFolderCurrentPath.orEmpty()}",
            title = context.getString(R.string.password_list_quick_folder_back),
            subtitle = context.getString(R.string.password_list_quick_folder_back_subtitle),
            isBack = true,
            targetFilter = parentTarget,
            passwordCount = null
        )
    }

    val shouldShowMonicaFolderShortcuts = when (currentFilter) {
        is CategoryFilter.All,
        is CategoryFilter.Local,
        is CategoryFilter.Starred,
        is CategoryFilter.Uncategorized,
        is CategoryFilter.LocalStarred,
        is CategoryFilter.LocalUncategorized,
        is CategoryFilter.Custom -> true

        else -> false
    }

    if (shouldShowMonicaFolderShortcuts) {
        val targetParentPath = quickFolderCurrentPath
        val children = quickFolderNodes.filter { node ->
            node.parentPath == targetParentPath
        }
        children.forEach { node ->
            val passwordCount = quickFolderPasswordCountByCategoryId[node.category.id] ?: 0
            if (isSearchActive && passwordCount <= 0) {
                return@forEach
            }
            shortcuts += PasswordQuickFolderShortcut(
                key = "folder_${node.category.id}_${node.path}",
                title = node.displayName,
                subtitle = "Monica",
                isBack = false,
                targetFilter = CategoryFilter.Custom(node.category.id),
                passwordCount = passwordCount
            )
        }
    }

    when (val filter = currentFilter) {
        is CategoryFilter.KeePassDatabase -> {
            val databaseId = filter.databaseId
            shortcuts += buildKeePassDatabaseQuickFolderShortcuts(
                databaseId = databaseId,
                keepassGroups = keepassGroupsForSelectedDb,
                allPasswords = quickFolderSourceEntries,
                isSearchActive = isSearchActive
            )
        }

        is CategoryFilter.KeePassGroupFilter -> {
            val databaseId = filter.databaseId
            val currentPath = filter.groupPath.trim('/').trim()
            val parentPath = currentPath.substringBeforeLast('/', missingDelimiterValue = "")

            if (includeBackNavigation) {
                val backTarget = if (parentPath.isBlank()) {
                    CategoryFilter.KeePassDatabase(databaseId)
                } else {
                    CategoryFilter.KeePassGroupFilter(databaseId, parentPath)
                }
                shortcuts += PasswordQuickFolderShortcut(
                    key = "back_keepass_${databaseId}_$currentPath",
                    title = context.getString(R.string.password_list_quick_folder_back),
                    subtitle = context.getString(R.string.password_list_quick_folder_back_subtitle),
                    isBack = true,
                    targetFilter = backTarget,
                    passwordCount = null
                )
            }
            shortcuts += buildKeePassGroupQuickFolderShortcuts(
                databaseId = databaseId,
                currentPath = currentPath,
                keepassGroups = keepassGroupsForSelectedDb,
                allPasswords = quickFolderSourceEntries,
                isSearchActive = isSearchActive
            )
        }

        is CategoryFilter.BitwardenVault,
        is CategoryFilter.BitwardenFolderFilter -> {
            val vaultId = if (filter is CategoryFilter.BitwardenVault) {
                filter.vaultId
            } else {
                (filter as CategoryFilter.BitwardenFolderFilter).vaultId
            }
            val syncedFolderNameById = selectedBitwardenFolders
                .asSequence()
                .map { it.bitwardenFolderId.trim() to it.name.trim() }
                .filter { (folderId, folderName) -> folderId.isNotBlank() && folderName.isNotBlank() }
                .toMap()
            val linkedFolderNameByKey = categories
                .asSequence()
                .mapNotNull { category ->
                    val categoryVaultId = category.bitwardenVaultId
                    val folderId = category.bitwardenFolderId?.trim()?.takeIf { it.isNotBlank() }
                    if (categoryVaultId == vaultId && folderId != null) {
                        folderId to category.name
                    } else {
                        null
                    }
                }
                .toMap()

            val folderCountById = quickFolderSourceEntries
                .asSequence()
                .mapNotNull { entry ->
                    val entryVaultId = entry.bitwardenVaultId
                    val folderId = entry.bitwardenFolderId?.trim()?.takeIf { it.isNotBlank() }
                    if (entryVaultId == vaultId && folderId != null) {
                        folderId
                    } else {
                        null
                    }
                }
                .groupingBy { it }
                .eachCount()
            val knownFolderIds = if (isSearchActive) {
                folderCountById.keys.sorted()
            } else {
                (folderCountById.keys + linkedFolderNameByKey.keys + syncedFolderNameById.keys)
                    .toSet()
                    .sorted()
            }

            knownFolderIds.forEach { folderId ->
                val folderName = syncedFolderNameById[folderId]
                    ?: linkedFolderNameByKey[folderId]
                    ?: "Folder ${folderId.take(8)}"
                shortcuts += PasswordQuickFolderShortcut(
                    key = "bitwarden_${vaultId}_${folderId}",
                    title = folderName,
                    subtitle = "Bitwarden 文件夹",
                    isBack = false,
                    targetFilter = CategoryFilter.BitwardenFolderFilter(folderId = folderId, vaultId = vaultId),
                    passwordCount = folderCountById[folderId] ?: 0
                )
            }
        }

        else -> Unit
    }

    if (currentFilter is CategoryFilter.All && quickFolderCurrentPath == null) {
        val keepassGroups = quickFolderSourceEntries
            .asSequence()
            .mapNotNull { entry ->
                val databaseId = entry.keepassDatabaseId
                val groupPath = entry.keepassGroupPath?.trim()?.takeIf { it.isNotBlank() }
                if (databaseId != null && groupPath != null) {
                    (databaseId to groupPath)
                } else {
                    null
                }
            }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedWith(compareBy({ it.first.first }, { it.first.second }))

        keepassGroups.forEach { (key, count) ->
            val databaseId = key.first
            val groupPath = key.second
            val databaseName = keepassDatabases.find { it.id == databaseId }?.name ?: "KeePass"
            shortcuts += PasswordQuickFolderShortcut(
                key = "keepass_${databaseId}_${groupPath}",
                title = decodeKeePassPathForDisplay(groupPath),
                subtitle = "KeePass 组 · $databaseName",
                isBack = false,
                targetFilter = CategoryFilter.KeePassGroupFilter(databaseId, groupPath),
                passwordCount = count
            )
        }

        val linkedFolderNameByKey = categories
            .asSequence()
            .mapNotNull { category ->
                val vaultId = category.bitwardenVaultId
                val folderId = category.bitwardenFolderId?.trim()?.takeIf { it.isNotBlank() }
                if (vaultId != null && folderId != null) {
                    (vaultId to folderId) to category.name
                } else {
                    null
                }
            }
            .toMap()

        val folderCountByKey = quickFolderSourceEntries
            .asSequence()
            .mapNotNull { entry ->
                val vaultId = entry.bitwardenVaultId
                val folderId = entry.bitwardenFolderId?.trim()?.takeIf { it.isNotBlank() }
                if (vaultId != null && folderId != null) {
                    (vaultId to folderId)
                } else {
                    null
                }
            }
            .groupingBy { it }
            .eachCount()
        val knownFolderKeys = if (isSearchActive) {
            folderCountByKey.keys.sortedWith(compareBy({ it.first }, { it.second }))
        } else {
            (folderCountByKey.keys + linkedFolderNameByKey.keys)
                .toSet()
                .sortedWith(compareBy({ it.first }, { it.second }))
        }

        knownFolderKeys.forEach { key ->
            val vaultId = key.first
            val folderId = key.second
            val vaultName = bitwardenVaults.find { it.id == vaultId }?.email ?: "Bitwarden"
            val folderName = linkedFolderNameByKey[key]
                ?: "Folder ${folderId.take(8)}"
            shortcuts += PasswordQuickFolderShortcut(
                key = "bitwarden_${vaultId}_${folderId}",
                title = folderName,
                subtitle = "Bitwarden 文件夹 · $vaultName",
                isBack = false,
                targetFilter = CategoryFilter.BitwardenFolderFilter(folderId = folderId, vaultId = vaultId),
                passwordCount = folderCountByKey[key] ?: 0
            )
        }
    }

    return shortcuts
}

private fun buildCategoryMenuFolderShortcuts(
    context: Context,
    currentFilter: CategoryFilter,
    quickFolderCurrentPath: String?,
    quickFolderNodes: List<PasswordQuickFolderNode>,
    quickFolderNodeByPath: Map<String, PasswordQuickFolderNode>,
    quickFolderPasswordCountByCategoryId: Map<Long, Int>,
    allPasswords: List<takagi.ru.monica.data.PasswordEntry>,
    searchScopedPasswords: List<takagi.ru.monica.data.PasswordEntry>,
    isSearchActive: Boolean,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    keepassGroupsForSelectedDb: List<takagi.ru.monica.utils.KeePassGroupInfo>,
    bitwardenVaults: List<takagi.ru.monica.data.bitwarden.BitwardenVault>,
    selectedBitwardenFolders: List<takagi.ru.monica.data.bitwarden.BitwardenFolder>,
    categories: List<Category>
): List<PasswordQuickFolderShortcut> {
    if (!currentFilter.supportsQuickFolders()) {
        return emptyList()
    }

    val shortcuts = mutableListOf<PasswordQuickFolderShortcut>()
    val menuSourceEntries = if (isSearchActive) {
        searchScopedPasswords
    } else {
        allPasswords
    }

    if (currentFilter is CategoryFilter.Custom) {
        val parentPath = quickFolderCurrentPath?.let(::passwordQuickFolderParentPath)
        val parentTarget = if (parentPath != null) {
            quickFolderNodeByPath[parentPath]?.category?.let { CategoryFilter.Custom(it.id) }
                ?: CategoryFilter.Local
        } else {
            CategoryFilter.Local
        }
        shortcuts += PasswordQuickFolderShortcut(
            key = "menu_back_${quickFolderCurrentPath.orEmpty()}",
            title = context.getString(R.string.password_list_quick_folder_back),
            subtitle = context.getString(R.string.password_list_quick_folder_back_subtitle),
            isBack = true,
            targetFilter = parentTarget,
            passwordCount = null
        )
    }

    val shouldShowMonicaFolders = when (currentFilter) {
        is CategoryFilter.All,
        is CategoryFilter.Local,
        is CategoryFilter.Starred,
        is CategoryFilter.Uncategorized,
        is CategoryFilter.LocalStarred,
        is CategoryFilter.LocalUncategorized,
        is CategoryFilter.Custom -> true

        else -> false
    }

    if (shouldShowMonicaFolders) {
        val children = quickFolderNodes.filter { node -> node.parentPath == quickFolderCurrentPath }
        children.forEach { node ->
            val passwordCount = quickFolderPasswordCountByCategoryId[node.category.id] ?: 0
            if (isSearchActive && passwordCount <= 0) {
                return@forEach
            }
            shortcuts += PasswordQuickFolderShortcut(
                key = "menu_folder_${node.category.id}_${node.path}",
                title = node.displayName,
                subtitle = "Monica",
                isBack = false,
                targetFilter = CategoryFilter.Custom(node.category.id),
                passwordCount = passwordCount
            )
        }
    }

    when (val filter = currentFilter) {
        is CategoryFilter.KeePassDatabase -> {
            shortcuts += buildKeePassDatabaseQuickFolderShortcuts(
                databaseId = filter.databaseId,
                keepassGroups = keepassGroupsForSelectedDb,
                allPasswords = menuSourceEntries,
                isSearchActive = isSearchActive
            )
        }

        is CategoryFilter.KeePassGroupFilter -> {
            val currentPath = filter.groupPath.trim('/').trim()
            val parentPath = currentPath.substringBeforeLast('/', missingDelimiterValue = "")
            val backTarget = if (parentPath.isBlank()) {
                CategoryFilter.KeePassDatabase(filter.databaseId)
            } else {
                CategoryFilter.KeePassGroupFilter(filter.databaseId, parentPath)
            }
            shortcuts += PasswordQuickFolderShortcut(
                key = "menu_back_keepass_${filter.databaseId}_$currentPath",
                title = context.getString(R.string.password_list_quick_folder_back),
                subtitle = context.getString(R.string.password_list_quick_folder_back_subtitle),
                isBack = true,
                targetFilter = backTarget,
                passwordCount = null
            )
            shortcuts += buildKeePassGroupQuickFolderShortcuts(
                databaseId = filter.databaseId,
                currentPath = currentPath,
                keepassGroups = keepassGroupsForSelectedDb,
                allPasswords = menuSourceEntries,
                isSearchActive = isSearchActive
            )
        }

        is CategoryFilter.BitwardenVault,
        is CategoryFilter.BitwardenFolderFilter -> {
            val vaultId = if (filter is CategoryFilter.BitwardenVault) {
                filter.vaultId
            } else {
                (filter as CategoryFilter.BitwardenFolderFilter).vaultId
            }
            val syncedFolderNameById = selectedBitwardenFolders
                .asSequence()
                .map { it.bitwardenFolderId.trim() to it.name.trim() }
                .filter { (folderId, folderName) -> folderId.isNotBlank() && folderName.isNotBlank() }
                .toMap()
            val linkedFolderNameById = categories
                .asSequence()
                .mapNotNull { category ->
                    val folderId = category.bitwardenFolderId?.trim()?.takeIf { it.isNotBlank() }
                    if (category.bitwardenVaultId == vaultId && folderId != null) {
                        folderId to category.name
                    } else {
                        null
                    }
                }
                .toMap()
            val folderCountById = menuSourceEntries
                .asSequence()
                .mapNotNull { entry ->
                    val folderId = entry.bitwardenFolderId?.trim()?.takeIf { it.isNotBlank() }
                    if (entry.bitwardenVaultId == vaultId && folderId != null) {
                        folderId
                    } else {
                        null
                    }
                }
                .groupingBy { it }
                .eachCount()
            val knownFolderIds = if (isSearchActive) {
                folderCountById.keys.sorted()
            } else {
                (folderCountById.keys + linkedFolderNameById.keys + syncedFolderNameById.keys)
                    .toSet()
                    .sorted()
            }

            knownFolderIds.forEach { folderId ->
                val folderName = syncedFolderNameById[folderId]
                    ?: linkedFolderNameById[folderId]
                    ?: "Folder ${folderId.take(8)}"
                shortcuts += PasswordQuickFolderShortcut(
                    key = "menu_bitwarden_${vaultId}_${folderId}",
                    title = folderName,
                    subtitle = "Bitwarden 文件夹",
                    isBack = false,
                    targetFilter = CategoryFilter.BitwardenFolderFilter(folderId = folderId, vaultId = vaultId),
                    passwordCount = folderCountById[folderId] ?: 0
                )
            }
        }

        else -> Unit
    }

    if (currentFilter is CategoryFilter.All && quickFolderCurrentPath == null) {
        val keepassGroups = menuSourceEntries
            .asSequence()
            .mapNotNull { entry ->
                val databaseId = entry.keepassDatabaseId
                val groupPath = entry.keepassGroupPath?.trim()?.takeIf { it.isNotBlank() }
                if (databaseId != null && groupPath != null) {
                    databaseId to groupPath
                } else {
                    null
                }
            }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedWith(compareBy({ it.first.first }, { it.first.second }))

        keepassGroups.forEach { (key, count) ->
            val databaseName = keepassDatabases.find { it.id == key.first }?.name ?: "KeePass"
            shortcuts += PasswordQuickFolderShortcut(
                key = "menu_keepass_${key.first}_${key.second}",
                title = decodeKeePassPathForDisplay(key.second),
                subtitle = "KeePass 组 · $databaseName",
                isBack = false,
                targetFilter = CategoryFilter.KeePassGroupFilter(key.first, key.second),
                passwordCount = count
            )
        }

        val linkedFolderNameByKey = categories
            .asSequence()
            .mapNotNull { category ->
                val vaultId = category.bitwardenVaultId
                val folderId = category.bitwardenFolderId?.trim()?.takeIf { it.isNotBlank() }
                if (vaultId != null && folderId != null) {
                    (vaultId to folderId) to category.name
                } else {
                    null
                }
            }
            .toMap()
        val folderCountByKey = menuSourceEntries
            .asSequence()
            .mapNotNull { entry ->
                val vaultId = entry.bitwardenVaultId
                val folderId = entry.bitwardenFolderId?.trim()?.takeIf { it.isNotBlank() }
                if (vaultId != null && folderId != null) {
                    vaultId to folderId
                } else {
                    null
                }
            }
            .groupingBy { it }
            .eachCount()
        val knownFolderKeys = if (isSearchActive) {
            folderCountByKey.keys.sortedWith(compareBy({ it.first }, { it.second }))
        } else {
            (folderCountByKey.keys + linkedFolderNameByKey.keys)
                .toSet()
                .sortedWith(compareBy({ it.first }, { it.second }))
        }

        knownFolderKeys.forEach { key ->
            val vaultName = bitwardenVaults.find { it.id == key.first }?.email ?: "Bitwarden"
            val folderName = linkedFolderNameByKey[key] ?: "Folder ${key.second.take(8)}"
            shortcuts += PasswordQuickFolderShortcut(
                key = "menu_bitwarden_${key.first}_${key.second}",
                title = folderName,
                subtitle = "Bitwarden 文件夹 · $vaultName",
                isBack = false,
                targetFilter = CategoryFilter.BitwardenFolderFilter(folderId = key.second, vaultId = key.first),
                passwordCount = folderCountByKey[key] ?: 0
            )
        }
    }

    return shortcuts
}

private fun buildQuickFolderBreadcrumbs(
    context: Context,
    quickFolderPathBannerEnabledForCurrentFilter: Boolean,
    currentFilter: CategoryFilter,
    quickFolderCurrentPath: String?,
    quickFolderNodeByPath: Map<String, PasswordQuickFolderNode>,
    quickFolderRootFilter: CategoryFilter,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    bitwardenVaults: List<takagi.ru.monica.data.bitwarden.BitwardenVault>,
    selectedBitwardenFolders: List<takagi.ru.monica.data.bitwarden.BitwardenFolder>,
    categories: List<Category>
): List<PasswordQuickFolderBreadcrumb> {
    if (!quickFolderPathBannerEnabledForCurrentFilter ||
        !currentFilter.supportsQuickFolderBreadcrumbs()
    ) {
        return emptyList()
    }

    val crumbs = mutableListOf<PasswordQuickFolderBreadcrumb>()
    when (val filter = currentFilter) {
        is CategoryFilter.KeePassDatabase -> {
            val databaseName = keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"
            crumbs += PasswordQuickFolderBreadcrumb(
                key = "root_keepass_${filter.databaseId}",
                title = databaseName,
                targetFilter = filter,
                isCurrent = true
            )
        }

        is CategoryFilter.KeePassGroupFilter -> {
            val databaseName = keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"
            crumbs += PasswordQuickFolderBreadcrumb(
                key = "root_keepass_${filter.databaseId}",
                title = databaseName,
                targetFilter = CategoryFilter.KeePassDatabase(filter.databaseId),
                isCurrent = false
            )
        }

        is CategoryFilter.BitwardenVault -> {
            val vaultName = bitwardenVaults.find { it.id == filter.vaultId }?.email ?: "Bitwarden"
            crumbs += PasswordQuickFolderBreadcrumb(
                key = "root_bitwarden_${filter.vaultId}",
                title = vaultName,
                targetFilter = filter,
                isCurrent = true
            )
        }

        is CategoryFilter.BitwardenFolderFilter -> {
            val vaultName = bitwardenVaults.find { it.id == filter.vaultId }?.email ?: "Bitwarden"
            crumbs += PasswordQuickFolderBreadcrumb(
                key = "root_bitwarden_${filter.vaultId}",
                title = vaultName,
                targetFilter = CategoryFilter.BitwardenVault(filter.vaultId),
                isCurrent = false
            )
        }

        else -> {
            val rootTitle = if (quickFolderRootFilter is CategoryFilter.All) {
                "ALL"
            } else {
                context.getString(R.string.password_list_quick_folder_root_label)
            }
            crumbs += PasswordQuickFolderBreadcrumb(
                key = "root",
                title = rootTitle,
                targetFilter = quickFolderRootFilter,
                isCurrent = quickFolderCurrentPath == null
            )
        }
    }

    when (val filter = currentFilter) {
        is CategoryFilter.Custom -> {
            if (quickFolderRootFilter is CategoryFilter.All) {
                crumbs += PasswordQuickFolderBreadcrumb(
                    key = "source_monica",
                    title = "Monica",
                    targetFilter = CategoryFilter.Local,
                    isCurrent = quickFolderCurrentPath == null
                )
            }
            val path = quickFolderCurrentPath
            if (!path.isNullOrBlank()) {
                var cumulative = ""
                val parts = path.split("/").filter { it.isNotBlank() }
                parts.forEachIndexed { index, part ->
                    cumulative = if (cumulative.isBlank()) part else "$cumulative/$part"
                    val targetFilter = quickFolderNodeByPath[cumulative]
                        ?.category
                        ?.let { CategoryFilter.Custom(it.id) }
                    if (targetFilter != null) {
                        crumbs += PasswordQuickFolderBreadcrumb(
                            key = "path_$cumulative",
                            title = part,
                            targetFilter = targetFilter,
                            isCurrent = index == parts.lastIndex
                        )
                    }
                }
            }
        }

        is CategoryFilter.KeePassDatabase -> Unit

        is CategoryFilter.KeePassGroupFilter -> {
            var cumulative = ""
            val parts = filter.groupPath.split("/").filter { it.isNotBlank() }
            parts.forEachIndexed { index, part ->
                cumulative = if (cumulative.isBlank()) part else "$cumulative/$part"
                crumbs += PasswordQuickFolderBreadcrumb(
                    key = "keepass_path_${filter.databaseId}_$cumulative",
                    title = decodeKeePassPathForDisplay(part),
                    targetFilter = CategoryFilter.KeePassGroupFilter(filter.databaseId, cumulative),
                    isCurrent = index == parts.lastIndex
                )
            }
        }

        is CategoryFilter.BitwardenVault -> Unit

        is CategoryFilter.BitwardenFolderFilter -> {
            val folderName = selectedBitwardenFolders.firstOrNull {
                it.bitwardenFolderId.trim() == filter.folderId
            }?.name?.takeIf { it.isNotBlank() }
                ?: categories.firstOrNull {
                    it.bitwardenVaultId == filter.vaultId &&
                        it.bitwardenFolderId?.trim() == filter.folderId
                }?.name
                ?: "Folder ${filter.folderId.take(8)}"
            crumbs += PasswordQuickFolderBreadcrumb(
                key = "bitwarden_folder_${filter.vaultId}_${filter.folderId}",
                title = folderName,
                targetFilter = filter,
                isCurrent = true
            )
        }

        else -> Unit
    }

    return crumbs
}

@Composable
private fun PasswordQuickFolderBreadcrumbBanner(
    breadcrumbs: List<PasswordQuickFolderBreadcrumb>,
    currentFilter: CategoryFilter,
    onNavigate: (CategoryFilter) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            breadcrumbs.forEachIndexed { index, crumb ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            color = if (crumb.isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.68f)
                            }
                        )
                        .clickable(enabled = !crumb.isCurrent) {
                            if (currentFilter != crumb.targetFilter) {
                                onNavigate(crumb.targetFilter)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = crumb.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (crumb.isCurrent) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
                if (index != breadcrumbs.lastIndex) {
                    Text(
                        text = ">",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordQuickFolderBreadcrumbChip(
    crumb: PasswordQuickFolderBreadcrumb,
    currentFilter: CategoryFilter,
    onNavigate: (CategoryFilter) -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                color = if (crumb.isCurrent) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.68f)
                }
            )
            .clickable(enabled = !crumb.isCurrent) {
                if (currentFilter != crumb.targetFilter) {
                    onNavigate(crumb.targetFilter)
                }
            }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = crumb.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (crumb.isCurrent) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
        )
    }
}

@Composable
private fun PasswordQuickFolderShortcutsSection(
    shortcuts: List<PasswordQuickFolderShortcut>,
    currentFilter: CategoryFilter,
    useM3CardStyle: Boolean,
    onNavigate: (CategoryFilter) -> Unit
) {
    if (useM3CardStyle) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            shortcuts.forEach { shortcut ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (currentFilter != shortcut.targetFilter) {
                                onNavigate(shortcut.targetFilter)
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (shortcut.isBack) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (shortcut.isBack) {
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft
                            } else {
                                Icons.Default.Folder
                            },
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (shortcut.isBack) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = shortcut.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (shortcut.subtitle.isNotBlank()) {
                                Text(
                                    text = shortcut.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!shortcut.isBack) {
                                Text(
                                    text = stringResource(
                                        R.string.password_list_quick_folder_count,
                                        shortcut.passwordCount ?: 0
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Icon(
                            imageVector = if (shortcut.isBack) {
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft
                            } else {
                                Icons.AutoMirrored.Filled.KeyboardArrowRight
                            },
                            contentDescription = null,
                            tint = if (shortcut.isBack) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 2.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            shortcuts.forEach { shortcut ->
                Card(
                    modifier = Modifier
                        .size(width = 182.dp, height = 74.dp)
                        .clickable {
                            if (currentFilter != shortcut.targetFilter) {
                                onNavigate(shortcut.targetFilter)
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (shortcut.isBack) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (shortcut.isBack) {
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft
                            } else {
                                Icons.Default.Folder
                            },
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (shortcut.isBack) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (!shortcut.isBack) {
                                Text(
                                    text = stringResource(
                                        R.string.password_list_quick_folder_count,
                                        shortcut.passwordCount ?: 0
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = shortcut.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (shortcut.subtitle.isNotBlank()) {
                                Text(
                                    text = shortcut.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildKeePassDatabaseQuickFolderShortcuts(
    databaseId: Long,
    keepassGroups: List<takagi.ru.monica.utils.KeePassGroupInfo>,
    allPasswords: List<takagi.ru.monica.data.PasswordEntry>,
    isSearchActive: Boolean
): List<PasswordQuickFolderShortcut> {
    val groupNameByPath = LinkedHashMap<String, String>()
    val directChildPaths = linkedSetOf<String>()
    for (group in keepassGroups) {
        val path = group.path.trim()
        if (path.isBlank()) continue
        groupNameByPath[path] = group.name.trim()
        if (path.substringBeforeLast('/', missingDelimiterValue = "").isBlank()) {
            directChildPaths += path
        }
    }

    if (directChildPaths.isEmpty()) return emptyList()

    val subtreeCountByPath = countKeePassSubtreePasswords(
        databaseId = databaseId,
        childPaths = directChildPaths,
        allPasswords = allPasswords
    )

    return directChildPaths
        .sorted()
        .mapNotNull { childPath ->
            val subtreeCount = subtreeCountByPath[childPath] ?: 0
            if (isSearchActive && subtreeCount <= 0) {
                return@mapNotNull null
            }
            PasswordQuickFolderShortcut(
                key = "keepass_${databaseId}_${childPath}",
                title = groupNameByPath[childPath]
                    ?.takeIf { it.isNotBlank() }
                    ?: decodeKeePassPathForDisplay(childPath),
                subtitle = "KeePass 组",
                isBack = false,
                targetFilter = CategoryFilter.KeePassGroupFilter(databaseId, childPath),
                passwordCount = subtreeCount
            )
        }
}

private fun buildKeePassGroupQuickFolderShortcuts(
    databaseId: Long,
    currentPath: String,
    keepassGroups: List<takagi.ru.monica.utils.KeePassGroupInfo>,
    allPasswords: List<takagi.ru.monica.data.PasswordEntry>,
    isSearchActive: Boolean
): List<PasswordQuickFolderShortcut> {
    val groupNameByPath = LinkedHashMap<String, String>()
    val directChildPaths = linkedSetOf<String>()
    for (group in keepassGroups) {
        val path = group.path.trim()
        if (path.isBlank()) continue
        groupNameByPath[path] = group.name.trim()
        val childParent = path.substringBeforeLast('/', missingDelimiterValue = "")
        if (childParent == currentPath) {
            directChildPaths += path
        }
    }

    if (directChildPaths.isEmpty()) return emptyList()

    val subtreeCountByPath = countKeePassSubtreePasswords(
        databaseId = databaseId,
        childPaths = directChildPaths,
        allPasswords = allPasswords
    )

    return directChildPaths
        .sorted()
        .mapNotNull { childPath ->
            val subtreeCount = subtreeCountByPath[childPath] ?: 0
            if (isSearchActive && subtreeCount <= 0) {
                return@mapNotNull null
            }
            PasswordQuickFolderShortcut(
                key = "keepass_${databaseId}_${childPath}",
                title = groupNameByPath[childPath]
                    ?.takeIf { it.isNotBlank() }
                    ?: decodeKeePassPathForDisplay(childPath),
                subtitle = "KeePass 子组",
                isBack = false,
                targetFilter = CategoryFilter.KeePassGroupFilter(databaseId, childPath),
                passwordCount = subtreeCount
            )
        }
}

private fun countKeePassSubtreePasswords(
    databaseId: Long,
    childPaths: Set<String>,
    allPasswords: List<takagi.ru.monica.data.PasswordEntry>
): Map<String, Int> {
    val counts = childPaths.associateWith { 0 }.toMutableMap()
    if (counts.isEmpty()) return counts

    val sortedPaths = childPaths.sortedByDescending { it.length }
    for (entry in allPasswords) {
        if (entry.keepassDatabaseId != databaseId) continue
        val groupPath = entry.keepassGroupPath?.trim().orEmpty()
        if (groupPath.isBlank()) continue

        val matchPath = sortedPaths.firstOrNull { childPath ->
            groupPath == childPath || groupPath.startsWith("$childPath/")
        } ?: continue
        counts[matchPath] = (counts[matchPath] ?: 0) + 1
    }

    return counts
}

private fun CategoryFilter.supportsQuickFolders(): Boolean = when (this) {
    is CategoryFilter.All,
    is CategoryFilter.Local,
    is CategoryFilter.Starred,
    is CategoryFilter.Uncategorized,
    is CategoryFilter.LocalStarred,
    is CategoryFilter.LocalUncategorized,
    is CategoryFilter.Custom,
    is CategoryFilter.KeePassDatabase,
    is CategoryFilter.KeePassGroupFilter,
    is CategoryFilter.BitwardenVault,
    is CategoryFilter.BitwardenFolderFilter -> true

    else -> false
}

private fun CategoryFilter.supportsQuickFolderBreadcrumbs(): Boolean = when (this) {
    is CategoryFilter.KeePassDatabase,
    is CategoryFilter.KeePassGroupFilter,
    is CategoryFilter.BitwardenVault,
    is CategoryFilter.BitwardenFolderFilter -> true

    else -> supportsQuickFolders()
}

private data class PasswordListEmptyStateMessage(
    val titleRes: Int,
    val subtitleRes: Int? = null
)

@Composable
private fun PasswordListEmptyState(message: PasswordListEmptyStateMessage) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(message.titleRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        message.subtitleRes?.let { subtitleRes ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
            )
        }
    }
}

private fun resolvePasswordListEmptyStateMessage(
    currentFilter: CategoryFilter,
    quickFoldersEnabledForCurrentFilter: Boolean,
    hasQuickFolderShortcuts: Boolean
): PasswordListEmptyStateMessage {
    val isQuickFolderRootDatabaseView = quickFoldersEnabledForCurrentFilter && when (currentFilter) {
        is CategoryFilter.Local,
        is CategoryFilter.KeePassDatabase,
        is CategoryFilter.BitwardenVault -> true
        else -> false
    }

    return if (isQuickFolderRootDatabaseView) {
        PasswordListEmptyStateMessage(
            titleRes = R.string.password_list_quick_folder_root_empty,
            subtitleRes = if (hasQuickFolderShortcuts) {
                R.string.password_list_quick_folder_root_empty_hint
            } else {
                null
            }
        )
    } else {
        PasswordListEmptyStateMessage(titleRes = R.string.no_passwords_saved)
    }
}

private fun applyQuickFolderRootVisibility(
    entries: List<takagi.ru.monica.data.PasswordEntry>,
    currentFilter: CategoryFilter
): List<takagi.ru.monica.data.PasswordEntry> = when (currentFilter) {
    is CategoryFilter.Local -> {
        entries.filter { entry ->
            entry.keepassDatabaseId == null &&
                entry.bitwardenVaultId == null &&
                entry.categoryId == null
        }
    }

    is CategoryFilter.KeePassDatabase -> {
        entries.filter { entry ->
            entry.keepassDatabaseId == currentFilter.databaseId &&
                entry.keepassGroupPath?.trim().isNullOrBlank()
        }
    }

    is CategoryFilter.BitwardenVault -> {
        entries.filter { entry ->
            entry.bitwardenVaultId == currentFilter.vaultId &&
                entry.bitwardenFolderId?.trim().isNullOrBlank()
        }
    }

    else -> entries
}

private fun CategoryFilter.toQuickFolderRootKeyOrNull(): String? = when (this) {
    is CategoryFilter.All -> QUICK_FOLDER_ROOT_ALL
    is CategoryFilter.Archived -> null
    is CategoryFilter.Custom -> QUICK_FOLDER_ROOT_LOCAL
    is CategoryFilter.Local -> QUICK_FOLDER_ROOT_LOCAL
    is CategoryFilter.Starred -> QUICK_FOLDER_ROOT_STARRED
    is CategoryFilter.Uncategorized -> QUICK_FOLDER_ROOT_UNCATEGORIZED
    is CategoryFilter.LocalStarred -> QUICK_FOLDER_ROOT_LOCAL_STARRED
    is CategoryFilter.LocalUncategorized -> QUICK_FOLDER_ROOT_LOCAL_UNCATEGORIZED
    is CategoryFilter.KeePassDatabase,
    is CategoryFilter.KeePassGroupFilter,
    is CategoryFilter.BitwardenVault,
    is CategoryFilter.BitwardenFolderFilter -> QUICK_FOLDER_ROOT_ALL
    else -> null
}

private fun String.toQuickFolderRootFilter(): CategoryFilter = when (this) {
    QUICK_FOLDER_ROOT_LOCAL -> CategoryFilter.Local
    QUICK_FOLDER_ROOT_STARRED -> CategoryFilter.Starred
    QUICK_FOLDER_ROOT_UNCATEGORIZED -> CategoryFilter.Uncategorized
    QUICK_FOLDER_ROOT_LOCAL_STARRED -> CategoryFilter.LocalStarred
    QUICK_FOLDER_ROOT_LOCAL_UNCATEGORIZED -> CategoryFilter.LocalUncategorized
    else -> CategoryFilter.All
}




