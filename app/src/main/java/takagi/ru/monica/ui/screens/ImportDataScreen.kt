package takagi.ru.monica.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.util.DataExportImportManager
import takagi.ru.monica.util.FileOperationHelper


/**
 * 数据导入界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDataScreen(
    onNavigateBack: () -> Unit,
    onImport: suspend (Uri) -> Result<Int>,  // 普通数据导入
    onImportAlipay: suspend (Uri) -> Result<Int>,  // 支付宝账单导入
    onImportAegis: suspend (Uri) -> Result<Int>  // Aegis JSON导入
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importType by remember { mutableStateOf("normal") } // "normal", "alipay" 或 "aegis"
    
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
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 警告卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            stringResource(R.string.import_data_description),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            stringResource(R.string.import_data_supported_formats),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 选择导入类型
            Text(
                stringResource(R.string.import_data_select_type),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = importType == "normal",
                    onClick = { importType = "normal" },
                    label = { Text(stringResource(R.string.import_data_type_normal)) },
                    leadingIcon = if (importType == "normal") {
                        { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp)) }
                    } else null,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                )
                FilterChip(
                    selected = importType == "alipay",
                    onClick = { importType = "alipay" },
                    label = { Text(stringResource(R.string.import_data_type_alipay)) },
                    leadingIcon = if (importType == "alipay") {
                        { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp)) }
                    } else null,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                )
                FilterChip(
                    selected = importType == "aegis",
                    onClick = { importType = "aegis" },
                    label = { Text("Aegis") },
                    leadingIcon = if (importType == "aegis") {
                        { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp)) }
                    } else null,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 选择文件按钮
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { 
                    activity?.let { act ->
                        // 根据导入类型选择不同的文件过滤器
                        when (importType) {
                            "aegis" -> FileOperationHelper.importFromJson(act)
                            else -> FileOperationHelper.importFromCsv(act) // normal 和 alipay 都使用 CSV
                        }
                    } ?: run {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.error_launch_export, "无法启动导出操作"))
                        }
                    }
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FileOpen,
                        contentDescription = null,
                        tint = if (selectedFileUri != null) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (selectedFileUri != null) stringResource(R.string.import_data_file_selected) else stringResource(R.string.import_data_select_file),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selectedFileUri != null) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurface
                        )
                        if (selectedFileName != null) {
                            Text(
                                selectedFileName!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            // 显示当前导入类型支持的文件格式
                            Text(
                                when (importType) {
                                    "aegis" -> stringResource(R.string.import_data_file_hint_json)
                                    "alipay" -> stringResource(R.string.import_data_file_hint_csv_alipay)
                                    else -> stringResource(R.string.import_data_file_hint_csv)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 导入按钮
            Button(
                onClick = {
                    selectedFileUri?.let { uri ->
                        scope.launch {
                            isImporting = true
                            try {
                                val result = when (importType) {
                                    "alipay" -> onImportAlipay(uri)  // 支付宝导入
                                    "aegis" -> onImportAegis(uri)   // Aegis导入
                                    else -> onImport(uri)  // 普通导入
                                }
                                
                                result.onSuccess { count ->
                                    val message = when (importType) {
                                        "alipay" -> context.getString(R.string.import_data_success_alipay, count)
                                        "aegis" -> "成功导入 $count 个TOTP验证器"
                                        else -> context.getString(R.string.import_data_success_normal, count)
                                    }
                                    snackbarHostState.showSnackbar(message)
                                    onNavigateBack()
                                }.onFailure { error ->
                                    snackbarHostState.showSnackbar(
                                        error.message ?: context.getString(R.string.import_data_error)
                                    )
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
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedFileUri != null && !isImporting
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.importing))
                } else {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.start_import))
                }
            }
            
            // 说明卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.import_data_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}