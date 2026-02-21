package takagi.ru.monica.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.bitwarden.BitwardenPendingOperation
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.data.model.PasskeyBinding
import takagi.ru.monica.data.model.PasskeyBindingCodec
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.components.M3IdentityVerifyDialog
import takagi.ru.monica.ui.components.SyncStatusBadge
import takagi.ru.monica.ui.components.SyncStatusIcon
import takagi.ru.monica.ui.components.UnifiedCategoryFilterBottomSheet
import takagi.ru.monica.ui.components.UnifiedCategoryFilterSelection
import takagi.ru.monica.ui.components.UnifiedMoveCategoryTarget
import takagi.ru.monica.ui.components.UnifiedMoveToCategoryBottomSheet
import takagi.ru.monica.ui.gestures.SwipeActions
import takagi.ru.monica.ui.haptic.rememberHapticFeedback
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.utils.KeePassKdbxService
import takagi.ru.monica.utils.SavedCategoryFilterState
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.viewmodel.PasskeyViewModel
import takagi.ru.monica.viewmodel.PasswordViewModel
import kotlinx.coroutines.flow.flowOf
import takagi.ru.monica.autofill.ui.rememberAppIcon
import takagi.ru.monica.autofill.ui.rememberFavicon
import takagi.ru.monica.ui.icons.rememberAutoMatchedSimpleIcon
import takagi.ru.monica.bitwarden.sync.SyncStatus

