package takagi.ru.monica.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.fragment.app.FragmentActivity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import takagi.ru.monica.R
import takagi.ru.monica.data.BottomNavContentTab
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.model.PasskeyBindingCodec
import takagi.ru.monica.data.model.TimelineEvent
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel
import takagi.ru.monica.viewmodel.TotpViewModel
import takagi.ru.monica.viewmodel.CategoryFilter
import takagi.ru.monica.data.Category
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.viewmodel.GeneratorViewModel
import takagi.ru.monica.viewmodel.GeneratorType
import takagi.ru.monica.viewmodel.NoteViewModel
import takagi.ru.monica.viewmodel.PasskeyViewModel
import takagi.ru.monica.viewmodel.TimelineViewModel
import takagi.ru.monica.ui.screens.SettingsScreen
import takagi.ru.monica.ui.screens.GeneratorScreen  // 添加生成器页面导入
import takagi.ru.monica.ui.screens.NoteListScreen
import takagi.ru.monica.ui.screens.NoteListContent
import takagi.ru.monica.ui.screens.PasswordDetailScreen
import takagi.ru.monica.ui.screens.SendScreen
import takagi.ru.monica.ui.screens.CardWalletScreen
import takagi.ru.monica.ui.screens.CardWalletTab
import takagi.ru.monica.ui.screens.BankCardDetailScreen
import takagi.ru.monica.ui.screens.DocumentDetailScreen
import takagi.ru.monica.ui.screens.TimelineScreen
import takagi.ru.monica.ui.screens.PasskeyListScreen
import takagi.ru.monica.ui.gestures.SwipeActions
import takagi.ru.monica.ui.haptic.rememberHapticFeedback
import kotlin.math.absoluteValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import takagi.ru.monica.ui.components.QrCodeDialog
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.components.DraggableBottomNavScaffold
import takagi.ru.monica.ui.components.SwipeableAddFab
import takagi.ru.monica.ui.components.DraggableNavItem
import takagi.ru.monica.ui.components.QuickActionItem
import takagi.ru.monica.ui.components.QuickAddCallback
import takagi.ru.monica.ui.components.SyncStatusIcon
import takagi.ru.monica.ui.components.M3IdentityVerifyDialog
import takagi.ru.monica.ui.components.UnifiedCategoryFilterBottomSheet
import takagi.ru.monica.ui.components.UnifiedCategoryFilterSelection
import takagi.ru.monica.ui.components.UnifiedMoveCategoryTarget
import takagi.ru.monica.ui.components.UnifiedMoveToCategoryBottomSheet
import takagi.ru.monica.ui.common.dialog.DeleteConfirmDialog
import takagi.ru.monica.ui.common.layout.DetailPane
import takagi.ru.monica.ui.common.layout.InspectorRow
import takagi.ru.monica.ui.common.layout.ListPane
import takagi.ru.monica.ui.common.pull.PullActionVisualState
import takagi.ru.monica.ui.common.pull.PullGestureIndicator
import takagi.ru.monica.ui.common.pull.rememberPullActionState
import takagi.ru.monica.ui.common.selection.CategoryListItem
import takagi.ru.monica.ui.common.selection.SelectionActionBar
import takagi.ru.monica.ui.common.selection.SelectionModeTopBar
import takagi.ru.monica.ui.main.navigation.BottomNavItem
import takagi.ru.monica.ui.main.navigation.fullLabelRes
import takagi.ru.monica.ui.main.navigation.indexToDefaultTabKey
import takagi.ru.monica.ui.main.navigation.shortLabelRes
import takagi.ru.monica.ui.main.navigation.toBottomNavItem
import takagi.ru.monica.ui.main.layout.AdaptiveMainScaffold
import takagi.ru.monica.ui.password.buildAdditionalInfoPreview
import takagi.ru.monica.ui.password.MultiPasswordEntryCard
import takagi.ru.monica.ui.password.StackedPasswordGroup
import takagi.ru.monica.ui.password.PasswordEntryCard
import takagi.ru.monica.ui.password.StackCardMode
import takagi.ru.monica.ui.password.getGroupKeyForMode
import takagi.ru.monica.ui.password.getPasswordGroupTitle
import takagi.ru.monica.ui.password.getPasswordInfoKey
import takagi.ru.monica.data.bitwarden.BitwardenPendingOperation
import takagi.ru.monica.data.bitwarden.BitwardenSend
import takagi.ru.monica.bitwarden.sync.SyncStatus
import takagi.ru.monica.security.SecurityManager
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import takagi.ru.monica.ui.screens.AddEditPasswordScreen
import takagi.ru.monica.ui.screens.AddEditTotpScreen
import takagi.ru.monica.ui.screens.AddEditBankCardScreen
import takagi.ru.monica.ui.screens.AddEditDocumentScreen
import takagi.ru.monica.ui.screens.AddEditNoteScreen
import takagi.ru.monica.ui.screens.AddEditSendScreen
import takagi.ru.monica.ui.theme.MonicaTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BankCardListContent(
    viewModel: takagi.ru.monica.viewmodel.BankCardViewModel,
    onCardClick: (Long) -> Unit,
    onSelectionModeChange: (Boolean, Int, () -> Unit, () -> Unit, () -> Unit, () -> Unit) -> Unit  // 添加第6个参数：收藏
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var masterPassword by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? FragmentActivity
    val settingsManager = remember { takagi.ru.monica.utils.SettingsManager(context) }
    val appSettings by settingsManager.settingsFlow.collectAsState(
        initial = takagi.ru.monica.data.AppSettings(biometricEnabled = false)
    )
    val biometricHelper = remember { BiometricHelper(context) }
    
    val cards by viewModel.allCards.collectAsState(initial = emptyList())
    
    // 添加触觉反馈
    val haptic = rememberHapticFeedback()
    
    // 添加单项删除对话框状态
    var itemToDelete by remember { mutableStateOf<takagi.ru.monica.data.SecureItem?>(null) }
    var singleItemPasswordInput by remember { mutableStateOf("") }
    var showSingleItemPasswordVerify by remember { mutableStateOf(false) }
    
    // 添加已删除项ID集合（用于在验证前隐藏项）
    var deletedItemIds by remember { mutableStateOf(setOf<Long>()) }
    
    // 过滤掉已删除的项
    val visibleCards = remember(cards, deletedItemIds) {
        cards.filter { it.id !in deletedItemIds }
    }
    
    // 通知父组件选择模式状态变化
    LaunchedEffect(isSelectionMode, selectedItems.size) {
        if (isSelectionMode) {
            onSelectionModeChange(
                true,
                selectedItems.size,
                {
                    // 退出选择模式
                    isSelectionMode = false
                    selectedItems = emptySet()
                },
                {
                    // 全选/取消全选
                    selectedItems = if (selectedItems.size == cards.size) {
                        emptySet()
                    } else {
                        cards.map { it.id }.toSet()
                    }
                },
                {
                    // 批量删除
                    showPasswordDialog = true
                },
                {
                    // 批量收藏
                    scope.launch {
                        selectedItems.forEach { id ->
                            viewModel.toggleFavorite(id)
                        }
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.batch_favorited, selectedItems.size),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        isSelectionMode = false
                        selectedItems = emptySet()
                    }
                }
            )
        } else {
            onSelectionModeChange(false, 0, {}, {}, {}, {})
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.batch_delete_bankcards_title)) },
            text = { Text(stringResource(R.string.batch_delete_bankcards_message, selectedItems.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        showPasswordDialog = true
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
    
    // 主密码验证对话框
    if (showPasswordDialog) {
        val biometricAction = if (
            activity != null &&
            appSettings.biometricEnabled &&
            biometricHelper.isBiometricAvailable()
        ) {
            {
                biometricHelper.authenticate(
                    activity = activity,
                    title = context.getString(R.string.verify_identity),
                    subtitle = context.getString(R.string.verify_to_delete),
                    onSuccess = {
                        selectedItems.forEach { id ->
                            viewModel.deleteCard(id)
                        }
                        showPasswordDialog = false
                        masterPassword = ""
                        passwordError = false
                        isSelectionMode = false
                        selectedItems = emptySet()
                    },
                    onError = { error ->
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    },
                    onFailed = {}
                )
            }
        } else {
            null
        }
        M3IdentityVerifyDialog(
            title = stringResource(R.string.verify_identity),
            message = stringResource(R.string.batch_delete_bankcards_message, selectedItems.size),
            passwordValue = masterPassword,
            onPasswordChange = {
                masterPassword = it
                passwordError = false
            },
            onDismiss = {
                showPasswordDialog = false
                masterPassword = ""
                passwordError = false
            },
            onConfirm = {
                scope.launch {
                    val securityManager = takagi.ru.monica.security.SecurityManager(context)
                    if (securityManager.verifyMasterPassword(masterPassword)) {
                        selectedItems.forEach { id ->
                            viewModel.deleteCard(id)
                        }
                        showPasswordDialog = false
                        masterPassword = ""
                        passwordError = false
                        isSelectionMode = false
                        selectedItems = emptySet()
                    } else {
                        passwordError = true
                    }
                }
            },
            confirmText = stringResource(R.string.delete),
            destructiveConfirm = true,
            isPasswordError = passwordError,
            passwordErrorText = stringResource(R.string.current_password_incorrect),
            onBiometricClick = biometricAction,
            biometricHintText = if (biometricAction == null) {
                context.getString(R.string.biometric_not_available)
            } else {
                null
            }
        )
    }
    
    if (cards.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_bank_cards_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        val lazyListState = rememberLazyListState()
        var localCards by remember(visibleCards) { mutableStateOf(visibleCards) }

        LaunchedEffect(visibleCards) {
            localCards = visibleCards
        }

        val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
            if (isSelectionMode) {
                localCards = localCards.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
            }
        }

        LaunchedEffect(reorderableLazyListState.isAnyItemDragging) {
            if (!reorderableLazyListState.isAnyItemDragging && isSelectionMode) {
                val newOrders = localCards.mapIndexed { index, item -> item.id to index }
                if (newOrders.isNotEmpty()) {
                    viewModel.updateSortOrders(newOrders)
                }
            }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isSelectionMode,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = localCards,  // 使用过滤后的列表
                key = { it.id }
            ) { card ->
                val index = localCards.indexOf(card)
                ReorderableItem(
                    reorderableLazyListState,
                    key = card.id,
                    enabled = isSelectionMode
                ) { isDragging ->
                    val elevation by animateDpAsState(
                        if (isDragging) 8.dp else 0.dp,
                        label = "drag_elevation"
                    )
                    val dragModifier = if (isSelectionMode) {
                        Modifier.longPressDraggableHandle(
                            onDragStarted = { haptic.performLongPress() },
                            onDragStopped = { haptic.performSuccess() }
                        )
                    } else {
                        Modifier
                    }

                    if (isSelectionMode) {
                        Box(
                            modifier = Modifier
                                .graphicsLayer { shadowElevation = elevation.toPx() }
                                .then(dragModifier)
                        ) {
                            BankCardItemCard(
                                item = card,
                                onClick = {
                                    if (isSelectionMode) {
                                        // 选择模式下点击切换选择状态
                                        selectedItems = if (selectedItems.contains(card.id)) {
                                            selectedItems - card.id
                                        } else {
                                            selectedItems + card.id
                                        }
                                    } else {
                                        // 普通模式下打开详情
                                        onCardClick(card.id)
                                    }
                                },
                                onDelete = {
                                    itemToDelete = card
                                },
                                onToggleFavorite = { id, _ ->
                                    viewModel.toggleFavorite(id)
                                },
                                onMoveUp = if (index > 0 && !isSelectionMode) {
                                    {
                                        val currentItem = localCards[index]
                                        val previousItem = localCards[index - 1]
                                        viewModel.updateSortOrders(listOf(
                                            currentItem.id to (index - 1),
                                            previousItem.id to index
                                        ))
                                    }
                                } else null,
                                onMoveDown = if (index < localCards.size - 1 && !isSelectionMode) {
                                    {
                                        val currentItem = localCards[index]
                                        val nextItem = localCards[index + 1]
                                        viewModel.updateSortOrders(listOf(
                                            currentItem.id to (index + 1),
                                            nextItem.id to index
                                        ))
                                    }
                                } else null,
                                onLongClick = {
                                    haptic.performLongPress()
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedItems = setOf(card.id)
                                    }
                                },
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedItems.contains(card.id)
                            )
                        }
                    } else {
                        takagi.ru.monica.ui.gestures.SwipeActions(
                            onSwipeLeft = {
                                // 左滑删除
                                haptic.performWarning()
                                itemToDelete = card
                                deletedItemIds = deletedItemIds + card.id
                            },
                            onSwipeRight = {
                                // 右滑选择
                                haptic.performSuccess()
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                }
                                selectedItems = if (selectedItems.contains(card.id)) {
                                    selectedItems - card.id
                                } else {
                                    selectedItems + card.id
                                }
                            },
                            isSwiped = itemToDelete?.id == card.id,
                            enabled = !isDragging
                        ) {
                            Box(
                                modifier = Modifier.graphicsLayer { shadowElevation = elevation.toPx() }
                            ) {
                                BankCardItemCard(
                                    item = card,
                                    onClick = { onCardClick(card.id) },
                                    onDelete = { itemToDelete = card },
                                    onToggleFavorite = { id, _ -> viewModel.toggleFavorite(id) },
                                    onMoveUp = if (index > 0) {
                                        {
                                            val currentItem = localCards[index]
                                            val previousItem = localCards[index - 1]
                                            viewModel.updateSortOrders(listOf(
                                                currentItem.id to (index - 1),
                                                previousItem.id to index
                                            ))
                                        }
                                    } else null,
                                    onMoveDown = if (index < localCards.size - 1) {
                                        {
                                            val currentItem = localCards[index]
                                            val nextItem = localCards[index + 1]
                                            viewModel.updateSortOrders(listOf(
                                                currentItem.id to (index + 1),
                                                nextItem.id to index
                                            ))
                                        }
                                    } else null,
                                    onLongClick = {
                                        haptic.performLongPress()
                                        isSelectionMode = true
                                        selectedItems = setOf(card.id)
                                    },
                                    isSelectionMode = false,
                                    isSelected = false
                                )
                            }
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }    // 单项删除确认对话框(支持指纹和密码验证)
    itemToDelete?.let { item ->
        DeleteConfirmDialog(
            itemTitle = item.title,
            itemType = stringResource(R.string.item_type_bank_card),
            biometricEnabled = appSettings.biometricEnabled,
            onDismiss = {
                // 取消删除，恢复卡片显示
                deletedItemIds = deletedItemIds - item.id
                itemToDelete = null
            },
            onConfirmWithPassword = { password ->
                singleItemPasswordInput = password
                showSingleItemPasswordVerify = true
            },
            onConfirmWithBiometric = {
                // 指纹验证成功，直接删除
                viewModel.deleteCard(item.id)
                Toast.makeText(
                    context,
                    context.getString(R.string.deleted),
                    Toast.LENGTH_SHORT
                ).show()
                itemToDelete = null
            }
        )
    }
    
    // 单项删除密码验证
    if (showSingleItemPasswordVerify && itemToDelete != null) {
        LaunchedEffect(Unit) {
            val securityManager = takagi.ru.monica.security.SecurityManager(context)
            if (securityManager.verifyMasterPassword(singleItemPasswordInput)) {
                // 密码正确，执行真实删除
                viewModel.deleteCard(itemToDelete!!.id)
                
                Toast.makeText(
                    context,
                    context.getString(R.string.deleted),
                    Toast.LENGTH_SHORT
                ).show()
                
                // 清理状态（保持在 deletedItemIds 中，因为已真实删除）
                itemToDelete = null
                singleItemPasswordInput = ""
                showSingleItemPasswordVerify = false
            } else {
                // 密码错误，恢复卡片显示
                deletedItemIds = deletedItemIds - itemToDelete!!.id
                
                Toast.makeText(
                    context,
                    context.getString(R.string.current_password_incorrect),
                    Toast.LENGTH_SHORT
                ).show()
                
                // 重置状态
                itemToDelete = null
                singleItemPasswordInput = ""
                showSingleItemPasswordVerify = false
            }
        }
    }
}


