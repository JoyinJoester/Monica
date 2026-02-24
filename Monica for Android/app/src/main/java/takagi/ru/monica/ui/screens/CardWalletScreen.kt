package takagi.ru.monica.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
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
import takagi.ru.monica.ui.components.PullActionVisualState
import takagi.ru.monica.ui.components.PullGestureIndicator
import takagi.ru.monica.ui.components.UnifiedCategoryFilterBottomSheet
import takagi.ru.monica.ui.components.UnifiedCategoryFilterSelection
import takagi.ru.monica.ui.components.UnifiedMoveAction
import takagi.ru.monica.ui.components.UnifiedMoveCategoryTarget
import takagi.ru.monica.ui.components.UnifiedMoveToCategoryBottomSheet
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.utils.KeePassGroupInfo
import takagi.ru.monica.utils.KeePassKdbxService
import takagi.ru.monica.utils.decodeKeePassPathForDisplay
import takagi.ru.monica.utils.SavedCategoryFilterState
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
    val density = LocalDensity.current
    val securityManager = remember { SecurityManager(context) }
    val biometricHelper = remember { BiometricHelper(context) }
    val settingsManager = remember { SettingsManager(context) }
    val appSettings by settingsManager.settingsFlow.collectAsState(
        initial = AppSettings(biometricEnabled = false)
    )
    val savedCategoryFilterState by settingsManager
        .categoryFilterStateFlow(SettingsManager.CategoryFilterScope.CARD_WALLET)
        .collectAsState(initial = null)
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
    var selectedCategoryFilter by rememberSaveable(stateSaver = cardWalletCategoryFilterSaver) {
        mutableStateOf<UnifiedCategoryFilterSelection>(UnifiedCategoryFilterSelection.All)
    }
    var hasRestoredCategoryFilter by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<SecureItem?>(null) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showVerifyDialog by remember { mutableStateOf(false) }
    var verifyPassword by remember { mutableStateOf("") }
    var verifyPasswordError by remember { mutableStateOf(false) }
    var verifyDeleteIds by remember { mutableStateOf(setOf<Long>()) }
    var showBatchMoveCategoryDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Card wallet keeps pull-to-search; disable pull-to-sync on Bitwarden filters.
    val isBitwardenDatabaseView = false && when (selectedCategoryFilter) {
        is UnifiedCategoryFilterSelection.BitwardenVaultFilter,
        is UnifiedCategoryFilterSelection.BitwardenFolderFilter,
        is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter,
        is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> true
        else -> false
    }
    val selectedBitwardenVaultId = when (val filter = selectedCategoryFilter) {
        is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> filter.vaultId
        is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> filter.vaultId
        is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> filter.vaultId
        is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> filter.vaultId
        else -> null
    }
    val bitwardenViewModel: takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel = viewModel()
    val bitwardenSyncStatusByVault by bitwardenViewModel.syncStatusByVault.collectAsState()
    val isTopBarSyncing = selectedBitwardenVaultId?.let { vaultId ->
        bitwardenSyncStatusByVault[vaultId]?.isRunning == true
    } == true
    var currentOffset by remember { mutableStateOf(0f) }
    val searchTriggerDistance = remember(density, isBitwardenDatabaseView) {
        with(density) { (if (isBitwardenDatabaseView) 40.dp else 72.dp).toPx() }
    }
    val syncTriggerDistance = remember(density) { with(density) { 72.dp.toPx() } }
    val maxDragDistance = remember(density) { with(density) { 100.dp.toPx() } }
    val syncHoldMillis = 500L
    var hasVibrated by remember { mutableStateOf(false) }
    var hasSyncStageVibrated by remember { mutableStateOf(false) }
    var syncHintArmed by remember { mutableStateOf(false) }
    var isBitwardenSyncing by remember { mutableStateOf(false) }
    var lockPullUntilSyncFinished by remember { mutableStateOf(false) }
    var canRunBitwardenSync by remember { mutableStateOf(false) }
    var showSyncFeedback by remember { mutableStateOf(false) }
    var syncFeedbackMessage by remember { mutableStateOf("") }
    var syncFeedbackIsSuccess by remember { mutableStateOf(false) }
    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }
    LaunchedEffect(Unit) {
        bankCardViewModel.syncAllKeePassCards()
        documentViewModel.syncAllKeePassDocuments()
        bitwardenVaults = bitwardenRepository.getAllVaults()
    }

    LaunchedEffect(savedCategoryFilterState, hasRestoredCategoryFilter) {
        if (hasRestoredCategoryFilter) return@LaunchedEffect
        // If state was already restored from SaveableStateRegistry, do not override it.
        if (selectedCategoryFilter != UnifiedCategoryFilterSelection.All) {
            hasRestoredCategoryFilter = true
            return@LaunchedEffect
        }
        val persisted = savedCategoryFilterState ?: return@LaunchedEffect
        selectedCategoryFilter = decodeCardWalletCategoryFilter(persisted)
        hasRestoredCategoryFilter = true
    }

    LaunchedEffect(selectedCategoryFilter, hasRestoredCategoryFilter) {
        if (!hasRestoredCategoryFilter) return@LaunchedEffect
        settingsManager.updateCategoryFilterState(
            scope = SettingsManager.CategoryFilterScope.CARD_WALLET,
            state = encodeCardWalletCategoryFilter(selectedCategoryFilter)
        )
    }

    LaunchedEffect(selectedBitwardenVaultId) {
        selectedBitwardenVaultId?.let { vaultId ->
            bitwardenViewModel.requestPageEnterAutoSync(vaultId)
        }
    }

    suspend fun resolveSyncableVaultId(): Long? {
        val activeVault = bitwardenRepository.getActiveVault() ?: run {
            canRunBitwardenSync = false
            return null
        }
        val unlocked = bitwardenRepository.isVaultUnlocked(activeVault.id)
        canRunBitwardenSync = unlocked
        return if (unlocked) activeVault.id else null
    }

    fun vibratePullThreshold(isSyncStage: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (isSyncStage && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                vibrator?.vibrate(
                    android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_DOUBLE_CLICK)
                )
            } else {
                vibrator?.vibrate(android.os.VibrationEffect.createWaveform(takagi.ru.monica.util.VibrationPatterns.TICK, -1))
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(if (isSyncStage) 36 else 20)
        }
    }

    fun updatePullThresholdHaptics(oldOffset: Float, newOffset: Float) {
        if (oldOffset < searchTriggerDistance && newOffset >= searchTriggerDistance && !hasVibrated) {
            hasVibrated = true
            vibratePullThreshold(isSyncStage = false)
        } else if (newOffset < searchTriggerDistance) {
            hasVibrated = false
        }

        if (!isBitwardenDatabaseView) {
            hasSyncStageVibrated = false
            return
        }

        if (oldOffset < syncTriggerDistance && newOffset >= syncTriggerDistance && !hasSyncStageVibrated) {
            hasSyncStageVibrated = true
            vibratePullThreshold(isSyncStage = true)
        } else if (newOffset < syncTriggerDistance) {
            hasSyncStageVibrated = false
        }
    }

    suspend fun collapsePullOffsetSmoothly() {
        if (currentOffset <= 0.5f) {
            currentOffset = 0f
            return
        }
        Animatable(currentOffset).animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = 180,
                easing = androidx.compose.animation.core.LinearOutSlowInEasing
            )
        ) {
            currentOffset = value
        }
    }

    fun onPullRelease(): Boolean {
        if (isBitwardenDatabaseView && syncHintArmed && !isBitwardenSyncing) {
            syncHintArmed = false
            isBitwardenSyncing = true
            lockPullUntilSyncFinished = true
            currentOffset = syncTriggerDistance
            scope.launch {
                val vaultId = resolveSyncableVaultId()
                if (vaultId == null) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.pull_sync_requires_bitwarden_login),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    isBitwardenSyncing = false
                    lockPullUntilSyncFinished = false
                    hasVibrated = false
                    hasSyncStageVibrated = false
                    collapsePullOffsetSmoothly()
                    return@launch
                }

                val syncResult = bitwardenRepository.sync(vaultId)
                when (syncResult) {
                    is BitwardenRepository.SyncResult.Success -> {
                        syncFeedbackIsSuccess = true
                        syncFeedbackMessage = context.getString(R.string.pull_sync_success)
                        showSyncFeedback = true
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.pull_sync_success),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    is BitwardenRepository.SyncResult.Error -> {
                        syncFeedbackIsSuccess = false
                        syncFeedbackMessage = context.getString(R.string.sync_status_failed_full)
                        showSyncFeedback = true
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.sync_status_failed_full) + ": " + syncResult.message,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    is BitwardenRepository.SyncResult.EmptyVaultBlocked -> {
                        syncFeedbackIsSuccess = false
                        syncFeedbackMessage = context.getString(R.string.sync_status_failed_full)
                        showSyncFeedback = true
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.sync_status_failed_full) + ": " + syncResult.reason,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                isBitwardenSyncing = false
                lockPullUntilSyncFinished = false
                hasVibrated = false
                hasSyncStageVibrated = false
                collapsePullOffsetSmoothly()
                kotlinx.coroutines.delay(1400)
                showSyncFeedback = false
            }
            return true
        }

        if (currentOffset >= searchTriggerDistance) {
            isSearchExpanded = true
            hasVibrated = false
        }
        return false
    }

    LaunchedEffect(isBitwardenDatabaseView) {
        if (isBitwardenDatabaseView) {
            resolveSyncableVaultId()
        } else {
            canRunBitwardenSync = false
            syncHintArmed = false
            isBitwardenSyncing = false
            lockPullUntilSyncFinished = false
            showSyncFeedback = false
            currentOffset = 0f
            hasVibrated = false
            hasSyncStageVibrated = false
        }
    }

    LaunchedEffect(currentOffset >= syncTriggerDistance, isBitwardenDatabaseView, isBitwardenSyncing) {
        if (isBitwardenDatabaseView && currentOffset >= syncTriggerDistance && !isBitwardenSyncing) {
            resolveSyncableVaultId()
        }
    }

    LaunchedEffect(currentOffset, isBitwardenDatabaseView, canRunBitwardenSync, isBitwardenSyncing) {
        if (isBitwardenDatabaseView && currentOffset >= syncTriggerDistance && canRunBitwardenSync && !isBitwardenSyncing) {
            kotlinx.coroutines.delay(syncHoldMillis)
            if (isBitwardenDatabaseView && currentOffset >= syncTriggerDistance && canRunBitwardenSync && !isBitwardenSyncing) {
                syncHintArmed = true
            }
        } else {
            syncHintArmed = false
        }
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            currentOffset = 0f
            hasVibrated = false
            hasSyncStageVibrated = false
            syncHintArmed = false
        }
    }

    val allItems = remember(cards, documents) {
        (cards + documents).sortedWith(
            compareByDescending<SecureItem> { it.isFavorite }
                .thenByDescending { it.updatedAt.time }
                .thenBy { it.sortOrder }
        )
    }

    val nestedScrollConnection = remember(isBitwardenDatabaseView) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (lockPullUntilSyncFinished) {
                    return available
                }
                if (currentOffset > 0 && available.y < 0) {
                    val newOffset = (currentOffset + available.y).coerceAtLeast(0f)
                    val consumed = currentOffset - newOffset
                    currentOffset = newOffset
                    return Offset(0f, -consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (lockPullUntilSyncFinished) {
                    return available
                }
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    val delta = available.y * 0.5f
                    val newOffset = (currentOffset + delta).coerceAtMost(maxDragDistance)
                    val oldOffset = currentOffset
                    currentOffset = newOffset
                    updatePullThresholdHaptics(oldOffset = oldOffset, newOffset = newOffset)
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val syncStarted = onPullRelease()
                if (!syncStarted && !lockPullUntilSyncFinished) {
                    collapsePullOffsetSmoothly()
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!lockPullUntilSyncFinished && currentOffset > 0f) {
                    val syncStarted = onPullRelease()
                    if (!syncStarted && !lockPullUntilSyncFinished) {
                        collapsePullOffsetSmoothly()
                    }
                }
                return Velocity.Zero
            }
        }
    }

    val performDelete: (Set<Long>) -> Unit = { ids ->
        val itemsToDelete = allItems.filter { it.id in ids }
        val affectedVaultIds = itemsToDelete.mapNotNull { it.bitwardenVaultId }.toSet()
        itemsToDelete.forEach { item ->
            when (item.itemType) {
                ItemType.BANK_CARD -> bankCardViewModel.deleteCard(item.id)
                ItemType.DOCUMENT -> documentViewModel.deleteDocument(item.id)
                else -> Unit
            }
        }
        affectedVaultIds.forEach(bitwardenViewModel::requestLocalMutationSync)
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

    fun performBatchMove(target: UnifiedMoveCategoryTarget, action: UnifiedMoveAction) {
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
            when {
                action == UnifiedMoveAction.COPY && item.itemType == ItemType.BANK_CARD -> {
                    val cardData = bankCardViewModel.parseCardData(item.itemData) ?: return@forEach
                    bankCardViewModel.addCard(
                        title = item.title,
                        cardData = cardData,
                        notes = item.notes,
                        isFavorite = item.isFavorite,
                        imagePaths = item.imagePaths,
                        categoryId = targetCategoryId,
                        keepassDatabaseId = targetKeepassDatabaseId,
                        keepassGroupPath = targetKeepassGroupPath,
                        bitwardenVaultId = targetBitwardenVaultId,
                        bitwardenFolderId = targetBitwardenFolderId
                    )
                }
                action == UnifiedMoveAction.COPY && item.itemType == ItemType.DOCUMENT -> {
                    val documentData = documentViewModel.parseDocumentData(item.itemData) ?: return@forEach
                    documentViewModel.addDocument(
                        title = item.title,
                        documentData = documentData,
                        notes = item.notes,
                        isFavorite = item.isFavorite,
                        imagePaths = item.imagePaths,
                        categoryId = targetCategoryId,
                        keepassDatabaseId = targetKeepassDatabaseId,
                        keepassGroupPath = targetKeepassGroupPath,
                        bitwardenVaultId = targetBitwardenVaultId,
                        bitwardenFolderId = targetBitwardenFolderId
                    )
                }
                item.itemType == ItemType.BANK_CARD -> {
                    bankCardViewModel.moveCardToStorage(
                        id = item.id,
                        categoryId = targetCategoryId,
                        keepassDatabaseId = targetKeepassDatabaseId,
                        keepassGroupPath = targetKeepassGroupPath,
                        bitwardenVaultId = targetBitwardenVaultId,
                        bitwardenFolderId = targetBitwardenFolderId
                    )
                }
                item.itemType == ItemType.DOCUMENT -> {
                    documentViewModel.moveDocumentToStorage(
                        id = item.id,
                        categoryId = targetCategoryId,
                        keepassDatabaseId = targetKeepassDatabaseId,
                        keepassGroupPath = targetKeepassGroupPath,
                        bitwardenVaultId = targetBitwardenVaultId,
                        bitwardenFolderId = targetBitwardenFolderId
                    )
                }
            }
        }
        val affectedVaultIds = buildSet {
            selectedItems.mapNotNullTo(this) { it.bitwardenVaultId }
            targetBitwardenVaultId?.let { add(it) }
        }
        affectedVaultIds.forEach(bitwardenViewModel::requestLocalMutationSync)

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

    val topBarTitle = when (val filter = selectedCategoryFilter) {
        UnifiedCategoryFilterSelection.All -> stringResource(R.string.nav_card_wallet)
        UnifiedCategoryFilterSelection.Local -> stringResource(R.string.filter_monica)
        UnifiedCategoryFilterSelection.Starred -> stringResource(R.string.filter_starred)
        UnifiedCategoryFilterSelection.Uncategorized -> stringResource(R.string.filter_uncategorized)
        UnifiedCategoryFilterSelection.LocalStarred ->
            "${stringResource(R.string.filter_monica)} · ${stringResource(R.string.filter_starred)}"
        UnifiedCategoryFilterSelection.LocalUncategorized ->
            "${stringResource(R.string.filter_monica)} · ${stringResource(R.string.filter_uncategorized)}"
        is UnifiedCategoryFilterSelection.Custom ->
            categories.find { it.id == filter.categoryId }?.name ?: stringResource(R.string.unknown_category)
        is UnifiedCategoryFilterSelection.KeePassDatabaseFilter ->
            keepassDatabases.find { it.id == filter.databaseId }?.name ?: stringResource(R.string.filter_keepass)
        is UnifiedCategoryFilterSelection.KeePassGroupFilter ->
            decodeKeePassPathForDisplay(filter.groupPath)
        is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> {
            val name = keepassDatabases.find { it.id == filter.databaseId }?.name ?: stringResource(R.string.filter_keepass)
            "$name · ${stringResource(R.string.filter_starred)}"
        }
        is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> {
            val name = keepassDatabases.find { it.id == filter.databaseId }?.name ?: stringResource(R.string.filter_keepass)
            "$name · ${stringResource(R.string.filter_uncategorized)}"
        }
        is UnifiedCategoryFilterSelection.BitwardenVaultFilter ->
            stringResource(R.string.filter_bitwarden)
        is UnifiedCategoryFilterSelection.BitwardenFolderFilter ->
            stringResource(R.string.filter_bitwarden)
        is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> {
            "${stringResource(R.string.filter_bitwarden)} · ${stringResource(R.string.filter_starred)}"
        }
        is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> {
            "${stringResource(R.string.filter_bitwarden)} · ${stringResource(R.string.filter_uncategorized)}"
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ExpressiveTopBar(
            title = topBarTitle,
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
                if (selectedBitwardenVaultId != null) {
                    IconButton(
                        onClick = {
                            if (isTopBarSyncing) return@IconButton
                            val vaultId = selectedBitwardenVaultId ?: return@IconButton
                            bitwardenViewModel.requestManualSync(vaultId)
                        },
                        enabled = !isTopBarSyncing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = stringResource(R.string.refresh)
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

        val searchProgress = (currentOffset / searchTriggerDistance).coerceIn(0f, 1f)
        val syncProgress = ((currentOffset - searchTriggerDistance) / (syncTriggerDistance - searchTriggerDistance))
            .coerceIn(0f, 1f)
        val pullVisualState = when {
            isBitwardenDatabaseView && isBitwardenSyncing -> PullActionVisualState.SYNCING
            isBitwardenDatabaseView && showSyncFeedback -> PullActionVisualState.SYNC_DONE
            isBitwardenDatabaseView && syncHintArmed -> PullActionVisualState.SYNC_READY
            currentOffset >= searchTriggerDistance -> PullActionVisualState.SEARCH_READY
            else -> PullActionVisualState.IDLE
        }
        val pullHintText = when (pullVisualState) {
            PullActionVisualState.SYNCING -> stringResource(R.string.pull_syncing_bitwarden)
            PullActionVisualState.SYNC_DONE -> syncFeedbackMessage.ifBlank {
                if (syncFeedbackIsSuccess) {
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
            syncHintArmed || isBitwardenSyncing || showSyncFeedback
        )
        val revealHeightTarget = with(density) {
            if (isBitwardenDatabaseView) {
                val pullHeight = currentOffset.toDp().coerceIn(0.dp, 112.dp)
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
            label = "wallet_pull_reveal_height"
        )
        val showPullIndicator = pullHintText != null && revealHeight > 0.5.dp
        val contentPullOffset = if (isBitwardenDatabaseView) {
            (currentOffset * 0.28f).toInt()
        } else {
            currentOffset.toInt()
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

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    bankLoading || documentLoading -> LoadingIndicator()
                    filteredItems.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset { IntOffset(0, contentPullOffset) }
                                .pointerInput(isSearchExpanded) {
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { _, dragAmount ->
                                            if (dragAmount > 0f) {
                                                val newOffset = (currentOffset + dragAmount * 0.5f).coerceAtMost(maxDragDistance)
                                                val oldOffset = currentOffset
                                                currentOffset = newOffset
                                                updatePullThresholdHaptics(oldOffset = oldOffset, newOffset = newOffset)
                                            }
                                        },
                                        onDragEnd = {
                                            val syncStarted = onPullRelease()
                                            if (!syncStarted && !lockPullUntilSyncFinished) {
                                                scope.launch { collapsePullOffsetSmoothly() }
                                            }
                                        },
                                        onDragCancel = {
                                            if (!lockPullUntilSyncFinished) {
                                                scope.launch { collapsePullOffsetSmoothly() }
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
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
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .offset { IntOffset(0, contentPullOffset) }
                                .nestedScroll(nestedScrollConnection),
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
        allowCopy = true,
        onTargetSelected = ::performBatchMove
    )
}

private fun encodeCardWalletCategoryFilter(filter: UnifiedCategoryFilterSelection): SavedCategoryFilterState = when (filter) {
    UnifiedCategoryFilterSelection.All -> SavedCategoryFilterState(type = "all")
    UnifiedCategoryFilterSelection.Local -> SavedCategoryFilterState(type = "local")
    UnifiedCategoryFilterSelection.Starred -> SavedCategoryFilterState(type = "starred")
    UnifiedCategoryFilterSelection.Uncategorized -> SavedCategoryFilterState(type = "uncategorized")
    UnifiedCategoryFilterSelection.LocalStarred -> SavedCategoryFilterState(type = "local_starred")
    UnifiedCategoryFilterSelection.LocalUncategorized -> SavedCategoryFilterState(type = "local_uncategorized")
    is UnifiedCategoryFilterSelection.Custom -> SavedCategoryFilterState(type = "custom", primaryId = filter.categoryId)
    is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> SavedCategoryFilterState(type = "bitwarden_vault", primaryId = filter.vaultId)
    is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> SavedCategoryFilterState(type = "bitwarden_folder", primaryId = filter.vaultId, text = filter.folderId)
    is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> SavedCategoryFilterState(type = "bitwarden_vault_starred", primaryId = filter.vaultId)
    is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> SavedCategoryFilterState(type = "bitwarden_vault_uncategorized", primaryId = filter.vaultId)
    is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> SavedCategoryFilterState(type = "keepass_database", primaryId = filter.databaseId)
    is UnifiedCategoryFilterSelection.KeePassGroupFilter -> SavedCategoryFilterState(type = "keepass_group", primaryId = filter.databaseId, text = filter.groupPath)
    is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> SavedCategoryFilterState(type = "keepass_database_starred", primaryId = filter.databaseId)
    is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> SavedCategoryFilterState(type = "keepass_database_uncategorized", primaryId = filter.databaseId)
}

private val cardWalletCategoryFilterSaver = listSaver<UnifiedCategoryFilterSelection, Any?>(
    save = { filter ->
        val state = encodeCardWalletCategoryFilter(filter)
        listOf(state.type, state.primaryId, state.secondaryId, state.text)
    },
    restore = { saved ->
        decodeCardWalletCategoryFilter(
            SavedCategoryFilterState(
                type = saved.getOrNull(0) as? String ?: "all",
                primaryId = saved.getOrNull(1) as? Long,
                secondaryId = saved.getOrNull(2) as? Long,
                text = saved.getOrNull(3) as? String
            )
        )
    }
)

private fun decodeCardWalletCategoryFilter(state: SavedCategoryFilterState): UnifiedCategoryFilterSelection {
    return when (state.type) {
        "local" -> UnifiedCategoryFilterSelection.Local
        "starred" -> UnifiedCategoryFilterSelection.Starred
        "uncategorized" -> UnifiedCategoryFilterSelection.Uncategorized
        "local_starred" -> UnifiedCategoryFilterSelection.LocalStarred
        "local_uncategorized" -> UnifiedCategoryFilterSelection.LocalUncategorized
        "custom" -> state.primaryId?.let { UnifiedCategoryFilterSelection.Custom(it) } ?: UnifiedCategoryFilterSelection.All
        "bitwarden_vault" -> state.primaryId?.let { UnifiedCategoryFilterSelection.BitwardenVaultFilter(it) } ?: UnifiedCategoryFilterSelection.All
        "bitwarden_folder" -> {
            val vaultId = state.primaryId
            val folderId = state.text
            if (vaultId != null && !folderId.isNullOrBlank()) {
                UnifiedCategoryFilterSelection.BitwardenFolderFilter(vaultId, folderId)
            } else {
                UnifiedCategoryFilterSelection.All
            }
        }
        "bitwarden_vault_starred" -> state.primaryId?.let { UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter(it) } ?: UnifiedCategoryFilterSelection.All
        "bitwarden_vault_uncategorized" -> state.primaryId?.let { UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter(it) } ?: UnifiedCategoryFilterSelection.All
        "keepass_database" -> state.primaryId?.let { UnifiedCategoryFilterSelection.KeePassDatabaseFilter(it) } ?: UnifiedCategoryFilterSelection.All
        "keepass_group" -> {
            val databaseId = state.primaryId
            val groupPath = state.text
            if (databaseId != null && !groupPath.isNullOrBlank()) {
                UnifiedCategoryFilterSelection.KeePassGroupFilter(databaseId, groupPath)
            } else {
                UnifiedCategoryFilterSelection.All
            }
        }
        "keepass_database_starred" -> state.primaryId?.let { UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter(it) } ?: UnifiedCategoryFilterSelection.All
        "keepass_database_uncategorized" -> state.primaryId?.let { UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter(it) } ?: UnifiedCategoryFilterSelection.All
        else -> UnifiedCategoryFilterSelection.All
    }
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