/**
 * Passkey 列表屏幕
 * 
 * 与密码列表页面保持完全一致的设计风格：
 * - ExpressiveTopBar 顶栏（大标题 + 胶囊形搜索按钮）
 * - 下拉触发搜索
 * - Android 14+ 完整功能支持
 * - Android 14 以下版本仅查看模式
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PasskeyListScreen(
    viewModel: PasskeyViewModel,
    onPasskeyClick: (PasskeyEntry) -> Unit = {},
    passwordViewModel: PasswordViewModel? = null,
    onNavigateToPasswordDetail: (Long) -> Unit = {},
    hideTopBar: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val database = remember(context) { PasswordDatabase.getDatabase(context) }
    
    // 收集状态
    val passkeys by viewModel.filteredPasskeys.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val passwords by (passwordViewModel?.allPasswords ?: flowOf(emptyList())).collectAsState(initial = emptyList())
    val passwordMap = remember(passwords) { passwords.associateBy { it.id } }
    val categories by database.categoryDao().getAllCategories().collectAsState(initial = emptyList())
    val keepassDatabases by database.localKeePassDatabaseDao().getAllDatabases().collectAsState(initial = emptyList())
    val bitwardenRepository = remember { BitwardenRepository.getInstance(context) }
    var bitwardenVaults by remember { mutableStateOf<List<BitwardenVault>>(emptyList()) }
    val securityManager = remember { takagi.ru.monica.security.SecurityManager(context) }
    val keePassService = remember {
        KeePassKdbxService(
            context,
            database.localKeePassDatabaseDao(),
            securityManager
        )
    }
    val keepassGroupFlows = remember {
        mutableMapOf<Long, kotlinx.coroutines.flow.MutableStateFlow<List<takagi.ru.monica.utils.KeePassGroupInfo>>>()
    }
    val getKeePassGroups: (Long) -> kotlinx.coroutines.flow.Flow<List<takagi.ru.monica.utils.KeePassGroupInfo>> = remember {
        { databaseId ->
            val flow = keepassGroupFlows.getOrPut(databaseId) {
                kotlinx.coroutines.flow.MutableStateFlow(emptyList())
            }
            if (flow.value.isEmpty()) {
                scope.launch {
                    flow.value = keePassService.listGroups(databaseId).getOrDefault(emptyList())
                }
            }
            flow
        }
    }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    LaunchedEffect(Unit) {
        bitwardenVaults = bitwardenRepository.getAllVaults()
    }

    val bindingPasskeys = remember(passwords, searchQuery) {
        val rawList = passwords.flatMap { password ->
            val bindings = PasskeyBindingCodec.decodeList(password.passkeyBindings)
            bindings.map { binding ->
                PasskeyEntry(
                    credentialId = binding.credentialId.ifBlank { "ref_${password.id}_${binding.rpId}" },
                    rpId = binding.rpId,
                    rpName = binding.rpName.ifBlank { binding.rpId },
                    userId = "",
                    userName = binding.userName,
                    userDisplayName = binding.userDisplayName.ifBlank { binding.userName },
                    publicKeyAlgorithm = PasskeyEntry.ALGORITHM_ES256,
                    publicKey = "",
                    privateKeyAlias = "",
                    createdAt = System.currentTimeMillis(),
                    lastUsedAt = System.currentTimeMillis(),
                    useCount = 0,
                    iconUrl = null,
                    isDiscoverable = false,
                    isUserVerificationRequired = true,
                    transports = PasskeyEntry.TRANSPORT_INTERNAL,
                    aaguid = "",
                    signCount = 0,
                    isBackedUp = true,
                    notes = "",
                    boundPasswordId = password.id,
                    categoryId = password.categoryId,
                    bitwardenVaultId = null,
                    bitwardenCipherId = null,
                    syncStatus = "REFERENCE"
                )
            }
        }

        if (searchQuery.isBlank()) {
            rawList
        } else {
            rawList.filter { passkey ->
                passkey.rpId.contains(searchQuery, ignoreCase = true) ||
                    passkey.rpName.contains(searchQuery, ignoreCase = true) ||
                    passkey.userName.contains(searchQuery, ignoreCase = true) ||
                    passkey.userDisplayName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val combinedPasskeys = remember(passkeys, bindingPasskeys) {
        if (bindingPasskeys.isEmpty()) return@remember passkeys
        val existingIds = passkeys.map { it.credentialId }.toSet()
        passkeys + bindingPasskeys.filterNot { it.credentialId in existingIds }
    }
    // 是否完全支持 Passkey
    val isFullySupported = viewModel.isPasskeyFullySupported
    
    // 列表状态
    val listState = rememberLazyListState()
    
    // 搜索栏展开状态
    var isSearchExpanded by remember { mutableStateOf(false) }

    var passkeyToBind by remember { mutableStateOf<PasskeyEntry?>(null) }
    var passkeyToMoveCategory by remember { mutableStateOf<PasskeyEntry?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPasskeys by remember { mutableStateOf(setOf<String>()) }
    var pendingDeletePasskey by remember { mutableStateOf<PasskeyEntry?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf<UnifiedCategoryFilterSelection>(UnifiedCategoryFilterSelection.All) }
    var showCategoryFilterDialog by remember { mutableStateOf(false) }
    var categoryPillBoundsInWindow by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var showBatchMoveCategoryDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deletePasswordInput by remember { mutableStateOf("") }
    var deletePasswordError by remember { mutableStateOf(false) }
    val haptic = rememberHapticFeedback()
    val settingsManager = remember { SettingsManager(context) }
    val savedCategoryFilterState by settingsManager
        .categoryFilterStateFlow(SettingsManager.CategoryFilterScope.PASSKEY)
        .collectAsState(initial = SavedCategoryFilterState())
    var hasRestoredCategoryFilter by remember { mutableStateOf(false) }
    val appSettings by settingsManager.settingsFlow.collectAsState(
        initial = AppSettings(biometricEnabled = false)
    )
    val biometricHelper = remember { BiometricHelper(context) }
    val canUseBiometric = remember(appSettings.biometricEnabled) {
        appSettings.biometricEnabled && biometricHelper.isBiometricAvailable()
    }
    val activity = context as? FragmentActivity
    val boundPasswordForPasskey: (PasskeyEntry) -> PasswordEntry? = remember(passwordMap) {
        { passkey -> passkey.boundPasswordId?.let { passwordId -> passwordMap[passwordId] } }
    }
    val effectiveCategoryId: (PasskeyEntry) -> Long? = remember(passwordMap) {
        { passkey -> boundPasswordForPasskey(passkey)?.categoryId ?: passkey.categoryId }
    }
    val effectiveBitwardenVaultId: (PasskeyEntry) -> Long? = remember(passwordMap) {
        { passkey -> boundPasswordForPasskey(passkey)?.bitwardenVaultId ?: passkey.bitwardenVaultId }
    }
    val effectiveBitwardenFolderId: (PasskeyEntry) -> String? = remember(passwordMap) {
        { passkey -> boundPasswordForPasskey(passkey)?.bitwardenFolderId }
    }
    val effectiveKeePassDatabaseId: (PasskeyEntry) -> Long? = remember(passwordMap) {
        { passkey -> boundPasswordForPasskey(passkey)?.keepassDatabaseId ?: passkey.keepassDatabaseId }
    }
    val effectiveKeePassGroupPath: (PasskeyEntry) -> String? = remember(passwordMap) {
        { passkey -> boundPasswordForPasskey(passkey)?.keepassGroupPath }
    }
    val effectiveIsFavorite: (PasskeyEntry) -> Boolean = remember(passwordMap) {
        { passkey -> boundPasswordForPasskey(passkey)?.isFavorite == true }
    }
    val categoryFilteredPasskeys = remember(combinedPasskeys, selectedCategoryFilter, passwordMap) {
        combinedPasskeys.filter { passkey ->
            val effectiveVaultId = effectiveBitwardenVaultId(passkey)
            val effectiveFolderId = effectiveBitwardenFolderId(passkey)
            val effectiveKeePassId = effectiveKeePassDatabaseId(passkey)
            val effectiveGroupPath = effectiveKeePassGroupPath(passkey)
            val isLocal = effectiveVaultId == null && effectiveKeePassId == null
            when (val filter = selectedCategoryFilter) {
                UnifiedCategoryFilterSelection.All -> true
                UnifiedCategoryFilterSelection.Local -> isLocal
                UnifiedCategoryFilterSelection.Uncategorized -> effectiveCategoryId(passkey) == null
                UnifiedCategoryFilterSelection.LocalStarred -> isLocal && effectiveIsFavorite(passkey)
                UnifiedCategoryFilterSelection.LocalUncategorized -> isLocal && effectiveCategoryId(passkey) == null
                is UnifiedCategoryFilterSelection.Custom -> effectiveCategoryId(passkey) == filter.categoryId
                UnifiedCategoryFilterSelection.Starred -> effectiveIsFavorite(passkey)
                is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> effectiveVaultId == filter.vaultId
                is UnifiedCategoryFilterSelection.BitwardenFolderFilter ->
                    effectiveVaultId == filter.vaultId && effectiveFolderId == filter.folderId
                is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter ->
                    effectiveVaultId == filter.vaultId && effectiveIsFavorite(passkey)
                is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter ->
                    effectiveVaultId == filter.vaultId && effectiveFolderId == null
                is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> effectiveKeePassId == filter.databaseId
                is UnifiedCategoryFilterSelection.KeePassGroupFilter ->
                    effectiveKeePassId == filter.databaseId && effectiveGroupPath == filter.groupPath
                is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter ->
                    effectiveKeePassId == filter.databaseId && effectiveIsFavorite(passkey)
                is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter ->
                    effectiveKeePassId == filter.databaseId && effectiveGroupPath.isNullOrBlank()
            }
        }
    }
    LaunchedEffect(savedCategoryFilterState, hasRestoredCategoryFilter) {
        if (hasRestoredCategoryFilter) return@LaunchedEffect
        selectedCategoryFilter = decodePasskeyCategoryFilter(savedCategoryFilterState)
        hasRestoredCategoryFilter = true
    }
    LaunchedEffect(selectedCategoryFilter, hasRestoredCategoryFilter) {
        if (!hasRestoredCategoryFilter) return@LaunchedEffect
        settingsManager.updateCategoryFilterState(
            scope = SettingsManager.CategoryFilterScope.PASSKEY,
            state = encodePasskeyCategoryFilter(selectedCategoryFilter)
        )
    }
    val visiblePasskeys = remember(categoryFilteredPasskeys, pendingDeletePasskey) {
        val deletingId = pendingDeletePasskey?.credentialId
        if (deletingId == null) {
            categoryFilteredPasskeys
        } else {
            categoryFilteredPasskeys.filterNot { it.credentialId == deletingId }
        }
    }
    val failedPasskeyCount = remember(visiblePasskeys) {
        visiblePasskeys.count { it.syncStatus == "FAILED" }
    }
    var lastShownFailedPasskeyCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(failedPasskeyCount) {
        if (failedPasskeyCount > 0 && failedPasskeyCount != lastShownFailedPasskeyCount) {
            val message = context.getString(R.string.sync_status_failed_short) + " ($failedPasskeyCount)"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        lastShownFailedPasskeyCount = failedPasskeyCount
    }
    
    // 下拉搜索相关
    var currentOffset by remember { mutableFloatStateOf(0f) }
    val triggerDistance = remember(density) { with(density) { 72.dp.toPx() } }
    var hasVibrated by remember { mutableStateOf(false) }
    var canTriggerPullToSearch by remember { mutableStateOf(false) }
    
    // 震动服务
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }
    
    // 嵌套滚动连接（下拉触发搜索）
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (currentOffset > 0 && available.y < 0) {
                    val newOffset = (currentOffset + available.y).coerceAtLeast(0f)
                    val consumed = currentOffset - newOffset
                    currentOffset = newOffset
                    return Offset(0f, -consumed)
                }
                return Offset.Zero
            }
            
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!isSearchExpanded && available.y > 0 && canTriggerPullToSearch) {
                    if (source == NestedScrollSource.UserInput) {
                        val delta = available.y * 0.5f
                        val newOffset = currentOffset + delta
                        val oldOffset = currentOffset
                        currentOffset = newOffset
                        
                        if (oldOffset < triggerDistance && newOffset >= triggerDistance && !hasVibrated) {
                            hasVibrated = true
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator?.vibrate(android.os.VibrationEffect.createWaveform(takagi.ru.monica.util.VibrationPatterns.TICK, -1))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator?.vibrate(20)
                            }
                        } else if (newOffset < triggerDistance) {
                            hasVibrated = false
                        }
                        
                        return available
                    }
                }
                return Offset.Zero
            }
            
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentOffset >= triggerDistance) {
                    isSearchExpanded = true
                    hasVibrated = false
                }
                androidx.compose.animation.core.Animatable(currentOffset).animateTo(0f) {
                    currentOffset = value
                }
                return super.onPreFling(available)
            }
        }
    }
    
    // 监听搜索查询变化
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            isSearchExpanded = true
        }
    }

    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
        viewModel.updateSearchQuery("")
    }

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedPasskeys = emptySet()
    }
    
    // 显示版本警告
    var showVersionWarning by remember { mutableStateOf(!isFullySupported) }

    suspend fun applyStorageTarget(passkey: PasskeyEntry, target: UnifiedMoveCategoryTarget): Result<PasskeyEntry> {
        val currentVaultId = passkey.bitwardenVaultId
        val currentCipherId = passkey.bitwardenCipherId
        val targetVaultId = when (target) {
            is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> target.vaultId
            is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> target.vaultId
            else -> null
        }

        val isLeavingCurrentCipher =
            currentVaultId != null &&
                !currentCipherId.isNullOrBlank() &&
                currentVaultId != targetVaultId

        if (isLeavingCurrentCipher) {
            val queueResult = bitwardenRepository.queueCipherDelete(
                vaultId = currentVaultId!!,
                cipherId = currentCipherId!!,
                itemType = BitwardenPendingOperation.ITEM_TYPE_PASSKEY
            )
            if (queueResult.isFailure) {
                return Result.failure(
                    queueResult.exceptionOrNull()
                        ?: IllegalStateException("Queue Bitwarden delete failed")
                )
            }
        }

        val moved = when (target) {
            UnifiedMoveCategoryTarget.Uncategorized -> passkey.copy(
                categoryId = null,
                keepassDatabaseId = null,
                bitwardenVaultId = null
            )
            is UnifiedMoveCategoryTarget.MonicaCategory -> passkey.copy(
                categoryId = target.categoryId,
                keepassDatabaseId = null,
                bitwardenVaultId = null
            )
            is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> passkey.copy(
                bitwardenVaultId = target.vaultId,
                keepassDatabaseId = null
            )
            is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> passkey.copy(
                bitwardenVaultId = target.vaultId,
                keepassDatabaseId = null
            )
            is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> passkey.copy(
                keepassDatabaseId = target.databaseId,
                bitwardenVaultId = null
            )
            is UnifiedMoveCategoryTarget.KeePassGroupTarget -> passkey.copy(
                keepassDatabaseId = target.databaseId,
                bitwardenVaultId = null
            )
        }

        val keepExistingCipher =
            !currentCipherId.isNullOrBlank() &&
                currentVaultId != null &&
                currentVaultId == targetVaultId

        val resolvedSyncStatus = when {
            moved.syncStatus == "REFERENCE" -> "REFERENCE"
            targetVaultId == null -> "NONE"
            keepExistingCipher -> if (passkey.syncStatus == "SYNCED") "SYNCED" else "PENDING"
            else -> "PENDING"
        }

        return Result.success(
            moved.copy(
                bitwardenCipherId = if (keepExistingCipher) currentCipherId else null,
                syncStatus = resolvedSyncStatus
            )
        )
    }

    suspend fun deletePasskeyWithBinding(passkey: PasskeyEntry): Boolean {
        val boundPassword = passkey.boundPasswordId?.let { passwordMap[it] }
        if (boundPassword != null && passwordViewModel != null) {
            val updatedBindings = PasskeyBindingCodec.removeBinding(boundPassword.passkeyBindings, passkey.credentialId)
            passwordViewModel.updatePasskeyBindings(boundPassword.id, updatedBindings)
        }
        val isReferenceOnly =
            passkey.syncStatus == "REFERENCE" &&
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
                    return false
                }
            }
            viewModel.deletePasskey(passkey)
        }
        return true
    }

    val performDeleteTargets: (List<PasskeyEntry>) -> Unit = { targets ->
        scope.launch {
            if (targets.isNotEmpty()) {
                var deletedCount = 0
                var failedCount = 0
                targets.forEach { passkey ->
                    if (deletePasskeyWithBinding(passkey)) {
                        deletedCount++
                    } else {
                        failedCount++
                    }
                }
                if (selectionMode) {
                    selectionMode = false
                    selectedPasskeys = emptySet()
                }
                pendingDeletePasskey = null
                showDeleteConfirmDialog = false
                deletePasswordInput = ""
                deletePasswordError = false
                val msg = if (failedCount == 0) {
                    context.getString(R.string.deleted_items, deletedCount)
                } else {
                    "${context.getString(R.string.deleted_items, deletedCount)}，失败$failedCount"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } else {
                showDeleteConfirmDialog = false
                pendingDeletePasskey = null
            }
        }
    }

    val topBarTitle = when (val filter = selectedCategoryFilter) {
        UnifiedCategoryFilterSelection.All -> stringResource(R.string.passkey_title)
        UnifiedCategoryFilterSelection.Local -> stringResource(R.string.passkey_title)
        UnifiedCategoryFilterSelection.Uncategorized -> stringResource(R.string.category_none)
        UnifiedCategoryFilterSelection.LocalStarred -> "${stringResource(R.string.filter_monica)} · ${stringResource(R.string.filter_starred)}"
        UnifiedCategoryFilterSelection.LocalUncategorized -> "${stringResource(R.string.filter_monica)} · ${stringResource(R.string.filter_uncategorized)}"
        is UnifiedCategoryFilterSelection.Custom -> categoryMap[filter.categoryId]?.name ?: stringResource(R.string.passkey_title)
        UnifiedCategoryFilterSelection.Starred -> stringResource(R.string.filter_starred)
        is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> stringResource(R.string.filter_bitwarden)
        is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> stringResource(R.string.filter_bitwarden)
        is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> "${stringResource(R.string.filter_bitwarden)} · ${stringResource(R.string.filter_starred)}"
        is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> "${stringResource(R.string.filter_bitwarden)} · ${stringResource(R.string.filter_uncategorized)}"
        is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> stringResource(R.string.filter_keepass)
        is UnifiedCategoryFilterSelection.KeePassGroupFilter -> stringResource(R.string.filter_keepass)
        is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> "${stringResource(R.string.filter_keepass)} · ${stringResource(R.string.filter_starred)}"
        is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> "${stringResource(R.string.filter_keepass)} · ${stringResource(R.string.filter_uncategorized)}"
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        // ExpressiveTopBar（与密码列表完全一致）
        if (!hideTopBar) {
            ExpressiveTopBar(
                title = topBarTitle,
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                isSearchExpanded = isSearchExpanded,
                onSearchExpandedChange = { isSearchExpanded = it },
                searchHint = stringResource(R.string.passkey_search_placeholder),
                onActionPillBoundsChanged = { bounds -> categoryPillBoundsInWindow = bounds },
                actions = {
                    IconButton(onClick = { showCategoryFilterDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = stringResource(R.string.category),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { isSearchExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
        
        // 版本兼容性警告
        AnimatedVisibility(
            visible = showVersionWarning && !isFullySupported,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            VersionWarningBanner(
                androidVersion = viewModel.androidVersion,
                onDismiss = { showVersionWarning = false }
            )
        }
        
        // 主内容 + 左下角胶囊多选栏
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (visiblePasskeys.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isSearchExpanded) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    if (!isSearchExpanded && dragAmount > 0f) {
                                        val newOffset = currentOffset + dragAmount * 0.5f
                                        val oldOffset = currentOffset
                                        currentOffset = newOffset

                                        if (oldOffset < triggerDistance && newOffset >= triggerDistance && !hasVibrated) {
                                            hasVibrated = true
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                vibrator?.vibrate(android.os.VibrationEffect.createWaveform(takagi.ru.monica.util.VibrationPatterns.TICK, -1))
                                            } else {
                                                @Suppress("DEPRECATION")
                                                vibrator?.vibrate(20)
                                            }
                                        } else if (newOffset < triggerDistance) {
                                            hasVibrated = false
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (currentOffset >= triggerDistance) {
                                        isSearchExpanded = true
                                        hasVibrated = false
                                    }
                                    scope.launch {
                                        androidx.compose.animation.core.Animatable(currentOffset).animateTo(0f) {
                                            currentOffset = value
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        androidx.compose.animation.core.Animatable(currentOffset).animateTo(0f) {
                                            currentOffset = value
                                        }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset { IntOffset(0, currentOffset.toInt()) }
                    ) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (searchQuery.isEmpty())
                                stringResource(R.string.passkey_empty_title)
                            else
                                stringResource(R.string.passkey_no_search_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        if (searchQuery.isEmpty() && isFullySupported) {
                            Text(
                                text = stringResource(R.string.passkey_empty_message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, currentOffset.toInt()) }
                        .nestedScroll(nestedScrollConnection)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                                canTriggerPullToSearch = isAtTop
                            }
                        },
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = if (selectionMode) 140.dp else 100.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = visiblePasskeys,
                        key = { it.credentialId }
                    ) { passkey ->
                        val boundPassword = passkey.boundPasswordId?.let { passwordMap[it] }
                        val currentCategoryId = effectiveCategoryId(passkey)
                        val categoryName = currentCategoryId
                            ?.let { categoryMap[it]?.name }
                            ?: context.getString(R.string.category_none)
                        val isSelected = selectedPasskeys.contains(passkey.credentialId)
                        val isPendingDelete = pendingDeletePasskey?.credentialId == passkey.credentialId

                        key(passkey.credentialId, isSelected, isPendingDelete, selectionMode) {
                            SwipeActions(
                                onSwipeLeft = {
                                    haptic.performWarning()
                                    pendingDeletePasskey = passkey
                                    showDeleteConfirmDialog = true
                                },
                                onSwipeRight = {
                                    haptic.performSuccess()
                                    val updatedSelection = if (selectedPasskeys.contains(passkey.credentialId)) {
                                        selectedPasskeys - passkey.credentialId
                                    } else {
                                        selectedPasskeys + passkey.credentialId
                                    }
                                    selectedPasskeys = updatedSelection
                                    selectionMode = updatedSelection.isNotEmpty()
                                },
                                isSwiped = isPendingDelete,
                                enabled = true,
                                modifier = Modifier
                            ) {
                                PasskeyListItem(
                                    passkey = passkey,
                                    boundPassword = boundPassword,
                                    currentCategoryName = categoryName,
                                    isCategoryLocked = boundPassword != null || passkey.syncStatus == "REFERENCE",
                                    iconCardsEnabled = appSettings.iconCardsEnabled,
                                    isSelected = isSelected,
                                    selectionMode = selectionMode,
                                    onClick = { onPasskeyClick(passkey) },
                                    onToggleSelect = {
                                        val updatedSelection = if (selectedPasskeys.contains(passkey.credentialId)) {
                                            selectedPasskeys - passkey.credentialId
                                        } else {
                                            selectedPasskeys + passkey.credentialId
                                        }
                                        selectedPasskeys = updatedSelection
                                        selectionMode = updatedSelection.isNotEmpty()
                                    },
                                    onLongPress = {
                                        haptic.performLongPress()
                                        if (!selectionMode) {
                                            selectionMode = true
                                            selectedPasskeys = setOf(passkey.credentialId)
                                        }
                                    },
                                    onDeleteRequest = {
                                        pendingDeletePasskey = passkey
                                        showDeleteConfirmDialog = true
                                    },
                                    onBindPassword = { passkeyToBind = passkey },
                                    onUnbindPassword = {
                                        if (boundPassword != null && passwordViewModel != null) {
                                            val updatedBindings = PasskeyBindingCodec.removeBinding(boundPassword.passkeyBindings, passkey.credentialId)
                                            passwordViewModel.updatePasskeyBindings(boundPassword.id, updatedBindings)
                                        }
                                        if (passkey.syncStatus != "REFERENCE") {
                                            viewModel.updateBoundPassword(passkey.credentialId, null)
                                        }
                                    },
                                    onOpenBoundPassword = { passwordId -> onNavigateToPasswordDetail(passwordId) },
                                    onChangeCategory = if (boundPassword != null || passkey.syncStatus == "REFERENCE") {
                                        null
                                    } else {
                                        { passkeyToMoveCategory = passkey }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (selectionMode) {
                PasskeySelectionActionBar(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 20.dp),
                    selectedCount = selectedPasskeys.size,
                    onExit = {
                        selectionMode = false
                        selectedPasskeys = emptySet()
                    },
                    onSelectAll = {
                        val allKeys = categoryFilteredPasskeys.map { it.credentialId }.toSet()
                        val updatedSelection = if (selectedPasskeys.size == allKeys.size) {
                            emptySet()
                        } else {
                            allKeys
                        }
                        selectedPasskeys = updatedSelection
                        selectionMode = updatedSelection.isNotEmpty()
                    },
                    onMoveToCategory = {
                        showBatchMoveCategoryDialog = true
                    },
                    onDelete = {
                        pendingDeletePasskey = null
                        showDeleteConfirmDialog = true
                    }
                )
            }
        }
    }

    UnifiedCategoryFilterBottomSheet(
        visible = showCategoryFilterDialog,
        onDismiss = { showCategoryFilterDialog = false },
        selected = selectedCategoryFilter,
        onSelect = { selection ->
            selectedCategoryFilter = selection
        },
        launchAnchorBounds = categoryPillBoundsInWindow,
        categories = categories,
        keepassDatabases = keepassDatabases,
        bitwardenVaults = bitwardenVaults,
        getBitwardenFolders = { vaultId -> database.bitwardenFolderDao().getFoldersByVaultFlow(vaultId) },
        onCreateCategoryWithName = { name ->
            scope.launch {
                val finalName = name.trim()
                if (finalName.isNotEmpty()) {
                    database.categoryDao().insert(Category(name = finalName))
                }
            }
        },
        onRenameCategory = { category ->
            scope.launch {
                database.categoryDao().update(category)
            }
        },
        onDeleteCategory = { category ->
            scope.launch {
                database.categoryDao().delete(category)
            }
        }
    )

    if (showDeleteConfirmDialog) {
        val deleteTargets = if (pendingDeletePasskey != null) {
            listOf(pendingDeletePasskey!!)
        } else {
            combinedPasskeys.filter { selectedPasskeys.contains(it.credentialId) }
        }

        val biometricAction = if (activity != null && canUseBiometric) {
            {
                biometricHelper.authenticate(
                    activity = activity,
                    title = context.getString(R.string.verify_identity),
                    subtitle = context.getString(R.string.verify_to_delete),
                    onSuccess = { performDeleteTargets(deleteTargets) },
                    onError = { error ->
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    },
                    onFailed = {}
                )
            }
        } else {
            null
        }

        M3IdentityVerifyDialog(
            title = if (pendingDeletePasskey != null) {
                stringResource(R.string.passkey_delete_title)
            } else {
                stringResource(R.string.delete_item_title, stringResource(R.string.passkey_title))
            },
            message = if (pendingDeletePasskey != null) {
                val passkey = pendingDeletePasskey!!
                stringResource(
                    R.string.passkey_delete_message,
                    passkey.rpName.ifBlank { passkey.rpId },
                    passkey.userDisplayName.ifBlank { passkey.userName }
                )
            } else {
                stringResource(
                    R.string.delete_item_message,
                    stringResource(R.string.passkey_title),
                    stringResource(R.string.selected_items, deleteTargets.size)
                )
            },
            passwordValue = deletePasswordInput,
            onPasswordChange = {
                deletePasswordInput = it
                deletePasswordError = false
            },
            onDismiss = {
                showDeleteConfirmDialog = false
                pendingDeletePasskey = null
                deletePasswordInput = ""
                deletePasswordError = false
            },
            onConfirm = {
                if (takagi.ru.monica.security.SecurityManager(context).verifyMasterPassword(deletePasswordInput)) {
                    performDeleteTargets(deleteTargets)
                } else {
                    deletePasswordError = true
                }
            },
            confirmText = stringResource(R.string.delete),
            destructiveConfirm = true,
            isPasswordError = deletePasswordError,
            passwordErrorText = stringResource(R.string.current_password_incorrect),
            onBiometricClick = biometricAction,
            biometricHintText = if (biometricAction == null) {
                context.getString(R.string.biometric_not_available)
            } else {
                null
            }
        )
    }

    if (passkeyToBind != null) {
        PasswordPickerDialog(
            passwords = passwords,
            onDismiss = { passkeyToBind = null },
            onPasswordSelected = { password ->
                val passkey = passkeyToBind!!
                val previousPasswordId = passkey.boundPasswordId
                scope.launch {
                    if (passwordViewModel != null) {
                        val newBinding = PasskeyBinding(
                            credentialId = passkey.credentialId,
                            rpId = passkey.rpId,
                            rpName = passkey.rpName,
                            userName = passkey.userName,
                            userDisplayName = passkey.userDisplayName
                        )

                        val targetEntry = passwordMap[password.id]
                        if (targetEntry != null) {
                            val updatedBindings = PasskeyBindingCodec.addBinding(targetEntry.passkeyBindings, newBinding)
                            passwordViewModel.updatePasskeyBindings(password.id, updatedBindings)
                        }

                        if (previousPasswordId != null && previousPasswordId != password.id) {
                            val previousEntry = passwordMap[previousPasswordId]
                            if (previousEntry != null) {
                                val updatedBindings = PasskeyBindingCodec.removeBinding(previousEntry.passkeyBindings, passkey.credentialId)
                                passwordViewModel.updatePasskeyBindings(previousPasswordId, updatedBindings)
                            }
                        }
                    }

                    if (passkey.syncStatus != "REFERENCE") {
                        val vaultId = passkey.bitwardenVaultId
                        val cipherId = passkey.bitwardenCipherId
                        if (vaultId != null && !cipherId.isNullOrBlank()) {
                            val queueResult = bitwardenRepository.queueCipherDelete(
                                vaultId = vaultId,
                                cipherId = cipherId,
                                itemType = BitwardenPendingOperation.ITEM_TYPE_PASSKEY
                            )
                            if (queueResult.isFailure) {
                                Toast.makeText(context, "操作失败", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                        }
                        viewModel.updatePasskey(
                            passkey.copy(
                                boundPasswordId = password.id,
                                categoryId = password.categoryId,
                                bitwardenVaultId = null,
                                bitwardenCipherId = null,
                                syncStatus = "NONE"
                            )
                        )
                    }
                    passkeyToBind = null
                }
            }
        )
    }

    UnifiedMoveToCategoryBottomSheet(
        visible = passkeyToMoveCategory != null,
        onDismiss = { passkeyToMoveCategory = null },
        categories = categories,
        keepassDatabases = keepassDatabases,
        bitwardenVaults = bitwardenVaults,
        getBitwardenFolders = { vaultId -> database.bitwardenFolderDao().getFoldersByVaultFlow(vaultId) },
        getKeePassGroups = getKeePassGroups,
        onTargetSelected = { target ->
            val passkey = passkeyToMoveCategory ?: return@UnifiedMoveToCategoryBottomSheet
            scope.launch {
                val updateResult = applyStorageTarget(passkey, target)
                if (updateResult.isFailure) {
                    Toast.makeText(context, "Bitwarden 操作失败，未移动", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                viewModel.updatePasskey(updateResult.getOrThrow())
                val targetLabel = when (target) {
                    UnifiedMoveCategoryTarget.Uncategorized -> context.getString(R.string.category_none)
                    is UnifiedMoveCategoryTarget.MonicaCategory -> categoryMap[target.categoryId]?.name ?: context.getString(R.string.category_none)
                    is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> context.getString(R.string.filter_bitwarden)
                    is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> context.getString(R.string.filter_bitwarden)
                    is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> keepassDatabases.find { it.id == target.databaseId }?.name ?: context.getString(R.string.filter_keepass)
                    is UnifiedMoveCategoryTarget.KeePassGroupTarget -> target.groupPath.substringAfterLast('/')
                }
                Toast.makeText(context, context.getString(R.string.passkey_category_updated, targetLabel), Toast.LENGTH_SHORT).show()
                passkeyToMoveCategory = null
            }
        }
    )

    UnifiedMoveToCategoryBottomSheet(
        visible = showBatchMoveCategoryDialog,
        onDismiss = { showBatchMoveCategoryDialog = false },
        categories = categories,
        keepassDatabases = keepassDatabases,
        bitwardenVaults = bitwardenVaults,
        getBitwardenFolders = { vaultId -> database.bitwardenFolderDao().getFoldersByVaultFlow(vaultId) },
        getKeePassGroups = getKeePassGroups,
        onTargetSelected = { target ->
            scope.launch {
                val selectedItems = combinedPasskeys.filter { selectedPasskeys.contains(it.credentialId) }
                val movable = selectedItems.filter { it.boundPasswordId == null && it.syncStatus != "REFERENCE" }
                val lockedCount = selectedItems.size - movable.size
                var movedCount = 0
                var failedCount = 0

                movable.forEach { passkey ->
                    val updateResult = applyStorageTarget(passkey, target)
                    if (updateResult.isSuccess) {
                        viewModel.updatePasskey(updateResult.getOrThrow())
                        movedCount++
                    } else {
                        failedCount++
                    }
                }

                val baseMessage = context.getString(R.string.selected_items, movedCount)
                val skippedTotal = lockedCount + failedCount
                val toastMessage = if (skippedTotal > 0) "$baseMessage，跳过$skippedTotal" else baseMessage
                Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()

                showBatchMoveCategoryDialog = false
                selectionMode = false
                selectedPasskeys = emptySet()
            }
        }
    )
}

private fun encodePasskeyCategoryFilter(filter: UnifiedCategoryFilterSelection): SavedCategoryFilterState = when (filter) {
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

private fun decodePasskeyCategoryFilter(state: SavedCategoryFilterState): UnifiedCategoryFilterSelection {
    return when (state.type) {
        "all" -> UnifiedCategoryFilterSelection.All
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

private fun formatPasswordSummary(entry: PasswordEntry): String {
    val parts = listOf(entry.title, entry.username, entry.website).filter { it.isNotBlank() }
    return if (parts.isEmpty()) entry.title else parts.joinToString(" · ")
}

@Composable
private fun PasswordPickerDialog(
    passwords: List<PasswordEntry>,
    onDismiss: () -> Unit,
    onPasswordSelected: (PasswordEntry) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredPasswords = remember(passwords, searchQuery) {
        if (searchQuery.isBlank()) {
            passwords
        } else {
            passwords.filter { entry ->
                entry.title.contains(searchQuery, ignoreCase = true) ||
                    entry.username.contains(searchQuery, ignoreCase = true) ||
                    entry.website.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.select_password_to_bind),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(filteredPasswords) { password ->
                        ListItem(
                            headlineContent = { Text(password.title) },
                            supportingContent = {
                                val parts = listOf(password.username, password.website).filter { it.isNotBlank() }
                                if (parts.isNotEmpty()) {
                                    Text(parts.joinToString(" · "))
                                }
                            },
                            leadingContent = {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = password.title.firstOrNull()?.toString()?.uppercase() ?: "?",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .clickable { onPasswordSelected(password) }
                                .fillMaxWidth()
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

/**
 * 版本警告横幅
 */
