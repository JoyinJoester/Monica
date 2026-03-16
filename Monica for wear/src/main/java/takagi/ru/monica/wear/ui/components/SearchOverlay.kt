package takagi.ru.monica.wear.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.foundation.BorderStroke
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.Button
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
    var expandedItemId by remember { mutableStateOf<Long?>(null) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberScalingLazyListState()
    val isRound = LocalConfiguration.current.isScreenRound
    val sheetWidth = if (isRound) 0.97f else 0.94f
    val sheetHeight = if (isRound) 0.82f else 0.88f
    val dismissThreshold = 44f

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.38f))
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(1f - sheetHeight + 0.05f)
                .align(Alignment.TopCenter)
                .pointerInput(onDismiss) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            if (dragAmount > 0f) totalDrag += dragAmount
                        },
                        onDragEnd = {
                            if (totalDrag > dismissThreshold) onDismiss()
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f }
                    )
                }
        )

        WearPanel(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(sheetWidth)
                .fillMaxHeight(sheetHeight),
            shape = searchSheetShape(isRound),
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.985f),
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    SearchSheetHeader(
                        query = searchQuery,
                        resultCount = searchResults.size,
                        focusRequester = focusRequester,
                        onQueryChange = {
                            expandedItemId = null
                            onSearchQueryChange(it)
                        },
                        onSearch = { keyboardController?.hide() },
                        onClear = {
                            expandedItemId = null
                            onSearchQueryChange("")
                        },
                        onDismiss = onDismiss
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        ScalingLazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            contentPadding = PaddingValues(
                                top = 6.dp,
                                bottom = 86.dp,
                                start = 10.dp,
                                end = 12.dp
                            )
                        ) {
                            if (searchResults.isEmpty()) {
                                item {
                                    SearchEmptyState(
                                        message = if (searchQuery.isBlank()) {
                                            stringResource(R.string.no_entries)
                                        } else {
                                            stringResource(R.string.no_search_results)
                                        },
                                        onAddClick = { showAddDialog = true }
                                    )
                                }
                            } else {
                                items(
                                    items = searchResults,
                                    key = { it.item.id }
                                ) { itemState ->
                                    TotpResultCard(
                                        itemState = itemState,
                                        isExpanded = expandedItemId == itemState.item.id,
                                        onClick = {
                                            expandedItemId = if (expandedItemId == itemState.item.id) null else itemState.item.id
                                        },
                                        onNavigate = {
                                            keyboardController?.hide()
                                            onNavigateToItem(itemState)
                                        },
                                        onEdit = {
                                            editingItem = itemState
                                            expandedItemId = null
                                        },
                                        onDelete = {
                                            deletingItem = itemState
                                            expandedItemId = null
                                        }
                                    )
                                }
                            }
                        }

                        ScrollIndicator(
                            state = listState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = if (isRound) 6.dp else 2.dp)
                        )
                    }
                }

                BottomActionBar(
                    onAdd = { showAddDialog = true },
                    onClose = onDismiss,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
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
                Text(
                    text = stringResource(R.string.delete_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = itemName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledTonalIconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(24.dp))

                    FilledIconButton(
                        onClick = onConfirm,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.confirm),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTotpCode(code: String): String {
    return when {
        code.length == 6 -> "${code.substring(0, 3)} ${code.substring(3)}"
        code.length == 8 -> "${code.substring(0, 4)} ${code.substring(4)}"
        else -> code
    }
}

private fun searchSheetShape(isRound: Boolean): Shape {
    return if (isRound) {
        RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp, bottomStart = 40.dp, bottomEnd = 40.dp)
    } else {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    }
}

@Composable
private fun SearchSheetHeader(
    query: String,
    resultCount: Int,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val dismissThreshold = 40f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(onDismiss) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0f) totalDrag += dragAmount
                    },
                    onDragEnd = {
                        if (totalDrag > dismissThreshold) onDismiss()
                        totalDrag = 0f
                    },
                    onDragCancel = { totalDrag = 0f }
                )
            }
            .padding(top = 12.dp, start = 14.dp, end = 14.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(999.dp)
                )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.search_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        SearchInputField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClear = onClear,
            focusRequester = focusRequester,
            modifier = Modifier.fillMaxWidth(roundContentWidthFraction(0.9f, 0.94f))
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (query.isBlank()) {
                stringResource(R.string.search_hint)
            } else {
                "$resultCount ${stringResource(R.string.search_hint)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    WearPanel(
        shape = RoundedCornerShape(999.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        modifier = modifier.height(50.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
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
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isBlank()) {
                            Text(
                                text = stringResource(R.string.search_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                FilledTonalIconButton(
                    onClick = onClear,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyState(
    message: String,
    onAddClick: () -> Unit
) {
    WearPanel(
        modifier = Modifier
            .fillMaxWidth(roundContentWidthFraction(0.82f, 0.92f))
            .padding(top = 8.dp),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onAddClick) {
                Text(text = stringResource(R.string.add_totp))
            }
        }
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
    val displayName = issuer.ifBlank { account }.ifBlank { "Unknown" }
    val subtitle = if (issuer.isNotBlank() && account.isNotBlank()) account else null
    val formattedCode = formatTotpCode(itemState.code)

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth(roundContentWidthFraction(0.86f, 0.94f))
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isExpanded) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = formattedCode,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${itemState.remainingSeconds}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FilledTonalIconButton(
                    onClick = onClick,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isExpanded) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchActionButton(
                        icon = Icons.Default.Check,
                        contentDescription = stringResource(R.string.common_confirm),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        onClick = onNavigate
                    )
                    SearchActionButton(
                        icon = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.common_edit),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = onEdit
                    )
                    SearchActionButton(
                        icon = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    onAdd: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    RoundActionDock(
        modifier = modifier
            .fillMaxWidth(roundContentWidthFraction(0.54f, 0.64f))
            .padding(bottom = 10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onClose,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close)
                )
            }

            FilledIconButton(
                onClick = onAdd,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_totp)
                )
            }
        }
    }
}

@Composable
private fun SearchActionButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = Modifier.size(42.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
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
                FilledTonalIconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel),
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(24.dp))
                
                FilledIconButton(
                    onClick = {
                        if (showSecret && secret.isBlank()) {
                            showError = true
                        } else {
                            onConfirm(secret, issuer, account)
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.confirm),
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
    WearTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        singleLine = true,
        isError = isError,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onDone = onDone?.let { { it() } }
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .padding(vertical = 4.dp)
    )
}
