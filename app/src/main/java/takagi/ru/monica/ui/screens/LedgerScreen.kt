package takagi.ru.monica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.ledger.LedgerEntry
import takagi.ru.monica.data.ledger.LedgerEntryType
import takagi.ru.monica.data.ledger.LedgerEntryWithRelations
import takagi.ru.monica.data.ledger.Asset
import takagi.ru.monica.viewmodel.LedgerViewModel
import takagi.ru.monica.viewmodel.LedgerViewType
import takagi.ru.monica.viewmodel.CurrencyStats
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Calendar
import java.util.Date
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// 获取货币符号
private fun getCurrencySymbol(currencyCode: String): String {
    return when (currencyCode) {
        "CNY" -> "¥"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        "KRW" -> "₩"
        "HKD" -> "HK$"
        "TWD" -> "NT$"
        "SGD" -> "S$"
        "AUD" -> "A$"
        "CAD" -> "C$"
        "CHF" -> "CHF"
        "THB" -> "฿"
        "MYR" -> "RM"
        "RUB" -> "₽"
        "INR" -> "₹"
        "BRL" -> "R$"
        else -> currencyCode
    }
}

/**
 * 记账主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    viewModel: LedgerViewModel,
    onNavigateToAddEntry: (Long?) -> Unit,
    onNavigateToAssetManagement: () -> Unit,
    onNavigateToEntryDetail: (Long) -> Unit // 新增参数
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val assets by viewModel.assets.collectAsState(initial = emptyList()) // 获取资产列表

    // 初始化默认资产
    LaunchedEffect(Unit) {
        viewModel.initializeDefaultAssets()
        // 重新计算所有资产余额以包含现有账单
        viewModel.recalculateAllAssetBalances()
    }

    // 同步银行卡到资产管理
    LaunchedEffect(Unit) {
        viewModel.syncBankCardsToAssets()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.ledger_title)) },
                actions = {
                    // 视图切换按钮
                    IconButton(onClick = { viewModel.switchViewType(uiState.viewType.getNext()) }) {
                        Icon(Icons.Default.ViewWeek, contentDescription = context.getString(R.string.ledger_view_switch))
                    }
                    
                    // 资产管理按钮
                    IconButton(onClick = onNavigateToAssetManagement) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = "资产管理")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToAddEntry(null) }
            ) {
                Icon(Icons.Default.Add, contentDescription = context.getString(R.string.ledger_add_entry))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 汇总卡片
            SummaryCard(
                currencyStats = uiState.currencyStats,
                onClick = onNavigateToAssetManagement
            )
            
            // 记账条目列表
            LedgerEntryList(
                entries = uiState.entries,
                viewType = uiState.viewType,
                onNavigateToAddEntry = onNavigateToAddEntry,
                onNavigateToEntryDetail = onNavigateToEntryDetail, // 传递新参数
                onDeleteEntry = { viewModel.deleteEntry(it) },
                assets = assets // 传递资产列表
            )
        }
    }
}

@Composable
private fun SummaryCard(
    currencyStats: List<CurrencyStats>,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = context.getString(R.string.ledger_balance),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 显示最多3种货币的统计
            if (currencyStats.isEmpty()) {
                Text(
                    text = getCurrencySymbol("CNY") + "0.00",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                currencyStats.forEach { stats ->
                    val currencySymbol = getCurrencySymbol(stats.currencyCode)
                    val balance = stats.balance
                    
                    // 余额显示
                    Text(
                        text = currencySymbol + String.format("%.2f", balance),
                        style = if (currencyStats.size == 1) 
                            MaterialTheme.typography.headlineMedium 
                        else 
                            MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            balance > 0 -> Color(0xFF4CAF50)
                            balance < 0 -> Color(0xFFF44336)
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                    
                    // 收入支出详情
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${context.getString(R.string.ledger_income)}: $currencySymbol${String.format("%.2f", stats.income)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF4CAF50)
                        )
                        Text(
                            text = "${context.getString(R.string.ledger_expense)}: $currencySymbol${String.format("%.2f", stats.expense)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFF44336)
                        )
                    }
                    
                    // 如果有多个货币,添加分隔空间
                    if (currencyStats.size > 1 && stats != currencyStats.last()) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerEntryCard(
    entryWithRelations: LedgerEntryWithRelations,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    assets: List<Asset> // 添加资产列表参数
) {
    val context = LocalContext.current
    val entry = entryWithRelations.entry
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    
    // 删除确认对话框状态
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // 获取记账条目的货币符号
    val currencySymbol = getCurrencySymbol(entry.currencyCode)
    
    // 获取支付方式显示文本(资产名称)
    val paymentMethodText = getPaymentMethodDisplayName(entry.paymentMethod, assets, context)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 类型图标
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            when (entry.type) {
                                LedgerEntryType.INCOME -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                                LedgerEntryType.EXPENSE -> Color(0xFFF44336).copy(alpha = 0.2f)
                                LedgerEntryType.TRANSFER -> Color(0xFF2196F3).copy(alpha = 0.2f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (entry.type) {
                            LedgerEntryType.INCOME -> Icons.Default.TrendingUp
                            LedgerEntryType.EXPENSE -> Icons.Default.TrendingDown
                            LedgerEntryType.TRANSFER -> Icons.Default.SwapHoriz
                        },
                        contentDescription = null,
                        tint = when (entry.type) {
                            LedgerEntryType.INCOME -> Color(0xFF4CAF50)
                            LedgerEntryType.EXPENSE -> Color(0xFFF44336)
                            LedgerEntryType.TRANSFER -> Color(0xFF2196F3)
                        }
                    )
                }

                Column {
                    // 标题位置显示支付方式
                    Text(
                        text = paymentMethodText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    // 备注信息（如果存在）
                    if (entry.note.isNotBlank()) {
                        Text(
                            text = entry.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    
                    // 时间信息单独一行显示
                    Text(
                        text = dateFormat.format(entry.occurredAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 金额和删除按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${if (entry.type == LedgerEntryType.INCOME) "+" else "-"}$currencySymbol${String.format("%.2f", entry.amountInCents / 100.0)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = when (entry.type) {
                        LedgerEntryType.INCOME -> Color(0xFF4CAF50)
                        LedgerEntryType.EXPENSE -> Color(0xFFF44336)
                        LedgerEntryType.TRANSFER -> MaterialTheme.colorScheme.onSurface
                    }
                )
                
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = context.getString(R.string.ledger_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(context.getString(R.string.ledger_delete_confirm_title)) },
            text = { Text(context.getString(R.string.ledger_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text(context.getString(R.string.ledger_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(context.getString(R.string.ledger_cancel))
                }
            }
        )
    }
}

@Composable
private fun LedgerEntryList(
    entries: List<LedgerEntryWithRelations>,
    viewType: LedgerViewType,
    onNavigateToAddEntry: (Long?) -> Unit,
    onNavigateToEntryDetail: (Long) -> Unit, // 新增参数
    onDeleteEntry: (LedgerEntry) -> Unit,
    assets: List<Asset> // 添加资产列表参数
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (viewType) {
            LedgerViewType.DAILY -> {
                // 按日分组显示
                val groupedByDay = entries.groupBy { getDayKey(it.entry.occurredAt) }
                groupedByDay.forEach { (dayKey, dayEntries) ->
                    // 显示日期分隔线和当日汇总
                    item {
                        DailySummaryHeader(
                            date = dayKey,
                            entries = dayEntries
                        )
                    }
                    
                    // 显示该日的记账条目
                    items(dayEntries) { entryWithRelations ->
                        LedgerEntryCard(
                            entryWithRelations = entryWithRelations,
                            onClick = { onNavigateToEntryDetail(entryWithRelations.entry.id) }, // 修改为详情界面
                            onDelete = { onDeleteEntry(entryWithRelations.entry) },
                            assets = assets // 传递资产列表
                        )
                    }
                }
            }
            
            LedgerViewType.WEEKLY -> {
                // 按周分组显示
                val groupedByWeek = entries.groupBy { getWeekKey(it.entry.occurredAt) }
                groupedByWeek.forEach { (weekKey, weekEntries) ->
                    item {
                        WeeklySummaryHeader(
                            week = weekKey,
                            entries = weekEntries
                        )
                    }
                    
                    items(weekEntries) { entryWithRelations ->
                        LedgerEntryCard(
                            entryWithRelations = entryWithRelations,
                            onClick = { onNavigateToEntryDetail(entryWithRelations.entry.id) }, // 修改为详情界面
                            onDelete = { onDeleteEntry(entryWithRelations.entry) },
                            assets = assets // 传递资产列表
                        )
                    }
                }
            }
            
            LedgerViewType.MONTHLY -> {
                // 按月分组显示
                val groupedByMonth = entries.groupBy { getMonthKey(it.entry.occurredAt) }
                groupedByMonth.forEach { (monthKey, monthEntries) ->
                    item {
                        MonthlySummaryHeader(
                            month = monthKey,
                            entries = monthEntries
                        )
                    }
                    
                    items(monthEntries) { entryWithRelations ->
                        LedgerEntryCard(
                            entryWithRelations = entryWithRelations,
                            onClick = { onNavigateToEntryDetail(entryWithRelations.entry.id) }, // 修改为详情界面
                            onDelete = { onDeleteEntry(entryWithRelations.entry) },
                            assets = assets // 传递资产列表
                        )
                    }
                }
            }
            
            LedgerViewType.YEARLY -> {
                // 按年分组显示
                val groupedByYear = entries.groupBy { getYearKey(it.entry.occurredAt) }
                groupedByYear.forEach { (yearKey, yearEntries) ->
                    item {
                        YearlySummaryHeader(
                            year = yearKey,
                            entries = yearEntries
                        )
                    }
                    
                    items(yearEntries) { entryWithRelations ->
                        LedgerEntryCard(
                            entryWithRelations = entryWithRelations,
                            onClick = { onNavigateToEntryDetail(entryWithRelations.entry.id) }, // 修改为详情界面
                            onDelete = { onDeleteEntry(entryWithRelations.entry) },
                            assets = assets // 传递资产列表
                        )
                    }
                }
            }
            
            LedgerViewType.ALL -> {
                // 显示所有条目，不进行分组
                items(entries) { entryWithRelations ->
                    LedgerEntryCard(
                        entryWithRelations = entryWithRelations,
                        onClick = { onNavigateToEntryDetail(entryWithRelations.entry.id) }, // 修改为详情界面
                        onDelete = { onDeleteEntry(entryWithRelations.entry) },
                        assets = assets // 传递资产列表
                    )
                }
            }
        }
    }
}

// 获取日期键（用于日视图分组）
private fun getDayKey(date: Date): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return dateFormat.format(date)
}

// 获取周键（用于周视图分组）
private fun getWeekKey(date: Date): String {
    val calendar = Calendar.getInstance()
    calendar.time = date
    val year = calendar.get(Calendar.YEAR)
    val week = calendar.get(Calendar.WEEK_OF_YEAR)
    return "$year-W$week"
}

// 获取月键（用于月视图分组）
private fun getMonthKey(date: Date): String {
    val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    return dateFormat.format(date)
}

// 获取年键（用于年视图分组）
private fun getYearKey(date: Date): String {
    val dateFormat = SimpleDateFormat("yyyy", Locale.getDefault())
    return dateFormat.format(date)
}

// 日视图的汇总头部
@Composable
private fun DailySummaryHeader(date: String, entries: List<LedgerEntryWithRelations>) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val displayFormat = SimpleDateFormat("MM月dd日", Locale.getDefault())
    
    val displayDate = try {
        displayFormat.format(dateFormat.parse(date)!!)
    } catch (e: Exception) {
        date
    }
    
    // 计算当日收入和支出汇总（按货币分组）
    val incomeByCurrency = entries
        .filter { it.entry.type == LedgerEntryType.INCOME }
        .groupBy { it.entry.currencyCode }
        .mapValues { (_, entries) ->
            entries.sumOf { it.entry.amountInCents } / 100.0
        }
        .toList()
        .sortedByDescending { it.second } // 按金额降序
        .take(3) // 最多显示3种货币
        
    val expenseByCurrency = entries
        .filter { it.entry.type == LedgerEntryType.EXPENSE }
        .groupBy { it.entry.currencyCode }
        .mapValues { (_, entries) ->
            entries.sumOf { it.entry.amountInCents } / 100.0
        }
        .toList()
        .sortedByDescending { it.second } // 按金额降序
        .take(3) // 最多显示3种货币
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // 日期标题
        Text(
            text = displayDate,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        // 收入汇总（最多3种货币）
        if (incomeByCurrency.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            incomeByCurrency.forEach { (currencyCode, amount) ->
                val currencySymbol = getCurrencySymbol(currencyCode)
                Text(
                    text = "${context.getString(R.string.ledger_income)}: $currencySymbol${String.format("%.2f", amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4CAF50)
                )
            }
        }
        
        // 支出汇总（最多3种货币）
        if (expenseByCurrency.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            expenseByCurrency.forEach { (currencyCode, amount) ->
                val currencySymbol = getCurrencySymbol(currencyCode)
                Text(
                    text = "${context.getString(R.string.ledger_expense)}: $currencySymbol${String.format("%.2f", amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFF44336)
                )
            }
        }
        
        // 分割线
        Divider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

// 周视图的汇总头部
@Composable
private fun WeeklySummaryHeader(week: String, entries: List<LedgerEntryWithRelations>) {
    val context = LocalContext.current
    
    // 计算当周收入和支出汇总（按货币分组）
    val incomeByCurrency = entries
        .filter { it.entry.type == LedgerEntryType.INCOME }
        .groupBy { it.entry.currencyCode }
        .mapValues { (_, entries) ->
            entries.sumOf { it.entry.amountInCents } / 100.0
        }
        .toList()
        .sortedByDescending { it.second } // 按金额降序
        .take(3) // 最多显示3种货币
        
    val expenseByCurrency = entries
        .filter { it.entry.type == LedgerEntryType.EXPENSE }
        .groupBy { it.entry.currencyCode }
        .mapValues { (_, entries) ->
            entries.sumOf { it.entry.amountInCents } / 100.0
        }
        .toList()
        .sortedByDescending { it.second } // 按金额降序
        .take(3) // 最多显示3种货币
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // 周标题
        Text(
            text = week,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        // 收入汇总（最多3种货币）
        if (incomeByCurrency.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            incomeByCurrency.forEach { (currencyCode, amount) ->
                val currencySymbol = getCurrencySymbol(currencyCode)
                Text(
                    text = "${context.getString(R.string.ledger_income)}: $currencySymbol${String.format("%.2f", amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4CAF50)
                )
            }
        }
        
        // 支出汇总（最多3种货币）
        if (expenseByCurrency.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            expenseByCurrency.forEach { (currencyCode, amount) ->
                val currencySymbol = getCurrencySymbol(currencyCode)
                Text(
                    text = "${context.getString(R.string.ledger_expense)}: $currencySymbol${String.format("%.2f", amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFF44336)
                )
            }
        }
        
        // 分割线
        Divider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

// 月视图的汇总头部
@Composable
private fun MonthlySummaryHeader(month: String, entries: List<LedgerEntryWithRelations>) {
    val context = LocalContext.current
    
    // 计算当月收入和支出汇总（按货币分组）
    val incomeByCurrency = entries
        .filter { it.entry.type == LedgerEntryType.INCOME }
        .groupBy { it.entry.currencyCode }
        .mapValues { (_, entries) ->
            entries.sumOf { it.entry.amountInCents } / 100.0
        }
        .toList()
        .sortedByDescending { it.second } // 按金额降序
        .take(3) // 最多显示3种货币
        
    val expenseByCurrency = entries
        .filter { it.entry.type == LedgerEntryType.EXPENSE }
        .groupBy { it.entry.currencyCode }
        .mapValues { (_, entries) ->
            entries.sumOf { it.entry.amountInCents } / 100.0
        }
        .toList()
        .sortedByDescending { it.second } // 按金额降序
        .take(3) // 最多显示3种货币
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // 月标题
        Text(
            text = month,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        // 收入汇总（最多3种货币）
        if (incomeByCurrency.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            incomeByCurrency.forEach { (currencyCode, amount) ->
                val currencySymbol = getCurrencySymbol(currencyCode)
                Text(
                    text = "${context.getString(R.string.ledger_income)}: $currencySymbol${String.format("%.2f", amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4CAF50)
                )
            }
        }
        
        // 支出汇总（最多3种货币）
        if (expenseByCurrency.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            expenseByCurrency.forEach { (currencyCode, amount) ->
                val currencySymbol = getCurrencySymbol(currencyCode)
                Text(
                    text = "${context.getString(R.string.ledger_expense)}: $currencySymbol${String.format("%.2f", amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFF44336)
                )
            }
        }
        
        // 分割线
        Divider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

// 年视图的汇总头部
@Composable
private fun YearlySummaryHeader(year: String, entries: List<LedgerEntryWithRelations>) {
    val context = LocalContext.current
    
    // 计算当年收入和支出汇总（按货币分组）
    val incomeByCurrency = entries
        .filter { it.entry.type == LedgerEntryType.INCOME }
        .groupBy { it.entry.currencyCode }
        .mapValues { (_, entries) ->
            entries.sumOf { it.entry.amountInCents } / 100.0
        }
        .toList()
        .sortedByDescending { it.second } // 按金额降序
        .take(3) // 最多显示3种货币
        
    val expenseByCurrency = entries
        .filter { it.entry.type == LedgerEntryType.EXPENSE }
        .groupBy { it.entry.currencyCode }
        .mapValues { (_, entries) ->
            entries.sumOf { it.entry.amountInCents } / 100.0
        }
        .toList()
        .sortedByDescending { it.second } // 按金额降序
        .take(3) // 最多显示3种货币
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // 年标题
        Text(
            text = year,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        // 收入汇总（最多3种货币）
        if (incomeByCurrency.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            incomeByCurrency.forEach { (currencyCode, amount) ->
                val currencySymbol = getCurrencySymbol(currencyCode)
                Text(
                    text = "${context.getString(R.string.ledger_income)}: $currencySymbol${String.format("%.2f", amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4CAF50)
                )
            }
        }
        
        // 支出汇总（最多3种货币）
        if (expenseByCurrency.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            expenseByCurrency.forEach { (currencyCode, amount) ->
                val currencySymbol = getCurrencySymbol(currencyCode)
                Text(
                    text = "${context.getString(R.string.ledger_expense)}: $currencySymbol${String.format("%.2f", amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFF44336)
                )
            }
        }
        
        // 分割线
        Divider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun EmptyLedgerState() {
    val context = LocalContext.current
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalance,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Text(
                text = context.getString(R.string.ledger_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// 获取支付方式的显示名称
fun getPaymentMethodDisplayName(paymentMethod: String, assets: List<Asset>, context: android.content.Context): String {
    if (paymentMethod.isEmpty()) {
        return context.getString(R.string.ledger_payment_method)
    }
    
    // 尝试将paymentMethod解析为资产ID
    val assetId = paymentMethod.toLongOrNull()
    if (assetId != null) {
        // 根据资产ID查找资产名称
        val asset = assets.find { it.id == assetId }
        if (asset != null) {
            return asset.name
        }
    }
    
    // 根据支付方式类型返回对应的本地化名称
    return when {
        paymentMethod.equals("wechat", ignoreCase = true) || 
        paymentMethod.equals("微信", ignoreCase = true) -> 
            context.getString(R.string.ledger_payment_wechat)
        paymentMethod.equals("alipay", ignoreCase = true) || 
        paymentMethod.equals("支付宝", ignoreCase = true) -> 
            context.getString(R.string.ledger_payment_alipay)
        paymentMethod.equals("unionpay", ignoreCase = true) || 
        paymentMethod.equals("云闪付", ignoreCase = true) -> 
            context.getString(R.string.ledger_payment_unionpay)
        paymentMethod.equals("paypal", ignoreCase = true) -> 
            "PayPal"
        paymentMethod.equals("cash", ignoreCase = true) || 
        paymentMethod.equals("现金", ignoreCase = true) -> 
            context.getString(R.string.asset_type_cash)
        else -> paymentMethod.ifEmpty { context.getString(R.string.ledger_payment_method) }
    }
}