@Composable
private fun VersionWarningBanner(
    androidVersion: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.passkey_version_warning_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.passkey_version_warning_message, androidVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Passkey 分组标题
 */
@Composable
private fun PasskeyGroupHeader(
    rpId: String,
    rpName: String,
    count: Int
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rpName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rpName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = rpId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = count.toString(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Passkey 列表项（与密码列表风格完全一致 - M3 Expressive 设计）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PasskeyListItem(
    passkey: PasskeyEntry,
    boundPassword: PasswordEntry?,
    currentCategoryName: String,
    isCategoryLocked: Boolean = false,
    iconCardsEnabled: Boolean = false,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onDeleteRequest: () -> Unit = {},
    onBindPassword: () -> Unit = {},
    onUnbindPassword: () -> Unit = {},
    onOpenBoundPassword: (Long) -> Unit = {},
    onChangeCategory: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val syncStatus = remember(passkey.syncStatus) { SyncStatus.fromDbValue(passkey.syncStatus) }
    LaunchedEffect(selectionMode) {
        if (selectionMode && expanded) {
            expanded = false
        }
    }
    val rpWebsite = remember(passkey.rpId) {
        val rpId = passkey.rpId.trim()
        when {
            rpId.isBlank() -> ""
            "://" in rpId -> rpId
            else -> "https://$rpId"
        }
    }
    val iconWebsite = remember(passkey.iconUrl, rpWebsite, boundPassword?.website) {
        when {
            rpWebsite.isNotBlank() -> rpWebsite
            !passkey.iconUrl.isNullOrBlank() -> passkey.iconUrl.trim()
            !boundPassword?.website.isNullOrBlank() -> boundPassword?.website?.trim().orEmpty()
            else -> ""
        }
    }
    val iconTitle = remember(passkey.rpName, passkey.userDisplayName, passkey.userName) {
        passkey.rpName.ifBlank { passkey.userDisplayName.ifBlank { passkey.userName } }
    }
    val autoMatchedSimpleIcon = rememberAutoMatchedSimpleIcon(
        website = iconWebsite,
        title = iconTitle,
        appPackageName = boundPassword?.appPackageName?.takeIf { it.isNotBlank() },
        tintColor = MaterialTheme.colorScheme.primary,
        enabled = iconCardsEnabled
    )
    val favicon = if (iconWebsite.isNotBlank()) {
        rememberFavicon(
            url = iconWebsite,
            enabled = iconCardsEnabled && autoMatchedSimpleIcon.resolved && autoMatchedSimpleIcon.slug == null
        )
    } else {
        null
    }
    val appIcon = if (iconCardsEnabled && !boundPassword?.appPackageName.isNullOrBlank()) {
        rememberAppIcon(boundPassword?.appPackageName.orEmpty())
    } else {
        null
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selectionMode && isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (selectionMode) {
                                onToggleSelect()
                            } else {
                                expanded = !expanded
                                onClick()
                            }
                        },
                        onLongClick = {
                            if (!selectionMode) {
                                onLongPress()
                            }
                        }
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：头像 + 标题区域
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Passkey 图标（无背景直出）
                    when {
                        iconCardsEnabled && autoMatchedSimpleIcon.bitmap != null -> {
                            Image(
                                bitmap = autoMatchedSimpleIcon.bitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(40.dp)
                            )
                        }
                        iconCardsEnabled && favicon != null -> {
                            Image(
                                bitmap = favicon,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(40.dp)
                            )
                        }
                        iconCardsEnabled && appIcon != null -> {
                            Image(
                                bitmap = appIcon,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(40.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = passkey.userDisplayName.ifBlank { passkey.userName },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = passkey.rpName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (selectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() }
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SyncStatusIcon(
                            status = syncStatus.takeIf { passkey.bitwardenVaultId != null || it == SyncStatus.FAILED },
                            size = 16.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) {
                                stringResource(R.string.collapse)
                            } else {
                                stringResource(R.string.expand)
                            },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // 展开内容
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    
                    // 域名
                    DetailRow(
                        label = stringResource(R.string.passkey_rp_id),
                        value = passkey.rpId,
                        icon = Icons.Default.Language
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 用户名
                    DetailRow(
                        label = stringResource(R.string.passkey_username),
                        value = passkey.userName,
                        icon = Icons.Default.Person
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 创建时间
                    DetailRow(
                        label = stringResource(R.string.passkey_created_at),
                        value = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(passkey.createdAt)),
                        icon = Icons.Default.Schedule
                    )
                    
                    if (passkey.lastUsedAt > passkey.createdAt) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 最后使用时间
                        DetailRow(
                            label = stringResource(R.string.passkey_last_used),
                            value = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(passkey.lastUsedAt)),
                            icon = Icons.Default.History
                        )
                    }
                    
                    if (passkey.useCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 使用次数
                        DetailRow(
                            label = stringResource(R.string.passkey_use_count),
                            value = passkey.useCount.toString(),
                            icon = Icons.Default.Numbers
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    SyncStatusBadge(
                        status = syncStatus.takeIf { passkey.bitwardenVaultId != null || it == SyncStatus.FAILED },
                        showLabel = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    DetailRow(
                        label = stringResource(R.string.category),
                        value = currentCategoryName,
                        icon = Icons.Default.Folder
                    )

                    if (isCategoryLocked) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.passkey_category_follow_binding),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (onChangeCategory != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onChangeCategory,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.move_to_category))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 绑定密码
                    if (boundPassword != null) {
                        DetailRow(
                            label = stringResource(R.string.bind_password),
                            value = formatPasswordSummary(boundPassword),
                            icon = Icons.Default.Lock
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onOpenBoundPassword(boundPassword.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.details))
                            }
                            OutlinedButton(
                                onClick = onBindPassword,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.bound_password_change))
                            }
                            OutlinedButton(
                                onClick = onUnbindPassword,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(stringResource(R.string.unbind))
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onBindPassword,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.bind_password))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 删除按钮
                    OutlinedButton(
                        onClick = onDeleteRequest,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.passkey_delete_button))
                    }
                }
            }
        }
    }
}

/**
 * Passkey 多选胶囊操作栏（左下角）
 */
@Composable
private fun PasskeySelectionActionBar(
    modifier: Modifier = Modifier,
    selectedCount: Int,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onMoveToCategory: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(
                    text = selectedCount.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            PasskeyActionIcon(
                icon = Icons.Default.CheckCircle,
                contentDescription = stringResource(id = R.string.select_all),
                onClick = onSelectAll
            )

            PasskeyActionIcon(
                icon = Icons.Default.Folder,
                contentDescription = stringResource(id = R.string.move_to_category),
                onClick = onMoveToCategory
            )

            PasskeyActionIcon(
                icon = Icons.Default.Delete,
                contentDescription = stringResource(id = R.string.delete),
                onClick = onDelete
            )

            Spacer(modifier = Modifier.width(4.dp))

            PasskeyActionIcon(
                icon = Icons.Default.Close,
                contentDescription = stringResource(id = R.string.close),
                onClick = onExit
            )
        }
    }
}

@Composable
private fun PasskeyActionIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 详情行组件
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
