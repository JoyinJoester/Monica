package takagi.ru.monica.wear.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import takagi.ru.monica.wear.R
import takagi.ru.monica.wear.viewmodel.TotpItemState

/**
 * 搜索页面 - 简洁 WearOS 风格
 */
@Composable
fun SearchOverlay(
    searchQuery: String,
    searchResults: List<TotpItemState>,
    onSearchQueryChange: (String) -> Unit,
    onNavigateToItem: (TotpItemState) -> Unit,
    onDismiss: () -> Unit,
    onAddTotp: (secret: String, issuer: String, accountName: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onEditTotp: (item: TotpItemState, secret: String, issuer: String, accountName: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onDeleteTotp: (item: TotpItemState) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<TotpItemState?>(null) }
    var deletingItem by remember { mutableStateOf<TotpItemState?>(null) }
    // 使用ID来追踪选中项，避免对象引用变化导致状态丢失
    var selectedItemId by remember { mutableStateOf<Long?>(null) }
    
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberScalingLazyListState()
    
    val configuration = LocalConfiguration.current
    val isRound = configuration.isScreenRound
    
    // 监听滚动状态来控制底部栏显示
    var showBottomBar by remember { mutableStateOf(true) }
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    var lastCenterItemIndex by remember { mutableStateOf(0) }
    
    // 只有在真正滚动时才检测方向
    LaunchedEffect(isScrolling, listState.centerItemIndex) {
        if (isScrolling) {
            val currentIndex = listState.centerItemIndex
            if (currentIndex > lastCenterItemIndex) {
                // 向下滚动（查看更多内容）- 隐藏底部栏
                showBottomBar = false
            } else if (currentIndex < lastCenterItemIndex) {
                // 向上滚动 - 显示底部栏
                showBottomBar = true
            }
            lastCenterItemIndex = currentIndex
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(
                top = 24.dp,
                bottom = 48.dp,
                start = 12.dp,
                end = 12.dp
            )
        ) {
            // 搜索框
            item {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onSearch = { keyboardController?.hide() },
                    modifier = Modifier
                        .fillMaxWidth(if (isRound) 0.92f else 0.95f)
                        .padding(bottom = 12.dp)
                )
            }
            
            // 搜索结果
            if (searchResults.isEmpty()) {
                item {
                    EmptySearchState(
                        message = if (searchQuery.isBlank()) {
                            stringResource(R.string.no_entries)
                        } else {
                            stringResource(R.string.no_search_results)
                        }
                    )
                }
            } else {
                items(
                    items = searchResults,
                    key = { it.item.id }
                ) { itemState ->
                    val itemId = itemState.item.id
                    TotpResultCard(
                        itemState = itemState,
                        isExpanded = selectedItemId == itemId,
                        onClick = {
                            selectedItemId = if (selectedItemId == itemId) null else itemId
                        },
                        onNavigate = {
                            onNavigateToItem(itemState)
                        },
                        onEdit = {
                            editingItem = itemState
                            selectedItemId = null
                        },
                        onDelete = {
                            deletingItem = itemState
                            selectedItemId = null
                        }
                    )
                }
            }
        }
        
        // 底部操作栏 - 固定在底部，根据滚动方向显隐
        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomActionBar(
                onAdd = { showAddDialog = true },
                onClose = onDismiss
            )
        }
    }
    
    // 添加对话框
    if (showAddDialog) {
        WearTotpDialog(
            title = stringResource(R.string.add_totp),
            initialIssuer = "",
            initialAccount = "",
            initialSecret = "",
            showSecret = true,
            onDismiss = { showAddDialog = false },
            onConfirm = { secret, issuer, account ->
                onAddTotp(secret, issuer, account) { success, _ ->
                    if (success) showAddDialog = false
                }
            }
        )
    }
    
    // 编辑对话框
    editingItem?.let { itemState ->
        WearTotpDialog(
            title = stringResource(R.string.edit_totp),
            initialIssuer = itemState.totpData.issuer,
            initialAccount = itemState.totpData.accountName,
            initialSecret = itemState.totpData.secret,
            showSecret = true,
            onDismiss = { editingItem = null },
            onConfirm = { secret, issuer, account ->
                onEditTotp(itemState, secret, issuer, account) { success, _ ->
                    if (success) editingItem = null
                }
            }
        )
    }
    
    // 删除确认对话框
    deletingItem?.let { itemState ->
        DeleteConfirmDialog(
            itemName = itemState.totpData.issuer.ifBlank { itemState.totpData.accountName }.ifBlank { "Unknown" },
            onDismiss = { deletingItem = null },
            onConfirm = {
                onDeleteTotp(itemState)
                deletingItem = null
            }
        )
    }
}

/**
 * 删除确认对话框 - 简洁版
 */
