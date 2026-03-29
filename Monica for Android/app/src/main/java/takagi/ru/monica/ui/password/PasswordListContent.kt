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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.BottomNavContentTab
import takagi.ru.monica.data.CategorySelectionUiMode
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordListTopModule
import takagi.ru.monica.data.PasswordPageContentType
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.OperationLogItemType
import takagi.ru.monica.data.model.PasskeyBindingCodec
import takagi.ru.monica.data.model.TimelinePasswordLocationState
import takagi.ru.monica.data.model.TimelineEvent
import takagi.ru.monica.notes.domain.NoteContentCodec
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
import takagi.ru.monica.ui.password.PasswordAggregateCardStyle
import takagi.ru.monica.ui.password.PasswordAggregateListItemUi
import takagi.ru.monica.ui.password.PasswordListCardBadge
import takagi.ru.monica.ui.password.PasswordGroupListItemUi
import takagi.ru.monica.ui.password.PasswordListAggregateConfig
import takagi.ru.monica.ui.password.PasswordPageListItemUi
import takagi.ru.monica.ui.password.PasswordListSingleCardItem
import takagi.ru.monica.ui.password.PasswordSupplementaryListItemUi
import takagi.ru.monica.ui.password.appendAggregateContentQuickFilterItems
import takagi.ru.monica.ui.password.buildPasswordAggregateItems
import takagi.ru.monica.ui.password.buildPasswordPageListItems
import takagi.ru.monica.ui.password.filterPasswordAggregateItemsByQuickFilters
import takagi.ru.monica.ui.password.icon
import takagi.ru.monica.ui.password.labelRes
import takagi.ru.monica.ui.password.resolvePasswordPageDisplayedTypes
import takagi.ru.monica.ui.password.resolvePasswordPageQuickFilterTypes
import takagi.ru.monica.ui.password.toPasswordPageContentTypeOrNull
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
import takagi.ru.monica.ui.password.passwordIdFromSelectionKey
import takagi.ru.monica.ui.password.passwordSelectionKey
import takagi.ru.monica.ui.password.selectedPasswordIds
import takagi.ru.monica.ui.password.selectionKeysForPasswords
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

private fun PasswordEntry.hasBoundAuthenticator(): Boolean = authenticatorKey.isNotBlank()

private fun PasswordEntry.hasBoundPasskey(): Boolean =
    PasskeyBindingCodec.decodeList(passkeyBindings).isNotEmpty()

