package takagi.ru.monica.ui.vaultv2

import android.icu.text.Transliterator
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import takagi.ru.monica.R
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.bitwarden.sync.isUserVisibleSyncInProgress
import takagi.ru.monica.bitwarden.ui.UnlockVaultDialog
import takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.CategorySelectionUiMode
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.PasswordListQuickFilterItem
import takagi.ru.monica.data.PasswordPageContentType
import takagi.ru.monica.data.isLocalOnlyItem
import takagi.ru.monica.data.isLocalOnlyPasskey
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.data.model.StorageTarget
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.UnmatchedIconHandlingStrategy
import takagi.ru.monica.notes.domain.NoteContentCodec
import takagi.ru.monica.ui.PasswordListCategoryChipMenu
import takagi.ru.monica.ui.buildCategoryMenuQuickFilterBindings
import takagi.ru.monica.ui.components.CreateCategoryDialog
import takagi.ru.monica.ui.components.UnifiedCategoryFilterBottomSheet
import takagi.ru.monica.ui.components.UnifiedCategoryFilterChipMenuDropdown
import takagi.ru.monica.ui.components.UnifiedCategoryFilterChipMenuOffset
import takagi.ru.monica.ui.components.UnifiedCategoryFilterSelection
import takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_NONE
import takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_SIMPLE
import takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_UPLOADED
import takagi.ru.monica.ui.icons.UnmatchedIconFallback
import takagi.ru.monica.ui.common.pull.rememberPullActionState
import takagi.ru.monica.ui.icons.rememberAutoMatchedSimpleIcon
import takagi.ru.monica.ui.icons.rememberSimpleIconBitmap
import takagi.ru.monica.ui.icons.rememberUploadedPasswordIcon
import takagi.ru.monica.ui.icons.shouldShowFallbackSlot
import takagi.ru.monica.ui.PasswordQuickFolderBreadcrumb
import takagi.ru.monica.ui.PasswordDisplayOptionsSheet
import takagi.ru.monica.ui.PasswordListInitialLoadingIndicator
import takagi.ru.monica.ui.buildPasswordQuickFolderNodes
import takagi.ru.monica.ui.buildCategoryMenuFolderShortcuts
import takagi.ru.monica.ui.buildLocalQuickFolderPasswordCountByCategoryId
import takagi.ru.monica.ui.buildQuickFolderBreadcrumbs
import takagi.ru.monica.ui.PasswordQuickFilterChipCallbacks
import takagi.ru.monica.ui.PasswordQuickFilterChipState
import takagi.ru.monica.ui.supportsQuickFolders
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.common.selection.SelectionActionBar
import takagi.ru.monica.ui.password.PasswordAuthenticatorDisplayState
import takagi.ru.monica.ui.password.BitwardenClearCacheTopActionsMenuItem
import takagi.ru.monica.ui.password.BitwardenLockTopActionsMenuItem
import takagi.ru.monica.ui.password.BitwardenReunlockTopActionsMenuItem
import takagi.ru.monica.ui.password.BitwardenSyncTopActionsMenuItem
import takagi.ru.monica.ui.password.CommonPasswordTopActionsMenuItems
import takagi.ru.monica.ui.password.KeepassRefreshTopActionsMenuItem
import takagi.ru.monica.ui.password.PasswordTopActionsDropdownMenu
import takagi.ru.monica.ui.password.StackCardMode
import takagi.ru.monica.ui.password.appendAggregateContentQuickFilterItems
import takagi.ru.monica.ui.password.resolvePasswordPageDisplayedTypes
import takagi.ru.monica.ui.password.resolvePasswordPageVisibleTypes
import takagi.ru.monica.ui.password.sanitizeSelectedPasswordPageTypes
import takagi.ru.monica.ui.password.rememberPasswordAuthenticatorDisplayState
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.CategoryFilter
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.viewmodel.LocalKeePassViewModel
import takagi.ru.monica.viewmodel.NoteViewModel
import takagi.ru.monica.viewmodel.PasskeyViewModel
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel
import takagi.ru.monica.viewmodel.TotpViewModel
import takagi.ru.monica.util.TotpGenerator
import takagi.ru.monica.utils.KEEPASS_DISPLAY_PATH_SEPARATOR
import takagi.ru.monica.utils.decodeKeePassPathSegments
import takagi.ru.monica.utils.planLocalCategoryMove
import java.util.concurrent.CancellationException
import java.util.Locale
import kotlin.math.roundToInt

private enum class VaultV2ItemType {
	PASSWORD,
	AUTHENTICATOR,
	NOTE,
	PASSKEY,
	BANK_CARD,
	DOCUMENT,
}

private data class VaultV2Item(
	val key: String,
	val type: VaultV2ItemType,
	val title: String,
	val subtitle: String,
	val isFavorite: Boolean,
	val sortKey: String,
	val searchText: String,
	val passwordEntry: PasswordEntry? = null,
	val totpItem: SecureItem? = null,
	val secureItem: SecureItem? = null,
	val passkeyEntry: PasskeyEntry? = null,
	val boundPasswordId: Long? = null,
)

private data class VaultV2SectionLayout(
	val title: String,
	val items: List<VaultV2Item>,
	val itemStartIndex: Int,
	val firstItemLazyIndex: Int,
)

private data class VaultV2ComputedListState(
	val allItemsRaw: List<VaultV2Item> = emptyList(),
	val passwordById: Map<Long, PasswordEntry> = emptyMap(),
)

private data class VaultV2AsyncComputedValue<T>(
	val value: T,
	val isComputing: Boolean,
)

private const val VAULT_V2_FAST_SCROLL_LOG_TAG = "VaultV2FastScroll"
private const val VAULT_V2_EMPTY_STATE_DEBOUNCE_MS = 220L
private const val MONICA_MANUAL_STACK_GROUP_FIELD_TITLE = "__monica_manual_stack_group"
private const val MONICA_NO_STACK_FIELD_TITLE = "__monica_no_stack"
private val vaultV2Transliterator: Transliterator by lazy(LazyThreadSafetyMode.NONE) {
	Transliterator.getInstance("Any-Latin; Latin-ASCII")
}

private fun PasswordEntry.isVaultV2LocalOnly(): Boolean {
	return isLocalOnlyEntry()
}

private fun SecureItem.isVaultV2LocalOnly(): Boolean {
	return isLocalOnlyItem()
}

private fun PasskeyEntry.isVaultV2LocalOnly(): Boolean {
	return isLocalOnlyPasskey()
}

private fun PasswordPageContentType.toVaultV2ItemTypes(): Set<VaultV2ItemType> = when (this) {
	PasswordPageContentType.PASSWORD -> setOf(VaultV2ItemType.PASSWORD)
	PasswordPageContentType.AUTHENTICATOR -> setOf(VaultV2ItemType.AUTHENTICATOR)
	PasswordPageContentType.NOTE -> setOf(VaultV2ItemType.NOTE)
	PasswordPageContentType.PASSKEY -> setOf(VaultV2ItemType.PASSKEY)
	PasswordPageContentType.CARD_WALLET -> setOf(VaultV2ItemType.BANK_CARD, VaultV2ItemType.DOCUMENT)
}

private fun VaultV2Item.toPasswordPageContentType(): PasswordPageContentType = when (type) {
	VaultV2ItemType.PASSWORD -> PasswordPageContentType.PASSWORD
	VaultV2ItemType.AUTHENTICATOR -> PasswordPageContentType.AUTHENTICATOR
	VaultV2ItemType.NOTE -> PasswordPageContentType.NOTE
	VaultV2ItemType.PASSKEY -> PasswordPageContentType.PASSKEY
	VaultV2ItemType.BANK_CARD,
	VaultV2ItemType.DOCUMENT -> PasswordPageContentType.CARD_WALLET
}

private fun VaultV2PaneState.toUnifiedCategoryFilterSelection(): UnifiedCategoryFilterSelection {
	return when (storageFilterType) {
		VAULT_V2_STORAGE_FILTER_ALL -> UnifiedCategoryFilterSelection.All
		VAULT_V2_STORAGE_FILTER_LOCAL -> UnifiedCategoryFilterSelection.Local
		VAULT_V2_STORAGE_FILTER_STARRED -> UnifiedCategoryFilterSelection.Starred
		VAULT_V2_STORAGE_FILTER_UNCATEGORIZED -> UnifiedCategoryFilterSelection.Uncategorized
		VAULT_V2_STORAGE_FILTER_LOCAL_STARRED -> UnifiedCategoryFilterSelection.LocalStarred
		VAULT_V2_STORAGE_FILTER_LOCAL_UNCATEGORIZED -> UnifiedCategoryFilterSelection.LocalUncategorized
		VAULT_V2_STORAGE_FILTER_CUSTOM -> {
			storageFilterPrimaryId?.let(UnifiedCategoryFilterSelection::Custom)
				?: UnifiedCategoryFilterSelection.Local
		}
		VAULT_V2_STORAGE_FILTER_KEEPASS_DATABASE -> {
			storageFilterPrimaryId?.let(UnifiedCategoryFilterSelection::KeePassDatabaseFilter)
				?: UnifiedCategoryFilterSelection.Local
		}
		VAULT_V2_STORAGE_FILTER_KEEPASS_GROUP -> {
			val databaseId = storageFilterPrimaryId
			val groupPath = storageFilterSecondaryKey
			if (databaseId != null && !groupPath.isNullOrBlank()) {
				UnifiedCategoryFilterSelection.KeePassGroupFilter(databaseId, groupPath)
			} else if (databaseId != null) {
				UnifiedCategoryFilterSelection.KeePassDatabaseFilter(databaseId)
			} else {
				UnifiedCategoryFilterSelection.Local
			}
		}
		VAULT_V2_STORAGE_FILTER_KEEPASS_DATABASE_STARRED -> {
			storageFilterPrimaryId?.let(UnifiedCategoryFilterSelection::KeePassDatabaseStarredFilter)
				?: UnifiedCategoryFilterSelection.Local
		}
		VAULT_V2_STORAGE_FILTER_KEEPASS_DATABASE_UNCATEGORIZED -> {
			storageFilterPrimaryId?.let(UnifiedCategoryFilterSelection::KeePassDatabaseUncategorizedFilter)
				?: UnifiedCategoryFilterSelection.Local
		}
		VAULT_V2_STORAGE_FILTER_BITWARDEN_VAULT -> {
			storageFilterPrimaryId?.let(UnifiedCategoryFilterSelection::BitwardenVaultFilter)
				?: UnifiedCategoryFilterSelection.Local
		}
		VAULT_V2_STORAGE_FILTER_BITWARDEN_FOLDER -> {
			val vaultId = storageFilterPrimaryId
			val folderId = storageFilterSecondaryKey
			if (vaultId != null && !folderId.isNullOrBlank()) {
				UnifiedCategoryFilterSelection.BitwardenFolderFilter(vaultId, folderId)
			} else if (vaultId != null) {
				UnifiedCategoryFilterSelection.BitwardenVaultFilter(vaultId)
			} else {
				UnifiedCategoryFilterSelection.Local
			}
		}
		VAULT_V2_STORAGE_FILTER_BITWARDEN_VAULT_STARRED -> {
			storageFilterPrimaryId?.let(UnifiedCategoryFilterSelection::BitwardenVaultStarredFilter)
				?: UnifiedCategoryFilterSelection.Local
		}
		VAULT_V2_STORAGE_FILTER_BITWARDEN_VAULT_UNCATEGORIZED -> {
			storageFilterPrimaryId?.let(UnifiedCategoryFilterSelection::BitwardenVaultUncategorizedFilter)
				?: UnifiedCategoryFilterSelection.Local
		}
		else -> UnifiedCategoryFilterSelection.Local
	}
}

private fun VaultV2PaneState.updateStorageFilter(selection: UnifiedCategoryFilterSelection) {
	when (selection) {
		UnifiedCategoryFilterSelection.All -> updateStorageFilter(VAULT_V2_STORAGE_FILTER_ALL)
		UnifiedCategoryFilterSelection.Local -> updateStorageFilter(VAULT_V2_STORAGE_FILTER_LOCAL)
		UnifiedCategoryFilterSelection.Starred -> updateStorageFilter(VAULT_V2_STORAGE_FILTER_STARRED)
		UnifiedCategoryFilterSelection.Uncategorized -> {
			updateStorageFilter(VAULT_V2_STORAGE_FILTER_UNCATEGORIZED)
		}
		UnifiedCategoryFilterSelection.LocalStarred -> {
			updateStorageFilter(VAULT_V2_STORAGE_FILTER_LOCAL_STARRED)
		}
		UnifiedCategoryFilterSelection.LocalUncategorized -> {
			updateStorageFilter(VAULT_V2_STORAGE_FILTER_LOCAL_UNCATEGORIZED)
		}
		is UnifiedCategoryFilterSelection.Custom -> {
			updateStorageFilter(
				type = VAULT_V2_STORAGE_FILTER_CUSTOM,
				primaryId = selection.categoryId,
			)
		}
		is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> {
			updateStorageFilter(
				type = VAULT_V2_STORAGE_FILTER_KEEPASS_DATABASE,
				primaryId = selection.databaseId,
			)
		}
		is UnifiedCategoryFilterSelection.KeePassGroupFilter -> {
			updateStorageFilter(
				type = VAULT_V2_STORAGE_FILTER_KEEPASS_GROUP,
				primaryId = selection.databaseId,
				secondaryKey = selection.groupPath,
			)
		}
		is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> {
			updateStorageFilter(
				type = VAULT_V2_STORAGE_FILTER_KEEPASS_DATABASE_STARRED,
				primaryId = selection.databaseId,
			)
		}
		is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> {
			updateStorageFilter(
				type = VAULT_V2_STORAGE_FILTER_KEEPASS_DATABASE_UNCATEGORIZED,
				primaryId = selection.databaseId,
			)
		}
		is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> {
			updateStorageFilter(
				type = VAULT_V2_STORAGE_FILTER_BITWARDEN_VAULT,
				primaryId = selection.vaultId,
			)
		}
		is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> {
			updateStorageFilter(
				type = VAULT_V2_STORAGE_FILTER_BITWARDEN_FOLDER,
				primaryId = selection.vaultId,
				secondaryKey = selection.folderId,
			)
		}
		is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> {
			updateStorageFilter(
				type = VAULT_V2_STORAGE_FILTER_BITWARDEN_VAULT_STARRED,
				primaryId = selection.vaultId,
			)
		}
		is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> {
			updateStorageFilter(
				type = VAULT_V2_STORAGE_FILTER_BITWARDEN_VAULT_UNCATEGORIZED,
				primaryId = selection.vaultId,
			)
		}
	}
}

