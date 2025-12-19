package takagi.ru.monica.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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

/**
 * 导出选项枚举
 */
enum class ExportOption {
    ALL,           // 全部（CSV格式）
    PASSWORDS,     // 密码（CSV格式）
    TOTP,          // TOTP（CSV或Aegis格式）
    BANK_CARDS_DOCS, // 银行卡和证件合并（CSV格式）
    NOTES,          // 笔记（CSV格式）
    ZIP_BACKUP      // 完整备份 (WebDAV兼容ZIP)
}

/**
 * TOTP导出格式枚举
 */
enum class TotpExportFormat {
    CSV,           // CSV格式
    AEGIS          // Aegis兼容格式
}

/**
 * 数据导出界面 - 重新设计
 * 采用M3 Expressive设计规范
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDataScreen(
    onNavigateBack: () -> Unit,
    onExportAll: suspend (Uri) -> Result<String>,
    onExportPasswords: suspend (Uri) -> Result<String>,
    onExportTotp: suspend (Uri, TotpExportFormat, String?) -> Result<String>,
    onExportBankCardsAndDocs: suspend (Uri) -> Result<String>,
    onExportNotes: suspend (Uri) -> Result<String>,
    onExportZip: suspend (Uri) -> Result<String>
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    
    var isExporting by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf(ExportOption.ALL) }
    var totpFormat by remember { mutableStateOf(TotpExportFormat.CSV) }
    var enableEncryption by remember { mutableStateOf(false) }
    var encryptionPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    
    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let { safeUri ->
            scope.launch {
                isExporting = true
                try {
                    val result = when (selectedOption) {
                        ExportOption.ALL -> onExportAll(safeUri)
                        ExportOption.PASSWORDS -> onExportPasswords(safeUri)
                        ExportOption.TOTP -> {
                            val password = if (enableEncryption && totpFormat == TotpExportFormat.AEGIS) {
                                encryptionPassword
                            } else null
                            onExportTotp(safeUri, totpFormat, password)
                        }
                        ExportOption.BANK_CARDS_DOCS -> onExportBankCardsAndDocs(safeUri)
                        ExportOption.NOTES -> onExportNotes(safeUri)
                        ExportOption.ZIP_BACKUP -> onExportZip(safeUri)
                    }
                    
                    isExporting = false
                    result.onSuccess { message ->
                        snackbarHostState.showSnackbar(message)
                        onNavigateBack()
                    }.onFailure { error ->
                        snackbarHostState.showSnackbar(
                            error.message ?: context.getString(R.string.export_data_error)
                        )
                    }
                } catch (e: Exception) {
                    isExporting = false
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.export_data_error) + ": ${e.message}"
                    )
                }
            }
        }
    }
    
    // 启动导出
    fun startExport() {
        // 如果选择了TOTP的Aegis加密格式且需要加密
        if (selectedOption == ExportOption.TOTP && 
            totpFormat == TotpExportFormat.AEGIS && 
            enableEncryption) {
            if (encryptionPassword.isEmpty()) {
                showPasswordDialog = true
                return
            }
        }
        
        // 根据选项设置文件名
        val fileName = when (selectedOption) {
            ExportOption.ALL -> "monica_all_${System.currentTimeMillis()}.csv"
            ExportOption.PASSWORDS -> "monica_passwords_${System.currentTimeMillis()}.csv"
            ExportOption.TOTP -> {
                if (totpFormat == TotpExportFormat.AEGIS) {
                    "monica_totp_${System.currentTimeMillis()}.json"
                } else {
                    "monica_totp_${System.currentTimeMillis()}.csv"
                }
            }
            ExportOption.BANK_CARDS_DOCS -> "monica_cards_docs_${System.currentTimeMillis()}.csv"
            ExportOption.NOTES -> "monica_notes_${System.currentTimeMillis()}.csv"
            ExportOption.ZIP_BACKUP -> "monica_backup_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())}.zip"
        }
        
        filePickerLauncher.launch(fileName)
    }
    
    // 密码输入对话框
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text(stringResource(R.string.export_encryption_password)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.export_encryption_password_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = encryptionPassword,
                        onValueChange = { encryptionPassword = it },
                        label = { Text(stringResource(R.string.password)) },
                        visualTransformation = if (passwordVisible) 
                            VisualTransformation.None 
                        else 
                            PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
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
                        showPasswordDialog = false
                        startExport()
                    },
                    enabled = encryptionPassword.isNotEmpty()
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
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
            
            // 1. 完整备份 (ZIP) - Replaced with Archive icon as Backup might not be standard
            ExportOptionCard(
                icon = Icons.Default.Archive,
                title = "完整备份 (ZIP)", // TODO: Add to strings.xml
                description = "包含所有数据（密码、笔记、图片等），兼容 Windows 和 Android 版本。推荐使用。",
                selected = selectedOption == ExportOption.ZIP_BACKUP,
                onClick = { selectedOption = ExportOption.ZIP_BACKUP }
            )

            ExportOptionCard(
                icon = Icons.Default.SelectAll,
                title = stringResource(R.string.export_option_all),
                description = stringResource(R.string.export_option_all_desc),
                selected = selectedOption == ExportOption.ALL,
                onClick = { selectedOption = ExportOption.ALL }
            )
            
            ExportOptionCard(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.export_option_passwords),
                description = stringResource(R.string.export_option_passwords_desc),
                selected = selectedOption == ExportOption.PASSWORDS,
                onClick = { selectedOption = ExportOption.PASSWORDS }
            )
            
            // TOTP选项卡（带格式选择）
            ExportOptionCard(
                icon = Icons.Default.Security,
                title = stringResource(R.string.export_option_totp),
                description = stringResource(R.string.export_option_totp_desc),
                selected = selectedOption == ExportOption.TOTP,
                onClick = { selectedOption = ExportOption.TOTP },
                expandable = true,
                expanded = selectedOption == ExportOption.TOTP,
                expandedContent = {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 格式选择
                        Text(
                            stringResource(R.string.export_totp_format),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = totpFormat == TotpExportFormat.CSV,
                                onClick = { 
                                    totpFormat = TotpExportFormat.CSV
                                    enableEncryption = false
                                },
                                label = { Text("CSV") },
                                leadingIcon = if (totpFormat == TotpExportFormat.CSV) {
                                    { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = totpFormat == TotpExportFormat.AEGIS,
                                onClick = { totpFormat = TotpExportFormat.AEGIS },
                                label = { Text("Aegis") },
                                leadingIcon = if (totpFormat == TotpExportFormat.AEGIS) {
                                    { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        // Aegis加密选项
                        AnimatedVisibility(
                            visible = totpFormat == TotpExportFormat.AEGIS,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            stringResource(R.string.export_enable_encryption),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            stringResource(R.string.export_encryption_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                    Switch(
                                        checked = enableEncryption,
                                        onCheckedChange = { 
                                            enableEncryption = it
                                            if (!it) encryptionPassword = ""
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
            
            ExportOptionCard(
                icon = Icons.Default.CreditCard,
                title = stringResource(R.string.export_option_cards_docs),
                description = stringResource(R.string.export_option_cards_docs_desc),
                selected = selectedOption == ExportOption.BANK_CARDS_DOCS,
                onClick = { selectedOption = ExportOption.BANK_CARDS_DOCS }
            )

            ExportOptionCard(
                icon = Icons.Default.Note,
                title = stringResource(R.string.export_option_notes),
                description = stringResource(R.string.export_option_notes_desc),
                selected = selectedOption == ExportOption.NOTES,
                onClick = { selectedOption = ExportOption.NOTES }
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
            if (selectedOption != ExportOption.TOTP || 
                totpFormat != TotpExportFormat.AEGIS || 
                !enableEncryption) {
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
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 导出选项卡片组件 - M3 Expressive风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expandable: Boolean = false,
    expanded: Boolean = false,
    expandedContent: @Composable (() -> Unit)? = null
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (selected) 4.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (selected) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (selected) 
                                MaterialTheme.colorScheme.onPrimary 
                            else 
                                MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) 
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (selected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            // 展开内容
            if (expandable && expanded && expandedContent != null) {
                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                )
                expandedContent()
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun rememberLauncherForActivityResult(
    contract: androidx.activity.result.contract.ActivityResultContracts.CreateDocument,
    onResult: (Uri?) -> Unit
): androidx.activity.compose.ManagedActivityResultLauncher<String, Uri?> {
    return androidx.activity.compose.rememberLauncherForActivityResult(
        contract = contract,
        onResult = onResult
    )
}