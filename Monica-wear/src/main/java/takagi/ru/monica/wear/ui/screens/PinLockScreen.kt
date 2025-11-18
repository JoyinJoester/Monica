package takagi.ru.monica.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import kotlinx.coroutines.delay

/**
 * PIN码锁定屏幕
 * 支持6位数字PIN码输入
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
    var errorMessage by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1B21))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 标题
        Text(
            text = when {
                isFirstTime && !isConfirming -> "设置PIN码"
                isFirstTime && isConfirming -> "确认PIN码"
                else -> "输入PIN码"
            },
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 提示信息
        if (isFirstTime && !isConfirming) {
            Text(
                text = "请输入6位数字",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // PIN码显示（圆点）
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val currentPin = if (isConfirming) confirmPin else pin
            repeat(6) { index ->
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = if (index < currentPin.length) Color.White else Color.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )
            }
        }
        
        // 错误信息
        if (showError) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = Color(0xFFEF4444),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            
            LaunchedEffect(showError) {
                delay(2000)
                showError = false
                errorMessage = ""
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 数字键盘
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 第一行: 1 2 3
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NumberButton("1") { onNumberClick("1", pin, confirmPin, isConfirming) { newPin, newConfirm -> 
                    pin = newPin
                    confirmPin = newConfirm
                } }
                NumberButton("2") { onNumberClick("2", pin, confirmPin, isConfirming) { newPin, newConfirm -> 
                    pin = newPin
                    confirmPin = newConfirm
                } }
                NumberButton("3") { onNumberClick("3", pin, confirmPin, isConfirming) { newPin, newConfirm -> 
                    pin = newPin
                    confirmPin = newConfirm
                } }
            }
            
            // 第二行: 4 5 6
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NumberButton("4") { onNumberClick("4", pin, confirmPin, isConfirming) { newPin, newConfirm -> 
                    pin = newPin
                    confirmPin = newConfirm
                } }
                NumberButton("5") { onNumberClick("5", pin, confirmPin, isConfirming) { newPin, newConfirm -> 
                    pin = newPin
                    confirmPin = newConfirm
                } }
                NumberButton("6") { onNumberClick("6", pin, confirmPin, isConfirming) { newPin, newConfirm -> 
                    pin = newPin
                    confirmPin = newConfirm
                } }
            }
            
            // 第三行: 7 8 9
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NumberButton("7") { onNumberClick("7", pin, confirmPin, isConfirming) { newPin, newConfirm -> 
                    pin = newPin
                    confirmPin = newConfirm
                } }
                NumberButton("8") { onNumberClick("8", pin, confirmPin, isConfirming) { newPin, newConfirm -> 
                    pin = newPin
                    confirmPin = newConfirm
                } }
                NumberButton("9") { onNumberClick("9", pin, confirmPin, isConfirming) { newPin, newConfirm -> 
                    pin = newPin
                    confirmPin = newConfirm
                } }
            }
            
            // 第四行: 删除 0 确认
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 删除按钮
                Button(
                    onClick = {
                        if (isConfirming) {
                            if (confirmPin.isNotEmpty()) {
                                confirmPin = confirmPin.dropLast(1)
                            }
                        } else {
                            if (pin.isNotEmpty()) {
                                pin = pin.dropLast(1)
                            }
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF374151)
                    )
                ) {
                    Text(
                        text = "←",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
                
                NumberButton("0") { onNumberClick("0", pin, confirmPin, isConfirming) { newPin, newConfirm -> 
                    pin = newPin
                    confirmPin = newConfirm
                } }
                
                // 确认按钮
                Button(
                    onClick = {
                        val currentPin = if (isConfirming) confirmPin else pin
                        if (currentPin.length == 6) {
                            if (isFirstTime) {
                                if (!isConfirming) {
                                    // 第一次输入，进入确认模式
                                    isConfirming = true
                                } else {
                                    // 确认模式，检查是否匹配
                                    if (pin == confirmPin) {
                                        onPinEntered(pin)
                                    } else {
                                        errorMessage = "PIN码不匹配"
                                        showError = true
                                        pin = ""
                                        confirmPin = ""
                                        isConfirming = false
                                    }
                                }
                            } else {
                                // 验证模式
                                onPinEntered(currentPin)
                            }
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if ((if (isConfirming) confirmPin else pin).length == 6) 
                            Color(0xFF60A5FA) 
                        else 
                            Color(0xFF374151)
                    ),
                    enabled = (if (isConfirming) confirmPin else pin).length == 6
                ) {
                    Text(
                        text = "✓",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 数字按钮
 */
@Composable
private fun NumberButton(
    number: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF374151)
        )
    ) {
        Text(
            text = number,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 处理数字点击
 */
private fun onNumberClick(
    number: String,
    currentPin: String,
    currentConfirmPin: String,
    isConfirming: Boolean,
    onUpdate: (String, String) -> Unit
) {
    if (isConfirming) {
        if (currentConfirmPin.length < 6) {
            onUpdate(currentPin, currentConfirmPin + number)
        }
    } else {
        if (currentPin.length < 6) {
            onUpdate(currentPin + number, currentConfirmPin)
        }
    }
}
