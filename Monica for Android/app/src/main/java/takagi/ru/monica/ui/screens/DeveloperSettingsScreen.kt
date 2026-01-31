package takagi.ru.monica.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.ui.res.stringResource
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
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

    // 准备共享元素 Modifier
    val sharedTransitionScope = takagi.ru.monica.ui.LocalSharedTransitionScope.current
    val animatedVisibilityScope = takagi.ru.monica.ui.LocalAnimatedVisibilityScope.current
    
    var sharedModifier: Modifier = Modifier
    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            sharedModifier = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "developer_settings_card"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
            )
        }
    }

    Scaffold(
        modifier = sharedModifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.developer_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.developer_settings_back))
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
                title = stringResource(R.string.developer_log_debugging)
            ) {
                SettingsItem(
                    icon = Icons.Default.BugReport,
                    title = stringResource(R.string.developer_view_logs),
                    subtitle = stringResource(R.string.developer_view_logs_desc),
                    onClick = { showDebugLogsDialog = true }
                )
                
                SettingsItem(
                    icon = Icons.Default.DeleteSweep,
                    title = stringResource(R.string.developer_clear_log_buffer),
                    subtitle = stringResource(R.string.developer_clear_log_buffer_desc),
                    onClick = {
                        try {
                            Runtime.getRuntime().exec("logcat -c")
                            Toast.makeText(
                                context,
                                context.getString(R.string.developer_log_buffer_cleared),
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.developer_clear_failed, e.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
                
                SettingsItem(
                    icon = Icons.Default.Share,
                    title = stringResource(R.string.developer_share_logs),
                    subtitle = stringResource(R.string.developer_share_logs_desc),
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
                                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.developer_share_subject))
                                    putExtra(Intent.EXTRA_TEXT, logs)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.developer_share_title)))
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.developer_share_failed, e.message ?: ""),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }
            
            // 开发者功能
            SettingsSection(
                title = stringResource(R.string.developer_functions)
            ) {
                SettingsItemWithSwitch(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.developer_disable_password_verification),
                    subtitle = stringResource(R.string.developer_disable_password_verification_desc),
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
                        text = stringResource(R.string.developer_warning),
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
    var logs by remember { mutableStateOf(context.getString(R.string.developer_loading_logs)) }
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
                context.getString(R.string.developer_no_logs)
            }
            isLoading = false
        } catch (e: Exception) {
            logs = context.getString(R.string.developer_load_failed, e.message ?: "")
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
                    text = stringResource(R.string.developer_system_logs),
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
                                        context.getString(R.string.developer_no_logs)
                                    }
                                } catch (e: Exception) {
                                    logs = context.getString(R.string.developer_load_failed, e.message ?: "")
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.developer_refresh),
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
                Text(stringResource(R.string.developer_close))
            }
        }
    )
}

