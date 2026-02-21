package takagi.ru.monica.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.ui.components.BankCardCard
import takagi.ru.monica.ui.components.DocumentCard
import takagi.ru.monica.ui.components.EmptyState
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.components.LoadingIndicator
import takagi.ru.monica.ui.components.M3IdentityVerifyDialog
import takagi.ru.monica.ui.components.UnifiedCategoryFilterBottomSheet
import takagi.ru.monica.ui.components.UnifiedCategoryFilterSelection
import takagi.ru.monica.ui.components.UnifiedMoveCategoryTarget
import takagi.ru.monica.ui.components.UnifiedMoveToCategoryBottomSheet
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.utils.KeePassGroupInfo
import takagi.ru.monica.utils.KeePassKdbxService
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel

enum class CardWalletTab {
    ALL,
    BANK_CARDS,
    DOCUMENTS
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardWalletScreen(
    bankCardViewModel: BankCardViewModel,
    documentViewModel: DocumentViewModel,
    onCardClick: (Long) -> Unit,
    onDocumentClick: (Long) -> Unit,
    currentTab: CardWalletTab,
    onTabSelected: (CardWalletTab) -> Unit,
    onSelectionModeChange: (Boolean, Int, () -> Unit, () -> Unit, () -> Unit, () -> Unit) -> Unit,
    onBankCardSelectionModeChange: (Boolean, Int, () -> Unit, () -> Unit, () -> Unit, () -> Unit, () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val securityManager = remember { SecurityManager(context) }
    val biometricHelper = remember { BiometricHelper(context) }
    val settingsManager = remember { SettingsManager(context) }
    val appSettings by settingsManager.settingsFlow.collectAsState(
        initial = AppSettings(biometricEnabled = false)
    )
    val database = remember { PasswordDatabase.getDatabase(context) }
    val categories by database.categoryDao().getAllCategories().collectAsState(initial = emptyList<Category>())
    val keepassDatabases by database.localKeePassDatabaseDao().getAllDatabases().collectAsState(initial = emptyList())
    val bitwardenRepository = remember { BitwardenRepository.getInstance(context) }
    val keePassService = remember {
        KeePassKdbxService(
            context,
            database.localKeePassDatabaseDao(),
            securityManager
        )
    }
    val keepassGroupFlows = remember {
        mutableMapOf<Long, MutableStateFlow<List<KeePassGroupInfo>>>()
    }
    val getKeePassGroups: (Long) -> Flow<List<KeePassGroupInfo>> = remember {
        { databaseId ->
            val flow = keepassGroupFlows.getOrPut(databaseId) {
                MutableStateFlow(emptyList())
            }
            if (flow.value.isEmpty()) {
                scope.launch {
                    flow.value = keePassService.listGroups(databaseId).getOrDefault(emptyList())
                }
            }
            flow
        }
    }

    val cards by bankCardViewModel.allCards.collectAsState(initial = emptyList())
    val documents by documentViewModel.allDocuments.collectAsState(initial = emptyList())
    val bankLoading by bankCardViewModel.isLoading.collectAsState()
    val documentLoading by documentViewModel.isLoading.collectAsState()
    var bitwardenVaults by remember { mutableStateOf<List<BitwardenVault>>(emptyList()) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var showCategoryFilterDialog by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf<UnifiedCategoryFilterSelection>(UnifiedCategoryFilterSelection.All) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<SecureItem?>(null) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showVerifyDialog by remember { mutableStateOf(false) }
    var verifyPassword by remember { mutableStateOf("") }
    var verifyPasswordError by remember { mutableStateOf(false) }
    var verifyDeleteIds by remember { mutableStateOf(setOf<Long>()) }
    var showBatchMoveCategoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        bankCardViewModel.syncAllKeePassCards()
        documentViewModel.syncAllKeePassDocuments()
        bitwardenVaults = bitwardenRepository.getAllVaults()
    }

    val allItems = remember(cards, documents) {
        (cards + documents).sortedWith(
            compareByDescending<SecureItem> { it.isFavorite }
                .thenByDescending { it.updatedAt.time }
                .thenBy { it.sortOrder }
        )
    }

    val performDelete: (Set<Long>) -> Unit = { ids ->
        allItems.filter { it.id in ids }.forEach { item ->
            when (item.itemType) {
                ItemType.BANK_CARD -> bankCardViewModel.deleteCard(item.id)
                ItemType.DOCUMENT -> documentViewModel.deleteDocument(item.id)
                else -> Unit
            }
        }
        isSelectionMode = false
        selectedIds = emptySet()
    }

    val requestDeleteVerification: (Set<Long>) -> Unit = requestDeleteVerification@{ ids ->
        if (ids.isEmpty()) return@requestDeleteVerification
        if (appSettings.disablePasswordVerification) {
            performDelete(ids)
            return@requestDeleteVerification
        }
        verifyDeleteIds = ids
        verifyPassword = ""
        verifyPasswordError = false
        showVerifyDialog = true
    }

    fun performBatchMove(target: UnifiedMoveCategoryTarget) {
        val targetCategoryId: Long? = when (target) {
            UnifiedMoveCategoryTarget.Uncategorized -> null
            is UnifiedMoveCategoryTarget.MonicaCategory -> target.categoryId
            else -> null
        }
        val targetKeepassDatabaseId: Long? = when (target) {
            is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> target.databaseId
            is UnifiedMoveCategoryTarget.KeePassGroupTarget -> target.databaseId
            else -> null
        }
        val targetKeepassGroupPath: String? = when (target) {
            is UnifiedMoveCategoryTarget.KeePassGroupTarget -> target.groupPath
            else -> null
        }
        val targetBitwardenVaultId: Long? = when (target) {
            is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> target.vaultId
            is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> target.vaultId
            else -> null
        }
        val targetBitwardenFolderId: String? = when (target) {
            is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> target.folderId
            else -> null
        }

        val selectedItems = allItems.filter { selectedIds.contains(it.id) }
        selectedItems.forEach { item ->
            when (item.itemType) {
                ItemType.BANK_CARD -> {
                    bankCardViewModel.moveCardToStorage(
                        id = item.id,
                        categoryId = targetCategoryId,
                        keepassDatabaseId = targetKeepassDatabaseId,
                        keepassGroupPath = targetKeepassGroupPath,
                        bitwardenVaultId = targetBitwardenVaultId,
                        bitwardenFolderId = targetBitwardenFolderId
                    )
                }
                ItemType.DOCUMENT -> {
                    documentViewModel.moveDocumentToStorage(
                        id = item.id,
                        categoryId = targetCategoryId,
                        keepassDatabaseId = targetKeepassDatabaseId,
                        keepassGroupPath = targetKeepassGroupPath,
                        bitwardenVaultId = targetBitwardenVaultId,
                        bitwardenFolderId = targetBitwardenFolderId
                    )
                }
                else -> Unit
            }
        }

        android.widget.Toast.makeText(
            context,
            context.getString(R.string.selected_items, selectedItems.size),
            android.widget.Toast.LENGTH_SHORT
        ).show()
        showBatchMoveCategoryDialog = false
        isSelectionMode = false
        selectedIds = emptySet()
    }

    val filteredItems = remember(allItems, currentTab, searchQuery, selectedCategoryFilter) {
        val query = searchQuery.trim()
        allItems
            .asSequence()
            .filter { item ->
                when (currentTab) {
                    CardWalletTab.ALL -> item.itemType == ItemType.BANK_CARD || item.itemType == ItemType.DOCUMENT
                    CardWalletTab.BANK_CARDS -> item.itemType == ItemType.BANK_CARD
                    CardWalletTab.DOCUMENTS -> item.itemType == ItemType.DOCUMENT
                }
            }
            .filter { item ->
                itemMatchesCategoryFilter(item, selectedCategoryFilter)
            }
            .filter { item ->
                if (query.isBlank()) {
                    true
                } else {
                    itemMatchesSearch(
                        item = item,
                        query = query,
                        bankCardViewModel = bankCardViewModel,
                        documentViewModel = documentViewModel
                    )
                }
            }
            .toList()
    }

    LaunchedEffect(filteredItems) {
        if (selectedIds.isEmpty()) return@LaunchedEffect
        val validIds = filteredItems.map { it.id }.toSet()
        selectedIds = selectedIds.intersect(validIds)
        if (selectedIds.isEmpty()) {
            isSelectionMode = false
        }
    }

    val exitSelection = {
        isSelectionMode = false
        selectedIds = emptySet()
    }
    val selectAll = {
        selectedIds = if (selectedIds.size == filteredItems.size) {
            emptySet()
        } else {
            filteredItems.map { it.id }.toSet()
        }
    }
    val deleteSelected = {
        if (selectedIds.isNotEmpty()) {
            showBatchDeleteDialog = true
        }
    }
    val moveSelected = {
        if (selectedIds.isNotEmpty()) {
            showBatchMoveCategoryDialog = true
        }
    }
    val favoriteSelected = {
        val selectedItems = allItems.filter { it.id in selectedIds }
        if (selectedItems.isNotEmpty()) {
            val shouldFavorite = selectedItems.any { !it.isFavorite }
            selectedItems.forEach { item ->
                if (item.isFavorite == shouldFavorite) return@forEach
                when (item.itemType) {
                    ItemType.BANK_CARD -> bankCardViewModel.toggleFavorite(item.id)
                    ItemType.DOCUMENT -> documentViewModel.toggleFavorite(item.id)
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(isSelectionMode, selectedIds, filteredItems) {
        onBankCardSelectionModeChange(
            isSelectionMode,
            selectedIds.size,
            exitSelection,
            selectAll,
            deleteSelected,
            favoriteSelected,
            moveSelected
        )
        onSelectionModeChange(
            isSelectionMode,
            selectedIds.size,
            exitSelection,
            selectAll,
            moveSelected,
            deleteSelected
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        ExpressiveTopBar(
            title = stringResource(R.string.nav_card_wallet),
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            isSearchExpanded = isSearchExpanded,
            onSearchExpandedChange = { expanded ->
                isSearchExpanded = expanded
                if (!expanded) {
                    searchQuery = ""
                }
            },
            searchHint = stringResource(R.string.topbar_search_hint),
            actions = {
                IconButton(onClick = { showCategoryFilterDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = stringResource(R.string.category)
                    )
                }
                Box {
                    IconButton(onClick = { showTypeMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.category)
                        )
                    }
                    DropdownMenu(
                        expanded = showTypeMenu,
                        onDismissRequest = { showTypeMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.filter_all)) },
                            onClick = {
                                showTypeMenu = false
                                onTabSelected(CardWalletTab.ALL)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nav_bank_cards_short)) },
                            onClick = {
                                showTypeMenu = false
                                onTabSelected(CardWalletTab.BANK_CARDS)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nav_documents_short)) },
                            onClick = {
                                showTypeMenu = false
                                onTabSelected(CardWalletTab.DOCUMENTS)
                            }
                        )
                    }
                }
                IconButton(onClick = { isSearchExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search)
                    )
                }
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                bankLoading || documentLoading -> LoadingIndicator()
                filteredItems.isEmpty() -> {
                    when (currentTab) {
                        CardWalletTab.BANK_CARDS -> EmptyState(
                            icon = Icons.Default.CreditCard,
                            title = stringResource(R.string.no_bank_cards_title),
                            description = stringResource(R.string.no_bank_cards_description)
                        )

                        CardWalletTab.DOCUMENTS -> EmptyState(
                            icon = Icons.Default.Description,
                            title = stringResource(R.string.no_documents_title),
                            description = stringResource(R.string.no_documents_description)
                        )

                        CardWalletTab.ALL -> EmptyState(
                            icon = Icons.Default.CreditCard,
                            title = stringResource(R.string.nav_card_wallet),
                            description = if (searchQuery.isBlank()) {
                                stringResource(R.string.no_bank_cards_description)
                            } else {
                                stringResource(R.string.passkey_no_search_results_hint)
                            }
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            val isSelected = selectedIds.contains(item.id)
                            when (item.itemType) {
                                ItemType.BANK_CARD -> BankCardCard(
                                    item = item,
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                            if (selectedIds.isEmpty()) isSelectionMode = false
                                        } else {
                                            onCardClick(item.id)
                                        }
                                    },
                                    onDelete = { itemToDelete = item },
                                    onToggleFavorite = { id, _ -> bankCardViewModel.toggleFavorite(id) },
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedIds = setOf(item.id)
                                        } else {
                                            selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                            if (selectedIds.isEmpty()) isSelectionMode = false
                                        }
                                    },
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                ItemType.DOCUMENT -> DocumentCard(
                                    item = item,
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                            if (selectedIds.isEmpty()) isSelectionMode = false
                                        } else {
                                            onDocumentClick(item.id)
                                        }
                                    },
                                    onDelete = { itemToDelete = item },
                                    onToggleFavorite = { id, _ -> documentViewModel.toggleFavorite(id) },
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedIds = setOf(item.id)
                                        } else {
                                            selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                            if (selectedIds.isEmpty()) isSelectionMode = false
                                        }
                                    },
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                else -> Unit
                            }
                        }
                        item { Box(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text(
                    stringResource(
                        if (item.itemType == ItemType.BANK_CARD) {
                            R.string.delete_bank_card_title
                        } else {
                            R.string.delete_document_title
                        }
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (item.itemType == ItemType.BANK_CARD) {
                            R.string.delete_bank_card_message
                        } else {
                            R.string.delete_document_message
                        },
                        item.title
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    requestDeleteVerification(setOf(item.id))
                    itemToDelete = null
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text(stringResource(R.string.batch_delete_title)) },
            text = { Text(stringResource(R.string.batch_delete_message, selectedIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    requestDeleteVerification(selectedIds)
                    showBatchDeleteDialog = false
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showVerifyDialog) {
        val biometricAction = if (
            activity != null &&
            appSettings.biometricEnabled &&
            biometricHelper.isBiometricAvailable()
        ) {
            {
                biometricHelper.authenticate(
                    activity = activity,
                    title = context.getString(R.string.verify_identity),
                    subtitle = context.getString(R.string.verify_to_delete),
                    onSuccess = {
                        performDelete(verifyDeleteIds)
                        verifyDeleteIds = emptySet()
                        verifyPassword = ""
                        verifyPasswordError = false
                        showVerifyDialog = false
                    },
                    onError = { error ->
                        android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onFailed = {}
                )
            }
        } else {
            null
        }

        M3IdentityVerifyDialog(
            title = stringResource(R.string.verify_identity),
            message = if (verifyDeleteIds.size > 1) {
                stringResource(R.string.batch_delete_message, verifyDeleteIds.size)
            } else {
                stringResource(R.string.verify_identity_to_delete)
            },
            passwordValue = verifyPassword,
            onPasswordChange = {
                verifyPassword = it
                verifyPasswordError = false
            },
            onDismiss = {
                showVerifyDialog = false
                verifyDeleteIds = emptySet()
                verifyPassword = ""
                verifyPasswordError = false
            },
            onConfirm = {
                scope.launch {
                    if (securityManager.verifyMasterPassword(verifyPassword)) {
                        performDelete(verifyDeleteIds)
                        verifyDeleteIds = emptySet()
                        verifyPassword = ""
                        verifyPasswordError = false
                        showVerifyDialog = false
                    } else {
                        verifyPasswordError = true
                    }
                }
            },
            confirmText = stringResource(R.string.delete),
            destructiveConfirm = true,
            isPasswordError = verifyPasswordError,
            passwordErrorText = stringResource(R.string.current_password_incorrect),
            onBiometricClick = biometricAction,
            biometricHintText = if (biometricAction == null) {
                context.getString(R.string.biometric_not_available)
            } else {
                null
            }
        )
    }

    UnifiedCategoryFilterBottomSheet(
        visible = showCategoryFilterDialog,
        onDismiss = { showCategoryFilterDialog = false },
        selected = selectedCategoryFilter,
        onSelect = { selection -> selectedCategoryFilter = selection },
        categories = categories,
        keepassDatabases = keepassDatabases,
        bitwardenVaults = bitwardenVaults,
        getBitwardenFolders = { vaultId -> database.bitwardenFolderDao().getFoldersByVaultFlow(vaultId) },
        getKeePassGroups = getKeePassGroups
    )

    UnifiedMoveToCategoryBottomSheet(
        visible = showBatchMoveCategoryDialog,
        onDismiss = { showBatchMoveCategoryDialog = false },
        categories = categories,
        keepassDatabases = keepassDatabases,
        bitwardenVaults = bitwardenVaults,
        getBitwardenFolders = { vaultId -> database.bitwardenFolderDao().getFoldersByVaultFlow(vaultId) },
        getKeePassGroups = getKeePassGroups,
        onTargetSelected = ::performBatchMove
    )
}

private fun itemMatchesSearch(
    item: SecureItem,
    query: String,
    bankCardViewModel: BankCardViewModel,
    documentViewModel: DocumentViewModel
): Boolean {
    if (item.title.contains(query, ignoreCase = true) || item.notes.contains(query, ignoreCase = true)) {
        return true
    }
    return when (item.itemType) {
        ItemType.BANK_CARD -> bankCardViewModel.parseCardData(item.itemData)?.let { card ->
            card.cardNumber.contains(query, ignoreCase = true) ||
                card.bankName.contains(query, ignoreCase = true) ||
                card.cardholderName.contains(query, ignoreCase = true)
        } ?: false

        ItemType.DOCUMENT -> documentViewModel.parseDocumentData(item.itemData)?.let { document ->
            document.documentNumber.contains(query, ignoreCase = true) ||
                document.fullName.contains(query, ignoreCase = true) ||
                document.issuedBy.contains(query, ignoreCase = true) ||
                document.nationality.contains(query, ignoreCase = true)
        } ?: false

        else -> false
    }
}

private fun itemMatchesCategoryFilter(
    item: SecureItem,
    filter: UnifiedCategoryFilterSelection
): Boolean {
    val vaultId = item.bitwardenVaultId
    val folderId = item.bitwardenFolderId
    val keePassId = item.keepassDatabaseId
    val groupPath = item.keepassGroupPath
    val isLocal = vaultId == null && keePassId == null
    return when (filter) {
        UnifiedCategoryFilterSelection.All -> true
        UnifiedCategoryFilterSelection.Local -> isLocal
        UnifiedCategoryFilterSelection.Starred -> item.isFavorite
        UnifiedCategoryFilterSelection.Uncategorized -> item.categoryId == null
        UnifiedCategoryFilterSelection.LocalStarred -> isLocal && item.isFavorite
        UnifiedCategoryFilterSelection.LocalUncategorized -> isLocal && item.categoryId == null
        is UnifiedCategoryFilterSelection.Custom -> item.categoryId == filter.categoryId
        is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> vaultId == filter.vaultId
        is UnifiedCategoryFilterSelection.BitwardenFolderFilter ->
            vaultId == filter.vaultId && folderId == filter.folderId
        is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter ->
            vaultId == filter.vaultId && item.isFavorite
        is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter ->
            vaultId == filter.vaultId && item.categoryId == null
        is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> keePassId == filter.databaseId
        is UnifiedCategoryFilterSelection.KeePassGroupFilter ->
            keePassId == filter.databaseId && groupPath == filter.groupPath
        is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter ->
            keePassId == filter.databaseId && item.isFavorite
        is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter ->
            keePassId == filter.databaseId && item.categoryId == null
    }
}
