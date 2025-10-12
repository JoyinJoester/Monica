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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.ledger.Asset
import takagi.ru.monica.data.ledger.AssetType
import takagi.ru.monica.viewmodel.LedgerViewModel

/**
 * 资产管理界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetManagementScreen(
    viewModel: LedgerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddAsset: (Long?) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val assets by viewModel.assets.collectAsState(initial = emptyList())
    val totalBalance by viewModel.totalBalance.collectAsState(initial = 0L)
    
    // 移除自动重新计算资产余额
    // 用户手动设置的余额不应该被覆盖
    // LaunchedEffect(Unit) {
    //     viewModel.recalculateAllAssetBalances()
    // }
    
    // 添加调试日志
    android.util.Log.d("AssetManagementScreen", "Assets updated, count: ${assets.size}")
    assets.forEach { asset ->
        android.util.Log.d("AssetManagementScreen", "Asset: id=${asset.id}, name=${asset.name}, balance=${asset.balanceInCents}")
    }
    
    // 获取多货币资产统计
    val currencyAssetStats = uiState.currencyAssetStats

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.asset_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = context.getString(R.string.asset_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToAddAsset(null) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = context.getString(R.string.asset_add))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 总资产卡片 - 支持多货币
            MultiCurrencyAssetCard(currencyAssetStats = currencyAssetStats)

            // 资产列表
            if (assets.isEmpty()) {
                EmptyAssetState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(assets) { asset ->
                        AssetCard(
                            asset = asset,
                            onClick = { onNavigateToAddAsset(asset.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiCurrencyAssetCard(
    currencyAssetStats: List<takagi.ru.monica.viewmodel.CurrencyAssetStats>
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
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
                text = context.getString(R.string.asset_total),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (currencyAssetStats.isEmpty()) {
                // 没有资产时显示默认值
                Text(
                    text = getCurrencySymbol("CNY") + "0.00",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                // 显示多货币统计（最多3种）
                currencyAssetStats.forEach { stats ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = getCurrencySymbol(stats.currencyCode) + 
                                  String.format("%.2f", stats.totalBalance / 100.0),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "(${stats.assetCount} ${context.getString(R.string.asset_count_unit)})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    
                    if (stats != currencyAssetStats.last()) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalAssetCard(
    totalBalance: Long,
    currencyCode: String
) {
    val context = LocalContext.current
    val currencySymbol = getCurrencySymbol(currencyCode)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
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
                text = context.getString(R.string.asset_total),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = currencySymbol + String.format("%.2f", totalBalance / 100.0),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun AssetCard(
    asset: Asset,
    onClick: () -> Unit
) {
    val cardColor = parseColor(asset.colorHex).copy(alpha = 0.7f) // 降低亮度
    val contentColor = if (isColorDark(cardColor)) Color.White else Color.Black
    
    // 添加调试日志
    android.util.Log.d("AssetManagementScreen", "AssetCard: id=${asset.id}, name=${asset.name}, balance=${asset.balanceInCents}")
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = asset.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = contentColor
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = getAssetTypeName(asset.assetType),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }

            Text(
                text = getCurrencySymbol(asset.currencyCode) + 
                      String.format("%.2f", asset.balanceInCents / 100.0),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun EmptyAssetState() {
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
                text = context.getString(R.string.asset_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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

private fun parseColor(colorHex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: Exception) {
        Color(0xFF4CAF50)
    }
}

/**
 * 判断颜色是否为深色
 * 使用亮度公式: (0.299*R + 0.587*G + 0.114*B)
 */
private fun isColorDark(color: Color): Boolean {
    val luminance = (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue)
    return luminance < 0.5
}

private fun getCurrencySymbol(code: String): String {
    return when (code) {
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
        else -> "¥"
    }
}