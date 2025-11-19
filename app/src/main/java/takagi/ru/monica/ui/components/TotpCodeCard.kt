package takagi.ru.monica.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import takagi.ru.monica.R
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.util.TotpGenerator
import kotlin.math.PI
import kotlin.math.sin

/**
 * TOTP验证码卡片
 * 显示实时生成的6位验证码和倒计时
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotpCodeCard(
    item: SecureItem,
    onClick: () -> Unit,
    onCopyCode: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    onToggleFavorite: ((Long, Boolean) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onGenerateNext: ((Long) -> Unit)? = null,
    onShowQrCode: ((SecureItem) -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false
) {
    val context = LocalContext.current
    
    // 获取设置以读取进度条样式
    val settingsManager = remember { takagi.ru.monica.utils.SettingsManager(context) }
    val settings by settingsManager.settingsFlow.collectAsState(initial = takagi.ru.monica.data.AppSettings())
    
    // 解析TOTP数据
    val totpData = try {
        Json.decodeFromString<TotpData>(item.itemData)
    } catch (e: Exception) {
        TotpData(secret = "")
    }
    
    // 实时更新验证码
    var currentCode by remember { mutableStateOf("") }
    var remainingSeconds by remember { mutableIntStateOf(30) }
    var progress by remember { mutableFloatStateOf(0f) }
    
    // 震动服务
    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }
    
    // 根据OTP类型更新验证码
    LaunchedEffect(item.itemData, totpData.otpType) {
        when (totpData.otpType) {
            OtpType.HOTP -> {
                // HOTP不需要自动刷新，只生成一次
                currentCode = TotpGenerator.generateOtp(totpData)
            }
            else -> {
                // TOTP/Steam/Yandex/mOTP 需要每秒更新
                while (true) {
                    currentCode = TotpGenerator.generateOtp(totpData)
                    val newRemainingSeconds = TotpGenerator.getRemainingSeconds(totpData.period)
                    remainingSeconds = newRemainingSeconds
                    progress = TotpGenerator.getProgress(totpData.period)
                    
                    // 倒计时<=5秒时每秒震动一次
                    if (settings.validatorVibrationEnabled && newRemainingSeconds <= 5 && newRemainingSeconds > 0) {
                        android.util.Log.d("TotpCodeCard", "Vibrating at ${newRemainingSeconds}s, enabled=${settings.validatorVibrationEnabled}")
                        vibrator?.let {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                it.vibrate(
                                    android.os.VibrationEffect.createOneShot(
                                        100,
                                        android.os.VibrationEffect.DEFAULT_AMPLITUDE
                                    )
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                it.vibrate(100)
                            }
                            android.util.Log.d("TotpCodeCard", "Vibration executed")
                        } ?: android.util.Log.w("TotpCodeCard", "Vibrator is null")
                    }
                    
                    delay(1000)
                }
            }
        }
    }
    
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 标题和菜单
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 添加复选框（选择模式）
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (totpData.issuer.isNotBlank()) {
                        Text(
                            text = totpData.issuer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (totpData.accountName.isNotBlank()) {
                        Text(
                            text = totpData.accountName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isFavorite) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "收藏",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    
                    // 菜单按钮
                    if (onDelete != null) {
                        var expanded by remember { mutableStateOf(false) }
                        
                        Box {
                            IconButton(onClick = { expanded = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "更多"
                                )
                            }
                            
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                // 收藏选项
                                if (onToggleFavorite != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(if (item.isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites)) },
                                        onClick = {
                                            expanded = false
                                            onToggleFavorite(item.id, !item.isFavorite)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (item.isFavorite) Icons.Default.FavoriteBorder else Icons.Default.Favorite,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                }
                                
                                // 上移选项
                                if (onMoveUp != null) {
                                    DropdownMenuItem(
                                        text = { Text("上移") },
                                        onClick = {
                                            expanded = false
                                            onMoveUp()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.KeyboardArrowUp,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                                
                                // 下移选项
                                if (onMoveDown != null) {
                                    DropdownMenuItem(
                                        text = { Text("下移") },
                                        onClick = {
                                            expanded = false
                                            onMoveDown()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.KeyboardArrowDown,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }

                                // 显示二维码选项
                                if (onShowQrCode != null) {
                                    DropdownMenuItem(
                                        text = { Text("显示二维码") },
                                        onClick = {
                                            expanded = false
                                            onShowQrCode(item)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.QrCode,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                                
                                DropdownMenuItem(
                                    text = { Text("删除") },
                                    onClick = {
                                        expanded = false
                                        onDelete()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 验证码显示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 验证码（等宽字体）
                Text(
                    text = formatOtpCode(currentCode, totpData.otpType),
                    fontSize = if (totpData.otpType == OtpType.STEAM) 28.sp else 32.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        totpData.otpType == OtpType.STEAM -> Color(0xFF66BB6A)
                        remainingSeconds <= 5 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                
                // 复制按钮
                IconButton(
                    onClick = { onCopyCode(currentCode) }
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制验证码"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 进度条/计数器显示
            when (totpData.otpType) {
                OtpType.HOTP -> {
                    // HOTP显示计数器和生成按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.counter_value, totpData.counter),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (onGenerateNext != null) {
                            OutlinedButton(
                                onClick = { onGenerateNext(item.id) },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.generate_next))
                            }
                        }
                    }
                }
                else -> {
                    // TOTP/Steam/Yandex/mOTP显示倒计时和进度条
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val progressColor = if (remainingSeconds <= 5) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                        
                        M3EProgressIndicator(
                            progress = progress,
                            color = progressColor,
                            showWaveAccent = false,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "${remainingSeconds}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (remainingSeconds <= 5) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun M3EProgressIndicator(
    progress: Float,
    color: Color,
    showWaveAccent: Boolean,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 6.dp
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "m3e_progress"
    )

    val waveTransition = rememberInfiniteTransition(label = "m3e_wave")
    val waveOffset by waveTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "m3e_wave_offset"
    )

    val trackShape = RoundedCornerShape(percent = 50)
    val fillFraction = animatedProgress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(trackHeight)
    ) {
        // 背景轨道
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(trackShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        )

        if (showWaveAccent) {
            // 波浪形进度
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = fillFraction)
                    .clip(trackShape)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    
                    val centerY = height / 2f
                    val amplitude = height * 0.4f
                    val wavelength = width * 0.3f
                    
                    val wavePath = Path().apply {
                        moveTo(0f, centerY)
                        var x = 0f
                        while (x <= width) {
                            val phase = ((x / wavelength) * 2f * PI.toFloat()) + waveOffset
                            val y = centerY + amplitude * sin(phase)
                            lineTo(x, y)
                            x += 2f
                        }
                        lineTo(width, height)
                        lineTo(0f, height)
                        close()
                    }
                    
                    drawPath(
                        path = wavePath,
                        color = color
                    )
                }
            }
        } else {
            // 线形进度
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = fillFraction)
                    .clip(trackShape)
                    .background(color)
            )
        }
    }
}

/**
 * 格式化OTP验证码（根据类型添加空格分隔）
 * 例如: 
 * - TOTP 6位: 123456 -> 123 456
 * - TOTP 8位: 12345678 -> 1234 5678
 * - Steam 5位: 2BC4X -> 2B C4X
 */
private fun formatOtpCode(code: String, otpType: OtpType): String {
    return when (otpType) {
        OtpType.STEAM -> {
            // Steam使用5位字符，格式为 2B C4X
            if (code.length == 5) {
                "${code.substring(0, 2)} ${code.substring(2)}"
            } else {
                code
            }
        }
        else -> {
            // 数字验证码
            when (code.length) {
                6 -> "${code.substring(0, 3)} ${code.substring(3)}"
                8 -> "${code.substring(0, 4)} ${code.substring(4)}"
                else -> code
            }
        }
    }
}
