package takagi.ru.monica.autofill.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import takagi.ru.monica.autofill.data.AutofillItem
import takagi.ru.monica.autofill.data.PaymentInfo

/**
 * 账单信息列表组件
 * 
 * 使用LazyColumn实现虚拟滚动,高效处理账单信息条目
 * 优化:
 * - 使用key参数避免不必要的重组
 * - 使用animateItemPlacement添加列表项动画
 * 
 * @param paymentInfo 账单信息列表
 * @param onItemSelected 选择回调
 * @param modifier 修饰符
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PaymentInfoList(
    paymentInfo: List<PaymentInfo>,
    onItemSelected: (AutofillItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (paymentInfo.isEmpty()) {
        // 显示空状态
        EmptyPaymentInfoState(modifier = modifier)
    } else {
        // 显示账单信息列表
        LazyColumn(
            modifier = modifier.fillMaxSize()
        ) {
            items(
                items = paymentInfo,
                key = { it.id }
            ) { info ->
                PaymentInfoListItem(
                    paymentInfo = info,
                    onItemClick = {
                        onItemSelected(AutofillItem.Payment(info))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItemPlacement()
                )
            }
        }
    }
}

/**
 * 空状态视图
 * 
 * 当账单信息列表为空或搜索无结果时显示
 * 
 * @param modifier 修饰符
 */
@Composable
fun EmptyPaymentInfoState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CreditCard,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "未找到账单信息",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "尝试调整搜索条件或添加新的支付信息",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
