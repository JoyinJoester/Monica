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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.ProgressBarStyle
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.UnifiedProgressBarMode
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.util.TotpGenerator
import kotlin.math.PI
import kotlin.math.sin
import takagi.ru.monica.util.VibrationPatterns
import takagi.ru.monica.bitwarden.sync.SyncStatus
import takagi.ru.monica.ui.icons.UnmatchedIconFallback
import takagi.ru.monica.ui.icons.shouldShowFallbackSlot

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
    allowVibration: Boolean = false,
    boundPasswordSummary: String? = null,
    sharedTickSeconds: Long? = null,
    appSettings: AppSettings? = null
) {
    val context = LocalContext.current
    val screenLifecycleOwner = LocalLifecycleOwner.current
    
    // 使用传入的设置或默认值，避免创建多个 SettingsManager 实例
    val settings = appSettings ?: AppSettings()
    
    // 解析TOTP数据
    val parsedTotpData = try {
        Json.decodeFromString<TotpData>(item.itemData)
    } catch (e: Exception) {
        TotpData(secret = "")
    }
    val totpData = remember(parsedTotpData) { normalizeTotpData(parsedTotpData) }
    
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
    val progressTimeMillis by produceState(initialValue = System.currentTimeMillis(), key1 = settings.validatorSmoothProgress) {
        while (true) {
            val now = System.currentTimeMillis()
            value = now
            val waitMillis = if (settings.validatorSmoothProgress) 50L else (1000L - (now % 1000L)).coerceAtLeast(16L)
            delay(waitMillis)
        }
    }

    var isScreenStarted by remember {
        mutableStateOf(screenLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var isScreenResumed by remember {
        mutableStateOf(screenLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(screenLifecycleOwner) {
        val lifecycle = screenLifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, _ ->
            isScreenStarted = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            isScreenResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    
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

    val progress = remember(
        currentSeconds,
        progressTimeMillis,
        totpData,
        settings.totpTimeOffset,
        settings.validatorSmoothProgress
    ) {
        if (totpData.otpType == OtpType.HOTP) {
            0f
        } else {
            if (settings.validatorSmoothProgress) {
                val periodMillis = (totpData.period * 1000L).coerceAtLeast(1000L)
                val correctedMillis = progressTimeMillis + (settings.totpTimeOffset * 1000L)
                val elapsedInPeriod = ((correctedMillis % periodMillis) + periodMillis) % periodMillis
                (elapsedInPeriod.toFloat() / periodMillis.toFloat()).coerceIn(0f, 1f)
            } else {
                TotpGenerator.getProgress(
                    period = totpData.period,
                    timeOffset = settings.totpTimeOffset,
                    currentSeconds = currentSeconds
                ).coerceIn(0f, 1f)
            }
        }
    }

    // 倒计时<=5秒时每秒触发震动（使用改进的双击模式）
    LaunchedEffect(remainingSeconds, totpData.otpType, settings.validatorVibrationEnabled, allowVibration, isScreenStarted, isScreenResumed) {
        if (allowVibration &&
            isScreenStarted &&
            isScreenResumed &&
            settings.validatorVibrationEnabled && 
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

    val iconWebsite = remember(totpData.link) { totpData.link.trim() }
    val iconTitle = remember(item.title, totpData.issuer) { totpData.issuer.ifBlank { item.title } }
    val associatedAppPackage = remember(totpData.associatedApp) { totpData.associatedApp.trim() }
    val selectedSimpleIconBitmap = takagi.ru.monica.ui.icons.rememberSimpleIconBitmap(
        slug = if (totpData.customIconType == takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_SIMPLE) {
            totpData.customIconValue
        } else {
            null
        },
        tintColor = MaterialTheme.colorScheme.primary,
        enabled = settings.iconCardsEnabled
    )
    val selectedUploadedIconBitmap = takagi.ru.monica.ui.icons.rememberUploadedPasswordIcon(
        value = if (totpData.customIconType == takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_UPLOADED) {
            totpData.customIconValue
        } else {
            null
        }
    )
    val autoMatchedSimpleIcon = takagi.ru.monica.ui.icons.rememberAutoMatchedSimpleIcon(
        website = iconWebsite,
        title = iconTitle,
        appPackageName = associatedAppPackage.ifBlank { null },
        tintColor = MaterialTheme.colorScheme.primary,
        enabled = settings.iconCardsEnabled &&
            totpData.customIconType == takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_NONE
    )
    val favicon = if (iconWebsite.isNotBlank()) {
        takagi.ru.monica.autofill.ui.rememberFavicon(
            url = iconWebsite,
            enabled = settings.iconCardsEnabled && autoMatchedSimpleIcon.resolved && autoMatchedSimpleIcon.slug == null
        )
    } else {
        null
    }
    val appIcon = if (settings.iconCardsEnabled && associatedAppPackage.isNotBlank()) {
        takagi.ru.monica.autofill.ui.rememberAppIcon(packageName = associatedAppPackage)
    } else {
        null
    }
    val infoLines = remember(
        totpData.issuer,
        totpData.accountName,
        settings.authenticatorCardDisplayFields
    ) {
        resolveAuthenticatorInfoLines(
            issuer = totpData.issuer,
            accountName = totpData.accountName,
            fields = settings.authenticatorCardDisplayFields
        )
    }
    
    val cardInteractionModifier = if (isSelectionMode) {
        // In selection mode, keep long-press free for list-level drag reorder.
        modifier
            .fillMaxWidth()
            .clickable { onToggleSelect?.invoke() }
    } else {
        modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onCopyCode(codeToCopy) },
                onLongClick = { onLongClick?.invoke() }
            )
    }

    Card(
        modifier = cardInteractionModifier,
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (settings.iconCardsEnabled) {
                        when {
                            selectedSimpleIconBitmap != null -> {
                                Image(
                                    bitmap = selectedSimpleIconBitmap,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            selectedUploadedIconBitmap != null -> {
                                Image(
                                    bitmap = selectedUploadedIconBitmap,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            autoMatchedSimpleIcon.bitmap != null -> {
                                Image(
                                    bitmap = autoMatchedSimpleIcon.bitmap,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            favicon != null -> {
                                Image(
                                    bitmap = favicon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                )
                            }
                            appIcon != null -> {
                                Image(
                                    bitmap = appIcon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                )
                            }
                            else -> {
                                if (shouldShowFallbackSlot(settings.unmatchedIconHandlingStrategy)) {
                                    UnmatchedIconFallback(
                                        strategy = settings.unmatchedIconHandlingStrategy,
                                        primaryText = iconWebsite,
                                        secondaryText = iconTitle,
                                        defaultIcon = Icons.Default.Security,
                                        iconSize = 40.dp
                                    )
                                }
                            }
                        }
                        if (autoMatchedSimpleIcon.bitmap != null ||
                            favicon != null ||
                            appIcon != null ||
                            shouldShowFallbackSlot(settings.unmatchedIconHandlingStrategy)
                        ) {
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        infoLines.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                    
                    if (!isSelectionMode && item.isFavorite) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = stringResource(R.string.favorite),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect?.invoke() }
                        )
                    } else if (onDelete != null) {
                        // 菜单按钮
                        var expanded by remember { mutableStateOf(false) }
                        
                        Box {
                            IconButton(onClick = { expanded = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more_options)
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
                                        text = { Text(stringResource(R.string.move_up)) },
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
                                        text = { Text(stringResource(R.string.move_down)) },
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
                                        text = { Text(stringResource(R.string.show_qr_code)) },
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
                                    text = { Text(stringResource(R.string.delete)) },
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
                val shouldBlink = remainingSeconds <= 5 && totpData.otpType != OtpType.HOTP
                val blinkAlpha = if (shouldBlink) {
                    if (((progressTimeMillis / 500L) % 2L) == 0L) 1f else 0.5f
                } else {
                    1f
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
                        totpData.otpType == OtpType.STEAM -> MaterialTheme.colorScheme.tertiary
                        remainingSeconds <= 5 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }.copy(alpha = blinkAlpha)
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
                            contentDescription = stringResource(R.string.copy_verification_code)
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
                                style = settings.validatorProgressBarStyle,
                                smoothProgress = settings.validatorSmoothProgress,
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

private fun resolveAuthenticatorInfoLines(
    issuer: String,
    accountName: String,
    fields: List<takagi.ru.monica.data.AuthenticatorCardDisplayField>
): List<String> {
    val distinctFields = fields.distinct()
    return buildList {
        distinctFields.forEach { field ->
            when (field) {
                takagi.ru.monica.data.AuthenticatorCardDisplayField.ISSUER -> {
                    val value = issuer.trim()
                    if (value.isNotBlank()) add(value)
                }
                takagi.ru.monica.data.AuthenticatorCardDisplayField.ACCOUNT_NAME -> {
                    val value = accountName.trim()
                    if (value.isNotBlank()) add(value)
                }
            }
        }
    }
}

@Composable
private fun M3EProgressIndicator(
    progress: Float,
    color: Color,
    style: ProgressBarStyle,
    smoothProgress: Boolean,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 12.dp
) {
    val safeProgress = if (progress.isFinite()) progress else 0f
    val clampedProgress = safeProgress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = tween(
            durationMillis = if (smoothProgress) 50 else 280,
            easing = if (smoothProgress) LinearEasing else FastOutSlowInEasing
        ),
        label = "m3e_progress"
    )

    val waveOffset = if (style == ProgressBarStyle.WAVE) {
        val waveTransition = rememberInfiniteTransition(label = "m3e_wave")
        waveTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "m3e_wave_offset"
        ).value
    } else {
        0f
    }

    val fillFraction = animatedProgress.coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .height(trackHeight)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@Canvas

            val progressWidth = width * fillFraction
            if (!progressWidth.isFinite()) return@Canvas
            val centerY = height / 2f
            val gap = 8.dp.toPx()

            when (style) {
                ProgressBarStyle.WAVE -> {
                    // 与统一进度条一致：波浪线 + 断开轨道
                    val strokeWidth = height * 0.5f
                    val amplitude = height * 0.25f
                    val wavelength = 35.dp.toPx()
                    if (wavelength <= 0f) return@Canvas

                    val trackStartX = progressWidth + gap
                    if (trackStartX < width) {
                        drawLine(
                            color = trackColor,
                            start = Offset(trackStartX, centerY),
                            end = Offset(width, centerY),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }

                    if (progressWidth > strokeWidth) {
                        val progressPath = Path().apply {
                            var x = 0f
                            val startY = centerY + amplitude * sin(waveOffset)
                            moveTo(0f, startY)
                            var safetySteps = 0
                            while (x <= progressWidth && safetySteps < 4000) {
                                val phase = (x / wavelength) * 2f * PI.toFloat() + waveOffset
                                val y = centerY + amplitude * sin(phase)
                                if (!y.isFinite()) break
                                lineTo(x, y)
                                x += 2f
                                safetySteps++
                            }
                        }
                        drawPath(
                            path = progressPath,
                            color = color,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }

                ProgressBarStyle.LINEAR -> {
                    // 与统一进度条一致：线形断开轨道
                    val strokeWidth = height * 0.6f

                    if (progressWidth > strokeWidth) {
                        drawLine(
                            color = color,
                            start = Offset(strokeWidth / 2f, centerY),
                            end = Offset(progressWidth, centerY),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }

                    val trackStartX = progressWidth + gap
                    if (trackStartX < width - strokeWidth / 2f) {
                        drawLine(
                            color = trackColor,
                            start = Offset(trackStartX, centerY),
                            end = Offset(width - strokeWidth / 2f, centerY),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

private fun normalizeTotpData(data: TotpData): TotpData {
    val safePeriod = data.period.takeIf { it > 0 } ?: 30
    val safeDigits = data.digits.coerceIn(4, 10)
    return if (safePeriod == data.period && safeDigits == data.digits) {
        data
    } else {
        data.copy(period = safePeriod, digits = safeDigits)
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
