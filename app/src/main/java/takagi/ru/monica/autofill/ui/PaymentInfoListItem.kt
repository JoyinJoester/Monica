package takagi.ru.monica.autofill.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import takagi.ru.monica.autofill.data.CardBrand
import takagi.ru.monica.autofill.data.PaymentInfo
import takagi.ru.monica.autofill.utils.CardUtils

/**
 * 账单信息列表项组件
 * 
 * 使用Material Design 3的ListItem组件显示账单信息
 * 显示信用卡品牌图标、脱敏卡号和有效期
 * 
 * @param paymentInfo 账单信息
 * @param onItemClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun PaymentInfoListItem(
    paymentInfo: PaymentInfo,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { 
            Text(
                text = CardUtils.maskCardNumber(paymentInfo.cardNumber),
                style = MaterialTheme.typography.bodyLarge
            ) 
        },
        supportingContent = { 
            Text(
                text = "有效期: ${paymentInfo.expiryDate}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ) 
        },
        leadingContent = {
            CardBrandIcon(
                cardBrand = CardBrand.detect(paymentInfo.cardNumber)
            )
        },
        modifier = modifier.clickable(onClick = onItemClick)
    )
}

/**
 * 信用卡品牌图标组件
 * 
 * 根据信用卡品牌显示对应的图标
 * 目前使用通用的信用卡图标,后续可以添加品牌特定图标
 * 
 * @param cardBrand 信用卡品牌
 * @param modifier 修饰符
 */
@Composable
fun CardBrandIcon(
    cardBrand: CardBrand,
    modifier: Modifier = Modifier
) {
    // TODO: 根据不同品牌显示不同图标
    // 目前使用通用信用卡图标
    Icon(
        imageVector = Icons.Default.CreditCard,
        contentDescription = cardBrand.getDisplayName(),
        tint = when (cardBrand) {
            CardBrand.VISA -> MaterialTheme.colorScheme.primary
            CardBrand.MASTERCARD -> MaterialTheme.colorScheme.secondary
            CardBrand.AMEX -> MaterialTheme.colorScheme.tertiary
            CardBrand.UNIONPAY -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier.size(40.dp)
    )
}
