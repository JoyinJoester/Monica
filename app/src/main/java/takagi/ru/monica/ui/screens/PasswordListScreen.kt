package takagi.ru.monica.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.utils.ClipboardUtils
import takagi.ru.monica.viewmodel.PasswordViewModel

@OptIn(ExperimentalMaterial3Api::class)  
@Composable
fun PasswordListScreen(
    viewModel: PasswordViewModel,
    onAddPassword: () -> Unit,
    onEditPassword: (Long) -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    hideTopBar: Boolean = false
) {
    val context = LocalContext.current
    val clipboardUtils = remember { ClipboardUtils(context) }
    val passwordEntries by viewModel.passwordEntries.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    var showLogoutDialog by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var batchPasswordInput by remember { mutableStateOf("") }
    var showBatchPasswordVerify by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            if (!hideTopBar) {
                TopAppBar(
                    title = { 
                        Text(
                            if (selectionMode) 
                                "已选择 ${selectedItems.size} 项" 
                            else 
                                context.getString(R.string.app_name)
                        )
                    },
                    navigationIcon = {
                        if (selectionMode) {
                            IconButton(onClick = {
                                selectionMode = false
                                selectedItems = setOf()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "取消选择")
                            }
                        }
                    },
                    actions = {
                        if (selectionMode) {
                            // 全选/取消全选
                            IconButton(onClick = {
                                selectedItems = if (selectedItems.size == passwordEntries.size) {
                                    setOf()
                                } else {
                                    passwordEntries.map { it.id }.toSet()
                                }
                            }) {
                                Icon(
                                    if (selectedItems.size == passwordEntries.size) 
                                        Icons.Default.CheckCircle 
                                    else 
                                        Icons.Default.CheckCircleOutline,
                                    contentDescription = "全选"
                                )
                            }
                            // 删除
                            IconButton(
                                onClick = { showDeleteConfirmDialog = true },
                                enabled = selectedItems.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = if (selectedItems.isNotEmpty()) 
                                        MaterialTheme.colorScheme.error 
                                    else 
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                        } else {
                            IconButton(onClick = onSettings) {
                                Icon(Icons.Default.Settings, contentDescription = context.getString(R.string.settings_title))
                            }
                            IconButton(onClick = { showLogoutDialog = true }) {
                                Icon(Icons.Default.ExitToApp, contentDescription = context.getString(R.string.logout))
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!hideTopBar) {
                FloatingActionButton(
                    onClick = onAddPassword,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = context.getString(R.string.add_password))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text(context.getString(R.string.search_passwords)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = context.getString(R.string.search))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = context.getString(R.string.clear_search))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
            )
            
            // Password List
            if (passwordEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (searchQuery.isEmpty()) 
                                context.getString(R.string.no_passwords_saved) 
                            else 
                                context.getString(R.string.no_passwords_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(passwordEntries) { entry ->
                        PasswordEntryCard(
                            entry = entry,
                            isSelected = selectedItems.contains(entry.id),
                            selectionMode = selectionMode,
                            onCopyPassword = { password ->
                                if (!selectionMode) {
                                    clipboardUtils.copyToClipboard(password, "Password")
                                }
                            },
                            onCopyUsername = { username ->
                                if (!selectionMode) {
                                    clipboardUtils.copyToClipboard(username, "Username")
                                }
                            },
                            onCopyWebsite = { website ->
                                if (!selectionMode) {
                                    clipboardUtils.copyToClipboard(website, "Website")
                                }
                            },
                            onCopyNotes = { notes ->
                                if (!selectionMode) {
                                    clipboardUtils.copyToClipboard(notes, "Notes")
                                }
                            },
                            onEdit = { 
                                if (!selectionMode) {
                                    onEditPassword(entry.id)
                                }
                            },
                            onDelete = { 
                                if (!selectionMode) {
                                    viewModel.deletePasswordEntry(entry)
                                }
                            },
                            onClick = {
                                if (selectionMode) {
                                    selectedItems = if (selectedItems.contains(entry.id)) {
                                        selectedItems - entry.id
                                    } else {
                                        selectedItems + entry.id
                                    }
                                } else {
                                    onEditPassword(entry.id)
                                }
                            },
                            onLongPress = {
                                android.util.Log.d("PasswordList", "Long press detected on entry: ${entry.title}")
                                if (!selectionMode) {
                                    selectionMode = true
                                    selectedItems = setOf(entry.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Batch Delete Confirmation Dialog with Password
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirmDialog = false
                batchPasswordInput = ""
            },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("确认批量删除?")
            },
            text = {
                Column {
                    Text("确定要删除选中的 ${selectedItems.size} 个密码吗?此操作无法撤销。")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = batchPasswordInput,
                        onValueChange = { batchPasswordInput = it },
                        label = { Text("请输入主密码确认") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatchPasswordVerify = true
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = batchPasswordInput.isNotEmpty()
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showDeleteConfirmDialog = false
                        batchPasswordInput = ""
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
    
    // Batch Password Verification
    if (showBatchPasswordVerify) {
        LaunchedEffect(Unit) {
            val securityManager = takagi.ru.monica.security.SecurityManager(context)
            if (securityManager.verifyMasterPassword(batchPasswordInput)) {
                // 批量删除
                passwordEntries.filter { selectedItems.contains(it.id) }
                    .forEach { viewModel.deletePasswordEntry(it) }
                showBatchPasswordVerify = false
                selectionMode = false
                selectedItems = setOf()
                batchPasswordInput = ""
                android.widget.Toast.makeText(
                    context,
                    "已删除 ${selectedItems.size} 个密码",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                // 密码错误
                android.widget.Toast.makeText(
                    context,
                    "主密码错误",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                showBatchPasswordVerify = false
                showDeleteConfirmDialog = true // 重新显示对话框
            }
        }
    }
    
    // Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(context.getString(R.string.logout)) },
            text = { Text(context.getString(R.string.logout_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text(context.getString(R.string.logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PasswordEntryCard(
    entry: PasswordEntry,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onCopyPassword: (String) -> Unit,
    onCopyUsername: (String) -> Unit,
    onCopyWebsite: (String) -> Unit,
    onCopyNotes: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        color = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧:复选框(选择模式)+ 标题区域(可点击展开)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (selectionMode) {
                                onClick()
                            } else {
                                expanded = !expanded
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 选择模式下显示复选框
                    if (selectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (entry.website.isNotEmpty()) {
                            Text(
                                text = entry.website,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    // 展开/收起图标
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                // 右侧:菜单按钮(独立,不在可点击区域内)
                if (!selectionMode) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "菜单"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("编辑") },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除") },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("多选") },
                                onClick = {
                                    showMenu = false
                                    onLongPress()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.CheckBox, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            }
            
            if (expanded && !selectionMode) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Website with copy button
                if (entry.website.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "网站",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = entry.website,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(onClick = { onCopyWebsite(entry.website) }) {
                            Icon(Icons.Default.FileCopy, contentDescription = context.getString(R.string.copy))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Username
                if (entry.username.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = context.getString(R.string.username),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = entry.username,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(onClick = { onCopyUsername(entry.username) }) {
                            Icon(Icons.Default.FileCopy, contentDescription = context.getString(R.string.copy_username))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Password
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = context.getString(R.string.password),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = context.getString(R.string.password_hidden),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    IconButton(onClick = { onCopyPassword(entry.password) }) {
                        Icon(Icons.Default.FileCopy, contentDescription = context.getString(R.string.copy_password))
                    }
                }
                
                // Notes
                if (entry.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = context.getString(R.string.notes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = entry.notes,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(onClick = { onCopyNotes(entry.notes) }) {
                            Icon(Icons.Default.FileCopy, contentDescription = context.getString(R.string.copy))
                        }
                    }
                }
            }
        }
    }
    
    // Delete Confirmation Dialog with Password
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteDialog = false
                passwordInput = ""
            },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("删除密码") },
            text = {
                Column {
                    Text("确定要删除「${entry.title}」吗?")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("请输入主密码确认") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPasswordDialog = true
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = passwordInput.isNotEmpty()
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showDeleteDialog = false
                        passwordInput = ""
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
    
    // Password Verification Dialog
    if (showPasswordDialog) {
        LaunchedEffect(Unit) {
            val securityManager = takagi.ru.monica.security.SecurityManager(context)
            if (securityManager.verifyMasterPassword(passwordInput)) {
                onDelete()
                showPasswordDialog = false
                passwordInput = ""
            } else {
                // 密码错误
                android.widget.Toast.makeText(
                    context,
                    "主密码错误",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                showPasswordDialog = false
                showDeleteDialog = true // 重新显示对话框
            }
        }
    }
}