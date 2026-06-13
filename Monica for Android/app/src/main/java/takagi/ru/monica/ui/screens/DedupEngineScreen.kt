package takagi.ru.monica.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.dedup.DedupMergePlan
import takagi.ru.monica.data.dedup.DedupMergeSourceKind
import takagi.ru.monica.data.dedup.DedupMergeSourceOption
import takagi.ru.monica.data.dedup.DedupMergeTarget
import takagi.ru.monica.data.dedup.DedupMergeTargetOption
import takagi.ru.monica.data.dedup.DedupResolvedPassword
import takagi.ru.monica.data.dedup.DedupResolvedSecureItem
import takagi.ru.monica.viewmodel.DedupEngineUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DedupEngineScreen(
    uiState: DedupEngineUiState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleSource: (String) -> Unit,
    onSelectAllSources: () -> Unit,
    onClearSources: () -> Unit,
    onSelectTarget: (DedupMergeTarget) -> Unit,
    onCreateMdbxTarget: () -> Unit,
    onExecuteMerge: () -> Unit,
    onConsumeMessage: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onConsumeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("去重合并", fontWeight = FontWeight.Bold)
                        Text(
                            text = "选择源数据库，预览后写入目标库",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (uiState.isLoading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            item {
                MergeSummaryCard(
                    plan = uiState.mergePlan,
                    isAnalyzing = uiState.isAnalyzing,
                    isExecuting = uiState.isExecutingMerge
                )
            }

            item {
                SourceSelectionCard(
                    sources = uiState.sourceOptions,
                    selectedKeys = uiState.selectedMergeSourceKeys,
                    onToggleSource = onToggleSource,
                    onSelectAllSources = onSelectAllSources,
                    onClearSources = onClearSources
                )
            }

            item {
                TargetSelectionCard(
                    targets = uiState.targetOptions,
                    selectedTarget = uiState.selectedMergeTarget,
                    onSelectTarget = onSelectTarget,
                    onCreateMdbxTarget = onCreateMdbxTarget
                )
            }

            if (uiState.error != null) {
                item { StatusCard(icon = Icons.Default.Warning, tint = MaterialTheme.colorScheme.error, text = uiState.error) }
            }

            if (uiState.mergePlan.warnings.isNotEmpty()) {
                item {
                    WarningListCard(warnings = uiState.mergePlan.warnings)
                }
            }

            item {
                MergeActionCard(
                    plan = uiState.mergePlan,
                    isExecuting = uiState.isExecutingMerge,
                    onExecuteMerge = onExecuteMerge
                )
            }

            item {
                Text(
                    text = "预览",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (uiState.mergePlan.previewPasswords.isEmpty() && uiState.mergePlan.previewSecureItems.isEmpty()) {
                item {
                    StatusCard(
                        icon = Icons.Default.Info,
                        tint = MaterialTheme.colorScheme.primary,
                        text = "选择源数据库和目标数据库后会在这里显示合并预览"
                    )
                }
            } else {
                items(uiState.mergePlan.previewPasswords.take(80), key = { it.mergeKey }) { resolved ->
                    ResolvedPasswordPreviewCard(resolved)
                }
                items(uiState.mergePlan.previewSecureItems.take(80), key = { "secure:${it.mergeKey}" }) { resolved ->
                    ResolvedSecureItemPreviewCard(resolved)
                }
                val hiddenCount = (uiState.mergePlan.previewPasswords.size - 80).coerceAtLeast(0) +
                    (uiState.mergePlan.previewSecureItems.size - 80).coerceAtLeast(0)
                if (hiddenCount > 0) {
                    item {
                        Text(
                            text = "还有 $hiddenCount 条未显示，执行时会一起处理",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MergeSummaryCard(
    plan: DedupMergePlan,
    isAnalyzing: Boolean,
    isExecuting: Boolean
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("合并计划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = when {
                            isExecuting -> "正在写入目标数据库"
                            isAnalyzing -> "正在分析重复项"
                            else -> "源库只读，结果只写入目标库"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isAnalyzing || isExecuting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Merge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryChip("源条目", plan.totalSourceItems.toString())
                SummaryChip("密码", plan.totalSourcePasswords.toString())
                SummaryChip("安全项", plan.totalSourceSecureItems.toString())
                SummaryChip("去重后", (plan.uniquePasswords + plan.uniqueSecureItems).toString())
                SummaryChip("重复组", (plan.duplicateGroups + plan.duplicateSecureItemGroups).toString())
                SummaryChip("将写入", plan.writableItems.toString())
                SummaryChip("跳过", plan.skippedItems.toString())
            }
        }
    }
}

@Composable
private fun SourceSelectionCard(
    sources: List<DedupMergeSourceOption>,
    selectedKeys: Set<String>,
    onToggleSource: (String) -> Unit,
    onSelectAllSources: () -> Unit,
    onClearSources: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(title = "源数据库", subtitle = "可多选，源数据库不会被修改")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSelectAllSources) { Text("全选") }
                TextButton(onClick = onClearSources) { Text("清空") }
            }
            if (sources.isEmpty()) {
                Text("暂无可比对的数据库", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                sources.forEach { source ->
                    SelectableSourceRow(
                        source = source,
                        selected = source.key in selectedKeys,
                        onClick = { onToggleSource(source.key) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetSelectionCard(
    targets: List<DedupMergeTargetOption>,
    selectedTarget: DedupMergeTarget?,
    onSelectTarget: (DedupMergeTarget) -> Unit,
    onCreateMdbxTarget: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(title = "目标数据库", subtitle = "合并后的条目会新增到这里")
            targets.forEach { target ->
                SelectableTargetRow(
                    target = target,
                    selected = target.target == selectedTarget,
                    onClick = { onSelectTarget(target.target) }
                )
            }
            OutlinedButton(onClick = onCreateMdbxTarget, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("新建 MDBX 目标库")
            }
        }
    }
}

@Composable
private fun MergeActionCard(
    plan: DedupMergePlan,
    isExecuting: Boolean,
    onExecuteMerge: () -> Unit
) {
    val canExecute = plan.selectedSources.isNotEmpty() && plan.target != null && plan.writableItems > 0 && !isExecuting
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("执行合并", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "只会新增到目标库。目标库已有的同类条目会跳过，不会覆盖。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onExecuteMerge,
                enabled = canExecute,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(if (isExecuting) "正在合并" else "写入 ${plan.writableItems} 条")
            }
        }
    }
}

@Composable
private fun SelectableSourceRow(
    source: DedupMergeSourceOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (selected) sourceColor(source.kind) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            SourceIcon(source.kind)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(source.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${source.kind.label()} · ${source.countSummary()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SelectableTargetRow(
    target: DedupMergeTargetOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(target.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = target.countSummary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResolvedSecureItemPreviewCard(resolved: DedupResolvedSecureItem) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (resolved.existsInTarget) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(resolved.item.title.ifBlank { resolved.item.itemType.label() }, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = resolved.item.itemType.label(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(if (resolved.existsInTarget) "跳过" else "写入") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (resolved.existsInTarget) Icons.Default.Info else Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                resolved.sourceLabels.forEach { label -> AssistChip(onClick = {}, label = { Text(label) }) }
                if (resolved.sourceItemIds.size > 1) AssistChip(onClick = {}, label = { Text("${resolved.sourceItemIds.size} 个副本") })
            }
            if (resolved.conflictFields.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "冲突字段：${resolved.conflictFields.joinToString("、")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ResolvedPasswordPreviewCard(resolved: DedupResolvedPassword) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (resolved.existsInTarget) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(resolved.entry.title.ifBlank { "未命名密码" }, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = listOf(resolved.entry.username, resolved.entry.website).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(if (resolved.existsInTarget) "跳过" else "写入") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (resolved.existsInTarget) Icons.Default.Info else Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                resolved.sourceLabels.forEach { label -> AssistChip(onClick = {}, label = { Text(label) }) }
                if (resolved.sourceEntryIds.size > 1) AssistChip(onClick = {}, label = { Text("${resolved.sourceEntryIds.size} 个副本") })
                if (resolved.customFields.isNotEmpty()) AssistChip(onClick = {}, label = { Text("${resolved.customFields.size} 个自定义字段") })
            }
            if (resolved.conflictFields.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "冲突字段：${resolved.conflictFields.joinToString("、")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun WarningListCard(warnings: List<String>) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            warnings.forEach { warning ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                    Text(warning, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun StatusCard(icon: ImageVector, tint: Color, text: String) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryChip(label: String, value: String) {
    FilterChip(
        selected = false,
        onClick = {},
        label = { Text("$label $value") }
    )
}

@Composable
private fun SourceIcon(kind: DedupMergeSourceKind) {
    Icon(
        imageVector = Icons.Default.Storage,
        contentDescription = null,
        tint = sourceColor(kind),
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun sourceColor(kind: DedupMergeSourceKind): Color {
    return when (kind) {
        DedupMergeSourceKind.MONICA_LOCAL -> MaterialTheme.colorScheme.primary
        DedupMergeSourceKind.MDBX -> MaterialTheme.colorScheme.tertiary
        DedupMergeSourceKind.KEEPASS -> MaterialTheme.colorScheme.secondary
        DedupMergeSourceKind.BITWARDEN -> MaterialTheme.colorScheme.error
    }
}

private fun DedupMergeSourceKind.label(): String {
    return when (this) {
        DedupMergeSourceKind.MONICA_LOCAL -> "Monica"
        DedupMergeSourceKind.MDBX -> "MDBX"
        DedupMergeSourceKind.KEEPASS -> "KeePass"
        DedupMergeSourceKind.BITWARDEN -> "Bitwarden"
    }
}

private val DedupMergePlan.skippedItems: Int
    get() = targetExistingDuplicates + targetExistingSecureItems + unsupportedSourcePasskeys

private fun DedupMergeSourceOption.countSummary(): String {
    return itemCountParts(
        passwordCount = passwordCount,
        secureItemCount = secureItemCount,
        passkeyCount = passkeyCount
    )
}

private fun DedupMergeTargetOption.countSummary(): String {
    return itemCountParts(
        passwordCount = passwordCount,
        secureItemCount = secureItemCount,
        passkeyCount = passkeyCount
    )
}

private fun itemCountParts(
    passwordCount: Int,
    secureItemCount: Int,
    passkeyCount: Int
): String {
    return buildList {
        add("$passwordCount 条密码")
        if (secureItemCount > 0) add("$secureItemCount 个安全项")
        if (passkeyCount > 0) add("$passkeyCount 个通行密钥")
    }.joinToString(" · ")
}

private fun ItemType.label(): String {
    return when (this) {
        ItemType.PASSWORD -> "密码"
        ItemType.TOTP -> "验证器"
        ItemType.BANK_CARD -> "银行卡"
        ItemType.DOCUMENT -> "证件"
        ItemType.BILLING_ADDRESS -> "账单地址"
        ItemType.PAYMENT_ACCOUNT -> "支付方式"
        ItemType.NOTE -> "笔记"
    }
}
