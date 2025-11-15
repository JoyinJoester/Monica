package takagi.ru.monica.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import takagi.ru.monica.utils.BiometricAuthHelper
import androidx.compose.ui.text.font.FontFamily
import android.util.Log
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.viewmodel.SettingsViewModel

/**
 * 开发者设置页面
 * 包含日志查看、清除以及开发者专用功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    
    var showDebugLogsDialog by remember { mutableStateOf(false) }
    var disablePasswordVerification by remember { mutableStateOf(settings.disablePasswordVerification) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开发者设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            // 日志调试区域
            SettingsSection(
                title = "日志调试"
            ) {
                SettingsItem(
                    icon = Icons.Default.BugReport,
                    title = "查看日志",
                    subtitle = "查看应用的 Logcat 输出日志",
                    onClick = { showDebugLogsDialog = true }
                )
                
                SettingsItem(
                    icon = Icons.Default.DeleteSweep,
                    title = "清除日志缓冲区",
                    subtitle = "清除设备的日志缓冲区",
                    onClick = {
                        try {
                            Runtime.getRuntime().exec("logcat -c")
                            Toast.makeText(
                                context,
                                "日志缓冲区已清除",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "清除失败: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
                
                SettingsItem(
                    icon = Icons.Default.Share,
                    title = "分享日志",
                    subtitle = "导出并分享最近的日志",
                    onClick = {
                        scope.launch {
                            try {
                                val process = Runtime.getRuntime().exec(arrayOf(
                                    "logcat",
                                    "-d",
                                    "-t", "500",
                                    "takagi.ru.monica:*",
                                    "MonicaAutofill:*",
                                    "*:S"
                                ))
                                val logs = process.inputStream.bufferedReader().readText()
                                
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Monica 应用日志")
                                    putExtra(Intent.EXTRA_TEXT, logs)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "分享日志"))
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "分享失败: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }
            
            // 开发者功能
            SettingsSection(
                title = "开发者功能"
            ) {
                SettingsItemWithSwitch(
                    icon = Icons.Default.Lock,
                    title = "关闭密码验证",
                    subtitle = "跳过应用启动时的密码验证（仅用于开发测试）",
                    checked = disablePasswordVerification,
                    onCheckedChange = { enabled ->
                        android.util.Log.d("DeveloperSettings", "Toggling password verification: $enabled")
                        disablePasswordVerification = enabled
                        scope.launch {
                            viewModel.updateDisablePasswordVerification(enabled)
                            android.util.Log.d("DeveloperSettings", "Password verification setting updated to: $enabled")
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 警告提示
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "⚠️ 开发者功能仅供测试使用，请勿在生产环境中启用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // 显示日志对话框
    if (showDebugLogsDialog) {
        DebugLogsDialog(
            onDismiss = { showDebugLogsDialog = false }
        )
    }
}

/**
 * 调试日志对话框 - 显示真实的 Logcat 输出
 */
@Composable
fun DebugLogsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf("正在加载日志...") }
    var isLoading by remember { mutableStateOf(true) }
    
    // 加载日志
    LaunchedEffect(Unit) {
        try {
            // 获取最近500行日志,过滤 Monica 应用相关
            val process = Runtime.getRuntime().exec(arrayOf(
                "logcat",
                "-d",
                "-t", "500",
                "takagi.ru.monica:*",
                "MonicaAutofill:*",
                "AutofillSaveActivity:*",
                "*:S"  // 静默其他标签
            ))
            
            val output = process.inputStream.bufferedReader().readText()
            logs = if (output.isNotBlank()) {
                output
            } else {
                "暂无日志\n\n提示: 确保应用已运行过一些操作"
            }
            isLoading = false
        } catch (e: Exception) {
            logs = "加载失败: ${e.message}\n\n可能需要调试权限才能读取日志"
            isLoading = false
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "系统日志",
                    fontWeight = FontWeight.Bold
                )
                if (!isLoading) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val process = Runtime.getRuntime().exec(arrayOf(
                                        "logcat",
                                        "-d",
                                        "-t", "500",
                                        "takagi.ru.monica:*",
                                        "MonicaAutofill:*",
                                        "AutofillSaveActivity:*",
                                        "*:S"
                                    ))
                                    val output = process.inputStream.bufferedReader().readText()
                                    logs = if (output.isNotBlank()) {
                                        output
                                    } else {
                                        "暂无日志\n\n提示: 确保应用已运行过一些操作"
                                    }
                                } catch (e: Exception) {
                                    logs = "加载失败: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        text = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(
                        text = logs,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

