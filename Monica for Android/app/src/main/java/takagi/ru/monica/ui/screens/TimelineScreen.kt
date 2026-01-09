package takagi.ru.monica.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
 * 时间线主屏幕 - 使用 M3E 主题取色
 * 支持切换 操作历史 和 回收站 视图
 */
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = viewModel(),
    trashViewModel: TrashViewModel = viewModel()
) {
    var currentTab by rememberSaveable { mutableStateOf(HistoryTab.TIMELINE) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // M3E 风格的顶部标题栏
        HistoryTopBar(
            currentTab = currentTab,
            onTabSelected = { currentTab = it }
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
                HistoryTab.TIMELINE -> TimelineContent(viewModel = viewModel)
                HistoryTab.TRASH -> TrashContent(viewModel = trashViewModel)
            }
        }
    }
}

/**
 * 历史页面顶栏 - 类似卡包的胶囊切换器
 */
@Composable
private fun HistoryTopBar(
    currentTab: HistoryTab,
    onTabSelected: (HistoryTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧大标题
        Text(
            text = if (currentTab == HistoryTab.TIMELINE) "历史" else "回收站",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // 右侧胶囊形切换器
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 历史 Tab
                HistoryPillTabItem(
                    selected = currentTab == HistoryTab.TIMELINE,
                    onClick = { onTabSelected(HistoryTab.TIMELINE) },
                    icon = Icons.Default.History,
                    contentDescription = "操作历史"
                )

                // 回收站 Tab
                HistoryPillTabItem(
                    selected = currentTab == HistoryTab.TRASH,
                    onClick = { onTabSelected(HistoryTab.TRASH) },
                    icon = Icons.Default.Delete,
                    contentDescription = "回收站"
                )
            }
        }
    }
}

/**
 * 胶囊形 Tab 项
 */
@Composable
private fun HistoryPillTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = backgroundColor,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
 * 操作历史内容
 */
