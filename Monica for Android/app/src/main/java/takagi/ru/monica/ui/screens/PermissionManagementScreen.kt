package takagi.ru.monica.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.data.model.PermissionInfo
import takagi.ru.monica.data.model.PermissionStatus
import takagi.ru.monica.ui.components.*
import takagi.ru.monica.viewmodel.PermissionViewModel

/**
 * 权限管理主界面
 * Permission management main screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: PermissionViewModel = viewModel()
) {
    val context = LocalContext.current
    val permissionsByCategory by viewModel.permissionsByCategory.collectAsState()
    val permissionStats by viewModel.permissionStats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showHelpDialog by remember { mutableStateOf(false) }
    var showInfoCard by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permission_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshPermissions() },
                        enabled = !isLoading
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh)
                        )
                    }
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            Icons.Default.Help,
                            contentDescription = stringResource(R.string.help)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // 信息卡片
                if (showInfoCard) {
                    PermissionInfoCard(
                        onDismiss = { showInfoCard = false }
                    )
                }

                // 权限统计
                permissionStats?.let { stats ->
                    PermissionStatsCard(stats = stats)
                }

                // 按分类显示权限
                permissionsByCategory.forEach { (category, permissions) ->
                    PermissionCategorySection(
                        category = category,
                        permissions = permissions,
                        onPermissionClick = { permission ->
                            handlePermissionClick(context, permission)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // 加载指示器
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(16.dp)
                        .size(48.dp)
                )
            }
        }
    }

    // 帮助对话框
    if (showHelpDialog) {
        PermissionHelpDialog(
            onDismiss = { showHelpDialog = false }
        )
    }
}

/**
 * 处理权限点击事件
 * Handle permission click event
 */
private fun handlePermissionClick(context: Context, permission: PermissionInfo) {
    // 所有权限点击都跳转到系统设置
    if (permission.status != PermissionStatus.UNAVAILABLE) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                context.getString(R.string.cannot_open_settings),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