private fun UnifiedCategoryFilterSelection.toCategoryFilterOrNull(): CategoryFilter? {
	return when (this) {
		UnifiedCategoryFilterSelection.All -> CategoryFilter.All
		UnifiedCategoryFilterSelection.Local -> CategoryFilter.Local
		UnifiedCategoryFilterSelection.Starred -> CategoryFilter.Starred
		UnifiedCategoryFilterSelection.Uncategorized -> CategoryFilter.Uncategorized
		UnifiedCategoryFilterSelection.LocalStarred -> CategoryFilter.LocalStarred
		UnifiedCategoryFilterSelection.LocalUncategorized -> CategoryFilter.LocalUncategorized
		is UnifiedCategoryFilterSelection.Custom -> CategoryFilter.Custom(categoryId)
		is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> CategoryFilter.KeePassDatabase(databaseId)
		is UnifiedCategoryFilterSelection.KeePassGroupFilter -> {
			CategoryFilter.KeePassGroupFilter(databaseId, groupPath)
		}
		is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> {
			CategoryFilter.KeePassDatabaseStarred(databaseId)
		}
		is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> {
			CategoryFilter.KeePassDatabaseUncategorized(databaseId)
		}
		is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> CategoryFilter.BitwardenVault(vaultId)
		is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> {
			CategoryFilter.BitwardenFolderFilter(folderId = folderId, vaultId = vaultId)
		}
		is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> {
			CategoryFilter.BitwardenVaultStarred(vaultId)
		}
		is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> {
			CategoryFilter.BitwardenVaultUncategorized(vaultId)
		}
	}
}

private fun CategoryFilter.toUnifiedCategoryFilterSelectionOrNull(): UnifiedCategoryFilterSelection? {
	return when (this) {
		is CategoryFilter.All -> UnifiedCategoryFilterSelection.All
		is CategoryFilter.Local -> UnifiedCategoryFilterSelection.Local
		is CategoryFilter.LocalOnly -> UnifiedCategoryFilterSelection.Local
		is CategoryFilter.Starred -> UnifiedCategoryFilterSelection.Starred
		is CategoryFilter.Uncategorized -> UnifiedCategoryFilterSelection.Uncategorized
		is CategoryFilter.LocalStarred -> UnifiedCategoryFilterSelection.LocalStarred
		is CategoryFilter.LocalUncategorized -> UnifiedCategoryFilterSelection.LocalUncategorized
		is CategoryFilter.Custom -> UnifiedCategoryFilterSelection.Custom(categoryId)
		is CategoryFilter.KeePassDatabase -> UnifiedCategoryFilterSelection.KeePassDatabaseFilter(databaseId)
		is CategoryFilter.KeePassGroupFilter -> {
			UnifiedCategoryFilterSelection.KeePassGroupFilter(databaseId, groupPath)
		}
		is CategoryFilter.KeePassDatabaseStarred -> {
			UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter(databaseId)
		}
		is CategoryFilter.KeePassDatabaseUncategorized -> {
			UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter(databaseId)
		}
		is CategoryFilter.BitwardenVault -> UnifiedCategoryFilterSelection.BitwardenVaultFilter(vaultId)
		is CategoryFilter.BitwardenFolderFilter -> {
			UnifiedCategoryFilterSelection.BitwardenFolderFilter(vaultId, folderId)
		}
		is CategoryFilter.BitwardenVaultStarred -> {
			UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter(vaultId)
		}
		is CategoryFilter.BitwardenVaultUncategorized -> {
			UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter(vaultId)
		}
		is CategoryFilter.Archived -> null
	}
}