@Composable
private fun TimelineContent(
    viewModel: TimelineViewModel
) {
    val timelineEvents by viewModel.timelineEvents.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // 底部弹窗状态
    var selectedBranch by remember { mutableStateOf<TimelineBranch?>(null) }
    var selectedLog by remember { mutableStateOf<TimelineEvent.StandardLog?>(null) }
    
    // 从 M3E 主题获取颜色
    val colorScheme = MaterialTheme.colorScheme
    
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
                    top = 16.dp,
                    bottom = 32.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(timelineEvents) { event ->
                    TimelineEventItem(
                        event = event,
                        isFirst = event == timelineEvents.first(),
                        isLast = event == timelineEvents.last(),
                        onLogClick = { selectedLog = it },
                        onBranchClick = { branch ->
                            selectedBranch = branch
                        }
                    )
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
 * 空状态显示
 */
@Composable
private fun EmptyTimelineState() {
    val colorScheme = MaterialTheme.colorScheme
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AccountTree,
                contentDescription = null,
                tint = colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.no_history_records),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 时间线事件项
 */
@Composable
private fun TimelineEventItem(
    event: TimelineEvent,
    isFirst: Boolean,
    isLast: Boolean,
    onLogClick: (TimelineEvent.StandardLog) -> Unit,
    onBranchClick: (TimelineBranch) -> Unit
) {
    when (event) {
        is TimelineEvent.StandardLog -> {
            StandardLogItem(
                log = event,
                isFirst = isFirst,
                isLast = isLast,
                onClick = { onLogClick(event) }
            )
        }
        is TimelineEvent.ConflictBranch -> {
            ConflictBranchItem(
                conflict = event,
                isFirst = isFirst,
                isLast = isLast,
                onBranchClick = onBranchClick
            )
        }
    }
}

/**
 * 获取操作类型的显示文本
 */
private fun getOperationLabel(operationType: String): String {
    return when (operationType) {
        "CREATE" -> "创建"
        "UPDATE" -> "编辑"
        "DELETE" -> "删除"
        "SYNC" -> "同步"
        else -> "操作"
    }
}

/**
 * 获取项目类型的显示文本
 */
private fun getItemTypeLabel(itemType: String): String {
    return when (itemType) {
        "PASSWORD" -> "密码"
        "TOTP" -> "验证器"
        "BANK_CARD" -> "卡片"
        "NOTE" -> "笔记"
        "DOCUMENT" -> "证件"
        "WEBDAV_UPLOAD" -> "上传"
        "WEBDAV_DOWNLOAD" -> "下载"
        else -> "项目"
    }
}

/**
 * 获取类别标签的背景色
 */
@Composable
private fun getCategoryColor(operationType: String, itemType: String): Color {
    val colorScheme = MaterialTheme.colorScheme
    // WebDAV 类型使用特殊颜色
    if (itemType == "WEBDAV_UPLOAD" || itemType == "WEBDAV_DOWNLOAD") {
        return colorScheme.tertiaryContainer
    }
    return when (operationType) {
        "CREATE" -> colorScheme.primaryContainer
        "UPDATE" -> colorScheme.secondaryContainer
        "DELETE" -> colorScheme.errorContainer
        "SYNC" -> colorScheme.tertiaryContainer
        else -> colorScheme.tertiaryContainer
    }
}

/**
 * 获取类别标签的文字色
 */
@Composable
private fun getCategoryTextColor(operationType: String, itemType: String): Color {
    val colorScheme = MaterialTheme.colorScheme
    // WebDAV 类型使用特殊颜色
    if (itemType == "WEBDAV_UPLOAD" || itemType == "WEBDAV_DOWNLOAD") {
        return colorScheme.onTertiaryContainer
    }
    return when (operationType) {
        "CREATE" -> colorScheme.onPrimaryContainer
        "UPDATE" -> colorScheme.onSecondaryContainer
        "DELETE" -> colorScheme.onErrorContainer
        "SYNC" -> colorScheme.onTertiaryContainer
        else -> colorScheme.onTertiaryContainer
    }
}

/**
 * 生成类别标签文本
 */
private fun getCategoryLabel(operationType: String, itemType: String): String {
    // WebDAV 类型使用特殊标签格式
    if (itemType == "WEBDAV_UPLOAD" || itemType == "WEBDAV_DOWNLOAD") {
        return "WebDAV"
    }
    return "${getOperationLabel(operationType)}-${getItemTypeLabel(itemType)}"
}

/**
 * 标准日志项 UI
 */
@Composable
private fun StandardLogItem(
    log: TimelineEvent.StandardLog,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val categoryLabel = getCategoryLabel(log.operationType, log.itemType)
    val categoryBgColor = getCategoryColor(log.operationType, log.itemType)
    val categoryTextColor = getCategoryTextColor(log.operationType, log.itemType)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        TimelineAxis(
            showTopLine = !isFirst,
            showBottomLine = !isLast
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp)
                .clickable { onClick() },
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 类别标签（色块）
                Surface(
                    color = categoryBgColor,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = categoryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryTextColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                
                // 已恢复标签
                if (log.isReverted) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "已恢复",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 标题
                Text(
                    text = log.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(6.dp))
                
                // 时间
                Text(
                    text = formatShortTime(log.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

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
    
    // 密码可见性状态
    var passwordVisible by remember { mutableStateOf(false) }
    
    // 是否是编辑操作（只有编辑操作才显示恢复按钮）
    val isUpdateOperation = log.operationType == "UPDATE"
    // 是否有可恢复的旧值
    val hasOldValues = log.changes.any { it.oldValue.isNotBlank() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题行 - 显示恢复状态标签
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = log.summary,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (log.isReverted) {
                    Surface(
                        color = colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "已恢复",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = formatTimestamp(log.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )

            if (log.changes.isEmpty()) {
                Text(
                    text = stringResource(takagi.ru.monica.R.string.no_changes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
            } else {
                // 普通日志展示
                log.changes.forEach { change ->
                    // 只有真正的密码字段才需要隐藏（不是数量统计）
                    val isRealPasswordField = change.fieldName == "密码" && !change.newValue.endsWith("项")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isRealPasswordField) {
                            // 密码字段：支持隐藏/显示切换
                            val hasOldValue = change.oldValue.isNotBlank()
                            val displayText = if (passwordVisible) {
                                if (hasOldValue) {
                                    "${change.fieldName}: ${change.oldValue} → ${change.newValue}"
                                } else {
                                    "${change.fieldName}: ${change.newValue}"
                                }
                            } else {
                                if (hasOldValue) {
                                    "${change.fieldName}: ●●●● → ●●●●"
                                } else {
                                    "${change.fieldName}: ●●●●"
                                }
                            }
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            val displayValue = if (change.oldValue.isBlank()) {
                                change.newValue
                            } else {
                                "${change.oldValue} → ${change.newValue}"
                            }
                            Text(
                                text = "${change.fieldName}: $displayValue",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            
            // 编辑操作的恢复按钮
            if (isUpdateOperation && hasOldValues) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // 恢复/重做按钮
                OutlinedButton(
                    onClick = onRevert,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (log.isReverted) "恢复到编辑后" else "恢复到编辑前"
                    )
                }
                
                // 保存旧数据为新条目按钮（仅在未恢复状态下显示）
                if (!log.isReverted) {
                    OutlinedButton(
                        onClick = onSaveOldAsNew,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "旧数据另存为新条目")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
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
 * 回收站内容
 */
@Composable
private fun TrashContent(
    viewModel: TrashViewModel
) {
    val trashCategories by viewModel.trashCategories.collectAsState()
    val trashSettings by viewModel.trashSettings.collectAsState()
    val totalCount by viewModel.totalTrashCount.collectAsState()
    
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var expandedCategory by remember { mutableStateOf<takagi.ru.monica.data.ItemType?>(null) }
    var selectedItem by remember { mutableStateOf<takagi.ru.monica.viewmodel.TrashItem?>(null) }
    
    // 多选模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<String>()) } // 使用 itemType_itemId 作为唯一标识
    
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    
    // 获取所有条目用于全选
    val allItems = remember(trashCategories) {
        trashCategories.flatMap { it.items }
    }
    
    // 选择/取消选择条目
    fun toggleItemSelection(item: takagi.ru.monica.viewmodel.TrashItem) {
        val key = "${item.itemType.name}_${item.id}"
        selectedItems = if (selectedItems.contains(key)) {
            selectedItems - key
        } else {
            selectedItems + key
        }
    }
    
    // 检查条目是否被选中
    fun isItemSelected(item: takagi.ru.monica.viewmodel.TrashItem): Boolean {
        return selectedItems.contains("${item.itemType.name}_${item.id}")
    }
    
    // 全选/取消全选
    fun toggleSelectAll() {
        if (selectedItems.size == allItems.size) {
            selectedItems = emptySet()
        } else {
            selectedItems = allItems.map { "${it.itemType.name}_${it.id}" }.toSet()
        }
    }
    
    // 退出选择模式
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems = emptySet()
    }
    
    // 批量恢复选中项
    fun restoreSelectedItems() {
        val itemsToRestore = allItems.filter { isItemSelected(it) }
        itemsToRestore.forEach { item ->
            viewModel.restoreItem(item) { _ -> }
        }
        exitSelectionMode()
    }
    
    // 批量删除选中项
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
            // 回收站未启用
            TrashDisabledView(
                onEnableClick = { showSettingsDialog = true }
            )
        } else if (trashCategories.isEmpty()) {
            // 回收站为空
            TrashEmptyView()
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 顶部操作栏（非选择模式下显示）
                TrashActionBar(
                    totalCount = totalCount,
                    autoDeleteDays = trashSettings.autoDeleteDays,
                    onSettingsClick = { showSettingsDialog = true },
                    onEmptyTrashClick = { showEmptyTrashDialog = true }
                )
                
                // 分类文件夹列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = if (isSelectionMode) 80.dp else 16.dp // 为底部操作条留空间
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(trashCategories) { category ->
                        TrashCategoryCard(
                            category = category,
                            isExpanded = expandedCategory == category.type,
                            isSelectionMode = isSelectionMode,
                            selectedItems = selectedItems,
                            onExpandClick = {
                                if (!isSelectionMode) {
                                    expandedCategory = if (expandedCategory == category.type) null else category.type
                                }
                            },
                            onItemClick = { item ->
                                if (isSelectionMode) {
                                    toggleItemSelection(item)
                                } else {
                                    selectedItem = item
                                }
                            },
                            onItemLongClick = { item ->
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    expandedCategory = category.type
                                    toggleItemSelection(item)
                                }
                            },
                            onRestoreCategory = {
                                if (!isSelectionMode) {
                                    viewModel.restoreCategory(category) { success ->
                                        // 可以添加 Toast 提示
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        
        // 底部浮动选择操作条（选择模式下显示）
        if (isSelectionMode && selectedItems.isNotEmpty()) {
            TrashSelectionFloatingBar(
                selectedCount = selectedItems.size,
                onSelectAll = { toggleSelectAll() },
                onRestore = { restoreSelectedItems() },
                onDelete = { deleteSelectedItems() },
                onExitSelection = { exitSelectionMode() },
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
            title = { Text("清空回收站") },
            text = { Text("确定要永久删除回收站中的 $totalCount 个条目吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.emptyTrash { success ->
                            showEmptyTrashDialog = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 条目详情对话框
    selectedItem?.let { item ->
        TrashItemActionDialog(
            item = item,
            onDismiss = { selectedItem = null },
            onRestore = {
                viewModel.restoreItem(item) { success ->
                    selectedItem = null
                }
            },
            onPermanentDelete = {
                viewModel.permanentlyDeleteItem(item) { success ->
                    selectedItem = null
                }
            }
        )
    }
}

/**
 * 底部浮动选择操作条（类似密码页面多选样式）
 */
@Composable
private fun TrashSelectionFloatingBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onExitSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier.height(56.dp),
        shape = CircleShape,
        color = colorScheme.primaryContainer,
        contentColor = colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 选中数量指示器
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = selectedCount.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onPrimary
                )
            }
            
            Spacer(modifier = Modifier.width(4.dp))
            
            // 全选按钮
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "全选"
                )
            }
            
            // 恢复按钮
            IconButton(onClick = onRestore) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "恢复选中项"
                )
            }
            
            // 删除按钮
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除选中项",
                    tint = colorScheme.error
                )
            }
            
            // 退出选择模式
            IconButton(onClick = onExitSelection) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "退出选择"
                )
            }
        }
    }
    
    // 批量删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("永久删除") },
            text = { Text("确定要永久删除选中的 $selectedCount 个条目吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
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
            text = "回收站已禁用",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "启用回收站后，删除的条目会在这里保留一段时间",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        FilledTonalButton(onClick = onEnableClick) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("设置")
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
            text = "回收站为空",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "删除的密码、验证器等会在这里保留",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

/**
 * 回收站操作栏
 */
@Composable
private fun TrashActionBar(
    totalCount: Int,
    autoDeleteDays: Int,
    onSettingsClick: () -> Unit,
    onEmptyTrashClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "$totalCount 个条目",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (autoDeleteDays > 0) {
                Text(
                    text = "${autoDeleteDays}天后自动清空",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Text(
                    text = "不会自动清空",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "回收站设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (totalCount > 0) {
                IconButton(onClick = onEmptyTrashClick) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "清空回收站",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * 回收站分类卡片（文件夹样式）
 */
@Composable
private fun TrashCategoryCard(
    category: takagi.ru.monica.viewmodel.TrashCategory,
    isExpanded: Boolean,
    isSelectionMode: Boolean,
    selectedItems: Set<String>,
    onExpandClick: () -> Unit,
    onItemClick: (takagi.ru.monica.viewmodel.TrashItem) -> Unit,
    onItemLongClick: (takagi.ru.monica.viewmodel.TrashItem) -> Unit,
    onRestoreCategory: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    
    // 根据类型获取颜色和图标
    val (icon, containerColor) = when (category.type) {
        takagi.ru.monica.data.ItemType.PASSWORD -> Icons.Default.Visibility to colorScheme.primaryContainer
        takagi.ru.monica.data.ItemType.TOTP -> Icons.Default.History to colorScheme.secondaryContainer
        takagi.ru.monica.data.ItemType.BANK_CARD -> Icons.Default.AccountTree to colorScheme.tertiaryContainer
        takagi.ru.monica.data.ItemType.DOCUMENT -> Icons.Default.Computer to colorScheme.errorContainer
        takagi.ru.monica.data.ItemType.NOTE -> Icons.Default.Smartphone to colorScheme.surfaceContainerHigh
    }
    
    // 计算该分类中被选中的数量
    val selectedCountInCategory = category.items.count { item ->
        selectedItems.contains("${item.itemType.name}_${item.id}")
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            // 文件夹头部
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpandClick)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 文件夹图标
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = containerColor
                    ) {
                        Box(
                            modifier = Modifier.padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    
                    Column {
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = if (isSelectionMode && selectedCountInCategory > 0) {
                                "已选择 $selectedCountInCategory/${category.count}"
                            } else {
                                "${category.count} 个条目"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelectionMode && selectedCountInCategory > 0) {
                                colorScheme.primary
                            } else {
                                colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 恢复全部按钮（非选择模式下显示）
                    if (!isSelectionMode) {
                        IconButton(onClick = onRestoreCategory) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = "恢复全部",
                                tint = colorScheme.primary
                            )
                        }
                    }
                    
                    // 展开/收起指示器
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isExpanded) "收起" else "展开",
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 展开的条目列表（选择模式下自动展开）
            if (isExpanded || isSelectionMode) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    category.items.forEach { item ->
                        val isSelected = selectedItems.contains("${item.itemType.name}_${item.id}")
                        TrashItemRow(
                            item = item,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongClick(item) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 回收站条目行
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashItemRow(
    item: takagi.ru.monica.viewmodel.TrashItem,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    
    val backgroundColor = if (isSelected) {
        colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 多选模式下显示复选框
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.size(24.dp)
            )
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurface
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "删除于 ${dateFormat.format(item.deletedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                if (item.daysRemaining >= 0) {
                    Text(
                        text = if (item.daysRemaining == 0) "今天清空" else "${item.daysRemaining}天后清空",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.daysRemaining <= 3) colorScheme.error else colorScheme.outline
                    )
                }
            }
        }
        
        // 非选择模式下显示恢复图标
        if (!isSelectionMode) {
            Icon(
                imageVector = Icons.Default.Restore,
                contentDescription = "恢复",
                tint = colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 回收站设置对话框
 */


/**
 * 回收站条目操作对话框
 */
@Composable
private fun TrashItemActionDialog(
    item: takagi.ru.monica.viewmodel.TrashItem,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "类型：${
                        when (item.itemType) {
                            takagi.ru.monica.data.ItemType.PASSWORD -> "密码"
                            takagi.ru.monica.data.ItemType.TOTP -> "验证器"
                            takagi.ru.monica.data.ItemType.BANK_CARD -> "银行卡"
                            takagi.ru.monica.data.ItemType.DOCUMENT -> "证件"
                            takagi.ru.monica.data.ItemType.NOTE -> "笔记"
                        }
                    }",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "删除时间：${dateFormat.format(item.deletedAt)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (item.daysRemaining >= 0) {
                    Text(
                        text = if (item.daysRemaining == 0) "将于今天自动清空" else "${item.daysRemaining}天后自动清空",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.daysRemaining <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRestore) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("恢复")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onPermanentDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("永久删除")
            }
        }
    )
}

