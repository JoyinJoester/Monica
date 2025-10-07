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
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 记账主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    viewModel: LedgerViewModel,
    onNavigateToAddEntry: (Long?) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.ledger_title)) },
                actions = {
                    IconButton(onClick = { /* TODO: 分类管理 */ }) {
                        Icon(Icons.Default.Category, contentDescription = "分类")
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
                Icon(Icons.Default.Add, contentDescription = "添加记账")
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
                    totalIncome = uiState.totalIncome,
                    totalExpense = uiState.totalExpense,
                    currencyFormat = currencyFormat
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
                                currencyFormat = currencyFormat,
                                onClick = { onNavigateToAddEntry(entryWithRelations.entry.id) }
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
    totalIncome: Double,
    totalExpense: Double,
    currencyFormat: NumberFormat
) {
    val context = LocalContext.current
    val balance = totalIncome - totalExpense

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
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
            
            Text(
                text = currencyFormat.format(balance),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    balance > 0 -> Color(0xFF4CAF50)
                    balance < 0 -> Color(0xFFF44336)
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = context.getString(R.string.ledger_income),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = currencyFormat.format(totalIncome),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = context.getString(R.string.ledger_expense),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = currencyFormat.format(totalExpense),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFF44336),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerEntryCard(
    entryWithRelations: LedgerEntryWithRelations,
    currencyFormat: NumberFormat,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val entry = entryWithRelations.entry
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    
    // 获取支付方式显示文本
    val paymentMethodText = when (entry.paymentMethod) {
        "wechat" -> context.getString(R.string.ledger_payment_wechat)
        "alipay" -> context.getString(R.string.ledger_payment_alipay)
        "unionpay" -> context.getString(R.string.ledger_payment_unionpay)
        "" -> context.getString(R.string.ledger_entry_type)
        else -> entry.paymentMethod // 银行卡ID,可以后续优化为显示银行卡名称
    }

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

            Text(
                text = "${if (entry.type == LedgerEntryType.INCOME) "+" else "-"}${currencyFormat.format(entry.amountInCents / 100.0)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = when (entry.type) {
                    LedgerEntryType.INCOME -> Color(0xFF4CAF50)
                    LedgerEntryType.EXPENSE -> Color(0xFFF44336)
                    LedgerEntryType.TRANSFER -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
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
