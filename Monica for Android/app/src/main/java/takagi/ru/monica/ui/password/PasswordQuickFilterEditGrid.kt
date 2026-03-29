package takagi.ru.monica.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import takagi.ru.monica.data.PasswordListQuickFilterItem
import takagi.ru.monica.data.PasswordPageContentType
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class PasswordQuickFilterChipState(
    val favorite: Boolean,
    val twoFa: Boolean,
    val notes: Boolean,
    val uncategorized: Boolean,
    val localOnly: Boolean,
    val manualStackOnly: Boolean,
    val neverStack: Boolean,
    val unstacked: Boolean,
    val aggregateSelectedTypes: Set<PasswordPageContentType>,
    val aggregateVisibleTypes: List<PasswordPageContentType>
)

internal data class PasswordQuickFilterChipCallbacks(
    val onFavoriteChange: (Boolean) -> Unit,
    val onTwoFaChange: (Boolean) -> Unit,
    val onNotesChange: (Boolean) -> Unit,
    val onUncategorizedChange: (Boolean) -> Unit,
    val onLocalOnlyChange: (Boolean) -> Unit,
    val onManualStackOnlyChange: (Boolean) -> Unit,
    val onNeverStackChange: (Boolean) -> Unit,
    val onUnstackedChange: (Boolean) -> Unit,
    val onToggleAggregateType: (PasswordPageContentType) -> Unit
)

internal data class PasswordQuickFilterEditGridParams(
    val items: List<PasswordListQuickFilterItem>,
    val measuredSizes: MutableMap<PasswordListQuickFilterItem, IntSize>,
    val availableWidth: Dp,
    val chipState: PasswordQuickFilterChipState,
    val chipCallbacks: PasswordQuickFilterChipCallbacks,
    val onOrderCommitted: (List<PasswordListQuickFilterItem>) -> Unit
)

private data class PasswordQuickFilterGridMetrics(
    val availableWidthPx: Float,
    val itemHeightPx: Float,
    val gridSpacingPx: Float
)

private fun <T> reorderListByInsertion(list: List<T>, item: T, insertionIndex: Int): List<T> {
    if (item !in list) return list
    return list.toMutableList().apply {
        remove(item)
        add(insertionIndex.coerceIn(0, size), item)
    }
}

private fun PasswordQuickFilterEditGridParams.itemSize(
    item: PasswordListQuickFilterItem,
    metrics: PasswordQuickFilterGridMetrics
): IntSize {
    return measuredSizes[item] ?: IntSize(
        width = ((metrics.availableWidthPx - metrics.gridSpacingPx) / 2f).roundToInt().coerceAtLeast(120),
        height = metrics.itemHeightPx.roundToInt()
    )
}

private fun PasswordQuickFilterEditGridParams.computeLayout(
    order: List<PasswordListQuickFilterItem>,
    metrics: PasswordQuickFilterGridMetrics
): Pair<Map<PasswordListQuickFilterItem, Offset>, Float> {
    val offsets = linkedMapOf<PasswordListQuickFilterItem, Offset>()
    var x = 0f
    var y = 0f
    var rowHeight = 0f

    order.forEach { item ->
        val size = itemSize(item, metrics)
        val itemWidth = size.width.toFloat()
        val itemHeight = maxOf(size.height.toFloat(), metrics.itemHeightPx)
        if (x > 0f && x + itemWidth > metrics.availableWidthPx) {
            x = 0f
            y += rowHeight + metrics.gridSpacingPx
            rowHeight = 0f
        }
        offsets[item] = Offset(x, y)
        x += itemWidth + metrics.gridSpacingPx
        rowHeight = maxOf(rowHeight, itemHeight)
    }

    val totalHeight = if (offsets.isEmpty()) metrics.itemHeightPx else y + rowHeight
    return offsets to totalHeight
}

private fun PasswordQuickFilterEditGridParams.insertionIndexFor(
    point: Offset,
    order: List<PasswordListQuickFilterItem>,
    offsets: Map<PasswordListQuickFilterItem, Offset>,
    metrics: PasswordQuickFilterGridMetrics,
    currentTargetIndex: Int,
    ignoredItem: PasswordListQuickFilterItem? = null
): Int {
    val candidates = order.filter { it != ignoredItem }
    if (candidates.isEmpty()) return 0

    val fallbackIndex = currentTargetIndex.takeIf { it in 0..candidates.size }
        ?: order.indexOf(ignoredItem).takeIf { it >= 0 }
        ?: candidates.size

    candidates.forEachIndexed { index, item ->
        val topLeft = offsets[item] ?: return@forEachIndexed
        val size = itemSize(item, metrics)
        val centerX = topLeft.x + size.width / 2f
        val centerY = topLeft.y + size.height / 2f
        val rowThreshold = size.height * 0.45f

        if (point.y < centerY - rowThreshold) return index
        if (abs(point.y - centerY) <= rowThreshold && point.x < centerX) return index
    }

    return candidates.size.coerceAtLeast(fallbackIndex)
}

