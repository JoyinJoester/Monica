package takagi.ru.monica.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.data.model.TimelineBranch
import takagi.ru.monica.data.model.TimelineEvent
import takagi.ru.monica.ui.components.DiffComparisonSheet
import takagi.ru.monica.ui.components.formatRelativeTime
import takagi.ru.monica.ui.components.formatShortTime
import takagi.ru.monica.viewmodel.TimelineViewModel
import takagi.ru.monica.ui.components.TrashSettingsSheet
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.TrashViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 历史/回收站 Tab 枚举
 */
enum class HistoryTab {
    TIMELINE,  // 操作历史
    TRASH      // 回收站
}

/**
 * 聚合后的时间线组 - 用于按日期和类型聚合
 */
data class TimelineGroup(
    val dateLabel: String,        // 日期标签：今天、昨天、本周等
    val items: List<TimelineDisplayItem>  // 组内的条目
)

/**
 * 时间线显示条目 - 可以是单个事件或聚合事件
 */
sealed class TimelineDisplayItem {
    data class Single(val event: TimelineEvent.StandardLog) : TimelineDisplayItem()
    data class Aggregated(
        val operationType: String,
        val itemType: String,
        val events: List<TimelineEvent.StandardLog>,
        val firstTimestamp: Long,
        val lastTimestamp: Long
    ) : TimelineDisplayItem()
    data class Conflict(val event: TimelineEvent.ConflictBranch) : TimelineDisplayItem()
}

/**
 * 时间线主屏幕 - 高级现代设计
 */
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = viewModel(),
    trashViewModel: TrashViewModel = viewModel(),
    onLogSelected: (TimelineEvent.StandardLog) -> Unit = {},
    splitPaneMode: Boolean = false,
    showBackButton: Boolean = false,
    onNavigateBack: () -> Unit = {}
) {
    if (splitPaneMode) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                SplitPaneHeader(
                    title = stringResource(R.string.timeline_title),
                    subtitle = stringResource(R.string.timeline_subtitle),
                    icon = Icons.Default.History
                )
                TimelineContent(
                    viewModel = viewModel,
                    onLogSelected = onLogSelected
                )
            }

            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                SplitPaneHeader(
                    title = stringResource(R.string.timeline_trash_title),
                    subtitle = stringResource(R.string.timeline_trash_subtitle),
                    icon = Icons.Default.Delete
                )
                TrashContent(
                    viewModel = trashViewModel,
                    embeddedInSplitPane = true
                )
            }
        }
        return
    }

    var currentTab by rememberSaveable { mutableStateOf(HistoryTab.TIMELINE) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // M3E 风格的顶部标题栏
        HistoryTopBar(
            currentTab = currentTab,
            onTabSelected = { currentTab = it },
            showBackButton = showBackButton,
            onNavigateBack = onNavigateBack
        )
        
        // 内容区域，带有切换动画
        AnimatedContent(
            targetState = currentTab,
            label = "HistoryTabContent",
            transitionSpec = {
                (fadeIn(animationSpec = tween(300))).togetherWith(fadeOut(animationSpec = tween(300)))
            },
            modifier = Modifier.weight(1f)
        ) { targetTab ->
            when (targetTab) {
                HistoryTab.TIMELINE -> TimelineContent(
                    viewModel = viewModel,
                    onLogSelected = onLogSelected
                )
                HistoryTab.TRASH -> TrashContent(viewModel = trashViewModel)
            }
        }
    }
}

@Composable
private fun SplitPaneHeader(
    title: String,
    icon: ImageVector,
    subtitle: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    }
}

/**
 * 历史页面顶栏 - 精致的玻璃态设计
 */
@Composable
private fun HistoryTopBar(
    currentTab: HistoryTab,
    onTabSelected: (HistoryTab) -> Unit,
    showBackButton: Boolean,
    onNavigateBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }

            // 右侧胶囊形切换器 - 更精致的设计
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    HistoryPillTabItem(
                        selected = currentTab == HistoryTab.TIMELINE,
                        onClick = { onTabSelected(HistoryTab.TIMELINE) },
                        icon = Icons.Default.History,
                        contentDescription = stringResource(R.string.timeline_subtitle)
                    )
                    HistoryPillTabItem(
                        selected = currentTab == HistoryTab.TRASH,
                        onClick = { onTabSelected(HistoryTab.TRASH) },
                        icon = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.timeline_trash_title)
                    )
                }
            }
        }
    }
}