@Composable
private fun DeleteConfirmDialog(
    itemName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题
                Text(
                    text = stringResource(R.string.delete_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // 按钮
                Row(
                    horizontalArrangement = Arrangement.Center
                ) {
                    // 取消按钮
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.size(ButtonDefaults.LargeButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(24.dp))
                    
                    // 删除按钮
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.size(ButtonDefaults.LargeButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.confirm),
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 搜索栏 - 只显示搜索图标
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.height(44.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .wrapContentHeight(Alignment.CenterVertically)
            )
        }
    }
}

/**
 * 空状态
 */
@Composable
private fun EmptySearchState(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 24.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 验证器结果卡片
 */
@Composable
private fun TotpResultCard(
    itemState: TotpItemState,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onNavigate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val issuer = itemState.totpData.issuer
    val account = itemState.totpData.accountName
    val code = itemState.code
    
    val displayName = when {
        issuer.isNotBlank() && account.isNotBlank() -> issuer
        issuer.isNotBlank() -> issuer
        account.isNotBlank() -> account
        else -> "Unknown"
    }
    
    val subtitle = when {
        issuer.isNotBlank() && account.isNotBlank() -> account
        else -> null
    }
    
    val formattedCode = if (code.length == 6) {
        "${code.substring(0, 3)} ${code.substring(3)}"
    } else code
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // 主卡片
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：名称和账户
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // 右侧：验证码
                Text(
                    text = formattedCode,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // 展开的操作按钮 - 只显示图标
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 })
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                // 跳转按钮
                Button(
                    onClick = onNavigate,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // 编辑按钮
                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.common_edit),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // 删除按钮
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * 底部操作栏
 */
@Composable
private fun BottomActionBar(
    onAdd: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(bottom = 8.dp, top = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 关闭按钮
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(24.dp))
            
            // 添加按钮
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_totp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * 通用 TOTP 编辑对话框
 */
@Composable
private fun WearTotpDialog(
    title: String,
    initialIssuer: String,
    initialAccount: String,
    initialSecret: String,
    showSecret: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (secret: String, issuer: String, account: String) -> Unit
) {
    var issuer by remember { mutableStateOf(initialIssuer) }
    var account by remember { mutableStateOf(initialAccount) }
    var secret by remember { mutableStateOf(initialSecret) }
    var showError by remember { mutableStateOf(false) }
    
    val listState = rememberScalingLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    
    val configuration = LocalConfiguration.current
    val isRound = configuration.isScreenRound
    val inputWidth = if (isRound) 0.92f else 0.95f
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(
                    top = 24.dp,
                    bottom = 48.dp,
                    start = 12.dp,
                    end = 12.dp
                )
            ) {
                // 标题
                item {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                
                // 服务名
                item {
                    DialogTextField(
                        value = issuer,
                        onValueChange = { issuer = it },
                        placeholder = stringResource(R.string.dialog_totp_issuer),
                        imeAction = ImeAction.Next,
                        modifier = Modifier.fillMaxWidth(inputWidth)
                    )
                }
                
                // 账户
                item {
                    DialogTextField(
                        value = account,
                        onValueChange = { account = it },
                        placeholder = stringResource(R.string.dialog_totp_account),
                        keyboardType = KeyboardType.Email,
                        imeAction = if (showSecret) ImeAction.Next else ImeAction.Done,
                        onDone = if (!showSecret) {{ keyboardController?.hide() }} else null,
                        modifier = Modifier.fillMaxWidth(inputWidth)
                    )
                }
                
                // 密钥
                if (showSecret) {
                    item {
                        DialogTextField(
                            value = secret,
                            onValueChange = { 
                                secret = it.uppercase().filter { c -> c.isLetterOrDigit() }
                                showError = false
                            },
                            placeholder = stringResource(R.string.dialog_totp_secret_required),
                            isError = showError,
                            imeAction = ImeAction.Done,
                            onDone = { keyboardController?.hide() },
                            modifier = Modifier.fillMaxWidth(inputWidth)
                        )
                    }
                    
                    if (showError) {
                        item {
                            Text(
                                text = stringResource(R.string.dialog_totp_secret_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            
            // 底部按钮
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(bottom = 12.dp, top = 20.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(24.dp))
                
                Button(
                    onClick = {
                        if (showSecret && secret.isBlank()) {
                            showError = true
                        } else {
                            onConfirm(secret, issuer, account)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.confirm),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * 对话框输入框
 */
@Composable
private fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    isError: Boolean = false,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        singleLine = true,
        isError = isError,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            cursorColor = MaterialTheme.colorScheme.primary,
            errorBorderColor = MaterialTheme.colorScheme.error
        ),
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onDone = onDone?.let { { it() } }
        ),
        modifier = modifier
            .padding(vertical = 4.dp)
            .height(52.dp)
    )
}