private fun PasswordEntry.matchesLinkedAggregateContentTypes(
    selectedTypes: Set<PasswordPageContentType>
): Boolean {
    val includeAuthenticator =
        PasswordPageContentType.AUTHENTICATOR in selectedTypes && hasBoundAuthenticator()
    val includePasskey =
        PasswordPageContentType.PASSKEY in selectedTypes && hasBoundPasskey()
    return includeAuthenticator || includePasskey
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
            keepassEntryUuid = null,
            keepassGroupUuid = null,
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
                    keepassEntryUuid = null,
                    keepassGroupUuid = null,
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
                    keepassEntryUuid = null,
                    keepassGroupUuid = null,
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
            keepassEntryUuid = null,
            keepassGroupUuid = null,
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
            keepassEntryUuid = null,
            keepassGroupUuid = null,
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
            keepassEntryUuid = null,
            keepassGroupUuid = null,
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
            keepassEntryUuid = null,
            keepassGroupUuid = null,
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
        onFavorite: (() -> Unit)?,
        onMoveToCategory: (() -> Unit)?,
        onStack: (() -> Unit)?,
        onDelete: () -> Unit
    ) -> Unit,
    onBackToTopVisibilityChange: (Boolean) -> Unit = {},
    scrollToTopRequestKey: Int = 0,
    onOpenHistory: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
    onOpenCommonAccountTemplates: () -> Unit = {},
    aggregateConfig: PasswordListAggregateConfig? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val passwordEntries by viewModel.passwordEntries.collectAsState()
    val allPasswords by viewModel.allPasswordsForUi.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val currentFilter by viewModel.categoryFilter.collectAsState()
    // settings
    val appSettings by settingsViewModel.settings.collectAsState()
    val aggregateUiState = rememberPasswordAggregateUiState(
        aggregateConfig = aggregateConfig,
        searchQuery = searchQuery,
        currentFilter = currentFilter,
        appSettings = appSettings
    )
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
    var selectedItemKeys by remember { mutableStateOf(setOf<String>()) }
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
        selectedItemKeys = emptySet()
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
    val configuredQuickFilterItems = remember(
        appSettings.passwordListQuickFilterItems,
        appSettings.passwordPageAggregateEnabled,
        aggregateUiState.visibleContentTypes
    ) {
        appendAggregateContentQuickFilterItems(
            configuredItems = appSettings.passwordListQuickFilterItems,
            visibleTypes = aggregateUiState.visibleContentTypes,
            aggregateEnabled = appSettings.passwordPageAggregateEnabled
        )
    }
    val quickFolderStyle = appSettings.passwordListQuickFolderStyle
    var quickFolderRootKey by rememberSaveable {
        mutableStateOf(currentFilter.toQuickFolderRootKeyOrNull() ?: QUICK_FOLDER_ROOT_ALL)
    }
    val outsideTapInteractionSource = remember { MutableInteractionSource() }
    val canCollapseExpandedGroups = effectiveStackCardMode == StackCardMode.AUTO && expandedGroups.isNotEmpty()

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
    val groupingConfig = remember(
        isLocalOnlyView,
        effectiveStackCardMode,
        effectiveGroupMode,
        appSettings.passwordWebsiteStackMatchMode,
        effectiveNoStackEntryIds,
        effectiveManualStackGroupByEntryId,
        context
    ) {
        PasswordGroupingConfig(
            isLocalOnlyView = isLocalOnlyView,
            effectiveStackCardMode = effectiveStackCardMode,
            effectiveGroupMode = effectiveGroupMode,
            websiteStackMatchMode = appSettings.passwordWebsiteStackMatchMode,
            effectiveNoStackEntryIds = effectiveNoStackEntryIds,
            effectiveManualStackGroupByEntryId = effectiveManualStackGroupByEntryId,
            untitledLabel = context.getString(R.string.untitled)
        )
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
        effectiveNoStackEntryIds,
        aggregateUiState.hasActiveContentTypeFilter,
        aggregateUiState.displayedContentTypes
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
            filtered = filtered.filter { it.hasBoundAuthenticator() }
        }
        if (quickFilterNotes && takagi.ru.monica.data.PasswordListQuickFilterItem.NOTES in configuredQuickFilterItems) {
            filtered = filtered.filter { it.notes.isNotBlank() }
        }
        if (quickFilterUncategorized && takagi.ru.monica.data.PasswordListQuickFilterItem.UNCATEGORIZED in configuredQuickFilterItems) {
            filtered = filtered.filter { it.categoryId == null }
        }
        if (quickFilterLocalOnly && takagi.ru.monica.data.PasswordListQuickFilterItem.LOCAL_ONLY in configuredQuickFilterItems) {
            filtered = filtered.filter {
                it.isLocalOnlyEntry()
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
            val singleCardEntryIds = buildGroupedPasswordsForEntries(
                sourceEntries = filtered,
                config = groupingConfig
            )
                .values
                .asSequence()
                .filter { group -> group.size == 1 }
                .flatten()
                .map { it.id }
                .toSet()
            filtered = filtered.filter { it.id in singleCardEntryIds }
        }
        if (aggregateUiState.hasActiveContentTypeFilter) {
            filtered = filtered.filter { entry ->
                entry.matchesLinkedAggregateContentTypes(aggregateUiState.displayedContentTypes)
            }
        }
        filtered
    }

    val visibleAggregateItems = remember(
        aggregateUiState.visibleItems,
        configuredQuickFilterItems,
        quickFilterFavorite,
        quickFilter2fa,
        quickFilterNotes,
        quickFilterUncategorized,
        quickFilterLocalOnly,
        quickFilterManualStackOnly,
        quickFilterNeverStack,
        quickFilterUnstacked,
        effectiveStackCardMode
    ) {
        filterPasswordAggregateItemsByQuickFilters(
            items = aggregateUiState.visibleItems,
            configuredQuickFilterItems = configuredQuickFilterItems,
            quickFilterFavorite = quickFilterFavorite,
            quickFilter2fa = quickFilter2fa,
            quickFilterNotes = quickFilterNotes,
            quickFilterUncategorized = quickFilterUncategorized,
            quickFilterLocalOnly = quickFilterLocalOnly,
            quickFilterManualStackOnly = quickFilterManualStackOnly,
            quickFilterNeverStack = quickFilterNeverStack,
            quickFilterUnstacked = quickFilterUnstacked,
            effectiveStackCardMode = effectiveStackCardMode
        )
    }
    val visibleSelectableKeys = remember(visiblePasswordEntries, visibleAggregateItems) {
        val visibleSupplementaryKeys = visibleAggregateItems
            .mapTo(linkedSetOf<String>()) { item -> item.key }
        selectionKeysForPasswords(visiblePasswordEntries.map { it.id }) + visibleSupplementaryKeys
    }
    val selectedPasswords = remember(selectedItemKeys) {
        selectedPasswordIds(selectedItemKeys)
    }
    val selectedSupplementaryItems = remember(selectedItemKeys, visibleAggregateItems) {
        visibleAggregateItems.filter { it.key in selectedItemKeys }
    }
    val hasSelectedSupplementaryItems = remember(selectedItemKeys, selectedPasswords) {
        selectedItemKeys.size != selectedPasswords.size
    }

    LaunchedEffect(visibleSelectableKeys) {
        if (selectedItemKeys.isEmpty()) return@LaunchedEffect
        selectedItemKeys = selectedItemKeys.intersect(visibleSelectableKeys)
    }

    LaunchedEffect(isSelectionMode, selectedItemKeys) {
        if (isSelectionMode && selectedItemKeys.isEmpty()) {
            isSelectionMode = false
        }
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
            buildGroupedPasswordsForEntries(
                sourceEntries = sourceEntries,
                config = groupingConfig
            )
        }
    }

    val shouldRenderPasswordGroups = remember(aggregateUiState.displayedContentTypes) {
        PasswordPageContentType.PASSWORD in aggregateUiState.displayedContentTypes ||
            PasswordPageContentType.AUTHENTICATOR in aggregateUiState.displayedContentTypes ||
            PasswordPageContentType.PASSKEY in aggregateUiState.displayedContentTypes
    }
    val visiblePasswordIds = remember(visiblePasswordEntries) {
        visiblePasswordEntries.map(PasswordEntry::id)
    }
    val groupedPasswordIds = remember(groupedPasswords) {
        groupedPasswords.values.flatten().map(PasswordEntry::id)
    }
    val isPasswordPageListModelReady = remember(
        shouldRenderPasswordGroups,
        visiblePasswordIds,
        groupedPasswordIds
    ) {
        !shouldRenderPasswordGroups ||
            visiblePasswordIds.isEmpty() ||
            (
                groupedPasswordIds.size == visiblePasswordIds.size &&
                    groupedPasswordIds.toSet() == visiblePasswordIds.toSet()
                )
    }
    var hasCompletedInitialPasswordListStabilization by rememberSaveable {
        mutableStateOf(false)
    }
    LaunchedEffect(isPasswordPageListModelReady) {
        if (isPasswordPageListModelReady) {
            hasCompletedInitialPasswordListStabilization = true
        }
    }
    val shouldGateInitialPasswordFirstFrame = remember(
        hasCompletedInitialPasswordListStabilization,
        isPasswordPageListModelReady,
        aggregateUiState.displayedContentTypes,
        searchQuery
    ) {
        !hasCompletedInitialPasswordListStabilization &&
            !isPasswordPageListModelReady &&
            PasswordPageContentType.PASSWORD in aggregateUiState.displayedContentTypes &&
            searchQuery.isEmpty()
    }
    val effectiveVisibleAggregateItems = remember(
        shouldGateInitialPasswordFirstFrame,
        visibleAggregateItems
    ) {
        if (shouldGateInitialPasswordFirstFrame) emptyList() else visibleAggregateItems
    }
    val effectiveQuickFolderShortcuts = remember(
        shouldGateInitialPasswordFirstFrame,
        quickFolderShortcuts
    ) {
        if (shouldGateInitialPasswordFirstFrame) emptyList() else quickFolderShortcuts
    }
    val effectiveQuickFolderBreadcrumbs = remember(
        shouldGateInitialPasswordFirstFrame,
        quickFolderBreadcrumbs
    ) {
        if (shouldGateInitialPasswordFirstFrame) emptyList() else quickFolderBreadcrumbs
    }
    val passwordPageListItems = remember(
        aggregateUiState.displayedContentTypes,
        groupedPasswords,
        effectiveVisibleAggregateItems
    ) {
        buildPasswordPageListItems(
            selectedContentTypes = aggregateUiState.displayedContentTypes,
            groupedPasswords = groupedPasswords,
            supplementaryItems = effectiveVisibleAggregateItems
        )
    }
    val passwordPageListItemKeys = remember(passwordPageListItems) {
        passwordPageListItems.map { item -> item.key }
    }
    val passwordPageListItemKeySet = remember(passwordPageListItemKeys) {
        passwordPageListItemKeys.toSet()
    }
    val hasVisibleQuickFilters = remember(
        appSettings.passwordListQuickFiltersEnabled,
        configuredQuickFilterItems,
        aggregateUiState.visibleContentTypes,
        shouldGateInitialPasswordFirstFrame
    ) {
        !shouldGateInitialPasswordFirstFrame &&
            appSettings.passwordListQuickFiltersEnabled &&
            configuredQuickFilterItems.any { item ->
                shouldShowQuickFilterItem(item, aggregateUiState.visibleContentTypes)
            }
    }
    val showPinnedQuickFolderPathBanner = effectiveQuickFolderBreadcrumbs.isNotEmpty()
    val hasScrollableHeaderContent = remember(
        hasVisibleQuickFilters,
        effectiveQuickFolderShortcuts,
        showPinnedQuickFolderPathBanner
    ) {
        hasVisibleQuickFilters ||
            effectiveQuickFolderShortcuts.isNotEmpty() ||
            showPinnedQuickFolderPathBanner
    }
    val hasVisibleListItems = passwordPageListItems.isNotEmpty()
    val usesLazyColumn = remember(
        isPasswordPageListModelReady,
        hasVisibleListItems,
        hasScrollableHeaderContent,
        searchQuery,
        visiblePasswordEntries,
        effectiveVisibleAggregateItems
    ) {
        if (!isPasswordPageListModelReady) {
            visiblePasswordEntries.isNotEmpty() ||
                effectiveVisibleAggregateItems.isNotEmpty() ||
                hasScrollableHeaderContent ||
                searchQuery.isNotEmpty()
        } else {
            hasVisibleListItems || hasScrollableHeaderContent || searchQuery.isNotEmpty()
        }
    }
    val showEmptyStateWithHeaders = remember(
        isPasswordPageListModelReady,
        usesLazyColumn,
        hasVisibleListItems,
        searchQuery
    ) {
        isPasswordPageListModelReady && usesLazyColumn && !hasVisibleListItems && searchQuery.isEmpty()
    }
    val listState = rememberPasswordListLazyListState(
        viewModel = viewModel,
        currentListItemKeys = passwordPageListItemKeys,
        scrollToTopRequestKey = scrollToTopRequestKey,
        fastScrollRequestKey = fastScrollRequestKey,
        fastScrollProgress = fastScrollProgress,
        allowScrollPositionPersistence = isPasswordPageListModelReady && hasVisibleListItems,
        onBackToTopVisibilityChange = onBackToTopVisibilityChange
    )

    val selectionHandlers = rememberPasswordListSelectionHandlers(
        context = context,
        coroutineScope = coroutineScope,
        viewModel = viewModel,
        selectedItemKeys = selectedItemKeys,
        visibleSelectableKeys = visibleSelectableKeys,
        selectedPasswords = selectedPasswords,
        passwordEntries = passwordEntries,
        selectedSupplementaryItems = selectedSupplementaryItems,
        aggregateUiState = aggregateUiState,
        onClearSelection = {
            isSelectionMode = false
            selectedItemKeys = emptySet()
        },
        onSelectedItemKeysChange = { selectedItemKeys = it },
        onShowMoveToCategoryDialog = { showMoveToCategoryDialog = true },
        onShowManualStackConfirmDialog = {
            selectedManualStackMode = ManualStackDialogMode.STACK
            showManualStackConfirmDialog = true
        },
        onShowBatchDeleteDialog = { showBatchDeleteDialog = true }
    )

    BindPasswordListSelectionModeChange(
        isSelectionMode = isSelectionMode,
        selectedItemKeys = selectedItemKeys,
        selectedPasswords = selectedPasswords,
        selectedSupplementaryItems = selectedSupplementaryItems,
        handlers = selectionHandlers,
        onSelectionModeChange = onSelectionModeChange
    )

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
            selectedItemKeys = emptySet()
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
        aggregateSelectedTypes = aggregateUiState.selectedContentTypes,
        aggregateVisibleTypes = aggregateUiState.visibleContentTypes,
        onToggleAggregateType = { type -> aggregateConfig?.onToggleContentType?.invoke(type) },
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

                if (showPinnedQuickFolderPathBanner) {
                    PasswordQuickFolderBreadcrumbBanner(
                        breadcrumbs = effectiveQuickFolderBreadcrumbs,
                        currentFilter = currentFilter,
                        onNavigate = { target -> viewModel.setCategoryFilter(target) }
                    )
                }

                if (isPasswordPageListModelReady && !hasVisibleListItems && searchQuery.isEmpty() && !hasScrollableHeaderContent) {
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
                        PasswordListEmptyState(
                            message = if (aggregateUiState.hasActiveContentTypeFilter) {
                                PasswordListEmptyStateMessage(titleRes = R.string.no_results)
                            } else {
                                emptyStateMessage
                            }
                        )
                    }
                } else {
                    PasswordListScrollableContent(
                        listState = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .offset { androidx.compose.ui.unit.IntOffset(0, contentPullOffset) }
                            .nestedScroll(pullAction.nestedScrollConnection),
                        isPasswordPageListModelReady = isPasswordPageListModelReady,
                        hasVisibleQuickFilters = hasVisibleQuickFilters,
                        appSettings = appSettings,
                        configuredQuickFilterItems = configuredQuickFilterItems,
                        aggregateUiState = aggregateUiState,
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
                        onToggleAggregateType = aggregateConfig?.onToggleContentType,
                        quickFolderShortcuts = effectiveQuickFolderShortcuts,
                        quickFolderStyle = quickFolderStyle,
                        currentFilter = currentFilter,
                        onNavigateFilter = { target -> viewModel.setCategoryFilter(target) },
                        hasVisibleListItems = hasVisibleListItems,
                        searchQuery = searchQuery,
                        emptyStateMessage = emptyStateMessage,
                        renderPasswordRows = {
                        passwordPageListRows(
                            passwordPageListItems = passwordPageListItems,
                            effectiveStackCardMode = effectiveStackCardMode,
                            expandedGroups = expandedGroups,
                            itemToDelete = itemToDelete,
                            onItemToDeleteChange = { itemToDelete = it },
                            isSelectionMode = isSelectionMode,
                            onSelectionModeChange = { isSelectionMode = it },
                            selectedItemKeys = selectedItemKeys,
                            onSelectedItemKeysChange = { selectedItemKeys = it },
                            selectedPasswords = selectedPasswords,
                            showBatchDeleteDialog = showBatchDeleteDialog,
                            onShowBatchDeleteDialogChange = { showBatchDeleteDialog = it },
                            viewModel = viewModel,
                            haptic = haptic,
                            onPasswordClick = { password ->
                                val topVisibleKey = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { item -> item.key.toString() in passwordPageListItemKeySet }
                                    ?.key
                                    ?.toString()
                                viewModel.updatePasswordListScrollPosition(
                                    listState.firstVisibleItemIndex,
                                    listState.firstVisibleItemScrollOffset,
                                    topVisibleKey
                                )
                                onPasswordClick(password)
                            },
                            appSettings = appSettings,
                            coroutineScope = coroutineScope,
                            context = context,
                            passwordEntries = passwordEntries,
                            aggregateConfig = aggregateConfig,
                            aggregateUiState = aggregateUiState
                        )
                        }
                    )
                }
            }
        }
    
    PasswordListDialogs(
        showManualStackConfirmDialog = showManualStackConfirmDialog,
        onShowManualStackConfirmDialogChange = { showManualStackConfirmDialog = it },
        selectedPasswords = selectedPasswords,
        selectedCount = selectedItemKeys.size,
        selectedManualStackMode = selectedManualStackMode,
        onSelectedManualStackModeChange = { selectedManualStackMode = it },
        viewModel = viewModel,
        context = context,
        coroutineScope = coroutineScope,
        onDeleteSelection = {
            val selectedPasswordEntries = passwordEntries.filter { it.id in selectedPasswords }
            selectedPasswordEntries.forEach(viewModel::deletePasswordEntry)

            selectedSupplementaryItems.forEach { item ->
                when (item.type) {
                    PasswordPageContentType.AUTHENTICATOR -> {
                        aggregateUiState.totpItems
                            .firstOrNull { it.id == item.secureItemId }
                            ?.let { aggregateUiState.totpViewModel?.deleteTotpItem(it) }
                    }

                    PasswordPageContentType.CARD_WALLET -> {
                        item.secureItemId?.let { id ->
                            if (item.isDocument) {
                                aggregateUiState.documentViewModel?.deleteDocument(id)
                            } else {
                                aggregateUiState.bankCardViewModel?.deleteCard(id)
                            }
                        }
                    }

                    PasswordPageContentType.NOTE -> {
                        aggregateUiState.notes
                            .firstOrNull { it.id == item.secureItemId }
                            ?.let { aggregateUiState.noteViewModel?.deleteNote(it) }
                    }

                    PasswordPageContentType.PASSKEY -> {
                        item.passkeyCredentialId?.let { credentialId ->
                            aggregateUiState.passkeyViewModel?.deletePasskeyById(credentialId)
                        }
                    }

                    PasswordPageContentType.PASSWORD -> Unit
                }
            }

            selectedItemKeys.size
        },
        onSelectionCleared = {
            isSelectionMode = false
            selectedItemKeys = emptySet()
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
        allowMove = passwordEntries.filter { it.id in selectedPasswords }.none { it.isKeePassEntry() },
        allowArchiveTarget = true,
        onTargetSelected = { target, action ->
            val selectedIds = selectedPasswords.toList()
            val selectedEntries = passwordEntries.filter { it.id in selectedPasswords }
            val actionResolution = resolvePasswordBatchMoveAction(
                requestedAction = action,
                selectedEntries = selectedEntries
            )
            if (actionResolution.showKeepassCopyOnlyHint) {
                Toast.makeText(
                    context,
                    context.getString(R.string.keepass_copy_only_hint),
                    Toast.LENGTH_SHORT
                ).show()
            }
            val effectiveAction = actionResolution.effectiveAction
            val targetRouting = resolvePasswordBatchMoveTargetRouting(target)
            if (effectiveAction == UnifiedMoveAction.COPY) {
                executePasswordBatchCopy(
                    context = context,
                    coroutineScope = coroutineScope,
                    selectedEntries = selectedEntries,
                    target = target,
                    targetRouting = targetRouting,
                    copyPasswordToMonicaLocal = { entry, categoryId ->
                        viewModel.copyPasswordToMonicaLocal(
                            entry = entry,
                            categoryId = categoryId
                        )
                    },
                    addCopiedEntry = { entry, onResult ->
                        viewModel.addPasswordEntryWithResult(
                            entry = entry,
                            includeDetailedLog = false,
                            onResult = onResult
                        )
                    },
                    buildCopiedEntryForTarget = ::buildCopiedEntryForTarget
                )
            } else {
                val oldStates = selectedEntries.map(::toLocationState)
                val newStates = selectedEntries.map { toMovedLocationState(it, target) }
                when {
                    targetRouting.isArchiveTarget -> {
                        viewModel.archivePasswords(selectedIds)
                        Toast.makeText(
                            context,
                            context.getString(R.string.archive_page_title),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    target == UnifiedMoveCategoryTarget.Uncategorized -> {
                        coroutineScope.launch {
                            try {
                                val keepassEntries = selectedEntries.filter { it.isKeePassEntry() }
                                val bitwardenEntries = selectedEntries.filter { it.isBitwardenEntry() }
                                val localIds = selectedEntries
                                    .filter { it.isLocalOnlyEntry() }
                                    .map { it.id }

                                if (keepassEntries.isNotEmpty()) {
                                    val result = localKeePassViewModel.movePasswordEntriesToMonicaLocal(keepassEntries)
                                    if (result.isFailure) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.webdav_operation_failed, result.exceptionOrNull()?.message ?: ""),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }
                                    val keepassIds = keepassEntries.map { it.id }
                                    viewModel.unarchivePasswords(keepassIds)
                                    viewModel.movePasswordsToCategory(keepassIds, null)
                                }

                                bitwardenEntries.forEach { entry ->
                                    val result = viewModel.moveBitwardenPasswordToMonicaLocal(entry, null)
                                    if (result.isFailure) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.webdav_operation_failed, result.exceptionOrNull()?.message ?: ""),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }
                                }

                                if (localIds.isNotEmpty()) {
                                    viewModel.unarchivePasswords(localIds)
                                    viewModel.movePasswordsToCategory(localIds, null)
                                }

                                Toast.makeText(
                                    context,
                                    context.getString(R.string.category_none),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.webdav_operation_failed, e.message ?: ""),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    target is UnifiedMoveCategoryTarget.MonicaCategory -> {
                        coroutineScope.launch {
                            try {
                                val keepassEntries = selectedEntries.filter { it.isKeePassEntry() }
                                val bitwardenEntries = selectedEntries.filter { it.isBitwardenEntry() }
                                val localIds = selectedEntries
                                    .filter { it.isLocalOnlyEntry() }
                                    .map { it.id }

                                if (keepassEntries.isNotEmpty()) {
                                    val result = localKeePassViewModel.movePasswordEntriesToMonicaLocal(keepassEntries)
                                    if (result.isFailure) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.webdav_operation_failed, result.exceptionOrNull()?.message ?: ""),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }
                                    val keepassIds = keepassEntries.map { it.id }
                                    viewModel.unarchivePasswords(keepassIds)
                                    viewModel.movePasswordsToCategory(keepassIds, target.categoryId)
                                }

                                bitwardenEntries.forEach { entry ->
                                    val result = viewModel.moveBitwardenPasswordToMonicaLocal(
                                        entry = entry,
                                        categoryId = target.categoryId
                                    )
                                    if (result.isFailure) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.webdav_operation_failed, result.exceptionOrNull()?.message ?: ""),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }
                                }

                                if (localIds.isNotEmpty()) {
                                    viewModel.unarchivePasswords(localIds)
                                    viewModel.movePasswordsToCategory(localIds, target.categoryId)
                                }

                                val name = categories.find { it.id == target.categoryId }?.name ?: ""
                                Toast.makeText(
                                    context,
                                    "${context.getString(R.string.move_to_category)} $name",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.webdav_operation_failed, e.message ?: ""),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
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
                val targetLabel = buildMoveTargetLabel(
                    context = context,
                    target = target,
                    categories = categories,
                    keepassDatabases = keepassDatabases
                )
                logPasswordBatchMoveTimeline(
                    context = context,
                    selectedEntries = selectedEntries,
                    oldStates = oldStates,
                    newStates = newStates,
                    targetLabel = targetLabel
                )
            }
            onDismiss()
            onSelectionCleared()
        }
    )
}

private const val MONICA_MANUAL_STACK_GROUP_FIELD_TITLE = "__monica_manual_stack_group"
private const val MONICA_NO_STACK_FIELD_TITLE = "__monica_no_stack"

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
