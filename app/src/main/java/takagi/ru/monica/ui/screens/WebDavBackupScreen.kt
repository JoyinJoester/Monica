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
import takagi.ru.monica.utils.BackupFile
import takagi.ru.monica.utils.BackupContent
import takagi.ru.monica.utils.WebDavHelper
import takagi.ru.monica.utils.AutoBackupManager
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.flow.first
import java.text.DateFormat
import android.text.format.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavBackupScreen(
    passwordRepository: PasswordRepository,
    secureItemRepository: SecureItemRepository,
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
    
    // 自动备份状态
    var autoBackupEnabled by remember { mutableStateOf(false) }
    var lastBackupTime by remember { mutableStateOf(0L) }
    
    // 选择性备份状态
    var backupPreferences by remember { mutableStateOf(takagi.ru.monica.data.BackupPreferences()) }
    var passwordCount by remember { mutableStateOf(0) }
    var authenticatorCount by remember { mutableStateOf(0) }
    var documentCount by remember { mutableStateOf(0) }
    var bankCardCount by remember { mutableStateOf(0) }
    
    val webDavHelper = remember { WebDavHelper(context) }
    val autoBackupManager = remember { AutoBackupManager(context) }
    
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
        
        // 加载自动备份状态
        autoBackupEnabled = webDavHelper.isAutoBackupEnabled()
        lastBackupTime = webDavHelper.getLastBackupTime()
        
        // 加载备份偏好设置
        backupPreferences = webDavHelper.getBackupPreferences()
        
        // 加载各类型的数量
        passwordCount = passwordRepository.getAllPasswordEntries().first().size
        val allSecureItems = secureItemRepository.getAllItems().first()
        authenticatorCount = allSecureItems.count { it.itemType == takagi.ru.monica.data.ItemType.TOTP }
        documentCount = allSecureItems.count { it.itemType == takagi.ru.monica.data.ItemType.DOCUMENT }
        bankCardCount = allSecureItems.count { it.itemType == takagi.ru.monica.data.ItemType.BANK_CARD }
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
            // 配置信息卡片 (如果已配置)
            if (isConfigured) {
                webDavHelper.getCurrentConfig()?.let { config ->
                    WebDavConfigSummaryCard(
                        config = config,
                        onEdit = {
                            isConfigured = false
                            serverUrl = config.serverUrl
                            username = config.username
                        },
                        onClear = {
                            webDavHelper.clearConfig()
                            isConfigured = false
                            serverUrl = ""
                            username = ""
                            password = ""
                            backupList = emptyList()
                        }
                    )
                }
            }
            
            // 配置卡片
            if (!isConfigured) {
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
                                                // 提供更友好的错误信息
                                                val userFriendlyMessage = when {
                                                    e.message?.contains("网络不可达") == true -> 
                                                        context.getString(R.string.webdav_network_unreachable)
                                                    e.message?.contains("连接超时") == true -> 
                                                        context.getString(R.string.webdav_connection_timeout)
                                                    e.message?.contains("认证失败") == true -> 
                                                        context.getString(R.string.webdav_auth_failed)
                                                    e.message?.contains("服务器路径未找到") == true -> 
                                                        context.getString(R.string.webdav_path_not_found)
                                                    else -> e.message ?: context.getString(R.string.webdav_connection_failed, "")
                                                }
                                                errorMessage = userFriendlyMessage
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.webdav_connection_failed, userFriendlyMessage),
                                                    Toast.LENGTH_LONG
                                                ).show()
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
                                        context.getString(R.string.webdav_config_cleared),
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
                                Text(stringResource(R.string.webdav_clear_config))
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
            }
            
            // 自动备份设置卡片 (仅在配置成功后显示)
            if (isConfigured) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.webdav_auto_backup),
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        // 自动备份开关
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.webdav_auto_backup),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = stringResource(R.string.webdav_auto_backup_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Switch(
                                checked = autoBackupEnabled,
                                onCheckedChange = { enabled ->
                                    autoBackupEnabled = enabled
                                    webDavHelper.configureAutoBackup(enabled)
                                    
                                    Toast.makeText(
                                        context,
                                        if (enabled) {
                                            context.getString(R.string.webdav_auto_backup_enabled)
                                        } else {
                                            context.getString(R.string.webdav_auto_backup_disabled)
                                        },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                        
                        // 显示上次备份时间
                        if (lastBackupTime > 0) {
                            val relativeTime = DateUtils.getRelativeTimeSpanString(
                                lastBackupTime,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.webdav_last_backup) + " " + relativeTime,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // 立即备份按钮
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        autoBackupManager.triggerBackupNow()
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.webdav_backup_in_progress),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        
                                        // 延迟2秒后更新上次备份时间和刷新备份列表
                                        kotlinx.coroutines.delay(2000)
                                        lastBackupTime = webDavHelper.getLastBackupTime()
                                        
                                        // 刷新备份列表
                                        isLoading = true
                                        loadBackups(webDavHelper) { list, error ->
                                            backupList = list
                                            isLoading = false
                                            error?.let { errorMessage = it }
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.webdav_backup_trigger_failed, e.message ?: ""),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading && isConfigured
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.webdav_backup_now))
                        }
                    }
                }
                
                // 选择性备份设置卡片
                takagi.ru.monica.ui.components.SelectiveBackupCard(
                    preferences = backupPreferences,
                    onPreferencesChange = { newPreferences ->
                        backupPreferences = newPreferences
                        webDavHelper.saveBackupPreferences(newPreferences)
                    },
                    passwordCount = passwordCount,
                    authenticatorCount = authenticatorCount,
                    documentCount = documentCount,
                    bankCardCount = bankCardCount
                )
            }
            
            // 备份列表(仅在配置成功后显示)
            if (isConfigured) {
                // 创建备份按钮
                Button(
                    onClick = {
                        // 验证：检查是否至少选择了一种内容类型
                        if (!backupPreferences.hasAnyEnabled()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.backup_validation_error),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        
                        isLoading = true
                        errorMessage = ""
                        coroutineScope.launch {
                            try {
                                // 获取所有密码数据
                                val allPasswords = passwordRepository.getAllPasswordEntries().first()
                                // 获取所有其他数据(TOTP、银行卡、证件)
                                val allSecureItems = secureItemRepository.getAllItems().first()
                                
                                // 创建并上传备份（使用偏好设置）
                                val result = webDavHelper.createAndUploadBackup(
                                    allPasswords, 
                                    allSecureItems,
                                    backupPreferences
                                )
                                
                                if (result.isSuccess) {
                                    // 更新上次备份时间
                                    lastBackupTime = webDavHelper.getLastBackupTime()
                                    
                                    // 计算实际备份的数量
                                    val backedUpPasswords = if (backupPreferences.includePasswords) allPasswords.size else 0
                                    val backedUpTotp = if (backupPreferences.includeAuthenticators) 
                                        allSecureItems.count { it.itemType == takagi.ru.monica.data.ItemType.TOTP } else 0
                                    val backedUpDocs = if (backupPreferences.includeDocuments) 
                                        allSecureItems.count { it.itemType == takagi.ru.monica.data.ItemType.DOCUMENT } else 0
                                    val backedUpCards = if (backupPreferences.includeBankCards) 
                                        allSecureItems.count { it.itemType == takagi.ru.monica.data.ItemType.BANK_CARD } else 0
                                    
                                    val message = buildString {
                                        append(context.getString(R.string.webdav_backup_success_detail, result.getOrNull() ?: "") + "\n")
                                        if (backedUpPasswords > 0) append(context.getString(R.string.webdav_backup_passwords, backedUpPasswords) + "\n")
                                        if (backedUpTotp > 0) append(context.getString(R.string.webdav_backup_authenticators, backedUpTotp) + "\n")
                                        if (backedUpDocs > 0) append(context.getString(R.string.webdav_backup_documents, backedUpDocs) + "\n")
                                        if (backedUpCards > 0) append(context.getString(R.string.webdav_backup_bank_cards, backedUpCards))
                                    }
                                    
                                    Toast.makeText(
                                        context,
                                        message,
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
                                    val error = result.exceptionOrNull()?.message ?: context.getString(R.string.webdav_create_backup_failed)
                                    errorMessage = error
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.webdav_backup_failed, error),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = e.message ?: context.getString(R.string.webdav_create_backup_failed)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.webdav_backup_failed, e.message ?: ""),
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
                    Text(stringResource(R.string.webdav_create_new_backup))
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
                                    val content = result.getOrNull() ?: BackupContent(emptyList(), emptyList())
                                    val passwords = content.passwords
                                    val secureItems = content.secureItems
                                    
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
                                    
                                    isRestoring = false
                                    val summaryParts = mutableListOf<String>()
                                    summaryParts += "$passwordCount 个密码"
                                    summaryParts += "$secureItemCount 个其他数据"
                                    val message = buildString {
                                        append("恢复成功! 导入了 ${summaryParts.joinToString("、")}")
                                        val skippedParts = mutableListOf<String>()
                                        if (passwordSkipped > 0) skippedParts += "$passwordSkipped 个重复密码"
                                        if (secureItemSkipped > 0) skippedParts += "$secureItemSkipped 个重复数据"
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

/**
 * WebDAV 配置信息卡片
 */
@Composable
fun WebDavConfigSummaryCard(
    config: WebDavHelper.WebDavConfig,
    onEdit: () -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
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
                    text = "WebDAV 配置",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.webdav_reconfigure),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.webdav_clear_config),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            Divider()
            
            ConfigInfoRow(
                label = "服务器",
                value = config.serverUrl,
                icon = Icons.Default.CloudUpload
            )
            
            ConfigInfoRow(
                label = "用户名",
                value = config.username,
                icon = Icons.Default.Person
            )
        }
    }
}

/**
 * 配置信息行组件
 */
@Composable
fun ConfigInfoRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val context = LocalContext.current
    val clipboardManager = remember { 
        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager 
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        IconButton(
            onClick = {
                val clip = android.content.ClipData.newPlainText(label, value)
                clipboardManager.setPrimaryClip(clip)
                Toast.makeText(context, "已复制 $label", Toast.LENGTH_SHORT).show()
            }
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "复制 $label",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
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


