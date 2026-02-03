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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.graphicsLayer
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
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.UnifiedProgressBarMode
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.util.TotpGenerator
import takagi.ru.monica.utils.SettingsManager
import kotlin.math.PI
import kotlin.math.sin
import takagi.ru.monica.util.VibrationPatterns
import takagi.ru.monica.bitwarden.sync.SyncStatus

/**
 * TOTP验证码卡片
 * 显示实时生成的6位验证码和倒计时
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TotpCodeCard(
    item: SecureItem,
    onCopyCode: (String) -> Unit,
    onToggleSelect: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    onToggleFavorite: ((Long, Boolean) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onGenerateNext: ((Long) -> Unit)? = null,
    onShowQrCode: ((SecureItem) -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    sharedTickSeconds: Long? = null,
    appSettings: AppSettings? = null
) {
    val context = LocalContext.current
    
    // 使用传入的设置或默认值，避免创建多个 SettingsManager 实例
    val settings = appSettings ?: AppSettings()
    
    // 解析TOTP数据
    val totpData = try {
        Json.decodeFromString<TotpData>(item.itemData)
    } catch (e: Exception) {
        TotpData(secret = "")
    }
    
    // 共享定时器（外部传入时不再单独启动）
    val internalTickSeconds by produceState(initialValue = System.currentTimeMillis() / 1000, key1 = sharedTickSeconds) {
        if (sharedTickSeconds != null) {
            value = sharedTickSeconds
            return@produceState
        }
        while (true) {
            value = System.currentTimeMillis() / 1000
            delay(1000)
        }
    }
    val currentSeconds = sharedTickSeconds ?: internalTickSeconds
    
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
    
    // 根据当前秒数计算验证码/倒计时/进度
    val currentCode = remember(currentSeconds, totpData, settings.totpTimeOffset) {
        when (totpData.otpType) {
            OtpType.HOTP -> TotpGenerator.generateOtp(totpData)
            else -> TotpGenerator.generateOtp(
                totpData = totpData,
                timeOffset = settings.totpTimeOffset,
                currentSeconds = currentSeconds
            )
        }
    }
    
    // 下一个验证码（用于倒计时结束前5秒内复制）
    val nextCode = remember(currentSeconds, totpData, settings.totpTimeOffset) {
        when (totpData.otpType) {
            OtpType.HOTP -> currentCode // HOTP 不支持下一个
            else -> TotpGenerator.generateOtp(
                totpData = totpData,
                timeOffset = settings.totpTimeOffset,
                currentSeconds = currentSeconds + totpData.period
            )
        }
    }

    val remainingSeconds = remember(currentSeconds, totpData, settings.totpTimeOffset) {
        if (totpData.otpType == OtpType.HOTP) {
            0
        } else {
            TotpGenerator.getRemainingSeconds(
                period = totpData.period,
                timeOffset = settings.totpTimeOffset,
                currentSeconds = currentSeconds
            )
        }
    }

    val progress = remember(currentSeconds, totpData, settings.totpTimeOffset) {
        if (totpData.otpType == OtpType.HOTP) {
            0f
        } else {
            TotpGenerator.getProgress(
                period = totpData.period,
                timeOffset = settings.totpTimeOffset,
                currentSeconds = currentSeconds
            )
        }
    }

    // 倒计时<=5秒时每秒触发震动（使用改进的双击模式）
    LaunchedEffect(remainingSeconds, totpData.otpType, settings.validatorVibrationEnabled) {
        if (settings.validatorVibrationEnabled && 
            totpData.otpType != OtpType.HOTP && 
            remainingSeconds in 1..5) {
            
            android.util.Log.d("TotpCodeCard", "Triggering vibration at ${remainingSeconds}s")
            
            vibrator?.let { vib ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    // 使用双击模式震动（比单次100ms更有节奏感）
                    val effect = android.os.VibrationEffect.createWaveform(
                        VibrationPatterns.TICK,
                        -1  // 不重复
                    )
                    vib.vibrate(effect)
                } else {
                    // 旧版本使用简单震动
                    @Suppress("DEPRECATION")
                    vib.vibrate(VibrationPatterns.TICK, -1)
                }
                android.util.Log.d("TotpCodeCard", "Tick vibration executed at ${remainingSeconds}s")
            } ?: android.util.Log.w("TotpCodeCard", "Vibrator is null")
        }
    }
    
    // 判断是否复制下一个验证码
    val codeToCopy = remember(currentCode, nextCode, remainingSeconds, settings.copyNextCodeWhenExpiring, totpData.otpType) {
        if (settings.copyNextCodeWhenExpiring && 
            totpData.otpType != OtpType.HOTP && 
            remainingSeconds in 1..5) {
            nextCode
        } else {
            currentCode
        }
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelect?.invoke()
                    } else {
                        onCopyCode(codeToCopy)
                    }
                },
                onLongClick = {
                    onLongClick?.invoke()
                }
            ),
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
                    // Bitwarden 同步状态指示器
                    if (item.bitwardenVaultId != null) {
                        val syncStatus = when (item.syncStatus) {
                            "PENDING" -> SyncStatus.PENDING
                            "SYNCING" -> SyncStatus.SYNCING
                            "SYNCED" -> SyncStatus.SYNCED
                            "FAILED" -> SyncStatus.FAILED
                            "CONFLICT" -> SyncStatus.CONFLICT
                            else -> if (item.bitwardenLocalModified) SyncStatus.PENDING else SyncStatus.SYNCED
                        }
                        SyncStatusIcon(
                            status = syncStatus,
                            size = 16.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    
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

                                // 编辑
                                if (onEdit != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.edit)) },
                                        onClick = {
                                            expanded = false
                                            onEdit()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Edit,
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
                // 闪烁动画（倒计时<=5秒时启用，参考Aegis实现）
                val blinkAlpha by if (remainingSeconds <= 5 && totpData.otpType != OtpType.HOTP) {
                    val blinkTransition = rememberInfiniteTransition(label = "blink")
                    blinkTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 500, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "blink_alpha"
                    )
                } else {
                    remember { mutableFloatStateOf(1f) }
                }
                
                // 验证码（等宽字体）
                // 统一进度条模式下放大验证码
                val isStandardPeriod = totpData.period == 30 || totpData.period == 60
                val useUnifiedProgressBar = settings.validatorUnifiedProgressBar == UnifiedProgressBarMode.ENABLED
                val isUnifiedMode = useUnifiedProgressBar && isStandardPeriod && totpData.otpType != OtpType.HOTP
                
                val codeFontSize = when {
                    totpData.otpType == OtpType.STEAM -> if (isUnifiedMode) 34.sp else 28.sp
                    isUnifiedMode -> 40.sp
                    else -> 32.sp
                }
                
                Text(
                    text = formatOtpCode(currentCode, totpData.otpType),
                    fontSize = codeFontSize,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = when {
                        totpData.otpType == OtpType.STEAM -> Color(0xFF66BB6A)
                        remainingSeconds <= 5 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.graphicsLayer { alpha = blinkAlpha }
                )
                
                // 下一次验证码预览
                if (totpData.otpType != OtpType.HOTP) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Next",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val nextCode = remember(currentSeconds, totpData, settings.totpTimeOffset) {
                            TotpGenerator.generateOtp(
                                totpData = totpData,
                                timeOffset = settings.totpTimeOffset,
                                currentSeconds = currentSeconds + totpData.period
                            )
                        }
                        Text(
                            text = formatOtpCode(nextCode, totpData.otpType),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    IconButton(
                        onClick = { onCopyCode(codeToCopy) }
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "复制验证码"
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 进度条/计数器显示
            // 判断是否需要隐藏进度条（启用统一进度条模式且是标准周期30s/60s）
            val isStandardPeriod = totpData.period == 30 || totpData.period == 60
            val useUnifiedProgressBar = settings.validatorUnifiedProgressBar == UnifiedProgressBarMode.ENABLED
            val shouldHideProgress = useUnifiedProgressBar && isStandardPeriod && totpData.otpType != OtpType.HOTP
            
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
                    if (!shouldHideProgress) {
                        // TOTP/Steam/Yandex/mOTP显示倒计时和进度条（仅在非统一进度条模式或自定义周期时显示）
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
                    } else {
                        // 统一进度条模式下，不显示任何内容（倒计时已在顶部统一显示）
                        // 不需要显示任何UI
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