/**
 * 胶囊形 Tab 项 - 更精致的设计
 */
@Composable
private fun HistoryPillTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String
) {
    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor = if (selected) colorScheme.primary else Color.Transparent
    val contentColor = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 将时间线事件按日期分组并聚合相同类型的连续操作
 */
@Composable
private fun groupAndAggregateEvents(events: List<TimelineEvent>): List<TimelineGroup> {
    if (events.isEmpty()) return emptyList()
    
    val calendar = Calendar.getInstance()
    val today = calendar.apply { 
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    
    val yesterday = today - 24 * 60 * 60 * 1000
    val thisWeekStart = today - calendar.get(Calendar.DAY_OF_WEEK) * 24 * 60 * 60 * 1000L
    
    // 按日期分组
    val groupedByDate = events.groupBy { event ->
        val timestamp = when (event) {
            is TimelineEvent.StandardLog -> event.timestamp
            is TimelineEvent.ConflictBranch -> event.ancestor.timestamp
        }
        when {
            timestamp >= today -> stringResource(R.string.timeline_date_today)
            timestamp >= yesterday -> stringResource(R.string.timeline_date_yesterday)
            timestamp >= thisWeekStart -> stringResource(R.string.timeline_date_this_week)
            else -> {
                val sdf = SimpleDateFormat(stringResource(R.string.timeline_date_month_day), Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }
    
    return groupedByDate.map { (dateLabel, dateEvents) ->
        val displayItems = mutableListOf<TimelineDisplayItem>()
        var i = 0
        
        while (i < dateEvents.size) {
            val event = dateEvents[i]
            
            when (event) {
                is TimelineEvent.ConflictBranch -> {
                    displayItems.add(TimelineDisplayItem.Conflict(event))
                    i++
                }
                is TimelineEvent.StandardLog -> {
                    // 查找连续的相同类型操作（用于聚合）
                    val sameTypeEvents = mutableListOf(event)
                    var j = i + 1
                    
                    // 聚合 WebDAV 同步操作、新建操作或连续的相同操作类型
                    val shouldAggregate = event.itemType in listOf("WEBDAV_UPLOAD", "WEBDAV_DOWNLOAD") ||
                            event.operationType == "SYNC" ||
                            event.operationType == "CREATE"
                    
                    if (shouldAggregate) {
                        while (j < dateEvents.size) {
                            val nextEvent = dateEvents[j]
                            if (nextEvent is TimelineEvent.StandardLog &&
                                nextEvent.operationType == event.operationType &&
                                nextEvent.itemType == event.itemType
                            ) {
                                sameTypeEvents.add(nextEvent)
                                j++
                            } else {
                                break
                            }
                        }
                    }
                    
                    if (sameTypeEvents.size >= 3) {
                        // 聚合显示
                        displayItems.add(
                            TimelineDisplayItem.Aggregated(
                                operationType = event.operationType,
                                itemType = event.itemType,
                                events = sameTypeEvents,
                                firstTimestamp = sameTypeEvents.last().timestamp,
                                lastTimestamp = sameTypeEvents.first().timestamp
                            )
                        )
                        i = j
                    } else {
                        // 单独显示
                        displayItems.add(TimelineDisplayItem.Single(event))
                        i++
                    }
                }
            }
        }
        
        TimelineGroup(dateLabel = dateLabel, items = displayItems)
    }
}

/**
 * 操作历史内容 - 全新的高级设计
 */
@Composable
private fun TimelineContent(
    viewModel: TimelineViewModel,
    onLogSelected: (TimelineEvent.StandardLog) -> Unit
) {
    val timelineEvents by viewModel.timelineEvents.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var selectedBranch by remember { mutableStateOf<TimelineBranch?>(null) }
    var selectedLog by remember { mutableStateOf<TimelineEvent.StandardLog?>(null) }
    
    val colorScheme = MaterialTheme.colorScheme
    val groups = groupAndAggregateEvents(timelineEvents)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        if (timelineEvents.isEmpty()) {
            EmptyTimelineState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = 100.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                groups.forEach { group ->
                    // 日期分组标题
                    item(key = "header_${group.dateLabel}") {
                        DateSectionHeader(dateLabel = group.dateLabel)
                    }
                    
                    // 组内条目
                    items(
                        items = group.items,
                        key = { item ->
                            when (item) {
                                is TimelineDisplayItem.Single -> "single_${item.event.id}"
                                is TimelineDisplayItem.Aggregated -> "agg_${item.itemType}_${item.firstTimestamp}"
                                is TimelineDisplayItem.Conflict -> "conflict_${item.event.ancestor.id}"
                            }
                        }
                    ) { item ->
                        when (item) {
                            is TimelineDisplayItem.Single -> {
                                ModernLogItem(
                                    log = item.event,
                                    onClick = {
                                        selectedLog = item.event
                                        onLogSelected(item.event)
                                    }
                                )
                            }
                            is TimelineDisplayItem.Aggregated -> {
                                AggregatedLogItem(
                                    aggregated = item,
                                    onItemClick = {
                                        selectedLog = it
                                        onLogSelected(it)
                                    }
                                )
                            }
                            is TimelineDisplayItem.Conflict -> {
                                ConflictBranchItem(
                                    conflict = item.event,
                                    isFirst = false,
                                    isLast = false,
                                    onBranchClick = { selectedBranch = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Diff 比较底部弹窗
    selectedBranch?.let { branch ->
        DiffComparisonSheet(
            branch = branch,
            onDismiss = { selectedBranch = null },
            onRestoreVersion = {
                viewModel.restoreVersion(branch)
                selectedBranch = null
            },
            onSaveAsNewEntry = {
                viewModel.saveAsNewEntry(branch)
                selectedBranch = null
            }
        )
    }

    selectedLog?.let { log ->
        StandardLogDetailSheet(
            log = log,
            onDismiss = { selectedLog = null },
            onRevert = { 
                viewModel.revertEdit(log) { success ->
                    if (success) {
                        selectedLog = null
                    }
                }
            },
            onSaveOldAsNew = {
                viewModel.saveOldDataAsNew(log) { success ->
                    if (success) {
                        selectedLog = null
                    }
                }
            }
        )
    }
}

/**
 * 日期分组标题 - 简洁现代风格
 */
@Composable
private fun DateSectionHeader(dateLabel: String) {
    val colorScheme = MaterialTheme.colorScheme
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.primary,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = colorScheme.outlineVariant.copy(alpha = 0.5f),
            thickness = 1.dp
        )
    }
}

/**
 * 空状态显示 - 更现代的设计
 */
@Composable
private fun EmptyTimelineState() {
    val colorScheme = MaterialTheme.colorScheme
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // 渐变背景的图标容器
            Surface(
                shape = CircleShape,
                color = colorScheme.primaryContainer.copy(alpha = 0.3f),
                modifier = Modifier.size(100.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = stringResource(R.string.no_history_records),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.timeline_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 获取操作类型的图标
 */
@Composable
private fun getOperationIcon(operationType: String, itemType: String): ImageVector {
    // WebDAV 类型
    if (itemType == "WEBDAV_UPLOAD" || itemType == "WEBDAV_DOWNLOAD") {
        return Icons.Default.Cloud
    }
    return when (operationType) {
        "CREATE" -> Icons.Default.Add
        "UPDATE" -> Icons.Default.Edit
        "DELETE" -> Icons.Default.Delete
        "SYNC" -> Icons.Default.Sync
        else -> Icons.Default.History
    }
}

/**
 * 获取项目类型的图标
 */
@Composable
private fun getItemTypeIcon(itemType: String): ImageVector {
    return when (itemType) {
        "PASSWORD" -> Icons.Default.Key
        "TOTP" -> Icons.Default.History
        "BANK_CARD" -> Icons.Default.CreditCard
        "NOTE" -> Icons.Default.Note
        "DOCUMENT" -> Icons.Default.Description
        "WEBDAV_UPLOAD", "WEBDAV_DOWNLOAD" -> Icons.Default.CloudUpload
        else -> Icons.Default.Description
    }
}

/**
 * 获取操作类型的显示文本
 */
@Composable
private fun getOperationLabel(operationType: String): String {
    return when (operationType) {
        "CREATE" -> stringResource(R.string.timeline_op_create)
        "UPDATE" -> stringResource(R.string.timeline_op_update)
        "DELETE" -> stringResource(R.string.timeline_op_delete)
        "SYNC" -> stringResource(R.string.timeline_op_sync)
        else -> stringResource(R.string.timeline_op_default)
    }
}

/**
 * 获取项目类型的显示文本
 */
@Composable
private fun getItemTypeLabel(itemType: String): String {
    return when (itemType) {
        "PASSWORD" -> stringResource(R.string.timeline_item_password)
        "TOTP" -> stringResource(R.string.timeline_item_authenticator)
        "BANK_CARD" -> stringResource(R.string.timeline_item_card)
        "NOTE" -> stringResource(R.string.timeline_item_note)
        "DOCUMENT" -> stringResource(R.string.timeline_item_document)
        "WEBDAV_UPLOAD" -> stringResource(R.string.timeline_item_cloud_backup)
        "WEBDAV_DOWNLOAD" -> stringResource(R.string.timeline_item_cloud_restore)
        else -> stringResource(R.string.timeline_item_default)
    }
}

/**
 * 获取操作的渐变颜色
 */
@Composable
private fun getOperationGradient(operationType: String, itemType: String): Brush {
    val colorScheme = MaterialTheme.colorScheme
    
    if (itemType == "WEBDAV_UPLOAD" || itemType == "WEBDAV_DOWNLOAD") {
        return Brush.linearGradient(
            colors = listOf(
                colorScheme.tertiary.copy(alpha = 0.8f),
                colorScheme.tertiary.copy(alpha = 0.5f)
            )
        )
    }
    
    return when (operationType) {
        "CREATE" -> Brush.linearGradient(
            colors = listOf(
                colorScheme.primary.copy(alpha = 0.8f),
                colorScheme.primary.copy(alpha = 0.5f)
            )
        )
        "UPDATE" -> Brush.linearGradient(
            colors = listOf(
                colorScheme.secondary.copy(alpha = 0.8f),
                colorScheme.secondary.copy(alpha = 0.5f)
            )
        )
        "DELETE" -> Brush.linearGradient(
            colors = listOf(
                colorScheme.error.copy(alpha = 0.8f),
                colorScheme.error.copy(alpha = 0.5f)
            )
        )
        else -> Brush.linearGradient(
            colors = listOf(
                colorScheme.tertiary.copy(alpha = 0.8f),
                colorScheme.tertiary.copy(alpha = 0.5f)
            )
        )
    }
}

/**
 * 现代风格的日志条目 - 卡片式设计
 */
@Composable
private fun ModernLogItem(
    log: TimelineEvent.StandardLog,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val icon = getOperationIcon(log.operationType, log.itemType)
    val gradient = getOperationGradient(log.operationType, log.itemType)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 左侧渐变图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            // 中间内容
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 标题行
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = log.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    // 已恢复标签
                    if (log.isReverted) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.timeline_reverted),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                
                // 操作类型和时间
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.timeline_log_meta,
                            getOperationLabel(log.operationType),
                            getItemTypeLabel(log.itemType)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
            
            // 右侧时间
            Text(
                text = formatShortTime(log.timestamp),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 聚合日志条目 - 可展开的卡片
 */
@Composable
private fun AggregatedLogItem(
    aggregated: TimelineDisplayItem.Aggregated,
    onItemClick: (TimelineEvent.StandardLog) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var isExpanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(200),
        label = "rotation"
    )
    
    val gradient = getOperationGradient(aggregated.operationType, aggregated.itemType)
    val icon = getOperationIcon(aggregated.operationType, aggregated.itemType)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // 主行 - 可点击展开
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 左侧渐变图标 - 带数量角标
                Box(
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(gradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    // 数量角标
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp),
                        shape = CircleShape,
                        color = colorScheme.primary
                    ) {
                        Text(
                            text = "${aggregated.events.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // 中间内容
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.timeline_aggregated_summary,
                            getOperationLabel(aggregated.operationType),
                            aggregated.events.size,
                            getItemTypeLabel(aggregated.itemType)
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.timeline_tap_expand_for_details),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                
                // 展开按钮
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotationAngle)
                )
            }
            
            // 展开后的内容
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(150)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(100))
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(
                        color = colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    aggregated.events.forEach { event ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onItemClick(event) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(colorScheme.primary, CircleShape)
                                )
                                Text(
                                    text = event.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = formatShortTime(event.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 日志详情底部弹窗 - 更精致的设计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StandardLogDetailSheet(
    log: TimelineEvent.StandardLog,
    onDismiss: () -> Unit,
    onRevert: () -> Unit = {},
    onSaveOldAsNew: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    
    var passwordVisible by remember { mutableStateOf(false) }
    
    val isUpdateOperation = log.operationType == "UPDATE"
    val hasOldValues = log.changes.any { it.oldValue.isNotBlank() }
    val isBatchOperation = log.changes.any {
        it.fieldName == stringResource(R.string.timeline_field_batch_move) ||
            it.fieldName == stringResource(R.string.timeline_field_batch_copy)
    }
    val gradient = getOperationGradient(log.operationType, log.itemType)
    val icon = getOperationIcon(log.operationType, log.itemType)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部图标和标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 渐变图标
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(gradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = log.summary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (log.isReverted) {
                            Surface(
                                color = colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.timeline_reverted),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = stringResource(
                            R.string.timeline_log_meta_with_time,
                            getOperationLabel(log.operationType),
                            getItemTypeLabel(log.itemType),
                            formatTimestamp(log.timestamp)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 分隔线
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            // 变更详情
            if (log.changes.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.no_changes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // 变更列表
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.timeline_change_details),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface
                    )
                    
                    log.changes.forEach { change ->
                        val isRealPasswordField = (change.fieldName == stringResource(R.string.timeline_field_password) || change.fieldName == stringResource(R.string.password)) &&
                            !change.newValue.endsWith(stringResource(R.string.timeline_count_suffix_items))
                        
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = change.fieldName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    if (isRealPasswordField) {
                                        val displayValue = if (passwordVisible) {
                                            if (change.oldValue.isNotBlank()) {
                                                "${change.oldValue} → ${change.newValue}"
                                            } else {
                                                change.newValue
                                            }
                                        } else {
                                            if (change.oldValue.isNotBlank()) {
                                                "●●●●●● → ●●●●●●"
                                            } else {
                                                "●●●●●●"
                                            }
                                        }
                                        Text(
                                            text = displayValue,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colorScheme.onSurface
                                        )
                                    } else {
                                        if (change.oldValue.isNotBlank()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = change.oldValue,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(1f, fill = false),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "→",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = colorScheme.primary
                                                )
                                                Text(
                                                    text = change.newValue,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f, fill = false),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = change.newValue,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                                
                                if (isRealPasswordField) {
                                    IconButton(
                                        onClick = { passwordVisible = !passwordVisible },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = if (passwordVisible) stringResource(R.string.hide_password) else stringResource(R.string.show_password),
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 操作按钮
            if (isUpdateOperation && hasOldValues && (!isBatchOperation || !log.isReverted)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRevert,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primaryContainer,
                            contentColor = colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                isBatchOperation -> stringResource(R.string.timeline_undo_operation)
                                log.isReverted -> stringResource(R.string.timeline_restore_to_after_edit)
                                else -> stringResource(R.string.timeline_restore_to_before_edit)
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    if (!log.isReverted && !isBatchOperation) {
                        OutlinedButton(
                            onClick = onSaveOldAsNew,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.timeline_save_old_as_new))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * 冲突分支项 UI
 */
@Composable
private fun ConflictBranchItem(
    conflict: TimelineEvent.ConflictBranch,
    isFirst: Boolean,
    isLast: Boolean,
    onBranchClick: (TimelineBranch) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val primaryColor = colorScheme.primary
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Ancestor 节点
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            TimelineAxis(
                showTopLine = !isFirst,
                showBottomLine = true
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Card(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.sync_conflict),
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = conflict.ancestor.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = formatRelativeTime(conflict.ancestor.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // 分支区域 - Canvas 绘制贝塞尔曲线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                val startX = size.width / 2f
                val startY = 0f
                val branchCount = conflict.branches.size
                
                if (branchCount > 0) {
                    val spacing = size.width / (branchCount + 1)
                    
                    conflict.branches.forEachIndexed { index, _ ->
                        val endX = spacing * (index + 1)
                        val endY = size.height
                        val controlY = size.height * 0.5f
                        
                        val path = Path().apply {
                            moveTo(startX, startY)
                            cubicTo(
                                startX, controlY,
                                endX, controlY,
                                endX, endY
                            )
                        }
                        
                        drawPath(
                            path = path,
                            color = primaryColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(10f, 10f),
                                    0f
                                ),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }
            }
        }
        
        // 分支卡片
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            conflict.branches.forEach { branch ->
                BranchCard(
                    branch = branch,
                    modifier = Modifier.weight(1f),
                    onClick = { onBranchClick(branch) }
                )
            }
        }
        
        // 底部时间线延续
        if (!isLast) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                TimelineAxis(
                    showTopLine = true,
                    showBottomLine = true,
                    showNode = false
                )
            }
        }
    }
}

/**
 * 分支卡片
 */
@Composable
private fun BranchCard(
    branch: TimelineBranch,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (branch.deviceName.contains("PC", ignoreCase = true) || 
                                       branch.deviceName.contains("Windows", ignoreCase = true) ||
                                       branch.deviceName.contains("Mac", ignoreCase = true)) {
                        Icons.Default.Computer
                    } else {
                        Icons.Default.Smartphone
                    },
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = branch.deviceName,
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (branch.changes.isNotEmpty()) {
                val firstChange = branch.changes.first()
                Text(
                    text = stringResource(R.string.modified_field, firstChange.fieldName),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = formatShortTime(branch.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 时间线轴组件
 */
@Composable
private fun TimelineAxis(
    showTopLine: Boolean = true,
    showBottomLine: Boolean = true,
    showNode: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    val lineColor = colorScheme.outline
    val nodeColor = colorScheme.primary
    
    Box(
        modifier = Modifier
            .width(24.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showTopLine) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(lineColor)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            
            if (showNode) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(nodeColor, CircleShape)
                )
            }
            
            if (showBottomLine) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(lineColor)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// ================== 回收站相关组件 ==================

/**
 * 回收站内容 - 简化版设计，直接显示所有条目
 */
@Composable
private fun TrashContent(
    viewModel: TrashViewModel,
    embeddedInSplitPane: Boolean = false
) {
    val trashCategories by viewModel.trashCategories.collectAsState()
    val trashSettings by viewModel.trashSettings.collectAsState()
    val totalCount by viewModel.totalTrashCount.collectAsState()
    
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<takagi.ru.monica.viewmodel.TrashItem?>(null) }
    
    // 多选模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<String>()) }
    
    val colorScheme = MaterialTheme.colorScheme
    
    // 扁平化所有条目，按删除时间排序
    val allItems = remember(trashCategories) {
        trashCategories.flatMap { it.items }.sortedByDescending { it.deletedAt.time }
    }
    
    // 选择/取消选择条目
    fun toggleItemSelection(item: takagi.ru.monica.viewmodel.TrashItem) {
        val key = "${item.itemType.name}_${item.id}"
        selectedItems = if (selectedItems.contains(key)) {
            selectedItems - key
        } else {
            selectedItems + key
        }
        // 如果取消选择后没有选中项，退出选择模式
        if (selectedItems.isEmpty()) {
            isSelectionMode = false
        }
    }
    
    fun isItemSelected(item: takagi.ru.monica.viewmodel.TrashItem): Boolean {
        return selectedItems.contains("${item.itemType.name}_${item.id}")
    }
    
    fun toggleSelectAll() {
        if (selectedItems.size == allItems.size) {
            selectedItems = emptySet()
            isSelectionMode = false
        } else {
            selectedItems = allItems.map { "${it.itemType.name}_${it.id}" }.toSet()
        }
    }
    
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems = emptySet()
    }
    
    fun restoreSelectedItems() {
        val itemsToRestore = allItems.filter { isItemSelected(it) }
        itemsToRestore.forEach { item ->
            viewModel.restoreItem(item) { _ -> }
        }
        exitSelectionMode()
    }
    
    fun deleteSelectedItems() {
        val itemsToDelete = allItems.filter { isItemSelected(it) }
        itemsToDelete.forEach { item ->
            viewModel.permanentlyDeleteItem(item) { _ -> }
        }
        exitSelectionMode()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        if (!trashSettings.enabled) {
            TrashDisabledView(onEnableClick = { showSettingsDialog = true })
        } else if (allItems.isEmpty()) {
            TrashEmptyView()
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部信息栏
                TrashHeaderBar(
                    totalCount = totalCount,
                    autoDeleteDays = trashSettings.autoDeleteDays,
                    isSelectionMode = isSelectionMode,
                    selectedCount = selectedItems.size,
                    embeddedInSplitPane = embeddedInSplitPane,
                    onSettingsClick = { showSettingsDialog = true },
                    onEmptyTrashClick = { showEmptyTrashDialog = true },
                    onSelectAll = { toggleSelectAll() },
                    onExitSelection = { exitSelectionMode() }
                )
                
                // 条目列表 - 直接平铺显示
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = if (isSelectionMode) 100.dp else 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = allItems,
                        key = { "${it.itemType.name}_${it.id}" }
                    ) { item ->
                        TrashItemCard(
                            item = item,
                            isSelectionMode = isSelectionMode,
                            isSelected = isItemSelected(item),
                            onClick = {
                                if (isSelectionMode) {
                                    toggleItemSelection(item)
                                } else {
                                    selectedItem = item
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    toggleItemSelection(item)
                                }
                            },
                            onRestore = {
                                viewModel.restoreItem(item) { _ -> }
                            }
                        )
                    }
                }
            }
        }
        
        // 底部浮动操作栏（选择模式）
        if (isSelectionMode && selectedItems.isNotEmpty()) {
            TrashSelectionBar(
                selectedCount = selectedItems.size,
                onRestore = { restoreSelectedItems() },
                onDelete = { deleteSelectedItems() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
    }
    
    // 回收站设置对话框
    if (showSettingsDialog) {
        TrashSettingsSheet(
            currentSettings = trashSettings,
            onDismiss = { showSettingsDialog = false },
            onConfirm = { enabled, days ->
                viewModel.updateTrashSettings(enabled, days)
                showSettingsDialog = false
            }
        )
    }
    
    // 清空回收站确认对话框
    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            title = { Text(stringResource(R.string.timeline_empty_trash_title)) },
            text = { Text(stringResource(R.string.timeline_empty_trash_message, totalCount)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.emptyTrash { success ->
                            showEmptyTrashDialog = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.error)
                ) {
                    Text(stringResource(R.string.timeline_empty_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // 条目操作弹窗
    selectedItem?.let { item ->
        TrashItemActionSheet(
            item = item,
            onDismiss = { selectedItem = null },
            onRestore = {
                viewModel.restoreItem(item) { _ -> selectedItem = null }
            },
            onPermanentDelete = {
                viewModel.permanentlyDeleteItem(item) { _ -> selectedItem = null }
            }
        )
    }
}

/**
 * 回收站顶部信息栏
 */
@Composable
private fun TrashHeaderBar(
    totalCount: Int,
    autoDeleteDays: Int,
    isSelectionMode: Boolean,
    selectedCount: Int,
    embeddedInSplitPane: Boolean,
    onSettingsClick: () -> Unit,
    onEmptyTrashClick: () -> Unit,
    onSelectAll: () -> Unit,
    onExitSelection: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = if (embeddedInSplitPane) 8.dp else 12.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            // 选择模式
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onExitSelection) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.timeline_exit_selection))
                }
                Text(
                    text = stringResource(R.string.selected_items, selectedCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            TextButton(onClick = onSelectAll) {
                Text(stringResource(R.string.select_all))
            }
        } else {
            // 普通模式
            Column {
                Text(
                    text = stringResource(R.string.timeline_deleted_items_count, totalCount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )
                Text(
                    text = if (autoDeleteDays > 0) stringResource(R.string.timeline_auto_clear_in_days, autoDeleteDays) else stringResource(R.string.timeline_auto_clear_never),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = colorScheme.onSurfaceVariant
                    )
                }
                if (totalCount > 0) {
                    IconButton(onClick = onEmptyTrashClick) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = stringResource(R.string.timeline_empty_action),
                            tint = colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * 回收站条目卡片 - 简洁直观的设计
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashItemCard(
    item: takagi.ru.monica.viewmodel.TrashItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRestore: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    
    // 根据类型获取图标和颜色
    val (icon, iconColor) = when (item.itemType) {
        takagi.ru.monica.data.ItemType.PASSWORD -> Icons.Default.Key to colorScheme.primary
        takagi.ru.monica.data.ItemType.TOTP -> Icons.Default.History to colorScheme.secondary
        takagi.ru.monica.data.ItemType.BANK_CARD -> Icons.Default.CreditCard to colorScheme.tertiary
        takagi.ru.monica.data.ItemType.DOCUMENT -> Icons.Default.Description to colorScheme.error
        takagi.ru.monica.data.ItemType.NOTE -> Icons.Default.Note to colorScheme.outline
    }
    
    val typeLabel = when (item.itemType) {
        takagi.ru.monica.data.ItemType.PASSWORD -> stringResource(R.string.item_type_password)
        takagi.ru.monica.data.ItemType.TOTP -> stringResource(R.string.item_type_authenticator)
        takagi.ru.monica.data.ItemType.BANK_CARD -> stringResource(R.string.item_type_bank_card)
        takagi.ru.monica.data.ItemType.DOCUMENT -> stringResource(R.string.item_type_document)
        takagi.ru.monica.data.ItemType.NOTE -> stringResource(R.string.timeline_item_note)
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                colorScheme.primaryContainer.copy(alpha = 0.5f) 
            else 
                colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 选择模式下显示复选框，否则显示类型图标
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.size(24.dp)
                )
            } else {
                // 类型图标
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            // 内容
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 类型标签
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = iconColor
                    )
                        Text(
                        text = stringResource(R.string.timeline_dot_separator),
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    // 删除时间
                    Text(
                        text = dateFormat.format(item.deletedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    // 剩余天数警告
                    if (item.daysRemaining in 0..3) {
                        Text(
                            text = stringResource(R.string.timeline_dot_separator),
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = if (item.daysRemaining == 0) {
                                stringResource(R.string.timeline_clear_today)
                            } else {
                                stringResource(R.string.timeline_clear_after_days_short, item.daysRemaining)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // 非选择模式下显示恢复按钮
            if (!isSelectionMode) {
                FilledTonalIconButton(
                    onClick = onRestore,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colorScheme.primaryContainer,
                        contentColor = colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = stringResource(R.string.timeline_restore_this_item),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 底部选择操作栏 - 简洁版
 */
@Composable
private fun TrashSelectionBar(
    selectedCount: Int,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = colorScheme.primaryContainer,
        shadowElevation = 8.dp,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 恢复按钮
            FilledTonalButton(
                onClick = onRestore,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.timeline_restore_selected, selectedCount))
            }
            
            // 删除按钮
            TextButton(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.delete))
            }
        }
    }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.timeline_permanent_delete_title)) },
            text = { Text(stringResource(R.string.timeline_permanent_delete_selected_message, selectedCount)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 条目操作底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashItemActionSheet(
    item: takagi.ru.monica.viewmodel.TrashItem,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    
    val (icon, iconColor) = when (item.itemType) {
        takagi.ru.monica.data.ItemType.PASSWORD -> Icons.Default.Key to colorScheme.primary
        takagi.ru.monica.data.ItemType.TOTP -> Icons.Default.History to colorScheme.secondary
        takagi.ru.monica.data.ItemType.BANK_CARD -> Icons.Default.CreditCard to colorScheme.tertiary
        takagi.ru.monica.data.ItemType.DOCUMENT -> Icons.Default.Description to colorScheme.error
        takagi.ru.monica.data.ItemType.NOTE -> Icons.Default.Note to colorScheme.outline
    }
    
    val typeLabel = when (item.itemType) {
        takagi.ru.monica.data.ItemType.PASSWORD -> stringResource(R.string.item_type_password)
        takagi.ru.monica.data.ItemType.TOTP -> stringResource(R.string.item_type_authenticator)
        takagi.ru.monica.data.ItemType.BANK_CARD -> stringResource(R.string.item_type_bank_card)
        takagi.ru.monica.data.ItemType.DOCUMENT -> stringResource(R.string.item_type_document)
        takagi.ru.monica.data.ItemType.NOTE -> stringResource(R.string.timeline_item_note)
    }
    
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.timeline_deleted_at_with_type, typeLabel, dateFormat.format(item.deletedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 剩余天数提示
            if (item.daysRemaining >= 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (item.daysRemaining <= 3) 
                        colorScheme.errorContainer.copy(alpha = 0.5f) 
                    else 
                        colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = if (item.daysRemaining <= 3) colorScheme.error else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = when {
                                item.daysRemaining == 0 -> stringResource(R.string.timeline_auto_permanent_delete_today)
                                item.daysRemaining <= 3 -> stringResource(R.string.timeline_auto_permanent_delete_in_days, item.daysRemaining)
                                else -> stringResource(R.string.timeline_auto_clear_in_days, item.daysRemaining)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.daysRemaining <= 3) colorScheme.error else colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            // 操作按钮
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 恢复按钮
                Button(
                    onClick = onRestore,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.timeline_restore_this_item), fontWeight = FontWeight.Medium)
                }
                
                // 永久删除按钮
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.error),
                    border = BorderStroke(1.dp, colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.timeline_permanent_delete_title))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // 删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = colorScheme.error) },
            title = { Text(stringResource(R.string.timeline_permanent_delete_title)) },
            text = { Text(stringResource(R.string.timeline_permanent_delete_item_message, item.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onPermanentDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 回收站未启用视图
 */
@Composable
private fun TrashDisabledView(
    onEnableClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.timeline_trash_disabled_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.timeline_trash_disabled_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        FilledTonalButton(onClick = onEnableClick) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.settings))
        }
    }
}

/**
 * 回收站为空视图
 */
@Composable
private fun TrashEmptyView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.timeline_trash_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.timeline_trash_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

