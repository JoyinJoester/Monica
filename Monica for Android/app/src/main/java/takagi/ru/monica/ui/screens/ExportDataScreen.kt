package takagi.ru.monica.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.BackupPreferences
import takagi.ru.monica.utils.WebDavHelper
import takagi.ru.monica.ui.components.OutlinedTextField

/**
 * 数据导出界面 - 重新设计
 * 采用M3 Expressive设计规范
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDataScreen(
    onNavigateBack: () -> Unit,
    onExportZip: suspend (Uri, BackupPreferences) -> Result<String>,
    onExportKdbx: suspend (Uri, String) -> Result<String> = { _, _ -> Result.failure(Exception("Not implemented")) }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    
    var isExporting by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf(ExportOption.ZIP_BACKUP) }
    
    // KDBX 导出密码
    var kdbxPassword by remember { mutableStateOf("") }
    var kdbxPasswordVisible by remember { mutableStateOf(false) }
    var showKdbxPasswordDialog by remember { mutableStateOf(false) }
    
    // ZIP 备份选项
    var backupPreferences by remember { mutableStateOf(BackupPreferences()) }
    var zipBackupExpanded by remember { mutableStateOf(false) }
    
    // 检测 WebDAV 是否已配置
    val webDavHelper = remember { WebDavHelper(context) }
    val isWebDavConfigured = remember { webDavHelper.isConfigured() }
    
    // 获取本地 KeePass 数据库数量
    var localKeePassCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        try {
            val database = takagi.ru.monica.data.PasswordDatabase.getDatabase(context)
            val keepassDao = database.localKeePassDatabaseDao()
            localKeePassCount = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                keepassDao.getAllDatabasesSync().size
            }
        } catch (e: Exception) {
            localKeePassCount = 0
        }
    }
    
    suspend fun handleExportUri(safeUri: Uri) {
        isExporting = true
        try {
            val result = when (selectedOption) {
                ExportOption.ZIP_BACKUP -> onExportZip(safeUri, backupPreferences)
                ExportOption.KDBX -> onExportKdbx(safeUri, kdbxPassword)
            }

            isExporting = false
            result.onSuccess { message ->
                scope.launch {
                    snackbarHostState.showSnackbar(message)
                    onNavigateBack()
                }
            }.onFailure { error ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        error.message ?: context.getString(R.string.export_data_error)
                    )
                }
            }
        } catch (e: Exception) {
            isExporting = false
            snackbarHostState.showSnackbar(
                context.getString(R.string.export_data_error) + ": ${e.message}"
            )
        }
    }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            return@rememberLauncherForActivityResult
        }
        result.data?.data?.let { safeUri ->
            scope.launch {
                handleExportUri(safeUri)
            }
        }
    }
    
    // 启动导出
    fun startExport() {
        // KDBX 导出需要密码
        if (selectedOption == ExportOption.KDBX) {
            if (kdbxPassword.isEmpty()) {
                showKdbxPasswordDialog = true
                return
            }
        }
        
        // ZIP 备份验证：至少选择一种内容
        if (selectedOption == ExportOption.ZIP_BACKUP && !backupPreferences.hasAnyEnabled()) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.backup_validation_error))
            }
            return
        }
        
        val (fileName, mimeType) = exportDocumentSpec(selectedOption)

        val createDocumentIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, fileName)
        }

        try {
            filePickerLauncher.launch(createDocumentIntent)
        } catch (e: Exception) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.error_launch_export, e.message ?: "unknown")
                )
            }
        }
    }
    
    // KDBX 密码输入对话框
    if (showKdbxPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showKdbxPasswordDialog = false },
            title = { Text(stringResource(R.string.kdbx_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.kdbx_password_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = kdbxPassword,
                        onValueChange = { kdbxPassword = it },
                        label = { Text(stringResource(R.string.password)) },
                        visualTransformation = if (kdbxPasswordVisible) 
                            VisualTransformation.None 
                        else 
                            PasswordVisualTransformation(),
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
                        startExport()
                    },
                    enabled = kdbxPassword.isNotEmpty()
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showKdbxPasswordDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_data_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部说明卡片 - M3 Expressive风格
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.export_data_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            stringResource(R.string.export_data_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            // 导出选项选择
            Text(
                stringResource(R.string.export_select_option),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            // 选项卡片组
            
            // 1. 完整备份 (ZIP) - 可展开选择备份内容
            ExportOptionCard(
                icon = Icons.Default.Archive,
                title = stringResource(R.string.export_option_zip),
                description = stringResource(R.string.export_option_zip_desc),
                selected = selectedOption == ExportOption.ZIP_BACKUP,
                onClick = { 
                    if (selectedOption == ExportOption.ZIP_BACKUP) {
                        // 已选中时，点击切换展开状态
                        zipBackupExpanded = !zipBackupExpanded
                    } else {
                        // 未选中时，选中但不展开
                        selectedOption = ExportOption.ZIP_BACKUP
                    }
                },
                expandable = true,
                expanded = selectedOption == ExportOption.ZIP_BACKUP && zipBackupExpanded,
                expandedContent = {
                    ZipBackupOptionsContent(
                        backupPreferences = backupPreferences,
                        onBackupPreferencesChange = { backupPreferences = it },
                        localKeePassCount = localKeePassCount,
                        isWebDavConfigured = isWebDavConfigured
                    )
                }
            )
            
            // KDBX 导出选项（KeePass 格式）
            ExportOptionCard(
                icon = Icons.Default.Key,
                title = stringResource(R.string.export_option_kdbx),
                description = stringResource(R.string.export_option_kdbx_desc),
                selected = selectedOption == ExportOption.KDBX,
                onClick = { selectedOption = ExportOption.KDBX }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 导出按钮
            Button(
                onClick = { startExport() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isExporting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.exporting))
                } else {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.start_export),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            
            // 安全警告
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        stringResource(R.string.export_data_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

