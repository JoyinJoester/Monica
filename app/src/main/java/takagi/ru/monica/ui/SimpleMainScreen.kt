package takagi.ru.monica.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel
import takagi.ru.monica.ui.screens.SettingsScreen

/**
 * 带有底部导航的主屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleMainScreen(
    passwordViewModel: PasswordViewModel,
    settingsViewModel: SettingsViewModel,
    totpViewModel: takagi.ru.monica.viewmodel.TotpViewModel,
    bankCardViewModel: takagi.ru.monica.viewmodel.BankCardViewModel,
    documentViewModel: takagi.ru.monica.viewmodel.DocumentViewModel,
    onNavigateToAddPassword: (Long?) -> Unit,
    onNavigateToAddTotp: (Long?) -> Unit,
    onNavigateToAddBankCard: (Long?) -> Unit,
    onNavigateToAddDocument: (Long?) -> Unit,
    onNavigateToChangePassword: () -> Unit = {},
    onNavigateToSecurityQuestion: () -> Unit = {},
    onNavigateToSupportAuthor: () -> Unit = {},
    onNavigateToExportData: () -> Unit = {},
    onNavigateToImportData: () -> Unit = {},
    onClearAllData: () -> Unit = {},
    initialTab: Int = 0
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    
    // 密码列表的选择模式状态
    var isPasswordSelectionMode by remember { mutableStateOf(false) }
    var selectedPasswordCount by remember { mutableIntStateOf(0) }
    var onExitPasswordSelection by remember { mutableStateOf({}) }
    var onSelectAllPasswords by remember { mutableStateOf({}) }
    var onDeleteSelectedPasswords by remember { mutableStateOf({}) }
    
    // TOTP的选择模式状态
    var isTotpSelectionMode by remember { mutableStateOf(false) }
    var selectedTotpCount by remember { mutableIntStateOf(0) }
    var onExitTotpSelection by remember { mutableStateOf({}) }
    var onSelectAllTotp by remember { mutableStateOf({}) }
    var onDeleteSelectedTotp by remember { mutableStateOf({}) }
    
    // 证件的选择模式状态
    var isDocumentSelectionMode by remember { mutableStateOf(false) }
    var selectedDocumentCount by remember { mutableIntStateOf(0) }
    var onExitDocumentSelection by remember { mutableStateOf({}) }
    var onSelectAllDocuments by remember { mutableStateOf({}) }
    var onDeleteSelectedDocuments by remember { mutableStateOf({}) }
    
    // 银行卡的选择模式状态
    var isBankCardSelectionMode by remember { mutableStateOf(false) }
    var selectedBankCardCount by remember { mutableIntStateOf(0) }
    var onExitBankCardSelection by remember { mutableStateOf({}) }
    var onSelectAllBankCards by remember { mutableStateOf({}) }
    var onDeleteSelectedBankCards by remember { mutableStateOf({}) }

    val tabs = listOf(
        BottomNavItem.Passwords to stringResource(R.string.nav_passwords_short),
        BottomNavItem.Authenticator to stringResource(R.string.nav_authenticator_short),
        BottomNavItem.Documents to stringResource(R.string.nav_documents_short),
        BottomNavItem.BankCards to stringResource(R.string.nav_bank_cards_short),
        BottomNavItem.Settings to stringResource(R.string.nav_settings_short)
    )

    Scaffold(
        topBar = {
            // 根据不同页面的选择模式显示对应的顶栏
            when {
                // 密码页面选择模式
                selectedTab == 0 && isPasswordSelectionMode -> {
                    SelectionModeTopBar(
                        selectedCount = selectedPasswordCount,
                        onExit = { onExitPasswordSelection() },
                        onSelectAll = { onSelectAllPasswords() },
                        onDelete = { onDeleteSelectedPasswords() }
                    )
                }
                // TOTP页面选择模式
                selectedTab == 1 && isTotpSelectionMode -> {
                    SelectionModeTopBar(
                        selectedCount = selectedTotpCount,
                        onExit = { onExitTotpSelection() },
                        onSelectAll = { onSelectAllTotp() },
                        onDelete = { onDeleteSelectedTotp() }
                    )
                }
                // 证件页面选择模式
                selectedTab == 2 && isDocumentSelectionMode -> {
                    SelectionModeTopBar(
                        selectedCount = selectedDocumentCount,
                        onExit = { onExitDocumentSelection() },
                        onSelectAll = { onSelectAllDocuments() },
                        onDelete = { onDeleteSelectedDocuments() }
                    )
                }
                // 银行卡页面选择模式
                selectedTab == 3 && isBankCardSelectionMode -> {
                    SelectionModeTopBar(
                        selectedCount = selectedBankCardCount,
                        onExit = { onExitBankCardSelection() },
                        onSelectAll = { onSelectAllBankCards() },
                        onDelete = { onDeleteSelectedBankCards() }
                    )
                }
                // 正常顶栏
                else -> {
                    TopAppBar(
                        title = { 
                            Text(tabs[selectedTab].second)
                        }
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, (item, label) ->
                    NavigationBarItem(
                        icon = { 
                            Icon(item.icon, contentDescription = label) 
                        },
                        label = { 
                            Text(label) 
                        },
                        selected = selectedTab == index,
                        onClick = { 
                            selectedTab = index 
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab in 0..3) { // 只在数据页面显示FAB
                FloatingActionButton(
                    onClick = { 
                        when (selectedTab) {
                            0 -> onNavigateToAddPassword(null)
                            1 -> onNavigateToAddTotp(null)
                            2 -> onNavigateToAddDocument(null)
                            3 -> onNavigateToAddBankCard(null)
                            else -> {
                                // 不应该到达这里
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> {
                    // 密码页面 - 使用现有的密码列表
                    PasswordListContent(
                        viewModel = passwordViewModel,
                        onPasswordClick = { password ->
                            onNavigateToAddPassword(password.id)
                        },
                        onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onDelete ->
                            isPasswordSelectionMode = isSelectionMode
                            selectedPasswordCount = count
                            onExitPasswordSelection = onExit
                            onSelectAllPasswords = onSelectAll
                            onDeleteSelectedPasswords = onDelete
                        }
                    )
                }
                1 -> {
                    // TOTP验证器页面
                    TotpListContent(
                        viewModel = totpViewModel,
                        onTotpClick = { totpId ->
                            onNavigateToAddTotp(totpId)
                        },
                        onDeleteTotp = { totp ->
                            totpViewModel.deleteTotpItem(totp)
                        },
                        onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onDelete ->
                            isTotpSelectionMode = isSelectionMode
                            selectedTotpCount = count
                            onExitTotpSelection = onExit
                            onSelectAllTotp = onSelectAll
                            onDeleteSelectedTotp = onDelete
                        }
                    )
                }
                2 -> {
                    // 文档页面
                    DocumentListContent(
                        viewModel = documentViewModel,
                        onDocumentClick = { documentId ->
                            onNavigateToAddDocument(documentId)
                        },
                        onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onDelete ->
                            isDocumentSelectionMode = isSelectionMode
                            selectedDocumentCount = count
                            onExitDocumentSelection = onExit
                            onSelectAllDocuments = onSelectAll
                            onDeleteSelectedDocuments = onDelete
                        }
                    )
                }
                3 -> {
                    // 银行卡页面
                    BankCardListContent(
                        viewModel = bankCardViewModel,
                        onCardClick = { cardId ->
                            onNavigateToAddBankCard(cardId)
                        },
                        onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onDelete ->
                            isBankCardSelectionMode = isSelectionMode
                            selectedBankCardCount = count
                            onExitBankCardSelection = onExit
                            onSelectAllBankCards = onSelectAll
                            onDeleteSelectedBankCards = onDelete
                        }
                    )
                }
                4 -> {
                    // 设置页面 - 使用完整的SettingsScreen
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateBack = {}, // 在主屏幕中不需要返回
                        onResetPassword = onNavigateToChangePassword,
                        onSecurityQuestions = onNavigateToSecurityQuestion,
                        onSupportAuthor = onNavigateToSupportAuthor,
                        onExportData = onNavigateToExportData,
                        onImportData = onNavigateToImportData,
                        onClearAllData = onClearAllData,
                        showTopBar = false  // 在标签页中不显示顶栏
                    )
                }
            }
        }
    }
}

/**
 * 密码列表内容
 */
