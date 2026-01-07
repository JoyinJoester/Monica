package takagi.ru.monica.wear.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * PIN码锁定屏幕 - Wear OS 拨号器风格
 * 简洁、无按钮背景、自动适配屏幕尺寸
 */
@Composable
fun PinLockScreen(
    isFirstTime: Boolean,
    onPinEntered: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    
    val haptics = LocalHapticFeedback.current
    val currentPin = if (isConfirming) confirmPin else pin
    
    // 标题
    val titleText = when {
        showError -> "PIN不匹配"
        isFirstTime && !isConfirming -> "设置PIN码"
        isFirstTime && isConfirming -> "确认PIN码"
        else -> "输入PIN码"
    }
    
    // 错误抖动动画
    val shakeAnimation = remember { Animatable(0f) }
    LaunchedEffect(showError) {
        if (showError) {
            repeat(3) {
                shakeAnimation.animateTo(8f, tween(40))
                shakeAnimation.animateTo(-8f, tween(40))
            }
            shakeAnimation.animateTo(0f, tween(40))
            delay(800)
            showError = false
        }
    }

    // 使用 BoxWithConstraints 获取屏幕尺寸并自动适配
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // 基于屏幕宽度计算所有尺寸
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        
        // 自适应尺寸计算
        val numberSize = (screenWidth * 0.13f)  // 数字大小约为屏幕宽度的13%
        val iconSize = screenWidth * 0.08f       // 图标大小
        val dotSize = screenWidth * 0.025f       // PIN 圆点大小
        val rowSpacing = screenHeight * 0.01f    // 行间距
        val columnSpacing = screenWidth * 0.08f  // 列间距
        val titleSize = (screenWidth.value * 0.07f)  // 标题字体
        
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 顶部区域：标题 + PIN 圆点
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { translationX = shakeAnimation.value }
            ) {
                // 标题
                Text(
                    text = titleText,
                    color = if (showError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontSize = titleSize.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(rowSpacing))
                
                // PIN 圆点指示器
                DialerPinDots(
                    currentLength = currentPin.length,
                    maxLength = 6,
                    dotSize = dotSize
                )
            }
            
            Spacer(modifier = Modifier.height(rowSpacing * 2))
            
            // 数字键盘 - 拨号器风格
            DialerKeypad(
                numberSize = numberSize,
                iconSize = iconSize,
                rowSpacing = rowSpacing,
                columnSpacing = columnSpacing,
                isComplete = currentPin.length == 6,
                onDigitClick = { digit ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isConfirming) {
                        if (confirmPin.length < 6) confirmPin += digit
                    } else {
                        if (pin.length < 6) pin += digit
                    }
                },
                onDeleteClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isConfirming) {
                        if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                    } else {
                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                    }
                },
                onConfirmClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (currentPin.length == 6) {
                        if (isFirstTime) {
                            if (!isConfirming) {
                                isConfirming = true
                            } else {
                                if (pin == confirmPin) {
                                    onPinEntered(pin)
                                } else {
                                    showError = true
                                    pin = ""
                                    confirmPin = ""
                                    isConfirming = false
                                }
                            }
                        } else {
                            onPinEntered(currentPin)
                        }
                    }
                }
            )
        }
    }
}

/**
 * 拨号器风格 PIN 圆点
 */
@Composable
private fun DialerPinDots(
    currentLength: Int,
    maxLength: Int,
    dotSize: androidx.compose.ui.unit.Dp
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(dotSize * 0.8f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(maxLength) { index ->
            val isFilled = index < currentLength
            
            val scale by animateFloatAsState(
                targetValue = if (isFilled) 1.3f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                ),
                label = "dotScale"
            )
            
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .scale(scale)
                    .background(
                        color = if (isFilled) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * 拨号器风格数字键盘 - 无背景，纯文本
 */
@Composable
private fun DialerKeypad(
    numberSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    rowSpacing: androidx.compose.ui.unit.Dp,
    columnSpacing: androidx.compose.ui.unit.Dp,
    isComplete: Boolean,
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onConfirmClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rowSpacing)
    ) {
        // 1 2 3
        Row(horizontalArrangement = Arrangement.spacedBy(columnSpacing)) {
            DialerDigitKey("1", numberSize, onDigitClick)
            DialerDigitKey("2", numberSize, onDigitClick)
            DialerDigitKey("3", numberSize, onDigitClick)
        }
        // 4 5 6
        Row(horizontalArrangement = Arrangement.spacedBy(columnSpacing)) {
            DialerDigitKey("4", numberSize, onDigitClick)
            DialerDigitKey("5", numberSize, onDigitClick)
            DialerDigitKey("6", numberSize, onDigitClick)
        }
        // 7 8 9
        Row(horizontalArrangement = Arrangement.spacedBy(columnSpacing)) {
            DialerDigitKey("7", numberSize, onDigitClick)
            DialerDigitKey("8", numberSize, onDigitClick)
            DialerDigitKey("9", numberSize, onDigitClick)
        }
        // ⌫ 0 ✓
        Row(horizontalArrangement = Arrangement.spacedBy(columnSpacing)) {
            // 删除键
            DialerIconKey(
                icon = Icons.Default.Backspace,
                size = iconSize,
                contentDescription = "删除",
                onClick = onDeleteClick
            )
            // 0
            DialerDigitKey("0", numberSize, onDigitClick)
            // 确认键
            DialerIconKey(
                icon = Icons.Default.Check,
                size = iconSize,
                contentDescription = "确认",
                tint = if (isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                enabled = isComplete,
                onClick = onConfirmClick
            )
        }
    }
}

/**
 * 拨号器风格数字键 - 纯文本，无背景
 */
@Composable
private fun DialerDigitKey(
    digit: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: (String) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.8f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "digitScale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.6f else 1f,
        animationSpec = tween(100),
        label = "digitAlpha"
    )
    
    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .alpha(alpha)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(digit) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = (size.value * 0.6f).sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 拨号器风格图标键
 */
@Composable
private fun DialerIconKey(
    icon: ImageVector,
    size: androidx.compose.ui.unit.Dp,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.8f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "iconScale"
    )
    
    // 图标容器大小与数字键相同，但图标本身较小
    val containerSize = size * 1.6f  // 与数字键容器大小一致
    
    Box(
        modifier = Modifier
            .size(containerSize)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}
