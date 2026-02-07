package takagi.ru.monica.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.util.DataExportImportManager
import takagi.ru.monica.util.FileOperationHelper

/**
 * 导入类型数据类
 */
private data class ImportTypeInfo(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val fileHint: String
)

/**
 * 导入类型选项卡片
 */
@Composable
private fun ImportTypeCard(
    info: ImportTypeInfo,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "containerColor"
    )
    val borderColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        label = "borderColor"
    )
    val elevation by animateDpAsState(
        if (selected) 4.dp else 0.dp,
        label = "elevation"
    )
    
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    info.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                )
                if (selected) {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                info.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 数据导入界面 - M3 Expressive 设计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDataScreen(
    onNavigateBack: () -> Unit,
    onImport: suspend (Uri) -> Result<Int>,  // 普通数据导入
    onImportAegis: suspend (Uri) -> Result<Int>,  // Aegis JSON导入
    onImportEncryptedAegis: suspend (Uri, String) -> Result<Int>,  // 加密的Aegis JSON导入
    onImportSteamMaFile: suspend (Uri) -> Result<Int>,  // Steam maFile导入
    onImportZip: suspend (Uri, String?) -> Result<Int>,  // Monica ZIP导入
    onImportKdbx: suspend (Uri, String) -> Result<Int> = { _, _ -> Result.failure(Exception("未实现")) },  // KDBX导入
    onImportKeePassCsv: suspend (Uri) -> Result<Int> = { _ -> Result.failure(Exception("未实现")) },  // KeePass CSV导入
    onImportBitwardenCsv: suspend (Uri) -> Result<Int> = { _ -> Result.failure(Exception("未实现")) }  // Bitwarden CSV导入
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importType by remember { mutableStateOf("monica_zip") } // 默认选择 ZIP 备份
    var csvImportType by remember { mutableStateOf("normal") } // CSV子类型
    var showPasswordDialog by remember { mutableStateOf(false) }
    var aegisPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    
    // KDBX 导入密码
    var showKdbxPasswordDialog by remember { mutableStateOf(false) }
    var kdbxPassword by remember { mutableStateOf("") }
    var kdbxPasswordVisible by remember { mutableStateOf(false) }
    
    // 导入类型信息列表
    val importTypes = remember {
        listOf(
            ImportTypeInfo(
                key = "monica_zip",
                icon = Icons.Default.Archive,
                title = "Monica 备份",
                description = "恢复完整备份 ZIP 文件，包含所有数据",
                fileHint = "选择 .zip 文件"
            ),
            ImportTypeInfo(
                key = "kdbx",
                icon = Icons.Default.Key,
                title = "KeePass 格式",
                description = "导入 KeePass 兼容的 .kdbx 数据库文件",
                fileHint = "选择 .kdbx 文件"
            ),
            ImportTypeInfo(
                key = "csv_group",
                icon = Icons.Default.TableChart,
                title = "CSV 数据",
                description = "导入 Monica / KeePass / Bitwarden 的 CSV 文件",
                fileHint = "选择 .csv 文件"
            ),
            ImportTypeInfo(
                key = "aegis",
                icon = Icons.Default.Security,
                title = "Aegis 验证器",
                description = "导入 Aegis Authenticator 的 JSON 备份",
                fileHint = "选择 .json 文件"
            ),
            ImportTypeInfo(
                key = "steam",
                icon = Icons.Default.SportsEsports,
                title = "Steam Guard",
                description = "导入 Steam 令牌的 maFile 文件",
                fileHint = "选择 .maFile 文件"
            )
        )
    }

    val csvImportTypes = remember {
        listOf(
            ImportTypeInfo(
                key = "normal",
                icon = Icons.Default.TableChart,
                title = "Monica CSV",
                description = "导入 Monica 应用导出的 CSV 文件",
                fileHint = "选择 Monica .csv 文件"
            ),
            ImportTypeInfo(
                key = "keepass_csv",
                icon = Icons.Default.Description,
                title = "KeePass CSV",
                description = "导入 KeePass 导出的 CSV 文件",
                fileHint = "选择 KeePass .csv 文件"
            ),
            ImportTypeInfo(
                key = "bitwarden_csv",
                icon = Icons.Default.Lock,
                title = "Bitwarden CSV",
                description = "导入 Bitwarden 导出的 CSV 文件",
                fileHint = "选择 Bitwarden .csv 文件"
            )
        )
    }

    val effectiveImportType = if (importType == "csv_group") csvImportType else importType
    
    val currentTypeInfo = remember(importType, csvImportType) {
        if (importType == "csv_group") {
            csvImportTypes.find { it.key == csvImportType } ?: csvImportTypes[0]
        } else {
            importTypes.find { it.key == importType } ?: importTypes[0]
        }
    }
    
    // 设置文件操作回调
    LaunchedEffect(Unit) {
        FileOperationHelper.setCallback(object : FileOperationHelper.FileOperationCallback {
            override fun onExportFileSelected(uri: Uri?) {
                // 导入界面不需要处理导出文件选择
            }
            
            override fun onImportFileSelected(uri: Uri?) {
                uri?.let { safeUri ->
                    scope.launch {
                        try {
                            // 获取持久化权限
                            try {
                                context.contentResolver.takePersistableUriPermission(
                                    safeUri,
                                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (e: SecurityException) {
                                // 某些URI可能不支持持久化权限,忽略这个错误
                                android.util.Log.w("ImportDataScreen", "无法获取持久化权限", e)
                            }
                            
                            selectedFileUri = safeUri
                            // 尝试获取文件名
                            selectedFileName = try {
                                safeUri.lastPathSegment?.substringAfterLast('/') ?: "已选择文件"
                            } catch (e: Exception) {
                                "已选择文件"
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ImportDataScreen", "文件选择异常", e)
                            snackbarHostState.showSnackbar("文件选择失败：${e.message ?: "未知错误"}")
                        }
                    }
                }
            }
        })
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_data_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 导入按钮
                    Button(
                        onClick = {
                            selectedFileUri?.let { uri ->
                                scope.launch {
                                    isImporting = true
                                    try {
                                        when (effectiveImportType) {
                                            "monica_zip" -> {
                                                val result = onImportZip(uri, null)
                                                result.onSuccess { count ->
                                                    handleImportResult(Result.success(count), context, snackbarHostState, effectiveImportType, onNavigateBack)
                                                }.onFailure { error ->
                                                    if (error is takagi.ru.monica.utils.WebDavHelper.PasswordRequiredException) {
                                                        isImporting = false
                                                        showPasswordDialog = true
                                                        passwordError = null
                                                        aegisPassword = ""
                                                    } else {
                                                        handleImportResult(Result.failure(error), context, snackbarHostState, effectiveImportType, onNavigateBack)
                                                    }
                                                }
                                            }
                                            "aegis" -> {
                                                // Aegis导入类型，先检查是否为加密文件
                                                val isEncryptedResult = DataExportImportManager(context).isEncryptedAegisFile(uri)
                                                val isEncrypted = isEncryptedResult.getOrDefault(false)
                                                if (isEncrypted) {
                                                    // 是加密文件，显示密码输入对话框
                                                    isImporting = false
                                                    showPasswordDialog = true
                                                    passwordError = null
                                                    aegisPassword = ""
                                                    return@launch
                                                } else {
                                                    // 不是加密文件，直接导入
                                                    val result = onImportAegis(uri)
                                                    handleImportResult(result, context, snackbarHostState, effectiveImportType, onNavigateBack)
                                                }
                                            }
                                            "steam" -> {
                                                // Steam maFile导入
                                                val result = onImportSteamMaFile(uri)
                                                handleImportResult(result, context, snackbarHostState, effectiveImportType, onNavigateBack)
                                            }
                                            "kdbx" -> {
                                                // KDBX 导入需要密码
                                                isImporting = false
                                                showKdbxPasswordDialog = true
                                                kdbxPassword = ""
                                            }
                                            "keepass_csv" -> {
                                                val result = onImportKeePassCsv(uri)
                                                handleImportResult(result, context, snackbarHostState, effectiveImportType, onNavigateBack)
                                            }
                                            "bitwarden_csv" -> {
                                                val result = onImportBitwardenCsv(uri)
                                                handleImportResult(result, context, snackbarHostState, effectiveImportType, onNavigateBack)
                                            }
                                            else -> {
                                                // 普通CSV导入
                                                val result = onImport(uri)
                                                handleImportResult(result, context, snackbarHostState, effectiveImportType, onNavigateBack)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("ImportDataScreen", "导入异常", e)
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.import_data_error_exception, e.message ?: "未知错误")
                                        )
                                    } finally {
                                        isImporting = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = selectedFileUri != null && !isImporting,
                        shape = MaterialTheme.shapes.large
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.importing), style = MaterialTheme.typography.titleMedium)
                        } else {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.start_import), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    
                    // 说明文字
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.import_data_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 选择导入类型标题
            Text(
                stringResource(R.string.import_data_select_type),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            // 导入类型卡片列表 - 垂直排列，适配各种屏幕尺寸
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                importTypes.forEach { typeInfo ->
                    ImportTypeCard(
                        info = typeInfo,
                        selected = importType == typeInfo.key,
                        onClick = { 
                            importType = typeInfo.key
                            // 切换类型时清除已选文件
                            selectedFileUri = null
                            selectedFileName = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (importType == "csv_group") {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "选择 CSV 来源",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        csvImportTypes.forEach { csvTypeInfo ->
                            ImportTypeCard(
                                info = csvTypeInfo,
                                selected = csvImportType == csvTypeInfo.key,
                                onClick = {
                                    csvImportType = csvTypeInfo.key
                                    selectedFileUri = null
                                    selectedFileName = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 文件选择区域
            Text(
                "选择文件",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // 选择文件卡片
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { 
                    activity?.let { act ->
                        // 根据导入类型选择不同的文件过滤器
                        when (effectiveImportType) {
                            "monica_zip" -> FileOperationHelper.importFromZip(act)
                            "kdbx" -> FileOperationHelper.importFromKdbx(act)
                            "keepass_csv" -> FileOperationHelper.importFromCsv(act)
                            "bitwarden_csv" -> FileOperationHelper.importFromCsv(act)
                            "aegis" -> FileOperationHelper.importFromJson(act)
                            "steam" -> FileOperationHelper.importFromMaFile(act)
                            else -> FileOperationHelper.importFromCsv(act)
                        }
                    } ?: run {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.error_launch_export, "无法启动操作"))
                        }
                    }
                },
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (selectedFileUri != null) 
                        MaterialTheme.colorScheme.secondaryContainer
                    else 
                        MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 文件图标
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (selectedFileUri != null)
                            MaterialTheme.colorScheme.secondary
                        else
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (selectedFileUri != null) Icons.Default.InsertDriveFile else Icons.Default.FileOpen,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (selectedFileUri != null)
                                    MaterialTheme.colorScheme.onSecondary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (selectedFileUri != null) "已选择文件" else "点击选择文件",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (selectedFileUri != null) 
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else 
                                MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            selectedFileName ?: currentTypeInfo.fileHint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedFileUri != null)
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = if (selectedFileUri != null)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 底部留白，避免被底部栏遮挡
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
    
    // 密码输入对话框
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { 
                showPasswordDialog = false
                passwordError = null
            },
            title = { Text("输入解密密码") },
            text = {
                Column {
                    Text(
                        "此Aegis备份文件已加密，请输入密码以解密",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = aegisPassword,
                        onValueChange = { 
                            aegisPassword = it
                            passwordError = null
                        },
                        label = { Text("密码") },
                        singleLine = true,
                        isError = passwordError != null,
                        supportingText = passwordError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (aegisPassword.isBlank()) {
                            passwordError = "密码不能为空"
                            return@TextButton
                        }
                        
                        scope.launch {
                            isImporting = true
                            showPasswordDialog = false
                            try {
                                selectedFileUri?.let { uri ->
                                    // 使用加密导入回调
                                    val result = if (importType == "monica_zip") {
                                        onImportZip(uri, aegisPassword)
                                    } else {
                                        onImportEncryptedAegis(uri, aegisPassword)
                                    }
                                    
                                    result.onSuccess { count ->
                                        val message = if (importType == "monica_zip") {
                                            "成功恢复备份，包含 $count 个条目"
                                        } else {
                                            "成功导入 $count 个TOTP验证器"
                                        }
                                        snackbarHostState.showSnackbar(message)
                                        onNavigateBack()
                                    }.onFailure { error ->
                                        val errorMsg = error.message ?: "未知错误"
                                        if (errorMsg.contains("密码错误") || errorMsg.contains("解密失败")) {
                                            passwordError = "密码错误，请重试"
                                            showPasswordDialog = true
                                        } else {
                                            snackbarHostState.showSnackbar("导入失败：$errorMsg")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ImportDataScreen", "加密导入异常", e)
                                snackbarHostState.showSnackbar("导入失败：${e.message ?: "未知错误"}")
                            } finally {
                                isImporting = false
                            }
                        }
                    },
                    enabled = !isImporting
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("确定")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showPasswordDialog = false
                        passwordError = null
                    },
                    enabled = !isImporting
                ) {
                    Text("取消")
                }
            }
        )
    }
    
    // KDBX 密码输入对话框
    if (showKdbxPasswordDialog) {
        AlertDialog(
            onDismissRequest = { 
                showKdbxPasswordDialog = false
                kdbxPassword = ""
            },
            title = { Text(stringResource(R.string.kdbx_import_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.kdbx_import_password_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = kdbxPassword,
                        onValueChange = { kdbxPassword = it },
                        label = { Text(stringResource(R.string.password)) },
                        visualTransformation = if (kdbxPasswordVisible) 
                            androidx.compose.ui.text.input.VisualTransformation.None 
                        else 
                            androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { kdbxPasswordVisible = !kdbxPasswordVisible }) {
                                Icon(
                                    if (kdbxPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showKdbxPasswordDialog = false
                        selectedFileUri?.let { uri ->
                            scope.launch {
                                isImporting = true
                                try {
                                    val result = onImportKdbx(uri, kdbxPassword)
                                    result.onSuccess { count ->
                                        snackbarHostState.showSnackbar("成功导入 $count 条记录")
                                        onNavigateBack()
                                    }.onFailure { error ->
                                        snackbarHostState.showSnackbar(
                                            error.message ?: context.getString(R.string.import_data_error)
                                        )
                                    }
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.import_data_error_exception, e.message ?: "未知错误")
                                    )
                                } finally {
                                    isImporting = false
                                    kdbxPassword = ""
                                }
                            }
                        }
                    },
                    enabled = kdbxPassword.isNotEmpty() && !isImporting
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.import_data_btn))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showKdbxPasswordDialog = false
                        kdbxPassword = ""
                    },
                    enabled = !isImporting
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// 处理导入结果的辅助函数
private suspend fun handleImportResult(
    result: Result<Int>,
    context: android.content.Context,
    snackbarHostState: SnackbarHostState,
    importType: String,
    onNavigateBack: () -> Unit
) {
    result.onSuccess { count ->
        val message = when (importType) {
            "aegis" -> "成功导入 $count 个TOTP验证器"
            "steam" -> "成功导入 Steam Guard 验证器"
            else -> context.getString(R.string.import_data_success_normal, count)
        }
        snackbarHostState.showSnackbar(message)
        onNavigateBack()
    }.onFailure { error ->
        snackbarHostState.showSnackbar(
            error.message ?: context.getString(R.string.import_data_error)
        )
    }
}