@Composable
private fun PasswordListContent(
    viewModel: PasswordViewModel,
    onPasswordClick: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onSelectionModeChange: (
        isSelectionMode: Boolean,
        selectedCount: Int,
        onExit: () -> Unit,
        onSelectAll: () -> Unit,
        onDelete: () -> Unit
    ) -> Unit
) {
    val passwordEntries by viewModel.passwordEntries.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    // 选择模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedPasswords by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var showPasswordVerify by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // 定义回调函数
    val exitSelection = {
        isSelectionMode = false
        selectedPasswords = setOf()
    }
    
    val selectAll = {
        selectedPasswords = if (selectedPasswords.size == passwordEntries.size) {
            setOf()
        } else {
            passwordEntries.map { it.id }.toSet()
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
            deleteSelected
        )
    }

    Column {
        // 搜索框 - 非选择模式下显示
        if (!isSelectionMode) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text(stringResource(R.string.search_passwords_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp)
            )
        } else {
            // 选择模式下添加顶部间距,避免内容被顶栏遮挡
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 密码列表
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(
                items = passwordEntries,
                key = { it.id }
            ) { password ->
                PasswordEntryCard(
                    entry = password,
                    onClick = { 
                        if (isSelectionMode) {
                            // 选择模式下切换选择状态
                            selectedPasswords = if (selectedPasswords.contains(password.id)) {
                                selectedPasswords - password.id
                            } else {
                                selectedPasswords + password.id
                            }
                        } else {
                            onPasswordClick(password)
                        }
                    },
                    onLongClick = {
                        // 长按进入选择模式
                        if (!isSelectionMode) {
                            isSelectionMode = true
                            selectedPasswords = setOf(password.id)
                        }
                    },
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedPasswords.contains(password.id)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
    
    // 批量删除确认对话框
    if (showBatchDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showBatchDeleteDialog = false
                passwordInput = ""
            },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.batch_delete_passwords_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.batch_delete_passwords_message, selectedPasswords.size))
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.enter_master_password_confirm)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showPasswordVerify = true
                        showBatchDeleteDialog = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = passwordInput.isNotEmpty()
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showBatchDeleteDialog = false
                        passwordInput = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // 批量删除密码验证
    if (showPasswordVerify) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val securityManager = takagi.ru.monica.security.SecurityManager(context)
            if (securityManager.verifyMasterPassword(passwordInput)) {
                // 批量删除
                coroutineScope.launch {
                    val toDelete = passwordEntries.filter { selectedPasswords.contains(it.id) }
                    toDelete.forEach { viewModel.deletePasswordEntry(it) }
                    
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.deleted_items, toDelete.size),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    
                    // 退出选择模式
                    isSelectionMode = false
                    selectedPasswords = setOf()
                    passwordInput = ""
                    showPasswordVerify = false
                }
            } else {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.current_password_incorrect),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                showPasswordVerify = false
                showBatchDeleteDialog = true
            }
        }
    }
}

