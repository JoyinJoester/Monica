package takagi.ru.monica.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import kotlin.math.roundToInt

/**
 * 底部导航项数据类（用于可拖拽导航栏）
 */
data class DraggableNavItem(
    val key: String,
    val icon: ImageVector,
    val labelRes: Int,
    val selected: Boolean,
    val onClick: () -> Unit
)

/**
 * 快速添加回调
 */
data class QuickAddCallback(
    val onAddPassword: (title: String, username: String, password: String) -> Unit,
    val onAddTotp: (name: String, secret: String) -> Unit,
    val onAddBankCard: (name: String, number: String) -> Unit,
    val onAddNote: (title: String, content: String) -> Unit
)

/**
 * 可拖拽底部导航栏组件
 * 使用自定义手势实现流畅的拖拽体验
 * 展开后显示快速添加表单，当前选中的图标会平滑移动到左上角
 */
@Composable
fun DraggableBottomNavScaffold(
    navItems: List<DraggableNavItem>,
    quickAddCallback: QuickAddCallback,
    modifier: Modifier = Modifier,
    peekHeight: Dp = 80.dp,
    expandedHeight: Dp = 320.dp,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    
    // 系统边距
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarExtraPadding = 6.dp
    
    // 实际高度（包含系统导航栏）
    val actualPeekHeight = peekHeight + navBarPadding + navBarExtraPadding
    val actualExpandedHeight = expandedHeight + navBarPadding + navBarExtraPadding
    
    // 拖拽状态
    val maxOffset = with(density) { (actualExpandedHeight - actualPeekHeight).toPx() }
    
    // 当前偏移值 (0 = 展开, maxOffset = 折叠)
    var offsetValue by remember { mutableFloatStateOf(maxOffset) }
    
    // 是否正在拖拽
    var isDragging by remember { mutableStateOf(false) }
    
    // 展开进度 (0 = 折叠, 1 = 展开)
    val expansionProgress = if (maxOffset == 0f) 1f else (1f - (offsetValue / maxOffset)).coerceIn(0f, 1f)
    
    // 圆角动画
    val cornerRadius by animateDpAsState(
        targetValue = if (expansionProgress > 0.05f) 28.dp else 0.dp,
        animationSpec = tween(150),
        label = "corner_radius"
    )
    
    // 记录每个导航项图标的位置
    val iconPositions = remember { mutableStateMapOf<String, Float>() }
    
    // 当前选中的导航项
    val selectedItem = navItems.find { it.selected }
    
    // 快速添加表单状态
    var quickAddTitle by remember { mutableStateOf("") }
    var quickAddField1 by remember { mutableStateOf("") }
    var quickAddField2 by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    
    // 展开时重置表单
    LaunchedEffect(expansionProgress) {
        if (expansionProgress < 0.1f) {
            quickAddTitle = ""
            quickAddField1 = ""
            quickAddField2 = ""
            showPassword = false
        }
    }
    
    // 平滑动画到目标位置
    fun animateTo(targetOffset: Float) {
        scope.launch {
            val startValue = offsetValue
            val distance = targetOffset - startValue
            val duration = 200L
            val startTime = System.currentTimeMillis()
            
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= duration) {
                    offsetValue = targetOffset
                    break
                }
                val progress = elapsed.toFloat() / duration
                // 缓动函数: ease-out
                val eased = 1f - (1f - progress) * (1f - progress)
                offsetValue = startValue + distance * eased
                delay(8)
            }
        }
    }
    
    // 执行快速添加
    fun performQuickAdd() {
        if (quickAddTitle.isBlank()) return
        
        when (selectedItem?.key) {
            "passwords" -> {
                quickAddCallback.onAddPassword(quickAddTitle, quickAddField1, quickAddField2)
            }
            "authenticator" -> {
                quickAddCallback.onAddTotp(quickAddTitle, quickAddField1)
            }
            "card_wallet" -> {
                quickAddCallback.onAddBankCard(quickAddTitle, quickAddField1)
            }
            "notes" -> {
                quickAddCallback.onAddNote(quickAddTitle, quickAddField1)
            }
        }
        
        // 重置表单并收起
        quickAddTitle = ""
        quickAddField1 = ""
        quickAddField2 = ""
        animateTo(maxOffset)
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // 主内容区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = statusBarPadding,
                    bottom = actualPeekHeight
                )
        ) {
            content(PaddingValues(0.dp))
        }
        
        // FAB (展开时隐藏)
        if (expansionProgress < 0.5f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = actualPeekHeight + 16.dp)
                    .graphicsLayer { alpha = 1f - expansionProgress * 2f }
            ) {
                floatingActionButton()
            }
        }
        
        // 底部抽屉
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .offset { IntOffset(0, offsetValue.roundToInt()) }
                .height(actualExpandedHeight)
                .pointerInput(maxOffset) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            isDragging = true
                        },
                        onDragEnd = {
                            isDragging = false
                            val threshold = maxOffset * 0.4f
                            val targetOffset = if (offsetValue < threshold) 0f else maxOffset
                            animateTo(targetOffset)
                        },
                        onDragCancel = {
                            isDragging = false
                            val targetOffset = if (offsetValue < maxOffset / 2f) 0f else maxOffset
                            animateTo(targetOffset)
                        },
                        onVerticalDrag = { _, dragAmount ->
                            offsetValue = (offsetValue + dragAmount).coerceIn(0f, maxOffset)
                        }
                    )
                },
            shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 拖拽手柄
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            val targetOffset = if (offsetValue < maxOffset / 2f) maxOffset else 0f
                            animateTo(targetOffset)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }
                
                // 导航栏图标层 - 处理图标的位置动画
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .height(56.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navItems.forEach { item ->
                        val isSelected = item.selected
                        
                        // 计算图标的目标位置
                        // 选中的图标: 从当前位置移动到左上角
                        // 未选中的图标: 淡出
                        
                        val targetX = if (isSelected) {
                            // 移动到左边 (16dp padding)
                            val currentX = iconPositions[item.key] ?: 0f
                            val targetXPx = with(density) { 24.dp.toPx() }
                            // 插值: 从当前位置到目标位置
                            currentX + (targetXPx - currentX) * expansionProgress
                        } else {
                            iconPositions[item.key] ?: 0f
                        }
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .onGloballyPositioned { coordinates ->
                                    if (expansionProgress < 0.1f) {
                                        iconPositions[item.key] = coordinates.positionInParent().x
                                    }
                                }
                                .graphicsLayer {
                                    // 选中项: 保持可见，移动到左边
                                    // 非选中项: 淡出
                                    alpha = if (isSelected) 1f else (1f - expansionProgress)
                                    
                                    // 选中项的位移
                                    if (isSelected && expansionProgress > 0f) {
                                        val currentX = iconPositions[item.key] ?: 0f
                                        val targetXPx = with(density) { 24.dp.toPx() }
                                        translationX = (targetXPx - currentX) * expansionProgress
                                        // 稍微放大
                                        scaleX = 1f + 0.2f * expansionProgress
                                        scaleY = 1f + 0.2f * expansionProgress
                                    }
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = expansionProgress < 0.3f
                                ) {
                                    item.onClick()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = stringResource(item.labelRes),
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                
                                // 标签在折叠时显示，展开时隐藏
                                if (expansionProgress < 0.5f) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(item.labelRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.graphicsLayer {
                                            alpha = 1f - expansionProgress * 2f
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 快速添加表单 (展开时显示)
                if (expansionProgress > 0.1f) {
                    QuickAddForm(
                        selectedItem = selectedItem,
                        expansionProgress = expansionProgress,
                        title = quickAddTitle,
                        onTitleChange = { quickAddTitle = it },
                        field1 = quickAddField1,
                        onField1Change = { quickAddField1 = it },
                        field2 = quickAddField2,
                        onField2Change = { quickAddField2 = it },
                        showPassword = showPassword,
                        onTogglePassword = { showPassword = !showPassword },
                        onAdd = { performQuickAdd() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp)
                            .padding(horizontal = 16.dp)
                            .graphicsLayer { alpha = expansionProgress }
                    )
                }
                
                // 系统导航栏空间
                Spacer(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(navBarPadding + navBarExtraPadding)
                )
            }
        }
    }
}

/**
 * 快速添加表单
 */
@Composable
private fun QuickAddForm(
    selectedItem: DraggableNavItem?,
    expansionProgress: Float,
    title: String,
    onTitleChange: (String) -> Unit,
    field1: String,
    onField1Change: (String) -> Unit,
    field2: String,
    onField2Change: (String) -> Unit,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    
    // 展开完成后自动聚焦
    LaunchedEffect(expansionProgress) {
        if (expansionProgress > 0.9f) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 根据当前页面显示不同的表单
        when (selectedItem?.key) {
            "passwords" -> {
                // 密码页面: 标题、账号、密码
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text(stringResource(R.string.title)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                
                OutlinedTextField(
                    value = field1,
                    onValueChange = onField1Change,
                    label = { Text(stringResource(R.string.username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                
                OutlinedTextField(
                    value = field2,
                    onValueChange = onField2Change,
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onAdd() }),
                    trailingIcon = {
                        IconButton(onClick = onTogglePassword) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
            
            "authenticator" -> {
                // 验证器页面: 名称、密钥
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                
                OutlinedTextField(
                    value = field1,
                    onValueChange = onField1Change,
                    label = { Text(stringResource(R.string.totp_secret)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAdd() })
                )
            }
            
            "card_wallet" -> {
                // 卡包页面: 名称、卡号
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text(stringResource(R.string.card_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                
                OutlinedTextField(
                    value = field1,
                    onValueChange = onField1Change,
                    label = { Text(stringResource(R.string.card_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onAdd() })
                )
            }
            
            "notes" -> {
                // 笔记页面: 标题、内容
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text(stringResource(R.string.title)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                
                OutlinedTextField(
                    value = field1,
                    onValueChange = onField1Change,
                    label = { Text(stringResource(R.string.content)) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAdd() })
                )
            }
            
            else -> {
                // 其他页面: 显示提示
                Text(
                    text = stringResource(R.string.quick_add_not_supported),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // 添加按钮
        if (selectedItem?.key in listOf("passwords", "authenticator", "card_wallet", "notes")) {
            FilledTonalButton(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add))
            }
        }
    }
}

// 保留旧的数据类以兼容现有代码
data class QuickActionItem(
    val icon: ImageVector,
    val labelRes: Int,
    val onClick: () -> Unit,
    val tint: Color? = null
)

// 兼容旧版本的函数签名
@Composable
fun DraggableBottomNavScaffold(
    navItems: List<DraggableNavItem>,
    quickActions: List<QuickActionItem>,
    modifier: Modifier = Modifier,
    peekHeight: Dp = 80.dp,
    expandedHeight: Dp = 380.dp,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    // 使用默认的空回调
    DraggableBottomNavScaffold(
        navItems = navItems,
        quickAddCallback = QuickAddCallback(
            onAddPassword = { _, _, _ -> },
            onAddTotp = { _, _ -> },
            onAddBankCard = { _, _ -> },
            onAddNote = { _, _ -> }
        ),
        modifier = modifier,
        peekHeight = peekHeight,
        expandedHeight = expandedHeight,
        floatingActionButton = floatingActionButton,
        content = content
    )
}
