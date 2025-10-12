package takagi.ru.monica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.ledger.Asset
import takagi.ru.monica.data.ledger.AssetType
import takagi.ru.monica.viewmodel.LedgerViewModel
import java.util.Date

/**
 * 添加/编辑资产界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAssetScreen(
    viewModel: LedgerViewModel,
    assetId: Long? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    
    var name by remember { mutableStateOf("") }
    var assetType by remember { mutableStateOf(AssetType.WECHAT) }
    var balance by remember { mutableStateOf("") }
    var currencyCode by remember { mutableStateOf("CNY") }
    var showTypeMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var existingAsset by remember { mutableStateOf<Asset?>(null) } // 保存现有资产的完整数据

    // 加载现有资产数据
    LaunchedEffect(assetId) {
        if (assetId != null && assetId > 0) {
            viewModel.getAssetById(assetId)?.let { asset ->
                existingAsset = asset // 保存完整的资产对象
                name = asset.name
                assetType = asset.assetType
                balance = (asset.balanceInCents / 100.0).toString()
                currencyCode = asset.currencyCode
                
                // 添加调试日志
                android.util.Log.d("AddEditAssetScreen", "Loaded asset: id=${asset.id}, name=${asset.name}, balance=${asset.balanceInCents}, balanceDisplay=$balance")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (assetId == null || assetId <= 0) 
                            context.getString(R.string.asset_add_title)
                        else 
                            context.getString(R.string.asset_edit_title)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = context.getString(R.string.asset_back))
                    }
                },
                actions = {
                    if (assetId != null && assetId > 0) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = context.getString(R.string.ledger_delete))
                        }
                    }
                    IconButton(
                        onClick = {
                            val balanceInCents = (balance.toDoubleOrNull()?.times(100))?.toLong() ?: 0L
                            val displayName = name.ifBlank { 
                                getAssetTypeDefaultName(context, assetType)
                            }
                            val iconKey = when (assetType) {
                                AssetType.WECHAT -> "wechat"
                                AssetType.ALIPAY -> "alipay"
                                AssetType.UNIONPAY -> "unionpay"
                                AssetType.PAYPAL -> "paypal"
                                AssetType.BANK_CARD -> "bank"
                                AssetType.CASH -> "cash"
                                AssetType.OTHER -> "wallet"
                            }
                            val color = when (assetType) {
                                AssetType.WECHAT -> "#09BB07"
                                AssetType.ALIPAY -> "#1677FF"
                                AssetType.UNIONPAY -> "#E4393C"
                                AssetType.PAYPAL -> "#003087"
                                AssetType.BANK_CARD -> "#FF5722"
                                AssetType.CASH -> "#FF9800"
                                AssetType.OTHER -> "#4CAF50"
                            }
                            
                            // 添加调试日志
                            android.util.Log.d("AddEditAssetScreen", "Saving asset: balance=$balance, balanceInCents=$balanceInCents")
                            
                            val asset = if (existingAsset != null) {
                                // 编辑现有资产，保留 createdAt 和其他字段
                                existingAsset!!.copy(
                                    name = displayName,
                                    assetType = assetType,
                                    balanceInCents = balanceInCents,
                                    currencyCode = currencyCode,
                                    iconKey = iconKey,
                                    colorHex = color,
                                    updatedAt = Date()
                                )
                            } else {
                                // 创建新资产
                                Asset(
                                    id = 0,
                                    name = displayName,
                                    assetType = assetType,
                                    balanceInCents = balanceInCents,
                                    currencyCode = currencyCode,
                                    iconKey = iconKey,
                                    colorHex = color,
                                    createdAt = Date(),
                                    updatedAt = Date()
                                )
                            }
                            
                            android.util.Log.d("AddEditAssetScreen", "Asset to save: id=${asset.id}, balance=${asset.balanceInCents}")
                            viewModel.saveAsset(asset)
                            onNavigateBack()
                        },
                        enabled = name.isNotBlank() || assetType != AssetType.OTHER
                    ) {
                        Icon(Icons.Default.Check, contentDescription = context.getString(R.string.asset_save))
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
            // 资产类型选择
            Text(
                text = context.getString(R.string.asset_type),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Card {
                Column(modifier = Modifier.padding(8.dp)) {
                    AssetType.values().filter { 
                        it != AssetType.BANK_CARD // 银行卡在银行卡页面管理
                    }.forEach { type ->
                        val typeColor = parseColor(getAssetColor(type))
                        val isColorDark = isColorDark(typeColor)
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { assetType = type }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 纯色卡片(降低亮度)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(typeColor.copy(alpha = 0.6f))
                            )

                            Text(
                                text = getAssetTypeName(type),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )

                            RadioButton(
                                selected = assetType == type,
                                onClick = { assetType = type }
                            )
                        }

                        if (type != AssetType.OTHER) {
                            HorizontalDivider()
                        }
                    }
                }
            }

            // 账户名称
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(context.getString(R.string.asset_name)) },
                placeholder = { Text(getAssetTypeDefaultName(context, assetType)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 余额输入
            OutlinedTextField(
                value = balance,
                onValueChange = { balance = it },
                label = { Text(context.getString(R.string.asset_balance)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                prefix = { Text(getCurrencySymbol(currencyCode)) },
                trailingIcon = {
                    IconButton(onClick = { showCurrencyDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = context.getString(R.string.asset_currency))
                    }
                }
            )

            // 提示信息
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "记账时，对应资产的余额会自动更新",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }

    // 货币选择对话框
    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text(context.getString(R.string.asset_select_currency)) },
            text = {
                LazyColumn {
                    items(getSupportedCurrencies()) { currency ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currencyCode = currency.code
                                    showCurrencyDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${currency.symbol} ${currency.name}",
                                modifier = Modifier.weight(1f)
                            )
                            RadioButton(
                                selected = currencyCode == currency.code,
                                onClick = {
                                    currencyCode = currency.code
                                    showCurrencyDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) {
                    Text(context.getString(R.string.asset_close))
                }
            }
        )
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(context.getString(R.string.asset_delete_title)) },
            text = { Text(context.getString(R.string.asset_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (assetId != null && assetId > 0) {
                            viewModel.deleteAssetById(assetId)
                        }
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

data class Currency(
    val code: String,
    val symbol: String,
    val name: String
)

private fun getSupportedCurrencies() = listOf(
    Currency("CNY", "¥", "人民币"),
    Currency("USD", "$", "美元"),
    Currency("EUR", "€", "欧元"),
    Currency("GBP", "£", "英镑"),
    Currency("JPY", "¥", "日元"),
    Currency("KRW", "₩", "韩元"),
    Currency("HKD", "HK$", "港币"),
    Currency("TWD", "NT$", "新台币"),
    Currency("SGD", "S$", "新加坡元"),
    Currency("AUD", "A$", "澳元"),
    Currency("CAD", "C$", "加元"),
    Currency("CHF", "CHF", "瑞士法郎"),
    Currency("THB", "฿", "泰铢"),
    Currency("MYR", "RM", "马来西亚林吉特"),
    Currency("RUB", "₽", "卢布"),
    Currency("INR", "₹", "印度卢比"),
    Currency("BRL", "R$", "巴西雷亚尔")
)

private fun getCurrencySymbol(code: String): String {
    return getSupportedCurrencies().find { it.code == code }?.symbol ?: "¥"
}

@Composable
private fun getAssetTypeName(type: AssetType): String {
    val context = LocalContext.current
    return when (type) {
        AssetType.WECHAT -> context.getString(R.string.asset_type_wechat)
        AssetType.ALIPAY -> context.getString(R.string.asset_type_alipay)
        AssetType.UNIONPAY -> context.getString(R.string.asset_type_unionpay)
        AssetType.PAYPAL -> context.getString(R.string.asset_type_paypal)
        AssetType.BANK_CARD -> context.getString(R.string.asset_type_bank_card)
        AssetType.CASH -> context.getString(R.string.asset_type_cash)
        AssetType.OTHER -> context.getString(R.string.asset_type_other)
    }
}

private fun getAssetTypeDefaultName(context: android.content.Context, type: AssetType) = when (type) {
    AssetType.WECHAT -> context.getString(R.string.asset_type_wechat)
    AssetType.ALIPAY -> context.getString(R.string.asset_type_alipay)
    AssetType.UNIONPAY -> context.getString(R.string.asset_type_unionpay)
    AssetType.PAYPAL -> context.getString(R.string.asset_type_paypal)
    AssetType.BANK_CARD -> context.getString(R.string.asset_type_bank_card)
    AssetType.CASH -> context.getString(R.string.asset_type_cash)
    AssetType.OTHER -> context.getString(R.string.asset_type_other)
}

private fun getAssetIconKey(type: AssetType) = when (type) {
    AssetType.WECHAT -> "wechat"
    AssetType.ALIPAY -> "alipay"
    AssetType.UNIONPAY -> "unionpay"
    AssetType.PAYPAL -> "paypal"
    AssetType.BANK_CARD -> "bank"
    AssetType.CASH -> "cash"
    AssetType.OTHER -> "wallet"
}

private fun getAssetColor(type: AssetType) = when (type) {
    AssetType.WECHAT -> "#07A658"
    AssetType.ALIPAY -> "#1296DB"
    AssetType.UNIONPAY -> "#D93026"
    AssetType.PAYPAL -> "#003087"
    AssetType.BANK_CARD -> "#E64A19"
    AssetType.CASH -> "#F57C00"
    AssetType.OTHER -> "#388E3C"
}

private fun parseColor(colorHex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: Exception) {
        Color(0xFF4CAF50)
    }
}

/**
 * 判断颜色是否为深色
 */
private fun isColorDark(color: Color): Boolean {
    val luminance = (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue)
    return luminance < 0.5
}
