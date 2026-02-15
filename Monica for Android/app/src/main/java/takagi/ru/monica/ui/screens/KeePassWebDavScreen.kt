package takagi.ru.monica.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * KeePass .kdbx 文件信息
 */
data class KdbxFileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val modified: Date
)

/**
 * KeePass WebDAV 配置状态
 */
data class KeePassWebDavState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isConfigured: Boolean = false,
    val isConnecting: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.NotConnected,
    val kdbxFiles: List<KdbxFileInfo> = emptyList(),
    val isLoadingFiles: Boolean = false,
    val isImporting: Boolean = false,
    val errorMessage: String = ""
)

enum class ConnectionStatus {
    NotConnected,
    Connecting,
    Connected,
    Failed
}

/**
 * KeePass 兼容性配置页面
 * 用于配置 WebDAV 并处理 .kdbx 格式数据库的接入和创建
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeePassWebDavScreen(
    onNavigateBack: () -> Unit,
    viewModel: KeePassWebDavViewModel = remember { KeePassWebDavViewModel() }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    // UI 状态
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    // Kdbx 密码（用于加密/解密 .kdbx 文件）
    var kdbxPassword by remember { mutableStateOf("") }
    var kdbxPasswordVisible by remember { mutableStateOf(false) }
    var rememberKdbxPassword by remember { mutableStateOf(false) }  // 是否记住密码
    
    // 连接状态
    var isConfigured by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf(ConnectionStatus.NotConnected) }
    var errorMessage by remember { mutableStateOf("") }
    
    // 文件列表状态
    var kdbxFiles by remember { mutableStateOf<List<KdbxFileInfo>>(emptyList()) }
    var isLoadingFiles by remember { mutableStateOf(false) }
    
    // 操作状态
    var isCreating by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var isForcingOverwrite by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<KdbxFileInfo?>(null) }
    var forceOverwriteFile by remember { mutableStateOf<KdbxFileInfo?>(null) }
    var conflictProtectionEnabled by remember { mutableStateOf(false) }
    var attachedRemotePaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // 对话框状态
    var showImportDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createFileName by remember { mutableStateOf("") }

    suspend fun refreshRemoteFiles() {
        isLoadingFiles = true
        viewModel.listKdbxFiles(context).fold(
            onSuccess = { files ->
                kdbxFiles = files
                attachedRemotePaths = viewModel.getAttachedRemotePaths(context)
                isLoadingFiles = false
            },
            onFailure = { e ->
                errorMessage = e.message ?: context.getString(R.string.keepass_webdav_load_files_failed)
                isLoadingFiles = false
            }
        )
    }
    
    // 启动时加载已保存的配置
    LaunchedEffect(Unit) {
        // 加载 KeePass 数据库密码（无论是否配置 WebDAV）
        val savedKdbxPassword = viewModel.getSavedKdbxPassword(context)
        conflictProtectionEnabled = viewModel.isConflictProtectionEnabled(context)
        if (savedKdbxPassword.isNotEmpty()) {
            kdbxPassword = savedKdbxPassword
            rememberKdbxPassword = true  // 有保存的密码，自动勾选
        }
        
        viewModel.loadSavedConfig(context)?.let { config ->
            serverUrl = config.serverUrl
            username = config.username
            if (config.kdbxPassword.isNotEmpty()) {
                kdbxPassword = config.kdbxPassword
                rememberKdbxPassword = true
            }
            conflictProtectionEnabled = config.conflictProtectionEnabled
            isConfigured = true
            connectionStatus = ConnectionStatus.Connected
            
            // 自动加载文件列表
            refreshRemoteFiles()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.keepass_webdav_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (isConfigured) {
                        IconButton(
                            onClick = {
                                // 刷新文件列表
                                coroutineScope.launch {
                                    refreshRemoteFiles()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
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
            // ========== 卡片 A：WebDAV 配置 ==========
            WebDavConfigCard(
                serverUrl = serverUrl,
                username = username,
                password = password,
                passwordVisible = passwordVisible,
                isConfigured = isConfigured,
                isConnecting = isConnecting,
                connectionStatus = connectionStatus,
                errorMessage = errorMessage,
                onServerUrlChange = { serverUrl = it },
                onUsernameChange = { username = it },
                onPasswordChange = { password = it },
                onPasswordVisibilityChange = { passwordVisible = it },
                onTestConnection = {
                    if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                        errorMessage = context.getString(R.string.webdav_fill_all_fields)
                        return@WebDavConfigCard
                    }
                    
                    isConnecting = true
                    connectionStatus = ConnectionStatus.Connecting
                    errorMessage = ""
                    
                    coroutineScope.launch {
                        viewModel.configureAndTest(context, serverUrl, username, password).fold(
                            onSuccess = {
                                isConfigured = true
                                isConnecting = false
                                connectionStatus = ConnectionStatus.Connected
                                
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.webdav_connection_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                                
                                // 加载文件列表
                                refreshRemoteFiles()
                            },
                            onFailure = { e ->
                                isConnecting = false
                                connectionStatus = ConnectionStatus.Failed
                                errorMessage = e.message
                                    ?: context.getString(R.string.webdav_connection_failed, "")
                            }
                        )
                    }
                },
                onClearConfig = {
                    viewModel.clearConfig(context)
                    isConfigured = false
                    serverUrl = ""
                    username = ""
                    password = ""
                    connectionStatus = ConnectionStatus.NotConnected
                    kdbxFiles = emptyList()
                    attachedRemotePaths = emptySet()
                    errorMessage = ""
                }
            )
            
            // ========== 卡片 B：操作区域（仅在配置成功后显示）==========
            if (isConfigured) {
                ActionsCard(
                    kdbxPassword = kdbxPassword,
                    kdbxPasswordVisible = kdbxPasswordVisible,
                    rememberPassword = rememberKdbxPassword,
                    isCreating = isCreating,
                    isImporting = isImporting,
                    conflictProtectionEnabled = conflictProtectionEnabled,
                    onKdbxPasswordChange = { kdbxPassword = it },
                    onKdbxPasswordVisibilityChange = { kdbxPasswordVisible = it },
                    onRememberPasswordChange = { checked ->
                        rememberKdbxPassword = checked
                        if (checked && kdbxPassword.isNotBlank()) {
                            // 勾选时保存密码
                            viewModel.saveKdbxPassword(context, kdbxPassword)
                        } else if (!checked) {
                            // 取消勾选时清除保存的密码
                            viewModel.saveKdbxPassword(context, "")
                        }
                    },
                    onConflictProtectionChange = { enabled ->
                        conflictProtectionEnabled = enabled
                        viewModel.setConflictProtectionEnabled(context, enabled)
                    },
                    onCreateRemoteClick = {
                        if (kdbxPassword.isBlank()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.keepass_webdav_enter_database_password),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@ActionsCard
                        }
                        // 如果勾选了记住密码，创建前保存
                        if (rememberKdbxPassword) {
                            viewModel.saveKdbxPassword(context, kdbxPassword)
                        }
                        createFileName = ""
                        showCreateDialog = true
                    }
                )
            }
            
            // ========== 卡片 C：云端文件列表（仅在配置成功后显示）==========
            if (isConfigured) {
                RemoteFilesCard(
                    files = kdbxFiles,
                    attachedRemotePaths = attachedRemotePaths,
                    isLoading = isLoadingFiles,
                    isImporting = isImporting,
                    isForcingOverwrite = isForcingOverwrite,
                    onFileClick = { file ->
                        selectedFile = file
                        showImportDialog = true
                    },
                    onForceOverwriteClick = { file ->
                        forceOverwriteFile = file
                    },
                    onRefresh = {
                        coroutineScope.launch {
                            refreshRemoteFiles()
                        }
                    }
                )
            }
        }
    }
    
    // ========== 新建远端数据库对话框 ==========
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.keepass_webdav_create_title)) },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.keepass_webdav_create_message))
                    OutlinedTextField(
                        value = createFileName,
                        onValueChange = { createFileName = it },
                        label = { Text(stringResource(R.string.keepass_webdav_create_name_label)) },
                        placeholder = { Text(stringResource(R.string.keepass_webdav_create_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (createFileName.isBlank()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.keepass_webdav_create_name_required),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        showCreateDialog = false
                        isCreating = true
                        
                        coroutineScope.launch {
                            viewModel.createRemoteKdbxDatabase(context, createFileName, kdbxPassword).fold(
                                onSuccess = { file ->
                                    isCreating = false
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.keepass_webdav_create_success, file.name),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    
                                    // 刷新文件列表
                                    refreshRemoteFiles()
                                },
                                onFailure = { e ->
                                    isCreating = false
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.keepass_webdav_create_failed, e.message),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                    },
                    enabled = !isCreating
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.keepass_webdav_create_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // ========== 接入确认对话框 ==========
    if (showImportDialog && selectedFile != null) {
        var importPassword by remember { mutableStateOf(kdbxPassword) }
        var importPasswordVisible by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { 
                showImportDialog = false
                selectedFile = null
            },
            title = { Text(stringResource(R.string.keepass_webdav_attach_title)) },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.keepass_webdav_attach_message))
                    Text(
                        text = selectedFile!!.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        label = { Text(stringResource(R.string.keepass_webdav_database_password)) },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        visualTransformation = if (importPasswordVisible) 
                            VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { importPasswordVisible = !importPasswordVisible }) {
                                Icon(
                                    if (importPasswordVisible) Icons.Default.Visibility 
                                    else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importPassword.isBlank()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.keepass_webdav_enter_password),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        
                        val fileToImport = selectedFile
                        if (fileToImport == null) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.keepass_webdav_select_file_first),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        
                        showImportDialog = false
                        isImporting = true
                        selectedFile = null
                        
                        coroutineScope.launch {
                            try {
                                viewModel.attachRemoteKdbx(context, fileToImport, importPassword).fold(
                                    onSuccess = { count ->
                                        isImporting = false
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.keepass_webdav_attach_success, count),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    onFailure = { e ->
                                        isImporting = false
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.keepass_webdav_attach_failed,
                                                e.message ?: context.getString(R.string.import_data_unknown_error)
                                            ),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("KeePassWebDAV", "Import exception", e)
                                isImporting = false
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.keepass_webdav_attach_exception,
                                        e.message ?: e.javaClass.simpleName
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !isImporting
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.keepass_webdav_confirm_attach))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showImportDialog = false
                        selectedFile = null
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (forceOverwriteFile != null) {
        val targetFile = forceOverwriteFile!!
        AlertDialog(
            onDismissRequest = {
                if (!isForcingOverwrite) {
                    forceOverwriteFile = null
                }
            },
            title = {
                Text(stringResource(R.string.keepass_webdav_force_overwrite_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.keepass_webdav_force_overwrite_message))
                    Text(
                        text = targetFile.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = forceOverwriteFile ?: return@Button
                        forceOverwriteFile = null
                        isForcingOverwrite = true
                        coroutineScope.launch {
                            viewModel.forceOverwriteRemoteDatabase(
                                context = context,
                                remotePath = file.path,
                                kdbxPassword = kdbxPassword.takeIf { it.isNotBlank() }
                            ).fold(
                                onSuccess = { count ->
                                    isForcingOverwrite = false
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.keepass_webdav_force_overwrite_success,
                                            count
                                        ),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    refreshRemoteFiles()
                                },
                                onFailure = { e ->
                                    isForcingOverwrite = false
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.keepass_webdav_force_overwrite_failed,
                                            e.message ?: context.getString(R.string.import_data_unknown_error)
                                        ),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                    },
                    enabled = !isForcingOverwrite
                ) {
                    if (isForcingOverwrite) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.keepass_webdav_force_overwrite_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { forceOverwriteFile = null },
                    enabled = !isForcingOverwrite
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// ==================== 子组件 ====================

/**
 * WebDAV 配置卡片
 */
