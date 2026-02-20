package takagi.ru.monica.passkey

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordDatabase
import java.text.SimpleDateFormat
import java.util.*

/**
 * Passkey 管理主界面
 * 
 * 展示用户保存的所有 Passkey，支持搜索、查看详情和删除
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasskeyScreen(
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { PasswordDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    // 数据状态
    val passkeys by database.passkeyDao().getAllPasskeys().collectAsState(initial = emptyList())
    val passkeyCount by database.passkeyDao().getPasskeyCount().collectAsState(initial = 0)
    
    // UI 状态
    var searchQuery by remember { mutableStateOf("") }
    var selectedPasskey by remember { mutableStateOf<PasskeyEntry?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var passkeyToDelete by remember { mutableStateOf<PasskeyEntry?>(null) }
    
    // 过滤后的列表
    val filteredPasskeys = remember(passkeys, searchQuery) {
        if (searchQuery.isBlank()) {
            passkeys
        } else {
            passkeys.filter { passkey ->
                passkey.rpId.contains(searchQuery, ignoreCase = true) ||
                passkey.rpName.contains(searchQuery, ignoreCase = true) ||
                passkey.userName.contains(searchQuery, ignoreCase = true) ||
                passkey.userDisplayName.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog && passkeyToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteDialog = false
                passkeyToDelete = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.passkey_delete_title)) },
            text = { 
                Text(
                    stringResource(
                        R.string.passkey_delete_message,
                        passkeyToDelete!!.rpName,
                        passkeyToDelete!!.userDisplayName.ifBlank { passkeyToDelete!!.userName }
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        passkeyToDelete?.let { passkey ->
                            scope.launch {
                                database.passkeyDao().delete(passkey)
                            }
                        }
                        showDeleteDialog = false
                        passkeyToDelete = null
                        selectedPasskey = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteDialog = false
                    passkeyToDelete = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // 详情底部表单
    if (selectedPasskey != null) {
        PasskeyDetailBottomSheet(
            passkey = selectedPasskey!!,
            onDismiss = { selectedPasskey = null },
            onDelete = {
                passkeyToDelete = selectedPasskey
                showDeleteDialog = true
            }
        )
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        // 顶部标题栏
        PasskeyHeader(
            passkeyCount = passkeyCount,
            onSettingsClick = onNavigateToSettings
        )
        
        // 搜索栏
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        // Passkey 列表
        if (filteredPasskeys.isEmpty()) {
            EmptyPasskeyState(
                hasSearchQuery = searchQuery.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = filteredPasskeys,
                    key = { it.credentialId }
                ) { passkey ->
                    PasskeyCard(
                        passkey = passkey,
                        onClick = { selectedPasskey = passkey },
                        onDelete = {
                            passkeyToDelete = passkey
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PasskeyHeader(
    passkeyCount: Int,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.passkey),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.passkey_count, passkeyCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.settings)
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.passkey_search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.clear)
                    )
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    )
}

@Composable
private fun EmptyPasskeyState(
    hasSearchQuery: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Key,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = if (hasSearchQuery) {
                stringResource(R.string.passkey_search_empty)
            } else {
                stringResource(R.string.passkey_empty_title)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (hasSearchQuery) {
                stringResource(R.string.passkey_search_empty_hint)
            } else {
                stringResource(R.string.passkey_empty_subtitle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PasskeyCard(
    passkey: PasskeyEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // 显示网站首字母
                Text(
                    text = passkey.rpName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = passkey.rpName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = passkey.userDisplayName.ifBlank { passkey.userName },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formatRelativeTime(passkey.lastUsedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            
            // 删除按钮
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasskeyDetailBottomSheet(
    passkey: PasskeyEntry,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // 头部
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Key,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = passkey.rpName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = passkey.rpId,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            
            // 详细信息
            DetailRow(
                icon = Icons.Outlined.Person,
                label = stringResource(R.string.passkey_detail_user),
                value = passkey.userDisplayName.ifBlank { passkey.userName }
            )
            
            DetailRow(
                icon = Icons.Outlined.Email,
                label = stringResource(R.string.passkey_detail_username),
                value = passkey.userName
            )
            
            DetailRow(
                icon = Icons.Outlined.DateRange,
                label = stringResource(R.string.passkey_detail_created),
                value = formatDateTime(passkey.createdAt)
            )
            
            DetailRow(
                icon = Icons.Outlined.Schedule,
                label = stringResource(R.string.passkey_detail_last_used),
                value = formatDateTime(passkey.lastUsedAt)
            )
            
            DetailRow(
                icon = Icons.Outlined.Numbers,
                label = stringResource(R.string.passkey_detail_use_count),
                value = passkey.useCount.toString()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 删除按钮
            Button(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.passkey_delete_button))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        diff < 604_800_000 -> "${diff / 86_400_000} 天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun formatDateTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