@Composable
private fun PasswordQuickFilterGridChip(
    item: PasswordListQuickFilterItem,
    params: PasswordQuickFilterEditGridParams,
    modifier: Modifier = Modifier,
    onMeasured: ((IntSize) -> Unit)? = null
) {
    val trackedModifier = if (onMeasured == null) {
        modifier
    } else {
        modifier.onGloballyPositioned { coordinates -> onMeasured(coordinates.size) }
    }
    PasswordQuickFilterChipItem(
        item = item,
        categoryEditMode = true,
        quickFilterFavorite = params.chipState.favorite,
        onQuickFilterFavoriteChange = params.chipCallbacks.onFavoriteChange,
        quickFilter2fa = params.chipState.twoFa,
        onQuickFilter2faChange = params.chipCallbacks.onTwoFaChange,
        quickFilterNotes = params.chipState.notes,
        onQuickFilterNotesChange = params.chipCallbacks.onNotesChange,
        quickFilterUncategorized = params.chipState.uncategorized,
        onQuickFilterUncategorizedChange = params.chipCallbacks.onUncategorizedChange,
        quickFilterLocalOnly = params.chipState.localOnly,
        onQuickFilterLocalOnlyChange = params.chipCallbacks.onLocalOnlyChange,
        quickFilterManualStackOnly = params.chipState.manualStackOnly,
        onQuickFilterManualStackOnlyChange = params.chipCallbacks.onManualStackOnlyChange,
        quickFilterNeverStack = params.chipState.neverStack,
        onQuickFilterNeverStackChange = params.chipCallbacks.onNeverStackChange,
        quickFilterUnstacked = params.chipState.unstacked,
        onQuickFilterUnstackedChange = params.chipCallbacks.onUnstackedChange,
        aggregateSelectedTypes = params.chipState.aggregateSelectedTypes,
        aggregateVisibleTypes = params.chipState.aggregateVisibleTypes,
        onToggleAggregateType = params.chipCallbacks.onToggleAggregateType,
        modifier = trackedModifier
    )
}