@Composable
private fun <T> rememberVaultV2AsyncComputed(
	vararg keys: Any?,
	initialValue: T,
	compute: suspend () -> T,
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

@Composable
private fun <T> rememberVaultV2AsyncComputedValue(
	vararg keys: Any?,
	initialValue: T,
	compute: suspend () -> T,
): VaultV2AsyncComputedValue<T> {
	var value by remember { mutableStateOf(initialValue) }
	var isComputing by remember { mutableStateOf(true) }
	val latestCompute by rememberUpdatedState(compute)

	LaunchedEffect(*keys) {
		isComputing = true
		value = withContext(Dispatchers.Default) {
			latestCompute()
		}
		isComputing = false
	}

	return VaultV2AsyncComputedValue(
		value = value,
		isComputing = isComputing,
	)
}

@Composable
private fun rememberVaultV2StorageFilterLabel(
	selected: UnifiedCategoryFilterSelection,
	categories: List<Category>,
	keepassDatabases: List<LocalKeePassDatabase>,
	bitwardenVaults: List<BitwardenVault>,
	bitwardenFolders: List<BitwardenFolder>,
): String {
	val monica = stringResource(R.string.filter_monica)
	val bitwarden = stringResource(R.string.filter_bitwarden)
	val keepass = stringResource(R.string.filter_keepass)
	val starred = stringResource(R.string.filter_starred)
	val uncategorized = stringResource(R.string.filter_uncategorized)
	return when (selected) {
		UnifiedCategoryFilterSelection.All -> stringResource(R.string.category_all)
		UnifiedCategoryFilterSelection.Local -> monica
		UnifiedCategoryFilterSelection.Starred -> starred
		UnifiedCategoryFilterSelection.Uncategorized -> uncategorized
		UnifiedCategoryFilterSelection.LocalStarred -> "$monica · $starred"
		UnifiedCategoryFilterSelection.LocalUncategorized -> "$monica · $uncategorized"
		is UnifiedCategoryFilterSelection.Custom -> {
			val categoryLabel = categories.find { it.id == selected.categoryId }?.name
				?: stringResource(R.string.unknown_category)
			"$monica · $categoryLabel"
		}
		is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> {
			keepassDatabases.find { it.id == selected.databaseId }?.name ?: keepass
		}
		is UnifiedCategoryFilterSelection.KeePassGroupFilter -> {
			val databaseLabel = keepassDatabases.find { it.id == selected.databaseId }?.name ?: keepass
			val groupLabel = decodeKeePassPathSegments(selected.groupPath)
				.joinToString(KEEPASS_DISPLAY_PATH_SEPARATOR)
				.ifBlank { keepass }
			"$databaseLabel · $groupLabel"
		}
		is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> {
			"${keepassDatabases.find { it.id == selected.databaseId }?.name ?: keepass} · $starred"
		}
		is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> {
			"${keepassDatabases.find { it.id == selected.databaseId }?.name ?: keepass} · $uncategorized"
		}
		is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> {
			bitwardenVaults.find { it.id == selected.vaultId }?.displayLabel() ?: bitwarden
		}
		is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> {
			val vaultLabel = bitwardenVaults.find { it.id == selected.vaultId }?.displayLabel() ?: bitwarden
			val folderLabel = bitwardenFolders.find { it.bitwardenFolderId == selected.folderId }?.name
			if (folderLabel.isNullOrBlank()) vaultLabel else "$vaultLabel · $folderLabel"
		}
		is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> {
			"${bitwardenVaults.find { it.id == selected.vaultId }?.displayLabel() ?: bitwarden} · $starred"
		}
		is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> {
			"${bitwardenVaults.find { it.id == selected.vaultId }?.displayLabel() ?: bitwarden} · $uncategorized"
		}
	}
}

private fun BitwardenVault.displayLabel(): String {
	return displayName?.takeIf { it.isNotBlank() } ?: email
}

private fun VaultV2Item.matchesStorageFilter(selection: UnifiedCategoryFilterSelection): Boolean {
	return when (selection) {
		UnifiedCategoryFilterSelection.All -> true
		UnifiedCategoryFilterSelection.Local -> isLocalOnly()
		UnifiedCategoryFilterSelection.Starred -> isFavorite
		UnifiedCategoryFilterSelection.Uncategorized -> categoryId() == null
		UnifiedCategoryFilterSelection.LocalStarred -> isLocalOnly() && isFavorite
		UnifiedCategoryFilterSelection.LocalUncategorized -> isLocalOnly() && categoryId() == null
		is UnifiedCategoryFilterSelection.Custom -> categoryId() == selection.categoryId
		is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> {
			keepassDatabaseId() == selection.databaseId
		}
		is UnifiedCategoryFilterSelection.KeePassGroupFilter -> {
			keepassDatabaseId() == selection.databaseId && keepassGroupPath() == selection.groupPath
		}
		is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> {
			keepassDatabaseId() == selection.databaseId && isFavorite
		}
		is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> {
			keepassDatabaseId() == selection.databaseId && keepassGroupPath().isNullOrBlank()
		}
		is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> {
			bitwardenVaultId() == selection.vaultId
		}
		is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> {
			bitwardenVaultId() == selection.vaultId && bitwardenFolderId() == selection.folderId
		}
		is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> {
			bitwardenVaultId() == selection.vaultId && isFavorite
		}
		is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> {
			bitwardenVaultId() == selection.vaultId && bitwardenFolderId().isNullOrBlank()
		}
	}
}

private fun VaultV2Item.isLocalOnly(): Boolean {
	return when (type) {
		VaultV2ItemType.PASSWORD -> passwordEntry?.isVaultV2LocalOnly() == true
		VaultV2ItemType.AUTHENTICATOR -> totpItem?.isVaultV2LocalOnly() == true
		VaultV2ItemType.NOTE,
		VaultV2ItemType.BANK_CARD,
		VaultV2ItemType.DOCUMENT -> secureItem?.isVaultV2LocalOnly() == true
		VaultV2ItemType.PASSKEY -> passkeyEntry?.isVaultV2LocalOnly() == true
	}
}

private fun VaultV2Item.categoryId(): Long? {
	return when (type) {
		VaultV2ItemType.PASSWORD -> passwordEntry?.categoryId
		VaultV2ItemType.AUTHENTICATOR -> totpItem?.categoryId
		VaultV2ItemType.NOTE,
		VaultV2ItemType.BANK_CARD,
		VaultV2ItemType.DOCUMENT -> secureItem?.categoryId
		VaultV2ItemType.PASSKEY -> passkeyEntry?.categoryId
	}
}

private fun VaultV2Item.keepassDatabaseId(): Long? {
	return when (type) {
		VaultV2ItemType.PASSWORD -> passwordEntry?.keepassDatabaseId
		VaultV2ItemType.AUTHENTICATOR -> totpItem?.keepassDatabaseId
		VaultV2ItemType.NOTE,
		VaultV2ItemType.BANK_CARD,
		VaultV2ItemType.DOCUMENT -> secureItem?.keepassDatabaseId
		VaultV2ItemType.PASSKEY -> passkeyEntry?.keepassDatabaseId
	}
}

private fun VaultV2Item.keepassGroupPath(): String? {
	return when (type) {
		VaultV2ItemType.PASSWORD -> passwordEntry?.keepassGroupPath
		VaultV2ItemType.AUTHENTICATOR -> totpItem?.keepassGroupPath
		VaultV2ItemType.NOTE,
		VaultV2ItemType.BANK_CARD,
		VaultV2ItemType.DOCUMENT -> secureItem?.keepassGroupPath
		VaultV2ItemType.PASSKEY -> passkeyEntry?.keepassGroupPath
	}
}

private fun VaultV2Item.bitwardenVaultId(): Long? {
	return when (type) {
		VaultV2ItemType.PASSWORD -> passwordEntry?.bitwardenVaultId
		VaultV2ItemType.AUTHENTICATOR -> totpItem?.bitwardenVaultId
		VaultV2ItemType.NOTE,
		VaultV2ItemType.BANK_CARD,
		VaultV2ItemType.DOCUMENT -> secureItem?.bitwardenVaultId
		VaultV2ItemType.PASSKEY -> passkeyEntry?.bitwardenVaultId
	}
}

private fun VaultV2Item.bitwardenFolderId(): String? {
	return when (type) {
		VaultV2ItemType.PASSWORD -> passwordEntry?.bitwardenFolderId
		VaultV2ItemType.AUTHENTICATOR -> totpItem?.bitwardenFolderId
		VaultV2ItemType.NOTE,
		VaultV2ItemType.BANK_CARD,
		VaultV2ItemType.DOCUMENT -> secureItem?.bitwardenFolderId
		VaultV2ItemType.PASSKEY -> passkeyEntry?.bitwardenFolderId
	}
}

private fun toggleVaultV2ContentType(
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

private fun VaultV2Item.matchesDisplayedTypes(
	displayedTypes: Set<PasswordPageContentType>
): Boolean {
	return toPasswordPageContentType() in displayedTypes
}

private fun VaultV2Item.matchesPasswordQuickFilters(
	configuredQuickFilterItems: List<PasswordListQuickFilterItem>,
	quickFilterFavorite: Boolean,
	quickFilter2fa: Boolean,
	quickFilterNotes: Boolean,
	quickFilterUncategorized: Boolean,
	quickFilterLocalOnly: Boolean,
	quickFilterManualStackOnly: Boolean,
	quickFilterNeverStack: Boolean,
	quickFilterUnstacked: Boolean,
	manualStackGroupByEntryId: Map<Long, String>,
	noStackEntryIds: Set<Long>,
): Boolean {
	if (quickFilterFavorite && PasswordListQuickFilterItem.FAVORITE in configuredQuickFilterItems && !isFavorite) {
		return false
	}
	if (
		quickFilter2fa &&
		PasswordListQuickFilterItem.TWO_FA in configuredQuickFilterItems &&
		type != VaultV2ItemType.AUTHENTICATOR &&
		passwordEntry?.authenticatorKey.isNullOrBlank()
	) {
		return false
	}
	if (
		quickFilterNotes &&
		PasswordListQuickFilterItem.NOTES in configuredQuickFilterItems &&
		passwordEntry?.notes.isNullOrBlank()
	) {
		return false
	}
	if (
		quickFilterUncategorized &&
		PasswordListQuickFilterItem.UNCATEGORIZED in configuredQuickFilterItems &&
		categoryId() != null
	) {
		return false
	}
	if (
		quickFilterLocalOnly &&
		PasswordListQuickFilterItem.LOCAL_ONLY in configuredQuickFilterItems &&
		!isLocalOnly()
	) {
		return false
	}

	val passwordId = passwordEntry?.id
	if (
		quickFilterManualStackOnly &&
		PasswordListQuickFilterItem.MANUAL_STACK_ONLY in configuredQuickFilterItems &&
		(passwordId == null || passwordId !in manualStackGroupByEntryId)
	) {
		return false
	}
	if (
		quickFilterNeverStack &&
		PasswordListQuickFilterItem.NEVER_STACK in configuredQuickFilterItems &&
		(passwordId == null || passwordId !in noStackEntryIds)
	) {
		return false
	}
	if (
		quickFilterUnstacked &&
		PasswordListQuickFilterItem.UNSTACKED in configuredQuickFilterItems &&
		(passwordId == null || passwordId in manualStackGroupByEntryId)
	) {
		return false
	}

	return true
}

@OptIn(
	ExperimentalMaterial3Api::class,
	ExperimentalFoundationApi::class,
)
@Composable
fun VaultV2Pane(
	passwordViewModel: PasswordViewModel,
	totpViewModel: TotpViewModel,
	bankCardViewModel: BankCardViewModel,
	documentViewModel: DocumentViewModel,
	noteViewModel: NoteViewModel,
	passkeyViewModel: PasskeyViewModel,
	keepassDatabases: List<LocalKeePassDatabase>,
	bitwardenVaults: List<BitwardenVault>,
	localKeePassViewModel: LocalKeePassViewModel,
	settingsViewModel: SettingsViewModel,
	state: VaultV2PaneState,
	onOpenPassword: (Long) -> Unit,
	onOpenTotp: (Long) -> Unit,
	onOpenBankCard: (Long) -> Unit,
	onOpenDocument: (Long) -> Unit,
	onOpenNote: (Long) -> Unit,
	onOpenPasskey: (Long) -> Unit,
	onOpenHistory: () -> Unit,
	onOpenTrashPage: () -> Unit,
	onOpenArchivePage: () -> Unit,
	onOpenCommonAccountTemplates: () -> Unit,
	showOnlyLocalData: Boolean = false,
	appSettings: AppSettings = AppSettings(),
	modifier: Modifier = Modifier,
) {
	var searchQuery by rememberSaveable { mutableStateOf("") }
	var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
	var isStorageFilterSheetVisible by rememberSaveable { mutableStateOf(false) }
	var isTopActionsMenuExpanded by rememberSaveable { mutableStateOf(false) }
	var showDisplayOptionsSheet by rememberSaveable { mutableStateOf(false) }
	var showBitwardenUnlockDialog by rememberSaveable { mutableStateOf(false) }
	var showClearBitwardenCacheDialog by rememberSaveable { mutableStateOf(false) }
	var showCreateCategoryDialog by rememberSaveable { mutableStateOf(false) }
	var quickFilterFavorite by rememberSaveable { mutableStateOf(false) }
	var quickFilter2fa by rememberSaveable { mutableStateOf(false) }
	var quickFilterNotes by rememberSaveable { mutableStateOf(false) }
	var quickFilterUncategorized by rememberSaveable { mutableStateOf(false) }
	var quickFilterLocalOnly by rememberSaveable { mutableStateOf(false) }
	var quickFilterManualStackOnly by rememberSaveable { mutableStateOf(false) }
	var quickFilterNeverStack by rememberSaveable { mutableStateOf(false) }
	var quickFilterUnstacked by rememberSaveable { mutableStateOf(false) }
	var selectedAggregateTypes by remember { mutableStateOf<Set<PasswordPageContentType>>(emptySet()) }
	val selectedKeys = remember { mutableStateListOf<String>() }
	val listState = rememberLazyListState(
		initialFirstVisibleItemIndex = state.scrollIndex,
		initialFirstVisibleItemScrollOffset = state.scrollOffset
	)
	val context = LocalContext.current
	val density = LocalDensity.current
	val scope = rememberCoroutineScope()
	val bitwardenViewModel: BitwardenViewModel = viewModel()
	val bitwardenRepository = remember(context) {
		takagi.ru.monica.bitwarden.repository.BitwardenRepository.getInstance(context)
	}
	val bitwardenSyncStatusByVault by bitwardenViewModel.syncStatusByVault.collectAsState()
	val pullSearchTriggerDistance = remember(density) { with(density) { 40.dp.toPx() } }
	val pullSyncTriggerDistance = remember(density) { with(density) { 72.dp.toPx() } }
	val pullMaxDragDistance = remember(density) { with(density) { 100.dp.toPx() } }
	val pullAction = rememberPullActionState(
		isBitwardenDatabaseView = false,
		isSearchExpanded = isSearchExpanded,
		searchTriggerDistance = pullSearchTriggerDistance,
		syncTriggerDistance = pullSyncTriggerDistance,
		maxDragDistance = pullMaxDragDistance,
		bitwardenRepository = bitwardenRepository,
		onSearchTriggered = { isSearchExpanded = true },
	)
	val stackCardMode = remember(appSettings.stackCardMode) {
		runCatching { StackCardMode.valueOf(appSettings.stackCardMode) }.getOrDefault(StackCardMode.AUTO)
	}

	val passwordEntries by passwordViewModel.allPasswordsForUi.collectAsState()
	val categories by passwordViewModel.categories.collectAsState()
	val totpItems by totpViewModel.allTotpItems.collectAsState()
	val bankCardItems by bankCardViewModel.allCards.collectAsState(initial = emptyList())
	val documentItems by documentViewModel.allDocuments.collectAsState(initial = emptyList())
	val noteItems by noteViewModel.allNotes.collectAsState(initial = emptyList())
	val passkeyItems by passkeyViewModel.allPasskeys.collectAsState()
	val fastScrollRequestKey = state.fastScrollRequestKey
	val fastScrollProgress = state.fastScrollProgress
	LaunchedEffect(state) {
		state.ensureAggregateDefaultStorageFilter()
	}
	val storageSelection = remember(
		state.storageFilterType,
		state.storageFilterPrimaryId,
		state.storageFilterSecondaryKey,
	) {
		state.toUnifiedCategoryFilterSelection()
	}
	val selectedBitwardenVaultId = remember(storageSelection) {
		when (storageSelection) {
			is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> storageSelection.vaultId
			is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> storageSelection.vaultId
			is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> storageSelection.vaultId
			is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> storageSelection.vaultId
			else -> null
		}
	}
	val selectedKeePassDatabaseId = remember(storageSelection) {
		when (storageSelection) {
			is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> storageSelection.databaseId
			is UnifiedCategoryFilterSelection.KeePassGroupFilter -> storageSelection.databaseId
			is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> storageSelection.databaseId
			is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> storageSelection.databaseId
			else -> null
		}
	}
	val isTopBarSyncing = selectedBitwardenVaultId?.let { vaultId ->
		bitwardenSyncStatusByVault[vaultId].isUserVisibleSyncInProgress()
	} == true
	var clearCacheRiskSummary by remember {
		mutableStateOf<BitwardenRepository.VaultCacheRiskSummary?>(null)
	}
	var isBitwardenMaintenanceActionRunning by remember { mutableStateOf(false) }
	val selectedBitwardenVault = selectedBitwardenVaultId?.let { vaultId ->
		bitwardenVaults.find { it.id == vaultId }
	}
	val selectedBitwardenFoldersFlow = remember(passwordViewModel, selectedBitwardenVaultId) {
		selectedBitwardenVaultId?.let(passwordViewModel::getBitwardenFolders) ?: flowOf(emptyList())
	}
	val selectedBitwardenFolders by selectedBitwardenFoldersFlow.collectAsState(initial = emptyList())
	val quickFolderNodes = remember(categories) { buildPasswordQuickFolderNodes(categories) }
	val quickFolderNodeByPath = remember(quickFolderNodes) { quickFolderNodes.associateBy { it.path } }
	val breadcrumbCategoryFilter = remember(storageSelection) {
		storageSelection.toCategoryFilterOrNull()
	}
	val quickFolderCurrentPath = remember(breadcrumbCategoryFilter, quickFolderNodes) {
		when (val filter = breadcrumbCategoryFilter) {
			is CategoryFilter.Custom -> quickFolderNodes.firstOrNull { it.category.id == filter.categoryId }?.path
			else -> null
		}
	}
	val breadcrumbRootFilter = remember(breadcrumbCategoryFilter) {
		when (breadcrumbCategoryFilter) {
			is CategoryFilter.Custom,
			is CategoryFilter.Local,
			is CategoryFilter.LocalStarred,
			is CategoryFilter.LocalUncategorized -> CategoryFilter.Local
			else -> CategoryFilter.All
		}
	}
	val pathBreadcrumbs = rememberVaultV2AsyncComputed(
		breadcrumbCategoryFilter,
		quickFolderCurrentPath,
		quickFolderNodeByPath,
		keepassDatabases,
		bitwardenVaults,
		selectedBitwardenFolders,
		categories,
		initialValue = emptyList<PasswordQuickFolderBreadcrumb>()
	) {
		val currentFilter = breadcrumbCategoryFilter ?: return@rememberVaultV2AsyncComputed emptyList()
		buildQuickFolderBreadcrumbs(
			context = context,
			quickFolderPathBannerEnabledForCurrentFilter = true,
			currentFilter = currentFilter,
			quickFolderCurrentPath = quickFolderCurrentPath,
			quickFolderNodeByPath = quickFolderNodeByPath,
			quickFolderRootFilter = breadcrumbRootFilter,
			keepassDatabases = keepassDatabases,
			bitwardenVaults = bitwardenVaults,
			selectedBitwardenFolders = selectedBitwardenFolders,
			categories = categories,
		)
	}
	val storageFilterLabel = rememberVaultV2StorageFilterLabel(
		selected = storageSelection,
		categories = categories,
		keepassDatabases = keepassDatabases,
		bitwardenVaults = bitwardenVaults,
		bitwardenFolders = selectedBitwardenFolders,
	)

	val visiblePasswordEntries = remember(passwordEntries, showOnlyLocalData) {
		if (showOnlyLocalData) passwordEntries.filter { it.isVaultV2LocalOnly() } else passwordEntries
	}
	val visibleTotpItems = remember(totpItems, showOnlyLocalData) {
		if (showOnlyLocalData) totpItems.filter { it.isVaultV2LocalOnly() } else totpItems
	}
	val visibleBankCardItems = remember(bankCardItems, showOnlyLocalData) {
		if (showOnlyLocalData) bankCardItems.filter { it.isVaultV2LocalOnly() } else bankCardItems
	}
	val visibleDocumentItems = remember(documentItems, showOnlyLocalData) {
		if (showOnlyLocalData) documentItems.filter { it.isVaultV2LocalOnly() } else documentItems
	}
	val visibleNoteItems = remember(noteItems, showOnlyLocalData) {
		if (showOnlyLocalData) noteItems.filter { it.isVaultV2LocalOnly() } else noteItems
	}
	val visiblePasskeyItems = remember(passkeyItems, showOnlyLocalData) {
		if (showOnlyLocalData) passkeyItems.filter { it.isVaultV2LocalOnly() } else passkeyItems
	}
	val categoryMenuFilter = remember(storageSelection) {
		storageSelection.toCategoryFilterOrNull() ?: CategoryFilter.All
	}
	val selectedKeePassGroupsFlow = remember(localKeePassViewModel, selectedKeePassDatabaseId) {
		selectedKeePassDatabaseId?.let(localKeePassViewModel::getGroups) ?: flowOf(emptyList())
	}
	val selectedKeePassGroups by selectedKeePassGroupsFlow.collectAsState(initial = emptyList())
	val quickFilterVisibleTypes = remember(
		appSettings.passwordPageAggregateEnabled,
		appSettings.passwordPageVisibleContentTypes
	) {
		resolvePasswordPageVisibleTypes(
			aggregateEnabled = appSettings.passwordPageAggregateEnabled,
			configuredTypes = appSettings.passwordPageVisibleContentTypes
		)
	}
	val configuredQuickFilterItems = remember(
		appSettings.passwordListQuickFilterItems,
		appSettings.passwordPageAggregateEnabled,
		quickFilterVisibleTypes
	) {
		appendAggregateContentQuickFilterItems(
			configuredItems = appSettings.passwordListQuickFilterItems,
			visibleTypes = quickFilterVisibleTypes,
			aggregateEnabled = appSettings.passwordPageAggregateEnabled
		)
	}
	LaunchedEffect(configuredQuickFilterItems, quickFilterVisibleTypes) {
		selectedAggregateTypes = sanitizeSelectedPasswordPageTypes(
			visibleTypes = quickFilterVisibleTypes,
			selectedTypes = selectedAggregateTypes
		)
		if (PasswordListQuickFilterItem.FAVORITE !in configuredQuickFilterItems) quickFilterFavorite = false
		if (PasswordListQuickFilterItem.TWO_FA !in configuredQuickFilterItems) quickFilter2fa = false
		if (PasswordListQuickFilterItem.NOTES !in configuredQuickFilterItems) quickFilterNotes = false
		if (PasswordListQuickFilterItem.UNCATEGORIZED !in configuredQuickFilterItems) quickFilterUncategorized = false
		if (PasswordListQuickFilterItem.LOCAL_ONLY !in configuredQuickFilterItems) quickFilterLocalOnly = false
		if (PasswordListQuickFilterItem.MANUAL_STACK_ONLY !in configuredQuickFilterItems) quickFilterManualStackOnly = false
		if (PasswordListQuickFilterItem.NEVER_STACK !in configuredQuickFilterItems) quickFilterNeverStack = false
		if (PasswordListQuickFilterItem.UNSTACKED !in configuredQuickFilterItems) quickFilterUnstacked = false
	}
	val displayedContentTypes = remember(quickFilterVisibleTypes, selectedAggregateTypes) {
		resolvePasswordPageDisplayedTypes(
			visibleTypes = quickFilterVisibleTypes,
			selectedTypes = selectedAggregateTypes
		)
	}
	val hasVisibleQuickFilters = remember(
		appSettings.passwordListQuickFiltersEnabled,
		configuredQuickFilterItems,
		quickFilterVisibleTypes
	) {
		appSettings.passwordListQuickFiltersEnabled &&
			configuredQuickFilterItems.any { item ->
				takagi.ru.monica.ui.shouldShowQuickFilterItem(item, quickFilterVisibleTypes)
			}
	}
	var manualStackGroupByEntryId by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
	var noStackEntryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
	var lastCustomFieldEntryIds by remember { mutableStateOf<List<Long>>(emptyList()) }
	val shouldLoadManualStackMetadata = remember(
		quickFilterManualStackOnly,
		quickFilterNeverStack,
		quickFilterUnstacked,
		configuredQuickFilterItems
	) {
		(quickFilterManualStackOnly && PasswordListQuickFilterItem.MANUAL_STACK_ONLY in configuredQuickFilterItems) ||
			(quickFilterNeverStack && PasswordListQuickFilterItem.NEVER_STACK in configuredQuickFilterItems) ||
			(quickFilterUnstacked && PasswordListQuickFilterItem.UNSTACKED in configuredQuickFilterItems)
	}
	LaunchedEffect(visiblePasswordEntries, shouldLoadManualStackMetadata) {
		if (!shouldLoadManualStackMetadata) {
			manualStackGroupByEntryId = emptyMap()
			noStackEntryIds = emptySet()
			lastCustomFieldEntryIds = emptyList()
			return@LaunchedEffect
		}
		val allIds = withContext(Dispatchers.Default) {
			visiblePasswordEntries.asSequence().map(PasswordEntry::id).toList()
		}
		if (allIds.isEmpty()) {
			manualStackGroupByEntryId = emptyMap()
			noStackEntryIds = emptySet()
			lastCustomFieldEntryIds = emptyList()
			return@LaunchedEffect
		}
		if (allIds == lastCustomFieldEntryIds) return@LaunchedEffect
		lastCustomFieldEntryIds = allIds
		val fieldMap = withContext(Dispatchers.IO) {
			passwordViewModel.getCustomFieldsByEntryIds(allIds)
		}
		val (manualStackMap, noStackIds) = withContext(Dispatchers.Default) {
			val manualStack = fieldMap.mapNotNull { (entryId, fields) ->
				val groupId = fields.firstOrNull {
					it.title == MONICA_MANUAL_STACK_GROUP_FIELD_TITLE
				}?.value?.takeIf(String::isNotBlank)
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
	val baseQuickFolderPasswordCountByCategoryId = rememberVaultV2AsyncComputed(
		visiblePasswordEntries,
		categories,
		initialValue = emptyMap<Long, Int>()
	) {
		buildLocalQuickFolderPasswordCountByCategoryId(
			entries = visiblePasswordEntries,
			categories = categories
		)
	}
	val categoryMenuQuickFolderPasswordCountByCategoryId = remember(
		baseQuickFolderPasswordCountByCategoryId,
		categoryMenuFilter
	) {
		if (!categoryMenuFilter.supportsQuickFolders()) emptyMap() else baseQuickFolderPasswordCountByCategoryId
	}
	val categoryMenuQuickFolderShortcuts = rememberVaultV2AsyncComputed(
		categoryMenuFilter,
		quickFolderCurrentPath,
		quickFolderNodes,
		quickFolderNodeByPath,
		categoryMenuQuickFolderPasswordCountByCategoryId,
		visiblePasswordEntries,
		searchQuery,
		keepassDatabases,
		selectedKeePassGroups,
		bitwardenVaults,
		selectedBitwardenFolders,
		categories,
		initialValue = emptyList()
	) {
		buildCategoryMenuFolderShortcuts(
			context = context,
			currentFilter = categoryMenuFilter,
			quickFolderCurrentPath = quickFolderCurrentPath,
			quickFolderNodes = quickFolderNodes,
			quickFolderNodeByPath = quickFolderNodeByPath,
			quickFolderPasswordCountByCategoryId = categoryMenuQuickFolderPasswordCountByCategoryId,
			allPasswords = visiblePasswordEntries,
			searchScopedPasswords = visiblePasswordEntries,
			isSearchActive = searchQuery.isNotBlank(),
			keepassDatabases = keepassDatabases,
			keepassGroupsForSelectedDb = selectedKeePassGroups,
			bitwardenVaults = bitwardenVaults,
			selectedBitwardenFolders = selectedBitwardenFolders,
			categories = categories
		)
	}
	val quickFilterBindings = remember(
		quickFilterFavorite,
		quickFilter2fa,
		quickFilterNotes,
		quickFilterUncategorized,
		quickFilterLocalOnly,
		quickFilterManualStackOnly,
		quickFilterNeverStack,
		quickFilterUnstacked,
		selectedAggregateTypes,
		quickFilterVisibleTypes
	) {
		buildCategoryMenuQuickFilterBindings(
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
			aggregateSelectedTypes = selectedAggregateTypes,
			aggregateVisibleTypes = quickFilterVisibleTypes,
			onToggleAggregateType = { type ->
				selectedAggregateTypes = toggleVaultV2ContentType(
					currentTypes = selectedAggregateTypes,
					toggledType = type,
					visibleTypes = quickFilterVisibleTypes
				)
			}
		)
	}

	val computedListStateAsync = rememberVaultV2AsyncComputedValue(
		visiblePasswordEntries,
		visibleTotpItems,
		visibleBankCardItems,
		visibleDocumentItems,
		visibleNoteItems,
		visiblePasskeyItems,
		initialValue = VaultV2ComputedListState(),
	) {
		val passwordList = visiblePasswordEntries.map { entry ->
			val displayTitle = entry.title.ifBlank { "(Untitled)" }
			val subtitle = entry.username.ifBlank { entry.website }.ifBlank { "-" }
			VaultV2Item(
				key = "password:${entry.id}",
				type = VaultV2ItemType.PASSWORD,
				title = displayTitle,
				subtitle = subtitle,
				isFavorite = entry.isFavorite,
				sortKey = normalizedVaultV2SortKey(displayTitle),
				searchText = listOf(displayTitle, entry.username, entry.website, entry.appName, entry.notes)
					.filter { it.isNotBlank() }
					.joinToString("\n"),
				passwordEntry = entry,
			)
		}

		val totpList = visibleTotpItems.map { item ->
			val data = runCatching { Json.decodeFromString<TotpData>(item.itemData) }.getOrNull()
			val subtitle = listOf(data?.issuer, data?.accountName)
				.filterNotNull()
				.map { it.trim() }
				.filter { it.isNotEmpty() }
				.joinToString(" · ")
				.ifBlank { item.notes.ifBlank { "-" } }

			val displayTitle = item.title.ifBlank { data?.issuer ?: "(Untitled)" }
			VaultV2Item(
				key = "totp:${item.id}",
				type = VaultV2ItemType.AUTHENTICATOR,
				title = displayTitle,
				subtitle = subtitle,
				isFavorite = item.isFavorite,
				sortKey = normalizedVaultV2SortKey(displayTitle),
				searchText = listOf(displayTitle, subtitle, item.notes, item.itemData)
					.filter { it.isNotBlank() }
					.joinToString("\n"),
				totpItem = item,
				boundPasswordId = data?.boundPasswordId,
			)
		}

		val noteList = visibleNoteItems.map { item ->
			val displayTitle = item.title.ifBlank { "(Untitled)" }
			val decoded = NoteContentCodec.decodeFromItem(item)
			val previewText = vaultV2PlainSingleLine(
				NoteContentCodec.toPlainPreview(decoded.content, decoded.isMarkdown)
			)
			VaultV2Item(
				key = "note:${item.id}",
				type = VaultV2ItemType.NOTE,
				title = displayTitle,
				subtitle = previewText.ifBlank { item.notes.ifBlank { "-" } },
				isFavorite = item.isFavorite,
				sortKey = normalizedVaultV2SortKey(displayTitle),
				searchText = listOf(displayTitle, decoded.content, decoded.tags.joinToString(" "), item.notes)
					.filter { it.isNotBlank() }
					.joinToString("\n"),
				secureItem = item,
			)
		}

		val passkeyList = visiblePasskeyItems.map { passkey ->
			val displayTitle = passkey.rpName.ifBlank { passkey.rpId }.ifBlank { "(Untitled)" }
			val subtitle = listOf(
				passkey.userDisplayName.ifBlank { passkey.userName },
				passkey.userName.takeIf {
					it.isNotBlank() && it != passkey.userDisplayName
				},
				passkey.rpId.takeIf { it.isNotBlank() && it != displayTitle }
			)
				.filterNotNull()
				.filter { it.isNotBlank() }
				.joinToString(" · ")
				.ifBlank { "-" }
			VaultV2Item(
				key = "passkey:${passkey.credentialId}",
				type = VaultV2ItemType.PASSKEY,
				title = displayTitle,
				subtitle = subtitle,
				isFavorite = false,
				sortKey = normalizedVaultV2SortKey(displayTitle),
				searchText = listOf(
					displayTitle,
					passkey.rpId,
					passkey.userName,
					passkey.userDisplayName,
					passkey.notes,
				).filter { it.isNotBlank() }.joinToString("\n"),
				passkeyEntry = passkey,
				boundPasswordId = passkey.boundPasswordId,
			)
		}

		val bankCardList = visibleBankCardItems.map { item ->
			val data = runCatching { Json.decodeFromString<BankCardData>(item.itemData) }.getOrNull()
			val displayTitle = item.title.ifBlank { data?.bankName ?: "(Untitled)" }
			val subtitle = vaultV2BankCardSubtitle(data = data, fallbackNotes = item.notes)
			VaultV2Item(
				key = "bank_card:${item.id}",
				type = VaultV2ItemType.BANK_CARD,
				title = displayTitle,
				subtitle = subtitle,
				isFavorite = item.isFavorite,
				sortKey = normalizedVaultV2SortKey(displayTitle),
				searchText = listOf(
					displayTitle,
					data?.bankName.orEmpty(),
					data?.cardholderName.orEmpty(),
					data?.cardNumber.orEmpty(),
					item.notes,
				).filter { it.isNotBlank() }.joinToString("\n"),
				secureItem = item,
			)
		}

		val documentList = visibleDocumentItems.map { item ->
			val data = runCatching { Json.decodeFromString<DocumentData>(item.itemData) }.getOrNull()
			val displayTitle = item.title.ifBlank { data?.fullName ?: "(Untitled)" }
			val subtitle = vaultV2DocumentSubtitle(data = data, fallbackNotes = item.notes)
			VaultV2Item(
				key = "document:${item.id}",
				type = VaultV2ItemType.DOCUMENT,
				title = displayTitle,
				subtitle = subtitle,
				isFavorite = item.isFavorite,
				sortKey = normalizedVaultV2SortKey(displayTitle),
				searchText = listOf(
					displayTitle,
					data?.fullName.orEmpty(),
					data?.documentNumber.orEmpty(),
					data?.issuedBy.orEmpty(),
					item.notes,
				).filter { it.isNotBlank() }.joinToString("\n"),
				secureItem = item,
			)
		}

		val allItemsRaw = dedupeExactVaultItems(
			passwordList + totpList + noteList + passkeyList + bankCardList + documentList
		).sortedWith(
				compareBy<VaultV2Item> { it.sortKey.lowercase(Locale.ROOT) }
					.thenBy { it.type.ordinal }
					.thenBy { it.key }
			)
		VaultV2ComputedListState(
			allItemsRaw = allItemsRaw,
			passwordById = visiblePasswordEntries.associateBy { it.id },
		)
	}
	val computedListState = computedListStateAsync.value
	val allItemsRaw = computedListState.allItemsRaw
	val passwordById = computedListState.passwordById

	var allItems by remember { mutableStateOf(allItemsRaw) }
	var pendingAllItems by remember { mutableStateOf<List<VaultV2Item>?>(null) }
	var isAutoScrollingToTop by remember { mutableStateOf(false) }
	var lastHandledScrollToTopRequestKey by rememberSaveable { mutableStateOf(0) }
	var lastHandledFastScrollRequestKey by remember {
		mutableStateOf(fastScrollRequestKey)
	}
	LaunchedEffect(allItemsRaw) {
		if (isAutoScrollingToTop) {
			pendingAllItems = allItemsRaw
		} else {
			// Coalesce rapid multi-source emissions so the list appears in one stable batch.
			delay(120)
			allItems = allItemsRaw
		}
	}

	LaunchedEffect(isAutoScrollingToTop) {
		if (!isAutoScrollingToTop) {
			pendingAllItems?.let { buffered ->
				allItems = buffered
				pendingAllItems = null
			}
		}
	}

	val normalizedQuery = remember(searchQuery) { searchQuery.trim() }
	val storageFilteredItems = remember(allItems, storageSelection) {
		allItems.filter { item -> item.matchesStorageFilter(storageSelection) }
	}
	val filteredItems = remember(
		storageFilteredItems,
		displayedContentTypes,
		configuredQuickFilterItems,
		quickFilterFavorite,
		quickFilter2fa,
		quickFilterNotes,
		quickFilterUncategorized,
		quickFilterLocalOnly,
		quickFilterManualStackOnly,
		quickFilterNeverStack,
		quickFilterUnstacked,
		manualStackGroupByEntryId,
		noStackEntryIds,
		normalizedQuery
	) {
		storageFilteredItems.filter { item ->
			if (!item.matchesDisplayedTypes(displayedContentTypes)) return@filter false
			if (
				!item.matchesPasswordQuickFilters(
					configuredQuickFilterItems = configuredQuickFilterItems,
					quickFilterFavorite = quickFilterFavorite,
					quickFilter2fa = quickFilter2fa,
					quickFilterNotes = quickFilterNotes,
					quickFilterUncategorized = quickFilterUncategorized,
					quickFilterLocalOnly = quickFilterLocalOnly,
					quickFilterManualStackOnly = quickFilterManualStackOnly,
					quickFilterNeverStack = quickFilterNeverStack,
					quickFilterUnstacked = quickFilterUnstacked,
					manualStackGroupByEntryId = manualStackGroupByEntryId,
					noStackEntryIds = noStackEntryIds
				)
			) return@filter false

			if (normalizedQuery.isBlank()) {
				true
			} else {
				item.searchText.contains(normalizedQuery, ignoreCase = true)
			}
		}
	}

	val groupedItems = remember(filteredItems) {
		filteredItems.groupBy { item ->
			firstLetterGroup(item.sortKey)
		}
	}

	val sectionedItems = remember(groupedItems) {
		groupedItems.keys
			.sortedWith(compareBy<String> { if (it == "#") 1 else 0 }.thenBy { it })
			.map { section -> section to groupedItems[section].orEmpty() }
	}
	val isVaultListLoading = remember(
		computedListStateAsync.isComputing,
		pendingAllItems,
		allItems,
		normalizedQuery,
	) {
		normalizedQuery.isBlank() &&
			allItems.isEmpty() &&
			(computedListStateAsync.isComputing || pendingAllItems != null)
	}
	var showVaultEmptyState by remember { mutableStateOf(false) }
	LaunchedEffect(sectionedItems, normalizedQuery, isVaultListLoading) {
		if (sectionedItems.isNotEmpty()) {
			showVaultEmptyState = false
			return@LaunchedEffect
		}
		if (normalizedQuery.isNotBlank()) {
			showVaultEmptyState = true
			return@LaunchedEffect
		}
		if (isVaultListLoading) {
			showVaultEmptyState = false
			return@LaunchedEffect
		}
		delay(VAULT_V2_EMPTY_STATE_DEBOUNCE_MS)
		showVaultEmptyState = true
	}
	val showVaultLoadingIndicator = sectionedItems.isEmpty() && !showVaultEmptyState
	val sectionLayouts = remember(sectionedItems) {
		var itemStartIndex = 0
		var lazyIndex = 0
		sectionedItems.map { (sectionTitle, itemsInSection) ->
			VaultV2SectionLayout(
				title = sectionTitle,
				items = itemsInSection,
				itemStartIndex = itemStartIndex,
				firstItemLazyIndex = lazyIndex + 1,
			).also {
				itemStartIndex += itemsInSection.size
				lazyIndex += itemsInSection.size + 1
			}
		}
	}

	val selectedCount by remember { derivedStateOf { selectedKeys.size } }
	val selectedItems = remember(selectedKeys, allItems) {
		val keySet = selectedKeys.toSet()
		allItems.filter { it.key in keySet }
	}
	val currentSectionIndicatorLabel by remember(listState, sectionLayouts) {
		derivedStateOf {
			if (sectionLayouts.isEmpty()) {
				"#"
			} else {
				vaultV2SectionTitleForLazyIndex(
					sectionLayouts = sectionLayouts,
					lazyIndex = listState.firstVisibleItemIndex,
				)?.take(2)?.uppercase(Locale.ROOT) ?: sectionLayouts.first().title
			}
		}
	}
	val showBackToTop by remember(listState) {
		derivedStateOf { listState.firstVisibleItemIndex > 3 }
	}

	LaunchedEffect(selectedCount) {
		state.updateSelectionCount(selectedCount)
	}

	LaunchedEffect(showBackToTop, selectedCount) {
		state.showBackToTop = showBackToTop && selectedCount == 0
	}

	DisposableEffect(Unit) {
		onDispose {
			state.fastScrollIndicatorLabel = null
		}
	}

	LaunchedEffect(state.scrollToTopRequestKey) {
		if (state.scrollToTopRequestKey > lastHandledScrollToTopRequestKey) {
			isAutoScrollingToTop = true
			try {
				runCatching {
					listState.animateScrollToItem(0)
				}
				listState.scrollToItem(0)
			} finally {
				isAutoScrollingToTop = false
				lastHandledScrollToTopRequestKey = state.scrollToTopRequestKey
			}
		}
	}

	LaunchedEffect(listState) {
		snapshotFlow {
			listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
		}
			.distinctUntilChanged()
			.collect { (index, offset) ->
				state.updateScrollPosition(index, offset)
			}
	}

	LaunchedEffect(fastScrollRequestKey, fastScrollProgress, sectionLayouts, filteredItems.size) {
		if (fastScrollRequestKey <= lastHandledFastScrollRequestKey) {
			return@LaunchedEffect
		}

		if (filteredItems.isEmpty() || sectionLayouts.isEmpty()) {
			lastHandledFastScrollRequestKey = fastScrollRequestKey
			return@LaunchedEffect
		}

		val targetItemIndex = (
			fastScrollProgress.coerceIn(0f, 1f) * (filteredItems.size - 1)
		).roundToInt().coerceIn(0, filteredItems.size - 1)
		val targetLazyIndex = vaultV2LazyIndexForItemIndex(
			sectionLayouts = sectionLayouts,
			targetItemIndex = targetItemIndex,
		)

		try {
			if (listState.firstVisibleItemIndex != targetLazyIndex) {
				runCatching {
					listState.scrollToItem(index = targetLazyIndex)
				}.onFailure { throwable ->
					if (throwable is CancellationException) return@onFailure
					Log.e(
						VAULT_V2_FAST_SCROLL_LOG_TAG,
						"scrollToItem failed: targetLazyIndex=$targetLazyIndex filteredSize=${filteredItems.size}",
						throwable
					)
				}
			}
		} finally {
			lastHandledFastScrollRequestKey = fastScrollRequestKey
		}
	}

	LaunchedEffect(listState, sectionLayouts, filteredItems.size) {
		snapshotFlow {
			if (filteredItems.size <= 1 || sectionLayouts.isEmpty()) {
				0f
			} else {
				val currentItemIndex = vaultV2ItemIndexForLazyIndex(
					sectionLayouts = sectionLayouts,
					lazyIndex = listState.firstVisibleItemIndex,
				).coerceIn(0, filteredItems.size - 1)
				(currentItemIndex.toFloat() / (filteredItems.size - 1).toFloat()).coerceIn(0f, 1f)
			}
		}
			.distinctUntilChanged()
			.collect { progress ->
				state.updateFastScrollProgress(progress)
			}
	}

	LaunchedEffect(listState, sectionLayouts, filteredItems.size) {
		snapshotFlow {
			if (filteredItems.isEmpty() || sectionLayouts.isEmpty()) {
				null
			} else {
				vaultV2SectionTitleForLazyIndex(
					sectionLayouts = sectionLayouts,
					lazyIndex = listState.firstVisibleItemIndex,
				)
			}
		}
			.distinctUntilChanged()
			.collect { sectionTitle ->
				state.fastScrollIndicatorLabel = sectionTitle
			}
	}

	Box(modifier = modifier.fillMaxSize()) {
		Column(modifier = Modifier.fillMaxSize()) {
			ExpressiveTopBar(
				title = storageFilterLabel,
				searchQuery = searchQuery,
				onSearchQueryChange = { searchQuery = it },
				isSearchExpanded = isSearchExpanded,
				onSearchExpandedChange = { isSearchExpanded = it },
				searchHint = stringResource(R.string.topbar_search_hint),
				actions = {
					Box {
						IconButton(onClick = { isStorageFilterSheetVisible = true }) {
							Icon(
								imageVector = Icons.Default.Folder,
								contentDescription = stringResource(R.string.category),
							)
						}
						if (appSettings.categorySelectionUiMode == CategorySelectionUiMode.CHIP_MENU) {
							UnifiedCategoryFilterChipMenuDropdown(
								expanded = isStorageFilterSheetVisible,
								onDismissRequest = { isStorageFilterSheetVisible = false },
								offset = UnifiedCategoryFilterChipMenuOffset
							) {
								PasswordListCategoryChipMenu(
									currentFilter = categoryMenuFilter,
									keepassDatabases = keepassDatabases,
									bitwardenVaults = bitwardenVaults,
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
									aggregateSelectedTypes = selectedAggregateTypes,
									aggregateVisibleTypes = quickFilterVisibleTypes,
									onToggleAggregateType = { type ->
										selectedAggregateTypes = toggleVaultV2ContentType(
											currentTypes = selectedAggregateTypes,
											toggledType = type,
											visibleTypes = quickFilterVisibleTypes
										)
									},
									quickFolderShortcuts = categoryMenuQuickFolderShortcuts,
									topModulesOrder = appSettings.passwordListTopModulesOrder,
									onTopModulesOrderChange = settingsViewModel::updatePasswordListTopModulesOrder,
									onQuickFilterItemsOrderChange = settingsViewModel::updatePasswordListQuickFilterItems,
									launchAnchorBounds = null,
									onDismiss = { isStorageFilterSheetVisible = false },
									onSelectFilter = { filter ->
										filter.toUnifiedCategoryFilterSelectionOrNull()?.let { selection ->
											selectedKeys.clear()
											state.updateStorageFilter(selection)
										}
										isStorageFilterSheetVisible = false
									},
									categories = categories,
									onCreateCategory = {
										isStorageFilterSheetVisible = false
										showCreateCategoryDialog = true
									},
									onMoveCategory = { category, targetParentCategoryId ->
										runCatching {
											planLocalCategoryMove(
												categories = categories,
												sourceCategory = category,
												targetParentCategory = categories.find { it.id == targetParentCategoryId }
											)
										}.onSuccess { plan ->
											plan.updatedCategories.forEach(passwordViewModel::updateCategory)
										}.onFailure { error ->
											Toast.makeText(
												context,
												context.getString(R.string.save_failed_with_error, error.message ?: ""),
												Toast.LENGTH_SHORT
											).show()
										}
									},
									onMoveCategoryToStorageTarget = { category, target ->
										when (target) {
											is StorageTarget.MonicaLocal -> {
												runCatching {
													planLocalCategoryMove(
														categories = categories,
														sourceCategory = category,
														targetParentCategory = categories.find { it.id == target.categoryId }
													)
												}.onSuccess { plan ->
													plan.updatedCategories.forEach(passwordViewModel::updateCategory)
												}.onFailure { error ->
													Toast.makeText(
														context,
														context.getString(R.string.save_failed_with_error, error.message ?: ""),
														Toast.LENGTH_SHORT
													).show()
												}
											}

											is StorageTarget.Bitwarden -> {
												passwordViewModel.updateCategory(
													category.copy(
														bitwardenVaultId = target.vaultId,
														bitwardenFolderId = target.folderId.orEmpty()
													)
												)
											}

											is StorageTarget.KeePass -> {
												Toast.makeText(
													context,
													context.getString(
														R.string.save_failed_with_error,
														"当前暂不支持将分类移动到 KeePass 数据库"
													),
													Toast.LENGTH_SHORT
												).show()
											}
										}
									},
									getBitwardenFolders = passwordViewModel::getBitwardenFolders,
									getKeePassGroups = localKeePassViewModel::getGroups,
								)
							}
						}
					}
					IconButton(onClick = { isSearchExpanded = true }) {
						Icon(
							imageVector = Icons.Default.Search,
							contentDescription = stringResource(R.string.search),
						)
					}
					Box {
						IconButton(onClick = { isTopActionsMenuExpanded = true }) {
							Icon(
								imageVector = Icons.Default.MoreVert,
								contentDescription = stringResource(R.string.more_options),
							)
						}
						PasswordTopActionsDropdownMenu(
							expanded = isTopActionsMenuExpanded,
							onDismissRequest = { isTopActionsMenuExpanded = false }
						) {
							if (selectedKeePassDatabaseId != null) {
								KeepassRefreshTopActionsMenuItem(
									onClick = {
										isTopActionsMenuExpanded = false
										passwordViewModel.refreshKeePassFromSourceForCurrentContext()
									}
								)
							}
							if (selectedBitwardenVaultId != null) {
								BitwardenSyncTopActionsMenuItem(
									isSyncing = isTopBarSyncing,
									enabled = !isTopBarSyncing && !isBitwardenMaintenanceActionRunning,
									onClick = {
										val vaultId = selectedBitwardenVaultId
										if (!isTopBarSyncing && !isBitwardenMaintenanceActionRunning && vaultId != null) {
											isTopActionsMenuExpanded = false
											bitwardenViewModel.requestManualSync(vaultId)
										}
									}
								)
								BitwardenReunlockTopActionsMenuItem(
									onClick = {
										isTopActionsMenuExpanded = false
										showBitwardenUnlockDialog = true
									}
								)
								BitwardenLockTopActionsMenuItem(
									onClick = {
										isTopActionsMenuExpanded = false
										scope.launch {
											runCatching {
												bitwardenRepository.forceLock(selectedBitwardenVaultId)
											}.onSuccess {
												Toast.makeText(
													context,
													context.getString(R.string.current_database_locked),
													Toast.LENGTH_SHORT
												).show()
											}.onFailure { error ->
												Toast.makeText(
													context,
													context.getString(
														R.string.save_failed_with_error,
														error.message ?: ""
													),
													Toast.LENGTH_SHORT
												).show()
											}
										}
									}
								)
								BitwardenClearCacheTopActionsMenuItem(
									enabled = !isBitwardenMaintenanceActionRunning,
									onClick = {
										val vaultId = selectedBitwardenVaultId
										if (vaultId != null) {
											isTopActionsMenuExpanded = false
											scope.launch {
												runCatching {
													passwordViewModel.getBitwardenVaultCacheRiskSummary(vaultId)
												}.onSuccess { summary ->
													clearCacheRiskSummary = summary
													showClearBitwardenCacheDialog = true
												}.onFailure { error ->
													Toast.makeText(
														context,
														context.getString(
															R.string.bitwarden_clear_cache_failed,
															error.message ?: ""
														),
														Toast.LENGTH_SHORT
													).show()
												}
											}
										}
									}
								)
							}
							CommonPasswordTopActionsMenuItems(
								onDismissMenu = { isTopActionsMenuExpanded = false },
								onShowDisplayOptions = { showDisplayOptionsSheet = true },
								onOpenCommonAccountTemplates = onOpenCommonAccountTemplates,
								onOpenHistory = onOpenHistory,
								onOpenTrash = onOpenTrashPage,
								onOpenArchive = onOpenArchivePage
							)
						}
					}
				}
			)

			if (showDisplayOptionsSheet) {
				PasswordDisplayOptionsSheet(
					stackCardMode = stackCardMode,
					groupMode = appSettings.passwordGroupMode,
					passwordCardDisplayMode = appSettings.passwordCardDisplayMode,
					onDismiss = { showDisplayOptionsSheet = false },
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

			VaultV2NavigationBanner(
				pathLabel = storageFilterLabel,
				currentSectionLabel = currentSectionIndicatorLabel,
				breadcrumbs = pathBreadcrumbs,
				currentFilter = breadcrumbCategoryFilter,
				onNavigate = { filter ->
					filter.toUnifiedCategoryFilterSelectionOrNull()?.let { selection ->
						selectedKeys.clear()
						state.updateStorageFilter(selection)
					}
				},
				onOpenStorageFilter = { isStorageFilterSheetVisible = true },
			)

			val contentPullOffset = pullAction.currentOffset.toInt()
			val listInteractionModifier = Modifier
				.offset { IntOffset(x = 0, y = contentPullOffset) }
				.nestedScroll(pullAction.nestedScrollConnection)
				.then(
					if (sectionedItems.isEmpty()) {
						Modifier.pointerInput(Unit) {
							detectVerticalDragGestures(
								onVerticalDrag = { _, dragAmount ->
									pullAction.onVerticalDrag(dragAmount)
								},
								onDragEnd = pullAction.onDragEnd,
								onDragCancel = pullAction.onDragCancel,
							)
						}
					} else {
						Modifier
					}
				)

			VaultV2List(
				hasVisibleQuickFilters = hasVisibleQuickFilters,
				configuredQuickFilterItems = configuredQuickFilterItems,
				quickFilterChipState = quickFilterBindings.state,
				quickFilterChipCallbacks = quickFilterBindings.callbacks,
				sections = sectionedItems,
				showLoadingIndicator = showVaultLoadingIndicator,
				showEmptyState = showVaultEmptyState,
				listState = listState,
				passwordById = passwordById,
				appSettings = appSettings,
				selectedKeys = selectedKeys,
				modifier = Modifier
					.weight(1f)
					.fillMaxWidth()
					.then(listInteractionModifier),
				onOpenItem = { item ->
					when (item.type) {
						VaultV2ItemType.PASSWORD -> item.passwordEntry?.id?.let(onOpenPassword)
						VaultV2ItemType.AUTHENTICATOR -> {
							val totp = item.totpItem ?: return@VaultV2List
							if (totp.id > 0) {
								onOpenTotp(totp.id)
							} else {
								item.boundPasswordId?.let(onOpenPassword)
							}
						}
						VaultV2ItemType.NOTE -> item.secureItem?.id?.let(onOpenNote)
						VaultV2ItemType.PASSKEY -> {
							item.passkeyEntry?.id?.takeIf { it > 0L }?.let(onOpenPasskey)
						}
						VaultV2ItemType.BANK_CARD -> item.secureItem?.id?.let(onOpenBankCard)
						VaultV2ItemType.DOCUMENT -> item.secureItem?.id?.let(onOpenDocument)
					}
				},
			)
		}

		if (appSettings.categorySelectionUiMode != CategorySelectionUiMode.CHIP_MENU) {
			UnifiedCategoryFilterBottomSheet(
				visible = isStorageFilterSheetVisible,
				onDismiss = { isStorageFilterSheetVisible = false },
				selected = storageSelection,
				onSelect = { selection ->
					selectedKeys.clear()
					state.updateStorageFilter(selection)
					isStorageFilterSheetVisible = false
				},
				categories = categories,
				keepassDatabases = keepassDatabases,
				bitwardenVaults = bitwardenVaults,
				getBitwardenFolders = passwordViewModel::getBitwardenFolders,
				getKeePassGroups = localKeePassViewModel::getGroups,
				quickFilterContent = {
					VaultV2QuickFilterFlow(
						configuredQuickFilterItems = configuredQuickFilterItems,
						chipState = quickFilterBindings.state,
						chipCallbacks = quickFilterBindings.callbacks,
					)
				},
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
				onCreateCategoryWithName = { name -> passwordViewModel.addCategory(name) },
				onCreateBitwardenFolder = { vaultId, name ->
					scope.launch {
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

		if (showBitwardenUnlockDialog && selectedBitwardenVault != null) {
			UnlockVaultDialog(
				email = selectedBitwardenVault.email,
				onUnlock = { masterPassword ->
					showBitwardenUnlockDialog = false
					scope.launch {
						when (val result = bitwardenRepository.unlock(
							selectedBitwardenVault.id,
							masterPassword
						)) {
							is BitwardenRepository.UnlockResult.Success -> {
								Toast.makeText(
									context,
									context.getString(R.string.current_database_unlocked),
									Toast.LENGTH_SHORT
								).show()
							}

							is BitwardenRepository.UnlockResult.Error -> {
								Toast.makeText(
									context,
									result.message,
									Toast.LENGTH_SHORT
								).show()
							}
						}
					}
				},
				onDismiss = { showBitwardenUnlockDialog = false }
			)
		}

		if (showClearBitwardenCacheDialog && selectedBitwardenVaultId != null && clearCacheRiskSummary != null) {
			val vaultId = selectedBitwardenVaultId
			val riskSummary = clearCacheRiskSummary!!
			val hasRisk = riskSummary.hasRisk
			val resetDialogState: () -> Unit = {
				showClearBitwardenCacheDialog = false
				clearCacheRiskSummary = null
			}

			AlertDialog(
				onDismissRequest = {
					if (!isBitwardenMaintenanceActionRunning) {
						resetDialogState()
					}
				},
				title = { Text(stringResource(R.string.bitwarden_clear_cache_confirm_title)) },
				text = {
					Text(
						if (hasRisk) {
							context.getString(
								R.string.bitwarden_clear_cache_confirm_message_with_risk,
								riskSummary.pendingOperationCount,
								riskSummary.passwordLocalModifiedCount,
								riskSummary.secureItemLocalModifiedCount,
								riskSummary.unresolvedConflictCount
							)
						} else {
							context.getString(R.string.bitwarden_clear_cache_confirm_message)
						}
					)
				},
				confirmButton = {
					TextButton(
						enabled = !isBitwardenMaintenanceActionRunning,
						onClick = {
							scope.launch {
								isBitwardenMaintenanceActionRunning = true
								runCatching {
									passwordViewModel.clearBitwardenVaultLocalCache(
										vaultId = vaultId,
										mode = if (hasRisk) {
											BitwardenRepository.CacheClearMode.SAFE_ONLY_SYNCED
										} else {
											BitwardenRepository.CacheClearMode.FULL_FORCE
										}
									)
								}.onSuccess { result ->
									val message = if (hasRisk) {
										context.getString(
											R.string.bitwarden_clear_cache_success_safe,
											result.totalClearedCount,
											result.protectedCipherCount
										)
									} else {
										context.getString(
											R.string.bitwarden_clear_cache_success,
											result.totalClearedCount
										)
									}
									Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
									resetDialogState()
								}.onFailure { error ->
									Toast.makeText(
										context,
										context.getString(
											R.string.bitwarden_clear_cache_failed,
											error.message ?: ""
										),
										Toast.LENGTH_SHORT
									).show()
								}
								isBitwardenMaintenanceActionRunning = false
							}
						}
					) {
						Text(
							stringResource(
								if (hasRisk) R.string.bitwarden_clear_cache_action_safe
								else R.string.bitwarden_clear_cache_action
							)
						)
					}
				},
				dismissButton = {
					Row {
						if (hasRisk) {
							TextButton(
								enabled = !isBitwardenMaintenanceActionRunning,
								onClick = {
									scope.launch {
										isBitwardenMaintenanceActionRunning = true
										runCatching {
											passwordViewModel.clearBitwardenVaultLocalCache(
												vaultId = vaultId,
												mode = BitwardenRepository.CacheClearMode.FULL_FORCE
											)
										}.onSuccess { result ->
											Toast.makeText(
												context,
												context.getString(
													R.string.bitwarden_clear_cache_force_success,
													result.totalClearedCount
												),
												Toast.LENGTH_SHORT
											).show()
											resetDialogState()
										}.onFailure { error ->
											Toast.makeText(
												context,
												context.getString(
													R.string.bitwarden_clear_cache_failed,
													error.message ?: ""
												),
												Toast.LENGTH_SHORT
											).show()
										}
										isBitwardenMaintenanceActionRunning = false
									}
								}
							) {
								Text(stringResource(R.string.bitwarden_clear_cache_action_force))
							}
						}
						TextButton(
							enabled = !isBitwardenMaintenanceActionRunning,
							onClick = { resetDialogState() }
						) {
							Text(stringResource(R.string.cancel))
						}
					}
				}
			)
		}

		if (selectedCount > 0) {
			SelectionActionBar(
				modifier = Modifier
					.align(Alignment.BottomStart)
					.padding(start = 16.dp, bottom = 20.dp),
				selectedCount = selectedCount,
				onExit = { selectedKeys.clear() },
				onSelectAll = {
					selectedKeys.clear()
					selectedKeys.addAll(filteredItems.map { it.key })
				},
				onFavorite = {
					selectedItems.forEach { item ->
						when (item.type) {
							VaultV2ItemType.PASSWORD -> {
								item.passwordEntry?.let { entry ->
									passwordViewModel.toggleFavorite(entry.id, !entry.isFavorite)
								}
							}

							VaultV2ItemType.AUTHENTICATOR -> {
								val id = item.totpItem?.id ?: return@forEach
								if (id > 0) {
									totpViewModel.toggleFavorite(id, !item.isFavorite)
								}
							}

							VaultV2ItemType.NOTE -> {
								item.secureItem?.let { note ->
									val decoded = NoteContentCodec.decodeFromItem(note)
									noteViewModel.updateNote(
										id = note.id,
										content = decoded.content,
										title = note.title,
										tags = decoded.tags,
										isMarkdown = decoded.isMarkdown,
										isFavorite = !note.isFavorite,
										createdAt = note.createdAt,
										categoryId = note.categoryId,
										imagePaths = note.imagePaths,
										keepassDatabaseId = note.keepassDatabaseId,
										keepassGroupPath = note.keepassGroupPath,
										bitwardenVaultId = note.bitwardenVaultId,
										bitwardenFolderId = note.bitwardenFolderId,
									)
								}
							}

							VaultV2ItemType.PASSKEY -> Unit

							VaultV2ItemType.BANK_CARD -> {
								item.secureItem?.id?.let(bankCardViewModel::toggleFavorite)
							}

							VaultV2ItemType.DOCUMENT -> {
								item.secureItem?.id?.let(documentViewModel::toggleFavorite)
							}
						}
					}
					selectedKeys.clear()
				},
				onDelete = {
					selectedItems.forEach { item ->
						when (item.type) {
							VaultV2ItemType.PASSWORD -> {
								item.passwordEntry?.let(passwordViewModel::deletePasswordEntry)
							}

							VaultV2ItemType.AUTHENTICATOR -> {
								item.totpItem?.let { totp ->
									if (totp.id > 0) {
										totpViewModel.deleteTotpItem(totp)
									}
								}
							}

							VaultV2ItemType.NOTE -> {
								item.secureItem?.let(noteViewModel::deleteNote)
							}

							VaultV2ItemType.PASSKEY -> {
								item.passkeyEntry?.let { passkey ->
									scope.launch {
										passkeyViewModel.deletePasskey(passkey)
									}
								}
							}

							VaultV2ItemType.BANK_CARD -> {
								item.secureItem?.id?.let(bankCardViewModel::deleteCard)
							}

							VaultV2ItemType.DOCUMENT -> {
								item.secureItem?.id?.let(documentViewModel::deleteDocument)
							}
						}
					}
					selectedKeys.clear()
				},
			)
		}

	}
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun VaultV2List(
	hasVisibleQuickFilters: Boolean,
	configuredQuickFilterItems: List<PasswordListQuickFilterItem>,
	quickFilterChipState: PasswordQuickFilterChipState,
	quickFilterChipCallbacks: PasswordQuickFilterChipCallbacks,
	sections: List<Pair<String, List<VaultV2Item>>>,
	showLoadingIndicator: Boolean,
	showEmptyState: Boolean,
	listState: LazyListState,
	passwordById: Map<Long, PasswordEntry>,
	appSettings: AppSettings,
	selectedKeys: MutableList<String>,
	modifier: Modifier = Modifier,
	onOpenItem: (VaultV2Item) -> Unit,
) {
	LazyColumn(
		state = listState,
		modifier = modifier,
		contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
	verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		if (hasVisibleQuickFilters) {
			item(key = "filter_row") {
				VaultV2QuickFilterRow(
					configuredQuickFilterItems = configuredQuickFilterItems,
					chipState = quickFilterChipState,
					chipCallbacks = quickFilterChipCallbacks,
				)
			}
		}

		if (sections.isEmpty() && showLoadingIndicator) {
			item(key = "loading") {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 56.dp),
					contentAlignment = Alignment.Center,
				) {
					PasswordListInitialLoadingIndicator()
				}
			}
		}

		if (sections.isEmpty() && showEmptyState) {
			item(key = "empty") {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 40.dp),
					contentAlignment = Alignment.Center,
				) {
					Text(
						text = stringResource(R.string.no_results),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
		}

		sections.forEach { (section, itemsInSection) ->
			item(key = "header:$section") {
				Surface(color = MaterialTheme.colorScheme.surface) {
					Text(
						text = section,
						style = MaterialTheme.typography.titleSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 12.dp, vertical = 4.dp),
					)
				}
			}

			items(itemsInSection, key = { item -> item.key }) { item ->
				val selected = item.key in selectedKeys
				VaultV2ItemCard(
					item = item,
					boundPassword = when (item.type) {
						VaultV2ItemType.AUTHENTICATOR,
						VaultV2ItemType.PASSKEY -> item.boundPasswordId?.let(passwordById::get)
						else -> null
					},
					appSettings = appSettings,
					selected = selected,
					onClick = {
						if (selectedKeys.isNotEmpty()) {
							if (selected) {
								selectedKeys.remove(item.key)
							} else {
								selectedKeys.add(item.key)
							}
						} else {
							onOpenItem(item)
						}
					},
					onLongClick = {
						if (selected) {
							selectedKeys.remove(item.key)
						} else {
							selectedKeys.add(item.key)
						}
					}
				)
			}
		}
	}
}

@Composable
private fun VaultV2NavigationBanner(
	pathLabel: String,
	currentSectionLabel: String,
	breadcrumbs: List<PasswordQuickFolderBreadcrumb>,
	currentFilter: CategoryFilter?,
	onNavigate: (CategoryFilter) -> Unit,
	onOpenStorageFilter: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 12.dp, vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(10.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Surface(
			shape = CircleShape,
			color = MaterialTheme.colorScheme.secondaryContainer,
			contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
			tonalElevation = 2.dp,
			shadowElevation = 0.dp,
			modifier = Modifier.size(32.dp)
		) {
			Box(contentAlignment = Alignment.Center) {
				Text(
					text = currentSectionLabel.ifBlank { "#" },
					style = MaterialTheme.typography.labelLarge,
					fontWeight = FontWeight.SemiBold,
				)
			}
		}

		Box(
			modifier = Modifier
				.weight(1f)
				.clip(RoundedCornerShape(14.dp))
				.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
				.then(
					if (breadcrumbs.isEmpty()) {
						Modifier.clickable(onClick = onOpenStorageFilter)
					} else {
						Modifier
					}
				)
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.horizontalScroll(rememberScrollState())
					.padding(horizontal = 8.dp, vertical = 6.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				if (breadcrumbs.isNotEmpty() && currentFilter != null) {
					breadcrumbs.forEachIndexed { index, crumb ->
						Box(
							modifier = Modifier
								.clip(RoundedCornerShape(10.dp))
								.background(
									if (crumb.isCurrent) {
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
								},
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
				} else {
					Box(
						modifier = Modifier
							.clip(RoundedCornerShape(10.dp))
							.background(MaterialTheme.colorScheme.primaryContainer)
							.padding(horizontal = 10.dp, vertical = 4.dp)
					) {
						Text(
							text = pathLabel,
							style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.onPrimaryContainer,
						)
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultV2ItemCard(
	item: VaultV2Item,
	boundPassword: PasswordEntry?,
	appSettings: AppSettings,
	selected: Boolean,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
) {
	val icon = when (item.type) {
		VaultV2ItemType.PASSWORD -> Icons.Default.Lock
		VaultV2ItemType.AUTHENTICATOR -> Icons.Default.Security
		VaultV2ItemType.NOTE -> Icons.Default.Description
		VaultV2ItemType.PASSKEY -> Icons.Default.VpnKey
		VaultV2ItemType.BANK_CARD -> Icons.Default.CreditCard
		VaultV2ItemType.DOCUMENT -> Icons.Default.Badge
	}
	val unmatchedIconStrategy = UnmatchedIconHandlingStrategy.DEFAULT_ICON

	val passwordIconSource = item.passwordEntry ?: boundPassword
	val totpData = remember(item.totpItem?.itemData) {
		item.totpItem?.itemData?.let { raw ->
			runCatching { Json.decodeFromString<TotpData>(raw) }.getOrNull()
		}
	}
	val iconWebsite = when {
		passwordIconSource != null -> passwordIconSource.website
		item.type == VaultV2ItemType.PASSKEY -> {
			val iconUrl = item.passkeyEntry?.iconUrl?.trim().orEmpty()
			if (iconUrl.isNotBlank()) {
				iconUrl
			} else {
				normalizeVaultV2PasskeyWebsite(item.passkeyEntry?.rpId)
			}
		}
		else -> totpData?.link?.trim().orEmpty()
	}
	val iconTitle = when {
		passwordIconSource != null -> passwordIconSource.title
		item.type == VaultV2ItemType.PASSKEY -> {
			item.passkeyEntry?.rpName?.ifBlank { item.title } ?: item.title
		}
		else -> totpData?.issuer?.ifBlank { item.title } ?: item.title
	}
	val iconAppPackage = when {
		passwordIconSource != null -> passwordIconSource.appPackageName
		else -> totpData?.associatedApp?.trim().orEmpty()
	}
	val customIconType = when {
		passwordIconSource != null -> passwordIconSource.customIconType
		else -> totpData?.customIconType ?: PASSWORD_ICON_TYPE_NONE
	}
	val customIconValue = when {
		passwordIconSource != null -> passwordIconSource.customIconValue
		else -> totpData?.customIconValue
	}
	val simpleIcon = if (customIconType == PASSWORD_ICON_TYPE_SIMPLE) {
		rememberSimpleIconBitmap(
			slug = customIconValue,
			tintColor = MaterialTheme.colorScheme.primary,
			enabled = true
		)
	} else {
		null
	}
	val uploadedIcon = if (customIconType == PASSWORD_ICON_TYPE_UPLOADED) {
		rememberUploadedPasswordIcon(customIconValue)
	} else {
		null
	}
	val autoMatchedSimpleIcon = rememberAutoMatchedSimpleIcon(
		website = iconWebsite,
		title = iconTitle,
		appPackageName = iconAppPackage.ifBlank { null },
		tintColor = MaterialTheme.colorScheme.primary,
		enabled = customIconType == PASSWORD_ICON_TYPE_NONE
	)
	val favicon = if (iconWebsite.isNotBlank()) {
		takagi.ru.monica.autofill_ng.ui.rememberFavicon(
			url = iconWebsite,
			enabled = autoMatchedSimpleIcon.resolved && autoMatchedSimpleIcon.slug == null
		)
	} else {
		null
	}
	val appIcon = if (iconAppPackage.isNotBlank()) {
		takagi.ru.monica.autofill_ng.ui.rememberAppIcon(iconAppPackage)
	} else {
		null
	}
	val authenticatorState = when (item.type) {
		VaultV2ItemType.PASSWORD -> {
			val authenticatorKey = item.passwordEntry?.authenticatorKey.orEmpty()
			val fallbackIssuer = item.passwordEntry?.website.orEmpty().ifBlank {
				item.passwordEntry?.title.orEmpty()
			}
			val fallbackAccountName = item.passwordEntry?.username.orEmpty().ifBlank {
				item.passwordEntry?.title.orEmpty()
			}
			if (authenticatorKey.isBlank()) {
				null
			} else {
				rememberPasswordAuthenticatorDisplayState(
					authenticatorKey = authenticatorKey,
					fallbackIssuer = fallbackIssuer,
					fallbackAccountName = fallbackAccountName,
					timeOffsetSeconds = appSettings.totpTimeOffset,
					smoothProgress = appSettings.validatorSmoothProgress
				)
			}
		}
		VaultV2ItemType.AUTHENTICATOR -> {
			rememberVaultV2TotpDisplayState(
				totpData = totpData,
				timeOffsetSeconds = appSettings.totpTimeOffset,
				smoothProgress = appSettings.validatorSmoothProgress
			)
		}
		else -> null
	}

	Surface(
		shape = RoundedCornerShape(14.dp),
		color = if (selected) {
			MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
		} else {
			MaterialTheme.colorScheme.surfaceContainerLow
		},
		modifier = Modifier
			.fillMaxWidth()
			.combinedClickable(onClick = onClick, onLongClick = onLongClick),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 10.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			when {
				simpleIcon != null -> {
					Image(
						bitmap = simpleIcon,
						contentDescription = null,
						contentScale = ContentScale.Fit,
						modifier = Modifier.size(40.dp).padding(2.dp)
					)
				}
				uploadedIcon != null -> {
					Image(
						bitmap = uploadedIcon,
						contentDescription = null,
						contentScale = ContentScale.Fit,
						modifier = Modifier.size(40.dp).padding(2.dp)
					)
				}
				autoMatchedSimpleIcon.bitmap != null -> {
					Image(
						bitmap = autoMatchedSimpleIcon.bitmap,
						contentDescription = null,
						contentScale = ContentScale.Fit,
						modifier = Modifier.size(40.dp).padding(2.dp)
					)
				}
				favicon != null -> {
					Image(
						bitmap = favicon,
						contentDescription = null,
						contentScale = ContentScale.Crop,
						modifier = Modifier.size(40.dp).clip(CircleShape)
					)
				}
				appIcon != null -> {
					Image(
						bitmap = appIcon,
						contentDescription = null,
						contentScale = ContentScale.Crop,
						modifier = Modifier.size(40.dp).clip(CircleShape)
					)
				}
				shouldShowFallbackSlot(unmatchedIconStrategy) -> {
					UnmatchedIconFallback(
						strategy = unmatchedIconStrategy,
						primaryText = iconWebsite,
						secondaryText = iconTitle,
						defaultIcon = icon,
						iconSize = 40.dp
					)
				}
			}
			Spacer(modifier = Modifier.width(2.dp))

			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				Text(
					text = item.title,
					style = MaterialTheme.typography.titleSmall,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					text = item.subtitle,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				authenticatorState?.let { state ->
					VaultV2AuthenticatorInlineRow(
						state = state,
						smoothProgress = appSettings.validatorSmoothProgress,
					)
				}
			}

			if (item.isFavorite) {
				Icon(
					imageVector = Icons.Outlined.Favorite,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.primary,
					modifier = Modifier.size(16.dp),
				)
				Spacer(modifier = Modifier.width(2.dp))
			}

			Icon(
				imageVector = Icons.AutoMirrored.Filled.ArrowRight,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.size(18.dp),
			)
		}
	}
}

@Composable
private fun VaultV2AuthenticatorInlineRow(
	state: PasswordAuthenticatorDisplayState,
	smoothProgress: Boolean,
) {
	val progressColor = when {
		(state.remainingSeconds ?: Int.MAX_VALUE) <= 5 -> MaterialTheme.colorScheme.error
		else -> MaterialTheme.colorScheme.primary
	}

	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(4.dp),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Icon(
				imageVector = Icons.Default.Security,
				contentDescription = null,
				modifier = Modifier.size(14.dp),
				tint = progressColor,
			)
			Text(
				text = state.code,
				style = MaterialTheme.typography.titleSmall.copy(
					fontWeight = FontWeight.SemiBold,
					fontFamily = FontFamily.Monospace,
				),
				color = progressColor,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f),
			)
			state.remainingSeconds?.let { remaining ->
				Text(
					text = stringResource(R.string.password_card_authenticator_seconds, remaining),
					style = MaterialTheme.typography.labelSmall.copy(
						fontFamily = FontFamily.Monospace,
					),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
		state.progress?.let { progress ->
			val animatedProgress = if (smoothProgress) {
				animateFloatAsState(
					targetValue = progress.coerceIn(0f, 1f),
					animationSpec = tween(durationMillis = 80, easing = LinearEasing),
					label = "vault_v2_auth_progress",
				).value
			} else {
				progress.coerceIn(0f, 1f)
			}
			LinearProgressIndicator(
				progress = { animatedProgress },
				modifier = Modifier
					.fillMaxWidth()
					.height(4.dp),
				color = progressColor,
				trackColor = MaterialTheme.colorScheme.surfaceVariant,
			)
		}
	}
}

@Composable
private fun rememberVaultV2TotpDisplayState(
	totpData: TotpData?,
	timeOffsetSeconds: Int,
	smoothProgress: Boolean,
): PasswordAuthenticatorDisplayState? {
	val normalizedTotpData = remember(totpData) {
		totpData?.let(::normalizeVaultV2TotpData)
	} ?: return null

	val currentTimeMillis by produceState(
		initialValue = System.currentTimeMillis(),
		key1 = normalizedTotpData,
		key2 = timeOffsetSeconds,
		key3 = smoothProgress,
	) {
		while (true) {
			val now = System.currentTimeMillis()
			value = now
			val waitMillis = if (smoothProgress) {
				50L
			} else {
				(1000L - (now % 1000L)).coerceAtLeast(16L)
			}
			delay(waitMillis)
		}
	}
	val currentSeconds = currentTimeMillis / 1000L
	val rawCode = remember(normalizedTotpData, currentSeconds, timeOffsetSeconds) {
		when (normalizedTotpData.otpType) {
			OtpType.HOTP -> TotpGenerator.generateOtp(normalizedTotpData)
			else -> TotpGenerator.generateOtp(
				totpData = normalizedTotpData,
				timeOffset = timeOffsetSeconds,
				currentSeconds = currentSeconds,
			)
		}
	}
	val formattedCode = remember(rawCode, normalizedTotpData.otpType) {
		formatVaultV2OtpCode(rawCode, normalizedTotpData.otpType)
	}

	return if (normalizedTotpData.otpType == OtpType.HOTP) {
		PasswordAuthenticatorDisplayState(
			code = formattedCode,
			remainingSeconds = null,
			progress = null,
		)
	} else {
		val remainingSeconds = remember(normalizedTotpData, currentSeconds, timeOffsetSeconds) {
			TotpGenerator.getRemainingSeconds(
				period = normalizedTotpData.period,
				timeOffset = timeOffsetSeconds,
				currentSeconds = currentSeconds,
			)
		}
		val progress = remember(
			normalizedTotpData,
			currentTimeMillis,
			currentSeconds,
			timeOffsetSeconds,
			smoothProgress,
		) {
			if (smoothProgress) {
				val periodMillis = (normalizedTotpData.period * 1000L).coerceAtLeast(1000L)
				val correctedMillis = currentTimeMillis + (timeOffsetSeconds * 1000L)
				val elapsedInPeriod = ((correctedMillis % periodMillis) + periodMillis) % periodMillis
				(elapsedInPeriod.toFloat() / periodMillis.toFloat()).coerceIn(0f, 1f)
			} else {
				TotpGenerator.getProgress(
					period = normalizedTotpData.period,
					timeOffset = timeOffsetSeconds,
					currentSeconds = currentSeconds,
				).coerceIn(0f, 1f)
			}
		}
		PasswordAuthenticatorDisplayState(
			code = formattedCode,
			remainingSeconds = remainingSeconds,
			progress = progress,
		)
	}
}

private fun normalizeVaultV2TotpData(data: TotpData): TotpData {
	val safePeriod = data.period.takeIf { it > 0 } ?: 30
	val safeDigits = data.digits.coerceIn(4, 10)
	return if (safePeriod == data.period && safeDigits == data.digits) {
		data
	} else {
		data.copy(period = safePeriod, digits = safeDigits)
	}
}

private fun formatVaultV2OtpCode(code: String, otpType: OtpType): String {
	return when (otpType) {
		OtpType.STEAM -> {
			if (code.length == 5) {
				"${code.substring(0, 2)} ${code.substring(2)}"
			} else {
				code
			}
		}
		else -> {
			when (code.length) {
				6 -> "${code.substring(0, 3)} ${code.substring(3)}"
				8 -> "${code.substring(0, 4)} ${code.substring(4)}"
				else -> code
			}
		}
	}
}

private fun vaultV2PlainSingleLine(raw: String): String {
	return raw.lineSequence()
		.map { it.trim() }
		.firstOrNull { it.isNotEmpty() }
		.orEmpty()
}

private fun vaultV2BankCardSubtitle(
	data: BankCardData?,
	fallbackNotes: String,
): String {
	if (data == null) {
		return fallbackNotes.ifBlank { "-" }
	}
	return listOf(
		data.bankName.takeIf { it.isNotBlank() },
		data.cardholderName.takeIf { it.isNotBlank() },
		vaultV2MaskedCardNumber(data.cardNumber),
	).filterNotNull()
		.joinToString(" · ")
		.ifBlank { fallbackNotes.ifBlank { "-" } }
}

private fun vaultV2DocumentSubtitle(
	data: DocumentData?,
	fallbackNotes: String,
): String {
	if (data == null) {
		return fallbackNotes.ifBlank { "-" }
	}
	return listOf(
		data.fullName.takeIf { it.isNotBlank() },
		data.documentNumber.takeIf { it.isNotBlank() }?.let(::vaultV2MaskedDocumentNumber),
		data.issuedBy.takeIf { it.isNotBlank() },
	).filterNotNull()
		.joinToString(" · ")
		.ifBlank { fallbackNotes.ifBlank { "-" } }
}

private fun vaultV2MaskedCardNumber(cardNumber: String): String? {
	val compact = cardNumber.filter { it.isDigit() }
	if (compact.isBlank()) return null
	val tail = compact.takeLast(4)
	return "•••• $tail"
}

private fun vaultV2MaskedDocumentNumber(documentNumber: String): String {
	val trimmed = documentNumber.trim()
	if (trimmed.length <= 4) return trimmed
	return buildString(trimmed.length) {
		repeat(trimmed.length - 4) { append('•') }
		append(trimmed.takeLast(4))
	}
}

private fun dedupeExactVaultItems(items: List<VaultV2Item>): List<VaultV2Item> {
	if (items.size <= 1) return items

	val indexByKey = items.mapIndexed { index, item -> item.key to index }.toMap()
	return items
		.groupBy(::buildVaultV2ExactDisplayKey)
		.values
		.mapNotNull(::pickBestVaultV2Item)
		.sortedBy { indexByKey[it.key] ?: Int.MAX_VALUE }
}

private fun buildVaultV2ExactDisplayKey(item: VaultV2Item): String {
	return when (item.type) {
		VaultV2ItemType.PASSWORD -> {
			val entry = item.passwordEntry
			listOf(
				item.type.name,
				buildVaultV2SourceKey(
					categoryId = entry?.categoryId,
					keepassDatabaseId = entry?.keepassDatabaseId,
					keepassEntryUuid = entry?.keepassEntryUuid,
					keepassGroupPath = entry?.keepassGroupPath,
					bitwardenVaultId = entry?.bitwardenVaultId,
					bitwardenCipherId = entry?.bitwardenCipherId,
					bitwardenFolderId = entry?.bitwardenFolderId,
				),
				normalizeVaultV2ComparableText(item.title),
				normalizeVaultV2ComparableText(entry?.username.orEmpty()),
				normalizeVaultV2Website(entry?.website.orEmpty()),
				entry?.replicaGroupId.orEmpty(),
				entry?.id?.toString().orEmpty(),
			).joinToString("|")
		}

		VaultV2ItemType.AUTHENTICATOR -> {
			val secureItem = item.totpItem
			listOf(
				item.type.name,
				buildVaultV2SourceKey(
					categoryId = secureItem?.categoryId,
					keepassDatabaseId = secureItem?.keepassDatabaseId,
					keepassEntryUuid = secureItem?.keepassEntryUuid,
					keepassGroupPath = secureItem?.keepassGroupPath,
					bitwardenVaultId = secureItem?.bitwardenVaultId,
					bitwardenCipherId = secureItem?.bitwardenCipherId,
					bitwardenFolderId = secureItem?.bitwardenFolderId,
				),
				normalizeVaultV2ComparableText(item.title),
				normalizeVaultV2ComparableText(item.subtitle),
				secureItem?.itemData.orEmpty(),
				secureItem?.notes.orEmpty(),
			).joinToString("|")
		}

		VaultV2ItemType.NOTE,
		VaultV2ItemType.BANK_CARD,
		VaultV2ItemType.DOCUMENT -> {
			val secureItem = item.secureItem
			listOf(
				item.type.name,
				buildVaultV2SourceKey(
					categoryId = secureItem?.categoryId,
					keepassDatabaseId = secureItem?.keepassDatabaseId,
					keepassEntryUuid = secureItem?.keepassEntryUuid,
					keepassGroupPath = secureItem?.keepassGroupPath,
					bitwardenVaultId = secureItem?.bitwardenVaultId,
					bitwardenCipherId = secureItem?.bitwardenCipherId,
					bitwardenFolderId = secureItem?.bitwardenFolderId,
				),
				normalizeVaultV2ComparableText(item.title),
				normalizeVaultV2ComparableText(item.subtitle),
				secureItem?.itemData.orEmpty(),
				secureItem?.notes.orEmpty(),
			).joinToString("|")
		}

		VaultV2ItemType.PASSKEY -> {
			val passkey = item.passkeyEntry
			listOf(
				item.type.name,
				normalizeVaultV2ComparableText(item.title),
				normalizeVaultV2ComparableText(item.subtitle),
				normalizeVaultV2ComparableText(passkey?.rpId.orEmpty()),
				normalizeVaultV2ComparableText(passkey?.userName.orEmpty()),
				normalizeVaultV2ComparableText(passkey?.userDisplayName.orEmpty()),
				normalizeVaultV2ComparableText(passkey?.notes.orEmpty()),
			).joinToString("|")
		}
	}
}

private fun pickBestVaultV2Item(candidates: List<VaultV2Item>): VaultV2Item? {
	return candidates.maxWithOrNull(
		compareBy<VaultV2Item> { it.subtitle.length }
			.thenBy { it.searchText.length }
			.thenBy { if (it.isFavorite) 1 else 0 }
			.thenBy { vaultV2UpdatedAtMillis(it) }
	)
}

private fun buildVaultV2SourceKey(
	categoryId: Long?,
	keepassDatabaseId: Long?,
	keepassEntryUuid: String?,
	keepassGroupPath: String?,
	bitwardenVaultId: Long?,
	bitwardenCipherId: String?,
	bitwardenFolderId: String?,
): String {
	return when {
		keepassDatabaseId != null -> {
			"kp:$keepassDatabaseId:${keepassEntryUuid.orEmpty()}:${keepassGroupPath.orEmpty()}"
		}
		bitwardenVaultId != null -> {
			"bw:$bitwardenVaultId:${bitwardenCipherId.orEmpty()}:${bitwardenFolderId.orEmpty()}"
		}
		else -> "local:${categoryId ?: -1L}"
	}
}

private fun normalizeVaultV2ComparableText(value: String): String {
	return value.trim().lowercase(Locale.ROOT)
}

private fun normalizeVaultV2Website(value: String): String {
	val raw = value.trim()
	if (raw.isEmpty()) return ""
	return raw
		.lowercase(Locale.ROOT)
		.removePrefix("http://")
		.removePrefix("https://")
		.removePrefix("www.")
		.trimEnd('/')
}

private fun vaultV2UpdatedAtMillis(item: VaultV2Item): Long {
	return when (item.type) {
		VaultV2ItemType.PASSWORD -> item.passwordEntry?.updatedAt?.time ?: Long.MIN_VALUE
		VaultV2ItemType.AUTHENTICATOR -> item.totpItem?.updatedAt?.time ?: Long.MIN_VALUE
		VaultV2ItemType.NOTE,
		VaultV2ItemType.BANK_CARD,
		VaultV2ItemType.DOCUMENT -> item.secureItem?.updatedAt?.time ?: Long.MIN_VALUE
		VaultV2ItemType.PASSKEY -> item.passkeyEntry?.lastUsedAt ?: Long.MIN_VALUE
	}
}

private fun normalizeVaultV2PasskeyWebsite(rpId: String?): String {
	val trimmed = rpId?.trim().orEmpty()
	if (trimmed.isBlank()) return ""
	return if (trimmed.contains("://")) {
		trimmed
	} else {
		"https://$trimmed"
	}
}

private fun firstLetterGroup(raw: String): String {
	val first = raw.trim().firstOrNull()?.uppercaseChar() ?: return "#"
	return if (first in 'A'..'Z') first.toString() else "#"
}

private fun normalizedVaultV2SortKey(raw: String): String {
	val trimmed = raw.trim()
	if (trimmed.isEmpty()) return "#"
	val latin = vaultV2Transliterator.transliterate(trimmed)
	val normalized = buildString(latin.length) {
		latin.forEach { char ->
			when {
				char.isLetterOrDigit() -> append(char)
				char.isWhitespace() && isNotEmpty() && last() != ' ' -> append(' ')
			}
		}
	}.trim()
	return normalized.ifEmpty { trimmed }
}

private fun vaultV2LazyIndexForItemIndex(
	sectionLayouts: List<VaultV2SectionLayout>,
	targetItemIndex: Int,
): Int {
	for (section in sectionLayouts) {
		val sectionEnd = section.itemStartIndex + section.items.size
		if (targetItemIndex in section.itemStartIndex until sectionEnd) {
			return section.firstItemLazyIndex + (targetItemIndex - section.itemStartIndex)
		}
	}
	return sectionLayouts.lastOrNull()?.let { lastSection ->
		lastSection.firstItemLazyIndex + lastSection.items.lastIndex.coerceAtLeast(0)
	} ?: 0
}

private fun vaultV2ItemIndexForLazyIndex(
	sectionLayouts: List<VaultV2SectionLayout>,
	lazyIndex: Int,
): Int {
	for (section in sectionLayouts) {
		val headerIndex = section.firstItemLazyIndex - 1
		val lastItemLazyIndex = section.firstItemLazyIndex + section.items.lastIndex.coerceAtLeast(0)
		if (lazyIndex <= headerIndex) {
			return section.itemStartIndex
		}
		if (lazyIndex in section.firstItemLazyIndex..lastItemLazyIndex) {
			return section.itemStartIndex + (lazyIndex - section.firstItemLazyIndex)
		}
	}
	return sectionLayouts.lastOrNull()?.let { lastSection ->
		lastSection.itemStartIndex + lastSection.items.lastIndex.coerceAtLeast(0)
	} ?: 0
}

private fun vaultV2SectionTitleForLazyIndex(
	sectionLayouts: List<VaultV2SectionLayout>,
	lazyIndex: Int,
): String? {
	for (section in sectionLayouts) {
		val headerIndex = section.firstItemLazyIndex - 1
		val lastItemLazyIndex = section.firstItemLazyIndex + section.items.lastIndex.coerceAtLeast(0)
		if (lazyIndex <= headerIndex) {
			return section.title
		}
		if (lazyIndex in section.firstItemLazyIndex..lastItemLazyIndex) {
			return section.title
		}
	}
	return sectionLayouts.lastOrNull()?.title
}
