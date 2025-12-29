package takagi.ru.monica.wear.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import takagi.ru.monica.wear.R
import takagi.ru.monica.wear.viewmodel.TotpItemState

private const val SEARCH_LOG_TAG = "WearSearchOverlay"

/**
 * 搜索覆盖层组件 - Bottom Sheet 样式
 * 纯色背景，从底部弹出
 */
@Composable
fun SearchOverlay(
    searchQuery: String,
    searchResults: List<TotpItemState>,
    onSearchQueryChange: (String) -> Unit,
    onResultClick: (TotpItemState) -> Unit,
    onDismiss: () -> Unit,
    onAddTotp: (secret: String, issuer: String, accountName: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onEditTotp: (item: TotpItemState, secret: String, issuer: String, accountName: String, onResult: (Boolean, String?) -> Unit) -> Unit = { _, _, _, _, onResult -> onResult(false, "未实现") },
    onDeleteTotp: (TotpItemState) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var dragOffset by remember { mutableStateOf(0f) }
    var showAddTotpDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<TotpItemState?>(null) }
    
    LaunchedEffect(searchQuery, searchResults.size) {
        Log.d(
            SEARCH_LOG_TAG,
            "Search state updated: query='${'$'}searchQuery', results=${'$'}{searchResults.size}"
        )
    }

    val scrimInteraction = remember { MutableInteractionSource() }
    val dismissAction: () -> Unit = remember(onDismiss) {
        {
            Log.d(SEARCH_LOG_TAG, "Dismiss requested")
            onDismiss()
        }
    }
    val handleResultClick: (TotpItemState) -> Unit = remember(onResultClick) {
        { item ->
            Log.d(
                SEARCH_LOG_TAG,
                "Search result clicked: issuer='${'$'}{item.totpData.issuer}', account='${'$'}{item.totpData.accountName}'"
            )
            onResultClick(item)
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 半透明遮罩，点击空白区域可关闭
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.5f))
                // 不再在遮罩上捕获拖拽手势，避免与输入法/候选词交互冲突
                .clickable(
                    onClick = dismissAction,
                    indication = null,
                    interactionSource = scrimInteraction
                )
        )

        // Bottom Sheet 容器
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .align(Alignment.BottomCenter)
                .offset(y = dragOffset.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .padding(20.dp)
        ) {
            // 顶部拖动指示器区域 - 可拖动关闭
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragOffset > 100) {
                                    Log.d(SEARCH_LOG_TAG, "Search sheet drag dismissed, offset=${'$'}dragOffset")
                                    dismissAction()
                                }
                                dragOffset = 0f
                            },
                            onVerticalDrag = { _, dragAmount ->
                                val newOffset = dragOffset + dragAmount
                                if (newOffset >= 0) {
                                    dragOffset = newOffset
                                }
                                Log.v(
                                    SEARCH_LOG_TAG,
                                    "Search sheet dragging: dragAmount=${'$'}dragAmount, accumulated=${'$'}dragOffset"
                                )
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // 拖动指示器
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 标题
            Text(
                text = stringResource(R.string.search_title),
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 搜索框
            SearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onAddClick = { showAddTotpDialog = true },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 搜索结果列表
            if (searchResults.isEmpty() && searchQuery.isNotBlank()) {
                // 无结果提示
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.search_no_results),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                // 结果列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(searchResults) { item ->
                        SearchResultItem(
                            item = item,
                            onClick = { handleResultClick(item) },
                            onEdit = { 
                                editingItem = item
                            },
                            onDelete = { onDeleteTotp(item) }
                        )
                    }
                }
            }
        }
    }
    
    // 添加验证器对话框
    if (showAddTotpDialog) {
        AddTotpDialog(
            onDismiss = { showAddTotpDialog = false },
            onConfirm = { secret, issuer, accountName, onResult ->
                onAddTotp(secret, issuer, accountName) { success, error ->
                    onResult(success, error)
                    if (success) {
                        showAddTotpDialog = false
                    }
                }
            }
        )
    }
    
    // 编辑验证器对话框
    if (editingItem != null) {
        AddTotpDialog(
            editItem = editingItem,
            onDismiss = { editingItem = null },
            onConfirm = { secret, issuer, accountName, onResult ->
                onEditTotp(editingItem!!, secret, issuer, accountName) { success, error ->
                    onResult(success, error)
                    if (success) {
                        editingItem = null
                    }
                }
            }
        )
    }
}

