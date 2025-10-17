package takagi.ru.monica.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.ui.theme.MonicaTheme

/**
 * 应用信息数据类
 */
data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable
)

/**
 * 应用列表屏幕 - Jetpack Compose 实现
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    
    var appList by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var filteredAppList by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()

    // 加载应用列表
    LaunchedEffect(Unit) {
        isLoading = true
        val apps = withContext(Dispatchers.IO) {
            loadInstalledApps(packageManager)
        }
        appList = apps
        filteredAppList = apps
        isLoading = false
    }

    // 搜索过滤
    LaunchedEffect(searchQuery) {
        filteredAppList = if (searchQuery.isEmpty()) {
            appList
        } else {
            appList.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                // 搜索模式
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索应用...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "关闭搜索")
                        }
                    }
                )
            } else {
                // 正常模式
                TopAppBar(
                    title = { 
                        Text("已安装应用 (${filteredAppList.size})") 
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                // 加载中
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在加载应用列表...")
                }
            } else if (filteredAppList.isEmpty()) {
                // 空列表
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (searchQuery.isEmpty()) "未找到已安装的应用" else "未找到匹配的应用",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 应用列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredAppList, key = { it.packageName }) { appInfo ->
                        AppListItem(
                            appInfo = appInfo,
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                                        if (launchIntent != null) {
                                            context.startActivity(launchIntent)
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("AppListScreen", "Failed to launch app", e)
                                    }
                                }
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

/**
 * 应用列表项
 */
@Composable
fun AppListItem(
    appInfo: AppInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 应用图标
        Image(
            bitmap = appInfo.icon.toBitmap().asImageBitmap(),
            contentDescription = appInfo.appName,
            modifier = Modifier.size(48.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // 应用信息
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = appInfo.appName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = appInfo.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 加载已安装的应用列表（只包含有启动器图标的应用）
 */
private fun loadInstalledApps(packageManager: PackageManager): List<AppInfo> {
    val appList = mutableListOf<AppInfo>()
    
    try {
        // 创建Intent，查询所有有启动器图标的应用
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        // 查询所有匹配的Activity
        val resolveInfoList = packageManager.queryIntentActivities(intent, 0)
        
        // 转换为AppInfo对象
        for (resolveInfo in resolveInfoList) {
            val activityInfo = resolveInfo.activityInfo
            val appName = activityInfo.loadLabel(packageManager).toString()
            val packageName = activityInfo.packageName
            val icon = activityInfo.loadIcon(packageManager)
            
            appList.add(AppInfo(appName, packageName, icon))
        }
        
        // 按应用名称排序
        appList.sortBy { it.appName.lowercase() }
        
    } catch (e: Exception) {
        android.util.Log.e("AppListScreen", "Error loading apps", e)
    }
    
    return appList
}