@Composable
private fun WebDavConfigCard(
    serverUrl: String,
    username: String,
    password: String,
    passwordVisible: Boolean,
    isConfigured: Boolean,
    isConnecting: Boolean,
    connectionStatus: ConnectionStatus,
    errorMessage: String,
    onServerUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityChange: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onClearConfig: () -> Unit
) {
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
                    text = stringResource(R.string.webdav_config),
                    style = MaterialTheme.typography.titleMedium
                )
                
                // 连接状态指示器
                ConnectionStatusChip(status = connectionStatus)
            }
            
            if (!isConfigured) {
                // 服务器地址
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onServerUrlChange,
                    label = { Text(stringResource(R.string.webdav_server_url)) },
                    placeholder = { Text("https://example.com/webdav/keepass") },
                    leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true
                )
                
                // 用户名
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text(stringResource(R.string.username)) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    singleLine = true
                )
                
                // 密码
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.password)) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = if (passwordVisible) 
                        VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { onPasswordVisibilityChange(!passwordVisible) }) {
                            Icon(
                                if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true
                )
                
                // 测试连接按钮
                Button(
                    onClick = onTestConnection,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting && serverUrl.isNotBlank() && 
                             username.isNotBlank() && password.isNotBlank()
                ) {
                    if (isConnecting) {
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
                // 已配置状态：显示配置摘要
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = serverUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.keepass_webdav_user_summary, username),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                OutlinedButton(
                    onClick = onClearConfig,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.webdav_clear_config))
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

/**
 * 连接状态芯片
 */
@Composable
private fun ConnectionStatusChip(status: ConnectionStatus) {
    val (text, color) = when (status) {
        ConnectionStatus.NotConnected -> stringResource(R.string.keepass_webdav_status_not_connected) to MaterialTheme.colorScheme.outline
        ConnectionStatus.Connecting -> stringResource(R.string.keepass_webdav_status_connecting) to MaterialTheme.colorScheme.tertiary
        ConnectionStatus.Connected -> stringResource(R.string.keepass_webdav_status_connected) to MaterialTheme.colorScheme.primary
        ConnectionStatus.Failed -> stringResource(R.string.keepass_webdav_status_failed) to MaterialTheme.colorScheme.error
    }
    
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (status == ConnectionStatus.Connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = color
                )
            } else {
                Icon(
                    when (status) {
                        ConnectionStatus.Connected -> Icons.Default.CheckCircle
                        ConnectionStatus.Failed -> Icons.Default.Error
                        else -> Icons.Default.Circle
                    },
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = color
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

/**
 * 操作区域卡片
 */
@Composable
private fun ActionsCard(
    kdbxPassword: String,
    kdbxPasswordVisible: Boolean,
    rememberPassword: Boolean,
    isCreating: Boolean,
    isImporting: Boolean,
    conflictProtectionEnabled: Boolean,
    onKdbxPasswordChange: (String) -> Unit,
    onKdbxPasswordVisibilityChange: (Boolean) -> Unit,
    onRememberPasswordChange: (Boolean) -> Unit,
    onConflictProtectionChange: (Boolean) -> Unit,
    onCreateRemoteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.keepass_webdav_actions),
                style = MaterialTheme.typography.titleMedium
            )
            
            // KeePass 数据库密码输入
            OutlinedTextField(
                value = kdbxPassword,
                onValueChange = onKdbxPasswordChange,
                label = { Text(stringResource(R.string.keepass_webdav_database_password)) },
                placeholder = { Text(stringResource(R.string.keepass_webdav_database_password_hint)) },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                visualTransformation = if (kdbxPasswordVisible) 
                    VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { onKdbxPasswordVisibilityChange(!kdbxPasswordVisible) }) {
                        Icon(
                            if (kdbxPasswordVisible) Icons.Default.Visibility 
                            else Icons.Default.VisibilityOff,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )
            )
            
            // 记住密码勾选框
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rememberPassword,
                    onCheckedChange = onRememberPasswordChange
                )
                Text(
                    text = stringResource(R.string.keepass_webdav_remember_password),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { onRememberPasswordChange(!rememberPassword) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.keepass_webdav_conflict_protection),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            ConflictModeOption(
                title = stringResource(R.string.keepass_webdav_conflict_mode_auto_title),
                description = stringResource(R.string.keepass_webdav_conflict_mode_auto_desc),
                selected = !conflictProtectionEnabled,
                onClick = { onConflictProtectionChange(false) }
            )

            ConflictModeOption(
                title = stringResource(R.string.keepass_webdav_conflict_mode_strict_title),
                description = stringResource(R.string.keepass_webdav_conflict_mode_strict_desc),
                selected = conflictProtectionEnabled,
                onClick = { onConflictProtectionChange(true) }
            )

            Text(
                text = if (conflictProtectionEnabled) {
                    stringResource(R.string.keepass_webdav_conflict_protection_enabled_hint)
                } else {
                    stringResource(R.string.keepass_webdav_conflict_protection_disabled_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // 新建远端库按钮
            Button(
                onClick = onCreateRemoteClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCreating && !isImporting && kdbxPassword.isNotBlank()
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.keepass_webdav_creating))
                } else {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.keepass_webdav_create_action))
                }
            }
            
            Text(
                text = stringResource(R.string.keepass_webdav_attach_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 云端文件列表卡片
 */
@Composable
private fun RemoteFilesCard(
    files: List<KdbxFileInfo>,
    attachedRemotePaths: Set<String>,
    isLoading: Boolean,
    isImporting: Boolean,
    isForcingOverwrite: Boolean,
    onFileClick: (KdbxFileInfo) -> Unit,
    onForceOverwriteClick: (KdbxFileInfo) -> Unit,
    onRefresh: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    
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
                    text = stringResource(R.string.keepass_webdav_remote_files_title),
                    style = MaterialTheme.typography.titleMedium
                )
                
                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            }
            
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (files.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.keepass_webdav_no_files),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                files.forEach { file ->
                    KdbxFileItem(
                        file = file,
                        isAttached = attachedRemotePaths.contains(file.path),
                        dateFormat = dateFormat,
                        isImporting = isImporting,
                        isForcingOverwrite = isForcingOverwrite,
                        onClick = { onFileClick(file) },
                        onForceOverwriteClick = { onForceOverwriteClick(file) }
                    )
                }
            }
        }
    }
}

/**
 * Kdbx 文件列表项
 */
@Composable
private fun KdbxFileItem(
    file: KdbxFileInfo,
    isAttached: Boolean,
    dateFormat: SimpleDateFormat,
    isImporting: Boolean,
    isForcingOverwrite: Boolean,
    onClick: () -> Unit,
    onForceOverwriteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isImporting, onClick = onClick),
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
                Icons.Default.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isAttached) {
                        Text(
                            text = stringResource(R.string.keepass_webdav_attached),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatFileSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateFormat.format(file.modified),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            IconButton(
                onClick = onClick,
                enabled = !isImporting
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = stringResource(R.string.keepass_webdav_attach_action),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (isAttached) {
                IconButton(
                    onClick = onForceOverwriteClick,
                    enabled = !isImporting && !isForcingOverwrite
                ) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = stringResource(R.string.keepass_webdav_force_overwrite_action),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ConflictModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Column(
            modifier = Modifier
                .padding(top = 2.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 格式化文件大小
 */
private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size.toFloat() / (1024 * 1024))
        else -> String.format("%.2f GB", size.toFloat() / (1024 * 1024 * 1024))
    }
}

