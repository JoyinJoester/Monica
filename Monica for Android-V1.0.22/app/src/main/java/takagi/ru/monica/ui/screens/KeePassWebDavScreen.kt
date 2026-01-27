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
    val isExporting: Boolean = false,
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
 * 用于配置 WebDAV 并处理 .kdbx 格式文件的导入导出
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
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<KdbxFileInfo?>(null) }
    
    // 对话框状态
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    
    // 启动时加载已保存的配置
    LaunchedEffect(Unit) {
        // 加载 KeePass 数据库密码（无论是否配置 WebDAV）
        val savedKdbxPassword = viewModel.getSavedKdbxPassword(context)
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
            isConfigured = true
            connectionStatus = ConnectionStatus.Connected
            
            // 自动加载文件列表
            isLoadingFiles = true
            viewModel.listKdbxFiles(context).fold(
                onSuccess = { files ->
                    kdbxFiles = files
                    isLoadingFiles = false
                },
                onFailure = { e ->
                    errorMessage = e.message ?: "加载文件列表失败"
                    isLoadingFiles = false
                }
            )
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KeePass WebDAV") },
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
                                isLoadingFiles = true
                                coroutineScope.launch {
                                    viewModel.listKdbxFiles(context).fold(
                                        onSuccess = { files ->
                                            kdbxFiles = files
                                            isLoadingFiles = false
                                        },
                                        onFailure = { e ->
                                            errorMessage = e.message ?: "刷新失败"
                                            isLoadingFiles = false
                                        }
                                    )
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
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
                        errorMessage = "请填写所有字段"
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
                                
                                Toast.makeText(context, "连接成功", Toast.LENGTH_SHORT).show()
                                
                                // 加载文件列表
                                isLoadingFiles = true
                                viewModel.listKdbxFiles(context).fold(
                                    onSuccess = { files ->
                                        kdbxFiles = files
                                        isLoadingFiles = false
                                    },
                                    onFailure = { e ->
                                        errorMessage = e.message ?: "加载文件列表失败"
                                        isLoadingFiles = false
                                    }
                                )
                            },
                            onFailure = { e ->
                                isConnecting = false
                                connectionStatus = ConnectionStatus.Failed
                                errorMessage = e.message ?: "连接失败"
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
                    errorMessage = ""
                }
            )
            
            // ========== 卡片 B：操作区域（仅在配置成功后显示）==========
            if (isConfigured) {
                ActionsCard(
                    kdbxPassword = kdbxPassword,
                    kdbxPasswordVisible = kdbxPasswordVisible,
                    rememberPassword = rememberKdbxPassword,
                    isExporting = isExporting,
                    isImporting = isImporting,
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
                    onExportClick = {
                        if (kdbxPassword.isBlank()) {
                            Toast.makeText(context, "请输入数据库密码", Toast.LENGTH_SHORT).show()
                            return@ActionsCard
                        }
                        // 如果勾选了记住密码，导出前保存
                        if (rememberKdbxPassword) {
                            viewModel.saveKdbxPassword(context, kdbxPassword)
                        }
                        showExportDialog = true
                    }
                )
            }
            
            // ========== 卡片 C：云端文件列表（仅在配置成功后显示）==========
            if (isConfigured) {
                RemoteFilesCard(
                    files = kdbxFiles,
                    isLoading = isLoadingFiles,
                    isImporting = isImporting,
                    onFileClick = { file ->
                        selectedFile = file
                        showImportDialog = true
                    },
                    onRefresh = {
                        isLoadingFiles = true
                        coroutineScope.launch {
                            viewModel.listKdbxFiles(context).fold(
                                onSuccess = { files ->
                                    kdbxFiles = files
                                    isLoadingFiles = false
                                },
                                onFailure = { e ->
                                    errorMessage = e.message ?: "刷新失败"
                                    isLoadingFiles = false
                                }
                            )
                        }
                    }
                )
            }
        }
    }
    
    // ========== 导出确认对话框 ==========
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("导出到 WebDAV") },
            text = { 
                Text("将本地数据转换为 KeePass (.kdbx) 格式并上传到 WebDAV 服务器。\n\n" +
                     "文件将使用您设置的密码加密。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportDialog = false
                        isExporting = true
                        
                        coroutineScope.launch {
                            viewModel.exportToKdbx(context, kdbxPassword).fold(
                                onSuccess = { fileName ->
                                    isExporting = false
                                    Toast.makeText(context, "导出成功: $fileName", Toast.LENGTH_LONG).show()
                                    
                                    // 刷新文件列表
                                    isLoadingFiles = true
                                    viewModel.listKdbxFiles(context).fold(
                                        onSuccess = { files ->
                                            kdbxFiles = files
                                            isLoadingFiles = false
                                        },
                                        onFailure = { /* ignore */ 
                                            isLoadingFiles = false
                                        }
                                    )
                                },
                                onFailure = { e ->
                                    isExporting = false
                                    Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    enabled = !isExporting
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("确认导出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // ========== 导入确认对话框 ==========
    if (showImportDialog && selectedFile != null) {
        var importPassword by remember { mutableStateOf(kdbxPassword) }
        var importPasswordVisible by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { 
                showImportDialog = false
                selectedFile = null
            },
            title = { Text("从 WebDAV 导入") },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("将下载并导入以下文件：")
                    Text(
                        text = selectedFile!!.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        label = { Text("KeePass 数据库密码") },
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
                            Toast.makeText(context, "请输入密码", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        val fileToImport = selectedFile
                        if (fileToImport == null) {
                            Toast.makeText(context, "请先选择文件", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        showImportDialog = false
                        isImporting = true
                        selectedFile = null
                        
                        coroutineScope.launch {
                            try {
                                viewModel.importFromKdbx(context, fileToImport, importPassword).fold(
                                    onSuccess = { count ->
                                        isImporting = false
                                        Toast.makeText(context, "导入成功: $count 个条目", Toast.LENGTH_LONG).show()
                                    },
                                    onFailure = { e ->
                                        isImporting = false
                                        Toast.makeText(context, "导入失败: ${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("KeePassWebDAV", "Import exception", e)
                                isImporting = false
                                Toast.makeText(context, "导入异常: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
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
                    Text("确认导入")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showImportDialog = false
                        selectedFile = null
                    }
                ) {
                    Text("取消")
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
                    text = "WebDAV 配置",
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
                    label = { Text("服务器地址") },
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
                    label = { Text("用户名") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    singleLine = true
                )
                
                // 密码
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("密码") },
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
                    Text("测试连接")
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
                            text = "用户: $username",
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
                    Text("清除配置")
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
        ConnectionStatus.NotConnected -> "未连接" to MaterialTheme.colorScheme.outline
        ConnectionStatus.Connecting -> "连接中..." to MaterialTheme.colorScheme.tertiary
        ConnectionStatus.Connected -> "已连接" to MaterialTheme.colorScheme.primary
        ConnectionStatus.Failed -> "连接失败" to MaterialTheme.colorScheme.error
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
    isExporting: Boolean,
    isImporting: Boolean,
    onKdbxPasswordChange: (String) -> Unit,
    onKdbxPasswordVisibilityChange: (Boolean) -> Unit,
    onRememberPasswordChange: (Boolean) -> Unit,
    onExportClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "操作",
                style = MaterialTheme.typography.titleMedium
            )
            
            // KeePass 数据库密码输入
            OutlinedTextField(
                value = kdbxPassword,
                onValueChange = onKdbxPasswordChange,
                label = { Text("数据库密码") },
                placeholder = { Text("输入 .kdbx 密码") },
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
                    text = "记住密码",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { onRememberPasswordChange(!rememberPassword) }
                )
            }
            
            // 导出按钮
            Button(
                onClick = onExportClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting && !isImporting && kdbxPassword.isNotBlank()
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导出中...")
                } else {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导出到 WebDAV")
                }
            }
            
            Text(
                text = "导出为 .kdbx 格式并上传",
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
    isLoading: Boolean,
    isImporting: Boolean,
    onFileClick: (KdbxFileInfo) -> Unit,
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
                    text = "云端 .kdbx 文件",
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
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
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
                            text = "暂无 .kdbx 文件",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                files.forEach { file ->
                    KdbxFileItem(
                        file = file,
                        dateFormat = dateFormat,
                        isImporting = isImporting,
                        onClick = { onFileClick(file) }
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
    dateFormat: SimpleDateFormat,
    isImporting: Boolean,
    onClick: () -> Unit
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
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
                    contentDescription = "导入",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
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
