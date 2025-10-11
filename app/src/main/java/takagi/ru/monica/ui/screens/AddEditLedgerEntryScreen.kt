package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import takagi.ru.monica.R
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.ledger.LedgerCategory
import takagi.ru.monica.data.ledger.LedgerEntry
import takagi.ru.monica.data.ledger.LedgerEntryType
import takagi.ru.monica.viewmodel.LedgerViewModel
import takagi.ru.monica.viewmodel.BankCardViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * 添加/编辑记账条目界面
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditLedgerEntryScreen(
    viewModel: LedgerViewModel,
    bankCardViewModel: BankCardViewModel,
    entryId: Long? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val assets by viewModel.assets.collectAsState(initial = emptyList())

    var amount by remember { mutableStateOf("") }
    var entryType by remember { mutableStateOf(LedgerEntryType.EXPENSE) }
    var note by remember { mutableStateOf("") }
    var occurredAt by remember { mutableStateOf(Date()) }
    var showNoteField by remember { mutableStateOf(false) }
    var showTimeSelector by remember { mutableStateOf(false) }
    var selectedAssetId by remember { mutableStateOf<Long?>(null) } // 选中的资产ID
    var showPaymentMethodMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    // 计算显示金额
    val displayAmount = amount.toDoubleOrNull() ?: 0.0
    

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    
    // 加载现有数据（如果是编辑模式）
    LaunchedEffect(entryId) {
        if (entryId != null && entryId > 0) {
            viewModel.getEntryById(entryId)?.let { entryWithRelations ->
                val entry = entryWithRelations.entry
                // 填充现有数据
                amount = String.format("%.2f", entry.amountInCents / 100.0)
                entryType = entry.type
                note = entry.note
                occurredAt = entry.occurredAt
                // 设置选中的资产ID
                entry.paymentMethod.toLongOrNull()?.let { assetId ->
                    selectedAssetId = assetId
                }
                // 如果有备注，展开备注字段
                if (note.isNotBlank()) {
                    showNoteField = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (entryId == null) 
                            context.getString(R.string.ledger_add_entry)
                        else 
                            context.getString(R.string.ledger_edit_entry)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            // 保存记账条目
                            val amountInCents = (amount.toDoubleOrNull()?.times(100))?.toLong() ?: 0L
                            if (amountInCents > 0 && selectedAssetId != null) {
                                // 获取选中资产的货币代码
                                val selectedAsset = assets.find { it.id == selectedAssetId }
                                val currencyCode = selectedAsset?.currencyCode ?: "CNY"
                                
                                val entry = LedgerEntry(
                                    id = entryId ?: 0,
                                    title = "", // 不再需要标题
                                    amountInCents = amountInCents,
                                    currencyCode = currencyCode,
                                    type = entryType,
                                    categoryId = null, // 不再需要分类
                                    occurredAt = occurredAt,
                                    note = note,
                                    paymentMethod = selectedAssetId.toString(), // 存储资产ID
                                    createdAt = if (entryId == null) Date() else Date(),
                                    updatedAt = Date()
                                )
                                viewModel.saveEntry(entry)
                                
                                // 添加调试日志
                                android.util.Log.d("AddEditLedgerEntryScreen", "Saved entry with paymentMethod: ${selectedAssetId.toString()}")
                                
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "保存")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部大金额显示
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
                        text = "¥%.2f".format(displayAmount),
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 48.sp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 金额输入框 (圆角)
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(context.getString(R.string.ledger_amount)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                prefix = { Text("¥") },
                shape = RoundedCornerShape(16.dp)
            )
            // 类型选择
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = context.getString(R.string.ledger_entry_type),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LedgerEntryType.values().forEach { type ->
                            FilterChip(
                                selected = entryType == type,
                                onClick = { entryType = type },
                                label = { 
                                    Text(
                                        when (type) {
                                            LedgerEntryType.INCOME -> context.getString(R.string.ledger_income)
                                            LedgerEntryType.EXPENSE -> context.getString(R.string.ledger_expense)
                                            LedgerEntryType.TRANSFER -> context.getString(R.string.ledger_transfer)
                                        }
                                    ) 
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 支付方式选择(资产选择)
            ExposedDropdownMenuBox(
                expanded = showPaymentMethodMenu,
                onExpandedChange = { showPaymentMethodMenu = it }
            ) {
                OutlinedTextField(
                    value = assets.find { it.id == selectedAssetId }?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(context.getString(R.string.ledger_payment_method)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPaymentMethodMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(16.dp)
                )
                
                ExposedDropdownMenu(
                    expanded = showPaymentMethodMenu,
                    onDismissRequest = { showPaymentMethodMenu = false }
                ) {
                    // 显示所有资产
                    assets.forEach { asset ->
                        DropdownMenuItem(
                            text = { 
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(asset.name)
                                    Text(
                                        text = getCurrencySymbol(asset.currencyCode),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                selectedAssetId = asset.id
                                showPaymentMethodMenu = false
                            }
                        )
                    }
                }
            }

            // 备注和时间并排显示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 备注按钮
                OutlinedButton(
                    onClick = { showNoteField = !showNoteField },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("备注")
                }

                // 时间按钮
                OutlinedButton(
                    onClick = { showTimeSelector = !showTimeSelector },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("时间")
                }
            }

            // 备注输入框 (展开时显示)
            if (showNoteField) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(context.getString(R.string.ledger_note)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // 时间选择 (展开时显示)
            if (showTimeSelector) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = context.getString(R.string.ledger_time),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = dateFormat.format(occurredAt),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Icon(Icons.Default.Schedule, contentDescription = null)
                    }
                }
            }
        }
    }

    // 日期选择对话框
    if (showDatePicker) {
        val calendar = remember { 
            java.util.Calendar.getInstance().apply {
                time = occurredAt
            }
        }
        
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = occurredAt.time
        )
        
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val newCalendar = java.util.Calendar.getInstance().apply {
                            timeInMillis = millis
                            set(java.util.Calendar.HOUR_OF_DAY, calendar.get(java.util.Calendar.HOUR_OF_DAY))
                            set(java.util.Calendar.MINUTE, calendar.get(java.util.Calendar.MINUTE))
                        }
                        occurredAt = newCalendar.time
                    }
                    showDatePicker = false
                    showTimePicker = true
                }) {
                    Text("下一步")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            androidx.compose.material3.DatePicker(
                state = datePickerState,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    // 时间选择对话框
    if (showTimePicker) {
        val calendar = remember { 
            java.util.Calendar.getInstance().apply {
                time = occurredAt
            }
        }
        
        // 时和分的状态变量(提升到对话框级别)
        var hour by remember { mutableStateOf(calendar.get(java.util.Calendar.HOUR_OF_DAY).toString()) }
        var minute by remember { mutableStateOf(calendar.get(java.util.Calendar.MINUTE).toString()) }
        
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择时间") },
            text = {
                // 简单的时间选择器(使用数字输入)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { 
                            if (it.isEmpty() || (it.toIntOrNull() != null && it.toInt() in 0..23)) {
                                hour = it
                            }
                        },
                        label = { Text("时") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Text(":")
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { 
                            if (it.isEmpty() || (it.toIntOrNull() != null && it.toInt() in 0..59)) {
                                minute = it
                            }
                        },
                        label = { Text("分") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val h = hour.toIntOrNull() ?: calendar.get(java.util.Calendar.HOUR_OF_DAY)
                    val m = minute.toIntOrNull() ?: calendar.get(java.util.Calendar.MINUTE)
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, h)
                    calendar.set(java.util.Calendar.MINUTE, m)
                    occurredAt = calendar.time
                    showTimePicker = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 已移除弹窗方式的分类选择

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