@Composable
internal fun PasswordQuickFilterEditGrid(params: PasswordQuickFilterEditGridParams) {
    val density = LocalDensity.current
    val metrics = with(density) {
        PasswordQuickFilterGridMetrics(
            availableWidthPx = params.availableWidth.toPx(),
            itemHeightPx = 48.dp.toPx(),
            gridSpacingPx = 8.dp.toPx()
        )
    }
    var draggingItem by remember { mutableStateOf<PasswordListQuickFilterItem?>(null) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }
    var dragPointerPosition by remember { mutableStateOf(Offset.Zero) }
    var dragTouchOffset by remember { mutableStateOf(Offset.Zero) }
    var pendingCommittedOrder by remember { mutableStateOf<List<PasswordListQuickFilterItem>?>(null) }
    var dragSnapshotOrder by remember { mutableStateOf<List<PasswordListQuickFilterItem>>(emptyList()) }
    var dragSnapshotOffsets by remember { mutableStateOf<Map<PasswordListQuickFilterItem, Offset>>(emptyMap()) }

    val displayOrder = pendingCommittedOrder ?: params.items
    val previewSourceOrder = if (draggingItem != null && dragSnapshotOrder.isNotEmpty()) dragSnapshotOrder else displayOrder
    val previewOrder = remember(previewSourceOrder, draggingItem, dragTargetIndex) {
        val dragged = draggingItem
        if (dragged != null && dragTargetIndex >= 0) {
            reorderListByInsertion(previewSourceOrder, dragged, dragTargetIndex)
        } else {
            previewSourceOrder
        }
    }
    val activeOrder = if (draggingItem != null) previewOrder else displayOrder
    val layoutInfo = remember(activeOrder, params.measuredSizes, params.availableWidth) {
        params.computeLayout(activeOrder, metrics)
    }
    val targetOffsets = layoutInfo.first
    val gridHeight = with(density) { layoutInfo.second.toDp() }

    fun resetDragState(clearSnapshot: Boolean = true) {
        draggingItem = null
        dragTargetIndex = -1
        dragPointerPosition = Offset.Zero
        dragTouchOffset = Offset.Zero
        if (clearSnapshot) {
            dragSnapshotOrder = emptyList()
            dragSnapshotOffsets = emptyMap()
        }
    }

    LaunchedEffect(params.items, pendingCommittedOrder) {
        if (pendingCommittedOrder != null && params.items == pendingCommittedOrder) {
            pendingCommittedOrder = null
        }
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Box(modifier = Modifier.requiredWidth(params.availableWidth).height(gridHeight)) {
            params.items.forEach { item ->
                val targetPosition = targetOffsets[item] ?: dragSnapshotOffsets[item] ?: Offset.Zero
                val chipSize = params.itemSize(item, metrics)
                val itemAlpha = if (draggingItem == item) 0f else 1f
                val animatedOffset by animateIntOffsetAsState(
                    targetValue = IntOffset(targetPosition.x.roundToInt(), targetPosition.y.roundToInt()),
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    ),
                    label = "password_menu_quick_filter_position"
                )

                Box(
                    modifier = Modifier
                        .offset { animatedOffset }
                        .requiredSize(
                            width = with(density) { chipSize.width.toDp() },
                            height = with(density) { chipSize.height.toDp() }
                        )
                        .alpha(itemAlpha),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(item, displayOrder) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val snapshotOrder = displayOrder
                                        val startIndex = snapshotOrder.indexOf(item)
                                        if (startIndex == -1) return@detectDragGesturesAfterLongPress
                                        val snapshotOffsets = params.computeLayout(snapshotOrder, metrics).first
                                        val startTopLeft = snapshotOffsets[item] ?: Offset.Zero
                                        draggingItem = item
                                        dragTargetIndex = startIndex
                                        dragPointerPosition = startTopLeft + offset
                                        dragTouchOffset = offset
                                        dragSnapshotOrder = snapshotOrder
                                        dragSnapshotOffsets = snapshotOffsets
                                    },
                                    onDragCancel = { resetDragState() },
                                    onDragEnd = {
                                        val dragged = draggingItem
                                        val insertionIndex = dragTargetIndex
                                        val sourceOrder = if (dragSnapshotOrder.isNotEmpty()) dragSnapshotOrder else displayOrder
                                        if (dragged == null || insertionIndex < 0) {
                                            resetDragState()
                                            return@detectDragGesturesAfterLongPress
                                        }
                                        val reordered = reorderListByInsertion(sourceOrder, dragged, insertionIndex)
                                        pendingCommittedOrder = reordered
                                        resetDragState()
                                        if (reordered != params.items) {
                                            params.onOrderCommitted(reordered)
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (draggingItem != item) return@detectDragGesturesAfterLongPress
                                        dragPointerPosition += Offset(dragAmount.x, dragAmount.y)
                                        val snapshotOrder = if (dragSnapshotOrder.isNotEmpty()) dragSnapshotOrder else displayOrder
                                        val snapshotOffsets = if (dragSnapshotOffsets.isNotEmpty()) {
                                            dragSnapshotOffsets
                                        } else {
                                            params.computeLayout(snapshotOrder, metrics).first
                                        }
                                        dragTargetIndex = params.insertionIndexFor(
                                            point = dragPointerPosition,
                                            order = snapshotOrder,
                                            offsets = snapshotOffsets,
                                            metrics = metrics,
                                            currentTargetIndex = dragTargetIndex,
                                            ignoredItem = item
                                        )
                                    }
                                )
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        PasswordQuickFilterGridChip(
                            item = item,
                            params = params,
                            onMeasured = { size ->
                                if (params.measuredSizes[item] != size) {
                                    params.measuredSizes[item] = size
                                }
                            }
                        )
                    }
                }
            }

            draggingItem?.let { item ->
                val placeholderOffset = targetOffsets[item] ?: dragSnapshotOffsets[item] ?: Offset.Zero
                val placeholderSize = params.itemSize(item, metrics)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                placeholderOffset.x.roundToInt(),
                                placeholderOffset.y.roundToInt()
                            )
                        }
                        .requiredSize(
                            width = with(density) { placeholderSize.width.toDp() },
                            height = with(density) { placeholderSize.height.toDp() }
                        )
                        .alpha(0.22f)
                        .zIndex(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    PasswordQuickFilterGridChip(item = item, params = params)
                }

                val overlayOffset = dragPointerPosition - dragTouchOffset
                val overlaySize = params.itemSize(item, metrics)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                overlayOffset.x.roundToInt(),
                                overlayOffset.y.roundToInt()
                            )
                        }
                        .requiredSize(
                            width = with(density) { overlaySize.width.toDp() },
                            height = with(density) { overlaySize.height.toDp() }
                        )
                        .zIndex(2f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    PasswordQuickFilterGridChip(item = item, params = params)
                }
            }
        }
    }
}
