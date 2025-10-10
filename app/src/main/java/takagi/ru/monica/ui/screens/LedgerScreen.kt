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
import takagi.ru.monica.data.ledger.LedgerEntryType
import takagi.ru.monica.data.ledger.LedgerEntryWithRelations
import takagi.ru.monica.viewmodel.LedgerViewModel
import takagi.ru.monica.viewmodel.CurrencyStats
import java.text.SimpleDateFormat
import java.util.Locale

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
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.ledger_title)) },
                actions = {
                    IconButton(onClick = { /* TODO: 分类管理 */ }) {
                        Icon(Icons.Default.Category, contentDescription = context.getString(R.string.ledger_category))
                    }
                    IconButton(onClick = { /* TODO: 标签管理 */ }) {
                        Icon(Icons.Default.Label, contentDescription = "标签")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToAddEntry(null) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = context.getString(R.string.ledger_add_entry))
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 统计卡片
                SummaryCard(
                    currencyStats = uiState.currencyStats,
                    onClick = onNavigateToAssetManagement
                )

                // 记账列表
                if (uiState.entries.isEmpty()) {
                    EmptyLedgerState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.entries) { entryWithRelations ->
                            LedgerEntryCard(
                                entryWithRelations = entryWithRelations,
                                onClick = { onNavigateToAddEntry(entryWithRelations.entry.id) },
                                onDelete = { viewModel.deleteEntry(entryWithRelations.entry) }
                            )
                        }
                    }
                }
            }
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
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val entry = entryWithRelations.entry
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    
    // 删除确认对话框状态
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // 获取记账条目的货币符号
    val currencySymbol = getCurrencySymbol(entry.currencyCode)
    
    // 获取支付方式显示文本(资产名称)
    // 由于数据库关系映射问题，暂时使用paymentMethod字段直接显示
    val paymentMethodText = entry.paymentMethod.ifEmpty { context.getString(R.string.ledger_payment_method) }

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
                    
                    // 第二行显示备注和时间
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (entry.note.isNotBlank()) {
                            Text(
                                text = entry.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = dateFormat.format(entry.occurredAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
