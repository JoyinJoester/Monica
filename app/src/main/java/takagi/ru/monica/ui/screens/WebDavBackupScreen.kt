package takagi.ru.monica.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.repository.LedgerRepository
import takagi.ru.monica.data.ledger.LedgerCategory
import takagi.ru.monica.utils.BackupFile
import takagi.ru.monica.utils.BackupContent
import takagi.ru.monica.utils.WebDavHelper
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavBackupScreen(
    passwordRepository: PasswordRepository,
    secureItemRepository: SecureItemRepository,
    ledgerRepository: LedgerRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    var isConfigured by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var backupList by remember { mutableStateOf<List<BackupFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val webDavHelper = remember { WebDavHelper(context) }
    
    // 启动时检查是否已有配置
    LaunchedEffect(Unit) {
        if (webDavHelper.isConfigured()) {
            isConfigured = true
            // 自动加载备份列表
            isLoading = true
            val result = webDavHelper.listBackups()
            isLoading = false
            if (result.isSuccess) {
                backupList = result.getOrNull() ?: emptyList()
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.webdav_backup)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 配置卡片
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.webdav_config),
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    // 服务器地址
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { 
                            serverUrl = it
                            isConfigured = false
                        },
                        label = { Text(stringResource(R.string.webdav_server_url)) },
                        placeholder = { Text("https://example.com/webdav") },
                        leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        enabled = !isConfigured
                    )
                    
                    // 用户名
                    OutlinedTextField(
                        value = username,
                        onValueChange = { 
                            username = it
                            isConfigured = false
                        },
                        label = { Text(stringResource(R.string.username_email)) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true,
                        enabled = !isConfigured
                    )
                    
                    // 密码
                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            password = it
                            isConfigured = false
                        },
                        label = { Text(stringResource(R.string.password_required)) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) stringResource(R.string.hide_password) else stringResource(R.string.show_password)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        enabled = !isConfigured
                    )
                    
                    // 测试连接按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isConfigured) {
                            Button(
                                onClick = {
                                    if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                                        errorMessage = context.getString(R.string.webdav_fill_all_fields)
                                        return@Button
                                    }
                                    
                                    isTesting = true
                                    errorMessage = ""
                                    webDavHelper.configure(serverUrl, username, password)
                                    
                                    coroutineScope.launch {
                                        webDavHelper.testConnection().fold(
                                            onSuccess = {
                                                isConfigured = true
                                                isTesting = false
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.webdav_connection_success),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                // 加载备份列表
                                                loadBackups(webDavHelper) { list, error ->
                                                    backupList = list
                                                    error?.let { errorMessage = it }
                                                }
                                            },
                                            onFailure = { e ->
                                                isTesting = false
                                                errorMessage = context.getString(R.string.webdav_connection_failed, e.message)
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isTesting && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.webdav_test_connection))
                            }
                        } else {
                            // 已配置状态显示重新配置和清除配置按钮
                            OutlinedButton(
                                onClick = {
                                    isConfigured = false
                                    backupList = emptyList()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.webdav_reconfigure))
                            }
                            
                            OutlinedButton(
                                onClick = {
                                    webDavHelper.clearConfig()
                                    isConfigured = false
                                    serverUrl = ""
                                    username = ""
                                    password = ""
                                    backupList = emptyList()
                                    Toast.makeText(
                                        context,
                                        "已清除WebDAV配置",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("清除配置")
                            }
                        }
                    }
                    
                    // 错误信息
                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // 备份列表(仅在配置成功后显示)
            if (isConfigured) {
                // 创建备份按钮
                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = ""
                        coroutineScope.launch {
                            try {
                                // 获取所有密码数据
                                val allPasswords = passwordRepository.getAllPasswordEntries().first()
                                // 获取所有其他数据(TOTP、银行卡、证件)
                                val allSecureItems = secureItemRepository.getAllItems().first()
                                // 获取所有账本数据
                                val allLedgerEntries = ledgerRepository.observeEntries().first()
                                
                                // 创建并上传备份
                                val result = webDavHelper.createAndUploadBackup(allPasswords, allSecureItems, allLedgerEntries)
                                
                                if (result.isSuccess) {
                                    Toast.makeText(
                                        context,
                                        "备份成功: ${result.getOrNull()}\n已备份 ${allPasswords.size} 个密码、${allSecureItems.size} 个其他数据和 ${allLedgerEntries.size} 条账本记录",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    
                                    // 刷新备份列表
                                    loadBackups(webDavHelper) { list, error ->
                                        backupList = list
                                        isLoading = false
                                        error?.let { errorMessage = it }
                                    }
                                } else {
                                    isLoading = false
                                    val error = result.exceptionOrNull()?.message ?: "未知错误"
                                    errorMessage = error
                                    Toast.makeText(
                                        context,
                                        "备份失败: $error",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = e.message ?: "创建备份失败"
                                Toast.makeText(
                                    context,
                                    "创建备份失败: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("创建新备份")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.webdav_backup_list),
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            IconButton(
                                onClick = {
                                    isLoading = true
                                    coroutineScope.launch {
                                        loadBackups(webDavHelper) { list, error ->
                                            backupList = list
                                            isLoading = false
                                            error?.let { errorMessage = it }
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                            }
                        }
                        
                        if (isLoading) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else if (backupList.isEmpty()) {
                            Text(
                                text = stringResource(R.string.webdav_no_backups),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            backupList.forEach { backup ->
                                BackupItem(
                                    backup = backup,
                                    webDavHelper = webDavHelper,
                                    passwordRepository = passwordRepository,
                                    secureItemRepository = secureItemRepository,
                                    ledgerRepository = ledgerRepository,
                                    onDeleted = {
                                        backupList = backupList - backup
                                    },
                                    onRestoreSuccess = {
                                        Toast.makeText(
                                            context,
                                            "数据已成功恢复",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
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
private fun BackupItem(
    backup: BackupFile,
    webDavHelper: WebDavHelper,
    passwordRepository: PasswordRepository,
    secureItemRepository: SecureItemRepository,
    ledgerRepository: LedgerRepository,
    onDeleted: () -> Unit,
    onRestoreSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = backup.name,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${dateFormat.format(backup.modified)} • ${webDavHelper.formatFileSize(backup.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 恢复按钮
            IconButton(
                onClick = { showRestoreDialog = true },
                enabled = !isRestoring
            ) {
                if (isRestoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "恢复备份",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // 删除按钮
            IconButton(
                onClick = { showDeleteDialog = true }
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = context.getString(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    
    // 恢复确认对话框
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("恢复备份") },
            text = { 
                Text("确定要从此备份恢复数据吗?\n\n${backup.name}\n\n注意: 这将导入备份中的所有数据到当前应用中。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreDialog = false
                        isRestoring = true
                        coroutineScope.launch {
                            try {
                                // 下载并恢复备份
                                val result = webDavHelper.downloadAndRestoreBackup(backup)
                                
                                if (result.isSuccess) {
                                    val content = result.getOrNull() ?: BackupContent(emptyList(), emptyList(), emptyList())
                                    val passwords = content.passwords
                                    val secureItems = content.secureItems
                                    val ledgerBackups = content.ledgerEntries
                                    
                                    // 导入密码数据到数据库(带去重)
                                    var passwordCount = 0
                                    var passwordSkipped = 0
                                    passwords.forEach { password ->
                                        try {
                                            val isDuplicate = passwordRepository.isDuplicateEntry(
                                                password.title,
                                                password.username,
                                                password.website
                                            )
                                            if (!isDuplicate) {
                                                val newPassword = password.copy(id = 0)
                                                passwordRepository.insertPasswordEntry(newPassword)
                                                passwordCount++
                                            } else {
                                                passwordSkipped++
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("WebDavBackup", "Failed to import password: ${e.message}")
                                        }
                                    }
                                    
                                    // 导入其他数据到数据库(带去重)
                                    var secureItemCount = 0
                                    var secureItemSkipped = 0
                                    secureItems.forEach { exportItem ->
                                        try {
                                            val itemType = takagi.ru.monica.data.ItemType.valueOf(exportItem.itemType)
                                            val isDuplicate = secureItemRepository.isDuplicateItem(
                                                itemType,
                                                exportItem.title
                                            )
                                            if (!isDuplicate) {
                                                val secureItem = takagi.ru.monica.data.SecureItem(
                                                    id = 0,
                                                    itemType = itemType,
                                                    title = exportItem.title,
                                                    itemData = exportItem.itemData,
                                                    notes = exportItem.notes,
                                                    isFavorite = exportItem.isFavorite,
                                                    imagePaths = exportItem.imagePaths,
                                                    createdAt = java.util.Date(exportItem.createdAt),
                                                    updatedAt = java.util.Date(exportItem.updatedAt)
                                                )
                                                secureItemRepository.insertItem(secureItem)
                                                secureItemCount++
                                            } else {
                                                secureItemSkipped++
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("WebDavBackup", "Failed to import secure item: ${e.message}")
                                        }
                                    }

                                    // 导入账本数据
                                    var ledgerCount = 0
                                    var ledgerSkipped = 0
                                    if (ledgerBackups.isNotEmpty()) {
                                        val existingCategories = ledgerRepository.observeCategories().first().toMutableList()
                                        val existingLedgerEntries = ledgerRepository.observeEntries().first().map { it.entry }.toMutableList()
                                        val categoryCache = mutableMapOf<Pair<String, takagi.ru.monica.data.ledger.LedgerEntryType>, LedgerCategory>()
                                        ledgerBackups.forEach { backupEntry ->
                                            try {
                                                val ledgerEntry = backupEntry.entry
                                                val duplicate = existingLedgerEntries.any {
                                                    it.title == ledgerEntry.title &&
                                                        it.amountInCents == ledgerEntry.amountInCents &&
                                                        it.type == ledgerEntry.type &&
                                                        it.occurredAt.time == ledgerEntry.occurredAt.time &&
                                                        it.paymentMethod == ledgerEntry.paymentMethod &&
                                                        it.note == ledgerEntry.note
                                                }
                                                if (duplicate) {
                                                    ledgerSkipped++
                                                } else {
                                                    val categoryName = backupEntry.categoryName
                                                    var categoryId: Long? = null
                                                    if (!categoryName.isNullOrBlank()) {
                                                        val key = categoryName to ledgerEntry.type
                                                        var category = categoryCache[key]
                                                        if (category == null) {
                                                            category = existingCategories.firstOrNull {
                                                                it.name == categoryName && (it.type == null || it.type == ledgerEntry.type)
                                                            }
                                                        }
                                                        if (category == null) {
                                                            val newCategory = LedgerCategory(name = categoryName, type = ledgerEntry.type)
                                                            val newId = ledgerRepository.upsertCategory(newCategory)
                                                            category = newCategory.copy(id = newId)
                                                            existingCategories.add(category)
                                                        }
                                                        categoryCache[key] = category
                                                        categoryId = category.id
                                                    }
                                                    val newEntry = ledgerEntry.copy(id = 0, categoryId = categoryId)
                                                    val newId = ledgerRepository.upsertEntry(newEntry)
                                                    existingLedgerEntries.add(newEntry.copy(id = newId))
                                                    ledgerCount++
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("WebDavBackup", "Failed to import ledger entry: ${e.message}")
                                            }
                                        }
                                    }
                                    
                                    isRestoring = false
                                    val summaryParts = mutableListOf<String>()
                                    summaryParts += "$passwordCount 个密码"
                                    summaryParts += "$secureItemCount 个其他数据"
                                    if (ledgerBackups.isNotEmpty()) {
                                        summaryParts += "$ledgerCount 条账本记录"
                                    }
                                    val message = buildString {
                                        append("恢复成功! 导入了 ${summaryParts.joinToString("、")}")
                                        val skippedParts = mutableListOf<String>()
                                        if (passwordSkipped > 0) skippedParts += "$passwordSkipped 个重复密码"
                                        if (secureItemSkipped > 0) skippedParts += "$secureItemSkipped 个重复数据"
                                        if (ledgerSkipped > 0) skippedParts += "$ledgerSkipped 条重复账本记录"
                                        if (skippedParts.isNotEmpty()) {
                                            append("\n跳过 ${skippedParts.joinToString("、")}")
                                        }
                                    }
                                    Toast.makeText(
                                        context,
                                        message,
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onRestoreSuccess()
                                } else {
                                    isRestoring = false
                                    val error = result.exceptionOrNull()?.message ?: "未知错误"
                                    Toast.makeText(
                                        context,
                                        "恢复失败: $error",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } catch (e: Exception) {
                                isRestoring = false
                                Toast.makeText(
                                    context,
                                    "恢复失败: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) {
                    Text("恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(context.getString(R.string.delete_backup)) },
            text = { Text(context.getString(R.string.delete_backup_confirm, backup.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        coroutineScope.launch {
                            webDavHelper.deleteBackup(backup).fold(
                                onSuccess = {
                                    onDeleted()
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.backup_deleted),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onFailure = { e ->
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.delete_failed, e.message),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                ) {
                    Text(context.getString(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }
}

private suspend fun loadBackups(
    webDavHelper: WebDavHelper,
    onResult: (List<BackupFile>, String?) -> Unit
) {
    webDavHelper.listBackups().fold(
        onSuccess = { list ->
            onResult(list, null)
        },
        onFailure = { e ->
            onResult(emptyList(), e.message)
        }
    )
}
