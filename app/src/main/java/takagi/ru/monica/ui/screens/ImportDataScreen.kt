package takagi.ru.monica.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.layout.*
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
    onImportAlipay: suspend (Uri) -> Result<Int>  // 支付宝账单导入
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importType by remember { mutableStateOf("normal") } // "normal" 或 "alipay"
    
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
                            "从CSV文件导入数据",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "支持应用导出的CSV文件和支付宝交易明细",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 选择导入类型
            Text(
                "选择导入类型",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = importType == "normal",
                    onClick = { importType = "normal" },
                    label = { Text("应用数据") },
                    leadingIcon = if (importType == "normal") {
                        { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = importType == "alipay",
                    onClick = { importType = "alipay" },
                    label = { Text("支付宝账单") },
                    leadingIcon = if (importType == "alipay") {
                        { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 选择文件按钮
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { 
                    activity?.let { act ->
                        FileOperationHelper.importFromCsv(act)
                    } ?: run {
                        scope.launch {
                            snackbarHostState.showSnackbar("无法启动导入操作")
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
                            if (selectedFileUri != null) "已选择文件" else "选择备份文件",
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
                            Text(
                                "点击选择 .csv 文件",
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
                                val result = if (importType == "alipay") {
                                    onImportAlipay(uri)  // 支付宝导入
                                } else {
                                    onImport(uri)  // 普通导入
                                }
                                
                                result.onSuccess { count ->
                                    val message = if (importType == "alipay") {
                                        "成功导入 $count 条支付宝交易"
                                    } else {
                                        "成功导入 $count 条数据"
                                    }
                                    snackbarHostState.showSnackbar(message)
                                    onNavigateBack()
                                }.onFailure { error ->
                                    snackbarHostState.showSnackbar(
                                        error.message ?: "导入失败，请检查文件格式"
                                    )
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ImportDataScreen", "导入异常", e)
                                snackbarHostState.showSnackbar(
                                    "导入失败：${e.message ?: "未知错误"}"
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
                        "导入的数据将添加到现有数据中，不会删除当前数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}