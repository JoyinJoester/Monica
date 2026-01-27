package takagi.ru.monica.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import takagi.ru.monica.R
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.ui.components.BankCardCard
import takagi.ru.monica.ui.components.DocumentCard
import takagi.ru.monica.ui.components.EmptyState
import takagi.ru.monica.ui.components.LoadingIndicator
import takagi.ru.monica.ui.haptic.rememberHapticFeedback
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel

enum class CardWalletTab {
    BANK_CARDS,
    DOCUMENTS
}

/**
 * 卡包界面，整合了银行卡和证件
 * 使用 M3E 设计风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardWalletScreen(
    bankCardViewModel: BankCardViewModel,
    documentViewModel: DocumentViewModel,
    onCardClick: (Long) -> Unit,
    onDocumentClick: (Long) -> Unit,
    currentTab: CardWalletTab,
    onTabSelected: (CardWalletTab) -> Unit,
    onSelectionModeChange: (Boolean, Int, () -> Unit, () -> Unit, () -> Unit) -> Unit,
    // Special callback for bank cards which supports favorite
    onBankCardSelectionModeChange: (Boolean, Int, () -> Unit, () -> Unit, () -> Unit, () -> Unit) -> Unit, 
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // M3E 风格的顶部标题栏 (Header)
        // 包含左侧的大标题和右侧的胶囊形切换器
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp), // 增加边距
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧大标题
            Text(
                text = stringResource(R.string.nav_card_wallet),
                style = MaterialTheme.typography.headlineLarge, // 使用大标题样式
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // 右侧胶囊形切换器 (Pill Switcher)
            Surface(
                shape = RoundedCornerShape(50), // 完全圆角 (胶囊形)
                color = MaterialTheme.colorScheme.surfaceContainerHigh, // 较深的容器色
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Bank Cards Pill
                    PillTabItem(
                        selected = currentTab == CardWalletTab.BANK_CARDS,
                        onClick = { onTabSelected(CardWalletTab.BANK_CARDS) },
                        icon = Icons.Default.CreditCard,
                        contentDescription = stringResource(R.string.nav_bank_cards_short)
                    )

                    // Documents Pill
                    PillTabItem(
                        selected = currentTab == CardWalletTab.DOCUMENTS,
                        onClick = { onTabSelected(CardWalletTab.DOCUMENTS) },
                        icon = Icons.Default.Description,
                        contentDescription = stringResource(R.string.nav_documents_short)
                    )
                }
            }
        }

        // 内容区域，带有切换动画
        AnimatedContent(
            targetState = currentTab,
            label = "CardWalletTabContent",
            transitionSpec = {
                (fadeIn(animationSpec = tween(300))).togetherWith(fadeOut(animationSpec = tween(300)))
            },
            modifier = Modifier.weight(1f)
        ) { targetTab ->
            when (targetTab) {
                CardWalletTab.BANK_CARDS -> {
                    BankCardListContent(
                        viewModel = bankCardViewModel,
                        onCardClick = onCardClick,
                        onSelectionModeChange = onBankCardSelectionModeChange
                    )
                }
                CardWalletTab.DOCUMENTS -> {
                    DocumentListContent(
                        viewModel = documentViewModel,
                        onDocumentClick = onDocumentClick,
                        onSelectionModeChange = onSelectionModeChange
                    )
                }
            }
        }
    }
}

// Reuse logic from BankCardListScreen but adapted for being a content part
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BankCardListContent(
    viewModel: BankCardViewModel,
    onCardClick: (Long) -> Unit,
    onSelectionModeChange: (Boolean, Int, () -> Unit, () -> Unit, () -> Unit, () -> Unit) -> Unit
) {
    val cards by viewModel.allCards.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    val haptic = rememberHapticFeedback()
    
    // Selection state
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<SecureItem?>(null) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    // Update parent about selection mode changes
    LaunchedEffect(isSelectionMode, selectedIds) {
        onSelectionModeChange(
            isSelectionMode,
            selectedIds.size,
            { // Exit selection
                isSelectionMode = false
                selectedIds = emptySet()
            },
            { // Select all
                if (selectedIds.size == cards.size) {
                    selectedIds = emptySet()
                } else {
                    selectedIds = cards.map { it.id }.toSet()
                }
            },
            { // Delete selected
                 if (selectedIds.isNotEmpty()) {
                     showBatchDeleteDialog = true
                 }
            },
            { // Favorite selected (Placeholder if needed)
                // Implement favorite logic if ViewModel supports it
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> LoadingIndicator()
            cards.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.CreditCard,
                    title = stringResource(R.string.no_bank_cards_title),
                    description = stringResource(R.string.no_bank_cards_description)
                )
            }
            else -> {
                // 用于拖动排序的本地列表状态
                var localCards by remember(cards) { mutableStateOf(cards) }
                
                LaunchedEffect(cards) {
                    localCards = cards
                }
                
                val lazyListState = rememberLazyListState()
                val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    if (isSelectionMode) {
                        localCards = localCards.toMutableList().apply {
                            add(to.index, removeAt(from.index))
                        }
                    }
                }
                
                // 保存排序
                LaunchedEffect(reorderableLazyListState.isAnyItemDragging) {
                    if (!reorderableLazyListState.isAnyItemDragging && isSelectionMode) {
                        val newOrders = localCards.mapIndexed { index, item ->
                            item.id to index
                        }
                        if (newOrders.isNotEmpty()) {
                            viewModel.updateSortOrders(newOrders)
                        }
                    }
                }
                
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = localCards, key = { it.id }) { card ->
                        val isSelected = selectedIds.contains(card.id)
                        
                        ReorderableItem(reorderableLazyListState, key = card.id) { isDragging ->
                            val elevation by animateDpAsState(
                                if (isDragging) 8.dp else 0.dp,
                                label = "drag_elevation"
                            )
                            
                            // 在多选模式下使用拖动手柄
                            val dragModifier = if (isSelectionMode) {
                                Modifier.longPressDraggableHandle(
                                    onDragStarted = { haptic.performLongPress() },
                                    onDragStopped = { haptic.performSuccess() }
                                )
                            } else {
                                Modifier
                            }
                            
                            Box(
                                modifier = Modifier
                                    .graphicsLayer { shadowElevation = elevation.toPx() }
                                    .then(dragModifier)
                            ) {
                                BankCardCard(
                                    item = card,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            haptic.performLongPress()
                                            isSelectionMode = true
                                            selectedIds = setOf(card.id)
                                        }
                                    },
                                    onClick = { 
                                        if (isSelectionMode) {
                                            selectedIds = if (isSelected) {
                                                selectedIds - card.id
                                            } else {
                                                selectedIds + card.id
                                            }
                                            if (selectedIds.isEmpty()) {
                                                isSelectionMode = false
                                            }
                                        } else {
                                            onCardClick(card.id)
                                        }
                                    },
                                    onDelete = { itemToDelete = card }
                                )
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
    
    // Single delete dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.delete_bank_card_title)) },
            text = { Text(stringResource(R.string.delete_bank_card_message, item.title)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteCard(item.id); itemToDelete = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Batch delete dialog
    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text(stringResource(R.string.batch_delete_title)) },
            text = { Text(stringResource(R.string.batch_delete_message, selectedIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    selectedIds.forEach { viewModel.deleteCard(it) }
                    isSelectionMode = false
                    selectedIds = emptySet()
                    showBatchDeleteDialog = false
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// Reuse logic from DocumentListScreen
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentListContent(
    viewModel: DocumentViewModel,
    onDocumentClick: (Long) -> Unit,
    onSelectionModeChange: (Boolean, Int, () -> Unit, () -> Unit, () -> Unit) -> Unit
) {
    val documents by viewModel.allDocuments.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    val haptic = rememberHapticFeedback()
    
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<SecureItem?>(null) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

     LaunchedEffect(isSelectionMode, selectedIds) {
        onSelectionModeChange(
            isSelectionMode,
            selectedIds.size,
            { // Exit
                isSelectionMode = false
                selectedIds = emptySet()
            },
            { // Select All
                if (selectedIds.size == documents.size) {
                    selectedIds = emptySet()
                } else {
                    selectedIds = documents.map { it.id }.toSet()
                }
            },
            { // Delete
                 if (selectedIds.isNotEmpty()) {
                     showBatchDeleteDialog = true
                 }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> LoadingIndicator()
            documents.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.Description,
                    title = stringResource(R.string.no_documents_title),
                    description = stringResource(R.string.no_documents_description)
                )
            }
            else -> {
                // 用于拖动排序的本地列表状态
                var localDocuments by remember(documents) { mutableStateOf(documents) }
                
                LaunchedEffect(documents) {
                    localDocuments = documents
                }
                
                val lazyListState = rememberLazyListState()
                val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    if (isSelectionMode) {
                        localDocuments = localDocuments.toMutableList().apply {
                            add(to.index, removeAt(from.index))
                        }
                    }
                }
                
                // 保存排序
                LaunchedEffect(reorderableLazyListState.isAnyItemDragging) {
                    if (!reorderableLazyListState.isAnyItemDragging && isSelectionMode) {
                        val newOrders = localDocuments.mapIndexed { index, item ->
                            item.id to index
                        }
                        if (newOrders.isNotEmpty()) {
                            viewModel.updateSortOrders(newOrders)
                        }
                    }
                }
                
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = localDocuments, key = { it.id }) { document ->
                        val isSelected = selectedIds.contains(document.id)
                        
                        ReorderableItem(reorderableLazyListState, key = document.id) { isDragging ->
                            val elevation by animateDpAsState(
                                if (isDragging) 8.dp else 0.dp,
                                label = "drag_elevation"
                            )
                            
                            // 在多选模式下使用拖动手柄
                            val dragModifier = if (isSelectionMode) {
                                Modifier.longPressDraggableHandle(
                                    onDragStarted = { haptic.performLongPress() },
                                    onDragStopped = { haptic.performSuccess() }
                                )
                            } else {
                                Modifier
                            }
                            
                            Box(
                                modifier = Modifier
                                    .graphicsLayer { shadowElevation = elevation.toPx() }
                                    .then(dragModifier)
                            ) {
                                DocumentCard(
                                    item = document,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            haptic.performLongPress()
                                            isSelectionMode = true
                                            selectedIds = setOf(document.id)
                                        }
                                    },
                                    onClick = { 
                                        if (isSelectionMode) {
                                            selectedIds = if (isSelected) {
                                                selectedIds - document.id
                                            } else {
                                                selectedIds + document.id
                                            }
                                            if (selectedIds.isEmpty()) {
                                                isSelectionMode = false
                                            }
                                        } else {
                                            onDocumentClick(document.id)
                                        }
                                    },
                                    onDelete = { itemToDelete = document }
                                )
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
    
    itemToDelete?.let { item ->
         AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.delete_document_title)) },
            text = { Text(stringResource(R.string.delete_document_message, item.title)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteDocument(item.id); itemToDelete = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
     if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text(stringResource(R.string.batch_delete_title)) },
            text = { Text(stringResource(R.string.batch_delete_message, selectedIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    selectedIds.forEach { viewModel.deleteDocument(it) }
                    isSelectionMode = false
                    selectedIds = emptySet()
                    showBatchDeleteDialog = false
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun PillTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String
) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(12.dp), // 增大点击区域和内边距
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
    }
}