/**
 * TOTP列表内容
 */
@Composable
private fun TotpListContent(
    viewModel: takagi.ru.monica.viewmodel.TotpViewModel,
    onTotpClick: (Long) -> Unit,
    onDeleteTotp: (takagi.ru.monica.data.SecureItem) -> Unit,
    onSelectionModeChange: (
        isSelectionMode: Boolean,
        selectedCount: Int,
        onExit: () -> Unit,
        onSelectAll: () -> Unit,
        onDelete: () -> Unit
    ) -> Unit
) {
    val totpItems by viewModel.totpItems.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    // 选择模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var showPasswordVerify by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // 添加单项删除对话框状态
    var itemToDelete by remember { mutableStateOf<takagi.ru.monica.data.SecureItem?>(null) }
    
    // 定义回调函数
    val exitSelection = {
        isSelectionMode = false
        selectedItems = setOf()
    }
    
    val selectAll = {
        selectedItems = if (selectedItems.size == totpItems.size) {
            setOf()
        } else {
            totpItems.map { it.id }.toSet()
        }
    }
    
    val deleteSelected = {
        showBatchDeleteDialog = true
    }
    
    // 通知父组件选择模式状态变化
    LaunchedEffect(isSelectionMode, selectedItems.size) {
        onSelectionModeChange(
            isSelectionMode,
            selectedItems.size,
            exitSelection,
            selectAll,
            deleteSelected
        )
    }

    Column {
        // 搜索框 - 非选择模式下显示
        if (!isSelectionMode) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text(stringResource(R.string.search_authenticator)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true,
                shape = RoundedCornerShape(28.dp)
            )
        } else {
            // 选择模式下添加顶部间距
            Spacer(modifier = Modifier.height(8.dp))
        }

        // TOTP列表
        if (totpItems.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.no_authenticators_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.no_authenticators_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = totpItems,
                    key = { it.id }
                ) { item ->
                    TotpItemCard(
                        item = item,
                        onClick = { onTotpClick(item.id) },
                        onDelete = {
                            itemToDelete = item
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
    
    // 单项删除确认对话框
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.delete_authenticator_title)) },
            text = { Text(stringResource(R.string.delete_authenticator_message, item.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTotp(item)
                        itemToDelete = null
                    }
                ) {
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
    
    // 批量删除确认对话框
    if (showBatchDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showBatchDeleteDialog = false
                passwordInput = ""
            },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.batch_delete_totp_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.batch_delete_totp_message, selectedItems.size))
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.enter_master_password_confirm)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showPasswordVerify = true
                        showBatchDeleteDialog = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = passwordInput.isNotEmpty()
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showBatchDeleteDialog = false
                        passwordInput = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // 批量删除验证
    if (showPasswordVerify) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val securityManager = takagi.ru.monica.security.SecurityManager(context)
            if (securityManager.verifyMasterPassword(passwordInput)) {
                // 批量删除
                coroutineScope.launch {
                    val toDelete = totpItems.filter { selectedItems.contains(it.id) }
                    toDelete.forEach { onDeleteTotp(it) }
                    
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.deleted_items, toDelete.size),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    
                    // 退出选择模式
                    isSelectionMode = false
                    selectedItems = setOf()
                    passwordInput = ""
                    showPasswordVerify = false
                }
            } else {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.current_password_incorrect),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                showPasswordVerify = false
                showBatchDeleteDialog = true
            }
        }
    }
}

