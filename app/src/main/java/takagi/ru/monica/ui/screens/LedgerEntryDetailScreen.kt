package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.ledger.LedgerEntry
import takagi.ru.monica.data.ledger.LedgerEntryType
import takagi.ru.monica.data.ledger.LedgerEntryWithRelations
import takagi.ru.monica.viewmodel.LedgerViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * 账单详情界面，支持二次编辑
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerEntryDetailScreen(
    entryWithRelations: LedgerEntryWithRelations,
    viewModel: LedgerViewModel,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val entry = entryWithRelations.entry
    val assets by viewModel.assets.collectAsState(initial = emptyList()) // 获取资产列表
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    
    // 删除确认对话框状态
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // 获取记账条目的货币符号
    val currencySymbol = getCurrencySymbol(entry.currencyCode)
    
    // 获取支付方式显示文本(资产名称)
    val paymentMethodText = getDetailPaymentMethodDisplayName(entry.paymentMethod, assets, context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.ledger_entry_detail)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToEdit(entry.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = context.getString(R.string.edit))
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = context.getString(R.string.delete))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 金额显示卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${if (entry.type == LedgerEntryType.INCOME) "+" else "-"}$currencySymbol${String.format("%.2f", entry.amountInCents / 100.0)}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = when (entry.type) {
                            LedgerEntryType.INCOME -> MaterialTheme.colorScheme.primary
                            LedgerEntryType.EXPENSE -> MaterialTheme.colorScheme.error
                            LedgerEntryType.TRANSFER -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }
            }
            
            // 基本信息
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailItem(
                        label = context.getString(R.string.ledger_entry_type),
                        value = when (entry.type) {
                            LedgerEntryType.INCOME -> context.getString(R.string.ledger_income)
                            LedgerEntryType.EXPENSE -> context.getString(R.string.ledger_expense)
                            LedgerEntryType.TRANSFER -> context.getString(R.string.ledger_transfer)
                        }
                    )
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    DetailItem(
                        label = context.getString(R.string.ledger_payment_method),
                        value = paymentMethodText
                    )
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    DetailItem(
                        label = context.getString(R.string.ledger_time),
                        value = dateFormat.format(entry.occurredAt)
                    )
                    
                    if (entry.note.isNotBlank()) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        DetailItem(
                            label = context.getString(R.string.ledger_note),
                            value = entry.note
                        )
                    }
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
                        viewModel.deleteEntry(entry)
                        showDeleteDialog = false
                        onNavigateBack()
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
private fun DetailItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

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

// 获取支付方式的显示名称 (账单详情专用版本)
private fun getDetailPaymentMethodDisplayName(paymentMethod: String, assets: List<takagi.ru.monica.data.ledger.Asset>, context: android.content.Context): String {
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