/**
 * 搜索框组件
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = query,
            onValueChange = { newValue ->
                Log.d(
                    SEARCH_LOG_TAG,
                    "Search input changed from '" + query + "' to '" + newValue + "'"
                )
                onQueryChange(newValue)
            },
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            ),
            placeholder = {
                Text(
                    text = stringResource(R.string.search_hint),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                focusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                cursorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
        
        androidx.compose.material3.IconButton(
            onClick = onAddClick,
            modifier = Modifier.size(48.dp),
            colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Add,
                contentDescription = stringResource(R.string.search_add_button),
                tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * 搜索结果项组件
 */
@Composable
private fun SearchResultItem(
    item: TotpItemState,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：标题信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 发行者
                if (item.totpData.issuer.isNotBlank()) {
                    Text(
                        text = item.totpData.issuer,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // 账户名
                if (item.totpData.accountName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.totpData.accountName,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 中间：验证码预览
            Text(
                text = formatCode(item.code),
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.width(4.dp))
            
            // 右侧：三点菜单
            Box {
                androidx.compose.material3.IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.dialog_menu_more),
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                androidx.compose.material3.DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(stringResource(R.string.dialog_menu_edit)) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        }
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { 
                            Text(
                                stringResource(R.string.dialog_menu_delete), 
                                color = androidx.compose.material3.MaterialTheme.colorScheme.error
                            ) 
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 格式化验证码
 */
private fun formatCode(code: String): String {
    return when {
        code.length == 6 -> "${code.substring(0, 3)} ${code.substring(3)}"
        code.length == 8 -> "${code.substring(0, 4)} ${code.substring(4)}"
        else -> code
    }
}

/**
 * 添加/编辑TOTP验证器对话框
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AddTotpDialog(
    editItem: TotpItemState? = null,
    onDismiss: () -> Unit,
    onConfirm: (secret: String, issuer: String, accountName: String, onResult: (Boolean, String?) -> Unit) -> Unit
) {
    val isEditMode = editItem != null
    var secret by remember { mutableStateOf(editItem?.totpData?.secret ?: "") }
    var issuer by remember { mutableStateOf(editItem?.totpData?.issuer ?: "") }
    var accountName by remember { mutableStateOf(editItem?.totpData?.accountName ?: "") }
    var secretError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            androidx.compose.material3.Icon(
                imageVector = if (isEditMode) 
                    androidx.compose.material.icons.Icons.Default.Edit 
                else 
                    androidx.compose.material.icons.Icons.Default.Add,
                contentDescription = null,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                stringResource(if (isEditMode) R.string.totp_edit_title else R.string.totp_add_title), 
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 密钥输入
                androidx.compose.material3.OutlinedTextField(
                    value = secret,
                    onValueChange = { 
                        secret = it
                        secretError = null
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.dialog_totp_secret)) },
                    placeholder = { Text(stringResource(R.string.dialog_totp_secret_hint)) },
                    isError = secretError != null,
                    supportingText = secretError?.let { { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )

                // 发行方输入
                androidx.compose.material3.OutlinedTextField(
                    value = issuer,
                    onValueChange = { 
                        issuer = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.dialog_totp_issuer)) },
                    placeholder = { Text(stringResource(R.string.dialog_totp_issuer_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )

                // 账户名输入
                androidx.compose.material3.OutlinedTextField(
                    value = accountName,
                    onValueChange = { 
                        accountName = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.dialog_totp_account)) },
                    placeholder = { Text(stringResource(R.string.dialog_totp_account_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )

                // 错误提示
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    when {
                        secret.isBlank() -> secretError = context.getString(R.string.dialog_totp_secret_error)
                        else -> {
                            isLoading = true
                            errorMessage = null
                            onConfirm(secret, issuer, accountName) { success, error ->
                                isLoading = false
                                if (!success) {
                                    errorMessage = error ?: context.getString(if (isEditMode) R.string.totp_update_failed else R.string.totp_add_failed)
                                }
                            }
                        }
                    }
                },
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