/**
 * TOTP项卡片
 */
@Composable
private fun TotpItemCard(
    item: takagi.ru.monica.data.SecureItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // 直接使用修改后的 TotpCodeCard 组件
    takagi.ru.monica.ui.components.TotpCodeCard(
        item = item,
        onClick = onClick,
        onCopyCode = { code ->
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("TOTP Code", code)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(context, "验证码已复制", android.widget.Toast.LENGTH_SHORT).show()
        },
        onDelete = onDelete
    )
}

/**
 * 银行卡列表内容
 */
@Composable
private fun BankCardListContent(
    viewModel: takagi.ru.monica.viewmodel.BankCardViewModel,
    onCardClick: (Long) -> Unit,
    onSelectionModeChange: (Boolean, Int, () -> Unit, () -> Unit, () -> Unit) -> Unit
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var masterPassword by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val cards by viewModel.allCards.collectAsState(initial = emptyList())
    
    // 添加单项删除对话框状态
    var itemToDelete by remember { mutableStateOf<takagi.ru.monica.data.SecureItem?>(null) }
    
    // 通知父组件选择模式状态变化
    LaunchedEffect(isSelectionMode, selectedItems.size) {
        if (isSelectionMode) {
            onSelectionModeChange(
                true,
                selectedItems.size,
                {
                    isSelectionMode = false
                    selectedItems = emptySet()
                },
                {
                    selectedItems = if (selectedItems.size == cards.size) {
                        emptySet()
                    } else {
                        cards.map { it.id }.toSet()
                    }
                },
                {
                    showPasswordDialog = true
                }
            )
        } else {
            onSelectionModeChange(false, 0, {}, {}, {})
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.batch_delete_bankcards_title)) },
            text = { Text(stringResource(R.string.batch_delete_bankcards_message, selectedItems.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        showPasswordDialog = true
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
    
    // 主密码验证对话框
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { 
                showPasswordDialog = false
                masterPassword = ""
            },
            title = { Text(stringResource(R.string.enter_master_password_confirm)) },
            text = {
                OutlinedTextField(
                    value = masterPassword,
                    onValueChange = { masterPassword = it },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            // TODO: 验证主密码
                            // 这里简化处理,直接删除
                            selectedItems.forEach { id ->
                                viewModel.deleteCard(id)
                            }
                            showPasswordDialog = false
                            masterPassword = ""
                            isSelectionMode = false
                            selectedItems = emptySet()
                        }
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showPasswordDialog = false
                        masterPassword = ""
                    }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
    
    if (cards.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_bank_cards_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = cards,
                key = { it.id }
            ) { card ->
                BankCardItemCard(
                    item = card,
                    onClick = { onCardClick(card.id) },
                    onDelete = {
                        itemToDelete = card
                    }
                )
            }
        }
    }
    
    // 单项删除确认对话框
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.delete_bank_card_title)) },
            text = { Text(stringResource(R.string.delete_bank_card_message, item.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCard(item.id)
                        itemToDelete = null
                    }
                ) {
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
}

@Composable
private fun BankCardItemCard(
    item: takagi.ru.monica.data.SecureItem,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    takagi.ru.monica.ui.components.BankCardCard(
        item = item,
        onClick = onClick,
        onDelete = onDelete
    )
}

/**
 * 证件列表内容
 */
@Composable
private fun DocumentListContent(
    viewModel: takagi.ru.monica.viewmodel.DocumentViewModel,
    onDocumentClick: (Long) -> Unit,
    onSelectionModeChange: (Boolean, Int, () -> Unit, () -> Unit, () -> Unit) -> Unit
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var masterPassword by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val documents by viewModel.allDocuments.collectAsState(initial = emptyList())
    
    // 添加单项删除对话框状态
    var itemToDelete by remember { mutableStateOf<takagi.ru.monica.data.SecureItem?>(null) }
    
    // 通知父组件选择模式状态变化
    LaunchedEffect(isSelectionMode, selectedItems.size) {
        if (isSelectionMode) {
            onSelectionModeChange(
                true,
                selectedItems.size,
                {
                    isSelectionMode = false
                    selectedItems = emptySet()
                },
                {
                    selectedItems = if (selectedItems.size == documents.size) {
                        emptySet()
                    } else {
                        documents.map { it.id }.toSet()
                    }
                },
                {
                    showPasswordDialog = true
                }
            )
        } else {
            onSelectionModeChange(false, 0, {}, {}, {})
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.batch_delete_documents_title)) },
            text = { Text(stringResource(R.string.batch_delete_documents_message, selectedItems.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        showPasswordDialog = true
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
    
    // 主密码验证对话框
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { 
                showPasswordDialog = false
                masterPassword = ""
            },
            title = { Text(stringResource(R.string.enter_master_password_confirm)) },
            text = {
                OutlinedTextField(
                    value = masterPassword,
                    onValueChange = { masterPassword = it },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            // TODO: 验证主密码
                            // 这里简化处理,直接删除
                            selectedItems.forEach { id ->
                                viewModel.deleteDocument(id)
                            }
                            showPasswordDialog = false
                            masterPassword = ""
                            isSelectionMode = false
                            selectedItems = emptySet()
                        }
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showPasswordDialog = false
                        masterPassword = ""
                    }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
    
    if (documents.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_documents_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = documents,
                key = { it.id }
            ) { document ->
                DocumentItemCard(
                    item = document,
                    onClick = { onDocumentClick(document.id) },
                    onDelete = {
                        itemToDelete = document
                    }
                )
            }
        }
    }
    
    // 单项删除确认对话框
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.delete_document_title)) },
            text = { Text(stringResource(R.string.delete_document_message, item.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDocument(item.id)
                        itemToDelete = null
                    }
                ) {
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
}

@Composable
private fun DocumentItemCard(
    item: takagi.ru.monica.data.SecureItem,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    takagi.ru.monica.ui.components.DocumentCard(
        item = item,
        onClick = onClick,
        onDelete = onDelete
    )
}

/**
 * 密码条目卡片
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PasswordEntryCard(
    entry: takagi.ru.monica.data.PasswordEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = if (isSelected) {
            androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            androidx.compose.material3.CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 选择模式下显示复选框
                if (isSelectionMode) {
                    androidx.compose.material3.Checkbox(
                        checked = isSelected,
                        onCheckedChange = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                // 标题和信息
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (entry.isFavorite) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = stringResource(R.string.favorite),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    if (entry.website.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entry.website,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (entry.username.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entry.username,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 选择模式顶栏组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionModeTopBar(
    selectedCount: Int,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        title = { 
            Text(stringResource(R.string.selected_items, selectedCount))
        },
        navigationIcon = {
            IconButton(onClick = onExit) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.exit_selection_mode))
            }
        },
        actions = {
            // 全选/取消全选
            IconButton(onClick = onSelectAll) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = stringResource(R.string.select_all)
                )
            }
            
            // 批量删除按钮
            IconButton(
                onClick = onDelete,
                enabled = selectedCount > 0
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.batch_delete),
                    tint = if (selectedCount > 0) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

/**
 * 底部导航项目
 */
sealed class BottomNavItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    object Passwords : BottomNavItem(Icons.Default.Lock)
    object Authenticator : BottomNavItem(Icons.Default.Security)
    object Documents : BottomNavItem(Icons.Default.Description)
    object BankCards : BottomNavItem(Icons.Default.CreditCard)
    object Settings : BottomNavItem(Icons.Default.Settings)
}
