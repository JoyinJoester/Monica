package takagi.ru.monica.ui.password

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordCardDisplayField
import takagi.ru.monica.ui.haptic.rememberHapticFeedback

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StackedPasswordGroup(
    @Suppress("UNUSED_PARAMETER") website: String,
    passwords: List<takagi.ru.monica.data.PasswordEntry>,
    isExpanded: Boolean,
    stackCardMode: StackCardMode,
    onToggleExpand: () -> Unit,
    onPasswordClick: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onSwipeLeft: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onSwipeRight: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onGroupSwipeRight: (List<takagi.ru.monica.data.PasswordEntry>) -> Unit,
    onToggleFavorite: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onToggleGroupFavorite: () -> Unit,
    onToggleGroupCover: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    isSelectionMode: Boolean,
    selectedPasswords: Set<Long>,
    swipedItemId: Long? = null,
    onToggleSelection: (Long) -> Unit,
    onOpenMultiPasswordDialog: (List<takagi.ru.monica.data.PasswordEntry>) -> Unit,
    onLongClick: (takagi.ru.monica.data.PasswordEntry) -> Unit, // 新增：长按进入多选模式
    iconCardsEnabled: Boolean = false,
    unmatchedIconHandlingStrategy: takagi.ru.monica.data.UnmatchedIconHandlingStrategy = takagi.ru.monica.data.UnmatchedIconHandlingStrategy.DEFAULT_ICON,
    passwordCardDisplayMode: takagi.ru.monica.data.PasswordCardDisplayMode = takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL,
    passwordCardDisplayFields: List<PasswordCardDisplayField> = PasswordCardDisplayField.DEFAULT_ORDER,
    showAuthenticator: Boolean = false,
    hideOtherContentWhenAuthenticator: Boolean = false,
    totpTimeOffsetSeconds: Int = 0,
    smoothAuthenticatorProgress: Boolean = true,
    enableSharedBounds: Boolean = true
) {
    val leadPassword = passwords.firstOrNull() ?: return

    // 检查是否为多密码合并卡片(除密码外信息完全相同)
    val isMergedPasswordCard = passwords.size > 1 && 
        passwords.map { getPasswordInfoKey(it) }.distinct().size == 1
    
    // 如果选择“始终展开”，则直接平铺展示，不使用堆叠容器
    if (stackCardMode == StackCardMode.ALWAYS_EXPANDED) {
        passwords.forEach { password ->
            key(password.id) {
                PasswordListSingleCardItem(
                    entry = password,
                    onClick = {
                        if (isSelectionMode) {
                            onToggleSelection(password.id)
                        } else {
                            onPasswordClick(password)
                        }
                    },
                    onLongClick = { onLongClick(password) },
                    onSwipeLeft = { onSwipeLeft(password) },
                    onSwipeRight = { onSwipeRight(password) },
                    isSwiped = password.id == swipedItemId,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedPasswords.contains(password.id),
                    onToggleFavorite = { onToggleFavorite(password) },
                    unmatchedIconHandlingStrategy = unmatchedIconHandlingStrategy,
                    passwordCardDisplayMode = passwordCardDisplayMode,
                    passwordCardDisplayFields = passwordCardDisplayFields,
                    showAuthenticator = showAuthenticator,
                    hideOtherContentWhenAuthenticator = hideOtherContentWhenAuthenticator,
                    totpTimeOffsetSeconds = totpTimeOffsetSeconds,
                    smoothAuthenticatorProgress = smoothAuthenticatorProgress,
                    iconCardsEnabled = iconCardsEnabled,
                    enableSharedBounds = enableSharedBounds
                )
            }
        }
        return
    }

    // 单个密码直接显示，不堆叠 (且不是合并卡片)
    if (passwords.size == 1 && !isMergedPasswordCard) {
        val password = leadPassword
        PasswordListSingleCardItem(
            entry = password,
            onClick = {
                if (isSelectionMode) {
                    onToggleSelection(password.id)
                } else {
                    onPasswordClick(password)
                }
            },
            onLongClick = { onLongClick(password) },
            onSwipeLeft = { onSwipeLeft(password) },
            onSwipeRight = { onSwipeRight(password) },
            isSwiped = password.id == swipedItemId,
            isSelectionMode = isSelectionMode,
            isSelected = selectedPasswords.contains(password.id),
            onToggleFavorite = { onToggleFavorite(password) },
            unmatchedIconHandlingStrategy = unmatchedIconHandlingStrategy,
            passwordCardDisplayMode = passwordCardDisplayMode,
            passwordCardDisplayFields = passwordCardDisplayFields,
            showAuthenticator = showAuthenticator,
            hideOtherContentWhenAuthenticator = hideOtherContentWhenAuthenticator,
            totpTimeOffsetSeconds = totpTimeOffsetSeconds,
            smoothAuthenticatorProgress = smoothAuthenticatorProgress,
            iconCardsEnabled = iconCardsEnabled,
            enableSharedBounds = enableSharedBounds
        )
        return
    }

    // 多密码合并卡片：普通模式显示为普通单卡代理，点击后仍进入多密码详情；多选模式保留多密码卡。
    if (isMergedPasswordCard) {
        if (isSelectionMode) {
            takagi.ru.monica.ui.gestures.SwipeActions(
                onSwipeLeft = { onSwipeLeft(leadPassword) },
                onSwipeRight = { onGroupSwipeRight(passwords) },
                isSwiped = leadPassword.id == swipedItemId,
                enabled = true
            ) {
                MultiPasswordEntryCard(
                    passwords = passwords,
                    onClick = { password ->
                        if (isSelectionMode) {
                            onToggleSelection(password.id)
                        } else {
                            onPasswordClick(password)
                        }
                    },
                    onCardClick = if (!isSelectionMode) {
                        { onOpenMultiPasswordDialog(passwords) }
                    } else null,
                    onLongClick = { onLongClick(leadPassword) },
                    onToggleFavorite = { password -> onToggleFavorite(password) },
                    onToggleGroupCover = null,
                    isSelectionMode = isSelectionMode,
                    selectedPasswords = selectedPasswords,
                    canSetGroupCover = false,
                    hasGroupCover = false,
                    isInExpandedGroup = false,
                    iconCardsEnabled = iconCardsEnabled,
                    unmatchedIconHandlingStrategy = unmatchedIconHandlingStrategy,
                    passwordCardDisplayMode = passwordCardDisplayMode,
                    passwordCardDisplayFields = passwordCardDisplayFields,
                    showAuthenticator = showAuthenticator,
                    hideOtherContentWhenAuthenticator = hideOtherContentWhenAuthenticator,
                    totpTimeOffsetSeconds = totpTimeOffsetSeconds,
                    smoothAuthenticatorProgress = smoothAuthenticatorProgress
                )
            }
        } else {
            val displayEntry = leadPassword.copy(
                isFavorite = passwords.all { it.isFavorite }
            )
            takagi.ru.monica.ui.gestures.SwipeActions(
                onSwipeLeft = { onSwipeLeft(leadPassword) },
                onSwipeRight = { onGroupSwipeRight(passwords) },
                isSwiped = leadPassword.id == swipedItemId,
                enabled = true
            ) {
                PasswordEntryCard(
                    entry = displayEntry,
                    onClick = { onOpenMultiPasswordDialog(passwords) },
                    onLongClick = { onLongClick(leadPassword) },
                    onToggleFavorite = { passwords.forEach(onToggleFavorite) },
                    onToggleGroupCover = null,
                    isSelectionMode = false,
                    isSelected = false,
                    canSetGroupCover = false,
                    isInExpandedGroup = false,
                    isSingleCard = true,
                    iconCardsEnabled = iconCardsEnabled,
                    unmatchedIconHandlingStrategy = unmatchedIconHandlingStrategy,
                    passwordCardDisplayMode = passwordCardDisplayMode,
                    passwordCardDisplayFields = passwordCardDisplayFields,
                    showAuthenticator = showAuthenticator,
                    hideOtherContentWhenAuthenticator = hideOtherContentWhenAuthenticator,
                    totpTimeOffsetSeconds = totpTimeOffsetSeconds,
                    smoothAuthenticatorProgress = smoothAuthenticatorProgress,
                    enableSharedBounds = enableSharedBounds
                )
            }
        }
        return
    }
    
    // 否则使用原有的堆叠逻辑
    val isGroupFavorited = passwords.all { it.isFavorite }
    val hasGroupCover = passwords.any { it.isGroupCover }
    
    // 🎨 动画状态
    val effectiveExpanded = when (stackCardMode) {
        StackCardMode.AUTO -> isExpanded
        StackCardMode.ALWAYS_EXPANDED -> true
    }

    val expandProgress by animateFloatAsState(
        targetValue = if (effectiveExpanded && passwords.size > 1) 1f else 0f,
        animationSpec = tween(200),
        label = "expand_animation"
    )
    
    val containerAlpha by animateFloatAsState(
        targetValue = if (effectiveExpanded && passwords.size > 1) 1f else 0f,
        animationSpec = tween(200),
        label = "container_alpha"
    )
    
    // 🎯 下滑手势状态
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val haptic = rememberHapticFeedback()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // 📚 堆叠背后的层级卡片 (仅在堆叠状态下可见，或动画过程中可见)
        val stackAlpha by animateFloatAsState(
            targetValue = if (effectiveExpanded) 0f else 1f,
            animationSpec = tween(200),
            label = "stack_alpha"
        )
        
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 背景堆叠层 (当 stackAlpha > 0 时显示)
            if (passwords.size > 1) {
                val stackCount = passwords.size.coerceAtMost(3)
                for (i in (stackCount - 1) downTo 1) {
                    val offsetDp = (i * 4).dp
                    val scaleFactor = 1f - (i * 0.02f)
                    val layerAlpha = (0.7f - (i * 0.2f)) * stackAlpha
                    
                    if (layerAlpha > 0.01f) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = offsetDp) // Padding top creates the vertical offset effect
                                .graphicsLayer {
                                    scaleX = scaleFactor
                                    scaleY = scaleFactor
                                    alpha = layerAlpha
                                    translationY = (i * 4).dp.toPx() * (1f - stackAlpha) // Optional: slide up when disappearing?
                                },
                            elevation = CardDefaults.cardElevation(defaultElevation = (i * 1.5).dp),
                            colors = CardDefaults.cardColors(), // Use default colors to match single cards
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Box(modifier = Modifier.height(76.dp))
                        }
                    }
                }
            }

            // 🎯 主卡片 (持续存在，内容和属性变化)
            val cardElevation by animateDpAsState(
                targetValue = if (effectiveExpanded) 4.dp else 6.dp,
                animationSpec = tween(200),
                label = "elevation"
            )
            val cardShape by animateDpAsState(
                targetValue = if (effectiveExpanded) 16.dp else 14.dp,
                animationSpec = tween(200),
                label = "shape"
            )
            
            val isSelected = selectedPasswords.contains(leadPassword.id)
            val cardColors = if (isSelected) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            } else {
                CardDefaults.cardColors()
            }
            
            takagi.ru.monica.ui.gestures.SwipeActions(
                onSwipeLeft = { 
                    if (!effectiveExpanded) onSwipeLeft(leadPassword) 
                    // Expanded state swipe logic handled inside? Or disable swipe on container when expanded?
                },
                onSwipeRight = { 
                    if (!effectiveExpanded) onGroupSwipeRight(passwords)
                },
                isSwiped = leadPassword.id == swipedItemId,
                enabled = !effectiveExpanded // Disable swipe actions on the container when expanded
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            // 展开时的下滑手势
                            if (effectiveExpanded && passwords.size > 1) {
                                Modifier.pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { haptic.performLongPress() },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            if (dragAmount.y > 0) {
                                                swipeOffset = (swipeOffset + dragAmount.y).coerceAtMost(150f)
                                            }
                                        },
                                        onDragEnd = {
                                            if (swipeOffset > 80f) {
                                                haptic.performSuccess()
                                                onToggleExpand()
                                            }
                                            swipeOffset = 0f
                                        },
                                        onDragCancel = { swipeOffset = 0f }
                                    )
                                }
                            } else Modifier
                        )
                        .graphicsLayer {
                            // 下滑时的位移效果
                            if (effectiveExpanded) {
                                translationY = swipeOffset * 0.5f
                            }
                        },
                    shape = RoundedCornerShape(cardShape),
                    elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                    colors = cardColors
                ) {
                    // 内容切换：收起态(Header) vs 展开态(Column)
                    AnimatedContent(
                        targetState = effectiveExpanded,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(180)) togetherWith
                                fadeOut(animationSpec = tween(140))).using(
                                SizeTransform(clip = false) { initialSize, targetSize ->
                                    tween(
                                        durationMillis = if (targetSize.height < initialSize.height) 120 else 220,
                                        easing = FastOutSlowInEasing
                                    )
                                }
                            )
                        },
                        label = "content_switch"
                    ) { expanded ->
                        if (!expanded) {
                            // --- 收起状态的内容 ---
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleExpand() }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // 🏷️ 数量徽章
                                            Surface(
                                                shape = RoundedCornerShape(18.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shadowElevation = 2.dp
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.Layers,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Text(
                                                        text = "${passwords.size}",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                            
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = leadPassword.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (leadPassword.website.isNotBlank()) {
                                                    Text(
                                                        text = leadPassword.website,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isSelectionMode) {
                                                androidx.compose.material3.Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = { onGroupSwipeRight(passwords) }
                                                )
                                            } else {
                                                if (isGroupFavorited) {
                                                    Icon(
                                                        Icons.Default.Favorite,
                                                        contentDescription = stringResource(R.string.favorite),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Icon(
                                                    Icons.Default.ExpandMore,
                                                    contentDescription = stringResource(R.string.expand),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // --- 展开状态的内容 ---
                            val edgeInteractionSource = remember { MutableInteractionSource() }
                            val edgeContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
                            val edgeBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            val edgeHitWidth = 14.dp
                            val edgeHitHeight = 12.dp
                            val edgeTapModifier = Modifier.clickable(
                                interactionSource = edgeInteractionSource,
                                indication = null,
                                onClick = onToggleExpand
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // 📌 1. 顶部标题栏
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 左侧：密码数量标签
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Layers,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = stringResource(R.string.passwords_count, passwords.size),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    
                                    // 右侧：收起按钮
                                    FilledTonalIconButton(
                                        onClick = onToggleExpand,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ExpandLess,
                                            contentDescription = stringResource(R.string.collapse),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                
                                // 分隔线
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                
                                // 📦 2. 密码列表内容
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(edgeContainerColor)
                                        .border(
                                            width = 1.dp,
                                            color = edgeBorderColor,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = edgeHitWidth, vertical = edgeHitHeight),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        val groupedByInfo = remember(passwords) {
                                            passwords
                                                .groupBy { getPasswordInfoKey(it) }
                                                .values
                                                .mapNotNull { group ->
                                                    group
                                                        .sortedBy { it.sortOrder }
                                                        .takeIf { it.isNotEmpty() }
                                                }
                                        }
                                        
                                        groupedByInfo.forEach { passwordGroup ->
                                            val groupLeadPassword = passwordGroup.firstOrNull()
                                                ?: return@forEach
                                            key("stack_group_${groupLeadPassword.id}_${passwordGroup.size}") {
                                                takagi.ru.monica.ui.gestures.SwipeActions(
                                                    onSwipeLeft = { onSwipeLeft(groupLeadPassword) },
                                                    onSwipeRight = { onSwipeRight(groupLeadPassword) },
                                                    enabled = true
                                                ) {
                                                    if (passwordGroup.size == 1) {
                                                        val password = groupLeadPassword
                                                        PasswordEntryCard(
                                                            entry = password,
                                                            onClick = {
                                                                if (isSelectionMode) {
                                                                    onToggleSelection(password.id)
                                                                } else {
                                                                    onPasswordClick(password)
                                                                }
                                                            },
                                                            onLongClick = { onLongClick(password) },
                                                            onToggleFavorite = { onToggleFavorite(password) },
                                                            onToggleGroupCover = if (passwords.size > 1) {
                                                                { onToggleGroupCover(password) }
                                                            } else null,
                                                            isSelectionMode = isSelectionMode,
                                                            isSelected = selectedPasswords.contains(password.id),
                                                            canSetGroupCover = passwords.size > 1,
                                                            isInExpandedGroup = true, // We are inside the expanded container
                                                            isSingleCard = false,
                                                            iconCardsEnabled = iconCardsEnabled,
                                                            unmatchedIconHandlingStrategy = unmatchedIconHandlingStrategy,
                                                            passwordCardDisplayMode = passwordCardDisplayMode,
                                                            passwordCardDisplayFields = passwordCardDisplayFields,
                                                            showAuthenticator = showAuthenticator,
                                                            hideOtherContentWhenAuthenticator = hideOtherContentWhenAuthenticator,
                                                            totpTimeOffsetSeconds = totpTimeOffsetSeconds,
                                                            smoothAuthenticatorProgress = smoothAuthenticatorProgress,
                                                            enableSharedBounds = enableSharedBounds
                                                        )
                                                    } else if (isSelectionMode) {
                                                        MultiPasswordEntryCard(
                                                            passwords = passwordGroup,
                                                            onClick = { password ->
                                                                if (isSelectionMode) {
                                                                    onToggleSelection(password.id)
                                                                } else {
                                                                    onPasswordClick(password)
                                                                }
                                                            },
                                                            onCardClick = if (!isSelectionMode) {
                                                                { onOpenMultiPasswordDialog(passwordGroup) }
                                                            } else null,
                                                            onLongClick = { onLongClick(groupLeadPassword) },
                                                            onToggleFavorite = { password -> onToggleFavorite(password) },
                                                            onToggleGroupCover = if (passwords.size > 1) {
                                                                { password -> onToggleGroupCover(password) }
                                                            } else null,
                                                            isSelectionMode = isSelectionMode,
                                                            selectedPasswords = selectedPasswords,
                                                            canSetGroupCover = passwords.size > 1,
                                                            hasGroupCover = hasGroupCover,
                                                            isInExpandedGroup = true, // We are inside the expanded container
                                                            iconCardsEnabled = iconCardsEnabled,
                                                            unmatchedIconHandlingStrategy = unmatchedIconHandlingStrategy,
                                                            passwordCardDisplayMode = passwordCardDisplayMode,
                                                            passwordCardDisplayFields = passwordCardDisplayFields,
                                                            showAuthenticator = showAuthenticator,
                                                            hideOtherContentWhenAuthenticator = hideOtherContentWhenAuthenticator,
                                                            totpTimeOffsetSeconds = totpTimeOffsetSeconds,
                                                            smoothAuthenticatorProgress = smoothAuthenticatorProgress
                                                        )
                                                    } else {
                                                        val displayEntry = groupLeadPassword.copy(
                                                            isFavorite = passwordGroup.all { it.isFavorite },
                                                            isGroupCover = passwordGroup.any { it.isGroupCover }
                                                        )
                                                        PasswordEntryCard(
                                                            entry = displayEntry,
                                                            onClick = { onOpenMultiPasswordDialog(passwordGroup) },
                                                            onLongClick = { onLongClick(groupLeadPassword) },
                                                            onToggleFavorite = { passwordGroup.forEach(onToggleFavorite) },
                                                            onToggleGroupCover = if (passwords.size > 1) {
                                                                {
                                                                    val coverTarget = passwordGroup.firstOrNull { it.isGroupCover }
                                                                        ?: groupLeadPassword
                                                                    onToggleGroupCover(coverTarget)
                                                                }
                                                            } else null,
                                                            isSelectionMode = false,
                                                            isSelected = false,
                                                            canSetGroupCover = passwords.size > 1,
                                                            isInExpandedGroup = true, // We are inside the expanded container
                                                            isSingleCard = false,
                                                            iconCardsEnabled = iconCardsEnabled,
                                                            unmatchedIconHandlingStrategy = unmatchedIconHandlingStrategy,
                                                            passwordCardDisplayMode = passwordCardDisplayMode,
                                                            passwordCardDisplayFields = passwordCardDisplayFields,
                                                            showAuthenticator = showAuthenticator,
                                                            hideOtherContentWhenAuthenticator = hideOtherContentWhenAuthenticator,
                                                            totpTimeOffsetSeconds = totpTimeOffsetSeconds,
                                                            smoothAuthenticatorProgress = smoothAuthenticatorProgress,
                                                            enableSharedBounds = enableSharedBounds
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    // Expanded state edge zones: only these non-card areas collapse the stack.
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .fillMaxWidth()
                                            .height(edgeHitHeight)
                                            .then(edgeTapModifier)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(edgeHitHeight)
                                            .then(edgeTapModifier)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .width(edgeHitWidth)
                                            .fillMaxHeight()
                                            .then(edgeTapModifier)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(edgeHitWidth)
                                            .fillMaxHeight()
                                            .then(edgeTapModifier)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


